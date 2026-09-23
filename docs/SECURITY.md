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

- `PENDING_ACTIVATION`
- `ACTIVE`
- `INACTIVE`
- `LOCKED`

Authentication and session use must respect account status. Detailed activation, locking, unlocking, and status-transition policies are outside this issue and must be defined before those behaviors are implemented.

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
- Password input must be validated before hashing. The final length and complexity policy remains a product decision.
- Password reset and account activation flows are outside this issue and must not be inferred from this model.

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

Presenting a previously rotated token is token reuse. On detected reuse, the backend revokes every active refresh session in that `familyId` and returns a generic `401 Unauthorized` response. Rotation and family revocation require transactional consistency.

## Browser transport and CORS

The refresh token is transported in a host-only cookie with:

- `HttpOnly` enabled.
- `Secure` enabled outside local HTTP development.
- `SameSite=Lax`.
- `Path=/api/v1/auth`.
- No `Domain` attribute unless a reviewed deployment requirement makes it necessary.

The frontend must not read the refresh token or store it in `localStorage`, `sessionStorage`, or persistent JavaScript variables. Cross-origin browser clients require an explicit allowed origin from `FRONTEND_URL` and credentialed CORS requests; wildcard origins are incompatible with credentials.

## Session policy

Each successful login creates an independent refresh-token family, allowing multiple devices or browsers. Phase one imposes no maximum number of concurrent sessions.

A refresh session ends through logout, absolute expiration, inactivity expiration, token-family reuse response, or when the account is no longer eligible under the security rules. The model supports a future “logout all devices” operation by revoking all active sessions for a user, but that operation is not part of this issue.

## Authentication API contracts

All error responses follow the centralized `ApiErrorResponse` contract in `API-CONVENTIONS.md`. Authentication failures use generic messages and never reveal whether an account, session, or token exists.

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

## Security flows

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
