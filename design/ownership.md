# Service ownership and call graph

## Why these boundaries (and not others)

Boundaries are drawn on **who is the single writer of a fact**, not on team size or
tech stack. Three nouns own state that nothing else may write directly:

- **Identity** owns *who is asking* (users, credentials, roles).
- **Catalog** owns *what exists to be booked* (movies, theaters, screens, shows,
  seat definitions). Catalog data changes rarely (managers create shows) and is
  read constantly (every browse, every hold).
- **Booking** owns *what has been claimed* (holds, bookings, payment attempts,
  webhook receipts). This is the only service allowed to mutate seat state for a
  show, and the only service with a write path that must be linearizable per
  seat.

A four-way split (identity/catalog/booking/payment) instead of a monolith
exists specifically so that:

1. Catalog's read-heavy, rarely-written data can scale/cache independently of
   Booking's low-latency, high-contention write path.
2. Booking's correctness-critical transaction boundary does not also carry
   Catalog's schema churn (new movie fields, etc.) or Identity's auth changes.
3. Payment is quarantined as *untrusted-by-default* — it is a service we would
   replace with a real PSP later; nothing else should assume its internal shape.

**Non-goal:** we do not split further (e.g. separate "seat-hold" and
"booking-history" services) at this scale. That split would add network hops
with no independent scaling or ownership benefit yet — YAGNI at 3 theaters /
~150 seats.

## Ownership table

| Service | Owns (writes) | Reads from others via |
|---|---|---|
| Identity | `users`, roles, (optional) refresh/session records | nothing — no outbound dependency on other services |
| Catalog | `movies`, `theaters`, `screens`, `seat_definitions`, `shows` | nothing — no outbound dependency |
| Booking | `seat_holds`, `bookings`, `booking_seats`, `payment_attempts`, `webhook_receipts`; also owns Redis hold keys | Catalog (gRPC, show/seat existence + price), Identity (JWT validation only, no DB read), Mock Payment (HTTP) |
| Mock Payment | its own simulated `payment_attempts`-equivalent state (in-memory or its own tiny DB) | nothing — calls back into Booking via signed webhook |

No service reads another service's tables directly — not even for
"just a quick join." Every cross-service fact is fetched through that
owner's public interface (REST) or internal interface (gRPC), which means each
owner can change its schema without a synchronized multi-service deploy.

## Call graph

```text
Client
  │ REST (JSON, JWT bearer)
  ▼
API Gateway  ── correlation ID injection, coarse auth (token present + well-formed)
  ├──────────────► Identity Service   ──► identity_db   (register, login, refresh)
  ├──────────────► Catalog Service    ──► catalog_db    (browse movies/shows/seats)
  └──────────────► Booking Service    ──► booking_db    (hold, checkout, history)
                       │
                       │  gRPC (internal only, not exposed through gateway)
                       ├────────────────► Catalog Service   (GetShow, GetSeatDefinitions)
                       │
                       │  HTTP (internal only)
                       └────────────────► Mock Payment Service
                                                │
                                                │  signed webhook (HMAC-SHA-256)
                                                └────────────────► Booking Service
                                                                       │
                                                                       │ background, after commit
                                                                       └──► Notification worker ──► Mailpit

All services  ── OTLP ──► Jaeger
Booking       ── atomic hold ops ──► Redis
```

## Why each hop is the protocol it is

| Hop | Protocol | Reasoning |
|---|---|---|
| Client → Gateway → {Identity, Catalog, Booking} | REST/JSON | Public client contract; JSON is the lowest-friction format for a browser/curl client; no internal detail leaks through it. |
| Booking → Catalog | gRPC/Protobuf | Internal, called on the hot path (every hold), needs a typed contract so field renames fail the build instead of failing at 2am; low serialization overhead matters here because it's on the synchronous critical path of a user-facing hold request. |
| Booking → Mock Payment | HTTP/JSON | Payment is meant to be swappable for a real PSP later; PSPs speak HTTP/JSON, not gRPC, so keeping this hop REST-shaped is realistic and keeps the compensation logic (see `design/sequence-diagrams.md`) protocol-agnostic. |
| Mock Payment → Booking | Signed HTTP webhook | Mirrors how real payment providers deliver async results; forces us to build idempotent webhook handling and signature verification instead of trusting a synchronous return value. |

## What would change at production scale (understand, not build)

- Catalog would get read replicas / a cache in front of it; Booking would not
  (holds must read fresh state).
- Gateway would move from a single process to a managed API gateway / service
  mesh ingress with per-route rate limiting.
- Identity's JWT signing key would live in a KMS-backed signer, not a
  config-provided symmetric key, and rotate on a schedule.
- Booking's Postgres would eventually need per-show partitioning if seat
  volume grew past a single primary's write throughput.
