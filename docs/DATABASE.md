# Database

## Engine

PostgreSQL (version 17+).

## Security rate-limit counters

Changeset `037-create-security-rate-limits` creates `security_rate_limits`, a technical shared-counter table for
multi-instance abuse protection. Its hashed bucket key is the primary key; PostgreSQL atomic upserts reset expired
windows or increment active counters without lost updates. An expiration index supports opportunistic cleanup.
No raw IP address, CUI, password, token, or request payload is persisted.

## Clinic settings and operational procedure catalog

Changeset `026-create-clinic-settings-procedure-catalog` creates `clinic_settings` as a database-enforced
singleton (`SMALLINT` primary key constrained to `id = 1`). Liquibase inserts only the structural row and
timestamps; institutional fields remain null until an administrator completes them through the API, so mock
frontend values never become production data. Updates record the authenticated user in `updated_by`.

`procedure_catalog_items` stores reusable administrative procedure definitions independently from inventory,
treatment-plan items, immutable budget snapshots, and executed treatment procedures. Money uses
`NUMERIC(12,2)`, duration uses integer minutes, and status is `ACTIVE` or `INACTIVE`. Functional unique indexes
on normalized code and name protect case-insensitive uniqueness under concurrent writes. Foreign keys preserve
the creating and updating users. Changeset 026 grants `SETTINGS_READ` and `SETTINGS_WRITE` only to the existing
`ADMINISTRATOR` role.

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

## Medical history core

Each patient may have at most one row in `medical_histories`, enforced by a unique foreign key to `patients.id`.
Allergies, current medications, and relevant conditions are persisted in separate child tables rather than as delimited strings. This keeps individual values queryable and prevents coupling clinical collections to a presentation format. General observations and medical-history timestamps belong to `medical_histories`; administrative patient timestamps remain independent.

Deleting a patient cascades to its medical-history core and collection rows. Clinical access is controlled by the backend permissions `MEDICAL_HISTORY_READ` and `MEDICAL_HISTORY_UPDATE`.

## Billing core

Billing persists two append-only tables linked to `patients.id`:

- `billing_charges`: `concept`, `amount`, and `created_at`.
- `billing_payments`: optional `charge_id`, `kind` (`PAYMENT`, `PARTIAL_PAYMENT`, `ADVANCE`), `method` (`CASH`, `CARD`, `TRANSFER`, `CHECK`), `amount`, and `created_at`.

Monetary amounts use `NUMERIC(12,2)` in PostgreSQL and `BigDecimal` in Java; floating-point types are never used for money. `CHECK` constraints require positive amounts, a non-blank concept, and `kind = 'ADVANCE'` exactly when `charge_id` is null. The composite foreign key `(charge_id, patient_id)` → `billing_charges (id, patient_id)` guarantees at the database level that a payment can only be applied to a charge of the same patient.

Paid amounts, charge status, and account balance are derived from these rows and are never stored. Registering a payment against a charge locks that charge row (`PESSIMISTIC_WRITE`) before summing its payments, so concurrent payments cannot exceed the charge amount. Account statements are read in a read-only `REPEATABLE READ` transaction so their totals always match the listed rows.

Billing foreign keys to `patients` do not cascade: financial history is never removed implicitly together with a patient. Changeset `009-create-billing-core` also seeds the permissions `BILLING_READ`, `BILLING_CHARGE_CREATE`, and `BILLING_PAYMENT_CREATE`.

## Treatment plan core

Treatment plans are stored in `treatment_plans` and their ordered proposed procedures in
`treatment_plan_items`. Each plan belongs to one patient and references one professional user. The service
requires that professional to be `ACTIVE` with an active `DENTIST` role. Patient and professional foreign
keys do not cascade on deletion; item rows cascade only when their owning plan is deleted.

Plans use only `DRAFT` and `APPROVED`. Approval records `approved_at`, and database constraints keep that
timestamp consistent with status. Items require a non-blank name, positive quantity and unit price, and a
non-negative position unique within the plan. Subtotals and plan totals are derived with `BigDecimal` from
`quantity * unit_price`; neither value is stored. Changeset `011-create-treatment-plans-core` seeds
`TREATMENT_PLAN_READ`, `TREATMENT_PLAN_CREATE`, `TREATMENT_PLAN_UPDATE`, and `TREATMENT_PLAN_APPROVE`.

## Treatment budgets and consents

