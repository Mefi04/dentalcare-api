# Security

## Scope and source of truth

This document defines the phase-one authentication and authorization model for the DentalCare backend. It is a design contract for Issues #5 through #8; it does not implement persistence, endpoints, tokens, or authorization rules.

Backend contracts are the source of truth for frontend integration. Frontend middleware may improve navigation and user experience, but it is not a security boundary. Every protected operation must be authenticated and authorized by the backend.

Authentication establishes who the caller is. Authorization determines whether that authenticated caller may perform a specific action.

## Conceptual identity and authorization model

### User

A user account has:

- `id`: UUID primary identifier.
- `username`: unique account name. It is normalized before uniqueness checks and persistence.
- `email`: unique email address. It is normalized before uniqueness checks and persistence.
- `cui`: unique, 13-digit Guatemalan CUI/DPI used exclusively for login.
- `passwordHash`: BCrypt hash; a raw password is never persisted.
- `status`: one of the account statuses defined below.
- `createdAt` and `updatedAt`: audit timestamps.
- `lastLoginAt`: nullable timestamp of the latest successful login.

Username and email normalization must be deterministic and applied consistently before lookups and database uniqueness checks. The exact normalization algorithm belongs to the implementation design, but it must not allow casing or formatting variants to bypass uniqueness.

User identity is a shared security concern. It must not contain clinical data or depend on a clinical business entity.

### Account statuses

- `PENDING_ACTIVATION`: Account is created with a temporary password. Web login with the correct temporary password returns a restricted initial-password-change challenge; `/api/v1/auth/activate` remains available as a compatible legacy flow.
- `ACTIVE`: Account is active and eligible for normal authentication and session creation.
- `INACTIVE`: Account is deactivated by administrative action.
- `LOCKED`: Account is locked.

Authentication and session use respect account status. Accounts in `PENDING_ACTIVATION` cannot create an authenticated session or access protected routes until the initial password is changed.

### Role and Permission

A role groups permissions and has:

- `id`: UUID primary identifier.
- `code`: unique stable machine-readable identifier.
- `name`: display name.
- `description`: human-readable purpose.
- `active`: whether the role may currently grant permissions.

A permission represents one backend capability and has:

- `id`: UUID primary identifier.
- `code`: unique stable machine-readable identifier.
- `description`: human-readable purpose.

Permission codes use the `RESOURCE_ACTION` convention. For example, `PATIENT_READ` illustrates the syntax only; it is not an approved permission or a requirement for a clinical module.

Users and roles have a many-to-many relationship through `UserRole`. Roles and permissions have a many-to-many relationship through `RolePermission`. Concrete roles and the permission catalog remain intentionally undefined until their business requirements are approved.

## Password security

- Store passwords only as BCrypt hashes using an application-selected work factor.
- Never store, log, return, or place a raw password in a token.
- Compare passwords through the password encoder; do not compare hashes directly.
- Password input must be validated before hashing. The baseline policy enforces a non-blank password between 8 and 128 characters.
- Initial account password change establishes the user's permanent password through the restricted login challenge. The legacy `/api/v1/auth/activate` endpoint remains compatible for existing consumers and is not scheduled for removal.
- Patient password recovery uses an expiring, one-time, out-of-band code whose raw value is never persisted.

## Access token

The access token is a signed JWT with a default lifetime of 30 minutes, configured through `JWT_ACCESS_EXPIRATION`. Clients send it as:

```http
Authorization: Bearer <access-token>
```

The token contains only the minimum authorization context:

- `sub`: user UUID.
- `authorities`: effective authority codes.
- `jti`: unique token identifier.
- `iat`: issued-at time.
- `exp`: expiration time.
- `typ`: `access`.

It must not contain passwords, password hashes, clinical data, addresses, phone numbers, refresh tokens, or other sensitive data. Role or permission changes are not reflected in an already issued access token; they take effect when that token expires or a new token is issued.

Unless a future server-side access-token revocation mechanism is introduced, an issued access token remains valid until expiration even when its associated refresh session is revoked.

## Agenda permission matrix

- `PATIENT`: only owned `/patients/me/appointment-requests/**` and `/patients/me/appointments/**` resources.
- `ADMINISTRATOR`, `SECRETARY`: process requests and manage agenda/waiting room.
- `DENTIST`: read administrative agenda and waiting room, without processing requests or check-in transitions.
- `ASSISTANT`: read agenda/waiting room and operate waiting-room transitions.
- `CASHIER`: no appointment-request or waiting-room access.

Patient identity comes from the JWT-linked `Patient`; foreign request ids return the same 404 as unknown ids.

