CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE cases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    timezone TEXT NOT NULL
);

CREATE TABLE people (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_subject TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL
);

CREATE TABLE case_members (
    case_id UUID NOT NULL REFERENCES cases (id) ON DELETE CASCADE,
    person_id UUID NOT NULL REFERENCES people (id) ON DELETE CASCADE,
    role TEXT NOT NULL,
    PRIMARY KEY (case_id, person_id)
);

CREATE TABLE children (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id UUID NOT NULL REFERENCES cases (id) ON DELETE CASCADE,
    name TEXT NOT NULL
);

CREATE TABLE schedule_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id UUID NOT NULL REFERENCES cases (id) ON DELETE CASCADE,
    type TEXT NOT NULL,
    anchor_date DATE NOT NULL,
    parent_a_id UUID NOT NULL REFERENCES people (id),
    parent_b_id UUID NOT NULL REFERENCES people (id),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE TABLE school_calendar_days (
    case_id UUID NOT NULL REFERENCES cases (id) ON DELETE CASCADE,
    date DATE NOT NULL,
    day_type TEXT NOT NULL,
    PRIMARY KEY (case_id, date)
);

CREATE TABLE events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id UUID NOT NULL REFERENCES cases (id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    event_type TEXT NOT NULL,
    applies_to TEXT NOT NULL,
    parent_id UUID REFERENCES people (id),
    locked BOOLEAN NOT NULL DEFAULT FALSE,
    recurrence_rule TEXT,
    notes TEXT
);

CREATE TABLE schedule_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id UUID NOT NULL REFERENCES cases (id) ON DELETE CASCADE,
    status TEXT NOT NULL,
    created_by UUID NOT NULL REFERENCES people (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    based_on_version_id UUID REFERENCES schedule_versions (id),
    reason TEXT NOT NULL,
    preferences JSONB
);

CREATE TABLE schedule_days (
    version_id UUID NOT NULL REFERENCES schedule_versions (id) ON DELETE CASCADE,
    date DATE NOT NULL,
    assigned_parent_id UUID NOT NULL REFERENCES people (id),
    locked_source_event_id UUID REFERENCES events (id),
    derived_from TEXT NOT NULL,
    PRIMARY KEY (version_id, date)
);

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id UUID NOT NULL REFERENCES cases (id) ON DELETE CASCADE,
    date DATE NOT NULL,
    from_parent_id UUID NOT NULL REFERENCES people (id),
    to_parent_id UUID NOT NULL REFERENCES people (id),
    amount_days INT NOT NULL,
    reason_type TEXT NOT NULL,
    event_id UUID REFERENCES events (id),
    version_id UUID REFERENCES schedule_versions (id),
    notes TEXT
);

CREATE INDEX idx_events_case_date_range ON events (case_id, start_date, end_date);
CREATE INDEX idx_schedule_days_version_date ON schedule_days (version_id, date);
CREATE INDEX idx_ledger_entries_case_date ON ledger_entries (case_id, date);