Changeset `025-create-treatment-budgets-consents` adds immutable financial snapshots in `treatment_budgets`
and `treatment_budget_items`. A budget derives its patient, items, prices, subtotal, and total from one approved
plan; clients cannot submit those values. `(treatment_plan_id, version)` is unique and a partial unique index
permits only one `PENDING` or `APPROVED` budget per plan. Rejected budgets remain as audit history and allow a
new version. Budget approval records the authenticated deciding user and does not create Billing charges.

Changeset `032-add-treatment-budget-patient-decision` keeps the clinic decision separate from the patient's
one-time response. `patient_decision` follows `PENDING -> ACCEPTED|REJECTED`; its authenticated user and
timestamp are mandatory only after a decision. Database checks enforce the lifecycle and chronology, while
the patient/decision/date index supports portal reads without changing the administrative budget status.

`treatment_consents` stores the exact consent text and document version linked to the real plan and, when one
exists, its latest approved budget. Consent text is immutable. Status and actor/timestamp constraints enforce
the explicit lifecycle `PENDING -> ACCEPTED -> REVOKED` (or direct revocation while pending), and a partial
unique index permits only one pending or accepted consent per plan. Foreign keys preserve plan, patient,
budget, and user traceability; plan/patient history indexes support efficient reads.

## Inventory catalog core

The inventory catalog persists articles in `inventory_items`:

- `id`: UUID primary key.
- `code`: unique SKU/item code (`uq_inventory_items_code`).
- `name`: item display name.
- `description`: optional details.
- `item_type`: `CONSUMABLE` or `INSTRUMENT`.
- `category`: item category.
- `status`: `ACTIVE` or `INACTIVE`.
- Consumable-specific columns: `unit`, `current_stock`, `minimum_stock`, `expiration_date`.
- Instrument-specific columns: `location`, `total_quantity`, `available_quantity`.

Database `CHECK` constraints enforce:
- Non-blank `code`, `name`, `category`.
- `item_type IN ('CONSUMABLE', 'INSTRUMENT')`.
- `status IN ('ACTIVE', 'INACTIVE')`.
- Consumable consistency: `unit`, `current_stock >= 0`, `minimum_stock >= 0` are required for consumables.
- Instrument consistency: `location`, `total_quantity >= 0`, `available_quantity >= 0`, and `available_quantity <= total_quantity` are required for instruments.

Changeset `010-create-inventory-core` creates indexes on `item_type`, `status`, `category`, and `name`, and seeds the permissions `INVENTORY_READ` and `INVENTORY_WRITE`.

## Inventory movements and Kardex

Changeset `012-create-inventory-movements` adds immutable rows in `inventory_movements` with:

- UUID primary key and foreign keys to `inventory_items` and the responsible `users` row, both using `ON DELETE RESTRICT`.
- `movement_type` constrained to `ENTRY`, `EXIT`, or `ADJUSTMENT`.
- Positive `quantity`, non-negative `stock_before`/`stock_after`, and optional instrument-specific `available_before`/`available_after` pairs.
- Optional `observation` and `reference`, plus an immutable `created_at` timestamp.

At API level, `observation` is mandatory for `ADJUSTMENT` movements so physical-count corrections are justified.

The availability check requires both availability values together, keeps them non-negative, and prevents either from exceeding its corresponding total. The main Kardex index is `(inventory_item_id, created_at DESC, id DESC)`; additional indexes support filters by movement type, responsible user, and date.

Movement insertion and catalog-stock update execute in one transaction. The service locks the `inventory_items` row with `PESSIMISTIC_WRITE`, validates against the latest persisted quantities, updates the catalog, and inserts the historical row. Any failure rolls back both changes. This prevents lost updates and negative stock when requests for the same item run concurrently. Catalog creation remains the only controlled initialization path; subsequent stock changes go through the movement service.

## Inventory suppliers and purchases

Changeset `017-create-inventory-suppliers-purchases` adds persistent suppliers, purchase records, and purchase item lines:

- `inventory_suppliers`: UUID primary key, non-blank `name`, optional `contact_name`, `phone`, `email`, `address`, `notes`, `status` (`ACTIVE`/`INACTIVE`), and timestamps. Check constraints validate non-blank name and valid status. No physical deletion is performed.
- `inventory_purchases`: UUID primary key, unique `code` generated from monotonic sequence `inventory_purchase_code_seq`, foreign key to `inventory_suppliers` (`ON DELETE RESTRICT`), `status` constrained to `PENDING` or `RECEIVED`, `purchase_date`, optional `reference` and `observation`, `created_by` (FK users `ON DELETE RESTRICT`), `created_at`, optional `received_by` (FK users `ON DELETE RESTRICT`), and optional `received_at`. Check constraint enforces receipt consistency: `PENDING` requires null received audit, while `RECEIVED` requires non-null `received_at` and `received_by`.
- `inventory_purchase_items`: UUID primary key, foreign key to `inventory_purchases` (`ON DELETE CASCADE`), foreign key to `inventory_items` (`ON DELETE RESTRICT`), positive `quantity`, non-negative `unit_cost NUMERIC(12, 2)`, and unique constraint `uq_inventory_purchase_items_item (purchase_id, inventory_item_id)`.

