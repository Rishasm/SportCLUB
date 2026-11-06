package ru.sportclub.club.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "club_classes")
public class ClubClass {
    @Id
    private UUID id;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(name = "class_type", nullable = false, length = 24)
    private String type;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trainer_id", nullable = false)
    private ClubTrainer trainer;

    @Column(nullable = false, length = 100)
    private String room;

    @Column(name = "starts_at", nullable = false)
    private OffsetDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private OffsetDateTime endsAt;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false, length = 1500)
    private String description = "";

    @Column(nullable = false, length = 1500)
    private String technique = "";

    @Column(nullable = false, length = 20)
    private String level = "ALL_LEVELS";

    @Column(nullable = false)
    private boolean cancelled;

    @Column(nullable = false)
    private boolean demo;

    protected ClubClass() {
    }

    public ClubClass(UUID id, String title, String type, ClubTrainer trainer, String room, OffsetDateTime startsAt, OffsetDateTime endsAt, int capacity) {
        this.id = id;
        this.title = title;
        this.type = type;
        this.trainer = trainer;
        this.room = room;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.capacity = capacity;
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getType() {
        return type;
    }

    public ClubTrainer getTrainer() {
        return trainer;
    }

    public String getRoom() {
        return room;
    }

    public OffsetDateTime getStartsAt() {
        return startsAt;
    }

    public OffsetDateTime getEndsAt() {
        return endsAt;
    }

    public int getCapacity() {
        return capacity;
    }

    public String getDescription() { return description; }

    public String getTechnique() { return technique; }

    public String getLevel() { return level; }

    public boolean isCancelled() { return cancelled; }

    public boolean isDemo() { return demo; }

    public void update(String title, String type, ClubTrainer trainer, String room,
                       OffsetDateTime start, OffsetDateTime end, int capacity, String level) {
        this.title = title;
        this.type = type;
        this.trainer = trainer;
        this.room = room;
        this.startsAt = start;
        this.endsAt = end;
        this.capacity = capacity;
        this.level = level;
    }

    public void updateContent(String description, String technique) {
        this.description = description;
        this.technique = technique;
    }

    public void markDemo() { this.demo = true; }

    public void cancel() { this.cancelled = true; }
}
