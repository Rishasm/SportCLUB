package ru.sportclub.members.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sportclub.members.entity.MemberAccount;

import java.util.Optional;
import java.util.UUID;

public interface MemberAccountRepository extends JpaRepository<MemberAccount, UUID> {
    Optional<MemberAccount> findByEmail(String email);

    boolean existsByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from MemberAccount account where account.id = :id")
    Optional<MemberAccount> findByIdForUpdate(@Param("id") UUID id);
}
