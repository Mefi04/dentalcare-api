# Database

## Engine

PostgreSQL.

## Provider

Supabase.

Supabase is used as managed PostgreSQL infrastructure.

## Architecture

Frontend
→ Spring Boot
→ PostgreSQL / Supabase

The frontend must not directly access the business database.

## ORM

Spring Data JPA + Hibernate.

Hibernate does not manage schema changes.

Use:

spring.jpa.hibernate.ddl-auto=validate

## Schema management

Liquibase owns database schema evolution.

Location:

src/main/resources/db/changelog

Structure:

db/changelog/
├── db.changelog-master.yaml
└── changes/

Example migrations:

001-create-users-and-roles.sql
002-create-patients.sql
003-create-appointments.sql

## ACID

Database operations must respect:

- Atomicity
- Consistency
- Isolation
- Durability

Use transactions where multiple related changes must behave as one unit.

Example:

Finalizing a dental procedure may include:

- procedure update
- material consumption
- inventory movement
- billing charge

These changes should be executed in one transaction when appropriate.

## Constraints

Use PostgreSQL constraints whenever possible.

Examples:

PRIMARY KEY

FOREIGN KEY

NOT NULL

UNIQUE

CHECK

Do not rely exclusively on frontend validation.

## Credentials

Database credentials must come from environment variables.

Expected variables:

DB_URL
DB_USERNAME
DB_PASSWORD

Never commit real Supabase database credentials.

## Files

Large clinical files must not be stored directly as binary database columns unless explicitly required.

Prefer external/object storage and store metadata/reference data in PostgreSQL.