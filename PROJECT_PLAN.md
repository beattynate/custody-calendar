# Custody Calendar App - Build Plan (MVP + Solver)

## Goal
Build a small, private custody calendar app (web + Android) backed by Postgres and a Spring Boot REST API.
Core pain point: accommodating vacations/holidays that interrupt a baseline 2-2-3 schedule while respecting constraints (no 1-day stays, school-night weighting, parity alignment, locked plans).

## Non-negotiables
- Deterministic scheduling solver (no ML required). Same inputs => same outputs.
- Auditability: explain why a proposal was chosen (score breakdown + patch operations).
- "Locked" plans cannot be violated (e.g., Christmas odd years, pre-approved vacations).
- Minimum consecutive days with a parent: default minRunDays=2.
- Return-to-baseline preference (odd/even weekend parity alignment).
- Avoid interfering with locked plans or near-term plans.
- Web + Android should consume the same API contract (OpenAPI).

## Proposed Architecture
Monorepo:
- /services/api  -> Spring Boot 3 REST API
- /apps/web      -> Web app (Next.js or Vite/React)
- /apps/android  -> Android app (Kotlin + Compose)
- /packages/shared -> (optional) shared OpenAPI client generation outputs
- /db            -> migrations (Flyway)

Backend stack:
- Spring Boot 3, Spring Web, Spring Security (JWT verification)
- Postgres
- Flyway migrations
- springdoc-openapi for OpenAPI generation
- Testcontainers for integration tests

Auth:
- Use external identity provider issuing JWTs (Firebase Auth / Clerk / Auth0 / Cognito).
- API verifies JWT and authorizes by case membership.
(Do NOT implement password hashing/login flows in API for MVP.)

## Core Domain Concepts
- Case: a custody arrangement group.
- Parents: members with roles.
- Baseline schedule: 2-2-3 pattern defined by an anchor date and parent assignment.
- Locked events: immutable constraints (holidays rules, pre-planned vacations, etc.).
- School day types: SCHOOL vs BREAK vs WEEKEND/HOLIDAY for scoring transitions.
- Solver: given baseline + locked + new event request + preferences -> returns ranked schedule proposals.
- Ledger: explainable owed-days accounting entries tied to events/proposals.

## Minimal Data Model (Postgres)

### cases
- id (uuid pk)
- name (text)
- timezone (text)

### people
- id (uuid pk)
- display_name (text)

### case_members
- case_id (uuid fk)
- person_id (uuid fk)
- role (text: PARENT, ADMIN)
- PRIMARY KEY (case_id, person_id)

### children
- id (uuid pk)
- case_id (uuid fk)
- name (text)

### schedule_rules
- id (uuid pk)
- case_id (uuid fk)
- type (text: TWO_TWO_THREE)
- anchor_date (date)  # defines parity / cycle start
- parent_a_id (uuid fk people)
- parent_b_id (uuid fk people)
- metadata (jsonb) # who starts the cycle, definitions for "odd weekend", etc.

### school_calendar_days
- case_id (uuid fk)
- date (date)
- day_type (text: SCHOOL, BREAK, WEEKEND, HOLIDAY, IN_SERVICE)
- PRIMARY KEY (case_id, date)

### events
Represents overrides/constraints, including recurring locked holidays.
- id (uuid pk)
- case_id (uuid fk)
- title (text)
- start_date (date, inclusive)
- end_date (date, inclusive)
- event_type (text: VACATION_WITH_KIDS, VACATION_NO_KIDS, HOLIDAY_LOCKED, EXCEPTION_SWAP, etc.)
- applies_to (text: KIDS_ASSIGNMENT, PARENT_UNAVAILABLE)
- parent_id (uuid fk people, nullable)
- locked (boolean)
- recurrence_rule (text nullable) # optional
- notes (text nullable)

