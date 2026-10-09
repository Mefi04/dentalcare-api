# Issue #149 — verificación final local

Fecha: 9 de octubre de 2026. Rama: `feature/issue-149-security-monitoring-alerts`.
Base: `9ee1cf996adaf5357ccfea653b5ee3fd472b2ade` (`origin/develop` al iniciar).
Cambios sin commit; no se hizo push, merge ni se publicó un PR.

## Arquitectura entregada

Actuator/Micrometer y registro Prometheus; un único timer HTTP con etiquetas acotadas e histogramas;
métricas HikariCP existentes; diez gauges de ventana HTTP y confirmación transaccional general separada de
fallos específicos de auditoría; mediciones en límites de repositorios, auditoría y almacenamiento sin consultas
ni escrituras adicionales. Correlation ID generado en servidor y logging JSON con lista de campos permitidos.
Actuator separado en 9091, loopback fuera de Docker e interfaz privada dentro del contenedor; acceso denegado
en el puerto API, incluso para usuarios autenticados. Prometheus interno y Grafana autenticado en loopback.
Dashboard de diez paneles, once reglas y retención limitada. No se añadió otra tabla/sistema de auditoría.

Configuración, arquitectura, umbrales, retención y runbook: [MONITORING.md](MONITORING.md).

## Resultados ejecutados

| Validación | Resultado real |
|---|---|
| `mvn clean verify`, Java 21, Maven 3.9.15 | BUILD SUCCESS; 1,251 pruebas, 0 fallos, 0 errores, 0 omitidas. Testcontainers disponible en el host. |
| `docker compose build` | Éxito; etapa Maven: 1,244 pruebas, 0 fallos/errores, 154 omitidas por falta de Docker accesible dentro del builder. La suite del host cubre las integraciones. |
| `git diff --check` | Correcto; revisión adicional de espacios finales en archivos modificados y nuevos: 0 hallazgos. |
| `promtool test rules alerts.test.yml` | SUCCESS: once alertas con disparo/recuperación; primera muestra tras ráfaga 401/403/429/5xx, expiración sin tráfico posterior, ceros sin tráfico y commit general sin falsa alerta de auditoría. |
| `monitoring/verify-local.ps1` | Éxito sobre PostgreSQL desechable, sin Supabase, R2 ni datos clínicos reales. Contenedores y volúmenes de este proyecto retirados al terminar. |

La imagen backend usada en la última demostración fue
`sha256:3dd21794c86d9296cade88174d4902089e05a5142c57376031d37007f8d36e00`.

## Evidencias de la última demostración

[Resultado completo](../monitoring/evidence/local-verification.json),
[promtool](../monitoring/evidence/promtool.txt),
[retención inspeccionada](../monitoring/evidence/retention.json) y
[hashes SHA-256](../monitoring/evidence/SHA256SUMS).

- Prometheus recolectando: `up{job="dentalcare"}=1`.
- HikariCP: máximo observado de 2 conexiones, conforme al perfil dev.
- Auditoría y cinco operaciones R2: series de fallo presentes desde cero.
- Histograma HTTP: p95 observado de aproximadamente **3.46 ms** sobre buckets acumulados del ensayo aislado (el dashboard sigue usando rate a cinco minutos). No constituye una línea base de rendimiento real.
- Grafana: dashboard provisionado con diez paneles; consulta autenticada a su datasource devolvió **40 respuestas 401** (cuarenta sintéticas, sin calentamiento). Se verificó mediante API, sin inspección visual del navegador.
- Prometheus permaneció detenido durante las cuarenta solicitudes. Su primer scrape observó exactamente **40** en `dentalcare_http_window_requests{status="401",route="application"}`: `burstBeforeFirstScrape=true`, sin muestra cero previa.
- `AuthenticationFailures`: estado `firing` tras ese primer scrape y recuperación natural al expirar la ventana de cinco minutos, sin nuevas solicitudes ni reinicio de métricas.
- Correlation IDs: 40 valores únicos, generados por servidor, sin reutilizar el encabezado recibido.
- Actuator en puerto API: health/prometheus/metrics/env denegados; 9091 sin publicación al host. Pruebas automatizadas adicionales deniegan acceso al usuario ordinario y ante colisión de puertos.
- Grafana sin sesión: 401; acceso anónimo deshabilitado.
- Logging: **233 registros estructurados** comprobados contra la lista de campos; sin centinelas sintéticos de JWT, token de conversación, query ni ID suministrado. Pruebas unitarias adicionales verifican exclusión de mensajes, argumentos, causas y MDC arbitrario.
- Logs Docker: 10 MB × 3 archivos por contenedor para backend, Prometheus y Grafana. Series temporales: políticas de siete días / 512 MB; no son una cuota rígida del volumen. WAL, head y compactación necesitan margen adicional.

