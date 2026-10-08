# Medical history questionnaire workflow (Issue #144)

Status: **proposed for clinical and technical review**. No schema or API changes from this document may be implemented until Brayan and Mefi approve the entity model, permission matrix, state machine, routes, and compatibility strategy.

## 1. Goals and non-goals

This design extends the existing `medicalhistory` module without replacing patients, authentication, clinical documents, or the legacy read projection. It provides versioned questionnaires, patient self-service, paper transcription, clinical review, dentist validation, change proposals, immutable validated versions, and auditable transitions.

The questionnaire engine is configurable. This proposal deliberately does **not** copy or seed a commercial questionnaire. The clinic and an odontologist must approve the exact wording, answer options, required questions, and conditional rules before publishing the first template version. Technical validation is not clinical or legal approval.

Out of scope: UI work, global authentication changes, appointments, billing, inventory, notifications containing clinical data, and a bespoke file store.

## 2. Existing contracts and compatibility

The following contracts remain available:

- `GET /api/v1/patients/{patientId}/medical-history` continues returning the current validated projection.
- `GET /api/v1/patients/me/health` continues deriving the authenticated patient's health summary without accepting a `patientId`.
- Existing rows in `medical_histories` and their allergy, medication, and condition collections are preserved.
- Existing clinical documents remain the only persisted file metadata and storage integration.

The legacy `PUT /api/v1/patients/{patientId}/medical-history` must no longer be a workflow bypass. After rollout it is restricted to `MEDICAL_HISTORY_LEGACY_IMPORT`, granted only to dentists during the compatibility window. Each accepted request creates and validates an immutable `LEGACY_COMPATIBILITY` questionnaire/version in the same transaction and updates the legacy projection. Assistants must use review/transcription routes. The endpoint is marked deprecated in OpenAPI and can be removed only in a later, separately coordinated version.

Existing records are backfilled as version 1 with provenance `LEGACY_UNVERIFIED`. No allergy, medication, condition, observation, or timestamp is deleted or overwritten by the migration.

## 3. Proposed persistence model

All identifiers are UUIDs and all timestamps are `TIMESTAMPTZ`. Mutable aggregates use optimistic locking.

### Template catalog

`medical_history_templates`

- `id`, unique stable `code`, `name`, `active`, audit timestamps.

`medical_history_template_versions`

- `id`, `template_id`, monotonically increasing `version_number`, `status` (`DRAFT`, `PUBLISHED`, `RETIRED`).
- `title`, `created_by`, `created_at`, `published_by`, `published_at`.
- A published version is immutable. Only one published version per template can be current.

`medical_history_template_sections`

- `id`, `template_version_id`, stable `section_key`, title, description, `display_order`.

`medical_history_template_questions`

- `id`, `section_id`, stable `question_key`, prompt, `answer_type`, `display_order`, `required`, `notes_allowed`.
- `answer_type`: `YES_NO_UNKNOWN_NA`, `SINGLE_CHOICE`, `MULTIPLE_CHOICE`, `SHORT_TEXT`, `LONG_TEXT`, `DATE`, `NUMBER`.
- Optional validation columns (`min_value`, `max_value`, `max_length`) and JSONB choice/conditional-note configuration validated at publication time.
- Unique `(template_version_id, question_key)` and unique section/question ordering constraints.

The initial clinic-authored catalog should cover, after odontologist approval: allergies/adverse reactions, current medications and anticoagulants, relevant medical conditions, surgeries/hospitalizations, pregnancy/lactation, bleeding/coagulation, cardiovascular/respiratory/metabolic/renal/hepatic/neurologic conditions, communicable diseases, tobacco/alcohol/other substances, prior dental/anesthesia experiences, devices/prostheses, treating professionals, emergency contact, and relevant observations.

### Questionnaire workflow

`medical_history_questionnaires`

