package ru.sportclub.workouts.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@IdClass(WorkoutProgramExerciseId.class)
@Table(name = "workout_program_exercises")
public class WorkoutProgramExercise {
    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false)
    private WorkoutProgram program;

    @Id
    @Column(name = "position_no")
    private int position;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id", nullable = false)
    private CatalogExercise exercise;

    @Column(name = "sets_count", nullable = false)
    private int sets;

    @Column(name = "planned_reps", nullable = false)
    private int reps;

    @Column(name = "weight_kg", nullable = false, precision = 8, scale = 2)
    private BigDecimal weightKg;

    protected WorkoutProgramExercise() {
    }

    public WorkoutProgramExercise(WorkoutProgram program, int position, CatalogExercise exercise, int sets, int reps, BigDecimal weightKg) {
        this.program = program;
        this.position = position;
        this.exercise = exercise;
        this.sets = sets;
        this.reps = reps;
        this.weightKg = weightKg;
    }

    public CatalogExercise getExercise() {
        return exercise;
    }

    public int getSets() {
        return sets;
    }

    public int getReps() {
        return reps;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }
}
