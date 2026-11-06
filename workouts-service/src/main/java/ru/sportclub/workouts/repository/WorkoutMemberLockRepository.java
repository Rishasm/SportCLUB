package ru.sportclub.workouts.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sportclub.workouts.entity.WorkoutMemberLock;

import java.util.Optional;
import java.util.UUID;

public interface WorkoutMemberLockRepository extends JpaRepository<WorkoutMemberLock, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from WorkoutMemberLock l where l.memberId = :memberId")
    Optional<WorkoutMemberLock> findForUpdate(@Param("memberId") UUID memberId);
}
