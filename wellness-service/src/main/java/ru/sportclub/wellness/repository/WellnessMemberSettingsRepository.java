package ru.sportclub.wellness.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import ru.sportclub.wellness.entity.WellnessMemberSettings;

import java.util.Optional;
import java.util.UUID;

public interface WellnessMemberSettingsRepository extends JpaRepository<WellnessMemberSettings, UUID> {
    @Modifying
    @Transactional
    @Query(value = "insert into wellness_member_settings (member_id) values (:memberId)", nativeQuery = true)
    void insertDefaults(@Param("memberId") UUID memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from WellnessMemberSettings s where s.memberId = :memberId")
    Optional<WellnessMemberSettings> findForUpdate(@Param("memberId") UUID memberId);
}
