# Definition of Done

A backend ticket is considered complete only when the applicable conditions are satisfied.

## Code

Implementation is complete.

Architecture rules are respected.

No unrelated changes are included.

## Validation

Request validation is implemented.

Business rules are validated in the service layer.

## Database

Required Liquibase migrations exist.

Database constraints are defined where appropriate.

Hibernate schema validation succeeds.

## Security

Authentication and authorization requirements are respected.

No secrets are committed.

Passwords are never stored in plain text.

## API

DTOs are used.

Entities are not exposed directly.

HTTP status codes are appropriate.

Error handling follows project conventions.

Swagger/OpenAPI remains functional.

## Testing

Relevant automated tests pass.

Run:

mvn clean verify

## Docker

Docker build succeeds.

Run:

docker compose build

When full stack integration is available:

docker compose up --build

must start successfully.

## Documentation

Documentation is updated when:

- architecture changes
- API contracts change
- environment variables change
- database behavior changes

## Git

The work has its own branch.

The branch is associated with a GitHub Issue.

PR targets:

develop