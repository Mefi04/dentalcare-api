--liquibase formatted sql

--changeset dentalcare:045-sanitize-appointment-request-statuses
--validCheckSum: ANY
UPDATE appointment_requests
SET status = 'PENDING'
WHERE status NOT IN ('PENDING', 'PROPOSED', 'CONFIRMED', 'REJECTED', 'CANCELLED');
