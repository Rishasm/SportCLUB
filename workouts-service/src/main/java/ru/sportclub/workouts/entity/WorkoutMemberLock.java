package ru.sportclub.workouts.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "workout_member_locks")
public class WorkoutMemberLock {
    @Id
    @Column(name = "member_id")
    private UUID memberId;

    protected WorkoutMemberLock() {
    }

    public WorkoutMemberLock(UUID memberId) {
        this.memberId = memberId;
    }
}
