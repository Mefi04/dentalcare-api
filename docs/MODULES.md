# DentalCare Modules

Public first-appointment intake, aggregated general-dentistry availability,
reception call history, telephone confirmation, and professional schedules live
in the `appointments` module. The patient portal retains its separate
authenticated appointment workflow. Public intake never creates a patient
record; the existing patient module handles registration after in-person
identity verification.

## auth

Responsible for:

- login
- refresh token
- logout
- session authentication
- patient password recovery with expiring one-time codes

## users

Responsible for:

- users
- roles
- permissions
- account status
- public professional profiles linked one-to-one to real dentist users

## patients

Responsible for administrative patient information.

Examples:

- patient registration
- patient editing
- patient search
- contact information

## appointments

Responsible for:

- appointments
- scheduling
- appointment status
- waiting room workflows
- appointment requests

## medical-history

Responsible for:

- medical background
- allergies
- medical conditions
- patient history

## clinical-records

Responsible for:

- clinical records
- odontograms
- findings
- diagnoses
- clinical evolution
- clinical attentions and history
- clinical document metadata and private R2 files
- explicit, audited sharing of selected clinical documents with the owning patient

## treatments

Responsible for:

- treatment plans
- treatment procedures
- estimates
- consents
- treatment completion
- persistent execution history for approved plan procedures
- immutable, versioned budgets calculated from approved plan items
- versioned consent documents with explicit acceptance and revocation audit
- patient-owned, read-only treatment-plan views with progress derived from persisted procedure executions

## prescriptions

Responsible for persistent medical and dental prescriptions, professional issuance, and patient-owned
self-service consultation.

## billing

Responsible for:

- payments
- patient balances
- charges
- receipts
- cash operations

## inventory

Responsible for:

- consumables
- instruments
- suppliers
- purchases
- stock
- inventory movements
- alerts

## sterilization

Responsible for sterilization workflows and protocols.

## reports

Responsible for read-only administrative reports. Its dashboard aggregates persisted data from
`patients`, `appointments`, and `billing` without changing those modules' business rules or persistence.

## settings

Responsible for:

- the singleton clinic configuration
- the administrative operational procedure catalog
- configurable application data

The procedure catalog is independent from inventory articles, treatment-plan snapshots, budget snapshots,
and executed treatment procedures. Staff users and roles remain owned by the `users` module.

## publicinfo

Provides the read-only public facade for the clinic web site. It consumes the singleton clinic configuration
and active procedure catalog from `settings`; it owns no persistence, clinic data, or procedure data.
Its response DTOs deliberately expose only visitor-safe fields.

## contactinquiries

Receives general public contact inquiries independently from patients, appointments, clinical records, and users.
Only administrators can read or change an inquiry status after receipt.

## Module rules

Business logic must remain within the appropriate module.

Do not move unrelated functionality into another module only for convenience.

Cross-module dependencies must be explicit and limited.
# Public assistant module

`modules/assistant` owns the public assistant HTTP contract, safe application rules, and
Gemini gateway. It reads published clinic/service information and general-dentistry
availability through their existing services. It does not persist conversations, read
patient records, or mutate appointment requests.
