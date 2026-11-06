package ru.sportclub.workouts.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sportclub.workouts.entity.WorkoutProgram;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkoutProgramRepository extends JpaRepository<WorkoutProgram, UUID> {
    @EntityGraph(attributePaths = {"exercises", "exercises.exercise"})
    @Query("""
            select p from WorkoutProgram p
            where p.deleted = false and (p.template = true or p.ownerId = :memberId)
            order by p.template desc, p.createdAt, p.id
            """)
    List<WorkoutProgram> findVisible(@Param("memberId") UUID memberId);

    @EntityGraph(attributePaths = {"exercises", "exercises.exercise"})
    @Query("""
            select p from WorkoutProgram p
            where p.id = :id and p.deleted = false and (p.template = true or p.ownerId = :memberId)
            """)
    Optional<WorkoutProgram> findVisibleById(@Param("id") UUID id, @Param("memberId") UUID memberId);

    @Modifying
    @Query("""
            update WorkoutProgram p set p.deleted = true
            where p.id = :id and p.ownerId = :memberId and p.template = false and p.deleted = false
            """)
    int markDeleted(@Param("id") UUID id, @Param("memberId") UUID memberId);
}
