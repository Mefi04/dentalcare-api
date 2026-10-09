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
- `429 Too Many Requests`: a sensitive public endpoint exceeded its configured shared rate limit. The response uses
  the normal error schema and includes `Retry-After` in seconds without disclosing account existence.
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
| `POST` | `/api/v1/auth/activate` | JSON `cui`, `temporaryPassword`, and `newPassword` | `200 OK` with status `ACTIVE` and success message | Compatible legacy activation flow retained for existing clients; no removal is scheduled. New clients should use normal login and `change-initial`. Invalid credentials return generic `401`. |
| `POST` | `/api/v1/auth/login` | JSON `cui` (exactly 13 digits) and `password`; optional `X-Client-Session-Id` UUID | `200 OK` with access token or initial-password-change challenge | A correct temporary password returns only `requiresPasswordChange`, a 10-minute one-use password-change token, and minimal user data; it creates no session/cookie. Active accounts receive the normal response and tab-scoped HttpOnly refresh cookie. Invalid credentials return generic `401`. |
| `POST` | `/api/v1/auth/password/change-initial` | Bearer `passwordChangeToken`, JSON `newPassword`, `confirmation`; optional `X-Client-Session-Id` UUID | `200 OK` with normal login response and tab-scoped refresh cookie | Confirmation must exactly match `newPassword`. Token cannot access normal routes, is bound to the user and expires after 10 minutes. Invalid/expired token returns `401`, replay after completion `409`, invalid password/confirmation `400`. |
| `POST` | `/api/v1/auth/refresh` | Refresh cookie; optional `X-Client-Session-Id` UUID; no token in body | `200 OK` with a new access token | Rotates the selected tab's cookie. Invalid, expired, revoked, or reused tokens return generic `401`. |
| `POST` | `/api/v1/auth/logout` | Refresh cookie; optional `X-Client-Session-Id` UUID | `204 No Content` | Revokes the selected session and clears only its cookie; idempotent where practical. |
| `GET` | `/api/v1/auth/me` | Bearer access token | `200 OK` with the current user view | Never returns password hashes, token material, or session data. |

Successful access-token responses use `tokenType: "Bearer"` and `expiresIn: 1800` by default. Login additionally returns `user` with `id`, `username`, `email`, `status`, `roles`, and `permissions`. For web endpoints (`/api/v1/auth/*`), refresh tokens are transported exclusively via `HttpOnly` cookies and never appear in JSON responses.

When `requiresPasswordChange` is true, the login response has no access token, normal refresh token, or cookie. The frontend must keep the short-lived `passwordChangeToken` only long enough to submit the initial password change, then replace it with the normal access token returned by that endpoint.

For independent accounts in multiple tabs of one browser, the frontend creates a UUID in each tab's `sessionStorage`, sends it as `X-Client-Session-Id` on web login, refresh, and logout, and keeps the access token in that tab's `sessionStorage`. The API names each refresh cookie from that UUID, so the browser can hold multiple HttpOnly refresh cookies without one tab rotating or clearing another tab's session. Omitting the header preserves the legacy single-cookie behavior.

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
| `GET` | `/api/v1/patients/me/notifications` | `ROLE_PATIENT` | `200 OK` | Lists the authenticated patient's persisted in-app notifications, newest first. Supports zero-based `page` and `size` capped at 100. |
| `PATCH` | `/api/v1/patients/me/notifications/{notificationId}/read` | `ROLE_PATIENT` | `200 OK` | Idempotently marks one owned notification as read. Missing and foreign identifiers both return generic `404 Not Found`. |
| `PATCH` | `/api/v1/patients/me/notifications/read-all` | `ROLE_PATIENT` | `200 OK` | Marks every unread notification owned by the authenticated patient as read and returns the number updated. |
| `GET` | `/api/v1/patients/me/notifications/preferences` | `ROLE_PATIENT` | `200 OK` | Returns persisted category preferences. The first read creates defaults enabled for appointments, payments, medications, and clinic updates. |
| `PUT` | `/api/v1/patients/me/notifications/preferences` | `ROLE_PATIENT` | `200 OK` | Replaces all four persisted category preferences for the authenticated patient. |

Patient notifications are persisted in-app records only. Initial event types are appointment scheduled/rescheduled/cancelled, payment registered, prescription issued, and clinic information updated. This contract does not implement mobile push delivery, device tokens, Expo Push, FCM, or APNs. Notification messages must contain only the minimal patient-facing context and must not expose administrative or unnecessary clinical data.
| `GET` | `/api/v1/patients/me/treatment-plans` | `ROLE_PATIENT` | `200 OK` | Lists only approved plans owned by the authenticated patient. Progress is derived from persisted completed procedure executions. |
| `GET` | `/api/v1/patients/me/treatment-plans/{planId}` | `ROLE_PATIENT` | `200 OK` | Returns an owned approved plan with item-level execution progress. Missing, draft, and foreign plans return the same generic `404 Not Found`. |

The standard `PatientResponse` is used for `/patients/me`; it never embeds user credentials, password hashes, roles, or refresh-session data. It exposes `portalAccessStatus` (`PENDING_ACTIVATION`, `ACTIVE`, `INACTIVE`, `LOCKED`, or `null` if no portal account exists) derived directly from the linked user.

## Administrative appointment endpoints

Administrative agenda operations use `/api/v1/appointments` and remain separate from patient self-service under `/api/v1/patients/me/appointments`.

