# Backup and recovery — issue #150, phase 1

## Scope and safety boundary

This implementation is a **synthetic laboratory**, not an enabled backup of Supabase or
the clinical R2 bucket. It accepts only PostgreSQL 17, the fixed Docker hostnames
`pgsource` / `pgtarget`, database `dentalcare_synthetic`, user `synthetic`, the explicit
`DENTALCARE_SYNTHETIC_LAB=1` switch, and a synthetic database marker. It rejects libpq
service overrides. The standalone Compose network is internal and publishes no ports.
There is no application container, R2 connection, SMTP, scheduler or notification worker.
Never combine the laboratory Compose file with the application's Compose or `.env`.
These guards are accidental-use protection, not a security boundary against a Docker
administrator changing source code, DNS, mounts or container environment.

No existing migrations or clinical services are modified. Liquibase creates the source
schema using the repository's complete changelog. The destination is a different empty
PostgreSQL instance. Both instances and their storage are disposable.

## Components and execution

From the repository root, with PowerShell 7 and Docker Compose:

```powershell
./operations/backup/run-lab.ps1
```

The launcher generates a disposable password in the operating system's temporary
directory, uses a unique Compose project, builds the operator, starts two databases,
runs Liquibase only against the source, and invokes the verification suite. Its `finally`
block checks removal of containers, networks and volumes and deletes the temporary environment
file. A failed cleanup raises an error and identifies the exact laboratory project; if the
primary operation also failed, its original error is preserved alongside the cleanup warning. The operator emits only fixed event codes, stage names, aggregate counts and
durations. PostgreSQL/age diagnostics and assertion payloads are captured, not printed.
Image builds need access to container/package registries; database operations take place
on the internal network. No provider API is invoked.

If the shell/daemon is forcibly terminated, cleanup is not guaranteed. Inspect Compose
projects with prefix `dc-backup-`, confirm the exact laboratory project, then run its
`down --volumes --remove-orphans`. Never use broad Docker prune commands. Check for the
matching temporary environment file and delete it. Do not retain the password.

Inside the laboratory operator, the explicit commands are:

```text
python3 /opt/backup/backup.py backup <age-public-recipient> /work/keys/identity
python3 /opt/backup/backup.py restore /work/keys/identity
python3 /opt/backup/backup.py verify
```

The launcher uses `verify`: it generates two independent age identities on `/work`
tmpfs, seeds synthetic data and a PDF-shaped byte fixture, and executes positive and
negative scenarios. No key, plaintext archive, dump or document is written into the
repository. `./operations/backup/test-cleanup.ps1` separately tests launcher success,
cleanup failure and simultaneous primary/cleanup failure without invoking Docker. Public recipients are encryption keys; private identities remain separate
from the encrypted state volume. Python, PostgreSQL client tools and age are packaged
in the operator image; no backend dependency is added.

## Backup format and consistency

An exclusive filesystem lock serializes backup and restore on the shared state volume.
The PostgreSQL server and client major versions must be 17. The source marker and
configuration must pass before a dump is created. `pg_dump -Fc` includes application
data, schema, sequences and `databasechangelog` / `databasechangeloglock`.

The operator inventories `clinical_documents`. A null object key means metadata only;
it is not reported as a missing object. Non-null keys must have the backend's
`patients/<id>/documents/<file>` structure, remain inside the synthetic store and avoid
symlink traversal. Each referenced file must exist and match the database size. SHA-256
is calculated over its bytes and checked after recovery.

The source and target use a filesystem adapter with the same object-key layout as R2.
**This is not an S3 protocol integration test and does not validate Cloudflare credentials,
provider consistency, lifecycle, Bucket Lock or account recovery.** No assumption of R2
version history or undelete is made.

The controlled laboratory has no concurrent application writers. Canonical table hashes,
constraint definitions and sequence values are compared around capture and after restore.
These comparisons detect changed data but do not replace write coordination in a real
multi-writer system. A future real procedure must pause every writer and drain in-flight
uploads/jobs for a coordinated PostgreSQL–R2 cut. A PostgreSQL snapshot alone cannot
provide an atomic R2 backup.

The encrypted tar bundle contains `database.dump`, numbered object payloads and
`manifest.json`. Format **2** requires a canonical, uncompressed tar with fixed producer
metadata. Format 1 is rejected; phase-1 synthetic sets are regenerated, not migrated.
The manifest includes PostgreSQL major, the repository changelog digest, canonical row
hashes, complete supported catalog definitions, sequence values, document references,
file sizes/hashes and the dump hash. It is encrypted and never logged.