The anonymous `POST /api/v1/public/appointment-requests` endpoint accepts only bounded, non-clinical scheduling
and contact data, is rate limited by IP, and returns an opaque acknowledgment without patient data or a CUI match
signal. Conversation verification and message writes are additionally
rate limited by request identity. Supplied CUI is stored only as a contact snapshot; it is not used to find or link a patient
and does not authenticate the requester. No account or patient is automatically created. Contact snapshots are
exposed only through the staff-authorized appointment-request inbox. Unmatched
requests must be identity-verified and linked by authorized staff before confirmation. Client retries must reuse
the UUID idempotency key; a different payload with that key receives only a generic conflict response.
Only administrators and secretaries can assign/reassign an active dentist on an open public request. Assignment
is audited and does not reserve a time or transition the request out of its open state.

Public first-appointment intake issues a random 256-bit scoped bearer token directly in the successful receipt;
it expires after seven days and is stored only as SHA-256. The same UUID `Idempotency-Key` and identical body may
be retried to rotate and receive a fresh token, while a different body receives a generic conflict. This supports
lost HTTP responses without storing a recoverable token. The token grants access only to its single scheduling
conversation; it is not proof of legal identity or ownership of the submitted phone/email. CUI is never used as
authentication. The visitor should save the token securely in browser session storage and must not place it in a
URL. There is no email/SMS verification or automatic recovery; losing both the token and original idempotency key
means the conversation cannot be recovered. Reception's existing identity verification remains required before
linking/creating a patient record and accepting a proposal.

Messages are limited to 500 characters, plain text, logistics-only language, with a per-request rate limit and
sender/request idempotency key. Stored message rendering must remain text-only. The previous OTP endpoints remain
available for compatibility, but the first-appointment web flow does not require them. Notification outbox recipients and
payloads are AES-256-GCM encrypted at rest and erased after terminal processing; provider status never exposes
contact details. The administrative WhatsApp route only constructs a reviewed manual draft and never sends it.

Patient clinical documents follow the same ownership model. Staff require `CLINICAL_RECORD_WRITE` to explicitly
share or unshare a document; documents are private by default and sharing is audited with the authenticated staff
user and server timestamp. `/api/v1/patients/me/documents/**` resolves the patient only from the JWT, returns only
owned documents whose visibility is enabled, and uses an indistinguishable `404` for private, foreign, and unknown
identifiers. Download authorization is checked before accessing the private R2 object, and storage keys are never
included in patient responses.

Clinical document uploads accept only PDF, JPEG, and PNG files up to the configured 10 MB default. Validation
normalizes the filename, checks MIME/extension agreement, and streams the content through bounded buffers. PDF
validation checks version, objects, catalog, cross-reference target, trailer/root and EOF; JPEG validation walks
marker segment lengths through scan data and EOI; PNG validation walks chunk lengths and verifies IHDR, CRCs and
final IEND. These structural checks are not antimalware analysis. The bytes consumed by R2 are counted again and
must exactly match the validated multipart size. Files are spooled by the servlet instead of being retained in
application heap. Invalid size, unsupported
type, corrupt content, and throttling return 413, 415, 422, and 429 respectively.

Upload and download frequency reuse the shared PostgreSQL-backed rate limiter. Separate configurable per-instance
semaphores bound simultaneous R2 transfers; these concurrency limits are intentionally not distributed. R2 calls
have explicit attempt and total timeouts. Downloads are attachments with `Cache-Control: no-store, private` and
`X-Content-Type-Options: nosniff`. The application does not claim antimalware scanning or quarantine; no scanner or
paid external service is part of this implementation.

R2 and PostgreSQL cannot participate in one atomic transaction. An uploaded object is therefore registered for
compensating deletion whenever the database transaction does not commit, including failures after the metadata
flush. Cleanup is best effort and deliberately logs neither patient identifiers nor object keys. Operations must
still monitor cleanup failures because an unavailable R2 service can leave an object requiring operational
reconciliation.
Staff responsible for processing and check-in also comes exclusively from JWT.

## Settings permission matrix

| Permission | Roles | Scope |
|---|---|---|
| `SETTINGS_READ` | `ADMINISTRATOR` | Read the singleton clinic configuration and operational procedure catalog. |
| `SETTINGS_WRITE` | `ADMINISTRATOR` | Update clinic data and create, update, activate, or deactivate catalog items. |

Settings mutations derive `created_by` and `updated_by` exclusively from `AuthenticatedUser.userId()` in the
validated access token. These actor fields, roles, and authorities are never accepted from request payloads.

The anonymous public surface is restricted to `GET /api/v1/public/clinic` and
`GET /api/v1/public/services`. It exposes visitor-safe projections of Settings data only; all Settings routes
and every non-GET public route still require authentication and their existing authority checks remain unchanged.

`GET /api/v1/public/professionals` and `GET /api/v1/public/professionals/{id}` are also anonymous read-only
projections. They return only visible profiles whose linked user remains active and has the active `DENTIST` role.
Profile administration stays under the existing administrator-only `/api/v1/users/**` surface.

`POST /api/v1/public/contact-inquiries` is an anonymous, exact public route for general inquiries only. It does not
create appointments, process emergencies, accept files, or collect structured clinical data. Input is persisted as
plain text and is not logged.