| Method | Path | Authorization | Success | Notes |
|---|---|---|---|---|
| `GET` | `/api/v1/appointments` | `ADMINISTRATOR`, `SECRETARY`, `DENTIST`, or `ASSISTANT` | `200 OK` | Lists persisted appointments. Optional filters: `from`, `to`, `patientId`, `professionalId`, and `status`; supports `page` and `size`. |
| `GET` | `/api/v1/appointments/{appointmentId}` | `ADMINISTRATOR`, `SECRETARY`, `DENTIST`, or `ASSISTANT` | `200 OK` | Returns patient and professional summaries plus schedule, status, and audit timestamps. |
| `POST` | `/api/v1/appointments` | `ADMINISTRATOR` or `SECRETARY` | `201 Created` | Creates a `SCHEDULED` appointment for an existing patient and active dentist. |
| `PATCH` | `/api/v1/appointments/{appointmentId}/schedule` | `ADMINISTRATOR` or `SECRETARY` | `200 OK` | Reschedules an existing `SCHEDULED` appointment to a future instant. |
| `PATCH` | `/api/v1/appointments/{appointmentId}/status` | `ADMINISTRATOR`, `SECRETARY`, `DENTIST`, or `ASSISTANT` | `200 OK` | Moves a `SCHEDULED` appointment to `COMPLETED` or `CANCELLED`; terminal appointments cannot transition again. |

`from` and `to` are inclusive ISO-8601 instants. When both are provided, `from` must not be after `to`. Pagination is zero-based and the service caps page size at 100. A scheduled appointment cannot share the same dentist and instant with another scheduled appointment. Administrative cancellation delegates to the same domain rule used by patient self-service (`AppointmentService.cancel`), changes status from `SCHEDULED` to `CANCELLED`, and never deletes the row.

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

## Treatment plan endpoints

Treatment plans use a clinical draft/approval workflow. Monetary subtotals and totals are derived from item
quantity and unit price and are never persisted.

| Method | Path | Authorization | Success | Notes |
|---|---|---|---|---|
| `GET` | `/api/v1/patients/{patientId}/treatment-plans` | `TREATMENT_PLAN_READ` | `200 OK` | Lists the patient's plans using zero-based pagination, ordered by `createdAt DESC, id DESC`. |
| `POST` | `/api/v1/patients/{patientId}/treatment-plans` | `TREATMENT_PLAN_CREATE` | `201 Created` | Creates a `DRAFT` plan with 1–100 ordered items and a `Location` header. |
| `GET` | `/api/v1/treatment-plans/professionals` | `TREATMENT_PLAN_READ` | `200 OK` | Returns only `id` and `fullName` for active users with an active `DENTIST` role. |
| `GET` | `/api/v1/treatment-plans/{planId}` | `TREATMENT_PLAN_READ` | `200 OK` | Returns plan detail, ordered items, derived subtotals, and derived total. |
| `PUT` | `/api/v1/treatment-plans/{planId}` | `TREATMENT_PLAN_UPDATE` | `200 OK` | Fully replaces the editable fields and items of a `DRAFT` plan. An approved plan returns `409 Conflict`. |
| `PATCH` | `/api/v1/treatment-plans/{planId}/approve` | `TREATMENT_PLAN_APPROVE` | `200 OK` | Performs the sole transition `DRAFT -> APPROVED`; repeated approval returns `409 Conflict`. |

Create and update requests contain `name`, optional `observations`, `professionalId`, and `items`. Each item
contains `name`, optional `tooth`, positive `quantity`, and positive `unitPrice` with at most two decimals.
The patient always comes from the path. Item positions are assigned by the backend from request order.
Clients cannot supply status, positions, totals, subtotals, or audit timestamps.

Patient self-service never reuses the administrative routes above. `PatientTreatmentPlanResponse` excludes
`patientId`, prices, internal observations, clinical notes, completion notes, and audit actors. It exposes the
professional display name, approved plan metadata, ordered procedures, persisted execution states, and progress
derived as completed executions divided by planned quantity. Draft plans remain internal until approved.

## Treatment budget and consent endpoints

Budgets are immutable snapshots of an approved plan. The backend derives patient, item data, prices, subtotal,
total, version, status, timestamps, and authenticated actors. An approved budget is final; a rejected budget
remains in history and permits generation of the next version. Approval never creates a Billing charge.

