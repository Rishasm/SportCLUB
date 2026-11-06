package ru.sportclub.wellness.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@IdClass(WellnessDayId.class)
@Table(name = "wellness_days")
public class WellnessDay {
    @Id
    @Column(name = "member_id")
    private UUID memberId;

    @Id
    @Column(name = "entry_date")
    private LocalDate date;

    @Column(name = "sleep_minutes")
    private Integer sleepMinutes;

    @Column(name = "weight_kg", precision = 5, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "height_cm", precision = 5, scale = 2)
    private BigDecimal heightCm;

    protected WellnessDay() {
    }

    public WellnessDay(UUID memberId, LocalDate date) {
        this.memberId = memberId;
        this.date = date;
    }

    public LocalDate getDate() {
        return date;
    }

    public Integer getSleepMinutes() {
        return sleepMinutes;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public BigDecimal getHeightCm() {
        return heightCm;
    }

    public void setSleepMinutes(Integer sleepMinutes) {
        this.sleepMinutes = sleepMinutes;
    }

    public void setBody(BigDecimal weightKg, BigDecimal heightCm) {
        this.weightKg = weightKg;
        this.heightCm = heightCm;
    }
}