- `id`, `patient_id`, `template_version_id`, `purpose` (`INITIAL`, `CHANGE_PROPOSAL`, `LEGACY_COMPATIBILITY`).
- `source` (`APP`, `WEB`, `PAPER`, `TRANSCRIPTION`, `LEGACY_UNVERIFIED`, `LEGACY_COMPATIBILITY`).
- `status` (`DRAFT`, `SUBMITTED`, `UNDER_REVIEW`, `CLARIFICATION_REQUIRED`, `VALIDATED`, `REJECTED`, `CANCELLED`).
- `base_validated_version_id` for change proposals.
- Delivery/receipt fields: `delivered_at`, `delivered_by`, `received_at`, `received_by`, `expires_at`.
- Submission/review/validation/rejection/cancellation timestamps and actor IDs; nonblank reason where applicable.
- `scan_document_id` nullable FK to `clinical_documents`; the service verifies the same patient and a stored file before linking it.
- `lock_version` for optimistic locking and standard audit timestamps.

`medical_history_questionnaire_answers`

- `id`, `questionnaire_id`, `question_id`, JSONB `answer_value`, optional note, audit timestamps.
- Unique `(questionnaire_id, question_id)`.
- Answers can change only in `DRAFT` or `CLARIFICATION_REQUIRED`. Once submitted, that snapshot is immutable; clarification creates a new answer revision rather than rewriting the submitted snapshot.

`medical_history_answer_revisions`

- Immutable `questionnaire_id`, `revision_number`, submitted timestamp/actor, and answer snapshot JSONB.
- Unique `(questionnaire_id, revision_number)`.

`medical_history_review_notes`

- `id`, questionnaire, author, type (`INTERNAL`, `CLARIFICATION_REQUEST`, `PATIENT_RESPONSE`), text, created timestamp.
- Patient responses are allowed only on their own questionnaire while clarification is required. Internal notes are never exposed through patient routes.

### Attestation and validated clinical versions

`medical_history_attestations`

- `id`, questionnaire/revision, `type` (`PATIENT_ELECTRONIC`, `HANDWRITTEN_SCAN`, `PROFESSIONAL_ATTESTATION`).
- signer user/name/type, `attested_at`, exact `template_version_id`, exact answer `revision_number`, evidence document ID where required.
- Handwritten attestations require a same-patient stored clinical document. Electronic patient attestations require the patient's authenticated user. Professional attestation requires a dentist.
- Attestations are immutable. Whether a signature mechanism has legal validity in Guatemala requires local legal review; the API records evidence and does not claim legal equivalence.

`medical_history_versions`

- `id`, `patient_id`, sequential `version_number`, source questionnaire/revision, previous version, validator, `validated_at`, immutable JSONB snapshot, and `current` flag.
- Unique `(patient_id, version_number)` and partial unique index ensuring one current version per patient.
- Validation locks the patient aggregate, creates one version, marks the prior version non-current, and updates `medical_histories` projection atomically.
- A pending or rejected change proposal never alters the current validated version.

`medical_history_transition_events`

- Append-only questionnaire ID, from/to status, actor, timestamp, reason code, and safe metadata without answers or PHI.
- Complements the global audit module; it is the domain history used by workflow clients.

## 4. State machine

| From | Action | To | Actor/permission | Notes |
|---|---|---|---|---|
| none | assign/create | `DRAFT` | `ASSIGN` or authenticated patient | Patient route always resolves patient from JWT. |
| `DRAFT` | save answers | `DRAFT` | owning patient or `TRANSCRIBE` | Paper transcription requires `PAPER`/`TRANSCRIPTION` provenance. |
| `DRAFT` | submit | `SUBMITTED` | owning patient or `SUBMIT` | Idempotent for the same revision; validates required answers and attestation. |
| `SUBMITTED` | start review | `UNDER_REVIEW` | `REVIEW` | Records reviewer and timestamp. |
| `UNDER_REVIEW` | request clarification | `CLARIFICATION_REQUIRED` | `REVIEW` | Nonblank reason required. |
| `CLARIFICATION_REQUIRED` | revise and resubmit | `SUBMITTED` | owning patient or `TRANSCRIBE` | Creates the next immutable answer revision. |
| `UNDER_REVIEW` | validate | `VALIDATED` | `VALIDATE` (dentist only) | Creates a new current clinical version atomically. Duplicate call returns the existing result. |
| `UNDER_REVIEW` | reject | `REJECTED` | `VALIDATE` (dentist only) | Nonblank reason; submission remains traceable. |
| `DRAFT`/`CLARIFICATION_REQUIRED` | cancel | `CANCELLED` | owning patient or `ASSIGN` | Nonblank reason for staff cancellation. |

