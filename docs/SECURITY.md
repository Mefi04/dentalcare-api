# Security

## Framework

Spring Security.

## Authentication

Authentication uses:

- username/email + password
- BCrypt password hashing
- JWT Access Token
- Refresh Token

## Login

Endpoint:

POST /api/v1/auth/login

General flow:

Client
→ credentials
→ backend
→ user lookup
→ BCrypt password verification
→ issue Access Token
→ issue Refresh Token

## Access Token

Access tokens should be short-lived.

Recommended initial configuration:

30 minutes.

The duration must be configurable.

JWT payload must contain only necessary claims.

Example:

- subject/user id
- role or authority information
- issued at
- expiration

Never include:

- password
- clinical history
- personal medical information

## Refresh Token

Refresh tokens are used to issue new access tokens.

Endpoint:

POST /api/v1/auth/refresh

Refresh tokens must be managed securely.

Token rotation is recommended.

## Logout

Endpoint:

POST /api/v1/auth/logout

Logout must revoke/invalidate the active refresh session where applicable.

## Password storage

Passwords must use BCrypt.

Never store plain text passwords.

## Backend authorization

Next.js route protection is useful for UX but does not replace backend authorization.

Every protected API request must be validated by Spring Security.

## Public endpoints

Expected examples:

POST /api/v1/auth/login
POST /api/v1/auth/refresh

Swagger endpoints may be public in development.

## CORS

CORS must allow only explicitly configured frontend origins in production.

Do not use unrestricted wildcard origins in production.

Frontend URL must be configurable through:

FRONTEND_URL

## Secrets

Never commit:

- JWT private keys
- JWT secrets
- Supabase passwords
- production credentials

Use environment variables.