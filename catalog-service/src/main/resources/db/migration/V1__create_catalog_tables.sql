CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE movies (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title           TEXT NOT NULL,
    runtime_minutes INT NOT NULL CHECK (runtime_minutes > 0)
);

CREATE TABLE theaters (
    id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL
);

CREATE TABLE screens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    theater_id UUID NOT NULL REFERENCES theaters(id),
    name       TEXT NOT NULL,
    UNIQUE (theater_id, name)
);

CREATE TABLE seat_definitions (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    screen_id  UUID NOT NULL REFERENCES screens(id),
    seat_label TEXT NOT NULL,
    UNIQUE (screen_id, seat_label)
);

CREATE TABLE shows (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    movie_id           UUID NOT NULL REFERENCES movies(id),
    screen_id          UUID NOT NULL REFERENCES screens(id),
    starts_at          TIMESTAMPTZ NOT NULL,
    ends_at            TIMESTAMPTZ NOT NULL,
    ticket_price_cents INT NOT NULL CHECK (ticket_price_cents > 0),
    CHECK (ends_at > starts_at),
    -- No two shows may overlap on the same screen. This is the database
    -- enforcing "prevent invalid show schedules for a screen" — the service
    -- layer must not be the only thing standing between two managers and a
    -- double-booked screen.
    EXCLUDE USING gist (screen_id WITH =, tstzrange(starts_at, ends_at) WITH &&)
);

CREATE INDEX idx_shows_movie ON shows (movie_id);