All other transitions return `409 Conflict`. Stale `lockVersion` returns `409`. Validation uses a patient row lock plus uniqueness constraints to prevent two current versions.

## 5. Permission matrix

| Permission | Administrator | Secretary | Assistant | Dentist | Patient |
|---|:---:|:---:|:---:|:---:|:---:|
| `MEDICAL_HISTORY_TEMPLATE_READ` | yes | no | yes | yes | via assigned snapshot only |
| `MEDICAL_HISTORY_TEMPLATE_MANAGE` | no | no | no | yes | no |
| `MEDICAL_HISTORY_ASSIGN` | yes | yes | yes | yes | own initial/change draft only |
| `MEDICAL_HISTORY_RECEIVE` | yes | yes | yes | yes | no |
| `MEDICAL_HISTORY_TRANSCRIBE` | no | no | yes | yes | no |
| `MEDICAL_HISTORY_REVIEW` | no | no | yes | yes | no |
| `MEDICAL_HISTORY_VALIDATE` | no | no | no | yes | no |
| `MEDICAL_HISTORY_CLINICAL_READ` | yes | no | yes | yes | own validated summary only |
| `MEDICAL_HISTORY_STATUS_READ` | yes | yes | yes | yes | own only |
| `MEDICAL_HISTORY_AUDIT_READ` | yes | no | no | yes | no |
| `MEDICAL_HISTORY_LEGACY_IMPORT` | no | no | no | yes | no |

Secretary routes return only patient identity needed for the task, questionnaire ID, source, status, and delivery/receipt timestamps. They never return answers, review notes, attestations, or validated snapshots.

Every `/patients/me/**` operation resolves the patient from `AuthenticatedUser.userId`; it never accepts or trusts a patient ID. Staff endpoints validate the requested patient and permission independently, preventing IDOR.

## 6. Proposed HTTP contract

### Templates (dentist-managed)

- `POST /api/v1/medical-history/templates`
- `POST /api/v1/medical-history/templates/{templateId}/versions`
- `PUT /api/v1/medical-history/template-versions/{versionId}` (draft only)
- `POST /api/v1/medical-history/template-versions/{versionId}/publish`
- `GET /api/v1/medical-history/template-versions/{versionId}`
- `GET /api/v1/medical-history/templates/current`

### Staff workflow

- `POST /api/v1/patients/{patientId}/medical-history/questionnaires`
- `GET /api/v1/patients/{patientId}/medical-history/questionnaires`
- `GET /api/v1/patients/{patientId}/medical-history/questionnaires/{questionnaireId}`
- `PATCH /api/v1/patients/{patientId}/medical-history/questionnaires/{questionnaireId}/delivery`
- `PATCH /api/v1/patients/{patientId}/medical-history/questionnaires/{questionnaireId}/receipt`
- `PUT /api/v1/patients/{patientId}/medical-history/questionnaires/{questionnaireId}/answers`
- `POST /api/v1/patients/{patientId}/medical-history/questionnaires/{questionnaireId}/submit`
- `POST /api/v1/patients/{patientId}/medical-history/questionnaires/{questionnaireId}/review`
- `POST /api/v1/patients/{patientId}/medical-history/questionnaires/{questionnaireId}/clarification`
- `POST /api/v1/patients/{patientId}/medical-history/questionnaires/{questionnaireId}/validate`
- `POST /api/v1/patients/{patientId}/medical-history/questionnaires/{questionnaireId}/reject`
- `GET /api/v1/patients/{patientId}/medical-history/versions`
- `GET /api/v1/patients/{patientId}/medical-history/versions/{versionId}`
- `GET /api/v1/patients/{patientId}/medical-history/audit`
- `GET /api/v1/patients/{patientId}/medical-history/questionnaires/{questionnaireId}/printable`

`printable` returns a stable print DTO for client-side print/PDF rendering. The backend does not add a PDF library in this issue. Existing authorized clinical-document upload/download routes handle scanned evidence.

### Patient self-service

