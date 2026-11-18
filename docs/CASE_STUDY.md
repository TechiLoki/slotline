# Slotline — appointment booking with protected availability

**Role:** Backend and full-stack developer. **Stack:** Java 21, PostgreSQL, TypeScript, Docker.

Small service teams need clients to book available time while keeping control of staff hours. A plain calendar form misses competing reservations, daylight-saving changes, after-session buffers and safe changes to existing bookings.

Slotline provides a guest booking flow, private self-service management and a staff workspace. Availability combines recurring hours, date exceptions, private blocks, existing reservations and service policies. Atomic rescheduling keeps the original reservation if the requested replacement is unavailable. Request keys protect repeated submissions; a PostgreSQL exclusion constraint guards occupied intervals independently of the browser.

The implementation separates pure time calculations from transactional storage. Tests are included for DST gaps/folds, competing booking attempts, direct-write constraints, retry behavior, ownership and lifecycle transitions. The full GitHub Actions run passed the Docker build, six frontend tests, 25 Java tests including ten PostgreSQL integration tests, and live HTTP lifecycle checks. Browser interaction/visual review remains pending; see `VALIDATION.md` for evidence and limits.

This is a personal MVP with fictional catalogue data. It does not send reminders, process payments or synchronize external calendars. The completed work demonstrates relational integrity, Java time handling, API design and a typed web interface; no client deployment or business results are claimed.
