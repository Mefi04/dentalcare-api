--liquibase formatted sql

--changeset dentalcare:033-create-professional-public-profiles
CREATE TABLE professional_public_profiles (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,
    professional_registration VARCHAR(100) NOT NULL,
    specialty VARCHAR(150) NOT NULL,
    summary VARCHAR(1000) NOT NULL,
    years_experience INTEGER,
    languages VARCHAR(255),
    photo_url VARCHAR(2048),
    public_visible BOOLEAN NOT NULL DEFAULT FALSE,
    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_professional_public_profiles_registration CHECK (BTRIM(professional_registration) <> ''),
    CONSTRAINT chk_professional_public_profiles_specialty CHECK (BTRIM(specialty) <> ''),
    CONSTRAINT chk_professional_public_profiles_summary CHECK (BTRIM(summary) <> ''),
    CONSTRAINT chk_professional_public_profiles_years_experience CHECK (years_experience IS NULL OR years_experience BETWEEN 0 AND 80),
    CONSTRAINT chk_professional_public_profiles_languages CHECK (languages IS NULL OR BTRIM(languages) <> ''),
    CONSTRAINT chk_professional_public_profiles_photo_url CHECK (photo_url IS NULL OR BTRIM(photo_url) <> ''),
    CONSTRAINT chk_professional_public_profiles_timestamps CHECK (updated_at >= created_at),
    CONSTRAINT fk_professional_public_profiles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_professional_public_profiles_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_professional_public_profiles_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE RESTRICT
);
CREATE INDEX idx_professional_public_profiles_visible ON professional_public_profiles (public_visible);

--rollback DROP TABLE professional_public_profiles;