| Method | Path | Authorization | Success | Notes |
|---|---|---|---|---|
| `POST` | `/api/v1/treatment-plans/{planId}/budgets` | `TREATMENT_BUDGET_CREATE` | `201 Created` | Generates a pending snapshot from an approved plan. Duplicate active budget returns `409 Conflict`. |
| `GET` | `/api/v1/treatment-plans/{planId}/budgets` | `TREATMENT_BUDGET_READ` | `200 OK` | Lists every version, newest first. |
| `GET` | `/api/v1/treatment-budgets/{budgetId}` | `TREATMENT_BUDGET_READ` | `200 OK` | Returns one snapshot and its ordered items. |
| `PATCH` | `/api/v1/treatment-budgets/{budgetId}/approve` | `TREATMENT_BUDGET_DECIDE` | `200 OK` | Performs `PENDING -> APPROVED`. |
| `PATCH` | `/api/v1/treatment-budgets/{budgetId}/reject` | `TREATMENT_BUDGET_DECIDE` | `200 OK` | Performs `PENDING -> REJECTED`. |
| `GET` | `/api/v1/patients/me/treatment-budgets` | `ROLE_PATIENT` | `200 OK` | Lists only clinic-approved budgets owned by the authenticated patient. |
| `GET` | `/api/v1/patients/me/treatment-budgets/{budgetId}` | `ROLE_PATIENT` | `200 OK` | Returns an owned clinic-approved snapshot; missing, unpublished, and foreign IDs share a generic `404`. |
| `PATCH` | `/api/v1/patients/me/treatment-budgets/{budgetId}/accept` | `ROLE_PATIENT` | `200 OK` | Persists the authenticated patient's one-time `ACCEPTED` decision. Repeated decisions return `409`. |
| `PATCH` | `/api/v1/patients/me/treatment-budgets/{budgetId}/reject` | `ROLE_PATIENT` | `200 OK` | Persists the authenticated patient's one-time `REJECTED` decision. Repeated decisions return `409`. |
| `POST` | `/api/v1/treatment-plans/{planId}/consents` | `TREATMENT_CONSENT_CREATE` | `201 Created` | Stores immutable `documentVersion` and `consentText` as pending. |
| `GET` | `/api/v1/treatment-plans/{planId}/consents` | `TREATMENT_CONSENT_READ` | `200 OK` | Lists consent history, newest first. |
| `GET` | `/api/v1/treatment-consents/{consentId}` | `TREATMENT_CONSENT_READ` | `200 OK` | Returns the exact versioned consent and audit actors. |
| `PATCH` | `/api/v1/treatment-consents/{consentId}/accept` | `TREATMENT_CONSENT_ACCEPT` | `200 OK` | Explicitly performs `PENDING -> ACCEPTED`; navigation never implies acceptance. |
| `PATCH` | `/api/v1/treatment-consents/{consentId}/revoke` | `TREATMENT_CONSENT_REVOKE` | `200 OK` | Revokes a pending or accepted consent and records actor/time. |

Invalid transitions return `409 Conflict`; missing resources return `404 Not Found`; validation errors return
`400 Bad Request`. Clients never submit patient ids, monetary totals, statuses, actors, or timestamps.

## Inventory endpoints

| Method | Path | Authorization | Success | Notes |
|---|---|---|---|---|
| `GET` | `/api/v1/inventory/items` | `INVENTORY_READ` | `200 OK` | Lists and filters inventory items (consumables and instruments). Supports `search`, `type`, `category`, `status`, `page`, and `size`. |
| `GET` | `/api/v1/inventory/items/{id}` | `INVENTORY_READ` | `200 OK` | Retrieves detailed item by UUID. Returns `404 Not Found` if not found. |
| `POST` | `/api/v1/inventory/items` | `INVENTORY_WRITE` | `201 Created` | Registers a consumable or instrument with type-specific validation. Duplicate code returns `409 Conflict`. |
| `PUT` | `/api/v1/inventory/items/{id}` | `INVENTORY_WRITE` | `200 OK` | Updates allowed administrative fields (`name`, `description`, `category`, `status`, `unit`, `minimumStock`, `expirationDate`, `location`). Does not alter stock directly. |
| `PATCH` | `/api/v1/inventory/items/{id}/status` | `INVENTORY_WRITE` | `200 OK` | Transitions item status to `ACTIVE` or `INACTIVE`. |
| `DELETE` | `/api/v1/inventory/items/{id}` | `INVENTORY_WRITE` | `204 No Content` | Performs logical deactivation (`INACTIVE`) without physical deletion. |
| `POST` | `/api/v1/inventory/items/{itemId}/movements` | `INVENTORY_WRITE` | `201 Created` | Atomically registers an `ENTRY`, `EXIT`, or `ADJUSTMENT` using the authenticated user as responsible. Inactive items reject new movements. |
| `GET` | `/api/v1/inventory/items/{itemId}/movements` | `INVENTORY_READ` | `200 OK` | Returns the item's paginated Kardex, including inactive items. Supports `type`, `performedBy`, `from`, `to`, `page`, and `size`. |
| `GET` | `/api/v1/inventory/movements` | `INVENTORY_READ` | `200 OK` | Searches the global paginated Kardex. Supports combinable `itemId`, `type`, `performedBy`, `from`, `to`, `page`, and `size` filters. |
| `GET` | `/api/v1/inventory/suppliers` | `INVENTORY_READ` | `200 OK` | Lists and searches suppliers with pagination. Supports `search` and `status`. |
| `GET` | `/api/v1/inventory/suppliers/{id}` | `INVENTORY_READ` | `200 OK` | Retrieves detailed supplier by UUID. |
| `POST` | `/api/v1/inventory/suppliers` | `INVENTORY_WRITE` | `201 Created` | Registers a new active supplier with uniqueness validation on name. |
| `PUT` | `/api/v1/inventory/suppliers/{id}` | `INVENTORY_WRITE` | `200 OK` | Updates supplier general details. |
| `PATCH` | `/api/v1/inventory/suppliers/{id}/status` | `INVENTORY_WRITE` | `200 OK` | Updates supplier status (`ACTIVE`/`INACTIVE`) without physical deletion. |
| `GET` | `/api/v1/inventory/purchases` | `INVENTORY_READ` | `200 OK` | Lists and filters inventory purchases. Supports `supplierId`, `status`, `from`, `to`, `page`, and `size`. |
| `GET` | `/api/v1/inventory/purchases/{id}` | `INVENTORY_READ` | `200 OK` | Retrieves detailed purchase by UUID including lines and audit. |
| `POST` | `/api/v1/inventory/purchases` | `INVENTORY_WRITE` | `201 Created` | Registers a purchase in `PENDING` status for an active supplier. Generates monotonic code `PUR-YYYY-XXXXXX` if not provided. Does not modify stock. |
| `POST` | `/api/v1/inventory/purchases/{id}/receive` | `INVENTORY_WRITE` | `200 OK` | Atomically receives a `PENDING` purchase, updates consumable stock, and logs auditable `ENTRY` movements in Kardex. Re-receiving returns `409 Conflict`. |

