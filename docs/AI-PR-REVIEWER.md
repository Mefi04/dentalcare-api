# DentalCare AI PR Reviewer

## Objetivo

El AI PR Reviewer compara automáticamente el Issue asignado con los cambios reales de un Pull Request dirigido a `develop`.

El agente es únicamente consultivo. No modifica código, no hace commits, no aprueba Pull Requests y no ejecuta merges.

## Flujo

1. Se abre o actualiza un PR hacia `develop`.
2. El agente identifica el Issue asociado.
3. Lee el título, descripción y comentarios de aclaración del Issue.
4. Obtiene los archivos modificados y el patch mediante la API de GitHub.
5. Lee reglas del proyecto desde la rama base, incluyendo `AGENTS.md` y documentación disponible.
6. Consulta los checks disponibles del commit del PR.
7. Envía a Gemini únicamente el contexto necesario y previamente filtrado.
8. Gemini compara requisitos vs. implementación.
9. El workflow crea o actualiza un único comentario `DentalCare AI PR Reviewer` en el PR.

## Asociar un PR con su Issue

La forma recomendada es incluir en la descripción del PR:

```text
Closes #10
```

También se reconocen referencias como:

```text
Fixes #10
Resolves #10
Issue #10
Ticket #10
```

Como fallback, el agente también reconoce ramas con formato similar a:

```text
feature/issue-10-login
fix/issue-10-login
```

Si no se identifica el ticket con suficiente confianza, el agente no inventa requisitos y publica instrucciones para relacionar correctamente el Issue.

## Configurar la API key de Gemini sin exponerla

La API key **nunca debe agregarse al repositorio, `.env`, YAML, README, Issue, PR o código fuente**.

En GitHub:

1. Abrir el repositorio `Mefi04/dentalcare-api`.
2. Ir a `Settings`.
3. Abrir `Secrets and variables`.
4. Entrar a `Actions`.
5. Seleccionar la pestaña `Secrets`.
6. Presionar `New repository secret`.
7. Configurar:

```text
Name: GEMINI_API_KEY
Secret: <pegar aquí la clave generada en Google AI Studio>
```

8. Guardar con `Add secret`.

El valor queda almacenado como GitHub Actions Secret y el workflow lo consume con:

```yaml
${{ secrets.GEMINI_API_KEY }}
```

No debe copiarse el valor de la clave en ninguna variable normal del repositorio.

## Modelo

Por defecto se utiliza:

```text
gemini-3.8-flash
```

Puede cambiarse sin modificar código creando una Repository Variable:

```text
GEMINI_MODEL
```

Ejemplo:

```text
GEMINI_MODEL=gemini-3.8-flash
```

La API key sigue siendo Secret; `GEMINI_MODEL` sí puede ser una Variable porque no contiene credenciales.

## Seguridad del workflow

El workflow utiliza `pull_request_target` porque necesita acceso a un Secret y debe evitar ejecutar código controlado por el PR.

Medidas aplicadas:

- el workflow se ejecuta desde código confiable de la rama predeterminada;
- no hace checkout de `head` del PR;
- no ejecuta Maven, scripts, dependencias ni código proveniente del PR;
- obtiene el diff usando la API de GitHub;
- no tiene permiso `contents: write`;
- no puede hacer push ni merge;
- solo utiliza permisos de lectura y permisos mínimos para actualizar el comentario del PR;
- omite archivos típicamente sensibles como `.env`, `.pem`, `.key`, `.p12`, `.pfx`, keystores y archivos de credenciales;
- redacta patrones comunes de password, token, API key, private key y Authorization Bearer antes de enviar contexto a Gemini;
- limita la cantidad de diff enviada para controlar cuota y exposición de información.

Nunca debe modificarse este workflow para hacer checkout del SHA/branch `head` del Pull Request y después ejecutar ese código con `GEMINI_API_KEY` disponible.

## Rama predeterminada y activación

Actualmente la rama predeterminada del repositorio es `main`.

`pull_request_target` ejecuta el workflow confiable desde la rama predeterminada. Por ese motivo, para que el AI Reviewer quede activo de forma automática en PRs cuyo destino sea `develop`, los archivos del AI Reviewer deben existir finalmente en `main`.

Despliegue recomendado:

1. Revisar esta implementación mediante PR.
2. Validar que no afecte Backend CI ni el PR Review Bot.
3. Promover los archivos del AI Reviewer a `main` mediante Pull Request, nunca mediante commit directo.
4. Configurar `GEMINI_API_KEY` como Repository Secret.
5. Abrir o actualizar un PR de prueba hacia `develop` con `Closes #XX`.
6. Confirmar que aparece el comentario `DentalCare AI PR Reviewer`.

## Privacidad

El Free Tier de Gemini puede utilizar contenido enviado para mejorar productos de Google. Por eso este agente minimiza el contexto y filtra archivos/patrones sensibles antes de llamar a Gemini.

Aun así, no se debe incluir en Issues o código versionado:

- datos reales de pacientes;
- contraseñas;
- tokens;
- claves privadas;
- secretos de Supabase;
- credenciales de producción;
- archivos `.env` reales.

## Qué analiza la IA

El reporte cubre:

- cumplimiento de requisitos del Issue;
- evidencia concreta por archivo;
- requisitos parcialmente implementados o no confirmados;
- posibles problemas de seguridad;
- desviaciones de arquitectura respecto a las reglas del proyecto;
- pruebas agregadas y resultados de checks visibles;
- cambios potencialmente fuera de alcance;
- riesgos restantes;
- elementos que requieren revisión humana.

## Qué no hace

El AI Reviewer no:

- sustituye `Backend CI`;
- sustituye `PR Review Bot`;
- sustituye SonarQube;
- ejecuta el código del PR;
- garantiza que el software esté libre de vulnerabilidades;
- toma la decisión final de aceptar un PR;
- hace merge automático.

La aprobación final continúa siendo responsabilidad del equipo.

## Comportamiento ante errores o cuota

Si Gemini devuelve HTTP `429` por agotamiento temporal de cuota del Free Tier, el agente publica un estado informativo y termina sin convertir ese evento en una aprobación o rechazo del PR.

Las validaciones deterministas existentes continúan funcionando independientemente del AI Reviewer.
