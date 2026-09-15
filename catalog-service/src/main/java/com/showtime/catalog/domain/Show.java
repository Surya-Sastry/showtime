package com.showtime.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A single screening of a movie on a screen. Two shows can never overlap on
 * the same screen — enforced primarily by the database's
 * {@code EXCLUDE USING gist} constraint (see the V1 migration); the
 * constructor check below exists only to fail fast with a readable message
 * before hitting the database, not as the actual guarantee.
 */
@Entity
@Table(name = "shows")
public class Show {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "screen_id", nullable = false)
    private Screen screen;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "ticket_price_cents", nullable = false)
    private int ticketPriceCents;

    protected Show() {
        // JPA
    }

    public Show(Movie movie, Screen screen, Instant startsAt, Instant endsAt, int ticketPriceCents) {
        if (movie == null || screen == null) {
            throw new IllegalArgumentException("movie and screen must not be null");
        }
        if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("endsAt must be after startsAt");
        }
        if (ticketPriceCents <= 0) {
            throw new IllegalArgumentException("ticketPriceCents must be positive");
        }
        this.movie = movie;
        this.screen = screen;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.ticketPriceCents = ticketPriceCents;
    }

    public UUID getId() {
        return id;
    }

    public Movie getMovie() {
        return movie;
    }

    public Screen getScreen() {
        return screen;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public int getTicketPriceCents() {
        return ticketPriceCents;
    }
}
