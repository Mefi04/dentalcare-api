# Public first appointment refactor: audit and implementation record

Status: public intake, aggregate availability, reception search, telephone contact history,
and administrative confirmation are implemented. Final `mvn clean verify` passed with
1,287 tests, zero failures, zero errors, and zero skipped tests. `docker compose build`
also passed. Compose was not started because its configured database is external.

The public flow now records an administrative request without creating an appointment or
issuing a conversation token. Reception can review available general dentistry slots,
record calls, and confirm a slot with an eligible active professional. Migration 050 adds
schedule intervals, blocks, administrative intake fields, and database overlap protection.
Previously applied conversation and notification tables remain for migration compatibility;
their public routes and background outbox worker are disabled.

## Current branch and baseline

- Work branch: `feature/public-first-appointment-chat`; integration branch: `develop`.
- The branch already contains the public conversation, verification, notification outbox, and token retention changes. It also has pre-existing, uncommitted changes in security, exception handling, appointment request service, and tests. Preserve and review these before editing the same files.
- `mvn clean verify` passed before this refactor: 1,285 tests, 0 failures, 0 errors, 162 skipped. PostgreSQL/Testcontainers coverage was skipped because Docker Desktop was unavailable.
- `docker compose build` could not connect to the Docker Desktop Linux engine. GitHub PR/check status could not be queried because the configured GitHub proxy refused the connection.
- After adding contact history, `mvn clean verify` passed: 1,287 tests, 0 failures, 0 errors, 162 skipped. The skipped PostgreSQL/Testcontainers tests still require a running Docker engine.

## Incompatibilities identified before the refactor

1. `CreatePublicAppointmentRequest` still accepts a client-selected `professionalId` and only name, CUI, contact, preferred time, and reason. It lacks birth date, legal guardian or alternative identification, administrative address, emergency contact, billing fields, and privacy consent. Add these with a forward-only Liquibase migration and a new request contract.
2. `ProfessionalPublicProfile.specialty` is free text. A `DENTIST` role alone does not establish eligibility for general dentistry. Define a controlled specialty or service classification, migrate and review existing professionals, and require active status before exposing availability or confirming a first visit.
3. `AppointmentRequestServiceImpl.createPublic` persists `PENDING_CLINIC` but also creates a chat message and issues a conversation token, including on an idempotent retry. The receipt and OpenAPI description expose that token. Removing the token requires changing the public controller, receipt, service, security rules, and tests together.
4. Availability currently accepts one professional and returns booked start instants. `AppointmentServiceImpl` checks exact `scheduledAt` equality. Introduce shared interval overlap checks, configured clinic working hours and breaks, and aggregate capacity across eligible general dentists. Apply the same check at confirmation and rescheduling. Protect concurrent confirmations with a database constraint or equivalent locking strategy.
5. Administrative request routes still expose proposal, conversation messages, notification status, and WhatsApp draft operations. Search and replace their consumers in frontend PR #137 before deleting routes. Keep patient portal request and appointment routes, general notification publishing, patient registration, and in-person identity verification.
6. Existing Liquibase changesets for conversations, messages, decisions, outbox, and retained tokens may have run in production. Leave them immutable. Disable old endpoints and jobs in code first; defer physical schema removal until data retention and migration dependencies are reviewed.

## Implementation order used

1. Introduce controlled general-dentistry eligibility and reviewed professional data.
2. Add administrative intake fields, call attempts, consent evidence, indexes, and overlap protection through new Liquibase changesets.
3. Implement aggregate public availability and reuse interval validation in the official appointment service.
4. Replace public intake response with an acknowledgement only; preserve idempotency and rate limiting.
5. Implement paginated reception search, call history, alternatives, and atomic telephone confirmation.
6. Remove public conversation routes and their exclusive services after a global consumer search. Retain shared notification, agenda, patient, audit, and security components.
7. Update OpenAPI, frontend contract, and tests; verify on PostgreSQL and in Docker before claiming completion.

## Delivered administrative contact contract

`POST /api/v1/appointment-requests/{requestId}/contact-attempts` accepts
`{"result":"NO_ANSWER","observation":"Call again","nextAttemptAt":"2026-10-10T16:00:00Z"}`.
The result is required and must be `CONTACTED`, `NO_ANSWER`, `CALL_BACK_LATER`,
`WRONG_NUMBER`, or `DECLINED`; the observation is optional (500 characters max),
and the optional next attempt must be in the future. It returns HTTP 201 with the
attempt ID, request ID, administrative actor ID, server timestamp, result,
observation, and next attempt. It applies only to public requests in
`PENDING_CLINIC` and never changes their status or creates an appointment.

`GET /api/v1/appointment-requests/{requestId}/contact-attempts?page=0&size=20`
returns a newest-first page; size is limited to 1–100. Both routes require the
existing `ADMINISTRATOR` or `SECRETARY` role and are unavailable to public users.
The actor comes from the authenticated principal. Migration 049 creates the
contact table, integrity constraints, and the history index without changing
earlier changesets.