Movement `quantity` is always positive. For `ENTRY` and `EXIT` it is the amount added or permanently removed. For `ADJUSTMENT`, request `quantity` is the absolute physical target; the response stores the actual positive change magnitude while `stockBefore` and `stockAfter` show its direction and result. An adjustment requires a non-blank `observation` and is rejected when the target equals the current quantity. Consumable movements update `currentStock`. Instrument entries/exits update both `totalQuantity` and `availableQuantity`; an exit can only remove available instruments. Instrument adjustments preserve the number of unavailable instruments and reject a target below that number.

The backend derives `performedBy` solely from the JWT principal. Clients cannot set IDs, responsible user, timestamps, or before/after values. Kardex results use stable ordering `createdAt DESC, id DESC`, zero-based pagination, and a maximum effective page size of 100. `from` and `to` are inclusive ISO-8601 instants and `from` must not be after `to`. Movement history is immutable: there are no update or delete endpoints. Initial quantities remain controlled catalog-creation values and do not generate retroactive `INITIAL` movements.

## Reports endpoints

| Method | Path | Authorization | Success | Notes |
|---|---|---|---|---|
| `GET` | `/api/v1/reports/dashboard` | `ADMINISTRATOR`, `SECRETARY`, or `CASHIER` | `200 OK` | Returns read-only patient, appointment, and billing metrics from persisted data. Optional inclusive `from` and `to` ISO `LocalDate` values represent clinic operating days in `America/Guatemala` and default to the current clinic month through the current clinic date. |

The dashboard runs its aggregate queries in one read-only repeatable-read transaction. It never loads full
patient, appointment, charge, or payment collections. `patients.registeredInPeriod` uses patient creation time;
appointment counts use `scheduledAt`; period charges and payments use their respective creation times.
`billing.pendingBalance` and `billing.availableCredit` are mutually exclusive values derived from all persisted
charges minus all persisted payments, matching the current account-statement balance semantics. An inverted
period returns `400 Bad Request`.

Dashboard date boundaries are calculated in `America/Guatemala` and converted to `Instant` only after applying
the clinic zone. Inclusive `from`/`to` dates are queried internally as the half-open interval
`[start of from, start of the day after to)`. Detailed upcoming appointments remain in the appointments domain
and are obtained from `GET /api/v1/appointments` with `from=<current instant>`, `status=SCHEDULED`, and the
desired pagination; they are not embedded in the dashboard report.

## Clinical record endpoints

The clinical record module manages consultation encounters, structured diagnoses, chronological evolution notes,
and dental odontogram charting without duplicating medical history, treatment plans, or patient identities.

| Method | Path | Authorization | Success | Notes |
|---|---|---|---|---|
| `GET` | `/api/v1/patients/{patientId}/clinical-record` | `CLINICAL_RECORD_READ` | `200 OK` | Returns aggregated clinical record summary (patient, medical history, latest attention, diagnoses, evolution, current odontogram, treatment plans). |
| `GET` | `/api/v1/patients/{patientId}/clinical-history` | `CLINICAL_RECORD_READ` | `200 OK` | Returns paginated chronological clinical events timeline. |
| `POST` | `/api/v1/patients/{patientId}/clinical-attentions` | `CLINICAL_RECORD_WRITE` | `201 Created` | Registers a clinical attention session. Professional is resolved from JWT principal. Optional `appointmentId` is strictly verified against `patientId` (Anti-IDOR). |
| `GET` | `/api/v1/patients/{patientId}/clinical-attentions` | `CLINICAL_RECORD_READ` | `200 OK` | Lists paginated clinical attention sessions for the patient, ordered by `occurredAt DESC, id DESC`. |
| `GET` | `/api/v1/clinical-attentions/{attentionId}` | `CLINICAL_RECORD_READ` | `200 OK` | Retrieves detail of an attention session. |
| `POST` | `/api/v1/clinical-attentions/{attentionId}/diagnoses` | `CLINICAL_RECORD_WRITE` | `201 Created` | Registers a structured diagnosis (`PRIMARY` or `SECONDARY`). Optional `treatmentPlanId` is strictly verified against the attention patient (Anti-IDOR). Author resolved from JWT. |
| `GET` | `/api/v1/patients/{patientId}/diagnoses` | `CLINICAL_RECORD_READ` | `200 OK` | Lists paginated diagnoses for the patient, with optional `type` filter. |
| `POST` | `/api/v1/clinical-attentions/{attentionId}/evolution` | `CLINICAL_RECORD_WRITE` | `201 Created` | Registers an evolution note. Author is resolved from JWT principal. |
| `GET` | `/api/v1/patients/{patientId}/evolution` | `CLINICAL_RECORD_READ` | `200 OK` | Lists paginated evolution notes for the patient, ordered by `consultationDate DESC, createdAt DESC`. |
| `POST` | `/api/v1/patients/{patientId}/odontogram/findings` | `CLINICAL_RECORD_WRITE` | `201 Created` | Registers an odontogram finding on a tooth. Validates FDI tooth code against the selected dentition. Optional `attentionId` is verified against `patientId`. |
| `GET` | `/api/v1/patients/{patientId}/odontogram` | `CLINICAL_RECORD_READ` | `200 OK` | Returns current odontogram chart with standard teeth for the dentition (defaulting to `HEALTHY`) overlaid with latest findings. |
| `GET` | `/api/v1/patients/{patientId}/odontogram/findings` | `CLINICAL_RECORD_READ` | `200 OK` | Lists paginated historical tooth findings for the patient. |