Purchase reception executes under `PESSIMISTIC_WRITE` lock over the purchase row, delegates strictly to `InventoryMovementService.register()` for each item to log auditable `ENTRY` movements in Kardex and update catalog stock, and transitions status to `RECEIVED`.

## Clinical records core

The clinical records core persists clinical consultation encounters, diagnoses, progress notes, and tooth-level odontogram findings:

- `clinical_attentions`: `patient_id` (FK patients, `ON DELETE RESTRICT`), `professional_id` (FK users, `ON DELETE RESTRICT`), optional `appointment_id` (FK appointments, `ON DELETE SET NULL`), non-blank `reason`, non-blank `clinical_notes`, optional `next_steps`, `occurred_at`, `created_at`, `updated_at`.
- `clinical_diagnoses`: `patient_id` (FK patients, `ON DELETE RESTRICT`), `attention_id` (FK clinical_attentions, `ON DELETE RESTRICT`), optional `treatment_plan_id` (FK treatment_plans, `ON DELETE SET NULL`), `author_id` (FK users, `ON DELETE RESTRICT`), `type` constrained to `PRIMARY` or `SECONDARY`, non-blank `description`, `created_at`.
- `clinical_evolution_notes`: `patient_id` (FK patients, `ON DELETE RESTRICT`), `attention_id` (FK clinical_attentions, `ON DELETE RESTRICT`), `author_id` (FK users, `ON DELETE RESTRICT`), `consultation_date`, non-blank `procedure_summary`, non-blank `note`, `created_at`.
- `odontogram_findings`: `patient_id` (FK patients, `ON DELETE RESTRICT`), optional `attention_id` (FK clinical_attentions, `ON DELETE SET NULL`), `author_id` (FK users, `ON DELETE RESTRICT`), `dentition` constrained to `ADULT`, `MIXED`, `CHILD`, non-blank `tooth_code`, optional `surface` constrained to `VESTIBULAR`, `PALATAL`, `MESIAL`, `DISTAL`, `OCCLUSAL`, `finding` constrained to `HEALTHY`, `CARIOUS`, `TREATED`, `MISSING`, `TO_TREAT`, optional `observation`, `created_at`.

Database indexes optimize queries by `(patient_id, occurred_at DESC, id DESC)`, `(patient_id, created_at DESC, id DESC)`, `(patient_id, consultation_date DESC, created_at DESC)`, and `(patient_id, tooth_code, created_at DESC)`. Changeset `013-create-clinical-records-core` also seeds permissions `CLINICAL_RECORD_READ` and `CLINICAL_RECORD_WRITE`. Clinical history is protected against destructive physical deletion.

## Prescriptions core

Changeset `015-create-prescriptions-core` stores immutable issuance records in `prescriptions`. Each row links
one patient and the authenticated professional, and persists medication, presentation, dosage, frequency,
duration, optional instructions, issuance timestamp, and status. Foreign keys use the default restrictive
deletion behavior so clinical prescription history is not removed implicitly.

Non-blank checks protect all required instruction fields and the initial status is constrained to `ISSUED`.
The composite index `(patient_id, issued_at DESC, id DESC)` supports stable patient history pagination; a
second index supports professional lookups. The changeset seeds `PRESCRIPTION_READ` and
`PRESCRIPTION_CREATE`. Only active dentists receive creation permission, while authorized clinical and
administrative staff may read prescriptions according to role permissions.

## Treatment procedure execution

Changeset `016-create-treatment-procedures` stores one execution row for each unit performed from an approved
`treatment_plan_items` entry. Each row references the existing plan, item, patient, and authenticated dentist;
the procedure name and tooth are snapshotted to preserve the clinical history. Execution begins in
`IN_PROGRESS` and may transition once to `COMPLETED`, which records `completed_at` and optional completion
notes. The database keeps timestamps consistent with status and permits only one active execution per plan item.

