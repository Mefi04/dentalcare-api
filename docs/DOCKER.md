# Docker

The optional `monitoring` profile adds local Prometheus/Grafana. Management port 9091 is never published;
Grafana requires a password and binds to loopback. See [MONITORING.md](MONITORING.md) for configuration,
disposable verification, alert simulation and retention.

## Requirement

DentalCare must be executable using Docker.

The integrated project must support:

docker compose up --build

## Backend image

Backend uses a multi-stage Docker build.

Build stage:

- Maven
- Java 21
- compile/package application

Runtime stage:

- lightweight Java 21 JRE
- copy generated JAR
- execute JAR

## Backend port

Default internal port:

8080

## Frontend

Frontend runs in its own container.

Expected port:

3000

## Docker Compose

Expected integrated flow:

frontend
→ backend
→ Supabase PostgreSQL

## Environment variables

Secrets must be injected through environment variables.

Do not place production secrets inside:

Dockerfile
docker-compose.yml
application.yml

## .env

Local `.env` must not be committed.

Provide:

.env.example

with variable names and safe placeholders.

## Supabase

Because PostgreSQL is managed externally through Supabase, production Docker Compose does not require a PostgreSQL container.

A local PostgreSQL service may optionally be added for isolated development/testing.

## Health checks

Backend should expose a health endpoint.

Spring Boot Actuator may be used.

Expected example:

GET /actuator/health

## Variables passed by Compose

Compose forwards only what the backend uses in this deployment:

- Required (Compose fails fast if missing): `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`,
  `JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY`.
- Optional: `SPRING_PROFILES_ACTIVE`, `FRONTEND_URL`, `DB_PREPARE_THRESHOLD`,
  `JWT_ACCESS_EXPIRATION`, `JWT_REFRESH_EXPIRATION`, `INITIAL_ADMIN_*`, `R2_*`,
  `GEMINI_API_KEY`, `GEMINI_MODEL`, `RATE_LIMIT_ENABLED`, `RATE_LIMIT_TRUSTED_PROXIES`.

Per-endpoint rate limits, upload limits, SMTP (`MAIL_*`), Twilio and the appointment
notification outbox are not wired in Compose; `application.yml` keeps safe defaults.
Email-based password recovery needs `MAIL_*` added explicitly if it is enabled later.
Frontend-only Supabase keys must never be forwarded to the backend container.

## Optional Gemini assistant

Set `GEMINI_API_KEY` in the local environment or uncommitted `.env` before starting Compose.
`GEMINI_MODEL` defaults to `gemini-3.8-flash`. Compose passes both values to the backend
container. Without a key, the API starts normally and the assistant endpoint returns 503.
