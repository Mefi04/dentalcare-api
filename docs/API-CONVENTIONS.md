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

Successful access-token responses use `tokenType: "Bearer"` and `expiresIn: 1800` by default. Login additionally returns `user` with `id`, `username`, `email`, `status`, `roles`, and `permissions`. For web endpoints (`/api/v1/auth/*`), refresh tokens are transported exclusively via `HttpOnly` cookies and never appear in JSON responses.

### Mobile authentication endpoints

Mobile clients (Expo / React Native) use dedicated endpoints under `/api/v1/auth/mobile`. Because native mobile clients do not have browser cookie jars and instead store tokens in hardware-backed storage (`expo-secure-store`), refresh tokens are transported directly in request and response JSON payloads.

| Method | Path | Authentication input | Success | Notes |
|---|---|---|---|---|
| `POST` | `/api/v1/auth/mobile/login` | JSON `cui` (exactly 13 digits) and `password` | `200 OK` with `accessToken`, `refreshToken`, `tokenType`, `expiresIn`, and `user` | Emits no cookies. Refresh token is returned in JSON for device secure storage. Invalid credentials return generic `401`; validation errors return `400`. |
| `POST` | `/api/v1/auth/mobile/refresh` | JSON `refreshToken` (opaque token) | `200 OK` with `accessToken`, rotated `refreshToken`, `tokenType`, and `expiresIn` | Rotates the refresh token. Invalid, expired, inactive, or revoked tokens return generic `401`. Token reuse revokes the entire token family and returns generic `401`. Emits no cookies. |
| `POST` | `/api/v1/auth/mobile/logout` | JSON `refreshToken` (opaque token, optional) | `204 No Content` | Revokes the identified session in database; idempotent. Emits no cookies. |

## Patient portal endpoints

| Method | Path | Authorization | Success | Notes |
|---|---|---|---|---|
| `POST` | `/api/v1/patients/{id}/access` | `ROLE_ADMINISTRATOR` | `201 Created` | Creates one linked `PENDING_ACTIVATION` user with only role `PATIENT`; returns its temporary password once. A second creation request returns `409 Conflict`. |
| `GET` | `/api/v1/patients/me` | `ROLE_PATIENT` | `200 OK` | Resolves the associated patient solely from the JWT principal. It neither accepts nor trusts a patient ID supplied by the client. |
| `GET` | `/api/v1/patients/me/profile` | `ROLE_PATIENT` | `200 OK` | Returns `PatientProfileResponse` with personal details and a masked DPI (`*********XXXX`). Identity resolved solely from JWT principal. |
| `PATCH` | `/api/v1/patients/me/profile` | `ROLE_PATIENT` | `200 OK` | Partially updates editable contact details (`phone`, `email`, `address`, `emergencyContact`, `emergencyPhone`) for the authenticated patient. Identity resolved solely from JWT principal. Returns updated `PatientProfileResponse`. |
| `GET` | `/api/v1/patients/me/health` | `ROLE_PATIENT` | `200 OK` | Returns the authenticated patient's persisted health summary. Identity is resolved solely from the JWT principal. |
| `GET` | `/api/v1/patients/me/appointments` | `ROLE_PATIENT` | `200 OK` | Lists paginated appointments owned by the authenticated patient in deterministic order (`scheduledAt DESC, id DESC`). Identity is resolved solely from the JWT principal. |
| `GET` | `/api/v1/patients/me/appointments/{appointmentId}` | `ROLE_PATIENT` | `200 OK` | Retrieves detail of an appointment owned by the authenticated patient. Returns generic `404 Not Found` if missing or foreign. |
| `POST` | `/api/v1/patients/me/appointments` | `ROLE_PATIENT` | `201 Created` | Schedules a new appointment for the authenticated patient with an active dentist. Time slot conflicts return `409 Conflict`. |
| `GET` | `/api/v1/patients/me/appointments/professionals` | `ROLE_PATIENT` | `200 OK` | Lists active dentists available for patient self-service booking. Excludes sensitive staff fields. |
| `PATCH` | `/api/v1/patients/me/appointments/{appointmentId}/cancel` | `ROLE_PATIENT` | `200 OK` | Cancels an appointment owned by the authenticated patient. Transitions status from `SCHEDULED` to `CANCELLED`. Rejects invalid transitions (`COMPLETED`, `CANCELLED`) with `409 Conflict`. Non-existent or foreign appointments return `404 Not Found`. Does not accept request body or patient ID parameter. |
| `GET` | `/api/v1/patients/me/account-statement` | `ROLE_PATIENT` | `200 OK` | Returns the authenticated patient's `AccountStatementResponse` (see Billing endpoints). Identity is resolved solely from the JWT principal; caller-supplied `patientId` or `userId` parameters are ignored. |

