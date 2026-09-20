# Docker

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