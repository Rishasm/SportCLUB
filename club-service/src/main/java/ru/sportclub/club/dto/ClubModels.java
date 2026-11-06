package ru.sportclub.club.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class ClubModels {
    private ClubModels() {}

    public record Trainer(UUID id, String name, String specialty, String bio, String initials) {}

    public record ClassView(UUID id, String title, String type, UUID trainerId, String trainerName,
                            String room, OffsetDateTime startsAt, OffsetDateTime endsAt,
                            int capacity, int availableSpots, boolean bookedByMe) {}

    public record Booking(UUID id, UUID classId, String title, String trainerName, String room,
                          OffsetDateTime startsAt, OffsetDateTime endsAt, String status) {}

    public record BookingRequest(@NotNull(message = "Выберите занятие") UUID classId) {}

    public record CalendarRange(LocalDate min, LocalDate max, LocalDate today) {}

    public record ClassDetails(ClassView schedule, Trainer trainer, String description, String technique,
                               String level, boolean cancelled, boolean squatDemo) {}

    public record ClassEdit(@NotBlank @Size(max = 160) String title, @NotNull ClassType type,
                            @NotNull UUID trainerId, @NotBlank @Size(max = 100) String room,
                            @NotNull OffsetDateTime startsAt, @Min(15) @Max(180) int durationMinutes,
                            @Min(1) @Max(100) int capacity, @NotNull Level level,
                            @NotBlank @Size(max = 1500) String description, @NotNull @Size(max = 1500) String technique) {}

    public record ClassContent(@NotBlank @Size(max = 1500) String description,
                               @NotNull @Size(max = 1500) String technique) {}

    public enum ClassType { STRENGTH, FUNCTIONAL, STRETCH }
    public enum Level { BEGINNER, INTERMEDIATE, ALL_LEVELS }

    public record Participant(UUID memberId, String name) {}

    public record Notice(UUID id, String title, String body, OffsetDateTime createdAt, boolean announcement) {}

    public record Announcement(@NotBlank @Size(max = 160) String title,
                               @NotBlank @Size(max = 1000) String body) {}
}