### schedule_versions
Stores draft/proposed/accepted schedules.
- id (uuid pk)
- case_id (uuid fk)
- status (text: DRAFT, PROPOSED, ACCEPTED, REJECTED, SUPERSEDED)
- created_by (uuid fk people)
- created_at (timestamptz)
- based_on_version_id (uuid nullable)
- reason (text)
- preferences (jsonb)

### schedule_days
Materialized per-day assignment for a version (only store horizon window).
- version_id (uuid fk schedule_versions)
- date (date)
- assigned_parent_id (uuid fk people)
- locked_source_event_id (uuid fk events, nullable)
- derived_from (text: BASELINE, OVERRIDE, SOLVER)
- PRIMARY KEY (version_id, date)

### ledger_entries
Explainable owed-days accounting.
- id (uuid pk)
- case_id (uuid fk)
- date (date)
- from_parent_id (uuid fk people)
- to_parent_id (uuid fk people)
- amount_days (int)
- reason_type (text: VACATION_TAKE, MAKEUP_GIVE, HOLIDAY_RULE, etc.)
- event_id (uuid fk events nullable)
- version_id (uuid fk schedule_versions nullable)
- notes (text nullable)

Indexes:
- events(case_id, start_date, end_date)
- schedule_days(version_id, date)
- ledger_entries(case_id, date)

## Solver: Deterministic Constraint + Optimization
This is NOT ML. Implement a deterministic solver with:
1) Generate baseline schedule over a horizon from schedule_rules (2-2-3).
2) Apply locked events as hard overrides (cannot violate).
3) Apply new requested event (vacation/holiday) as an override (may create issues).
4) Repair by generating candidate "patches" (contiguous block swaps / boundary shifts / compensation insertions).
5) Validate hard constraints:
   - locked days unchanged
   - every day assigned to exactly one parent
   - minRunDays >= 2 (no 1-day stays)
   - no overlap / conflicts
6) Score candidates using weighted penalties (soft constraints):
   - transitions count
   - school-night transitions weighted higher
   - parity drift from baseline (odd/even weekend alignment)
   - proximity to locked plans
   - owed-days imbalance / speed of payback
7) Return top 3-5 distinct options with score breakdown + patch operations + ledger impact.

Representation:
- Use per-day assignment for scoring.
- Derive runs (contiguous blocks) for constraint checking and moves.

Move set (initial):
- Shift a handoff boundary by +/- 1 day if still minRunDays>=2.
- Swap ownership of a contiguous block (2-7 days).
- Compensation insertion before/after vacation within compensationWindowDays.
- Weekend-preserving swap (treat Fri-Sun as atomic where possible).

Parity:
- Define parity via schedule_rules.anchor_date.
- Baseline defines which parent "owns" each weekend parity; penalize drift; reward return-to-parity within Y weeks.

## API Contract (Spring Boot)

### AuthZ
All endpoints require JWT; authorize by case membership.

### Core endpoints (MVP)
- POST /cases
- GET /cases/{caseId}

- POST /cases/{caseId}/people (add member)
- GET /cases/{caseId}/people

- POST /cases/{caseId}/children
- GET /cases/{caseId}/children

- PUT /cases/{caseId}/schedule-rule
- GET /cases/{caseId}/schedule-rule

- POST /cases/{caseId}/events
- GET /cases/{caseId}/events?from=YYYY-MM-DD&to=YYYY-MM-DD

- POST /cases/{caseId}/schedule/solve
Request:
{
  "baseVersionId": "...(optional: current accepted)",
  "horizonStart": "YYYY-MM-DD",
  "horizonEnd": "YYYY-MM-DD",
  "newEvent": { ...same shape as events... },
  "constraints": { "minRunDays": 2, "compensationWindowDays": 60, "respectLocked": true },
  "weights": {
    "transitionPenalty": 50,
    "schoolNightTransitionPenalty": 30,
    "parityDriftPenalty": 40,
    "lockedProximityPenalty": 100,
    "owedImbalancePenalty": 5
  }
}
Response:
{
  "options": [
    {
      "optionId": "A",
      "scoreTotal": 1234,
      "scoreBreakdown": { ... },
      "patchOperations": [ "Swap block 2026-06-18..2026-06-20", ... ],
      "changedDays": [ { "date":"...", "fromParent":"...", "toParent":"..." } ],
      "ledgerImpact": [ { "fromParent":"...", "toParent":"...", "amountDays": 2, "reason":"MAKEUP_GIVE" } ]
    }
  ]
}

