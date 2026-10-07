# DentalCare OpenAPI contract

The committed contract at `docs/openapi/dentalcare-api.json` is the integration source for Web and Mobile. It is
generated from the running Spring MVC application, canonicalized, and compared byte-for-byte during Maven tests.
It contains DTO schemas only; JPA entities are not a supported client contract.

## Consumer workflow

1. When a backend PR changes a controller or DTO, inspect the OpenAPI snapshot diff before approving it.
2. Web and Mobile should review changed paths, request/response schemas, status codes, and security requirements.
3. Coordinate incompatible changes with both clients before merging. This ticket does not generate client code.
4. Public operations carry `Audience: Public`; staff operations carry `Audience: Staff`; patient-owned operations
   carry `Audience: Patient self-service` and never accept `patientId` or `userId` as an ownership selector.

Bearer-protected operations use the `bearerAuth` security scheme. Web refresh and logout use the HttpOnly
`refreshToken` cookie documented as `refreshCookie`. Mobile refresh tokens remain request DTO fields according to
their existing contract. Examples must always use synthetic values and must never contain real credentials or data.

## Updating the snapshot

Only update the snapshot together with the intentional controller/DTO change:

```bash
mvn -Dtest=OpenApiContractIntegrationTests -Dopenapi.snapshot.update=true test
mvn -Dtest=OpenApiContractIntegrationTests test
git diff -- docs/openapi/dentalcare-api.json
```

The first command regenerates the committed snapshot; the second proves it is reproducible. `mvn clean verify`
also generates `target/openapi/dentalcare-api.json` and fails if it differs. CI performs an additional byte comparison
so a broken generator or stale snapshot cannot be merged silently.
