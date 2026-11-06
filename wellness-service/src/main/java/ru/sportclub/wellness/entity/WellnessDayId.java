package ru.sportclub.wellness.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public class WellnessDayId implements Serializable {
    private UUID memberId;
    private LocalDate date;

    public WellnessDayId() {
    }

    public WellnessDayId(UUID memberId, LocalDate date) {
        this.memberId = memberId;
        this.date = date;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof WellnessDayId id && Objects.equals(memberId, id.memberId)
                && Objects.equals(date, id.date);
    }

    @Override
    public int hashCode() {
        return Objects.hash(memberId, date);
    }
}
