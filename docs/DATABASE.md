# Database

## Engine

PostgreSQL (version 17+).

## Provider

Supabase.

Supabase is used as managed PostgreSQL infrastructure.

## Architecture

Frontend
→ Spring Boot
→ PostgreSQL / Supabase

The frontend must not directly access the business database. All data access must pass through the Spring Boot backend.

## Connection & Pooler Strategy

### Protocol & JDBC Format

Spring Boot connects via PostgreSQL JDBC Driver. The connection URL must follow the format:

```text
jdbc:postgresql://HOST:PORT/DATABASE?sslmode=require
```

SSL must remain enabled (`sslmode=require`).

### Supabase Connection Modes

Supabase provides multiple connection options:

1. **Direct Connection (`db.<project-ref>.supabase.co:5432`)**:
   - Direct connection to PostgreSQL.
   - Supports full DDL commands, session locks, and advisory locks required by Liquibase.
   - Recommended when IPv6 connectivity is available.

2. **Session Pooler (`aws-0-<region>.pooler.supabase.com:5432`)**:
   - Maintains backend PostgreSQL connections for the full duration of client sessions.
   - Provides native IPv4 compatibility.
   - Fully compatible with Liquibase migration locking (`DATABASECHANGELOGLOCK`) and Spring Boot HikariCP connection pooling.
   - **Recommended mode for standard development and deployment environments.**

3. **Transaction Pooler (`aws-0-<region>.pooler.supabase.com:6543`)**:
   - Allocates connections only for transaction duration and releases them immediately.
   - **Incompatible with Liquibase**: Breaks session-level advisory locks and migration locks. Must NOT be used for backend migration runners.

## ORM

Spring Data JPA + Hibernate.

Hibernate does not manage schema changes.

Use:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
```

Do not use `update`, `create`, or `create-drop`.

## Schema Management

Liquibase is the single source of truth for schema creation and modification.

Location:

```text
src/main/resources/db/changelog/
├── db.changelog-master.yaml
└── changes/
```

Master changelog:

`db.changelog-master.yaml`

Sequential change files:

```text
001-create-users-and-roles.sql
002-create-patients.sql
003-create-appointments.sql
```

### Technical Tables

Liquibase automatically manages its state in:

- `DATABASECHANGELOG`
- `DATABASECHANGELOGLOCK`

These are technical tables and must not be treated as business entities.

## ACID

Database operations must respect:

- Atomicity
- Consistency
- Isolation
- Durability

Use `@Transactional` when multiple related changes must behave as one atomic unit.

Example:

Finalizing a dental procedure may include:

- procedure update
- material consumption
- inventory movement
- billing charge

These changes should be executed in one transaction when appropriate.

## Constraints

Use PostgreSQL database constraints whenever possible:

- `PRIMARY KEY`
- `FOREIGN KEY`
- `NOT NULL`
- `UNIQUE`
- `CHECK`

Do not rely exclusively on application-level validation.

## Patient portal identity

Patient portal accounts reuse the `users` table. `patients.user_id` is nullable for patients without portal access and is a unique foreign key to `users.id`, enforcing a one-to-zero-or-one relationship in both directions.

`users.email` is nullable only at the persistence level so a patient account can be created without inventing an email address. The administrative contact email remains `patients.email`. The staff-user service and initial-administrator bootstrap continue to require a valid, unique email address; the database unique constraint remains in place for non-null email values.

After a patient is linked to a user, their `dpi` must not change through the administrative patient update flow. This preserves the identity invariant `patients.dpi = users.cui`.

## Credentials & Environment Variables

Database credentials must come exclusively from environment variables:

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`

Never commit real Supabase database credentials to the repository. Use local `.env` (ignored by Git) for local testing.

## Files

Large clinical files must not be stored directly as binary database columns unless explicitly required.

Prefer external object storage and store metadata/reference URLs in PostgreSQL.