Document IDs, object keys and payload paths must be unique. Paths must be canonical,
relative and confined to the supported layout; duplicate JSON fields, archive members,
extra/missing members, hidden trailing bytes and unexpected document fields are rejected.
Metadata-only documents retain their database size even without an object key. They must
not carry a payload reference. Size/hash checking does not establish historical validity:
a same-size source change before capture cannot be diagnosed without an independently
trusted earlier content hash.

Catalog comparison includes user schemas, relation kinds/options, column types/nullability/
defaults/identity, constraint definitions and validation state, index definitions and
validity, sequences and their settings, views, supported user functions, domains/enums,
triggers, policies, extension names/versions and large-object content hashes. All application
rows, including Liquibase history/checksums, are hashed. Physical OIDs/storage layout and
original owner/ACL restoration are deliberately excluded. This is validation of the current
project schema, not certification of every PostgreSQL extension or server-wide object.
PostgreSQL 17's equivalent literal varchar-array casts are narrowly normalized; values,
operators and other casts remain checked.

Before touching the destination database, the dump is restored in a uniquely named
`dentalcare_inspect_<random>` database on the isolated target PostgreSQL instance. Its
inventory and fingerprint must match the already structurally validated manifest. The
inspection database is dropped in `finally`; cleanup errors fail the operation. This
uses the disposable lab superuser and is not an institutional permission model. It never
writes to `dentalcare_synthetic` until preflight is complete. A matching public recipient
is not proof that an input dump is trusted; see authenticity below.

## Publication and resource bounds

Every completed set has a unique `sets/<random>/backup.age` directory. The operator never
overwrites an earlier set. Ciphertext is first written in a private `.pending-*` directory;
plaintext stays in a private `/work/backup-*` tmpfs directory. The lab requires both the
public recipient and a separately stored verification identity: the recipient must match,
and a bounded decryption round trip must verify the encrypted contents before publication.
This private-key access is a synthetic verification step, not a design for unattended
institutional custody.

Files and directories are flushed, then the finished directory is renamed into `sets`.
Completed files/directories lose write permission (0400/0500). This is operator-level
write-once behavior, **not WORM protection against root/Docker administrators**. Attempt
status is written atomically before the final `latest.json` reference is updated. Only
that reference supplies the complete-backup timestamp and default recovery location.
A corrupt status is rejected before capture. A failed status/reference write before the
atomic reference replacement preserves the previous reference and set. A fully verified
but unreferenced set may remain after a publication failure; it is not selected as latest.
Historical pruning and automatic orphan collection are not implemented.

File/directory fsync and atomic rename provide their filesystem's guarantees. An error or
power loss after a reference replacement but before directory persistence has an ambiguous
durability outcome; software cannot promise preservation on failing media. On restart,
validate the referenced ciphertext/hash; do not infer completeness from a `.pending-*`
directory or select an unreferenced set by its name. A lost/damaged reference requires an
explicit operator review of retained sets; no automatic fallback manufactures freshness.

Resource ceilings are enforced before and during processing:

| Resource | Ceiling |
|---|---|
| Ciphertext | 132 MiB, checked by stat and during feeding to age |
| Decrypted archive | 128 MiB, streamed to controlled tmpfs with a deadline |
| Individual dump/object | 64 MiB |
| Manifest | 4 MiB |
| Archive entries | 256, including dump and manifest |
| Accumulated entry payload | 128 MiB |

Compressed archives, links, special files and noncanonical tar encodings are unsupported
and rejected; compressed tar is never expanded. Tar entry headers are checked before
payload reads. Payloads remain bounded in memory; this is not a production-scale streaming
backup implementation. Available tmpfs space can impose a stricter limit than these
ceilings. The 256 MiB tmpfs quota is a separate hard storage boundary.

SIGTERM after plaintext archive creation runs cleanup and preserves the prior latest set.
SIGKILL, daemon failure or power loss cannot execute Python/PowerShell cleanup. Private
plaintext tmpfs may remain until container/mount removal; recovered synthetic documents
and database state are on disposable volumes until confirmed `down --volumes` succeeds.
Inspect and remove only the failed laboratory project. Do not claim secure physical-block
erasure or treat a cleanup warning as a completed verification.

## Encryption, authenticity and custody

