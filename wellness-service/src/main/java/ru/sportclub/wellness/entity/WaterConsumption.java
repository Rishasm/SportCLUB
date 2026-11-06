package ru.sportclub.wellness.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "wellness_water_entries")
public class WaterConsumption {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "entry_date", nullable = false)
    private LocalDate date;

    @Column(name = "amount_ml", nullable = false)
    private int amountMl;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected WaterConsumption() {
    }

    public WaterConsumption(UUID memberId, LocalDate date, int amountMl, OffsetDateTime createdAt) {
        this.memberId = memberId;
        this.date = date;
        this.amountMl = amountMl;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public LocalDate getDate() {
        return date;
    }

    public int getAmountMl() {
        return amountMl;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
