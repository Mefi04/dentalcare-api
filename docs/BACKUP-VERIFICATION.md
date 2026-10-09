# Backup verification — phase 1

Executed locally on **2026-10-09**, branch
`feature/issue-150-backup-disaster-recovery`, based on
`816507e8fe156fc85a97b4292c32ae94f2639b50` (#163 / #149 integrated).
No Supabase database, clinical R2 bucket, real document or external notification service
was accessed by the backup laboratory. No commit, push, merge or PR was created.

## Executed results

| Verification | Actual result |
|---|---|
| `mvn clean verify` (final execution) | BUILD SUCCESS; 1,254 tests, 0 failures, 0 errors, 0 skipped; 5:20 minutes |
| UNKNOWN callback characterization | 3 cases passed: committed, rolled back, unknown; mocked storage only |
| `docker compose build` (after the new Java tests) | Exit 0; backend image built |
| `./operations/backup/run-lab.ps1` (final execution) | Exit 0; 43 operational tests, 0 failures/errors |
| Existing eleven alerts, `promtool` | SUCCESS |
| Six backup alerts, `promtool` | SUCCESS; firing/recovery, missing series, failure before first scrape and restart |
| `git diff --check` | No errors |
| New, untracked files checked separately with `git diff --no-index --check` | No whitespace errors |
| Optional monitoring Compose configuration | Valid using synthetic placeholders; exporter/Prometheus have no published ports, exporter read-only, Grafana host IP 127.0.0.1 |
| Launcher cleanup failure suite | 3 tests, 0 failures; mocked Docker exit codes |
| Cleanup | No `dc-backup-` containers, volumes or networks remained after the final run |

The backend image's Maven build runs without Docker Engine access; it does not replace
the host Maven execution or the separate backup laboratory. No claim of representative
production performance is made. The laboratory is not a full application startup test.

## Sanitized evidence from the final laboratory execution

```json
{"event":"encrypted_backup_created","plaintext_artifacts_published":false}
{"event":"isolated_restore_verified","tables":66,"documents":1,"metadata_only":1,"recovery_seconds":8.336}
{"event":"backup_tests_completed","tests":43,"failures":0,"errors":0,"failed_tests":[]}
```

The source schema was created by Liquibase from all 41 repository migrations. Its 64
application tables and two Liquibase tables were restored into an independent empty
PostgreSQL 17 instance. Canonical row hashes, catalog definitions (including columns and independent indexes), constraints, sequence state, document
references and recovered document SHA-256 matched. A foreign-key-protected patient
deletion was rejected after restoration. The metadata-only fixture deliberately has no
object key and a non-null size to exercise that distinction without inventing a missing file.

Operational-log inspection checked all three JSON evidence events and the launcher
output: no UUID-shaped patient/document identifiers, object paths, connection strings,
private age key markers, JWT markers or the sensitive-field test sentinel were present.
This is a scoped verification of synthetic output, not a claim that every possible future
provider diagnostic has been reviewed.

## Positive and negative scenarios actually executed

1. Successful encrypted backup and independent database/object restoration.
2. Missing source object: backup rejected; prior valid bundle/timestamp preserved.
3. Corrupted/truncated source size: rejected.
4. Modified ciphertext: authenticated decryption rejected before database modification.
5. Wrong private identity: decryption rejected.
6. Interrupted encryption with partial output: partial removed, previous complete set retained.
7. Actual refused PostgreSQL connection on the laboratory endpoint: safe failure.
8. Simulated zero free space at the capacity check: safe rejection (not a physical disk-full test).
9. Unsupported bundle format, changelog digest or PostgreSQL major: rejected.
10. Missing or same-size-corrupted bundled object: integrity rejected, including a re-encrypted altered bundle.
11. Truncated custom dump: restore failed and destination contained no restored public tables.
12. Two separate processes contending for the same operation lock: second rejected.
13. SIGTERM of an actual backup process paused after plaintext bundle creation: temporary plaintext and pending ciphertext removed, previous immutable set/reference retained.
14. Disabled laboratory configuration and traversal paths: rejected.
15. Metrics field/stage allowlist and absent status file: no arbitrary data exported.
16. Post-restore comparison failure: verified-restore timestamp not advanced.
17. Incompatible source PostgreSQL major: capture rejected.
18. Actual private HTTP metrics endpoint, 404 path handling and exporter restart with persisted status.
19. Invalid status JSON/type/field values: metrics fail closed.
20. Narrow equivalent CHECK-cast normalization, with changed values/operators still detected.
21. Original duplicate-ID manifest exploit rejected before destination modification.
22. Duplicate object keys and duplicate payload paths rejected.
23. Missing/additional manifest database rows rejected by an independent inspection restore.
24. Absolute, traversal and noncanonical manifest paths rejected.
25. Additional archive payload and metadata-only/payload confusion rejected.
26. Independent index removed after real restore: fingerprint rejects it.
27. Column type, nullability and default changed after real restore: rejected.
28. Existing alternate schema containing a table/row: restore rejected and original row retained.
29. Existing views, sequences and functions: rejected without destructive cleanup.
30. Corrupted status before backup: immutable prior set/reference preserved and still restorable.
31. Injected status-write failure after encryption: prior reference/set preserved and restorable.
32. Successful publication creates a different set and removes write permissions; previous bytes unchanged.
33. Reduced ciphertext ceiling rejects input before decryption.
34. Real age decryption exceeds a reduced plaintext ceiling: streaming abort and temporary cleanup.
35. Highly compressible gzip tar rejected without expansion.
36. Reduced archive-entry, individual-file and manifest ceilings: rejected.
37. Duplicate tar entries and duplicate JSON fields: rejected.
38. Hidden bytes appended after canonical tar: rejected.
39. Failure followed immediately by success retains the failure timestamp.
40. Injected latest-reference write failure: prior pointer and bytes preserved and restorable.
41. Sparse ciphertext larger than 132 MiB: rejected before starting age.
42. Oversized declared tar member and truncated member payload: rejected and plaintext removed.
43. Exporter supplied only sanitized status/latest JSON, without ciphertext: reports freshness;
    restoration still rejects the absent payload and metrics contain no set path.

The launcher cleanup suite was separately executed: **3 tests, 0 failures** (success,
cleanup failure, simultaneous primary/cleanup failure). Docker failures in this suite are
command doubles. Status/reference-write failures and zero free-space tests are injected,
not physical filesystem exhaustion. Decryption, malformed archives, database restoration,
SQL catalog mutations, refused connection, lock contention and SIGTERM use real local
processes/containers. No power-loss, SIGKILL recovery or physical erasure guarantee is claimed.

The measured 8.336 seconds cover decryption, independent inspection restore, destination
restore, catalog/data/document verification and status publication. Image build, initial
Liquibase setup, key provisioning and full service reopening are excluded. The fixture has
one tiny synthetic document; this measurement cannot establish the proposed 8-hour RTO.

Constraint deparsing initially exposed PostgreSQL's equivalent array-cast rendering after
restore. The final implementation normalizes only that literal varchar-to-text-array case;
it does not suppress constraint comparison. Earlier failed iterations are not counted as
successful verification.

## Alert verification

Run from the repository root:

```powershell
docker run --rm --network none -v "${PWD}/monitoring/prometheus:/rules:ro" `
  --entrypoint /bin/promtool prom/prometheus:v3.5.0 test rules `
  /rules/alerts.test.yml /rules/backup-alerts.test.yml
```

Both suites returned SUCCESS. Backup tests cover absent/zero complete-backup timestamp,
failure followed by success before the first scrape (no later failure traffic), persisted failure signal through restart and expiry after five minutes, warning after 26 hours, critical after 48 hours and recovery
after a fresh timestamp, missing/old restore verification and recovery, and exporter
unavailability/absent series followed by recovery.

The private exporter was exercised through real HTTP inside the synthetic runner, twice,
with process restart. The optional combined Prometheus configuration and Grafana dashboard
were prepared and checked structurally; **a live Grafana dashboard and the optional normal
API monitoring stack were not started for this verification**. Promtool fixtures prove rule
evaluation, not external notification delivery or an enabled daily backup schedule.

## UNKNOWN outcome and remaining boundaries

The new Java characterization test invokes the actual registered cleanup callback with
mocked storage. It confirms that UNKNOWN currently causes deletion; it also verifies that
COMMITTED does not and ROLLED_BACK does. It does not reproduce a real JDBC network-induced
ambiguous commit. No clinical service behavior was changed. The proposed minimal fix and
separate-ticket recommendation are in `BACKUP-RECOVERY.md`.

The document adapter is a local synthetic object store matching backend keys, not a real
S3/R2 integration. The lab uses small fixtures and bounded in-memory payloads. Format 2 rejects older phase-1 sets. Completed sets are immutable to the operator but not to Docker/root administrators; latest-reference atomicity depends on filesystem guarantees. Failure after rename but before directory fsync has an ambiguous durability outcome. There is no historical pruning or automatic orphan selection. A metadata-only exporter cannot independently verify ciphertext availability; periodic restore verification and storage supervision remain necessary. Source data has no
independent historic hash; an already-corrupt same-size original cannot be diagnosed by a
newly calculated hash alone. PostgreSQL/filesystem publication is not a distributed
transaction. The lab restores without original owners/ACLs and runs no application workers.

Age authenticates encrypted content but does not identify its producer. An authenticated
catalog/signature, institutional key custody, historical retention, independent storage,
scheduling, writer coordination, role/grant validation and provider plan checks remain
required before real operation. RPO 24 hours / RTO 8 hours are proposals, not achieved SLAs.

PR #161 was checked again during corrections on 2026-10-09: OPEN, head
`0773e9935cb6c0dcd155f32441bce2d33318847b`. Its shared configuration, migrations and business
files were not changed. Its future integration requires an updated laboratory/schema digest
and explicit handling of restored conversation credentials and notification outbox.

**Verdict: APROBABLE for the authorized synthetic phase 1. Not authorized or certified for
real clinical backup/restore.**
