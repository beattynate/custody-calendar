ALTER TABLE events
    ADD COLUMN approval_status TEXT NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN created_by UUID REFERENCES people (id),
    ADD COLUMN change_requested_by UUID REFERENCES people (id),
    ADD COLUMN pending_change JSONB,
    ADD COLUMN decided_by UUID REFERENCES people (id),
    ADD COLUMN decided_at TIMESTAMPTZ;

ALTER TABLE schedule_rules
    ADD COLUMN pending_change JSONB,
    ADD COLUMN change_requested_by UUID REFERENCES people (id);

CREATE TABLE audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id UUID NOT NULL REFERENCES cases (id) ON DELETE CASCADE,
    actor_person_id UUID REFERENCES people (id),
    action TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id UUID,
    details JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_case_created ON audit_log (case_id, created_at DESC);
