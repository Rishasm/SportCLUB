package ru.sportclub.club.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sportclub.club.entity.ClubClass;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClubClassRepository extends JpaRepository<ClubClass, UUID> {
    @EntityGraph(attributePaths = "trainer")
    @Query("""
            select c from ClubClass c
            where c.startsAt >= :from and c.startsAt < :until and c.cancelled = false
              and (:type is null or c.type = :type)
            order by c.startsAt
            """)
    List<ClubClass> findSchedule(@Param("from") OffsetDateTime from, @Param("until") OffsetDateTime until,
                                 @Param("type") String type);

    @EntityGraph(attributePaths = "trainer")
    List<ClubClass> findByStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(OffsetDateTime from, OffsetDateTime until);

    @EntityGraph(attributePaths = "trainer")
    List<ClubClass> findByTrainer_IdAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAt(
            UUID trainerId, OffsetDateTime from, OffsetDateTime until);

    boolean existsByTrainer_IdAndCancelledFalseAndIdNotAndStartsAtLessThanAndEndsAtGreaterThan(
            UUID trainerId, UUID id, OffsetDateTime end, OffsetDateTime start);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ClubClass c where c.id = :id")
    Optional<ClubClass> findByIdForUpdate(@Param("id") UUID id);
}
