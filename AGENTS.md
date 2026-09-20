# DentalCare API - Agent Instructions

## Project

DentalCare API is the backend of the DentalCare system.

Technology stack:

- Java 21
- Spring Boot
- Maven
- Spring Web
- Spring Data JPA
- Spring Security
- JWT Access Token
- Refresh Token
- BCrypt
- PostgreSQL
- Supabase as managed PostgreSQL
- Liquibase
- OpenAPI / Swagger
- Docker
- Docker Compose

## Architecture

The backend must be implemented as a modular monolith.

Business logic must be organized by domain under:

src/main/java/com/dentalcare/api/modules

Each module must use layered architecture.

Recommended layers:

- controller
- dto
- mapper
- model
- repository
- service

Shared technical concerns must stay outside business modules:

- config
- security
- exception
- shared

Do not create a microservices architecture.

## Base package

Use:

com.dentalcare.api

## Module structure

Example:

modules/patients/
├── controller/
├── dto/
│   ├── request/
│   └── response/
├── mapper/
├── model/
├── repository/
└── service/

Services may contain interfaces and implementations when appropriate.

Example:

PatientService.java
PatientServiceImpl.java

## Layer responsibilities

### Controller

Controllers handle HTTP concerns only.

Controllers may:

- receive requests
- validate request DTOs
- call services
- return HTTP responses

Controllers must not:

- access repositories directly
- contain business logic
- expose JPA entities directly

### Service

Services contain business rules and application logic.

Services:

- coordinate operations
- access repositories
- define transactional boundaries
- validate business rules

Use `@Transactional` where atomic operations are required.

### Repository

Repositories handle persistence only.

Use Spring Data JPA.

### Model

Models contain JPA entities and domain persistence representation.

Do not use entities as API responses.

### DTO

DTOs define API contracts.

Separate request and response DTOs when useful.

Example:

CreatePatientRequest
UpdatePatientRequest
PatientResponse

Use Jakarta Bean Validation.

### Mapper

Mappers transform:

Entity → Response DTO

and when appropriate:

Request DTO → Entity/domain data

Mapping logic must not be placed in controllers.

## SOLID

Apply SOLID pragmatically.

Important principles:

- one clear responsibility per class
- depend on abstractions where useful
- avoid unnecessary coupling
- avoid giant services
- avoid giant controllers

Do not introduce abstractions that provide no real benefit.

## Database

Database engine:

PostgreSQL

Infrastructure provider:

Supabase

Supabase is used primarily as managed PostgreSQL.

The frontend must never connect directly to PostgreSQL for business operations.

All business data access must go through Spring Boot.

## Database migrations

Liquibase is the only mechanism responsible for schema creation and modification.

Migration files belong in:

src/main/resources/db/changelog

Use:

db.changelog-master.yaml

and:

db/changelog/changes/

Use sequential migration names such as:

001-create-users-and-roles.sql
002-create-patients.sql
003-create-appointments.sql

Do not manually modify production database structure without a migration.

## Hibernate

Use:

spring.jpa.hibernate.ddl-auto=validate

Do not use:

create
create-drop
update

Hibernate validates the schema.

Liquibase owns the schema.

## ACID

Database operations must respect ACID principles.

Use database constraints:

- PRIMARY KEY
- FOREIGN KEY
- UNIQUE
- NOT NULL
- CHECK

Use `@Transactional` when multiple changes must succeed or fail together.

## Security

Use Spring Security.

Authentication strategy:

- JWT Access Token
- Refresh Token
- BCrypt password hashing

Passwords must never be stored in plain text.

Protected endpoints must validate authentication in the backend.

Frontend middleware is not a security boundary.

JWTs must contain only minimum required information.

Never put sensitive clinical information inside a JWT.

## API

Base API path:

/api/v1

Follow REST conventions.

Examples:

GET /api/v1/patients
GET /api/v1/patients/{id}
POST /api/v1/patients
PUT /api/v1/patients/{id}
DELETE /api/v1/patients/{id}

Do not expose entities directly.

Use consistent HTTP status codes.

## API errors

Use centralized exception handling.

Use:

GlobalExceptionHandler

Typical exceptions:

- BadRequestException
- ResourceNotFoundException
- UnauthorizedException
- ForbiddenException
- ConflictException

Never expose Java stack traces to API clients.

## Swagger / OpenAPI

OpenAPI documentation must remain functional.

Swagger UI must be available in development.

Document public API endpoints.

## Configuration

Use Spring profiles.

Expected configuration files:

- application.yml
- application-dev.yml
- application-prod.yml

Sensitive values must come from environment variables.

Examples:

DB_URL
DB_USERNAME
DB_PASSWORD

JWT_PRIVATE_KEY
JWT_PUBLIC_KEY

JWT_ACCESS_EXPIRATION
JWT_REFRESH_EXPIRATION

FRONTEND_URL

Never commit real credentials.

## Docker

The project must be dockerized.

Backend must have a Dockerfile.

The complete system must be compatible with Docker Compose.

Expected command:

docker compose up --build

A feature is not considered complete if it works only when executed manually outside Docker.

Use multi-stage Docker builds.

Use Java 21 runtime.

Do not hardcode credentials inside Docker images.

## Testing

Tests belong under:

src/test

Add tests for important business logic.

Authentication and critical financial/inventory operations should receive particular attention.

## Git

Main integration branch:

develop

Production/stable branch:

main

Feature branches:

feature/issue-XX-description

Bug fixes:

fix/issue-XX-description

Do not commit directly to main.

Avoid unrelated changes in the same ticket.

## Verification

Before declaring backend work complete run:

mvn clean verify

and:

docker compose build

When the full stack is available also validate:

docker compose up --build

## Documentation

Before changing architecture read:

- docs/ARCHITECTURE.md
- docs/MODULES.md
- docs/DATABASE.md
- docs/SECURITY.md
- docs/API-CONVENTIONS.md
- docs/DOCKER.md

Update documentation when an architectural decision changes.

## Prohibited shortcuts

Do not:

- create microservices
- return entities directly from controllers
- access repositories directly from controllers
- place business logic in controllers
- use ddl-auto=update
- hardcode database credentials
- hardcode JWT secrets
- commit .env files containing secrets
- allow frontend direct access to business database tables
- bypass Liquibase for schema changes
- reorganize architecture without explicit approval