- POST /cases/{caseId}/schedule-versions/{versionId}/accept
Creates an ACCEPTED version and materializes schedule_days for horizon.

- GET /cases/{caseId}/schedule?from=YYYY-MM-DD&to=YYYY-MM-DD
Returns assignments and markers for locked/events.

## Implementation Plan (Codex tasks)

### Task 1: Repo scaffold + tooling
- Create monorepo structure: /services/api, /apps/web, /apps/android, /db
- Add root README with local dev commands.
- Add docker-compose for local Postgres.
- Configure Flyway in API with baseline migration.
- Add springdoc-openapi and ensure OpenAPI JSON is served.

### Task 2: API domain + DB migrations
- Implement migrations for all tables above.
- Add JPA entities or jOOQ schema (choose JPA for speed unless instructed otherwise).
- Add repositories and basic service layer.

### Task 3: AuthZ skeleton
- Add JWT verification (resource server style).
- Add "case membership" authorization helper.
- Add integration test scaffolding with Testcontainers.

### Task 4: CRUD endpoints for MVP
- Implement cases/people/children/schedule-rule/events endpoints.
- Add request validation + error responses.

### Task 5: Baseline schedule generator (2-2-3)
- Implement ScheduleGenerator that returns per-day parent assignment for a given horizon based on schedule_rules.
- Implement run-derivation helper to compute contiguous runs.

### Task 6: Locked event application
- Implement function to apply locked events as overrides (splitting runs).
- Mark schedule_days locked_source_event_id where applicable.

### Task 7: Solver v1 (generate-and-rank)
- Implement:
  - ConstraintValidator (minRunDays, locked immutability, coverage)
  - MoveGenerator (moves listed above)
  - Scorer (weighted penalties)
  - SolverService that:
    - builds baseline
    - applies locked
    - applies newEvent
    - generates candidates
    - validates + scores
    - returns top N diverse options + score breakdown + patchOperations + ledgerImpact

### Task 8: Schedule versions
- Implement schedule_versions + schedule_days materialization for accepted proposal.
- Implement accept endpoint.

### Task 9: Web MVP
- Minimal UI:
  - login placeholder (token input ok for MVP)
  - month view calendar
  - list events + add event form
  - "Solve" screen showing 3-5 options and diffs

### Task 10: Android MVP
- Compose screens:
  - month view
  - event list/add
  - solver options view
- Networking via Retrofit.
- Basic caching optional.

## Definition of Done (MVP)
- Local dev: `docker compose up` + API runs + sample seed data.
- API endpoints functional.
- Solver returns options for a vacation override with minRunDays >= 2.
- Web and Android can display calendar and run solve to view options.
- Option can be accepted and schedule materialized.
- Tests: at least generator + validator + one solver scenario integration test.

## Notes
- Start with a fixed horizon (e.g., 180 days) for proposals.
- Ensure privacy: access restricted by case membership; no public endpoints.

## Future Enhancements (Post-MVP)
- Bulk input school calendar days:
  - CSV/iCal import
  - Date-range classification (e.g., “School year start/end”)
  - Day-of-week auto-tagging (e.g., Mon-Thu = SCHOOL)
- Expanded baseline schedule templates:
  - Week-on/week-off
  - 2-2-5-5
  - Custom pattern builder
- Recurring rules:
  - Holidays by rule (e.g., “Christmas odd years = Parent A”)
  - School breaks/holidays generated from rule templates
- Solver improvements:
  - Use `school_calendar_days` explicitly for scoring instead of weekday heuristics
  - Option diversity controls and configurability of move sets
  - Make long-run penalty threshold configurable (currently hardcoded as runs longer than 3 days)