- `POST /api/v1/patients/me/medical-history/questionnaires` (initial draft or change proposal)
- `GET /api/v1/patients/me/medical-history/questionnaires`
- `GET /api/v1/patients/me/medical-history/questionnaires/{questionnaireId}`
- `PUT /api/v1/patients/me/medical-history/questionnaires/{questionnaireId}/answers`
- `POST /api/v1/patients/me/medical-history/questionnaires/{questionnaireId}/submit`
- `POST /api/v1/patients/me/medical-history/questionnaires/{questionnaireId}/cancel`
- `GET /api/v1/patients/me/medical-history/versions/current`

Resource mismatches under an authenticated patient's routes return `404` rather than revealing ownership. `400` covers malformed input, `401` missing/invalid authentication, `403` insufficient staff permission, `404` absent or hidden resources, and `409` invalid transitions, stale versions, duplicate current versions, or incompatible evidence.

## 7. Security, privacy, and audit

- Questionnaire answers, snapshots, and document keys never appear in application logs, audit details, notifications, or URL query parameters.
- Global audit actions record action, module, resource ID, actor ID, result, and timestamp only.
- Domain transition events contain no answer values.
- Request DTOs have explicit size/range validation. JSONB answer shapes are validated against the immutable question definition before persistence.
- Submission and validation are transactional and idempotent by state/revision. Database constraints are the final concurrency guard.
- Assigned questionnaires may expire. Expiration blocks new submission but never deletes a draft or submitted evidence.
- The current P1 uses authenticated portal/app sessions; it does not send bearer links. Any future external link must use a hashed one-time token with expiry and revocation.
- Clinical document access continues through existing authorized endpoints; no storage URL is returned by this module.

## 8. Liquibase plan

Create new incremental changesets after the current latest changeset; never edit `008-create-medical-history-core.sql`.

1. Schema, enums/check constraints, indexes, partial unique current-version index, and granular permissions.
2. Backfill every existing `medical_histories` row to an immutable `LEGACY_UNVERIFIED` version while retaining original timestamps and collection values.
3. Add nullable `current_version_id` to `medical_histories`, populate it, then add its FK/index. Keep legacy tables as the compatibility projection.

Every changeset includes rollback where data-safe. Destructive rollback of a populated workflow schema is intentionally not automated; rollback documentation will state the preservation procedure.

## 9. Delivery slices and tests

Because this is a large clinical/security change, implementation should use reviewed vertical slices:

- **A — schema and questionnaire core:** migration, templates, assignments, drafts/submission, patient ownership, paper provenance, compatibility/backfill.
- **B — clinical review and versions:** review/clarification/validation/rejection, immutable versions, projection update, concurrency/idempotency, granular permissions.
- **C — change proposals and contract completion:** attestations/evidence, history/audit/print DTO, OpenAPI, compatibility deprecation, future-client documentation.

Required automated coverage includes:

- patient JWT can draft/submit only its own questionnaire and cannot perform IDOR;
- secretary/assistant cannot validate and dentist can;
- pending/rejected changes do not alter current validated data;
- accepted change creates one new current version and preserves the old one;
- stale concurrent updates and duplicate submit/validate do not create duplicate current versions;
- paper source, receipt, transcription, scan reference, and attestation remain traceable;
- attestations bind the exact immutable template and answer revision and enforce evidence;
- backfill preserves legacy data and labels it `LEGACY_UNVERIFIED`;
- legacy GET and `/patients/me/health` regressions pass, and legacy PUT cannot bypass validation;
- expected `400/401/403/404/409` responses and OpenAPI generation pass;
- `mvn clean verify`, `docker compose build`, `git diff --check`, and `git status` are clean for every implementation PR.

## 10. Decisions required before implementation

Brayan and Mefi must approve:

1. The entity boundaries and use of JSONB for typed answer values and immutable snapshots.
2. The permission matrix, especially administrator clinical-read access and dentist-only template publication/validation.
3. The legacy `PUT` compatibility path and its dentist-only temporary permission.
4. The proposed state machine and idempotency behavior.
5. The HTTP route shapes and the decision to expose a print DTO rather than server-rendered PDF.
6. Reuse of `clinical_documents` for scan/signature evidence with same-patient verification.
7. The clinic-approved question catalog and local legal review of attestation types before publishing a production template.
