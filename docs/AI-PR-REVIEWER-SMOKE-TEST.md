# AI PR Reviewer Smoke Test

Este archivo existe únicamente para validar de forma controlada el funcionamiento del AI PR Reviewer de DentalCare.

La prueba debe confirmar que el agente puede:

- detectar el Issue relacionado con el Pull Request;
- leer el diff sin ejecutar código del PR;
- comparar los criterios del Issue contra la implementación;
- utilizar Gemini mediante `GEMINI_API_KEY` almacenada como GitHub Actions Secret;
- publicar un comentario automático en el Pull Request.

No contiene lógica de producción ni modifica el comportamiento del backend.
