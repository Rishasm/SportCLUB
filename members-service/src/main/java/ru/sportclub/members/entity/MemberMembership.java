package ru.sportclub.members.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "member_memberships")
public class MemberMembership {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private MemberAccount member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private MembershipPlan plan;

    @Column(name = "starts_at", nullable = false)
    private OffsetDateTime startsAt;

    @Column(name = "valid_until", nullable = false)
    private OffsetDateTime validUntil;

    protected MemberMembership() {
    }

    public MemberMembership(MemberAccount member, MembershipPlan plan, OffsetDateTime startsAt, OffsetDateTime validUntil) {
        this.member = member;
        this.plan = plan;
        this.startsAt = startsAt;
        this.validUntil = validUntil;
    }

    public UUID getId() {
        return id;
    }

    public MembershipPlan getPlan() {
        return plan;
    }

    public OffsetDateTime getStartsAt() {
        return startsAt;
    }

    public OffsetDateTime getValidUntil() {
        return validUntil;
    }
}
