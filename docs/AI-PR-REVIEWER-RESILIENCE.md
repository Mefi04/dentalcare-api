# Resiliencia del DentalCare AI PR Reviewer

El AI PR Reviewer usa una estrategia de tolerancia a fallos para errores transitorios de Gemini.

## Orden de modelos

1. `gemini-3.8-flash` como modelo principal.
2. `gemini-3.7-flash` como primer respaldo.
3. `gemini-3.5-flash-lite` como segundo respaldo.

El modelo principal puede cambiarse con la Repository Variable `GEMINI_MODEL`.
Los respaldos pueden cambiarse con `GEMINI_FALLBACK_MODELS`, usando valores separados por coma.

## Reintentos

Se consideran transitorios:

- HTTP 408
- HTTP 429
- HTTP 500
- HTTP 502
- HTTP 503
- HTTP 504
- errores de red y timeout
- respuestas vacías o JSON inválido del modelo

El modelo principal recibe hasta 4 intentos. Cada modelo de respaldo recibe hasta 2 intentos.
Entre intentos se aplica backoff exponencial con jitter y se respeta `Retry-After` cuando Gemini lo envía.

No se reintentan automáticamente errores permanentes como 400, 401 o 403, porque pueden indicar configuración o credenciales inválidas.

## Seguridad

La estrategia resiliente no cambia el modelo de seguridad original:

- `GEMINI_API_KEY` continúa únicamente como GitHub Actions Secret.
- El workflow no ejecuta código proveniente del head del PR con el Secret disponible.
- Los modelos de respaldo reciben exactamente el mismo contexto previamente filtrado y redactado.
- El agente sigue sin permisos para hacer push o merge.

## Resultado

Si un modelo de respaldo responde correctamente, el comentario del PR muestra el modelo que realmente produjo el análisis.

Si todos los intentos fallan, el AI Reviewer publica un estado informativo y Backend CI, PR Review Bot y SonarQube continúan funcionando de manera independiente.