## Distributed rate limiting

Authentication and anonymous contact entry points use PostgreSQL-backed fixed-window counters, so all application
instances share the same limits. The protected routes are web/mobile login, password-recovery request, and public
contact inquiry. Login and recovery apply both an IP bucket and a normalized-identity bucket; bucket keys contain
only SHA-256 digests, never CUI values, passwords, request bodies, or tokens. Contact uses only an IP bucket.

An exceeded bucket returns `429 Too Many Requests` with a generic message and `Retry-After`. The block is recorded
as `SECURITY_RATE_LIMIT_BLOCKED` without request payload or identifier. Counters expire automatically and cannot
permanently lock an account.

Client IP defaults to the servlet connection address and ignores forwarding headers. `X-Forwarded-For` is considered
only when the immediate peer matches a CIDR configured in `RATE_LIMIT_TRUSTED_PROXIES`; the resolver walks the chain
from the trusted edge toward the first untrusted address. The reverse proxy must overwrite, not append to an
untrusted client-provided header. Keep the setting empty when the application is directly internet-facing.

Limits and windows are configured with `RATE_LIMIT_*` variables documented in `.env.example`. Disabling the feature
is intended only for isolated troubleshooting. Production instances must share the same PostgreSQL database and
configuration.

Only the existing administrator role receives these permissions. Other staff modules must not reuse settings
permissions as a shortcut for consuming catalog data; any future cross-module contract requires its own approved
authorization design.

## Refresh token and session

The refresh token is an opaque, cryptographically secure random value, not a JWT. Its raw value is sent only to the client and is never persisted. The backend stores only a cryptographic hash suitable for deterministic token lookup.

Each `RefreshSession` has:

- `id`: UUID primary identifier.
- `userId`: UUID of the owning user.
- `familyId`: UUID shared by every token produced from the same login session.
- `tokenHash`: hash of the current opaque token.
- `createdAt`: session creation time.
- `expiresAt`: absolute expiration time.
- `lastActivityAt`: last successful refresh activity.
- `revokedAt`: nullable revocation time.
- `replacedBySessionId`: nullable UUID identifying the session created by rotation.

The absolute lifetime is seven days by default and is configured through `JWT_REFRESH_EXPIRATION`. A session also expires after 24 hours of inactivity by default, configured through `JWT_REFRESH_INACTIVITY_TIMEOUT`. Both absolute expiration and inactivity are evaluated on every refresh request.

### Mandatory rotation and reuse detection

Every successful refresh rotates the token:

1. Read the opaque refresh token from the cookie.
2. Hash it using the configured lookup strategy.
3. Find the matching refresh session.
4. Reject it with a generic authentication failure if no matching session exists.
5. If the session was already replaced, treat the request as reuse, revoke its entire token family, and return a generic authentication failure.
6. Reject it generically if it is otherwise revoked, absolutely expired, or inactive.
7. Validate that the owning user is still eligible to authenticate.
8. Generate a new opaque refresh token and create its hashed replacement session in the same family, preserving the original absolute lifetime.
9. Mark the presented session as revoked, link `replacedBySessionId`, and commit both changes atomically.
10. Issue a new access token and replace the refresh-token cookie only after the rotation succeeds.

Presenting a previously rotated token is token reuse. On detected reuse, the backend revokes every active refresh session in that `familyId` and returns a generic `401 Unauthorized` response. Rotation and family revocation require transactional consistency. Refresh processing locks the matching session row and uses `READ COMMITTED` isolation so concurrent requests for the same token are serialized before validation and rotation. Reuse-driven family revocation participates in that transaction and is committed with the `401` response.

## Browser transport and CORS

The refresh token is transported in a host-only cookie with:

- `HttpOnly` enabled.
- `Secure` enabled outside local HTTP development.
- `SameSite=Lax`.
- `Path=/api/v1/auth`.
- No `Domain` attribute unless a reviewed deployment requirement makes it necessary.

The frontend must not read the refresh token or store it in `localStorage`, `sessionStorage`, or persistent JavaScript variables. Cross-origin browser clients require an explicit allowed origin from `FRONTEND_URL` and credentialed CORS requests; wildcard origins are incompatible with credentials.

### Isolating browser tabs

Web clients that allow separate accounts in separate tabs must generate one random UUID per tab and persist that identifier in that tab's `sessionStorage`. They send it in `X-Client-Session-Id` on web login, refresh, and logout. The API validates it as a canonical UUID and uses it only to select a cookie name (`refreshToken-<uuid>`); the refresh token remains HttpOnly and is never exposed to JavaScript. Logout expires only the cookie selected by that identifier. The access token returned by login or refresh must also be held in tab-scoped `sessionStorage`, not shared `localStorage`, so each tab sends its own Bearer token. The identifier is a namespace, not an authentication credential. Requests without the header retain the legacy `refreshToken` cookie behavior.

