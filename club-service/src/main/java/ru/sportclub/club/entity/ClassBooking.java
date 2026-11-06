package ru.sportclub.club.entity;

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
@Table(name = "bookings")
public class ClassBooking {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id", nullable = false)
    private ClubClass clubClass;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "booked_at", nullable = false)
    private OffsetDateTime bookedAt;

    @Column(name = "member_name", length = 80)
    private String memberName;

    protected ClassBooking() {
    }

    public ClassBooking(ClubClass clubClass, UUID memberId, OffsetDateTime bookedAt) {
        this.clubClass = clubClass;
        this.memberId = memberId;
        confirm(bookedAt);
    }

    public UUID getId() {
        return id;
    }

    public ClubClass getClubClass() {
        return clubClass;
    }

    public String getStatus() {
        return status;
    }

    public UUID getMemberId() { return memberId; }

    public String getMemberName() { return memberName; }

    public void setMemberName(String memberName) { this.memberName = memberName; }

    public void confirm(OffsetDateTime bookedAt) {
        this.status = "CONFIRMED";
        this.bookedAt = bookedAt;
    }

    public void cancel() {
        this.status = "CANCELLED";
    }
}