The consolidated clinical history also derives treatment-procedure events from persisted
`treatment_procedures`; it creates no duplicate clinical row. Every execution contributes a
`TREATMENT_PROCEDURE_STARTED` event at `performedAt`. Completed executions additionally contribute a
`TREATMENT_PROCEDURE_COMPLETED` event at `completedAt`, including procedure, tooth, professional, clinical
observations, and completion notes. These events participate in the same chronological ordering and pagination.

## Prescription endpoints

Prescriptions are immutable issuance records. The authenticated professional is always derived from the JWT;
clients cannot choose the professional, issuance timestamp, status, or identifiers.

| Method | Path | Authorization | Success | Notes |
|---|---|---|---|---|
| `GET` | `/api/v1/patients/{patientId}/prescriptions` | `PRESCRIPTION_READ` | `200 OK` | Lists the patient's prescriptions with zero-based pagination, ordered by `issuedAt DESC, id DESC`. |
| `POST` | `/api/v1/patients/{patientId}/prescriptions` | `PRESCRIPTION_CREATE` | `201 Created` | Issues a prescription as the authenticated active dentist and returns a `Location` header. |
| `GET` | `/api/v1/prescriptions/{prescriptionId}` | `PRESCRIPTION_READ` | `200 OK` | Returns prescription detail for authorized staff. |
| `GET` | `/api/v1/patients/me/prescriptions` | `ROLE_PATIENT` | `200 OK` | Lists only prescriptions owned by the patient linked to the JWT user. |
| `GET` | `/api/v1/patients/me/prescriptions/{prescriptionId}` | `ROLE_PATIENT` | `200 OK` | Returns the owned prescription or `404 Not Found`, preventing ownership disclosure. |

Creation requires `medication`, `presentation`, `dosage`, `frequency`, and `duration`; `instructions` is
optional. The response contains only the patient identity needed for display (`id`, `code`, `name`), the
issuing professional (`id`, `fullName`), prescription instructions, `issuedAt`, and `status`. The first version
creates records with status `ISSUED` and exposes no update or deletion operation.

## Treatment procedure endpoints

Procedure executions reuse approved treatment plans and their items. The authenticated user is always the
professional recorded by the backend; clients cannot submit patient, professional, procedure name, tooth,
status, sequence, or timestamps.

| Method | Path | Authorization | Success | Notes |
|---|---|---|---|---|
| `POST` | `/api/v1/treatment-plans/{planId}/procedures` | `TREATMENT_PROCEDURE_EXECUTE` | `201 Created` | Starts one unit of an approved plan item as `IN_PROGRESS`. Body: `treatmentPlanItemId` and optional `clinicalObservations`. |
| `PATCH` | `/api/v1/treatment-procedures/{procedureId}/complete` | `TREATMENT_PROCEDURE_COMPLETE` | `200 OK` | Performs `IN_PROGRESS -> COMPLETED`; only the dentist who started it may complete it. Optional body: `completionNotes`. |
| `GET` | `/api/v1/treatment-procedures/{procedureId}` | `TREATMENT_PROCEDURE_READ` | `200 OK` | Returns current execution state and traceability data. |
| `GET` | `/api/v1/treatment-plans/{planId}/procedures` | `TREATMENT_PROCEDURE_READ` | `200 OK` | Paginated execution history for one plan. |
| `GET` | `/api/v1/patients/{patientId}/treatment-procedures` | `TREATMENT_PROCEDURE_READ` | `200 OK` | Paginated clinical procedure history for one patient. |

Registration is rejected with `409 Conflict` when the plan is not approved, the item already has an execution
in progress, or its approved quantity has been completed. A plan item from another plan returns `404 Not
Found`. Repeated completion returns `409 Conflict`; attempting to complete another dentist's execution returns
`403 Forbidden`. History is immutable and ordered by `performedAt DESC, id DESC`.

## Authenticated patient password change

| Method | Path | Authorization | Success | Notes |
|---|---|---|---|---|
| `PATCH` | `/api/v1/auth/mobile/password` | Authenticated patient | `204 No Content` | Body: `currentPassword`, `newPassword`. The target user is derived only from the JWT. A successful change stores a BCrypt hash and revokes every refresh session for the user. |

Incorrect current credentials return the generic `Invalid credentials` response. Existing stateless access tokens remain valid only until their normal short expiration; all refresh tokens are revoked, so clients must clear the local session after success and authenticate again.

## Patient password recovery endpoints

Both endpoints are public so the App Móvil can recover an unauthenticated patient account. They never reveal
whether a CUI exists and never return a raw recovery code.

