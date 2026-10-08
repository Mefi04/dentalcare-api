--liquibase formatted sql

--changeset dentalcare:039-create-medical-history-questionnaire-workflow
CREATE TABLE medical_history_templates (
    id UUID PRIMARY KEY,
    code VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_mh_templates_code CHECK (BTRIM(code) <> ''),
    CONSTRAINT chk_mh_templates_name CHECK (BTRIM(name) <> '')
);

CREATE TABLE medical_history_template_versions (
    id UUID PRIMARY KEY,
    template_id UUID NOT NULL REFERENCES medical_history_templates(id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL CHECK (version_number > 0),
    title VARCHAR(180) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT','PUBLISHED','RETIRED')),
    created_by UUID NOT NULL REFERENCES users(id),
    published_by UUID NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ NULL,
    CONSTRAINT uq_mh_template_version UNIQUE (template_id, version_number),
    CONSTRAINT chk_mh_template_version_title CHECK (BTRIM(title) <> ''),
    CONSTRAINT chk_mh_template_publication CHECK (
        (status = 'DRAFT' AND published_at IS NULL AND published_by IS NULL)
        OR (status IN ('PUBLISHED','RETIRED') AND published_at IS NOT NULL AND published_by IS NOT NULL)
    )
);
CREATE UNIQUE INDEX uq_mh_template_current_published
    ON medical_history_template_versions(template_id) WHERE status = 'PUBLISHED';

CREATE TABLE medical_history_template_sections (
    id UUID PRIMARY KEY,
    template_version_id UUID NOT NULL REFERENCES medical_history_template_versions(id) ON DELETE CASCADE,
    section_key VARCHAR(80) NOT NULL,
    title VARCHAR(180) NOT NULL,
    description VARCHAR(500) NULL,
    display_order INTEGER NOT NULL CHECK (display_order >= 0),
    CONSTRAINT uq_mh_template_section_key UNIQUE (template_version_id, section_key),
    CONSTRAINT uq_mh_template_section_order UNIQUE (template_version_id, display_order),
    CONSTRAINT chk_mh_template_section_key CHECK (BTRIM(section_key) <> ''),
    CONSTRAINT chk_mh_template_section_title CHECK (BTRIM(title) <> '')
);

CREATE TABLE medical_history_template_questions (
    id UUID PRIMARY KEY,
    section_id UUID NOT NULL REFERENCES medical_history_template_sections(id) ON DELETE CASCADE,
    question_key VARCHAR(100) NOT NULL,
    prompt VARCHAR(500) NOT NULL,
    answer_type VARCHAR(30) NOT NULL CHECK (answer_type IN ('YES_NO_UNKNOWN_NA','SINGLE_CHOICE','MULTIPLE_CHOICE','SHORT_TEXT','LONG_TEXT','DATE','NUMBER')),
    display_order INTEGER NOT NULL CHECK (display_order >= 0),
    required BOOLEAN NOT NULL DEFAULT FALSE,
    notes_allowed BOOLEAN NOT NULL DEFAULT FALSE,
    max_length INTEGER NULL CHECK (max_length IS NULL OR max_length BETWEEN 1 AND 4000),
    min_value NUMERIC(15,4) NULL,
    max_value NUMERIC(15,4) NULL,
    choices_json JSONB NULL,
    conditional_note_json JSONB NULL,
    CONSTRAINT uq_mh_template_question_key UNIQUE (section_id, question_key),
    CONSTRAINT uq_mh_template_question_order UNIQUE (section_id, display_order),
    CONSTRAINT chk_mh_template_question_prompt CHECK (BTRIM(prompt) <> ''),
    CONSTRAINT chk_mh_template_question_range CHECK (min_value IS NULL OR max_value IS NULL OR min_value <= max_value)
);

CREATE TABLE medical_history_questionnaires (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
    template_version_id UUID NOT NULL REFERENCES medical_history_template_versions(id),
    purpose VARCHAR(30) NOT NULL CHECK (purpose IN ('INITIAL','CHANGE_PROPOSAL','LEGACY_COMPATIBILITY')),
    source VARCHAR(30) NOT NULL CHECK (source IN ('APP','WEB','PAPER','TRANSCRIPTION','LEGACY_COMPATIBILITY')),
    status VARCHAR(30) NOT NULL CHECK (status IN ('DRAFT','SUBMITTED','UNDER_REVIEW','CLARIFICATION_REQUIRED','VALIDATED','REJECTED','CANCELLED')),
    base_validated_version_id UUID NULL,
    scan_document_id UUID NULL REFERENCES clinical_documents(id),
    delivered_at TIMESTAMPTZ NULL,
    delivered_by UUID NULL REFERENCES users(id),
    received_at TIMESTAMPTZ NULL,
    received_by UUID NULL REFERENCES users(id),
    expires_at TIMESTAMPTZ NULL,
    submitted_at TIMESTAMPTZ NULL,
    submitted_by UUID NULL REFERENCES users(id),
    review_started_at TIMESTAMPTZ NULL,
    reviewed_by UUID NULL REFERENCES users(id),
    validated_at TIMESTAMPTZ NULL,
    validated_by UUID NULL REFERENCES users(id),
    rejected_at TIMESTAMPTZ NULL,
    rejected_by UUID NULL REFERENCES users(id),
    cancelled_at TIMESTAMPTZ NULL,
    cancelled_by UUID NULL REFERENCES users(id),
    status_reason VARCHAR(1000) NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_mh_questionnaires_patient_status ON medical_history_questionnaires(patient_id, status, created_at DESC);
CREATE INDEX idx_mh_questionnaires_status_source ON medical_history_questionnaires(status, source, created_at DESC);

CREATE TABLE medical_history_questionnaire_answers (
    id UUID PRIMARY KEY,
    questionnaire_id UUID NOT NULL REFERENCES medical_history_questionnaires(id) ON DELETE CASCADE,
    question_id UUID NOT NULL REFERENCES medical_history_template_questions(id),
    answer_value JSONB NOT NULL,
    note VARCHAR(2000) NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_mh_questionnaire_answer UNIQUE (questionnaire_id, question_id)
);

CREATE TABLE medical_history_answer_revisions (
    id UUID PRIMARY KEY,
    questionnaire_id UUID NOT NULL REFERENCES medical_history_questionnaires(id) ON DELETE CASCADE,
    revision_number INTEGER NOT NULL CHECK (revision_number > 0),
    answers_snapshot JSONB NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL,
    submitted_by UUID NOT NULL REFERENCES users(id),
    CONSTRAINT uq_mh_answer_revision UNIQUE (questionnaire_id, revision_number)
);

CREATE TABLE medical_history_review_notes (
    id UUID PRIMARY KEY,
    questionnaire_id UUID NOT NULL REFERENCES medical_history_questionnaires(id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES users(id),
    note_type VARCHAR(30) NOT NULL CHECK (note_type IN ('INTERNAL','CLARIFICATION_REQUEST','PATIENT_RESPONSE')),
    note_text VARCHAR(2000) NOT NULL CHECK (BTRIM(note_text) <> ''),
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_mh_review_notes_questionnaire ON medical_history_review_notes(questionnaire_id, created_at);

CREATE TABLE medical_history_attestations (
    id UUID PRIMARY KEY,
    questionnaire_id UUID NOT NULL REFERENCES medical_history_questionnaires(id) ON DELETE CASCADE,
    revision_number INTEGER NOT NULL CHECK (revision_number > 0),
    template_version_id UUID NOT NULL REFERENCES medical_history_template_versions(id),
    attestation_type VARCHAR(40) NOT NULL CHECK (attestation_type IN ('PATIENT_ELECTRONIC','HANDWRITTEN_SCAN','PROFESSIONAL_ATTESTATION')),
    signer_user_id UUID NULL REFERENCES users(id),
    signer_name VARCHAR(180) NOT NULL CHECK (BTRIM(signer_name) <> ''),
    evidence_document_id UUID NULL REFERENCES clinical_documents(id),
    attested_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_mh_attestation_revision UNIQUE (questionnaire_id, revision_number, attestation_type),
    CONSTRAINT chk_mh_attestation_evidence CHECK (attestation_type <> 'HANDWRITTEN_SCAN' OR evidence_document_id IS NOT NULL)
);

CREATE TABLE medical_history_versions (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL CHECK (version_number > 0),
    source VARCHAR(30) NOT NULL CHECK (source IN ('QUESTIONNAIRE','LEGACY_UNVERIFIED','LEGACY_COMPATIBILITY')),
    source_questionnaire_id UUID NULL REFERENCES medical_history_questionnaires(id),
    source_revision_number INTEGER NULL,
    previous_version_id UUID NULL REFERENCES medical_history_versions(id),
    validated_by UUID NULL REFERENCES users(id),
    validated_at TIMESTAMPTZ NOT NULL,
    snapshot JSONB NOT NULL,
    current BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_mh_version_number UNIQUE (patient_id, version_number)
);
CREATE UNIQUE INDEX uq_mh_version_current ON medical_history_versions(patient_id) WHERE current;
CREATE INDEX idx_mh_versions_patient_date ON medical_history_versions(patient_id, validated_at DESC);
ALTER TABLE medical_history_questionnaires
    ADD CONSTRAINT fk_mh_questionnaire_base_version FOREIGN KEY (base_validated_version_id) REFERENCES medical_history_versions(id);

CREATE TABLE medical_history_transition_events (
    id UUID PRIMARY KEY,
    questionnaire_id UUID NOT NULL REFERENCES medical_history_questionnaires(id) ON DELETE CASCADE,
    from_status VARCHAR(30) NULL,
    to_status VARCHAR(30) NOT NULL,
    actor_id UUID NOT NULL REFERENCES users(id),
    reason_code VARCHAR(80) NULL,
    occurred_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_mh_transition_questionnaire ON medical_history_transition_events(questionnaire_id, occurred_at, id);

ALTER TABLE medical_histories ADD COLUMN current_version_id UUID NULL;

INSERT INTO medical_history_versions (
    id, patient_id, version_number, source, source_questionnaire_id, source_revision_number,
    previous_version_id, validated_by, validated_at, snapshot, current
)
SELECT gen_random_uuid(), h.patient_id, 1, 'LEGACY_UNVERIFIED', NULL, NULL, NULL, NULL, h.updated_at,
       jsonb_build_object(
           'allergies', COALESCE((SELECT jsonb_agg(a.description ORDER BY a.description) FROM medical_history_allergies a WHERE a.medical_history_id = h.id), '[]'::jsonb),
           'currentMedications', COALESCE((SELECT jsonb_agg(m.description ORDER BY m.description) FROM medical_history_medications m WHERE m.medical_history_id = h.id), '[]'::jsonb),
           'relevantConditions', COALESCE((SELECT jsonb_agg(c.description ORDER BY c.description) FROM medical_history_conditions c WHERE c.medical_history_id = h.id), '[]'::jsonb),
           'observations', h.observations
       ), TRUE
FROM medical_histories h;

UPDATE medical_histories h SET current_version_id = v.id
FROM medical_history_versions v WHERE v.patient_id = h.patient_id AND v.current;
ALTER TABLE medical_histories ADD CONSTRAINT fk_medical_histories_current_version
    FOREIGN KEY (current_version_id) REFERENCES medical_history_versions(id);
CREATE INDEX idx_medical_histories_current_version ON medical_histories(current_version_id);

INSERT INTO permissions (id, code, description) VALUES
    (gen_random_uuid(), 'MEDICAL_HISTORY_TEMPLATE_READ', 'Read medical history questionnaire templates'),
    (gen_random_uuid(), 'MEDICAL_HISTORY_TEMPLATE_MANAGE', 'Create and publish medical history questionnaire templates'),
    (gen_random_uuid(), 'MEDICAL_HISTORY_ASSIGN', 'Assign medical history questionnaires'),
    (gen_random_uuid(), 'MEDICAL_HISTORY_RECEIVE', 'Record paper questionnaire delivery and receipt'),
    (gen_random_uuid(), 'MEDICAL_HISTORY_TRANSCRIBE', 'Transcribe paper medical history questionnaires'),
    (gen_random_uuid(), 'MEDICAL_HISTORY_REVIEW', 'Review medical history questionnaires and request clarification'),
    (gen_random_uuid(), 'MEDICAL_HISTORY_VALIDATE', 'Clinically validate or reject medical history questionnaires'),
    (gen_random_uuid(), 'MEDICAL_HISTORY_CLINICAL_READ', 'Read clinical questionnaire answers and validated versions'),
    (gen_random_uuid(), 'MEDICAL_HISTORY_STATUS_READ', 'Read questionnaire status without clinical answers'),
    (gen_random_uuid(), 'MEDICAL_HISTORY_AUDIT_READ', 'Read medical history workflow audit trail'),
    (gen_random_uuid(), 'MEDICAL_HISTORY_LEGACY_IMPORT', 'Import legacy medical history as a dentist-attested version')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE (p.code = 'MEDICAL_HISTORY_TEMPLATE_READ' AND r.code IN ('ADMINISTRATOR','ASSISTANT','DENTIST'))
   OR (p.code = 'MEDICAL_HISTORY_TEMPLATE_MANAGE' AND r.code = 'DENTIST')
   OR (p.code = 'MEDICAL_HISTORY_ASSIGN' AND r.code IN ('ADMINISTRATOR','SECRETARY','ASSISTANT','DENTIST'))
   OR (p.code = 'MEDICAL_HISTORY_RECEIVE' AND r.code IN ('ADMINISTRATOR','SECRETARY','ASSISTANT','DENTIST'))
   OR (p.code = 'MEDICAL_HISTORY_TRANSCRIBE' AND r.code IN ('ASSISTANT','DENTIST'))
   OR (p.code = 'MEDICAL_HISTORY_REVIEW' AND r.code IN ('ASSISTANT','DENTIST'))
   OR (p.code = 'MEDICAL_HISTORY_VALIDATE' AND r.code = 'DENTIST')
   OR (p.code = 'MEDICAL_HISTORY_CLINICAL_READ' AND r.code IN ('ADMINISTRATOR','ASSISTANT','DENTIST'))
   OR (p.code = 'MEDICAL_HISTORY_STATUS_READ' AND r.code IN ('ADMINISTRATOR','SECRETARY','ASSISTANT','DENTIST'))
   OR (p.code = 'MEDICAL_HISTORY_AUDIT_READ' AND r.code IN ('ADMINISTRATOR','DENTIST'))
   OR (p.code = 'MEDICAL_HISTORY_LEGACY_IMPORT' AND r.code = 'DENTIST')
ON CONFLICT (role_id, permission_id) DO NOTHING;

DELETE FROM role_permissions rp USING roles r, permissions p
WHERE rp.role_id = r.id AND rp.permission_id = p.id
  AND p.code = 'MEDICAL_HISTORY_UPDATE' AND r.code = 'ASSISTANT';

--rollback DELETE FROM role_permissions WHERE permission_id IN (SELECT id FROM permissions WHERE code LIKE 'MEDICAL_HISTORY_%' AND code NOT IN ('MEDICAL_HISTORY_READ','MEDICAL_HISTORY_UPDATE'));
--rollback DELETE FROM permissions WHERE code LIKE 'MEDICAL_HISTORY_%' AND code NOT IN ('MEDICAL_HISTORY_READ','MEDICAL_HISTORY_UPDATE');
--rollback ALTER TABLE medical_histories DROP CONSTRAINT IF EXISTS fk_medical_histories_current_version;
--rollback ALTER TABLE medical_histories DROP COLUMN IF EXISTS current_version_id;
--rollback DROP TABLE IF EXISTS medical_history_transition_events;
--rollback DROP TABLE IF EXISTS medical_history_attestations;
--rollback DROP TABLE IF EXISTS medical_history_review_notes;
--rollback DROP TABLE IF EXISTS medical_history_answer_revisions;
--rollback DROP TABLE IF EXISTS medical_history_questionnaire_answers;
--rollback ALTER TABLE medical_history_questionnaires DROP CONSTRAINT IF EXISTS fk_mh_questionnaire_base_version;
--rollback DROP TABLE IF EXISTS medical_history_versions;
--rollback DROP TABLE IF EXISTS medical_history_questionnaires;
--rollback DROP TABLE IF EXISTS medical_history_template_questions;
--rollback DROP TABLE IF EXISTS medical_history_template_sections;
--rollback DROP TABLE IF EXISTS medical_history_template_versions;
--rollback DROP TABLE IF EXISTS medical_history_templates;