The standard `PatientResponse` is used for `/patients/me`; it never embeds user credentials, password hashes, roles, or refresh-session data. It exposes `portalAccessStatus` (`PENDING_ACTIVATION`, `ACTIVE`, `INACTIVE`, `LOCKED`, or `null` if no portal account exists) derived directly from the linked user.

`/patients/me/profile` returns:
- `fullName`: patient full name.
- `maskedDpi`: 13-digit DPI with only the last 4 digits visible (`*********XXXX`).
- `birthDate`: ISO-8601 date (`YYYY-MM-DD`).
- `phone`: patient phone.
- `email`: patient contact email (nullable).
- `address`: patient physical address (nullable).
- `emergencyContact`: nested object with `name`, `phone`, and `relationship: null` (nullable if no emergency contact details exist).

`PATCH /patients/me/profile`:
- Editable fields: `phone` (required, max 30 chars), `email` (optional, valid email regex, max 255 chars), `address` (optional, max 255 chars), `emergencyContact` (optional, max 150 chars), `emergencyPhone` (optional, max 30 chars).
- Semantics: Fields omitted from request JSON are preserved unchanged. Optional fields explicitly passed as `null` or blank strings are cleared on the entity; `phone` is required and cannot be null or blank. The request body must include at least one field.
- Read-only fields: Identity, clinical, and administrative fields (`fullName` / `name`, `dpi`, `code`, `birthDate`, `gender`, `billingName`, `nit`, `billingAddress`, `guardianName`, `guardianRelationship`, `guardianPhone`, `user`, portal credentials, timestamps) are strictly immutable through this endpoint.
- Security: Identity is derived solely from the authenticated JWT principal (`principal.userId()`), preventing IDOR vulnerabilities. Mass assignment is prevented via a dedicated DTO.
- Status codes: `200 OK` on success with `PatientProfileResponse`; `400 Bad Request` on invalid email, blank phone, exceeded field lengths, or empty request payload; `401 Unauthorized` when unauthenticated; `403 Forbidden` for non-patient roles; `404 Not Found` if no patient is linked to the authenticated user account.

`/patients/me/health` returns:
- `allergies`: persisted allergies, or `[]` when none are recorded.
- `currentMedications`: persisted current medications, or `[]` when none are recorded.
- `relevantConditions`: persisted relevant medical conditions, or `[]` when none are recorded.
- `recentChanges`: reserved for a future clinical audit feed and currently returned as `[]`.
- `observations`: persisted general clinical observations, or `null`.
- `lastUpdated`: medical-history update timestamp, or `null` when no record exists. It never uses administrative `patient.updatedAt`.
- `status`: `EMPTY` when no clinical information exists, otherwise `UPDATED`.

### Patient appointment cancellation

`PATCH /api/v1/patients/me/appointments/{appointmentId}/cancel`:
- Authorization: Requires authenticated user with `ROLE_PATIENT`. Unauthenticated callers receive `401 Unauthorized`. Users without `ROLE_PATIENT` receive `403 Forbidden`.
- Identity & Ownership: The patient is resolved exclusively from the JWT principal (`principal.userId()`). The appointment is queried strictly using `appointmentRepository.findByIdAndPatient_Id(appointmentId, patient.getId())`. An appointment that does not exist or belongs to another patient always returns `404 Not Found` with message `"Appointment not found"`, completely preventing IDOR and identifier enumeration.
- Request payload: Does not accept a request body or any caller-supplied `patientId`, `userId`, or status parameter.
- Transition rule: Only appointments in `SCHEDULED` status may be cancelled (`SCHEDULED -> CANCELLED`). Attempting to cancel an appointment in `COMPLETED` or `CANCELLED` status returns `409 Conflict` with message `"Only scheduled appointments can be cancelled"`.
- Persistence & Audit: The cancellation is a state update, not a physical delete (`delete` / `deleteById` are never called). `appointment.status` is set to `CANCELLED` and `appointment.updatedAt` is updated with the current clock instant. The cancelled appointment row remains in the database and subsequent calls to list and detail reflect the `CANCELLED` status. Because the partial unique scheduling constraint covers only `WHERE status = 'SCHEDULED'`, cancelling frees the professional's time slot for new bookings.
- Success response: `200 OK` returning `PatientAppointmentResponse` with updated `status: "CANCELLED"` and professional summary (`id`, `fullName`).

