package ru.sportclub.workouts.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import ru.sportclub.foundation.ApiException;
import ru.sportclub.workouts.entity.CatalogExercise;
import ru.sportclub.workouts.entity.WorkoutMemberLock;
import ru.sportclub.workouts.entity.WorkoutProgram;
import ru.sportclub.workouts.entity.WorkoutSession;
import ru.sportclub.workouts.entity.WorkoutSetEntry;
import ru.sportclub.workouts.repository.CatalogExerciseRepository;
import ru.sportclub.workouts.repository.WorkoutMemberLockRepository;
import ru.sportclub.workouts.repository.WorkoutProgramRepository;
import ru.sportclub.workouts.repository.WorkoutSessionRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static ru.sportclub.workouts.dto.WorkoutModels.*;

@Service
public class WorkoutService {
    private static final String ACTIVE = "ACTIVE";
    private static final BigDecimal CALORIES_FACTOR = new BigDecimal("0.06125");

    private final CatalogExerciseRepository catalog;
    private final WorkoutProgramRepository programs;
    private final WorkoutSessionRepository sessions;
    private final WorkoutMemberLockRepository memberLocks;
    private final TransactionTemplate transactions;

    public WorkoutService(CatalogExerciseRepository catalog, WorkoutProgramRepository programs,
                          WorkoutSessionRepository sessions, WorkoutMemberLockRepository memberLocks,
                          TransactionTemplate transactions) {
        this.catalog = catalog;
        this.programs = programs;
        this.sessions = sessions;
        this.memberLocks = memberLocks;
        this.transactions = transactions;
    }

    public List<Exercise> exercises() {
        return catalog.findAllByOrderByMuscleGroupAscNameAsc().stream().map(e ->
                new Exercise(e.getId(), e.getName(), e.getMuscleGroup(), e.getEquipment(), e.getDescription())).toList();
    }

    @Transactional(readOnly = true)
    public List<Program> programs(UUID memberId) {
        return programs.findVisible(memberId).stream().map(WorkoutService::programView).toList();
    }

    @Transactional
    public Program createProgram(UUID memberId, ProgramRequest request) {
        String name = request.name().trim();
        if (name.length() < 2) throw badRequest("Название программы должно содержать от 2 до 100 символов.");
        WorkoutProgram program = new WorkoutProgram(memberId, name,
                request.description() == null ? "" : request.description().trim(), now());
        Set<String> unique = new HashSet<>();
        for (var item : request.exercises()) {
            if (!unique.add(item.exerciseId())) throw badRequest("Упражнения в программе не должны повторяться.");
            CatalogExercise exercise = catalog.findById(item.exerciseId())
                    .orElseThrow(() -> badRequest("Выбрано неизвестное упражнение."));
            program.addExercise(exercise, item.sets(), item.reps(), item.weightKg().setScale(2));
        }
        return programView(programs.save(program));
    }

    @Transactional
    public void deleteProgram(UUID memberId, UUID programId) {
        if (programs.markDeleted(programId, memberId) == 0) throw notFound();
    }

    @Transactional(readOnly = true)
    public Session active(UUID memberId) {
        return sessions.findFirstByMemberIdAndStatusOrderByStartedAtDesc(memberId, ACTIVE)
                .map(WorkoutService::sessionView).orElse(null);
    }

    public Session start(UUID memberId, UUID programId) {
        ensureMemberLock(memberId);
        return transactions.execute(transaction -> {
            memberLocks.findForUpdate(memberId).orElseThrow();
            if (sessions.existsByMemberIdAndStatus(memberId, ACTIVE)) {
                throw conflict("ACTIVE_WORKOUT_EXISTS", "Сначала заверши или отмени текущую тренировку.");
            }
            WorkoutProgram program = programs.findVisibleById(programId, memberId).orElseThrow(WorkoutService::notFound);
            WorkoutSession session = new WorkoutSession(memberId, program, now());
            for (var planned : program.getExercises()) {
                var exercise = planned.getExercise();
                var previous = sessions.findPreviousSets(memberId, exercise.getId(), PageRequest.of(0, 1))
                        .stream().findFirst().orElse(null);
                var snapshot = session.addExercise(exercise, previous == null ? null : previous.getWeightKg(),
                        previous == null ? null : previous.getReps(), previous == null ? null : previous.getCompletedAt());
                for (int set = 0; set < planned.getSets(); set++) {
                    snapshot.addSet(planned.getWeightKg(), planned.getReps());
                }
            }
            return sessionView(sessions.save(session));
        });
    }

    @Transactional(readOnly = true)
    public Session session(UUID memberId, UUID sessionId) {
        return sessionView(sessions.findByIdAndMemberId(sessionId, memberId).orElseThrow(WorkoutService::notFound));
    }