Age provides established authenticated encryption. Wrong identities and modified
ciphertext fail before PostgreSQL restore. Manifest hashes detect inconsistent contents
inside the decrypted bundle. Encryption and integrity do **not** establish producer
identity: anyone possessing a public recipient can construct a new encrypted bundle.
The negative tests deliberately exercise that distinction by re-encrypting altered
manifests. Institutional use needs a separately authenticated catalog or signed manifest,
trusted verification keys and protected publication permissions before accepting inputs.
Do not restore untrusted PostgreSQL dumps: they can contain executable SQL.

For a future real environment, use institutional storage, a source read-only database
role, source R2 read-only credentials, distinct destination credentials and separately
authorized restore/deletion roles. Keep decryption identities outside backup storage,
with controlled emergency custody and recovery tests. Supply secrets through protected
files/mounts or a secure secret system; environment variables may identify their paths.
Never place secrets in command arguments, logs, Git, personal cloud accounts or CI
artifacts. Encryption does not solve compromise while plaintext is being captured.

Proposed retention, pending approval: 7 daily, 4 weekly and 3 monthly copies, across
independent failure/administrative domains with an offline or protected institutional
copy. This is separate from clinical-record retention. Key rotation must preserve the
ability to decrypt retained copies. Deletion and lock expiry need an approved policy;
secure erasure of SSD/cloud plaintext cannot be promised merely by deleting a filename.

## Restore and acceptance gates

1. Authenticate the source of the set independently and obtain its recovery identity.
2. Require an empty target across **all user schemas**. Only `pg_catalog`, `pg_toast`,
   `information_schema` and PostgreSQL-generated `pg_temp_<number>` /
   `pg_toast_temp_<number>` namespaces are internal. The default `public` schema may be
   present but must be empty; any extra user schema, relation, function/type/operator,
   sequence, view, large object or default ACL rejects recovery. Only the built-in
   plpgsql extension in pg_catalog is allowed in an otherwise empty target.
3. Bound decryption, reject malformed manifests and check hashes/format/compatibility.
4. Restore in a separate inspection database and compare catalog, data and exact document
   inventory. Missing/additional manifest rows fail before modifying the destination.
5. Recheck destination emptiness, then restore with `pg_restore --single-transaction
   --exit-on-error --no-owner --no-privileges`.
6. Compare the complete supported fingerprint and inventory again, including independent
   indexes, column properties, sequence state and Liquibase checksums.
7. Recover objects into private staging, verify hashes and publish the object directory.
8. Update last-verified-restore only after all gates pass; measure decryption, preflight,
   destination recovery, object validation and status publication time.

There is no destructive force/clean option. Existing alternative-schema data is rejected
and retained. `--force` is used only to drop the uniquely named inspection database, never
on the destination. A SQL restore failure rolls back its transaction. A subsequent catalog,
object-stage or status failure leaves an **unverified disposable destination**; discard the
laboratory and create a fresh empty destination, without reopening it. PostgreSQL and
filesystem publication remain separate, not a distributed transaction. A background
administrator can still race emptiness checks; the lab assumes one controlled writer.
Original owners/ACLs are intentionally not recovered; real restoration requires explicit
role, grant, extension and provider-schema validation.

The lab never starts Spring Boot on restored data; consequently no automatic migration,
email, OTP, outbox worker or scheduled business operation can run. A future application
verification must first use matching code and disable Liquibase until history/checksums
are verified, then explicitly approve any subsequent migrations. Do not clear checksums
or locks blindly. Incident recovery must rotate compromised credentials and revoke
sessions/recovery/conversation credentials before reopening access. Pending notifications
must not replay automatically.

## Monitoring integration

The existing #149 stack remains unchanged unless the optional override is selected:

```powershell
# Set BACKUP_STATUS_DIRECTORY to an absolute directory containing ONLY sanitized status.json and latest.json metadata.
docker compose -f docker-compose.yml -f operations/backup/compose.monitoring.yml `
  --profile monitoring --profile backup-monitoring up -d --build
