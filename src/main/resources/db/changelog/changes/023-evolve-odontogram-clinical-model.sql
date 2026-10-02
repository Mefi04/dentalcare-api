--liquibase formatted sql

--changeset dentalcare:023-evolve-odontogram-clinical-model
ALTER TABLE odontogram_findings
    DROP CONSTRAINT IF EXISTS chk_odontogram_findings_surface;

ALTER TABLE odontogram_findings
    ADD CONSTRAINT chk_odontogram_findings_surface
    CHECK (surface IS NULL OR surface IN ('VESTIBULAR', 'PALATAL', 'LINGUAL', 'MESIAL', 'DISTAL', 'OCCLUSAL', 'INCISAL'));

ALTER TABLE odontogram_findings
    DROP CONSTRAINT IF EXISTS chk_odontogram_findings_finding;

ALTER TABLE odontogram_findings
    ADD CONSTRAINT chk_odontogram_findings_finding
    CHECK (finding IN ('HEALTHY', 'CARIOUS', 'TREATED', 'MISSING', 'TO_TREAT', 'RESTORED', 'CROWN', 'IMPLANT', 'ENDODONTIC', 'FRACTURE', 'PROSTHESIS'));

CREATE INDEX IF NOT EXISTS idx_odontogram_findings_patient_dentition_created
    ON odontogram_findings (patient_id, dentition, created_at ASC, id ASC);
