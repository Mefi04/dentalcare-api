--liquibase formatted sql

--changeset dentalcare:024-add-patient-visibility-to-clinical-documents
ALTER TABLE clinical_documents
    ADD COLUMN patient_visible BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN shared_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN shared_by UUID;

ALTER TABLE clinical_documents
    ADD CONSTRAINT fk_clinical_documents_shared_by
        FOREIGN KEY (shared_by) REFERENCES users (id) ON DELETE RESTRICT,
    ADD CONSTRAINT chk_clinical_documents_patient_visibility_audit
        CHECK (
            (patient_visible = FALSE AND shared_at IS NULL AND shared_by IS NULL)
            OR
            (patient_visible = TRUE AND shared_at IS NOT NULL AND shared_by IS NOT NULL)
        );

CREATE INDEX idx_clinical_documents_patient_visible_date
    ON clinical_documents (patient_id, document_date DESC, created_at DESC, id DESC)
    WHERE patient_visible = TRUE;

--rollback DROP INDEX IF EXISTS idx_clinical_documents_patient_visible_date;
--rollback ALTER TABLE clinical_documents DROP CONSTRAINT IF EXISTS chk_clinical_documents_patient_visibility_audit;
--rollback ALTER TABLE clinical_documents DROP CONSTRAINT IF EXISTS fk_clinical_documents_shared_by;
--rollback ALTER TABLE clinical_documents DROP COLUMN IF EXISTS shared_by;
--rollback ALTER TABLE clinical_documents DROP COLUMN IF EXISTS shared_at;
--rollback ALTER TABLE clinical_documents DROP COLUMN IF EXISTS patient_visible;
