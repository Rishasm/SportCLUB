package ru.sportclub.workouts.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "workout_sets")
public class WorkoutSetEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_exercise_id", nullable = false)
    private WorkoutSessionExercise exercise;

    @Column(name = "set_number", nullable = false)
    private int setNumber;

    @Column(name = "weight_kg", nullable = false, precision = 8, scale = 2)
    private BigDecimal weightKg;

    @Column(nullable = false)
    private int reps;

    @Column(nullable = false)
    private boolean completed;

    protected WorkoutSetEntry() {
    }

    public WorkoutSetEntry(WorkoutSessionExercise exercise, int setNumber, BigDecimal weightKg, int reps) {
        this.exercise = exercise;
        this.setNumber = setNumber;
        this.weightKg = weightKg;
        this.reps = reps;
    }

    public UUID getId() {
        return id;
    }

    public int getSetNumber() {
        return setNumber;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public int getReps() {
        return reps;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void revise(BigDecimal weightKg, int reps, boolean completed) {
        this.weightKg = weightKg;
        this.reps = reps;
        this.completed = completed;
    }
}
