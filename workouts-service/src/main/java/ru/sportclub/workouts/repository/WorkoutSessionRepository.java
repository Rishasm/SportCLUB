package ru.sportclub.workouts.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sportclub.workouts.entity.WorkoutSession;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkoutSessionRepository extends JpaRepository<WorkoutSession, UUID> {
    Optional<WorkoutSession> findByIdAndMemberId(UUID id, UUID memberId);

    Optional<WorkoutSession> findFirstByMemberIdAndStatusOrderByStartedAtDesc(UUID memberId, String status);

    boolean existsByMemberIdAndStatus(UUID memberId, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from WorkoutSession s where s.id = :id and s.memberId = :memberId")
    Optional<WorkoutSession> findForUpdate(@Param("id") UUID id, @Param("memberId") UUID memberId);

    @Query("""
            select s.id as id, s.programName as programName, s.status as status,
                   s.startedAt as startedAt, s.finishedAt as finishedAt, s.durationMinutes as durationMinutes,
                   s.estimatedCalories as estimatedCalories, s.weightKg as weightKg,
                   count(a) as totalSets,
                   coalesce(sum(case when a.completed = true then 1 else 0 end), 0) as completedSets,
                   coalesce(sum(case when a.completed = true then a.weightKg * a.reps else 0 end), 0) as totalVolumeKg
            from WorkoutSession s left join s.exercises e left join e.sets a
            where s.memberId = :memberId and s.status = 'COMPLETED'
            group by s.id, s.programName, s.status, s.startedAt, s.finishedAt, s.durationMinutes,
                     s.estimatedCalories, s.weightKg
            order by s.finishedAt desc, s.id desc
            """)
    List<HistoryEntry> findHistory(@Param("memberId") UUID memberId, Pageable page);

    @Query("""
            select a.weightKg as weightKg, a.reps as reps, s.finishedAt as completedAt
            from WorkoutSession s join s.exercises e join e.sets a
            where s.memberId = :memberId and s.status = 'COMPLETED' and e.exerciseId = :exerciseId
              and a.completed = true
            order by s.finishedAt desc, s.id desc, a.setNumber desc
            """)
    List<PreviousSet> findPreviousSets(@Param("memberId") UUID memberId, @Param("exerciseId") String exerciseId,
                                       Pageable page);

    interface HistoryEntry {
        UUID getId();
        String getProgramName();
        String getStatus();
        OffsetDateTime getStartedAt();
        OffsetDateTime getFinishedAt();
        Integer getDurationMinutes();
        Integer getEstimatedCalories();
        BigDecimal getWeightKg();
        long getTotalSets();
        long getCompletedSets();
        BigDecimal getTotalVolumeKg();
    }

    interface PreviousSet {
        BigDecimal getWeightKg();
        int getReps();
        OffsetDateTime getCompletedAt();
    }
}
