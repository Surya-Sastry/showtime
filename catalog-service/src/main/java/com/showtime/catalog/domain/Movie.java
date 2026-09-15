package com.showtime.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "movies")
public class Movie {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(name = "runtime_minutes", nullable = false)
    private int runtimeMinutes;

    protected Movie() {
        // JPA
    }

    public Movie(String title, int runtimeMinutes) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (runtimeMinutes <= 0) {
            throw new IllegalArgumentException("runtimeMinutes must be positive");
        }
        this.title = title;
        this.runtimeMinutes = runtimeMinutes;
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public int getRuntimeMinutes() {
        return runtimeMinutes;
    }
}