| Method | Path | Authorization | Success | Notes |
|---|---|---|---|---|
| `POST` | `/api/v1/auth/password-recovery/request` | Public | `202 Accepted` | Body: `cui`. Always returns the same generic message. For an eligible active patient, revokes earlier codes and sends a new eight-digit code to the patient's registered email. |
| `POST` | `/api/v1/auth/password-recovery/confirm` | Public | `200 OK` | Body: `cui`, `code`, `newPassword`. Consumes a valid code, stores the password with BCrypt, and revokes all refresh sessions. |

Codes expire after `PASSWORD_RECOVERY_EXPIRATION` (15 minutes by default), are stored only as BCrypt hashes,
and are revoked after `PASSWORD_RECOVERY_MAX_ATTEMPTS` failed attempts (five by default). Unknown accounts,
invalid codes, expired codes, and previously used codes share the generic confirmation error
`Invalid or expired recovery credentials`. Request delivery uses the configured SMTP variables and occurs
asynchronously after persistence commits, without exposing delivery state. Existing access JWTs cannot be recalled
under the current stateless architecture and expire according to the normal short access-token lifetime.

## Clinical documents endpoints

Endpoints for managing patient clinical document metadata:

| Method | Path | Authorization | Success | Notes |
|---|---|---|---|---|
| `GET` | `/api/v1/patients/{patientId}/documents` | `CLINICAL_RECORD_READ` | `200 OK` | Paginated list of documents for an existing patient. Supports optional filter `?type=...` and stable sorting by `documentDate DESC, createdAt DESC, id DESC`. Unknown patient returns `404 Not Found`. |
| `GET` | `/api/v1/patients/{patientId}/documents/{documentId}` | `CLINICAL_RECORD_READ` | `200 OK` | Detail of a single document. Enforces ownership: if the document does not exist or belongs to another patient, returns `404 Not Found` without disclosing existence (anti-IDOR). |
| `POST` | `/api/v1/patients/{patientId}/documents` | `CLINICAL_RECORD_WRITE` | `201 Created` | Registers clinical document metadata. Author is taken exclusively from the authenticated JWT user. Returns `Location: /api/v1/patients/{patientId}/documents/{id}` and `ClinicalDocumentResponse`. |
| `PATCH` | `/api/v1/patients/{patientId}/documents/{documentId}/visibility` | `CLINICAL_RECORD_WRITE` | `200 OK` | Idempotently shares or unshares the document. Body: `{ "visible": true|false }`. Sharing actor and time come from the authenticated user and server clock. |
| `GET` | `/api/v1/patients/me/documents` | `PATIENT` | `200 OK` | Paginated list containing only the authenticated patient's shared documents. Supports optional `type`, `page`, and `size`. |
| `GET` | `/api/v1/patients/me/documents/{documentId}` | `PATIENT` | `200 OK` | Returns only an owned, shared document. Private, foreign, and unknown ids all return the same `404 Not Found`. |
| `GET` | `/api/v1/patients/me/documents/{documentId}/download` | `PATIENT` | `200 OK` | Revalidates ownership and visibility before reading the private object from R2. Internal storage keys are never returned. |

Supported categories (`ClinicalDocumentType`):
- `RADIOGRAPHY`: Dental X-rays (panoramic, periapical, bitewing, etc.)
- `LAB_RESULT`: Clinical laboratory test results
- `INFORMED_CONSENT`: Signed patient procedure consents
- `CLINICAL_REPORT`: Specialist interconsultations and medical summaries
- `PHOTOGRAPHY`: Clinical intraoral and extraoral dental photography
- `OTHER`: Miscellaneous attached clinical documentation

## Appointment request endpoints

An appointment request is not a confirmed appointment. Once accepted, the backend transactionally creates one
`Appointment` and exposes its id as `appointmentId`; clients then use the existing appointment endpoints as the
source of truth.

### Public first-appointment request

`POST /api/v1/public/appointment-requests` is an anonymous intake endpoint separate from contact inquiries and
from the authenticated patient endpoint. It accepts `Idempotency-Key` as a UUID header and a JSON body with
required `fullName` (1–150 characters), `phone` (7–30 allowed phone characters; 7–15 digits after normalization),
and `requestedAt` (future ISO-8601 instant). Optional fields are `cui` (13 digits; only used to associate an
already-existing patient), `email` (valid address, at most 255 characters), `professionalId` (active dentist), and
`reason` (at most 300 characters; scheduling context only, no symptoms or clinical data). No account or patient
record is created. A matching CUI links the existing patient internally; otherwise the request stays unlinked
until clinic staff verifies identity and uses the administrative link action. The response never confirms whether
the CUI matched a patient.

Example request:

```http
POST /api/v1/public/appointment-requests
Idempotency-Key: 447cf365-823d-4f06-a22e-04d9e6a91393
Content-Type: application/json
```

```json
{
  "fullName": "María López",
  "cui": "1234567890123",
  "phone": "+502 5555-0101",
  "email": "maria@example.com",
  "requestedAt": "2027-03-15T16:00:00Z",
  "professionalId": null,
  "reason": "Primera consulta, horario de tarde"
}
```