## Session policy

Each successful login creates an independent refresh-token family, allowing multiple devices or browsers. Phase one imposes no maximum number of concurrent sessions.

A refresh session ends through logout, absolute expiration, inactivity expiration, token-family reuse response, or when the account is no longer eligible under the security rules. The model supports a future “logout all devices” operation by revoking all active sessions for a user, but that operation is not part of this issue.

## Authentication API contracts

All error responses follow the centralized `ApiErrorResponse` contract in `API-CONVENTIONS.md`. Authentication failures use generic messages and never reveal whether an account, session, or token exists.

### Account activation

`POST /api/v1/auth/activate`

Public endpoint permitting users in `PENDING_ACTIVATION` status to authenticate with their temporary password and define their permanent password.

Request:

```json
{
  "cui": "1234567890123",
  "temporaryPassword": "TemporaryPassword123!",
  "newPassword": "NewPermanentPassword123!"
}
```

Success: `200 OK`:

```json
{
  "status": "ACTIVE",
  "message": "Account activated successfully"
}
```

Key rules:
- The temporary password is valid solely for activation; it cannot be used for direct login.
- Successful activation permanently replaces the temporary password hash with the BCrypt hash of the new password.
- The account transitions atomically to `ACTIVE`.
- No access token or refresh cookie is generated upon activation. The user must subsequently authenticate via `POST /api/v1/auth/login`.
- To prevent user enumeration, unknown CUI numbers, non-pending account statuses (`ACTIVE`, `INACTIVE`, `LOCKED`), and invalid temporary passwords all return a generic `401 Unauthorized` with message `"Invalid activation credentials"`.
- The endpoint remains available for existing consumers; no removal date has been set. New web clients should use the initial-password challenge flow below.

### Initial password change during web login

`POST /api/v1/auth/login` continues to accept `{ "cui", "password" }`. Active accounts receive the established login response and their refresh cookie. A correct temporary password for a `PENDING_ACTIVATION` account returns `200 OK` with:

```json
{
  "requiresPasswordChange": true,
  "passwordChangeToken": "<signed-token>",
  "user": { "id": "<uuid>", "fullName": "<display-name>" }
}
```

No access token, refresh token, or cookie is created in this response. Unknown users, wrong passwords, and `INACTIVE`/`LOCKED` accounts receive the same generic `401 Invalid credentials`; the CUI is not disclosed.

The change token is an RS256 JWT with only user subject, token purpose, token ID, and timestamps. It has a fixed 10-minute lifetime, carries no authorities, and is rejected by the normal access-token filter. It is accepted only by `POST /api/v1/auth/password/change-initial` with `Authorization: Bearer <passwordChangeToken>` and body `{ "newPassword": "...", "confirmation": "..." }`. Confirmation must exactly match; the password policy remains 8–128 non-blank characters.

The change endpoint locks the user row and requires `PENDING_ACTIVATION`; its successful atomic transition to `ACTIVE` makes the token unusable on every subsequent request, including concurrent replay. Expired, malformed, or wrong-purpose tokens return generic `401`; replay after completion returns `409`; password/confirmation validation returns `400`. Success immediately returns the normal login response and creates a refresh session. The optional `X-Client-Session-Id` UUID chooses the matching `refreshToken-<uuid>` HttpOnly cookie, falling back to the legacy `refreshToken` cookie when omitted. No database migration is needed: the existing account status is the persisted single-use marker.

### Patient password recovery

`POST /api/v1/auth/password-recovery/request` accepts a 13-digit `cui` and always returns `202 Accepted`
with the same generic message, regardless of account existence, status, patient linkage, email availability,
or mail delivery. Eligible active patient accounts receive an eight-digit code through their registered patient
email. Delivery runs outside the response path to reduce timing-based account enumeration.

`POST /api/v1/auth/password-recovery/confirm` accepts `cui`, `code`, and `newPassword`. A valid code is
single-use, expires after 15 minutes by default, and is revoked after five failed attempts by default. Invalid,
expired, revoked, used, or unknown credentials return the same generic `400 Bad Request`. Successful recovery
stores only the BCrypt password hash, consumes the code, revokes other outstanding codes, and revokes every
refresh session for the account. Existing stateless access JWTs retain only their normal short remaining life.

The database stores only a BCrypt code hash plus minimal lifecycle audit timestamps and attempt count. Raw
passwords and codes are never persisted or logged. A new request revokes previous outstanding codes.

### Login

`POST /api/v1/auth/login`

Request:

```json
{
  "cui": "1234567890123",
  "password": "raw-password"
}
```

Success: `200 OK`, creates a refresh session, sets the refresh cookie, and returns:

```json
{
  "accessToken": "signed-jwt",
  "tokenType": "Bearer",
  "expiresIn": 1800,
  "user": {
    "id": "uuid",
    "username": "username",
    "email": "user@example.com",
    "status": "ACTIVE",
    "roles": [],
    "permissions": []
  }
}
```

