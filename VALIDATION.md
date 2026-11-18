# Validation record

Updated 2026-11-18.

## Executed locally

- TypeScript strict compilation: passed using the installed TypeScript compiler.
- Six Node frontend tests: passed, with individual cases reported (escaping, cross-zone dates, leap-year/Monday calendar layout, date arithmetic, DST fold offsets and invalid zones).
- Docker Compose configuration: `docker compose config --quiet` passed.
- HTTP smoke script JavaScript syntax: passed.

## Prepared, not executed

- Fifteen JUnit unit tests: slot calculations/buffers/horizon/notice, DST gaps/folds/elapsed durations, signed/expiring cookies, CSRF/origin protection, management tokens, JSON validation and calendar escaping/UTF-8 folding.
- Ten JUnit PostgreSQL integration tests: competing reservations, direct-write exclusion constraint, touching ranges, idempotency, cancellation release, atomic/replayed rescheduling, private ownership, blocks/overrides, completion buffers and restricted database permissions.
- HTTP smoke test: readiness, staff authentication, catalogue privacy, two competing requests, replay/conflicting request keys, management token checks, calendar export, atomic reschedule and release.
- Docker/Maven build, real PostgreSQL startup and browser interaction/visual review.

The workspace has no JDK/Maven, cannot access the Docker daemon, and shell network DNS is unavailable for dependency/tool downloads. No Java build or integration success is claimed. GitHub Actions is prepared to run the complete verification after this project is published; no Slotline workflow has run yet.
