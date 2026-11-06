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
@Table(name = "workout_session_exercises")
public class WorkoutSessionExercise {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private WorkoutSession session;

    @Column(name = "position_no", nullable = false)
    private int position;

    @Column(name = "exercise_id", nullable = false, length = 60)
    private String exerciseId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "muscle_group", nullable = false, length = 100)
    private String muscleGroup;

    @Column(name = "previous_weight_kg", precision = 8, scale = 2)
    private BigDecimal previousWeightKg;

    @Column(name = "previous_reps")
    private Integer previousReps;

    @Column(name = "previous_completed_at")
    private OffsetDateTime previousCompletedAt;

    @OneToMany(mappedBy = "exercise", cascade = CascadeType.ALL)
    @OrderBy("setNumber ASC")
    private List<WorkoutSetEntry> sets = new ArrayList<>();

    protected WorkoutSessionExercise() {
    }

    public WorkoutSessionExercise(WorkoutSession session, int position, CatalogExercise exercise, BigDecimal previousWeightKg, Integer previousReps, OffsetDateTime previousCompletedAt) {
        this.session = session;
        this.position = position;
        this.exerciseId = exercise.getId();
        this.name = exercise.getName();
        this.muscleGroup = exercise.getMuscleGroup();
        this.previousWeightKg = previousWeightKg;
        this.previousReps = previousReps;
        this.previousCompletedAt = previousCompletedAt;
    }

    public String getExerciseId() {
        return exerciseId;
    }

    public String getName() {
        return name;
    }

    public String getMuscleGroup() {
        return muscleGroup;
    }

    public BigDecimal getPreviousWeightKg() {
        return previousWeightKg;
    }

    public Integer getPreviousReps() {
        return previousReps;
    }

    public OffsetDateTime getPreviousCompletedAt() {
        return previousCompletedAt;
    }

    public List<WorkoutSetEntry> getSets() {
        return sets;
    }

    public void addSet(BigDecimal weightKg, int reps) {
        sets.add(new WorkoutSetEntry(this, sets.size() + 1, weightKg, reps));
    }
}