The refresh token appears only in the cookie. Invalid credentials return a generic `401 Unauthorized`; malformed or invalid request fields return `400 Bad Request`.

### Refresh

`POST /api/v1/auth/refresh`

The request has no token in its body; the refresh token comes from the cookie. Success returns `200 OK`, rotates the refresh cookie, and returns:

```json
{
  "accessToken": "signed-jwt",
  "tokenType": "Bearer",
  "expiresIn": 1800
}
```

A missing, invalid, expired, inactive, revoked, or reused refresh token returns a generic `401 Unauthorized`.

### Logout

`POST /api/v1/auth/logout`

The backend identifies the current refresh session from the cookie, revokes it, clears the cookie, and returns `204 No Content`. Logout should be idempotent where practical so a missing or already invalidated session does not expose internal session state.

### Current user

`GET /api/v1/auth/me`

Requires a Bearer access token. Success returns `200 OK` with the current user's `id`, `username`, `email`, `status`, roles, and permissions. It never includes password hashes, refresh tokens, or refresh-session data.

## Mobile authentication and session renewal

Mobile applications developed with Expo / React Native operate under different architectural constraints than browser applications:

- **No browser cookie jar**: Native HTTP engines (OkHttp on Android, NSURLSession on iOS) do not automatically provide the cookie lifecycle guarantees that browsers provide, especially during background tasks, headless execution, or across app restarts.
- **Hardware-backed secure storage**: Mobile platforms provide native hardware-backed encrypted storage (`expo-secure-store`, Android Keystore, iOS Keychain). Following RFC 8252 and OWASP MASVS recommendations, mobile clients securely store opaque refresh tokens in device secure storage.
- **Dedicated endpoints (`/api/v1/auth/mobile/*`)**: To prevent any accidental weakening or modification of the web authentication contracts (which strictly forbid refresh tokens in JSON and require `HttpOnly` cookies), the backend exposes dedicated mobile endpoints sharing 100% of the underlying identity, domain, and session models.

### Shared security invariants between Web and Mobile

Both web and mobile authentication share the exact same security foundation:
- Single source of truth: `users` and `refresh_sessions` tables.
- Cryptographic hashing: Refresh tokens are generated as 32-byte cryptographically secure random values and stored only as SHA-256 hashes.
- Token family tracking: Every session rotation maintains the initial `familyId`.
- Reuse detection: If a rotated or superseded token is presented, the entire family is immediately revoked, and the request is rejected with `401 Unauthorized`.
- Expiration rules: Both absolute expiration (default 7 days) and sliding inactivity expiration (default 24 hours) apply equally to mobile sessions.
- Account eligibility: Account must remain in `ACTIVE` status; deactivated (`INACTIVE`), locked, or unactivated (`PENDING_ACTIVATION`) accounts are rejected.

### Mobile login

`POST /api/v1/auth/mobile/login`

Request:

```json
{
  "cui": "1234567890123",
  "password": "raw-password"
}
```

Success: `200 OK`, creates a refresh session, sets no cookies, and returns:

```json
{
  "accessToken": "signed-jwt",
  "refreshToken": "opaque-refresh-token",
  "tokenType": "Bearer",
  "expiresIn": 1800,
  "user": {
    "id": "uuid",
    "username": "username",
    "email": "user@example.com",
    "status": "ACTIVE",
    "roles": [],
    "permissions": []
  }
}
```

Key rules:
- Returns the initial opaque refresh token directly in the response payload for storage in `expo-secure-store`.
- No `Set-Cookie` header is emitted.
- Invalid credentials return generic `401 Unauthorized`; validation errors return `400 Bad Request`.

### Mobile refresh

`POST /api/v1/auth/mobile/refresh`

Request:

```json
{
  "refreshToken": "opaque-refresh-token"
}
```

Success: `200 OK`, rotates the session in the database, sets no cookies, and returns:

```json
{
  "accessToken": "new-signed-jwt",
  "refreshToken": "new-rotated-opaque-refresh-token",
  "tokenType": "Bearer",
  "expiresIn": 1800
}
```

Key rules:
- Performs mandatory rotation: generates a new refresh token, atomically replaces the presented session, and invalidates the previous token.
- Returns the newly rotated refresh token in the response payload; the mobile client must overwrite its stored token in `expo-secure-store`.
- Token reuse detection: presenting an already-rotated token revokes all sessions in the token family and returns `401 Unauthorized`.
- A missing, blank, or invalid format token returns `400 Bad Request`; an expired, inactive, revoked, or non-existent token returns `401 Unauthorized`.
- No `Set-Cookie` header is emitted.

### Mobile logout

`POST /api/v1/auth/mobile/logout`

Request:

```json
{
  "refreshToken": "opaque-refresh-token"
}
```

