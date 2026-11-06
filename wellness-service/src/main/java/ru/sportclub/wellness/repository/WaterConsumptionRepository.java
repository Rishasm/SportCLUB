package ru.sportclub.wellness.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sportclub.wellness.entity.WaterConsumption;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WaterConsumptionRepository extends JpaRepository<WaterConsumption, UUID> {
    Optional<WaterConsumption> findByIdAndMemberId(UUID id, UUID memberId);

    List<WaterConsumption> findAllByMemberIdAndDateBetweenOrderByCreatedAtAscIdAsc(UUID memberId, LocalDate from, LocalDate to);

    @Query("select coalesce(sum(w.amountMl), 0) from WaterConsumption w where w.memberId = :memberId and w.date = :date")
    long totalForDay(@Param("memberId") UUID memberId, @Param("date") LocalDate date);
}
