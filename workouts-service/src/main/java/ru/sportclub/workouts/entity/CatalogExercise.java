package ru.sportclub.workouts.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "exercise_catalog")
public class CatalogExercise {
    @Id
    @Column(length = 60)
    private String id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "muscle_group", nullable = false, length = 100)
    private String muscleGroup;

    @Column(nullable = false, length = 100)
    private String equipment;

    @Column(nullable = false, length = 500)
    private String description;

    protected CatalogExercise() {
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getMuscleGroup() {
        return muscleGroup;
    }

    public String getEquipment() {
        return equipment;
    }

    public String getDescription() {
        return description;
    }
}
