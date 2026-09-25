# API Conventions

## Base path

All REST API endpoints use:

/api/v1

## Resource naming

Use plural nouns.

Correct:

/api/v1/patients

/api/v1/appointments

/api/v1/treatments

Avoid verbs when normal REST semantics are enough.

## HTTP methods

GET

Read resources.

POST

Create resources or trigger explicit actions.

PUT

Update complete resources when appropriate.

PATCH

Partial update when appropriate.

DELETE

Delete/deactivate when business rules allow.

## DTOs

Never return JPA entities directly.

Use response DTOs.

Example:

PatientResponse

Use request DTOs.

Example:

CreatePatientRequest

UpdatePatientRequest

## Validation

Use Jakarta Bean Validation.

Examples:

@NotNull
@NotBlank
@Email
@Size
@Positive

Business validation belongs in services.

## Status codes

Common codes:

- `200 OK`: successful read or update.
- `201 Created`: successful creation.
- `204 No Content`: successful operation without a response body.
- `400 Bad Request`: malformed request or validation failure.
- `401 Unauthorized`: authentication is missing, invalid, or no longer usable. Authentication errors must be generic and must not disclose whether an account or session exists.
- `403 Forbidden`: authentication succeeded, but the caller lacks permission for the operation.
- `404 Not Found`: resource does not exist.
- `409 Conflict`: unique constraint or state conflict.
- `422 Unprocessable Entity`: semantically invalid request when specifically appropriate.
- `500 Internal Server Error`: unexpected server failure.

Do not use `403 Forbidden` as a substitute for failed authentication. Backend authorization is authoritative; frontend route guards and middleware are user-experience aids only.

## Error responses

Errors must use one consistent structure.

Contract:

```json
{
  "timestamp": "...",
  "status": 400,
  "error": "Bad Request",
  "message": "...",
  "path": "/api/v1/example",
  "fieldErrors": {
    "fieldName": "must not be blank"
  }
}
```

Validation errors may include field-level details.

The contract always exposes `timestamp`, `status`, `error`, `message`, `path`, and `fieldErrors`; `fieldErrors` may be empty when the error is not field-specific. Error handling is centralized. Responses must never expose stack traces, credentials, database details, token material, or implementation internals.

## Authentication endpoints

Authentication uses `/api/v1/auth`. Detailed token, cookie, rotation, and session rules are defined in `SECURITY.md`.

| Method | Path | Authentication input | Success | Notes |
|---|---|---|---|---|
| `POST` | `/api/v1/auth/activate` | JSON `cui`, `temporaryPassword`, and `newPassword` | `200 OK` with status `ACTIVE` and success message | Activates a `PENDING_ACTIVATION` user, replaces temporary password with new BCrypt hash. Does NOT return tokens. Invalid credentials or non-pending status return generic `401`. |
| `POST` | `/api/v1/auth/login` | JSON `cui` (exactly 13 digits) and `password` | `200 OK` with access token and user view | Sets refresh token only in an HttpOnly cookie. Invalid credentials return generic `401`; request validation returns `400`. |
| `POST` | `/api/v1/auth/refresh` | Refresh cookie; no token in body | `200 OK` with a new access token | Rotates the refresh cookie. Invalid, expired, revoked, or reused tokens return generic `401`. |
| `POST` | `/api/v1/auth/logout` | Refresh cookie | `204 No Content` | Revokes the identified session and clears the cookie; idempotent where practical. |
| `GET` | `/api/v1/auth/me` | Bearer access token | `200 OK` with the current user view | Never returns password hashes, token material, or session data. |

Successful access-token responses use `tokenType: "Bearer"` and `expiresIn: 1800` by default. Login additionally returns `user` with `id`, `username`, `email`, `status`, `roles`, and `permissions`. Refresh tokens never appear in JSON responses.

## Patient portal endpoints

| Method | Path | Authorization | Success | Notes |
|---|---|---|---|---|
| `POST` | `/api/v1/patients/{id}/access` | `ROLE_ADMINISTRATOR` | `201 Created` | Creates one linked `PENDING_ACTIVATION` user with only role `PATIENT`; returns its temporary password once. A second creation request returns `409 Conflict`. |
| `GET` | `/api/v1/patients/me` | `ROLE_PATIENT` | `200 OK` | Resolves the associated patient solely from the JWT principal. It neither accepts nor trusts a patient ID supplied by the client. |
| `GET` | `/api/v1/patients/me/profile` | `ROLE_PATIENT` | `200 OK` | Returns `PatientProfileResponse` with personal details and a masked DPI (`*********XXXX`). Identity resolved solely from JWT principal. |
| `GET` | `/api/v1/patients/me/health` | `ROLE_PATIENT` | `200 OK` | Returns `PatientHealthResponse` representing the patient's health summary (`EMPTY` status until clinical persistence models are implemented). Identity resolved solely from JWT principal. |

The standard `PatientResponse` is used for `/patients/me`; it never embeds user credentials, password hashes, roles, or refresh-session data.

`/patients/me/profile` returns:
- `fullName`: patient full name.
- `maskedDpi`: 13-digit DPI with only the last 4 digits visible (`*********XXXX`).
- `birthDate`: ISO-8601 date (`YYYY-MM-DD`).
- `phone`: patient phone.
- `email`: patient contact email (nullable).
- `address`: patient physical address (nullable).
- `emergencyContact`: nested object with `name`, `phone`, and `relationship: null` (nullable if no emergency contact details exist).

`/patients/me/health` returns:
- `allergies`: list of allergies (currently empty list `[]`).
- `currentMedications`: list of medications (currently empty list `[]`).
- `relevantConditions`: list of conditions (currently empty list `[]`).
- `recentChanges`: list of changes (currently empty list `[]`).
- `observations`: clinical observations (`null`).
- `lastUpdated`: timestamp of medical record update (`null`, never using administrative `patient.updatedAt`).
- `status`: `EMPTY` (valid domain representation for absent clinical records).

## Pagination

Large collections should support pagination where necessary.

Suggested parameters:

?page=0
&size=20
&sort=name,asc

## OpenAPI

Public API endpoints should be documented using OpenAPI/Swagger.