Success: `204 No Content`.

Key rules:
- The backend identifies the session matching the SHA-256 hash of `refreshToken` and sets `revokedAt`.
- Subsequent attempts to use the revoked token return `401 Unauthorized`.
- Operation is idempotent: calling with an empty payload `{}` or an already revoked/unknown token returns `204 No Content`.
- The mobile client must remove the stored refresh token from `expo-secure-store`.
- No cookies are set or cleared.

## Security flows

### Account activation flow

1. Validate and normalize the 13-digit CUI.
2. Validate new password compliance (8 to 128 characters, non-blank).
3. Acquire a pessimistic write lock on the user record (`findByCuiForUpdate`).
4. Reject the request with generic `401 Unauthorized` if the account does not exist, status is not `PENDING_ACTIVATION`, or the temporary password does not match `passwordHash`.
5. Encode the new password with BCrypt via `PasswordEncoder.encode()`.
6. Replace `passwordHash`, transition status from `PENDING_ACTIVATION` to `ACTIVE`, and update `updatedAt`.
7. Atomically persist changes in a single transaction.
8. Return activation confirmation without issuing any access or refresh tokens.

### Patient portal access flow

A patient portal account is an ordinary `User`; no parallel authentication model exists. An administrator creates access with `POST /api/v1/patients/{id}/access`. The operation locks the patient row, creates a `PENDING_ACTIVATION` user with only the persisted `PATIENT` role, and returns a cryptographically generated temporary password exactly once. Only its BCrypt hash is persisted.

The patient uses their existing DPI as `User.cui`. Web clients can complete the initial change from the normal login challenge; existing web/mobile clients may continue to use `POST /api/v1/auth/activate` compatibly. The established authority is `ROLE_PATIENT`; it does not grant administrative patient permissions. `GET /api/v1/patients/me` requires that role and resolves the profile only from the JWT user ID, never from a browser-supplied patient ID.

Patient contact email remains separate administrative data in `patients.email`. A portal user may have a null `users.email`; staff users and the initial administrator must still supply a valid email. To preserve the identity link, a patient DPI cannot be changed after portal access has been created.

### Patient portal self-service flow

Patient self-service endpoints enable authenticated patients to inspect their own identity, profile details, and health summary:

- `GET /api/v1/patients/me`: Returns the basic administrative identity (`PatientResponse`).
- `GET /api/v1/patients/me/profile`: Returns personal contact and identity details with a masked DPI (`PatientProfileResponse`).
- `GET /api/v1/patients/me/health`: Returns the patient's persisted health summary (`PatientHealthResponse`), or an `EMPTY` state when no clinical information exists.
- `GET /api/v1/patients/me/account-statement`: Returns the patient's own charges, payments, and derived balance (`AccountStatementResponse`).
- `GET /api/v1/patients/me/treatment-plans`: Returns only approved treatment plans linked to the JWT-owned patient.
- `GET /api/v1/patients/me/treatment-plans/{planId}`: Returns a plan only when ownership matches; foreign and draft UUIDs produce the same generic `404`.

Security and authorization rules:
1. **Identity resolution authority**: The backend is the sole authority for identifying the patient. Identity is resolved exclusively via `JWT` → `AuthenticatedUser.userId()` → `PatientRepository.findByUser_Id(userId)`.
2. **Strict prohibition of client-supplied identifiers**: No self-service endpoint accepts or trusts `patientId`, `userId`, `dpi`, `cui`, or any identity parameter via path or query parameters. This eliminates IDOR and horizontal privilege escalation.
3. **Role requirement**: All self-service endpoints require `ROLE_PATIENT` enforced via `@PreAuthorize("hasRole('PATIENT')")`. Staff roles (`ADMINISTRATOR`, `SECRETARY`, `DENTIST`, `ASSISTANT`, `CASHIER`) receive `403 Forbidden`. Requests without valid authentication receive `401 Unauthorized`.
4. **Data protection and minimal exposure**: The `/me/profile` response exposes only the last four digits of the DPI (`*********XXXX`) and excludes sensitive administrative and user credentials (such as passwords, internal identifiers, billing tax IDs, or audit stamps).
5. **Accurate semantics**: In `/me/health`, `lastUpdated` comes only from the persisted medical history and remains `null` when none exists. It is never derived from `Patient.updatedAt`, and no fictional clinical data is returned.
6. **Treatment-plan isolation**: The patient role receives no administrative `TREATMENT_PLAN_READ` authority. The self-service DTO omits prices, internal observations, clinical notes, patient identifiers, and audit data; progress comes exclusively from persisted procedure executions.

Administrative medical-history operations require explicit clinical authorities. `MEDICAL_HISTORY_READ` permits reading the subresource and `MEDICAL_HISTORY_UPDATE` permits creating or replacing it. Secretary, cashier, patient, or other roles without those authorities cannot use administrative clinical endpoints. Patient self-service remains read-only and resolves identity exclusively from the JWT principal.

