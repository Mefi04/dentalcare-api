# DentalCare Modules

## auth

Responsible for:

- login
- refresh token
- logout
- session authentication

## users

Responsible for:

- users
- roles
- permissions
- account status

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

## treatments

Responsible for:

- treatment plans
- treatment procedures
- estimates
- consents
- prescriptions
- treatment completion

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

Responsible for administrative and clinical reports.

## settings

Responsible for:

- clinic configuration
- catalogs
- configurable application data

## Module rules

Business logic must remain within the appropriate module.

Do not move unrelated functionality into another module only for convenience.

Cross-module dependencies must be explicit and limited.