# Validation record

Updated 2026-11-18.

## Executed locally

- TypeScript strict compilation: passed using the installed TypeScript compiler.
- Six Node frontend tests: passed, with individual cases reported (escaping, cross-zone dates, leap-year/Monday calendar layout, date arithmetic, DST fold offsets and invalid zones).
- Docker Compose configuration: `docker compose config --quiet` passed.
- HTTP smoke script JavaScript syntax: passed.

## Executed in GitHub Actions

- Fifteen JUnit unit tests: slot calculations/buffers/horizon/notice, DST gaps/folds/elapsed durations, signed/expiring cookies, CSRF/origin protection, management tokens, JSON validation and calendar escaping/UTF-8 folding.
- Ten JUnit PostgreSQL integration tests: competing reservations, direct-write exclusion constraint, touching ranges, idempotency, cancellation release, atomic/replayed rescheduling, private ownership, blocks/overrides, completion buffers and restricted database permissions.
- HTTP smoke test: readiness, staff authentication, catalogue privacy, two competing requests, replay/conflicting request keys, management token checks, calendar export, atomic reschedule and release.
- Docker/Maven build, Java 21 compilation, real PostgreSQL startup and application readiness: passed.

Passing run: https://github.com/TechiLoki/slotline/actions/runs/44328062774

The integration test phase reports 25 Java tests with zero failures/errors/skips (15 unit and 10 real-database tests). The frontend build reports six tests passed. The live HTTP lifecycle also passed. The first run caught whitespace-only JSON acceptance; the parser was corrected before this passing run.

## Remaining limits

- Browser interaction and visual review have not been executed.
- No public deployment, email/SMS, payments or external calendar synchronization has been tested or claimed.

Local execution remains restricted by the lack of JDK/Maven and Docker daemon access. GitHub Actions supplied the actual Java/container/PostgreSQL execution evidence above; local checks alone are not used to claim integration success.
