# Slotline

Appointment booking for a small consulting team. Guests choose a service, person and available time; staff manage appointments, recurring hours, day exceptions and private time blocks.

**Java 21 · PostgreSQL 16 · TypeScript · Docker Compose**

Personal portfolio project. The seeded people and services are fictional. The application starts without appointments: bookings and workspace counts come from PostgreSQL.

## Run

```bash
cp .env.example .env
docker compose up --build -d --wait
```

Open **http://localhost:8092**. Choose **Staff workspace** and enter `slotline-local-admin` (the default `ADMIN_TOKEN`). First build downloads Maven/npm dependencies and base images. No cloud accounts or paid services are needed.

Stop with `docker compose down`. The named database volume preserves appointments. For a disposable development reset, `docker compose down -v` deletes the database. Initialization scripts run only on an empty volume; editing `.env` passwords does not update an existing database role.

## Try it

1. Choose a service and provider, then a future weekday. Calendar dates use the provider’s zone; slot labels use the visitor’s selected zone, with explicit UTC offsets.
2. Book using fictional contact details. Save the private management link shown on confirmation. Open it in another browser session to reschedule, cancel or download an `.ics` event. **No confirmation email is sent.**
3. Open the staff workspace. Select a provider and week to inspect appointments and history. Add a lunch block or closed date, then check that public availability changes.
4. Try booking the same occupied time from a second tab. The server returns a conflict. A failed reschedule keeps the original reservation.
5. Mark a finished appointment completed or no-show. Staff can override the guest change cutoff, but cannot move a reservation into an occupied time.

## What it handles

- Three seeded service durations, provider assignments, service-specific lead time, booking horizon, after-session buffers and change cutoffs.
- Weekly availability, closed days, custom opening hours and private blocks. Existing bookings survive availability edits. Blocks cannot overlap existing reservations or their buffers.
- UTC appointment instants with IANA provider/visitor zones. Nonexistent spring clock times are omitted; autumn repeated times are distinct instants. Service duration means elapsed minutes. Overnight hours are outside this MVP.
- Database exclusion constraint over `[starts_at, occupied_until)` for every noncancelled booking. Transactions lock the provider while making booking/availability changes. Touching occupied intervals are allowed.
- Idempotent create/reschedule requests. A key reused with different details returns a conflict. Retrying an older successful request returns the booking’s current state, rather than recreating or reversing it.
- Rescheduling updates the existing reservation atomically. Service duration, buffer and cancellation policy are snapshotted at booking; rescheduling uses current lead time/horizon and the original duration/buffer.
- Private management links, signed staff sessions, audit events and stable calendar UIDs/revision numbers.
- Responsive booking and staff interfaces; no frontend runtime framework or third-party scripts.

## Verify

```bash
docker compose --profile test run --build --rm tests
node scripts/smoke.mjs
```

Start `db` and `api` before the HTTP smoke test. Use Node 20+; `APP_URL` and `ADMIN_TOKEN` can override the test defaults. Smoke fixtures use `example.test` addresses and cancel their reservations afterward, leaving audit history. Run against a disposable local database.

The Docker build runs six frontend tests and fifteen Java unit tests. The `tests` Compose service additionally runs ten real PostgreSQL integration tests. The workflow in `.github/workflows/ci.yml` builds the application, runs those tests, and exercises concurrent HTTP booking and the lifecycle. A plain `mvn verify` without `TEST_DATABASE_URL` skips database tests; it does not prove integration behavior.

**Current evidence:** TypeScript compilation, six frontend tests, script syntax and Compose configuration pass locally. Java compilation, database integration, live HTTP behavior and browser visual checks have not run in this restricted workspace. See [VALIDATION.md](VALIDATION.md).

## Layout

| Path | Purpose |
| --- | --- |
| `src/main/java/dev/slotline/SlotEngine.java` | Pure time-zone/availability calculations |
| `BookingStore.java` in the same directory | Transactional booking and staff operations |
| `src/main/resources/db/` | Separate application role, schema, constraints and fictional catalogue |
| `web/src/` | Typed booking and staff interface |
| `src/test/`, `web/tests/`, `scripts/smoke.mjs` | Unit, real-database and HTTP checks |
| `docs/ARCHITECTURE.md` | Invariants, API and tradeoffs |

## Deployment scope

Compose binds only to `127.0.0.1`; PostgreSQL has no host port. The runtime runs as an unprivileged user with a read-only filesystem. For a public deployment, configure unique admin/database/management secrets, HTTPS with `COOKIE_SECURE=true`, a reverse proxy, request throttling, backups and retention appropriate to the installation. Preserve `MANAGEMENT_SECRET` across restarts: it reconstructs the original management token on a create retry. Rotating it affects replayed create responses; existing tokens still verify against their stored hashes.

Staff authentication is a single shared installation token, not per-user accounts or tenant isolation. There is no email/SMS, payment, video-call provisioning, external calendar synchronization or waitlist. The provider/service catalogue is seeded in SQL; its CRUD UI is outside this MVP. Downloading a revised `.ics` file does not automatically synchronize an already imported external calendar event.
