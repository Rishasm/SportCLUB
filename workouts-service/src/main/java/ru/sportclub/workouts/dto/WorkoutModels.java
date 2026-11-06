package ru.sportclub.workouts.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class WorkoutModels {
    private WorkoutModels() {}

    public record Exercise(String id, String name, String muscleGroup, String equipment, String description) {}
    public record ProgramExercise(String exerciseId, String name, String muscleGroup, int sets, int reps, BigDecimal weightKg) {}
    public record Program(UUID id, String name, String description, boolean isTemplate, int exerciseCount, List<ProgramExercise> exercises) {}
    public record ProgramExerciseRequest(@NotBlank String exerciseId, @Min(1) @Max(6) int sets,
                                         @Min(1) @Max(100) int reps,
                                         @NotNull @DecimalMin("0") @DecimalMax("500") @Digits(integer=3,fraction=2) BigDecimal weightKg) {}
    public record ProgramRequest(@NotBlank @Size(min=2,max=100) String name, @Size(max=500) String description,
                                  @NotEmpty @Size(max=12) List<@NotNull @Valid ProgramExerciseRequest> exercises) {}
    public record StartRequest(@NotNull UUID programId) {}
    public record SetRequest(@NotNull @DecimalMin("0") @DecimalMax("500") @Digits(integer=3,fraction=2) BigDecimal weightKg,
                              @NotNull @Min(1) @Max(100) Integer reps, @NotNull Boolean completed) {}
    public record FinishRequest(@Min(1) @Max(360) Integer durationMinutes,
                                 @DecimalMin("30") @DecimalMax("300") @Digits(integer=3,fraction=2) BigDecimal weightKg) {}
    public record PreviousResult(BigDecimal weightKg, int reps, OffsetDateTime completedAt) {}
    public record WorkoutSet(UUID id, int setNumber, BigDecimal weightKg, int reps, boolean completed) {}
    public record SessionExercise(String exerciseId, String name, String muscleGroup, PreviousResult previousResult,
                                   List<WorkoutSet> sets) {}
    public record Session(UUID id, String programName, String status, OffsetDateTime startedAt, OffsetDateTime finishedAt,
                           Integer durationMinutes, int completedSets, int totalSets, BigDecimal totalVolumeKg,
                           Integer estimatedCalories, BigDecimal weightKg, List<SessionExercise> exercises) {}
    public record SessionSummary(UUID id, String programName, String status, OffsetDateTime startedAt, OffsetDateTime finishedAt,
                                  Integer durationMinutes, int completedSets, int totalSets, BigDecimal totalVolumeKg,
                                  Integer estimatedCalories, BigDecimal weightKg) {}
}