Success is `202 Accepted` with only `{ "requestId": "<opaque UUID>", "message": "..." }`. The requested time
is a preference, not a reservation; no calendar slot is held. Clinic staff must link an unassociated requester to
a patient, then propose a real available time/professional or accept an existing requested slot through the
existing availability validation. Receptionists and administrators can assign or reassign an active dentist using
`POST /api/v1/appointment-requests/{requestId}/assign-professional` with
`{ "professionalId": "<uuid>" }`. It is available to `ADMINISTRATOR` and `SECRETARY` for public requests in
`PENDING` or `PROPOSED`. Assignment is stored separately from `requestedProfessional`, so receptionist assignment
does not overwrite the visitor's dentist preference. It does not reserve a slot, change request status, or confirm
an appointment. Its response includes the refreshed administrative projection and `assignedProfessional`.
Changing assignment does not silently alter an already-sent `proposedProfessional`/`proposedAt`; clinic staff can
review and explicitly submit a replacement proposal through the existing proposal endpoint.
Repeating an identical payload with the same key returns the same receipt;
reusing a key with another payload or submitting an equivalent active request for the same CUI/time/preferred
dentist returns generic `409 Conflict`. Missing/invalid fields or a past time return `400 Bad Request`, an
unavailable/inactive professional returns generic `409 Conflict`, and excessive requests return `429 Too Many
Requests` with `Retry-After`. Rate limiting is 5 requests per IP per 15 minutes (configurable); clients should
generate one UUID per submission and retain it across network retries.

Administrative `GET /api/v1/appointment-requests` and `GET /api/v1/appointment-requests/{requestId}` include
public requests, including those linked to an existing patient by CUI and those with no patient link. They expose
`source: "PUBLIC"` independent of the patient link, `contact` (`fullName`, `phone`, and available `cui`, `email`,
`reason`), `requestedAt`, actual `status`, `requestedProfessional`, and `assignedProfessional` (or `null`). The
existing `publicRequester` field is retained for compatibility. `contact` is non-clinical and staff-only; public
intake never returns clinical data. Patient-origin requests identify as `PATIENT_PORTAL` in the administrative
projection and otherwise retain their existing patient data and workflow. `POST /api/v1/appointment-requests/{requestId}/link-patient` accepts
`{ "patientId": "<uuid>" }`; when the intake included CUI, it must match the selected patient's DPI. Linking an
existing patient record is required before public acceptance, but never confirms the proposal. The legacy
`POST /api/v1/appointment-requests/{requestId}/confirm-public-proposal` is retained for compatibility and returns
`409 PUBLIC_PATIENT_ACCEPTANCE_REQUIRED`; only the verified public conversation can accept. Existing patient
endpoints and their contracts are unchanged.

Assignment errors use `409 Conflict` with a stable `code`: `APPOINTMENT_REQUEST_NOT_PUBLIC`,
`APPOINTMENT_REQUEST_STATE_NOT_ELIGIBLE` (includes the actual and allowed states), or
`PROFESSIONAL_NOT_AVAILABLE`. Appointment creation conflicts use `APPOINTMENT_TIME_UNAVAILABLE` and a message
that identifies the occupied date/time. Assignment itself performs no availability reservation, so it cannot
produce an appointment-slot conflict.

| Method | Path | Authorization | Success | Notes |
|---|---|---|---|---|
| `GET` | `/api/v1/appointment-requests` | `ADMINISTRATOR`, `SECRETARY` | `200 OK` | Filters: `from`, `to`, `patientId`, `professionalId`, `status`, `page`, `size`. |
| `GET` | `/api/v1/appointment-requests/{requestId}` | `ADMINISTRATOR`, `SECRETARY` | `200 OK` | Administrative detail. |
| `POST` | `/api/v1/appointment-requests/{requestId}/accept` | `ADMINISTRATOR`, `SECRETARY` | `200 OK` | Accepts requested slot; a successful retry returns the same confirmation. |
| `POST` | `/api/v1/appointment-requests/{requestId}/proposal` | `ADMINISTRATOR`, `SECRETARY` | `200 OK` | Body: future `proposedAt`, optional `professionalId`. |
| `POST` | `/api/v1/appointment-requests/{requestId}/reject` | `ADMINISTRATOR`, `SECRETARY` | `200 OK` | Rejects an open request. |
| `POST` | `/api/v1/appointment-requests/{requestId}/link-patient` | `ADMINISTRATOR`, `SECRETARY` | `200 OK` | Links a public request after identity verification; CUI must match when supplied. |
| `POST` | `/api/v1/appointment-requests/{requestId}/assign-professional` | `ADMINISTRATOR`, `SECRETARY` | `200 OK` | Assigns/reassigns an active dentist to an open public request; does not confirm a slot. Body: `professionalId`. |
| `GET` | `/api/v1/appointment-requests/{requestId}/availability?professionalId={uuid}&date=YYYY-MM-DD` | `ADMINISTRATOR`, `SECRETARY` | `200 OK` | Returns only booked instants for that dentist on the Guatemala clinic date. Proposal submission validates availability again. |
| `POST` | `/api/v1/appointment-requests/{requestId}/messages` | `ADMINISTRATOR`, `SECRETARY` | `200 OK` | Body is a predefined non-clinical template type; free-text/clinical messages are not accepted. |
| `POST` | `/api/v1/appointment-requests/{requestId}/confirm-public-proposal` | `ADMINISTRATOR`, `SECRETARY` | `409 Conflict` | Legacy compatibility route; public proposal acceptance is restricted to the verified conversation. |
| `POST` | `/api/v1/public/appointment-requests` | Public | `202 Accepted` | First appointment intake; request time is not reserved. Requires UUID `Idempotency-Key`; see contract above. |
| `POST` | `/api/v1/public/appointment-requests/{requestId}/verification-codes` | Public | `202 Accepted` | Body `channel: SMS|EMAIL`; always returns a generic acknowledgment. OTP expires in 10 minutes, max five attempts. |
| `POST` | `/api/v1/public/appointment-requests/{requestId}/verification` | Public | `200 OK` | Body `code`; success returns a scoped 24-hour `conversationToken`. Invalid code is 401, expired/consumed code is 410. |
| `GET` | `/api/v1/public/appointment-requests/{requestId}/conversation` | Conversation bearer token | `200 OK` | Returns only that request's state, proposal and messages. |
| `POST` | `/api/v1/public/appointment-requests/{requestId}/decision` | Conversation bearer token | `200 OK` | Body `decision: ACCEPT|REJECT`; UUID `Idempotency-Key` required. Accept creates a cita only if an existing patient record is linked and slot still free. |
| `POST` | `/api/v1/patients/me/appointment-requests` | `PATIENT` | `201 Created` | Body: `professionalId`, future `requestedAt`; patient comes from JWT. |
| `GET` | `/api/v1/patients/me/appointment-requests` | `PATIENT` | `200 OK` | Lists only owned requests. |
| `GET` | `/api/v1/patients/me/appointment-requests/{requestId}` | `PATIENT` | `200 OK` | Foreign and unknown ids both return 404. |
| `POST` | `/api/v1/patients/me/appointment-requests/{requestId}/accept-proposal` | `PATIENT` | `200 OK` | Creates exactly one appointment. |
| `POST` | `/api/v1/patients/me/appointment-requests/{requestId}/reject-proposal` | `PATIENT` | `200 OK` | Rejects the clinic proposal. |
| `POST` | `/api/v1/patients/me/appointment-requests/{requestId}/cancel` | `PATIENT` | `200 OK` | Cancels a pending request. |

