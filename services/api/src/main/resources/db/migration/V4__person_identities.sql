CREATE TABLE person_identities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id UUID NOT NULL REFERENCES people (id) ON DELETE CASCADE,
    external_subject TEXT NOT NULL UNIQUE,
    label TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_person_identities_person ON person_identities (person_id);

INSERT INTO person_identities (person_id, external_subject, label)
SELECT id, external_subject, 'Primary'
FROM people;
