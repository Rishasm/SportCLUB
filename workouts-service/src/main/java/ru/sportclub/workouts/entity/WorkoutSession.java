package ru.sportclub.workouts.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "workout_sessions")
public class WorkoutSession {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false)
    private WorkoutProgram program;

    @Column(name = "program_name", nullable = false, length = 100)
    private String programName;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "weight_kg", precision = 6, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "estimated_calories")
    private Integer estimatedCalories;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL)
    @OrderBy("position ASC")
    private List<WorkoutSessionExercise> exercises = new ArrayList<>();

    protected WorkoutSession() {
    }

    public WorkoutSession(UUID memberId, WorkoutProgram program, OffsetDateTime startedAt) {
        this.memberId = memberId;
        this.program = program;
        this.programName = program.getName();
        this.status = "ACTIVE";
        this.startedAt = startedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getProgramName() {
        return programName;
    }

    public String getStatus() {
        return status;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public OffsetDateTime getFinishedAt() {
        return finishedAt;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public Integer getEstimatedCalories() {
        return estimatedCalories;
    }

    public List<WorkoutSessionExercise> getExercises() {
        return exercises;
    }

    public WorkoutSessionExercise addExercise(CatalogExercise exercise, BigDecimal previousWeightKg,
                                              Integer previousReps, OffsetDateTime previousCompletedAt) {
        var snapshot = new WorkoutSessionExercise(this, exercises.size() + 1, exercise, previousWeightKg,
                previousReps, previousCompletedAt);
        exercises.add(snapshot);
        return snapshot;
    }

    public void finish(OffsetDateTime finishedAt, int durationMinutes, BigDecimal weightKg, Integer estimatedCalories) {
        this.status = "COMPLETED";
        this.finishedAt = finishedAt;
        this.durationMinutes = durationMinutes;
        this.weightKg = weightKg;
        this.estimatedCalories = estimatedCalories;
    }
}
