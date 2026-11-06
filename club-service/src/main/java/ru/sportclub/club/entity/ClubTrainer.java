package ru.sportclub.club.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "trainers")
public class ClubTrainer {
    @Id
    private UUID id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 160)
    private String specialty;

    @Column(nullable = false, length = 1000)
    private String bio;

    @Column(nullable = false, length = 8)
    private String initials;

    @Column(name = "account_id", unique = true)
    private UUID accountId;

    protected ClubTrainer() {
    }

    public ClubTrainer(UUID id, String name, String specialty, String bio, String initials) {
        this.id = id;
        this.name = name;
        this.specialty = specialty;
        this.bio = bio;
        this.initials = initials;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSpecialty() {
        return specialty;
    }

    public String getBio() {
        return bio;
    }

    public String getInitials() {
        return initials;
    }

    public void assignAccount(UUID id) {
        if (accountId == null) accountId = id;
    }
}
