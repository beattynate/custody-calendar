-- DEV ONLY: local convenience seed for stable manual testing.
-- Do not run in production environments.
--
-- Fixed IDs (documented in dev/README.md):
--   Case      = 11111111-1111-1111-1111-111111111111
--   Parent A  = aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa (subject: dev|parent-a)
--   Parent B  = bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb (subject: dev|parent-b)
--   Child     = cccccccc-cccc-cccc-cccc-cccccccccccc
--   Rule      = dddddddd-dddd-dddd-dddd-dddddddddddd

BEGIN;

INSERT INTO cases (id, name, timezone)
VALUES ('11111111-1111-1111-1111-111111111111', 'Local Dev Case', 'America/Denver')
ON CONFLICT (id) DO UPDATE
SET name = EXCLUDED.name,
    timezone = EXCLUDED.timezone;

INSERT INTO people (id, external_subject, display_name)
VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'dev|parent-a', 'Parent A'),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'dev|parent-b', 'Parent B')
ON CONFLICT (id) DO UPDATE
SET external_subject = EXCLUDED.external_subject,
    display_name = EXCLUDED.display_name;

INSERT INTO case_members (case_id, person_id, role)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'ADMIN'),
    ('11111111-1111-1111-1111-111111111111', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'PARENT')
ON CONFLICT (case_id, person_id) DO UPDATE
SET role = EXCLUDED.role;

INSERT INTO children (id, case_id, name)
VALUES ('cccccccc-cccc-cccc-cccc-cccccccccccc', '11111111-1111-1111-1111-111111111111', 'Child 1')
ON CONFLICT (id) DO UPDATE
SET case_id = EXCLUDED.case_id,
    name = EXCLUDED.name;

INSERT INTO schedule_rules (id, case_id, type, anchor_date, parent_a_id, parent_b_id, metadata)
VALUES (
    'dddddddd-dddd-dddd-dddd-dddddddddddd',
    '11111111-1111-1111-1111-111111111111',
    'TWO_TWO_THREE',
    DATE '2026-01-05',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
    '{"anchorParent":"A"}'::jsonb
)
ON CONFLICT (id) DO UPDATE
SET case_id = EXCLUDED.case_id,
    type = EXCLUDED.type,
    anchor_date = EXCLUDED.anchor_date,
    parent_a_id = EXCLUDED.parent_a_id,
    parent_b_id = EXCLUDED.parent_b_id,
    metadata = EXCLUDED.metadata;

COMMIT;
