# Security monitoring — issue #149

## Architecture

The modular monolith remains unchanged. Actuator/Micrometer provides one HTTP timer (including 401,
403, 429 and 5xx), JVM, datasource and HikariCP instruments. A Prometheus registry is the only new
Java dependency. `MonitoringMetricsConfig` reduces HTTP labels to fixed route groups (`auth`,
`documents`, `api`, `management`, `other`), a bounded method and status. No patient, actor, IP,
document, object key, request ID, exception message, conversation token or query is a metric label.
Histograms span 1 ms–30 s with explicit latency boundaries; dashboard p95 uses histogram_quantile
over a five-minute rate. Documents are displayed separately and excluded from the initial latency alert.

`OperationMetricsPostProcessor` decorates existing repository, AuditService and ClinicalDocumentStorage
boundaries. It never adds SQL queries, schema changes or audit rows. Database errors have fixed categories
constraint/timeout/connection/other. This measures errors escaping repository calls, not every PostgreSQL
server error or errors caught within a repository. Constraint violations are displayed but excluded from
the initial technical-database alert. Audit failures are measured at the service boundary, including errors
propagated by its existing transaction proxy. An external transaction can commit after that boundary returns.
A single TransactionSynchronization per observed existing transaction separately counts
`dentalcare.transaction.commit.failures{completion="rolled_back|unknown"}` when confirmation has begun
(beforeCommit) and completion is not COMMITTED. Multiple repository/audit calls do not duplicate that count.
Explicit business rollbacks and rollback-only outcomes before confirmation are excluded. No transaction,
flush, query or write is added; atomicity and propagation remain unchanged.

Spring supplies no cause to afterCompletion. These general commit outcomes are NEVER attributed to audit
persistence, even if an audit method participated. UNKNOWN can mean the commit outcome is uncertain, not
proven data loss. A failure before this observer's beforeCommit callback, an unobserved transaction, or a
process crash cannot be reliably classified by this hook. Direct errors escaping AuditService still use the
specific audit counter. Repository failures and general commit failures are distinct boundaries, not a count
of unique SQL errors. The DatabaseErrors alert/dashboard includes the general metric; AuditPersistenceFailure
retains its specific meaning. Existing rate-limit audit writes are preserved, not duplicated.

Storage operations have fixed names store/load/getMetadata/exists/delete and success/failure. A successful
load only means stream opening succeeded. A stream wrapper separately measures EOF completion, I/O failure,
truncation and early cancellation, counting each transfer once. No extra reads, buffering or storage requests
are introduced. This observes the R2 input stream, not proof of delivery to the final client socket.
Unknown-length streams require EOF to claim completion. Operation failures are generic storage failures (including disabled storage), not proof of a
provider outage. Total bucket occupation is intentionally not inferred from transfer traffic.

Prometheus evaluates rules; Grafana queries the same datasource and displays firing alerts. This setup has
no email, SMS or remote notification delivery. There is no Loki, ELK, SaaS or separate Alertmanager.
Custom error/transfer/commit series are initialized at zero with twenty bounded meter identities. A zero
means no application event, not provider health. HTTP timers remain the single cumulative HTTP instrument.
Initializing a counter at zero alone cannot recover events before its first scrape.

HttpWindowMetrics therefore exports a rolling view as ten gauges, not a second cumulative counter:
`dentalcare_http_window_requests{status="401|403|429|5xx|other",route="application|management"}`.
It consumes the same Spring HTTP observations and status convention once, via Boot's automatic handler
registration. A fixed 300-slot ring records one-second buckets; no raw route, identifier or exception text is kept.
Scrapes sum only current buckets and never increment anything. The four HTTP error alerts read this window
without increase(), so a complete burst before the first scrape is visible and expires without later traffic.
This works even if Prometheus starts after the backend has been running for a long time. Window precision
is one second. Restarting the backend loses this in-memory window; no additional persistence is introduced.
Other cumulative error metrics still require a baseline scrape for increase(). Histogram/rate dashboard gaps
mean no observed interval, not proven zero. The isolated verification starts Prometheus AFTER its HTTP burst.

## Management isolation

Actuator listens on loopback port 9091 by default; Compose explicitly binds it to the container's network
interface for scraping. Only health and prometheus are permitted on that local port; the ordered
security chain denies other Actuator requests and denies Actuator on API port 8080 even for authenticated
users. Compose never publishes 9091 or Prometheus 9090. The private listener relies on container network
access control, not user JWTs. Never publish it, proxy it through the frontend, or run an untrusted container
on the same network. Outside Docker, retain the default loopback address. Setting API port 9091 fails closed
for management access; do not reuse that port for the API.
Health moved to the private listener; probes must use it there.

