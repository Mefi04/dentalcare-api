# Google Drive OAuth 2.0

## Objetivo

Configurar `dentalcare-api` para autenticarse contra el My Drive personal del propietario mediante OAuth 2.0, sin Service Account y sin guardar secretos en Git.

La subida y descarga de documentos clínicos no forma parte de esta configuración.

## Arquitectura

`DentalCare API -> OAuth 2.0 -> Google Drive API -> My Drive del propietario`

Los archivos creados posteriormente por la API pertenecerán a la cuenta Google autorizada y consumirán su cuota de almacenamiento.

## Configuración en Google Cloud

1. Crear o seleccionar el proyecto `DentalCare-Storage`.
2. Habilitar **Google Drive API**.
3. Configurar **Google Auth Platform / OAuth consent**.
4. Mientras la aplicación esté en pruebas, registrar la cuenta propietaria como usuario de prueba autorizado.
5. Crear un **OAuth 2.0 Client ID** para el flujo que se utilice durante la autorización.
6. Solicitar acceso offline y forzar consentimiento cuando sea necesario para obtener un `refresh_token`.
7. Mantener inicialmente el scope:

   `https://www.googleapis.com/auth/drive.file`

8. Crear la carpeta `DentalCare_Expedientes` en My Drive y conservar su `folderId`.

### Vigencia del refresh token durante pruebas

Para aplicaciones OAuth de tipo **External** con estado de publicación **Testing**, Google limita normalmente la vigencia del `refresh_token` a 7 días cuando se solicitan scopes como Drive.

Esto significa que un token obtenido durante pruebas sirve para validar la integración, pero no debe asumirse como una credencial permanente de producción. Antes de habilitar Drive de forma estable se debe revisar el estado de publicación de la aplicación OAuth y obtener un token apropiado para ese estado.

No se debe solucionar este comportamiento guardando access tokens manualmente ni deshabilitando la renovación automática.

## Consideración importante sobre `drive.file`

El scope `drive.file` otorga acceso por archivo: permite crear archivos nuevos y trabajar con archivos que la aplicación creó o que el usuario compartió explícitamente con la aplicación, por ejemplo mediante Google Picker.

Una carpeta creada manualmente antes de la integración no debe asumirse automáticamente accesible solo por conocer su `folderId`.

Por ello:

- este ticket conserva `drive.file` como scope inicial;
- no se amplía silenciosamente a `https://www.googleapis.com/auth/drive`;
- cuando se implemente la subida real, se debe comprobar el acceso a `DentalCare_Expedientes`;
- si la carpeta preexistente no es accesible con `drive.file`, se debe preferir crear/seleccionar la carpeta mediante un flujo compatible con acceso por archivo antes de solicitar un scope más amplio;
- cualquier ampliación de permisos debe documentarse y aprobarse explícitamente.

## Variables de entorno

```text
GOOGLE_DRIVE_ENABLED=false
GOOGLE_DRIVE_FOLDER_ID=
GOOGLE_DRIVE_CLIENT_ID=
GOOGLE_DRIVE_CLIENT_SECRET=
GOOGLE_DRIVE_REFRESH_TOKEN=
GOOGLE_DRIVE_APPLICATION_NAME=DentalCare API
```

No guardar valores reales de `client_secret`, `refresh_token` ni tokens de acceso en Git, logs, issues, PRs o capturas públicas.

## Comportamiento del backend

Con `GOOGLE_DRIVE_ENABLED=false`:

- no se crea el cliente `Drive`;
- no se requieren credenciales de Google;
- el backend mantiene su comportamiento normal.

Con `GOOGLE_DRIVE_ENABLED=true`:

- `GOOGLE_DRIVE_FOLDER_ID`, `GOOGLE_DRIVE_CLIENT_ID`, `GOOGLE_DRIVE_CLIENT_SECRET` y `GOOGLE_DRIVE_REFRESH_TOKEN` son obligatorios;
- una configuración incompleta detiene el arranque con un error claro;
- `UserCredentials` conserva el `refresh_token`;
- `HttpCredentialsAdapter` se encarga de proporcionar y renovar access tokens cuando el cliente realice solicitudes autenticadas;
- se expone un bean reutilizable de `Drive` para la futura capa de almacenamiento.

## Seguridad

- No usar Service Account para este My Drive personal.
- No crear permisos públicos `anyone`.
- No registrar tokens en logs.
- No incluir JSON de credenciales en el repositorio.
- Mantener Drive deshabilitado en entornos donde no esté configurado.
- Rotar/revocar las credenciales si un secreto se expone accidentalmente.

## Verificación

Antes de mergear:

```bash
mvn clean verify
git diff --check
```

Cuando el entorno lo permita:

```bash
docker compose build
```
