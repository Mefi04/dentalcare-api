# SonarQube en Pull Requests

El repositorio incluye el workflow `.github/workflows/sonarqube-pr.yml` para analizar Pull Requests cuyo destino sea `develop`.

## Qué valida

Cuando SonarQube está configurado, el workflow:

- usa Java 21;
- hace checkout con historial completo para mejorar la precisión del análisis de Pull Requests;
- ejecuta el build y las pruebas Maven;
- permite que Testcontainers utilice Docker en el runner;
- genera cobertura de pruebas en formato XML con JaCoCo 0.8.15;
- ejecuta SonarScanner for Maven 5.8.0.7211;
- envía el resultado a SonarQube Cloud o a un servidor SonarQube configurado;
- espera el resultado del Quality Gate y falla el check cuando el Quality Gate queda rojo.

El workflow complementa `Backend CI` y `PR Review Bot`. No reemplaza ninguno de los dos.

## Configuración requerida

La integración está diseñada para no romper los Pull Requests existentes mientras todavía no se hayan configurado las credenciales. Si faltan valores, el workflow deja una advertencia y omite únicamente el envío a SonarQube.

### SonarQube Cloud

En GitHub, dentro de `Settings > Secrets and variables > Actions`, configurar:

**Secret del repositorio**

- `SONAR_TOKEN`: token de análisis generado en SonarQube Cloud.

**Variables del repositorio**

- `SONAR_PROJECT_KEY`: clave del proyecto importado en SonarQube Cloud.
- `SONAR_ORGANIZATION`: clave de la organización de SonarQube Cloud.
- `SONAR_HOST_URL`: opcional. Si se omite, el workflow usa `https://sonarcloud.io`.

El proyecto debe estar importado/vinculado con este repositorio de GitHub para que SonarQube Cloud pueda decorar los Pull Requests con su Quality Gate.

### SonarQube Server

Para un servidor SonarQube propio:

**Secret del repositorio**

- `SONAR_TOKEN`: token de análisis del proyecto.

**Variables del repositorio**

- `SONAR_PROJECT_KEY`: clave del proyecto.
- `SONAR_HOST_URL`: URL base del servidor SonarQube, por ejemplo `https://sonar.ejemplo.com`.
- `SONAR_ORGANIZATION`: normalmente no es necesaria en SonarQube Server.

La disponibilidad de análisis de Pull Requests depende de la edición/configuración de SonarQube Server utilizada.

## Flujo esperado

Después de configurar los valores anteriores, un Pull Request hacia `develop` tendrá checks separados, entre ellos:

- `Backend CI / Verify backend`
- `PR Review Bot / Analyze pull request`
- `PR Review Bot / Publish review`
- `SonarQube PR Analysis / Code analysis`

Si Maven/tests fallan o el Quality Gate de SonarQube falla, el check de SonarQube quedará rojo.

## Seguridad

- `SONAR_TOKEN` se mantiene exclusivamente como GitHub Actions Secret.
- El token no se imprime en logs.
- El workflow usa permisos de GitHub de solo lectura (`contents: read`).
- No se reutilizan credenciales de Supabase, JWT ni base de datos de producción.
- Pull Requests desde forks no reciben secrets de GitHub; en ese caso el análisis remoto puede quedar omitido de forma segura.
