package ru.sportclub.club.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import ru.sportclub.club.entity.ClubTrainer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClubTrainerRepository extends JpaRepository<ClubTrainer, UUID> {
    List<ClubTrainer> findAllByOrderByNameAsc();

    Optional<ClubTrainer> findByAccountId(UUID accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ClubTrainer t where t.id = :id")
    Optional<ClubTrainer> findByIdForUpdate(@Param("id") UUID id);
}