## Medical history endpoints

| Method | Path | Authorization | Success | Notes |
|---|---|---|---|---|
| `GET` | `/api/v1/patients/{patientId}/medical-history` | `MEDICAL_HISTORY_READ` | `200 OK` | Returns the clinical background for an existing patient. An existing patient without a record receives an `EMPTY` response. |
| `PUT` | `/api/v1/patients/{patientId}/medical-history` | `MEDICAL_HISTORY_UPDATE` | `200 OK` | Creates or fully replaces allergies, current medications, relevant conditions, and general observations for the patient. |

The administrative medical-history request uses complete replacement semantics and requires all three collection fields. Each collection accepts at most 100 non-blank values of at most 200 characters; observations accept at most 4000 characters. Duplicate list values are normalized case-insensitively. Entities are never exposed directly.

## Billing endpoints

| Method | Path | Authorization | Success | Notes |
|---|---|---|---|---|
| `GET` | `/api/v1/patients/{patientId}/account-statement` | `BILLING_READ` | `200 OK` | Returns the summary, charges, and payments of an existing patient. An unknown patient returns `404 Not Found`. |
| `POST` | `/api/v1/patients/{patientId}/charges` | `BILLING_CHARGE_CREATE` | `201 Created` | Registers a charge and returns `ChargeResponse`. |
| `POST` | `/api/v1/patients/{patientId}/payments` | `BILLING_PAYMENT_CREATE` | `201 Created` | Registers a payment applied to a charge, or an advance when `chargeId` is omitted, and returns `PaymentResponse`. |

The patient always comes from the path, or from the JWT principal for `/patients/me/account-statement`; a `patientId` sent in a request body is ignored. Monetary values are JSON numbers with two decimals.

`POST /charges` request:
- `concept`: required, non-blank after trimming, at most 200 characters.
- `amount`: required, greater than zero, at most 10 integer digits and 2 decimals.

`POST /payments` request:
- `chargeId`: optional UUID of a charge that belongs to the same patient.
- `amount`: same rules as the charge amount.
- `method`: required; one of `CASH`, `CARD`, `TRANSFER`, `CHECK`.

Payment rules:
- The backend derives `kind`; clients cannot choose it. Without `chargeId` the payment is an `ADVANCE`. With `chargeId`, an amount equal to the pending balance is a `PAYMENT` and a smaller amount is a `PARTIAL_PAYMENT`.
- A charge that does not exist or belongs to another patient returns `404 Not Found` with message `"Charge not found"`.
- An amount greater than the pending balance returns `409 Conflict` with message `"Payment amount exceeds the pending balance of the charge"`. A fully paid charge returns `409 Conflict` with message `"Charge is already paid"`.
- Advances are not applied to charges automatically; they only reduce the account balance.

`AccountStatementResponse`:
- `patientId`.
- `summary`: `charged` (sum of charges), `paid` (sum of payments applied to charges), `advances` (sum of advances), and `balance` = `charged - paid - advances`. A negative balance is credit in favor of the patient.
- `charges`: `id`, `concept`, `amount`, `paid`, `pending` (`amount - paid`), `status` (`PENDING`, `PARTIALLY_PAID`, `PAID`), and `createdAt`, ordered by `createdAt` and `id` ascending.
- `payments`: `id`, `chargeId` (`null` for advances), `kind`, `method`, `amount`, and `createdAt`, ordered by `createdAt` and `id` ascending.

`paid`, `pending`, `status`, and `balance` are always derived from persisted charges and payments; they are never stored or accepted from clients. The statement is not paginated in this first version. Discounts, receipts, cash drawer operations, refunds, voids, installment plans, and fiscal invoicing are not part of this contract.

## Pagination

Large collections should support pagination where necessary.

Suggested parameters:

?page=0
&size=20
&sort=name,asc

## OpenAPI

Public API endpoints should be documented using OpenAPI/Swagger.
