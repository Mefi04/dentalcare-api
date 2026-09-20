# DentalCare API

Backend del sistema DentalCare.

Este proyecto implementa la API REST encargada de la lógica de negocio, seguridad, persistencia y comunicación con la base de datos del sistema DentalCare.

## Stack tecnológico

- Java 21
- Spring Boot
- Maven
- Spring Web
- Spring Data JPA
- Spring Security
- JWT + Refresh Token
- BCrypt
- PostgreSQL
- Supabase
- Liquibase
- OpenAPI / Swagger
- Spring Boot Actuator
- Docker
- Docker Compose

## Arquitectura

DentalCare API utiliza:

- Monolito modular
- Arquitectura por capas
- API REST
- Arquitectura cliente-servidor

La aplicación se divide por módulos de negocio.

Cada módulo sigue una estructura similar a:

```text
modules/
└── patients/
    ├── controller/
    ├── dto/
    │   ├── request/
    │   └── response/
    ├── mapper/
    ├── model/
    ├── repository/
    └── service/
```

## Ejecución local

Requisitos: Java 21, Maven 3.6.3 o superior y acceso a PostgreSQL/Supabase.

1. Copia `.env.example` como `.env` y sustituye los valores de ejemplo localmente.
2. Exporta las variables del archivo en tu entorno.
3. Ejecuta `mvn spring-boot:run -Dspring-boot.run.profiles=dev`.

La API escucha en `http://localhost:8080`. El health check público está disponible en
`GET /actuator/health`; Swagger UI está habilitado únicamente con el perfil `dev`.

## Docker

Con las variables de entorno configuradas, ejecuta:

```bash
docker compose up --build
```

El Compose principal usa PostgreSQL/Supabase externo y no crea una base de datos local.

## Estado del esqueleto

La infraestructura está preparada para JWT y refresh tokens, pero su emisión, validación,
rotación y revocación se implementarán en el ticket específico de autenticación. Los módulos
de negocio están declarados como paquetes y todavía no contienen CRUDs ni modelos de dominio.