Administrative billing operations require explicit billing authorities, assigned following the frontend access rules for the cash module:

| Permission | Roles | Allows |
|---|---|---|
| `BILLING_READ` | `ADMINISTRATOR`, `SECRETARY`, `CASHIER` | Reading any patient's account statement. |
| `BILLING_CHARGE_CREATE` | `ADMINISTRATOR`, `CASHIER` | Registering charges. |
| `BILLING_PAYMENT_CREATE` | `ADMINISTRATOR`, `CASHIER` | Registering payments and advances. |

`DENTIST`, `ASSISTANT`, `PATIENT`, and any caller without the required authority receive `403 Forbidden` on administrative billing endpoints; unauthenticated requests receive `401 Unauthorized`. Patients read only their own statement through `GET /api/v1/patients/me/account-statement`, which is read-only, requires `ROLE_PATIENT`, and returns `403 Forbidden` to staff roles. Clients never send the payment kind, paid amounts, or balances; the backend derives them from persisted data.

Administrative treatment-plan operations use dedicated clinical authorities:

| Permission | Roles | Allows |
|---|---|---|
| `TREATMENT_PLAN_READ` | `ADMINISTRATOR`, `DENTIST`, `ASSISTANT` | Reading plans and the minimal active-dentist catalog. |
| `TREATMENT_PLAN_CREATE` | `DENTIST`, `ASSISTANT` | Creating draft plans. |
| `TREATMENT_PLAN_UPDATE` | `DENTIST`, `ASSISTANT` | Replacing editable data and items while a plan is a draft. |
| `TREATMENT_PLAN_APPROVE` | `DENTIST` | Performing the clinical transition from `DRAFT` to `APPROVED`. |

`SECRETARY`, `CASHIER`, and `PATIENT` receive none of these permissions. Approved plans are immutable through
the update contract. The professional catalog exposes only user id and full name; it does not expose account,
identity, credential, role, authority, or audit data.

Treatment budget and consent operations use dedicated authorities. Read access belongs to administrator,
dentist, and assistant roles. Dentists and assistants may generate a budget or prepare a consent, while only a
dentist may approve/reject a budget or explicitly accept/revoke a consent. Every mutation derives its actor
from the verified JWT. The patient is always derived from the referenced plan, preventing clients from
substituting a foreign patient id. These administrative contracts expose no credential or internal JPA data.

Patient budget self-service is isolated under `/api/v1/patients/me/treatment-budgets` and requires
`ROLE_PATIENT`. Ownership is resolved from the verified JWT; no patient id is accepted from the client.
Only clinic-approved budgets are visible. Foreign, unpublished, and unknown identifiers return the same
generic `404`, and the portal DTO omits patient ids and internal clinic actors. Accept/reject is a separate,
locked, one-time patient decision; it never grants the administrative `TREATMENT_BUDGET_DECIDE` authority.

Administrative appointment endpoints are isolated under `/api/v1/appointments`. `ADMINISTRATOR`, `SECRETARY`, `DENTIST`, and `ASSISTANT` may read the agenda and update appointment status. Creating and rescheduling appointments is limited to `ADMINISTRATOR` and `SECRETARY`. `PATIENT` and `CASHIER` cannot use the administrative contract. Patient-owned appointment operations remain under `/api/v1/patients/me/appointments` and continue to derive ownership exclusively from the JWT principal.

### Login flow

1. Validate and normalize the 13-digit CUI.
2. Resolve the account and verify its status and BCrypt password.
3. Create an independent refresh session and token family.
4. Update `lastLoginAt` after successful authentication.
5. Return the access token and user view; set the opaque refresh-token cookie.

### Protected request flow

1. Read the Bearer token.
2. Verify signature, token type, and temporal claims.
3. Establish the authenticated user and authorities from the approved claims.
4. Enforce endpoint authorization in the backend.

### Refresh flow

1. Read the refresh cookie.
2. Evaluate session validity, account eligibility, absolute lifetime, and inactivity.
3. Rotate the refresh token atomically and detect reuse as described above.
4. Return a new access token and refresh cookie.

### Logout flow

1. Identify the refresh session from the cookie.
2. Revoke that session when present.
3. Clear the browser cookie.
4. Return `204 No Content`.

## Configuration and secrets

- `JWT_PRIVATE_KEY`
- `JWT_PUBLIC_KEY`
- `JWT_ACCESS_EXPIRATION` (default: 30 minutes)
- `JWT_REFRESH_EXPIRATION` (default: 7 days)
- `JWT_REFRESH_INACTIVITY_TIMEOUT` (default: 24 hours)
- `FRONTEND_URL`

Secrets must come from environment variables or an approved secret store. Never commit real keys, credentials, raw refresh tokens, or password material.

## Initial administrator bootstrap

