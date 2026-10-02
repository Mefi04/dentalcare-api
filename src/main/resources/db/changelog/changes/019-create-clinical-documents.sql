--liquibase formatted sql

--changeset dentalcare:019-create-clinical-documents
CREATE TABLE IF NOT EXISTS clinical_documents (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL,
    author_id UUID NOT NULL,
    title VARCHAR(150) NOT NULL,
    type VARCHAR(30) NOT NULL,
    description VARCHAR(1000),
    document_date DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_clinical_documents_patient
        FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE RESTRICT,
    CONSTRAINT fk_clinical_documents_author
        FOREIGN KEY (author_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_clinical_documents_title CHECK (BTRIM(title) <> ''),
    CONSTRAINT chk_clinical_documents_type CHECK (type IN ('RADIOGRAPHY', 'LAB_RESULT', 'INFORMED_CONSENT', 'CLINICAL_REPORT', 'PHOTOGRAPHY', 'OTHER')),
    CONSTRAINT chk_clinical_documents_description CHECK (description IS NULL OR BTRIM(description) <> '')
);

CREATE INDEX IF NOT EXISTS idx_clinical_documents_patient_date
    ON clinical_documents (patient_id, document_date DESC, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_clinical_documents_author
    ON clinical_documents (author_id);
CREATE INDEX IF NOT EXISTS idx_clinical_documents_type
    ON clinical_documents (patient_id, type);
