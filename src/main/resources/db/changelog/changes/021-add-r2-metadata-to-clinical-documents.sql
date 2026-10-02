--liquibase formatted sql

--changeset dentalcare:021-add-r2-metadata-to-clinical-documents
ALTER TABLE clinical_documents
    ADD COLUMN storage_object_key VARCHAR(500),
    ADD COLUMN file_name VARCHAR(255),
    ADD COLUMN file_size BIGINT,
    ADD COLUMN content_type VARCHAR(100);

ALTER TABLE clinical_documents
    ADD CONSTRAINT uq_clinical_documents_storage_object_key UNIQUE (storage_object_key);

ALTER TABLE clinical_documents
    ADD CONSTRAINT chk_clinical_documents_file_size CHECK (file_size IS NULL OR file_size >= 0);

ALTER TABLE clinical_documents
    ADD CONSTRAINT chk_clinical_documents_storage_object_key CHECK (storage_object_key IS NULL OR BTRIM(storage_object_key) <> '');

ALTER TABLE clinical_documents
    ADD CONSTRAINT chk_clinical_documents_file_name CHECK (file_name IS NULL OR BTRIM(file_name) <> '');

ALTER TABLE clinical_documents
    ADD CONSTRAINT chk_clinical_documents_content_type CHECK (content_type IS NULL OR BTRIM(content_type) <> '');