    public List<SessionSummary> history(UUID memberId) {
        return sessions.findHistory(memberId, PageRequest.of(0, 100)).stream().map(row ->
                new SessionSummary(row.getId(), row.getProgramName(), row.getStatus(), row.getStartedAt(), row.getFinishedAt(),
                        row.getDurationMinutes(), (int) row.getCompletedSets(), (int) row.getTotalSets(),
                        row.getTotalVolumeKg(), row.getEstimatedCalories(), row.getWeightKg())).toList();
    }

    @Transactional
    public Session updateSet(UUID memberId, UUID sessionId, UUID setId, SetRequest request) {
        WorkoutSession session = lockActiveSession(memberId, sessionId);
        WorkoutSetEntry set = session.getExercises().stream().flatMap(exercise -> exercise.getSets().stream())
                .filter(item -> item.getId().equals(setId)).findFirst().orElseThrow(WorkoutService::notFound);
        set.revise(request.weightKg().setScale(2), request.reps(), request.completed());
        return sessionView(session);
    }

    @Transactional
    public Session finish(UUID memberId, UUID sessionId, FinishRequest request) {
        WorkoutSession session = lockActiveSession(memberId, sessionId);
        boolean hasCompletedSet = session.getExercises().stream().flatMap(exercise -> exercise.getSets().stream())
                .anyMatch(WorkoutSetEntry::isCompleted);
        if (!hasCompletedSet) throw conflict("EMPTY_WORKOUT", "Отметь хотя бы один выполненный подход.");

        OffsetDateTime finished = now();
        long elapsedMinutes = Math.max(1, (Duration.between(session.getStartedAt(), finished).getSeconds() + 59) / 60);
        if (request.durationMinutes() == null && elapsedMinutes > 360) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DURATION_REQUIRED",
                    "Укажите фактическую длительность тренировки от 1 до 360 минут.");
        }
        int minutes = request.durationMinutes() == null ? (int) elapsedMinutes : request.durationMinutes();
        BigDecimal weight = request.weightKg() == null ? null : request.weightKg().setScale(2);
        Integer calories = weight == null ? null : weight.multiply(BigDecimal.valueOf(minutes)).multiply(CALORIES_FACTOR)
                .setScale(0, RoundingMode.HALF_UP).intValueExact();
        session.finish(finished, minutes, weight, calories);
        return sessionView(session);
    }

    @Transactional
    public void discard(UUID memberId, UUID sessionId) {
        sessions.delete(lockActiveSession(memberId, sessionId));
    }

    private WorkoutSession lockActiveSession(UUID memberId, UUID sessionId) {
        WorkoutSession session = sessions.findForUpdate(sessionId, memberId).orElseThrow(WorkoutService::notFound);
        if (!ACTIVE.equals(session.getStatus())) {
            throw conflict("WORKOUT_COMPLETED", "Завершённую тренировку нельзя изменять.");
        }
        return session;
    }

    private void ensureMemberLock(UUID memberId) {
        if (memberLocks.existsById(memberId)) return;
        try {
            memberLocks.saveAndFlush(new WorkoutMemberLock(memberId));
        } catch (DataIntegrityViolationException exception) {
            if (!memberLocks.existsById(memberId)) throw exception;
        }
    }

    private static Program programView(WorkoutProgram program) {
        var exercises = program.getExercises().stream().map(planned -> {
            var exercise = planned.getExercise();
            return new ProgramExercise(exercise.getId(), exercise.getName(), exercise.getMuscleGroup(),
                    planned.getSets(), planned.getReps(), planned.getWeightKg());
        }).toList();
        return new Program(program.getId(), program.getName(), program.getDescription(), program.isTemplate(),
                exercises.size(), exercises);
    }

    private static Session sessionView(WorkoutSession session) {
        var exercises = session.getExercises().stream().map(exercise -> {
            PreviousResult previous = exercise.getPreviousWeightKg() == null ? null : new PreviousResult(
                    exercise.getPreviousWeightKg(), exercise.getPreviousReps(), exercise.getPreviousCompletedAt());
            var sets = exercise.getSets().stream().map(set ->
                    new WorkoutSet(set.getId(), set.getSetNumber(), set.getWeightKg(), set.getReps(), set.isCompleted())).toList();
            return new SessionExercise(exercise.getExerciseId(), exercise.getName(), exercise.getMuscleGroup(), previous, sets);
        }).toList();
        int total = 0;
        int completed = 0;
        BigDecimal volume = BigDecimal.ZERO;
        for (var exercise : exercises) {
            total += exercise.sets().size();
            for (var set : exercise.sets()) {
                if (set.completed()) {
                    completed++;
                    volume = volume.add(set.weightKg().multiply(BigDecimal.valueOf(set.reps())));
                }
            }
        }
        return new Session(session.getId(), session.getProgramName(), session.getStatus(), session.getStartedAt(),
                session.getFinishedAt(), session.getDurationMinutes(), completed, total, volume,
                session.getEstimatedCalories(), session.getWeightKg(), exercises);
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Программа или тренировка не найдена.");
    }

    private static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PROGRAM", message);
    }

    private static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
