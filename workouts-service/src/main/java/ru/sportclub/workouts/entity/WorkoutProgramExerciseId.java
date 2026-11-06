package ru.sportclub.workouts.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class WorkoutProgramExerciseId implements Serializable {
    private UUID program;
    private int position;

    public WorkoutProgramExerciseId() {
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof WorkoutProgramExerciseId id && position == id.position
                && Objects.equals(program, id.program);
    }

    @Override
    public int hashCode() {
        return Objects.hash(program, position);
    }
}