Grafana is bound to 127.0.0.1:3001 (configurable). Anonymous access and registration are disabled. Its
startup fails if GRAFANA_ADMIN_PASSWORD is absent. Normal backend-only Compose builds do not require
Grafana credentials. Existing Grafana volumes retain the initial administrator password; changing the
environment alone does not reset that account. Use the supported Grafana password-reset procedure.

## Local configuration

Use existing backend environment variables and a unique local Grafana password:

```powershell
$env:GRAFANA_ADMIN_PASSWORD = '<unique local password>'
docker compose --profile monitoring up --build -d
```

Open http://127.0.0.1:3001, user `monitor` (or GRAFANA_ADMIN_USER), and dashboard
`DentalCare security and operations`. This normal command uses the configured database: use the isolated
verification command below when real clinical data might otherwise be selected.

Prometheus scrapes every 15 s with retention policies of seven days and 512 MB. The size policy is NOT a
hard quota for the volume: WAL, checkpoints, active/head chunks, compaction and delayed block deletion need
additional disk space. Reserve operating headroom beyond 512 MB and monitor free space; only eligible
persistent blocks can be deleted by retention. See [Prometheus storage](https://prometheus.io/docs/prometheus/latest/storage/).
Grafana and Prometheus
volumes persist between ordinary restarts. Recording rules and the dashboard live under `monitoring/`.
Adjust thresholds only after a representative baseline; local traffic is sparse and does not establish an SLA.

## Initial thresholds

| Alert | Window / threshold | Pending duration |
|---|---|---|
| BackendUnavailable | scrape down | 1 min |
| AuthenticationFailures | at least 20 HTTP 401 in 5 min | immediate |
| AuthorizationFailures | at least 10 HTTP 403 in 5 min | immediate |
| RateLimitSurge | at least 10 HTTP 429 in 5 min | immediate |
| ServerErrors | at least 5 HTTP 5xx and over 5% in 5 min | immediate |
| HighLatency | p95 >1 s, at least 50 non-document/non-management requests in 5 min | 5 min |
| DatabasePoolPressure | active/max >80% and pending >0 | 2 min |
| DatabaseConnectionTimeout | any connection timeout in 5 min | immediate |
| DatabaseErrors | at least 3 non-constraint repository / general commit failures in 5 min | immediate |
| StorageFailures | at least 3 operation/transfer failures in 5 min | immediate |
| AuditPersistenceFailure | any audit persistence failure in 5 min | immediate |

A single storage request can fail at opening or transfer, never both in the current decorator. Early client
cancellation is visible but does not trigger StorageFailures. 429 includes frequency and concurrency
rejections because both use the existing HTTP contract. Metrics do not attempt to identify individuals.

## Logging and retention

SafeJsonEncoder emits only timestamp, level, logger, fixed event code, generated request_id, exception_type,
HTTP status (when determined) and duration_ms. Messages, arguments, arbitrary MDC/structured fields, stack traces and causes
are discarded, including Hibernate and AWS diagnostic text. Fixed operation events retain safe context;
arbitrary startup/library logs retain logger, level and exception class only. This intentionally reduces
free-text diagnostics. Do not add a plain console appender, SQL stdout, request/response logging or wire logs
in an operational profile. The Boot banner/JVM notices may be plain text; they contain no request content.

CorrelationIdFilter ignores the supplied X-Request-ID and generates a new UUID, returns it in the response,
and clears its MDC scope in finally. ERROR dispatches reuse the server-generated request attribute and
restore their own MDC scope. Logs describe dispatches: when an exception escapes before an error status is
observable, status is omitted rather than guessed as 200/500. The container ERROR dispatch records the actual
status when it occurs; a previously determined error status is preserved. One request can have two dispatch
records with the same ID; these records do not drive HTTP counters. Authorized CORS clients can read
X-Request-ID through Access-Control-Expose-Headers; origins and allowed request headers are unchanged.
It is never a metric label or an audit entity identifier. Asynchronous
tasks do not inherit this scope automatically; do not copy arbitrary request MDC to worker threads.

Backend, Prometheus and Grafana Docker logs rotate at 10 MB x 3 files per container. This is a size bound, not a guaranteed number of days.
Metrics use seven-day / 512 MB retention policies, with additional disk headroom as described above. Proposed incident evidence retention is 30 days, access restricted to
the response team, with deletion after review unless a documented preservation hold applies. Export only
sanitized metrics, event codes, timestamps and generated IDs; never database rows or object contents.
The existing protected audit table retains its own identifiers and remains separate from operational logs.
This ticket does not introduce automatic deletion of business audit history: its retention needs an approved
policy and migration/maintenance design. Local disks/volumes are not an immutable evidence archive.

## Verification without clinical data

Run with PowerShell 7, Docker Desktop and Java 21/Maven available:

```powershell
mvn clean verify
docker compose build
git diff --check
docker run --rm --entrypoint promtool -v "${PWD}/monitoring/prometheus:/etc/prometheus:ro" -w /etc/prometheus prom/prometheus:v3.5.0 test rules alerts.test.yml
./monitoring/verify-local.ps1
```

The last command creates project `dentalcare-issue149`, a disposable PostgreSQL database and process-local
RSA/DB/Grafana credentials. R2 and initial-admin creation are disabled. The API is bound to 127.0.0.1:18080,
Grafana to 127.0.0.1:13001. It does not connect to Supabase or send files, email or business mutations.
It first waits on private health with Prometheus stopped, generates a 401 burst against a protected audit
route, starts Prometheus, and verifies that its first scrape sees all forty rolling-window events. It verifies
real scrape/Hikari series, validates
unique IDs and private endpoints, queries actual values through authenticated Grafana, waits for firing
and natural five-minute recovery, checks synthetic secret sentinels are absent from logs, then removes
only this project's containers/volumes. Evidence contains no credentials. Do not run concurrently with
another verification using the same project name/ports. Allow roughly ten minutes plus image build time.

`alerts.test.yml` exercises all eleven alerts and their recovery, plus low-error-rate and expected-constraint
negative cases with promtool's virtual clock. Added cases cover all four HTTP categories on the very first
sample, expiry without later traffic, no-traffic zeros, and general commit failures without an audit alert.
Java tests cover the rolling window clock, exact counts from real HTTP observations, Tomcat ERROR dispatch,
resolved controller errors, normal responses, CORS allow/reject behavior, encoder redaction, stream failures,
MDC cleanup and management isolation. PostgreSQL/Testcontainers reproduces deferred-constraint commit
failure after two audit calls return, with both UNKNOWN and ROLLED_BACK completion, and verifies one general
counter / zero specific audit failures. Success, unrelated rollback and rollback-only outcomes remain zero.
The verification p95 uses cumulative buckets for this isolated cold burst; the normal dashboard continues to
use a five-minute rate. Real cloud-provider failure/credential rotation is not exercised.

## Incident response

1. Triage: record UTC time, alert name, severity and safe aggregate metrics; distinguish backend-down,
   abuse, pool saturation, PostgreSQL failure and storage failure. Check scrape freshness first.
2. Escalate: inform the project maintainer/security owner through the team's existing channel; this local
   deployment has no staffed pager or automatic external notification. Treat audit failure and sustained
   5xx/unavailability as critical; do not label every 401 as compromise.
3. Contain: restrict ingress or temporarily stop the affected local service. Preserve rate/concurrency limits;
   do not disable authentication, ownership checks, validation or increase the pool blindly.
4. Credentials: revoke affected refresh sessions using the existing service/approved administrative procedure.
   Family reuse already revokes its family; password change/recovery revokes all user refresh sessions.
   Existing access JWTs remain valid until expiration (default 30 min). Immediate global invalidation requires
   coordinated replacement of the signing key pair/restart and client reauthentication; there is no per-token
   access revocation endpoint. Do not invent an unauthenticated emergency endpoint.
5. Rotate compromised DB/R2 credentials in the provider console with least privilege, replace environment
   values and restart affected services; verify old credentials are revoked. Preserve unrelated data and do
   not test against real documents. Conversation/OTP credentials from #161 must never be copied into logs;
   its final revocation procedures need integration when that PR lands.
6. Preserve sanitized evidence with restricted access and an integrity hash; never attach raw dumps, clinical
   objects, provider keys or token-bearing requests to GitHub. Record actions and timestamps in the team's
   incident record; use the existing audit module for relevant application events, not a second audit table.
7. Recover: verify access controls, error rates, pool pressure, scrape freshness and alert resolution. Record
   root cause, remaining exposure and follow-up work, then apply the evidence retention policy.

## Integration and limits

PR #161 was open at implementation start (head 387f852c, base 9ee1cf9). Its branch is untouched. Shared
integration files are application.yml, .env.example and docs/SECURITY.md; preserve #161's appointment/outbox
settings and environment placeholders during integration. The new ordered management chain must remain ahead of
SecurityConfig. SafeJsonEncoder intentionally suppresses free text from future OTP/conversation log calls.
Re-run auth/conversation/rate-limit tests and regenerate OpenAPI only if the merged API contract changes.
No new Liquibase migration or business endpoint is added here.

Local monitoring stops when Docker/the laptop stops and cannot notify during host failure. Public deployment
requires private-network/firewall checks, TLS, least-privilege infrastructure credentials, secured remote
notification delivery, operational ownership, representative load testing and approved evidence/audit retention.
R2 occupation and PostgreSQL server-wide query diagnostics require separately authorized provider telemetry;
these application counters cannot claim that coverage. Existing per-block database audit writes can still
contribute to pool pressure during abuse; this ticket adds no more writes and makes that pressure observable.