Las alertas de almacenamiento y BD se validaron con series simuladas y pruebas de adaptadores/repositorios;
no se provocaron fallos contra proveedores reales. Las ejecuciones intermedias detectaron registro doble del observador y reintentos 429 del cliente HTTP de
pruebas. Se eliminaron el registro explícito redundante (Boot ya registra el handler) y los reintentos del
cliente. La prueba exige cuarenta eventos para cuarenta solicitudes; la tabla corresponde a la versión final.

## Correcciones posteriores a la auditoría independiente

| Hallazgo / causa raíz | Solución y prueba ejecutada |
|---|---|
| Commit externo posterior al retorno de AuditService | Un TransactionSynchronization por transacción existente. beforeCommit marca la confirmación y afterCompletion registra resultado no COMMITTED en dentalcare.transaction.commit.failures, nunca como fallo atribuible a auditoría. |
| Primera ráfaga invisible para increase() | HttpWindowMetrics consume las observaciones HTTP existentes y exporta diez gauges de ventana con categorías fijas. Sin contador acumulativo adicional ni incrementos artificiales. Java y promtool cubren primera muestra y expiración sin tráfico. |
| Excepción escapada registrada como 200 | Estado cuando se conoce; omisión cuando es indeterminado. REQUEST/ERROR conservan el ID generado; ERROR registra el resultado del contenedor y finally limpia/restaura MDC. |
| X-Request-ID no legible desde frontend | Access-Control-Expose-Headers limitado a X-Request-ID. Orígenes y permisos originales intactos; pruebas de origen autorizado/rechazado. |
| Retención confundida con cuota del volumen | MONITORING.md distingue política de eliminación y capacidad necesaria para WAL, head, checkpoints y compactación. |

Las **14 pruebas nuevas**, ejecutadas sin omisiones en el host, son:

- `TransactionObservationIntegrationTests`: cinco casos con PostgreSQL 17/Testcontainers. El fixture
  AuditService escribe valores duplicados en una restricción UNIQUE diferida dentro de una transacción
  Spring JDBC real: ambas llamadas retornan y el commit falla después. Se prueban completion UNKNOWN y
  ROLLED_BACK: un único fallo general, cero fallos atribuidos a auditoría y ninguna fila confirmada. Commit
  exitoso, rollback por otra operación y rollback-only mantienen ambos contadores en cero. No utiliza tablas
  clínicas ni altera propagación/atomicidad de servicios de negocio.
- `CorrelationHttpIntegrationTests`: cinco casos con Tomcat real / filtro aislado. Excepción del filtro y
  despacho ERROR, error de controlador resuelto como 422, respuestas normales con IDs distintos, 503 ya
  determinado y ráfagas HTTP reales 401/403/429 con conteo exacto (sin scraping ni calentamiento).
- `HttpWindowMetricsTests`: dos casos con reloj controlado. Ráfaga antes de la primera lectura, 500/503
  agrupados como 5xx, expiración sin tráfico, ceros sin tráfico, separación de gestión y scrapes sin incrementos.
- `MonitoringCorsTests`: dos casos, exposición al origen autorizado y rechazo de origen ajeno.

DatabaseErrors y su panel incluyen los fallos generales de confirmación; AuditPersistenceFailure conserva
su significado específico. Spring no proporciona la causa en afterCompletion: UNKNOWN no prueba pérdida
de datos ni permite culpar a una escritura de auditoría. Un error anterior al callback beforeCommit,
una transacción no observada o una caída del proceso quedan fuera de esa atribución. La ventana HTTP reside
en memoria, usa buckets de un segundo y se pierde al reiniciar el backend. No se añadieron dependencias,
transacciones, consultas operativas ni infraestructura en esta ronda.

## Archivos modificados y añadidos

Existentes:

- `.env.example`
- `docker-compose.yml`
- `pom.xml`
- `src/main/resources/application.yml`
- `src/main/java/com/dentalcare/api/config/CorsConfig.java`
- `docs/ARCHITECTURE.md`
- `docs/DOCKER.md`
- `docs/SECURITY.md`

Infraestructura técnica nueva:

- `src/main/java/com/dentalcare/api/config/MonitoringMetricsConfig.java`
- `src/main/java/com/dentalcare/api/config/MonitoringSecurityConfig.java`
- `src/main/java/com/dentalcare/api/shared/observability/CorrelationIdFilter.java`
- `src/main/java/com/dentalcare/api/shared/observability/HttpWindowMetrics.java`
- `src/main/java/com/dentalcare/api/shared/observability/OperationMetricsPostProcessor.java`
- `src/main/java/com/dentalcare/api/shared/observability/SafeJsonEncoder.java`
- `src/main/resources/logback-spring.xml`

Monitoreo y simulaciones:

- `monitoring/compose.verify.yml`
- `monitoring/verify-local.ps1`
- `monitoring/prometheus/prometheus.yml`
- `monitoring/prometheus/alerts.yml`
- `monitoring/prometheus/alerts.test.yml`
- `monitoring/grafana/dashboards/dentalcare.json`
- `monitoring/grafana/provisioning/dashboards/dentalcare.yml`
- `monitoring/grafana/provisioning/datasources/prometheus.yml`

Pruebas:

- `src/test/java/com/dentalcare/api/config/MonitoringMetricsTests.java`
- `src/test/java/com/dentalcare/api/config/MonitoringCorsTests.java`
- `src/test/java/com/dentalcare/api/config/MonitoringPortCollisionTests.java`
- `src/test/java/com/dentalcare/api/config/MonitoringSecurityTests.java`
- `src/test/java/com/dentalcare/api/shared/observability/ObservabilityTests.java`
- `src/test/java/com/dentalcare/api/shared/observability/TransactionObservationIntegrationTests.java`
- `src/test/java/com/dentalcare/api/shared/observability/CorrelationHttpIntegrationTests.java`
- `src/test/java/com/dentalcare/api/shared/observability/HttpWindowMetricsTests.java`

Documentación y evidencias:

- `docs/MONITORING.md`
- `docs/MONITORING-VERIFICATION.md`
- `monitoring/evidence/local-verification.json`
- `monitoring/evidence/promtool.txt`
- `monitoring/evidence/retention.json`
- `monitoring/evidence/SHA256SUMS`

## PR #161 e integración pendiente

Revisado antes de modificar código y nuevamente durante el cierre: abierto, sin merge, `mergeable=true`,
head `387f852c07c4eee146093adabec6b6a929c6ce7c`. Su rama no fue modificada. Los puntos compartidos son
`application.yml`, `.env.example` y `docs/SECURITY.md`; deben conservarse las propiedades y documentación
de conversaciones/outbox de ese PR. La cadena nueva de gestión debe conservar su prioridad frente a
SecurityConfig. Reejecutar las pruebas de conversación/OTP después de integrar ambas ramas.

## Riesgos residuales y veredicto

- Es monitoreo local: no hay disponibilidad continua ni notificación externa durante caída del host.
- Los umbrales necesitan tráfico representativo. La ráfaga HTTP anterior al primer scrape está cubierta; un reinicio pierde la ventana en memoria. Los otros contadores acumulativos todavía necesitan muestra de referencia para increase().
- Las métricas de almacenamiento observan operaciones y el stream de entrada R2, no ocupación real del bucket ni entrega final al cliente. No se verificó infraestructura R2/Supabase real.
- Revocar refresh tokens no invalida inmediatamente access JWT existentes; el runbook documenta expiración y rotación global de claves.
- Se suprimen mensajes libres y stack traces: se reduce el detalle diagnóstico para garantizar minimización de datos.
- La auditoría existente conserva sus escrituras por bloqueo y su política de retención propia pendiente; este ticket no aumenta esa carga ni borra evidencias de negocio.
- La gestión privada confía en la red de contenedores: no publicar 9091/9090 ni conectar contenedores no confiables. El despliegue requiere validar firewall, TLS, operación y escalamiento.

**Veredicto técnico: APROBABLE para revisión local tras las correcciones.** No implica aprobación de despliegue público ni
verificación contra proveedores reales. Pendiente revisión del usuario antes de publicar el PR.
