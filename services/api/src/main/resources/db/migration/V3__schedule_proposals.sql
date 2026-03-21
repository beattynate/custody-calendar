CREATE TABLE schedule_proposals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id UUID NOT NULL REFERENCES cases (id) ON DELETE CASCADE,
    created_by UUID NOT NULL REFERENCES people (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    status TEXT NOT NULL,
    option_id TEXT NOT NULL,
    solve_command_json JSONB NOT NULL,
    option_snapshot_json JSONB NOT NULL,
    reason TEXT,
    accepted_version_id UUID REFERENCES schedule_versions (id)
);

CREATE TABLE schedule_proposal_approvals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    proposal_id UUID NOT NULL REFERENCES schedule_proposals (id) ON DELETE CASCADE,
    person_id UUID NOT NULL REFERENCES people (id),
    decision TEXT,
    decided_at TIMESTAMPTZ,
    comment TEXT,
    UNIQUE (proposal_id, person_id)
);

CREATE INDEX idx_schedule_proposals_case_created_at ON schedule_proposals (case_id, created_at DESC);
CREATE INDEX idx_schedule_proposal_approvals_proposal ON schedule_proposal_approvals (proposal_id);
