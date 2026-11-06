package ru.sportclub.workouts.controller;

import ru.sportclub.workouts.service.WorkoutService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import static ru.sportclub.workouts.dto.WorkoutModels.*;

@RestController
@RequestMapping("/api/workouts")
@Tag(name="Дневник тренировок")
@SecurityRequirement(name="bearerAuth")
public class WorkoutController {
    private final WorkoutService service;
    public WorkoutController(WorkoutService service) { this.service = service; }
    private UUID member(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }

    @GetMapping("/exercises")
    @Operation(summary="Каталог упражнений")
    public List<Exercise> exercises() { return service.exercises(); }

    @GetMapping("/programs")
    @Operation(summary="Готовые и собственные программы")
    public List<Program> programs(@AuthenticationPrincipal Jwt jwt) { return service.programs(member(jwt)); }

    @PostMapping("/programs") @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary="Создать собственную программу")
    public Program create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProgramRequest request) {
        return service.createProgram(member(jwt), request);
    }

    @DeleteMapping("/programs/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary="Удалить свою программу, сохранив историю")
    public void deleteProgram(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) { service.deleteProgram(member(jwt), id); }

    @GetMapping("/sessions/active")
    @Operation(summary="Текущая незавершённая тренировка")
    public Session active(@AuthenticationPrincipal Jwt jwt) { return service.active(member(jwt)); }

    @GetMapping("/sessions")
    @Operation(summary="Последние завершённые тренировки текущего клиента")
    public List<SessionSummary> history(@AuthenticationPrincipal Jwt jwt) { return service.history(member(jwt)); }

    @PostMapping("/sessions") @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary="Начать тренировку по программе")
    public Session start(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody StartRequest request) {
        return service.start(member(jwt), request.programId());
    }

    @GetMapping("/sessions/{id}")
    @Operation(summary="Подробности своей тренировки")
    public Session session(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) { return service.session(member(jwt), id); }

    @PutMapping("/sessions/{id}/sets/{setId}")
    @Operation(summary="Сохранить вес, повторения и отметку подхода")
    public Session updateSet(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID setId,
                             @Valid @RequestBody SetRequest request) {
        return service.updateSet(member(jwt), id, setId, request);
    }

    @PostMapping("/sessions/{id}/finish")
    @Operation(summary="Завершить тренировку; расход калорий является приблизительной оценкой")
    public Session finish(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                           @Valid @RequestBody(required=false) FinishRequest request) {
        return service.finish(member(jwt), id, request == null ? new FinishRequest(null, null) : request);
    }

    @DeleteMapping("/sessions/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary="Отменить незавершённую тренировку")
    public void discard(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) { service.discard(member(jwt), id); }
}
