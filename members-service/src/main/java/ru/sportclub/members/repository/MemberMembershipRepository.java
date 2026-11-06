package ru.sportclub.members.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sportclub.members.entity.MemberMembership;

import java.util.Optional;
import java.util.UUID;

public interface MemberMembershipRepository extends JpaRepository<MemberMembership, UUID> {
    Optional<MemberMembership> findFirstByMember_IdOrderByValidUntilDescStartsAtDesc(UUID memberId);
}
