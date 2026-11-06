package ru.sportclub.wellness.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "wellness_member_settings")
public class WellnessMemberSettings {
    @Id
    @Column(name = "member_id")
    private UUID memberId;

    @Column(name = "water_goal_ml", nullable = false)
    private int waterGoalMl;

    protected WellnessMemberSettings() {
    }

    public int getWaterGoalMl() {
        return waterGoalMl;
    }

    public void setWaterGoalMl(int waterGoalMl) {
        this.waterGoalMl = waterGoalMl;
    }
}
