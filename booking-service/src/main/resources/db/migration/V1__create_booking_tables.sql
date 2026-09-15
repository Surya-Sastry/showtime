CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- seat_holds/hold_seats are the durable audit trail for holds. Redis is the
-- fast-path source of truth for "is this seat held right now" (with TTL
-- expiry); these tables exist so a hold's history survives a Redis flush
-- and so booking creation can verify hold ownership without trusting the
-- client's word for it.
CREATE TABLE seat_holds (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    show_id    UUID NOT NULL,
    user_id    UUID NOT NULL,
    status     TEXT NOT NULL CHECK (status IN ('ACTIVE', 'RELEASED', 'EXPIRED', 'CONSUMED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_seat_holds_user ON seat_holds (user_id);

CREATE TABLE hold_seats (
    id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hold_id UUID NOT NULL REFERENCES seat_holds(id),
    seat_id UUID NOT NULL,
    UNIQUE (hold_id, seat_id)
);

CREATE TABLE bookings (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID NOT NULL,
    show_id            UUID NOT NULL,
    -- UNIQUE: a hold can produce at most one booking. This is what makes
    -- concurrent retries/double-submits of "create booking from this hold"
    -- idempotent instead of creating duplicate bookings for one hold.
    hold_id            UUID NOT NULL UNIQUE REFERENCES seat_holds(id),
    status             TEXT NOT NULL CHECK (
        status IN ('PENDING_PAYMENT', 'CONFIRMED', 'CANCELLED', 'EXPIRED', 'REQUIRES_REVIEW')),
    idempotency_key    UUID NOT NULL UNIQUE,
    ticket_code        TEXT,
    total_price_cents  INT NOT NULL CHECK (total_price_cents > 0),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_bookings_user ON bookings (user_id);

-- The seats a booking is trying to buy, recorded before payment resolves.
-- Deliberately has no cross-booking uniqueness: several PENDING_PAYMENT
-- bookings may (briefly, before one fails) name the same seat. Only
-- booking_seats below is the double-booking authority.
CREATE TABLE booking_requested_seats (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID NOT NULL REFERENCES bookings(id),
    seat_id    UUID NOT NULL,
    UNIQUE (booking_id, seat_id)
);

-- The single source of truth for "is this seat confirmed for this show."
-- A row is inserted here ONLY inside the atomic confirmation transaction,
-- and the UNIQUE(show_id, seat_id) constraint is what actually makes
-- double-booking impossible — not application logic, not Redis.
CREATE TABLE booking_seats (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id   UUID NOT NULL REFERENCES bookings(id),
    show_id      UUID NOT NULL,
    seat_id      UUID NOT NULL,
    confirmed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (show_id, seat_id)
);

CREATE TABLE payment_attempts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id          UUID NOT NULL REFERENCES bookings(id),
    provider_payment_id TEXT NOT NULL,
    status              TEXT NOT NULL CHECK (status IN ('CREATED', 'SUCCEEDED', 'FAILED')),
    amount_cents        INT NOT NULL CHECK (amount_cents > 0),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_payment_attempts_booking ON payment_attempts (booking_id);

-- One row per processed webhook delivery. The UNIQUE constraint on
-- event_id is what makes "duplicate callback delivered twice" harmless:
-- the second insert fails, and the handler treats that as "already
-- processed, return success without repeating effects."
CREATE TABLE webhook_receipts (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id    TEXT NOT NULL UNIQUE,
    booking_id  UUID NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
