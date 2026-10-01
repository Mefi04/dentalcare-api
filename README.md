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

## Integración continua

Los Pull Requests dirigidos a `develop` o `main` ejecutan el workflow `Backend CI`. La validación comprueba el diff real del PR con `git diff --check`, configura Java 21 con caché de Maven, confirma que Docker esté disponible, ejecuta `mvn -B clean verify` y construye la imagen del backend con el `Dockerfile` del repositorio.

Las pruebas de integración que utilizan Testcontainers levantan su propio PostgreSQL en Docker cuando corresponde; el CI no depende de la instancia compartida de Supabase ni requiere credenciales reales del proyecto.

Para reproducir las verificaciones principales localmente desde una rama basada en `develop`:

```bash
git fetch origin develop
git diff --check origin/develop...HEAD
mvn -B clean verify
docker build --tag dentalcare-api:local .
```

Docker debe estar disponible para que las pruebas basadas en Testcontainers puedan ejecutarse.

## Estado del esqueleto

La infraestructura está preparada para JWT y refresh tokens, pero su emisión, validación,
rotación y revocación se implementarán en el ticket específico de autenticación. Los módulos
de negocio están declarados como paquetes y todavía no contienen CRUDs ni modelos de dominio.
