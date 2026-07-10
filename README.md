# Custody Calendar Monorepo

## Structure
- `services/api`: Spring Boot API
- `apps/web`: Web app placeholder
- `apps/android`: Android app placeholder
- `db`: Shared database assets (if needed)

## Local development
1. Start Postgres:
   - `docker compose up -d postgres`
2. Run API from `services/api`:
   - `./mvnw spring-boot:run` (Git Bash)
   - `mvnw.cmd spring-boot:run` (PowerShell)
3. OpenAPI JSON:
   - `http://localhost:8080/v3/api-docs`
4. Optional stable local test seed (fixed case/member IDs):
   - See `dev/README.md`

## Linked logins
A person can have multiple sign-in identities (`person_identities`), so e.g. a parent and their
partner can each use their own Clerk account while acting as the same parent in every case:

1. The partner signs in on their own device and copies "My subject" from Settings.
2. The parent opens Settings -> Linked Logins and links that subject to themselves.
3. The partner now sees the same cases, calendar, and approvals as the parent.

Identities can only be linked to or removed from yourself (`/api/v1/me/identities`); a login that
already belongs to another person is rejected. Approvals made by a linked login count as the
person they are linked to — the two-parent approval rule still requires the other parent.

## Consent & audit
- Locked events (hard schedule overrides) require the other schedule-rule parent's approval to
  create, edit, or delete. Non-locked events apply immediately.
- Schedule rule changes require the other parent's approval once a rule exists; initial creation
  applies directly so onboarding is not blocked.
- Every member/event/rule/proposal/schedule action is written to an immutable per-case audit log
  (`GET /cases/{id}/audit`, "Activity" tab in the web app).

## Ledger
Owed-day entries recorded by approved proposals are listed at `GET /cases/{id}/ledger`, with
netted balances at `/ledger/balance` ("Ledger" tab in the web app).

## Calendar extras
- `GET /cases/{id}/schedule` falls back to the generated baseline before any version is accepted.
- `GET /cases/{id}/schedule.ics?from&to` exports custody runs as an iCalendar file ("Export .ics"
  button), importable into Google/Apple/Outlook calendars.
- The web app is installable to a phone home screen (PWA manifest + icons).

## Notes
- Flyway migrations live in `services/api/src/main/resources/db/migration`.
- JWT verification is configured as a resource server skeleton and expects a JWK set URI.
- Local-only dev helpers (stable seed data, dev JWT minting) are documented in `dev/README.md`.
