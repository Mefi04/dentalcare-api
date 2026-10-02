--liquibase formatted sql

--changeset dentalcare:022-create-clinical-preparations
CREATE TABLE IF NOT EXISTS clinical_preparations (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL,
    attention_id UUID,
    prepared_by_id UUID NOT NULL,
    blood_pressure VARCHAR(20),
    heart_rate INTEGER,
    temperature NUMERIC(4, 1),
    weight NUMERIC(5, 2),
    observations VARCHAR(2000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_clinical_preparations_patient
        FOREIGN KEY (patient_id) REFERENCES patients (id) ON DELETE RESTRICT,
    CONSTRAINT fk_clinical_preparations_attention
        FOREIGN KEY (attention_id) REFERENCES clinical_attentions (id) ON DELETE SET NULL,
    CONSTRAINT fk_clinical_preparations_prepared_by
        FOREIGN KEY (prepared_by_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_clinical_preparations_blood_pressure
        CHECK (blood_pressure IS NULL OR BTRIM(blood_pressure) <> ''),
    CONSTRAINT chk_clinical_preparations_heart_rate
        CHECK (heart_rate IS NULL OR (heart_rate >= 30 AND heart_rate <= 300)),
    CONSTRAINT chk_clinical_preparations_temperature
        CHECK (temperature IS NULL OR (temperature >= 30.0 AND temperature <= 45.0)),
    CONSTRAINT chk_clinical_preparations_weight
        CHECK (weight IS NULL OR (weight > 0.0 AND weight <= 500.0)),
    CONSTRAINT chk_clinical_preparations_observations
        CHECK (observations IS NULL OR BTRIM(observations) <> '')
);

CREATE INDEX IF NOT EXISTS idx_clinical_preparations_patient_created
    ON clinical_preparations (patient_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_clinical_preparations_attention
    ON clinical_preparations (attention_id);
CREATE INDEX IF NOT EXISTS idx_clinical_preparations_prepared_by
    ON clinical_preparations (prepared_by_id);