```

This command starts the normal API stack and therefore needs its normal authorized
configuration. It is **not** part of the synthetic laboratory launcher. Do not run it
against real providers for this phase. The private exporter has no published port and
receives only a read-only metadata directory, no database/object credentials or keys.
Prometheus scrapes it over the Docker network. Existing Actuator isolation and Grafana
authentication/loopback exposure are preserved. Grafana provisions an additional backup
dashboard using the existing Prometheus datasource.

Metrics expose last-complete, last-attempt, last-result, durations, last-verified-restore,
last-failure and cumulative failures by a fixed allowlist of stages. No object/patient/run ID is a label. Status
survives an exporter restart if its directory persists. Concurrent lock rejection cannot
safely rewrite the status owned by another writer and is returned as a fixed error stage.
The exporter whitelists numeric fields; it never exports arbitrary status content.
The latest reference is the authoritative complete-backup marker, independent of attempt
status. Persist both sanitized JSON files in the exporter directory; never mount sets,
objects or keys into that service. A missing/corrupt reference exposes zero freshness.

Six optional alerts cover: missing complete backup (5 minutes), failed operation,
age over 26 hours (warning), age over 48 hours (critical), restore verification older than
30 days or absent (5 minutes), and unavailable/absent exporter (1 minute). A failure timestamp is persisted on each observed failure and survives immediate success
and exporter restart. The failure alert stays observable for five minutes even if the failure
and success both preceded the first scrape; an unsuccessful latest operation also keeps it
firing. Recovery follows successful operation plus expiry of that window. A backup success does not fabricate a successful
restore timestamp. Prometheus/Grafana retention from #149 is not backup retention.

```powershell
docker run --rm --network none -v "${PWD}/monitoring/prometheus:/rules:ro" `
  --entrypoint /bin/promtool prom/prometheus:v3.5.0 test rules `
  /rules/alerts.test.yml /rules/backup-alerts.test.yml
```

There is no automatic local scheduler or external notification delivery in phase 1.
An institutional runner, durable status publication and independent supervision remain
required before promising daily backups. An offline workstation cannot alert on itself.

## Targets, provider checks and approvals

Proposed RPO: 24 hours for the **paired** database/documents set. Proposed RTO: 8 hours
for complete service recovery. Neither is an SLA or demonstrated by a tiny synthetic
fixture. Schedule a monthly restore rehearsal and one after material schema/format changes.
Approve accountable operators, key custodians, incident decision makers and retention
owners before enabling real operations.

Supabase plan, PostgreSQL version, direct/session connection, TLS verification, export
permissions, volume, egress limits, backup history and PITR are unconfirmed. Free-plan
operation must rely on independently managed logical exports; paid backups/PITR are
optional additional protections, not assumed capabilities. They do not back up R2.
Check R2 storage class, operation quotas, account permissions, lifecycle and Bucket Lock;
none is assumed enabled. Protecting a second bucket in the same account does not eliminate
account-compromise risk. No external resource or paid service is created by this phase.

## PR #161 and transactional UNKNOWN finding

At implementation start, #161 remains open at `0773e9935cb6c0dcd155f32441bce2d33318847b`.
This phase avoids its shared `.env.example`, `application.yml`, security documentation
and changelog. After integration, regenerate and test against the new changelog digest;
old bundles are intentionally rejected by this laboratory if schema source differs.
Conversations, tokens, messages and encrypted notification outbox must be included in
future inventory. Outbox decryption-key custody and suppression of restored deliveries
need explicit policy. Nothing in #161 is overwritten or merged here.

`ClinicalDocumentServiceImpl.registerRollbackCleanup` currently deletes the uploaded
object for every completion other than `STATUS_COMMITTED`, including `STATUS_UNKNOWN`.
Reproduction: initialize Spring transaction synchronization, upload a synthetic document
using the existing mocked storage/repository test fixture, obtain the registered callback,
then invoke `afterCompletion(STATUS_UNKNOWN)`. The existing condition calls storage
`delete(key)` even though the callback cannot prove that PostgreSQL rolled back. A network
failure after server commit is one possible ambiguous outcome; callback simulation alone
does not prove a particular driver produces it.

Minimal proposed correction: delete only on `STATUS_ROLLED_BACK`; for `UNKNOWN`, preserve
the object, emit a safe fixed incident signal and defer any cleanup until authoritative
database reconciliation proves it unreferenced. Test committed, rolled-back and unknown
statuses, including a reconciliation race. Do not introduce artificial transactions.
Preserving an uncertain object trades a potential orphan for avoiding irreversible loss.
This behavior is **not changed or claimed fixed**. Prefer a separate focused ticket;
it can join #150 only with additional explicit authorization. The phase-1 lab avoids it
because there is no clinical service execution during capture or restoration.