`sequence_number` is unique per plan item and allows the service to execute, in order, up to the quantity
approved in the plan. Plan-level pessimistic locking serializes registration so concurrent requests cannot
exceed that quantity. History indexes support stable ordering by `performed_at DESC, id DESC` for both patient
and plan queries. The migration seeds read permission for administrator, dentist, and assistant roles; only
dentists may start or complete procedures.

## Patient password recovery

Changeset `018-create-password-recovery-tokens` stores only BCrypt hashes of eight-digit recovery codes.
Each row belongs to a user and records request, expiration, use, revocation, and failed-attempt audit data.
Codes expire after 15 minutes by default, are single-use, and are revoked when superseded or after the
configured attempt limit. Raw codes and passwords are never persisted or logged. A successful reset revokes
all refresh sessions for the user; already-issued access JWTs remain valid only until their normal short expiry.

## Credentials & Environment Variables

Database credentials must come exclusively from environment variables:

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`

Never commit real Supabase database credentials to the repository. Use local `.env` (ignored by Git) for local testing.

## Clinical documents core

Changeset `019-create-clinical-documents` persists patient clinical document metadata in `clinical_documents`.
Each record references the existing patient and authenticated author user with `ON DELETE RESTRICT` constraints
to prevent accidental data loss. Fields include `title` (VARCHAR(150) NOT NULL), `type` (VARCHAR(30) NOT NULL with
CHECK constraint for `RADIOGRAPHY`, `LAB_RESULT`, `INFORMED_CONSENT`, `CLINICAL_REPORT`, `PHOTOGRAPHY`, `OTHER`),
optional `description` (VARCHAR(1000)), `document_date` (DATE NOT NULL), and audit timestamps.
Indexes support chronological pagination by patient (`idx_clinical_documents_patient_date`) and filtering by type (`idx_clinical_documents_type`).
Queries strictly enforce `patient_id` alongside `id` to prevent IDOR vulnerabilities.

Changeset `024-add-patient-visibility-to-clinical-documents` adds opt-in patient sharing to the same table.
`patient_visible` is `FALSE` by default. A database constraint requires `shared_at` and `shared_by` together only
while a document is visible; `shared_by` references `users` with `ON DELETE RESTRICT`. A partial index supports
the patient-visible chronological listing. No object-storage key is exposed through patient DTOs.

## Files

Large clinical files must not be stored directly as binary database columns unless explicitly required.

Prefer external object storage and store metadata/reference URLs in PostgreSQL.
## Sterilization core

`sterilization_protocols` stores reusable active/inactive protocols and their supported method (`STEAM`, `DRY_HEAT`, or `CHEMICAL`). `sterilization_cycles` records each load with its protocol, authenticated responsible user, status, observations, and lifecycle timestamps. `sterilization_cycle_instruments` links cycles to existing `inventory_items`; it does not duplicate instruments or alter stock/Kardex.

The first supported lifecycle is `IN_PROGRESS -> RELEASED`. Released cycles are immutable with respect to status, and the database requires a release timestamp only for released cycles.

## Appointment requests and waiting room

Changeset `020-create-appointment-requests-waiting-room` adds two operational resources without duplicating
confirmed appointments. `appointment_requests` stores the patient, requested dentist/time, optional clinic
proposal, explicit lifecycle (`PENDING`, `PROPOSED`, `CONFIRMED`, `REJECTED`, `CANCELLED`), processing staff,
audit timestamps, and a unique optional `appointment_id`. A confirmed request must reference exactly one
persisted appointment. Confirmation locks the request and creates the appointment in the same transaction.

Changeset `040-public-appointment-requests` extends this workflow for anonymous first-appointment intake without
creating users or patient records. Public contact snapshots, idempotency key/hash, and nullable patient/preferred
dentist links support unassociated pending requests. Partial unique indexes enforce idempotency keys and equivalent
active requests for an existing CUI and preferred slot. A preferred public time is not reserved; only the existing
appointment creation/availability path creates a confirmed slot.

Changeset `041-add-assigned-professional-to-appointment-requests` separates reception's assigned dentist from
the original requested dentist. Assignment is persisted independently, leaves request status unchanged, and
does not create or reserve an appointment.

`appointment_waiting_room_entries` has a unique one-to-one foreign key to `appointments` and stores
`ARRIVED -> WAITING -> READY -> CLOSED`, transition timestamps, check-in staff and latest responsible staff.
Check-in is limited to a `SCHEDULED` appointment on the current `America/Guatemala` clinic day. Cancelling or
completing the appointment closes any existing operational entry.
