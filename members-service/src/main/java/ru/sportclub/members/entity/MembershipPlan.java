package ru.sportclub.members.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "membership_plans")
public class MembershipPlan {
    @Id
    @Column(length = 40)
    private String id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 300)
    private String description;

    @Column(name = "price_rub", nullable = false)
    private int priceRub;

    @Column(name = "duration_days", nullable = false)
    private int durationDays;

    @Column(nullable = false, length = 600)
    private String features;

    protected MembershipPlan() {
    }

    public String getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getPriceRub() {
        return priceRub;
    }

    public int getDurationDays() {
        return durationDays;
    }

    public String getFeatures() {
        return features;
    }
}
