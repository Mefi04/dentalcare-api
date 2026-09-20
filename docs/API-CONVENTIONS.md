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

200 OK

201 Created

204 No Content

400 Bad Request

401 Unauthorized

403 Forbidden

404 Not Found

409 Conflict

422 Unprocessable Entity when appropriate

500 Internal Server Error

## Error responses

Errors must use one consistent structure.

Suggested shape:

{
  "timestamp": "...",
  "status": 400,
  "error": "Bad Request",
  "message": "...",
  "path": "/api/v1/..."
}

Validation errors may include field-level details.

## Pagination

Large collections should support pagination where necessary.

Suggested parameters:

?page=0
&size=20
&sort=name,asc

## OpenAPI

Public API endpoints should be documented using OpenAPI/Swagger.