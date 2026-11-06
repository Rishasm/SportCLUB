package ru.sportclub.workouts.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "workout_programs")
public class WorkoutProgram {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "is_template", nullable = false)
    private boolean template;

    @Column(nullable = false)
    private boolean deleted;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "program", cascade = CascadeType.ALL)
    @OrderBy("position ASC")
    private List<WorkoutProgramExercise> exercises = new ArrayList<>();

    protected WorkoutProgram() {
    }

    public WorkoutProgram(UUID ownerId, String name, String description, OffsetDateTime createdAt) {
        this.ownerId = ownerId;
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isTemplate() {
        return template;
    }

    public List<WorkoutProgramExercise> getExercises() {
        return exercises;
    }

    public void addExercise(CatalogExercise exercise, int sets, int reps, BigDecimal weightKg) {
        exercises.add(new WorkoutProgramExercise(this, exercises.size() + 1, exercise, sets, reps, weightKg));
    }
}
