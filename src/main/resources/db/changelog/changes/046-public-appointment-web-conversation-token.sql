--liquibase formatted sql

--changeset dentalcare:046-public-appointment-web-conversation-token
ALTER TABLE appointment_public_conversations
    DROP CONSTRAINT appointment_public_conversations_channel_check;

ALTER TABLE appointment_public_conversations
    ADD CONSTRAINT chk_appointment_public_conversations_channel
        CHECK (channel IN ('SMS', 'EMAIL', 'WEB'));

--rollback ALTER TABLE appointment_public_conversations DROP CONSTRAINT chk_appointment_public_conversations_channel;
--rollback ALTER TABLE appointment_public_conversations ADD CONSTRAINT appointment_public_conversations_channel_check CHECK (channel IN ('SMS', 'EMAIL'));
