package ru.sportclub.wellness.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sportclub.wellness.entity.WellnessDay;
import ru.sportclub.wellness.entity.WellnessDayId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface WellnessDayRepository extends JpaRepository<WellnessDay, WellnessDayId> {
    List<WellnessDay> findAllByMemberIdAndDateBetween(UUID memberId, LocalDate from, LocalDate to);
}
