package com.showtime.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "seat_definitions")
public class SeatDefinition {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "screen_id", nullable = false)
    private Screen screen;

    @Column(name = "seat_label", nullable = false)
    private String seatLabel;

    protected SeatDefinition() {
        // JPA
    }

    public SeatDefinition(Screen screen, String seatLabel) {
        if (screen == null) {
            throw new IllegalArgumentException("screen must not be null");
        }
        if (seatLabel == null || seatLabel.isBlank()) {
            throw new IllegalArgumentException("seatLabel must not be blank");
        }
        this.screen = screen;
        this.seatLabel = seatLabel;
    }

    public UUID getId() {
        return id;
    }

    public Screen getScreen() {
        return screen;
    }

    public String getSeatLabel() {
        return seatLabel;
    }
}
