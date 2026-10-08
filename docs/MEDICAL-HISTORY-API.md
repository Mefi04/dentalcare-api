# Medical history questionnaire API

This contract implements Issue #144. All routes require a bearer access token. Patient routes derive ownership from the token and never accept `patientId`.

## Workflow

`DRAFT -> SUBMITTED -> UNDER_REVIEW -> VALIDATED|REJECTED`

An odontologist or assistant may move `UNDER_REVIEW -> CLARIFICATION_REQUIRED`; the patient or transcriber then saves and submits a new immutable answer revision. `DRAFT` and `CLARIFICATION_REQUIRED` may be cancelled. Invalid or stale transitions return `409`.

Sources are `APP`, `WEB`, `PAPER`, `TRANSCRIPTION`, and compatibility-only `LEGACY_COMPATIBILITY`. Patient editing is limited to `APP` and `WEB`. Each successful clinical validation creates a new immutable `medical_history_versions` snapshot and atomically replaces the current legacy projection.

## Templates

- `POST /api/v1/medical-history/templates`
- `POST /api/v1/medical-history/templates/{templateId}/versions`
- `PUT /api/v1/medical-history/template-versions/{versionId}` (draft only)
- `POST /api/v1/medical-history/template-versions/{versionId}/publish`
- `GET /api/v1/medical-history/template-versions/{versionId}`
- `GET /api/v1/medical-history/templates/current`

Publishing retires the previous published version. Published versions and questionnaires already assigned from them remain immutable.

## Staff workflow

- `GET /api/v1/medical-history/questionnaires` supports `patientId`, `status`, `source`, `from`, `to`, `page`, and `size`.
- `POST /api/v1/patients/{patientId}/medical-history/questionnaires`
- `GET /api/v1/patients/{patientId}/medical-history/questionnaires/{questionnaireId}`
- `PATCH .../{questionnaireId}/delivery`
- `PATCH .../{questionnaireId}/receipt`
- `PUT .../{questionnaireId}/answers`
- `POST .../{questionnaireId}/submit`
- `POST .../{questionnaireId}/review`
- `POST .../{questionnaireId}/notes`
- `POST .../{questionnaireId}/clarification`
- `POST .../{questionnaireId}/validate`
- `POST .../{questionnaireId}/reject`
- `POST .../{questionnaireId}/cancel`
- `GET /api/v1/patients/{patientId}/medical-history/versions`
- `GET /api/v1/patients/{patientId}/medical-history/versions/{versionId}`
- `GET .../{questionnaireId}/audit`
- `GET .../{questionnaireId}/printable`

Every mutable request includes the last received `lockVersion`. Paper submission with `HANDWRITTEN_SCAN` requires the ID of an already stored clinical document belonging to the same patient.

## Patient self-service

- `POST /api/v1/patients/me/medical-history/change-proposals`
- `GET /api/v1/patients/me/medical-history/questionnaires`
- `GET /api/v1/patients/me/medical-history/questionnaires/{questionnaireId}`
- `PUT .../{questionnaireId}/answers`
- `POST .../{questionnaireId}/submit`
- `POST .../{questionnaireId}/cancel`
- `GET /api/v1/patients/me/medical-history/versions/current`

Electronic patient submission uses:

```json
{
  "lockVersion": 2,
  "attestationType": "PATIENT_ELECTRONIC",
  "signerName": "Nombre del paciente",
  "evidenceDocumentId": null
}
```

Answers use the immutable question IDs returned with the assigned questionnaire:

```json
{
  "lockVersion": 1,
  "answers": [
    {"questionId": "00000000-0000-0000-0000-000000000000", "value": ["Penicilina"], "note": null}
  ]
}
```

## Authorization and errors

Granular authorities separate status access, receipt, transcription, review, clinical validation, clinical detail, audit, template management, and legacy import. Secretaries receive workflow summaries only; only `MEDICAL_HISTORY_VALIDATE` may create a validated clinical version.

- `400`: malformed input or answer incompatible with its question.
- `401`: missing or invalid authentication.
- `403`: insufficient staff authority.
- `404`: absent resource or a resource not owned by the authenticated patient.
- `409`: invalid transition, expired assignment, stale `lockVersion`, incompatible evidence, or concurrent conflict.

Responses never expose storage keys, password/token data, or internal patient-only ownership selectors. Audit transition events contain no answers or clinical payloads.
