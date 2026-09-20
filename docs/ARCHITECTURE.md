# DentalCare Backend Architecture

## Architectural style

DentalCare uses:

- Modular Monolith
- Layered Architecture
- REST API
- Client-Server architecture

Backend technology:

Java 21 + Spring Boot.

## General architecture

Frontend:

Next.js + React + TypeScript

Communication:

HTTPS + REST + JSON

Backend:

Spring Boot

Database:

PostgreSQL managed through Supabase.

General flow:

User
→ Frontend
→ REST API
→ Spring Security
→ Controller
→ Service
→ Repository
→ PostgreSQL

## Modular monolith

DentalCare is deployed as one Spring Boot application.

The application is internally separated into business modules.

Modules must remain logically independent.

Examples:

- auth
- users
- patients
- appointments
- medical-history
- clinical-records
- treatments
- billing
- inventory
- sterilization
- reports
- settings

This is NOT a microservices architecture.

## Root structure

src/main/java/com/dentalcare/api/

├── DentalCareApplication.java
├── config/
├── security/
├── exception/
├── shared/
└── modules/

## Shared infrastructure

### config

Application-level configuration.

Examples:

- ApplicationConfig
- CorsConfig
- OpenApiConfig
- SecurityConfig

### security

Authentication and authorization infrastructure.

Examples:

- JWT service
- JWT filter
- authentication handlers
- security services

### exception

Global API error handling.

### shared

Reusable technical components that do not belong to one business domain.

## Business modules

Business functionality belongs under:

modules/

Example:

modules/patients/

Each module uses layered architecture.

Example:

patients/
├── controller/
├── dto/
├── mapper/
├── model/
├── repository/
└── service/

## Dependency flow

The expected dependency direction is:

Controller
↓
Service
↓
Repository
↓
Database

DTO and Mapper support data transformation.

Controllers must not directly access repositories.

## Frontend integration

The frontend consumes the backend through REST services.

Expected frontend flow:

Page
→ Component / Hook
→ Frontend Service
→ REST API
→ DTO
→ Adapter / Mapper
→ Frontend Model
→ UI

The backend must provide stable DTO contracts.

## Design goals

The architecture must provide:

- maintainability
- clear responsibilities
- low coupling
- team parallelism
- testability
- controlled database evolution
- security
- easier Git collaboration