On a new installation, the initial administrator can be created only during application startup. There is no HTTP endpoint for this operation. Set `INITIAL_ADMIN_ENABLED=true` together with `INITIAL_ADMIN_FULL_NAME`, `INITIAL_ADMIN_CUI`, `INITIAL_ADMIN_EMAIL`, and `INITIAL_ADMIN_PASSWORD`.

The bootstrap locks the persisted `ADMINISTRATOR` role, checks whether a user is already associated with that role, and creates one active account only when none exists. Subsequent starts do not create or modify accounts. The persisted role must exist and be active; otherwise startup fails without disclosing secret values.

After successful initialization, set `INITIAL_ADMIN_ENABLED=false` and remove the bootstrap credentials from the environment. The password is read only in memory for BCrypt encoding and is never logged or stored as plaintext.

## Implementation handoff for Issue #5

Issue #5 should translate this conceptual model into persistence and Liquibase migrations without adding authentication behavior:

- `users`: UUID id, normalized unique username, normalized unique email, BCrypt password hash, status, creation/update timestamps, and nullable last-login timestamp.
- `roles`: UUID id, unique code, name, description, and active flag.
- `permissions`: UUID id, unique code, and description.
- `user_roles`: association between users and roles, with database-enforced uniqueness for each pair.
- `role_permissions`: association between roles and permissions, with database-enforced uniqueness for each pair.
- `refresh_sessions`: UUID id, user foreign key, family UUID, unique token hash, creation/absolute-expiration/last-activity timestamps, nullable revocation timestamp, and nullable self-reference to the replacement session.

Foreign keys, unique constraints, required columns, indexes needed for identifier and token lookup, and timestamp types must be defined explicitly in the migrations. No raw refresh-token column is permitted.

Responsibility boundaries:

- Issue #5: persistence model, repositories as needed, and Liquibase schema.
- Issue #6: login, password verification, access JWT issuance, and `/auth/me` authentication support.
- Issue #7: refresh-session creation, rotation, reuse detection, and logout.
- Issue #8: role/permission authorization enforcement.

Still unresolved and intentionally deferred: concrete roles, the complete permission catalog, account activation and lock-transition rules, and the final password policy.

## Authority conventions

Role codes are persisted without a prefix, for example `ADMINISTRATOR`. When an active role is converted to a
JWT authority or `GrantedAuthority`, the backend prefixes it as `ROLE_ADMINISTRATOR`. This allows method
authorization such as `@PreAuthorize("hasRole('ADMINISTRATOR')")`. Inactive roles grant neither their role authority
nor their associated permissions.

Permission codes are used directly as authorities without an additional prefix. For example, a persisted
`PATIENT_READ` permission is checked with `@PreAuthorize("hasAuthority('PATIENT_READ')")`. API user responses keep
the original persisted codes: `roles` contains `ADMINISTRATOR`, not `ROLE_ADMINISTRATOR`, and `permissions` contains
the unmodified permission codes.

Missing or invalid authentication produces `401 Unauthorized`. An authenticated caller who lacks a required role
or permission produces `403 Forbidden`. Future modules must define and enforce their concrete permissions when
their business operations are implemented; this infrastructure does not establish an exhaustive permission catalog.

## Authenticated password change

Authenticated patient password changes derive the account exclusively from the JWT principal. The API never accepts a target user id or CUI. The current password is verified against the stored BCrypt hash, the replacement uses the shared password policy, and all refresh sessions are revoked atomically after success. Existing access JWTs expire normally because the architecture is stateless. Clients must clear local tokens and require a new login after a successful change.

## Patient self-service ownership and visibility

Every route below `/api/v1/patients/me/**` is restricted to `ROLE_PATIENT` and derives the account exclusively from
the authenticated JWT user id. Caller-supplied `patientId` or `userId` values never select the owner. Child-resource
lookups combine the resource id with the resolved patient id and return the same not-found response for unknown,
foreign, or non-visible records. Staff use separate administrative routes and permissions.

| Self-service area | Ownership and visibility rule |
| --- | --- |
| Profile and health | Resolves the patient linked to the JWT user; masks DPI and excludes security internals. |
| Appointments and requests | Reads and mutates only rows owned by the resolved patient. |
| Treatment plans | Exposes only owned plans whose clinic status is `APPROVED`. |
| Treatment budgets | Exposes only owned budgets whose clinic status is `APPROVED`. |
| Prescriptions | Lists and loads only prescriptions owned by the resolved patient. |
| Clinical documents | Requires ownership and `patientVisible=true`; storage keys are never returned. |
| Account statement | Resolves the linked patient ledger and returns a dedicated patient DTO. |

Treatment-budget clinic status and patient decision are independent concepts. `PENDING` and clinic-rejected budgets
are not published to patients. Clinic `APPROVED` is the publication gate; only afterward may the patient's decision
move independently from `PENDING` to `ACCEPTED` or `REJECTED`. A patient decision never publishes a budget or
overrides its clinic status.