`actionRequiredBy` is derived as `CLINIC`, `PATIENT`, or `NONE`. Existing direct appointment endpoints remain
compatible.

### Verified public first-appointment conversation

Public intake creates `PENDING_CLINIC`; patient-portal requests keep their existing `PENDING`/`PROPOSED` lifecycle.
Reception retains `ADMINISTRATOR`/`SECRETARY` access to list/detail, dentist assignment and proposal actions. A
public proposal moves to `PENDING_PATIENT`, includes `proposedExpiresAt` (24-hour default), and adds a fixed,
non-clinical clinic message to the conversation. Reception can inspect one dentist's already-booked instants for a
Guatemala clinic date with `GET /api/v1/appointment-requests/{requestId}/availability?professionalId={uuid}&date=YYYY-MM-DD`;
this response contains no patient details, and proposal submission checks the slot again.

The anonymous conversation routes are `POST /api/v1/public/appointment-requests/{requestId}/verification-codes`
(`{"channel":"SMS"|"EMAIL"}`), `POST .../{requestId}/verification` (`{"code":"123456"}`),
`GET .../{requestId}/conversation`, and `POST .../{requestId}/decision` (`{"decision":"ACCEPT"|"REJECT"}`).
The OTP lasts 10 minutes, has five attempts, and is sent only to the phone/email captured on that request; the
request-code response is generic to prevent request enumeration. SMS uses Twilio credentials from environment;
email uses configured SMTP. Successful verification returns a random 256-bit bearer token stored only as SHA-256,
scoped to exactly one request and valid for 24 hours. Invalid token is 401, expired token/code/proposal is 410, and
per-IP throttling returns 429 with `Retry-After`. OTPs are BCrypt hashes and never returned.

Conversation reads and decisions require `Authorization: Bearer <conversationToken>`. `Idempotency-Key` (UUID) is
required for decisions. Rejecting or expiring a proposal returns the request to `PENDING_CLINIC`; it remains open.
Acceptance rechecks availability and creates the appointment transactionally. A partial unique PostgreSQL index
on active dentist/time reservations closes concurrent booking races; an occupied slot returns `409` with
`APPOINTMENT_TIME_UNAVAILABLE` and creates no appointment. If no patient record exists, acceptance returns
`409 PATIENT_RECORD_LINK_REQUIRED`; reception must link an existing record first. CUI is never used as chat
authentication, no account/record is auto-created, and no arbitrary patient messages are accepted. Reception
messages use predefined scheduling templates to avoid collecting clinical information.

## Waiting room endpoints

| Method | Path | Authorization | Success | Notes |
|---|---|---|---|---|
| `GET` | `/api/v1/appointments/waiting-room` | `ADMINISTRATOR`, `SECRETARY`, `DENTIST`, `ASSISTANT` | `200 OK` | Guatemala clinic `date`; optional `status`, `professionalId`, `page`, `size`. |
| `GET` | `/api/v1/appointments/{appointmentId}/waiting-room` | Same read roles | `200 OK` | Operational detail. |
| `POST` | `/api/v1/appointments/{appointmentId}/waiting-room/check-in` | `ADMINISTRATOR`, `SECRETARY`, `ASSISTANT` | `201 Created` | Registers arrival for a scheduled appointment of the current clinic day. |
| `PATCH` | `/api/v1/appointments/{appointmentId}/waiting-room/status` | `ADMINISTRATOR`, `SECRETARY`, `ASSISTANT` | `200 OK` | Body `status`; only `ARRIVED -> WAITING -> READY`. |

Duplicate check-in, invalid transitions and inactive appointments return `409 Conflict`. Responsible users are
always obtained from JWT.

## Pagination

Large collections should support pagination where necessary.

Suggested parameters:

?page=0
&size=20
&sort=name,asc

## OpenAPI

Public API endpoints should be documented using OpenAPI/Swagger.
