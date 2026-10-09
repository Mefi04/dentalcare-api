"""Synthetic-only operator. No production connection or S3 credentials are accepted."""
import contextlib
import fcntl
import hashlib
import http.server
import io
import json
import math
import os
import re
from pathlib import Path, PurePosixPath
import shutil
import signal
import subprocess
import sys
import tarfile
import tempfile
import time
import uuid

ROOT = Path('/lab')
SCHEMA = Path('/schema')
MAX_CIPHER = 132 * 1024 * 1024
MAX_PLAIN = 128 * 1024 * 1024
MAX_FILE = 64 * 1024 * 1024
MAX_MANIFEST = 4 * 1024 * 1024
MAX_ENTRIES = 256
# Only PostgreSQL's built-in namespaces and generated temporary namespaces are internal.
INTERNAL_SCHEMAS = ('pg_catalog', 'pg_toast', 'information_schema')
USER_NAMESPACE = "n.nspname NOT IN ('pg_catalog','pg_toast','information_schema') AND n.nspname !~ '^pg_(toast_)?temp_[0-9]+$'"
STAGES = ('configuration', 'connection', 'inventory', 'dump', 'objects', 'encryption',
          'integrity', 'restore', 'validation', 'concurrency', 'interrupted', 'space')


class Failure(Exception):
    def __init__(self, stage):
        self.stage = stage if stage in STAGES else 'validation'


def require(condition, stage):
    if not condition:
        raise Failure(stage)


def run(args, stage, data=None, env=None):
    try:
        result = subprocess.run(args, input=data, stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE, env=env, timeout=120)
    except (OSError, subprocess.TimeoutExpired):
        raise Failure(stage) from None
    require(result.returncode == 0, stage)
    return result.stdout


def sql(host, query, database='dentalcare_synthetic'):
    require(host in ('pgsource', 'pgtarget'), 'configuration')
    require(database == 'dentalcare_synthetic' or re.fullmatch(r'dentalcare_inspect_[a-f0-9]{32}', database), 'configuration')
    return run(['psql', '-X', '-qAt', '-v', 'ON_ERROR_STOP=1', '-h', host,
                '-d', database, '-c', query], 'connection').decode().strip()


def schema_hash():
    digest = hashlib.sha256()
    for p in sorted(SCHEMA.rglob('*')):
        if p.is_file():
            digest.update(str(p.relative_to(SCHEMA)).encode())
            digest.update(p.read_bytes())
    return digest.hexdigest()


def guard():
    require(os.getenv('DENTALCARE_SYNTHETIC_LAB') == '1', 'configuration')
    require(os.getenv('PGDATABASE') == 'dentalcare_synthetic'
            and os.getenv('PGUSER') == 'synthetic' and os.getenv('PGPASSWORD'), 'configuration')
    # libpq service/options could silently redirect otherwise fixed endpoints.
    require(not any(os.getenv(k) for k in ('PGSERVICE', 'PGSERVICEFILE', 'PGOPTIONS',
                                          'PGHOSTADDR', 'PGPASSFILE')), 'configuration')
    require(os.getenv('PGPORT', '5432') == '5432', 'configuration')
    ROOT.mkdir(exist_ok=True)
    require(schema_hash() != hashlib.sha256().hexdigest(), 'configuration')


@contextlib.contextmanager
def lock():
    with (ROOT / '.lock').open('a') as handle:
        try:
            fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise Failure('concurrency') from None
        try:
            yield
        finally:
            fcntl.flock(handle, fcntl.LOCK_UN)


def atomic_json(path, value):
    # The old file remains intact on failures before replace. Directory fsync is required
    # for durability; errors after replace have an ambiguous outcome on failing media.
    fd, name = tempfile.mkstemp(prefix='.state-', dir=path.parent)
    try:
        with os.fdopen(fd, 'w') as handle:
            json.dump(value, handle)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(name, path)
        sync_directory(path.parent)
    finally:
        Path(name).unlink(missing_ok=True)


def sync_directory(path):
    directory = os.open(path, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(directory)
    finally:
        os.close(directory)


def load_status(strict=False):
    try:
        data = json.loads((ROOT / 'status.json').read_text())
        require(isinstance(data, dict) and isinstance(data.get('failures', {}), dict), 'configuration')
        require(all(type(v) is int and v >= 0 for v in data.get('failures', {}).values()), 'configuration')
        return data
    except (OSError, ValueError, Failure):
        require(not strict or not (ROOT / 'status.json').exists(), 'configuration')
        return {'failures': {}}


def status_update(**values):
    old = load_status()
    old.update(values)
    atomic_json(ROOT / 'status.json', old)


def latest(require_payload=True):
    try:
        data = json.loads((ROOT / 'latest.json').read_text())
        require(isinstance(data, dict) and re.fullmatch(r'sets/[a-f0-9]{32}/backup.age', data['file']), 'integrity')
        require(isinstance(data['completed_at'], (int, float)) and math.isfinite(data['completed_at']), 'integrity')
        require(re.fullmatch(r'[a-f0-9]{64}', data['sha256']) is not None, 'integrity')
        if require_payload:
            require((ROOT / data['file']).is_file(), 'integrity')
        return data
    except (OSError, ValueError, KeyError, TypeError):
        raise Failure('integrity') from None


def latest_bundle():
    info = latest()
    path = ROOT / info['file']
    require(file_sha(path) == info['sha256'], 'integrity')
    return path


def failed(stage, duration):
    old = load_status()
    failures = old.get('failures', {})
    failures[stage] = failures.get(stage, 0) + 1
    status_update(last_attempt=time.time(), last_failure=time.time(), last_result=0, duration=duration, failures=failures)


def catalog(host, database='dentalcare_synthetic'):
    # Compare definitions, not counts or physical OIDs/owners. pg_dump --no-owner/--no-acl
    # intentionally maps ownership to the lab role; institutional ACL restore is out of scope.
    queries = {
        'schemas': f'SELECT n.nspname FROM pg_namespace n WHERE {USER_NAMESPACE}',
        'relations': f"SELECT n.nspname, c.relname, c.relkind, c.relpersistence, c.reloptions, pg_get_partkeydef(c.oid) partition_key FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE {USER_NAMESPACE} AND c.relkind IN ('r','p','v','m','S','f')",
        'columns': f"SELECT n.nspname,c.relname,a.attname,a.attnum,format_type(a.atttypid,a.atttypmod) type,a.attnotnull,a.attidentity,a.attgenerated,pg_get_expr(d.adbin,d.adrelid) default_value,co.collname FROM pg_attribute a JOIN pg_class c ON c.oid=a.attrelid JOIN pg_namespace n ON n.oid=c.relnamespace LEFT JOIN pg_attrdef d ON d.adrelid=c.oid AND d.adnum=a.attnum LEFT JOIN pg_collation co ON co.oid=a.attcollation WHERE {USER_NAMESPACE} AND c.relkind IN ('r','p','v','m','f') AND a.attnum>0 AND NOT a.attisdropped",
        'indexes': f'SELECT n.nspname,c.relname,i.indisunique,i.indisprimary,i.indisexclusion,i.indisvalid,i.indisready,pg_get_indexdef(i.indexrelid) definition FROM pg_index i JOIN pg_class c ON c.oid=i.indexrelid JOIN pg_namespace n ON n.oid=c.relnamespace WHERE {USER_NAMESPACE}',
        'views': f"SELECT n.nspname,c.relname,pg_get_viewdef(c.oid,false) definition FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE {USER_NAMESPACE} AND c.relkind IN ('v','m')",
        'functions': f"SELECT n.nspname,p.proname,pg_get_function_identity_arguments(p.oid) arguments,pg_get_functiondef(p.oid) definition FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE {USER_NAMESPACE} AND p.prokind IN ('f','p') AND NOT EXISTS(SELECT 1 FROM pg_depend dep WHERE dep.classid='pg_proc'::regclass AND dep.objid=p.oid AND dep.deptype='e')",
        'types': f"SELECT n.nspname,t.typname,t.typtype,format_type(t.typbasetype,t.typtypmod) base,t.typnotnull,t.typdefault FROM pg_type t JOIN pg_namespace n ON n.oid=t.typnamespace WHERE {USER_NAMESPACE} AND t.typtype IN ('d','e')",
        'enums': f'SELECT n.nspname,t.typname,e.enumlabel,e.enumsortorder FROM pg_enum e JOIN pg_type t ON t.oid=e.enumtypid JOIN pg_namespace n ON n.oid=t.typnamespace WHERE {USER_NAMESPACE}',
        'triggers': f'SELECT n.nspname,c.relname,t.tgname,pg_get_triggerdef(t.oid,false) definition FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid JOIN pg_namespace n ON n.oid=c.relnamespace WHERE {USER_NAMESPACE} AND NOT t.tgisinternal',
        'policies': f'SELECT n.nspname,c.relname,p.polname,p.polcmd,p.polpermissive,pg_get_expr(p.polqual,p.polrelid) qual,pg_get_expr(p.polwithcheck,p.polrelid) with_check FROM pg_policy p JOIN pg_class c ON c.oid=p.polrelid JOIN pg_namespace n ON n.oid=c.relnamespace WHERE {USER_NAMESPACE}',
        'extensions': 'SELECT e.extname,e.extversion,n.nspname FROM pg_extension e JOIN pg_namespace n ON n.oid=e.extnamespace',
        'sequence_definitions': f'SELECT n.nspname,c.relname,format_type(s.seqtypid,NULL) type,s.seqstart,s.seqincrement,s.seqmax,s.seqmin,s.seqcache,s.seqcycle FROM pg_sequence s JOIN pg_class c ON c.oid=s.seqrelid JOIN pg_namespace n ON n.oid=c.relnamespace WHERE {USER_NAMESPACE}',
        'large_objects': "SELECT m.oid,coalesce((SELECT md5(string_agg(encode(l.data,'hex'),'' ORDER BY l.pageno)) FROM pg_largeobject l WHERE l.loid=m.oid),'') content_hash FROM pg_largeobject_metadata m",
    }
    result = {}
    for label, query in queries.items():
        entries = json.loads(sql(host, f"SELECT coalesce(jsonb_agg(x ORDER BY x::text),'[]') FROM ({query}) x", database))
        for entry in entries:
            if isinstance(entry.get('definition'), str):
                entry['definition'] = normalize_constraint(entry['definition'])
        result[label] = sorted(entries, key=lambda entry: json.dumps(entry, sort_keys=True))
    return result


def quoted(name):
    require(isinstance(name, str) and re.fullmatch(r'[a-z_][a-z0-9_]*', name), 'validation')
    return '"' + name + '"'


def fingerprint(host, database='dentalcare_synthetic'):
    structure = catalog(host, database)
    rows = {}
    for relation in structure['relations']:
        if relation['relkind'] not in ('r', 'p', 'm'):
            continue
        table = quoted(relation['nspname']) + '.' + quoted(relation['relname'])
        rows[table] = sha(sql(host, f'SELECT coalesce(jsonb_agg(r ORDER BY r::text), \'[]\'::jsonb) FROM (SELECT to_jsonb(t) r FROM {table} t) s', database).encode())
    constraints = json.loads(sql(host, f"SELECT coalesce(jsonb_agg(x ORDER BY x::text), '[]') FROM (SELECT n.nspname,conrelid::regclass::text t,conname,pg_get_constraintdef(c.oid) d,convalidated FROM pg_constraint c JOIN pg_namespace n ON n.oid=c.connamespace WHERE {USER_NAMESPACE}) x", database))
    for constraint in constraints:
        constraint['d'] = normalize_constraint(constraint['d'])
    constraints = json.dumps(sorted(constraints, key=lambda c: (c['t'], c['conname'])), sort_keys=True)
    sequences = {}
    for definition in structure['sequence_definitions']:
        seq = quoted(definition['nspname']) + '.' + quoted(definition['relname'])
        sequences[seq] = sql(host, f'SELECT last_value,is_called FROM {seq}', database)
    return {'rows': rows, 'catalog': structure, 'constraints': constraints, 'sequences': sequences}


def empty_target():
    structure = catalog('pgtarget')
    require(structure['schemas'] == [{'nspname': 'public'}], 'restore')
    for key, entries in structure.items():
        if key == 'schemas':
            continue
        if key == 'extensions':
            require(all(e['extname'] == 'plpgsql' and e['nspname'] == 'pg_catalog' for e in entries), 'restore')
        else:
            require(not entries, 'restore')
    # Catch user aggregates, composite/base types, operators, rules and extension-free
    # objects not represented above. pg_depend pins built-ins in internal namespaces.
    for table, namespace_column in [('pg_proc','pronamespace'), ('pg_type','typnamespace'),
                                    ('pg_operator','oprnamespace'), ('pg_conversion','connamespace')]:
        count = sql('pgtarget', f'SELECT count(*) FROM {table} o JOIN pg_namespace n ON n.oid=o.{namespace_column} WHERE {USER_NAMESPACE}')
        require(count == '0', 'restore')
    require(sql('pgtarget', 'SELECT count(*) FROM pg_default_acl') == '0', 'restore')


def normalize_constraint(definition):
    # PostgreSQL 17 reparses a varchar literal-array -> text[] cast as per-element
    # casts during pg_restore. Normalize ONLY that provably equivalent literal form;
    # retain every value, operator and other cast. Never drop arbitrary SQL casts.
    literal = r"'(?:[^']|'')*'::character varying"
    pattern = r'\(ARRAY\[(' + literal + r'(?:, ' + literal + r')*)\]\)::text\[\]'
    return re.sub(pattern, lambda m: 'ARRAY[' + ', '.join(
        '(' + value + ')::text' for value in re.findall(literal, m.group(1))) + ']', definition)


def inventory(host, database='dentalcare_synthetic'):
    raw = sql(host, "SELECT coalesce(jsonb_agg(x ORDER BY id), '[]') FROM (SELECT id, storage_object_key AS key, file_size AS size FROM public.clinical_documents) x", database)
    return json.loads(raw)


def safe_key(key):
    require(isinstance(key, str) and 0 < len(key) <= 500
            and re.fullmatch(r'[A-Za-z0-9._/-]+', key), 'objects')
    p = PurePosixPath(key)
    require(not p.is_absolute() and '..' not in p.parts and '\\' not in key
            and len(p.parts) == 4 and p.parts[0] == 'patients'
            and p.parts[2] == 'documents', 'objects')
    return p


def sha(data):
    return hashlib.sha256(data).hexdigest()


def file_sha(path):
    require(Path(path).stat().st_size <= MAX_CIPHER, 'space')
    digest = hashlib.sha256()
    total = 0
    with Path(path).open('rb') as handle:
        while block := handle.read(65536):
            total += len(block)
            require(total <= MAX_CIPHER, 'space')
            digest.update(block)
    return digest.hexdigest()


def strict_json(data):
    def pairs(values):
        result = {}
        for key, value in values:
            require(key not in result, 'integrity')
            result[key] = value
        return result
    return json.loads(data, object_pairs_hook=pairs)


def validate_documents(documents, files):
    require(isinstance(documents, list) and len(documents) <= MAX_ENTRIES - 2, 'integrity')
    ids, keys, paths = set(), set(), set()
    expected = {'manifest.json', 'database.dump'}
    for doc in documents:
        require(isinstance(doc, dict) and set(doc) >= {'id', 'key', 'size'}, 'integrity')
        require(isinstance(doc['id'], str) and str(uuid.UUID(doc['id'])) == doc['id']
                and doc['id'] not in ids, 'integrity')
        ids.add(doc['id'])
        require(doc['size'] is None or type(doc['size']) is int and 0 <= doc['size'] <= MAX_FILE, 'integrity')
        if doc['key'] is None:
            require(set(doc) == {'id', 'key', 'size'}, 'integrity')
            continue
        require(set(doc) == {'id', 'key', 'size', 'file', 'sha256'}, 'integrity')
        key = doc['key']
        require(isinstance(key, str) and str(safe_key(key)) == key and key not in keys, 'integrity')
        keys.add(key)
        path = doc['file']
        require(isinstance(path, str) and re.fullmatch(r'objects/(0|[1-9][0-9]*)', path)
                and path not in paths, 'integrity')
        paths.add(path)
        require(path in files and type(doc['size']) is int
                and len(files[path]) == doc['size'] and sha(files[path]) == doc['sha256'], 'integrity')
        expected.add(path)
    require(set(files) == expected, 'integrity')


def decrypt_file(identity, bundle, output):
    bundle = Path(bundle)
    require(bundle.is_file() and not bundle.is_symlink()
            and 0 < bundle.stat().st_size <= MAX_CIPHER, 'space')
    # Feed bounded ciphertext and consume bounded plaintext concurrently. Diagnostics go
    # to DEVNULL; neither a pipe buffer nor stderr can grow without limit.
    import threading
    import selectors
    process = subprocess.Popen(['age', '-d', '-i', str(identity)], stdin=subprocess.PIPE,
                               stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
    stopped = threading.Event()
    feed_error = []
    def feed():
        try:
            count = 0
            with bundle.open('rb') as source:
                while not stopped.is_set() and (block := source.read(65536)):
                    count += len(block)
                    if count > MAX_CIPHER:
                        feed_error.append(True)
                        break
                    process.stdin.write(block)
            process.stdin.close()
        except (OSError, ValueError):
            if process.poll() is None:
                feed_error.append(True)
    feeder = threading.Thread(target=feed, daemon=True)
    feeder.start()
    try:
        total = 0
        deadline = time.monotonic() + 120
        with output.open('xb') as destination, selectors.DefaultSelector() as selector:
            selector.register(process.stdout, selectors.EVENT_READ)
            while True:
                require(time.monotonic() < deadline, 'integrity')
                if not selector.select(0.2):
                    continue
                block = os.read(process.stdout.fileno(), 65536)
                if not block:
                    break
                total += len(block)
                require(total <= MAX_PLAIN, 'space')
                destination.write(block)
        require(process.wait(timeout=5) == 0 and not feed_error, 'integrity')
    finally:
        stopped.set()
        if process.poll() is None:
            process.kill()
        process.wait()
        feeder.join(timeout=5)
        process.stdout.close()


def read_bundle(identity, bundle):
    try:
        with tempfile.TemporaryDirectory(prefix='decrypt-', dir='/work') as directory:
            plain = Path(directory) / 'bundle.tar'
            decrypt_file(identity, bundle, plain)
            files = {}
            total = 0
            # r| accepts ONLY uncompressed tar; gzip/bzip/xz expansion is not supported.
            with plain.open('rb') as stream, tarfile.open(fileobj=stream, mode='r|') as tar:
                for member in tar:
                    require(len(files) < MAX_ENTRIES and member.isfile()
                            and member.name not in files, 'integrity')
                    require(member.name in ('manifest.json', 'database.dump')
                            or re.fullmatch(r'objects/(0|[1-9][0-9]*)', member.name), 'integrity')
                    limit = MAX_MANIFEST if member.name == 'manifest.json' else MAX_FILE
                    require(0 <= member.size <= limit, 'space')
                    total += member.size
                    require(total <= MAX_PLAIN, 'space')
                    handle = tar.extractfile(member)
                    payload = bytearray()
                    while block := handle.read(min(65536, member.size + 1 - len(payload))):
                        payload.extend(block)
                        require(len(payload) <= member.size, 'integrity')
                    require(len(payload) == member.size, 'integrity')
                    files[member.name] = bytes(payload)
            manifest = strict_json(files['manifest.json'])
            require(isinstance(manifest, dict) and set(manifest) == {
                'format', 'postgres_major', 'schema', 'fingerprint', 'documents', 'dump_sha256'}, 'integrity')
            require(manifest['format'] == 2 and manifest['postgres_major'] == 17
                    and manifest['schema'] == schema_hash(), 'configuration')
            require(sha(files['database.dump']) == manifest['dump_sha256'], 'integrity')
            validate_documents(manifest['documents'], files)
            # Reject appended hidden members/garbage. All producer tar metadata is fixed.
            with plain.open('rb') as original:
                class ComparisonWriter:
                    def tell(self):
                        return original.tell()

                    def write(self, chunk):
                        require(chunk == original.read(len(chunk)), 'integrity')
                        return len(chunk)
                write_tar(ComparisonWriter(), files)
                require(original.read(1) == b'', 'integrity')
            return manifest, files
    except (OSError, KeyError, ValueError, tarfile.TarError, TypeError, OverflowError):
        raise Failure('integrity') from None


def write_tar(output, files):
    with tarfile.open(fileobj=output, mode='w') as tar:
        for name, data in files.items():
            info = tarfile.TarInfo(name)
            info.size = len(data)
            info.mode = 0o600
            tar.addfile(info, io.BytesIO(data))


def compare_inventory(manifest, actual):
    # Strict uniqueness was checked before any conversion; compare complete ordered rows.
    expected = sorted((d['id'], d['key'], d['size']) for d in manifest['documents'])
    observed = sorted((d['id'], d['key'], d['size']) for d in actual)
    require(len(observed) == len(expected) and observed == expected, 'validation')


def restore_dump(dump, database):
    run(['pg_restore', '-h', 'pgtarget', '-d', database, '--exit-on-error',
         '--single-transaction', '--no-owner', '--no-privileges'], 'restore', dump)


def inspect_dump(manifest, dump):
    # Validation DB is separate from dentalcare_synthetic, with no application attached.
    name = 'dentalcare_inspect_' + uuid.uuid4().hex
    run(['createdb', '-h', 'pgtarget', '--template=template0', name], 'validation')
    try:
        restore_dump(dump, name)
        require(fingerprint('pgtarget', name) == manifest['fingerprint'], 'validation')
        compare_inventory(manifest, inventory('pgtarget', name))
    finally:
        run(['dropdb', '-h', 'pgtarget', '--force', name], 'validation')


def backup(recipient, identity):
    guard()
    started = time.monotonic()
    pending = None
    with lock():
        try:
            load_status(strict=True)
            require(run(['age-keygen', '-y', str(identity)], 'configuration').decode().strip() == recipient, 'configuration')
            require(sql('pgsource', "SELECT current_setting('server_version_num')::int / 10000") == '17', 'configuration')
            require(run(['pg_dump', '--version'], 'configuration').decode().split()[2].startswith('17.'), 'configuration')
            require(sql('pgsource', "SELECT description FROM pg_shdescription WHERE objoid=(SELECT oid FROM pg_database WHERE datname=current_database())") == 'DENTALCARE_SYNTHETIC_ONLY', 'configuration')
            before = fingerprint('pgsource')
            docs = inventory('pgsource')
            require(len(docs) <= MAX_ENTRIES - 2, 'space')
            required_bytes = sum(x['size'] or 0 for x in docs) * 4 + int(sql('pgsource', 'SELECT pg_database_size(current_database())')) * 4
            require(shutil.disk_usage('/work').free > required_bytes + 16 * 1024 * 1024, 'space')
            with tempfile.TemporaryDirectory(prefix='backup-', dir='/work') as directory:
                work = Path(directory)
                dump = run(['pg_dump', '-h', 'pgsource', '-Fc', '--no-owner', '--no-privileges'], 'dump')
                require(len(dump) <= MAX_FILE, 'space')
                run(['pg_restore', '--list'], 'dump', dump)
                files = {'database.dump': dump}
                entries = []
                for doc in docs:
                    if doc['key'] is None:
                        entries.append(doc)
                        continue
                    key = safe_key(doc['key'])
                    source = ROOT / 'source-objects' / key
                    require(source.is_file() and not source.is_symlink()
                            and source.resolve().is_relative_to((ROOT / 'source-objects').resolve())
                            and not any(p.is_symlink() for p in source.parents), 'objects')
                    require(source.stat().st_size == doc['size'] and 0 <= doc['size'] <= MAX_FILE, 'objects')
                    with source.open('rb') as handle:
                        data = handle.read(MAX_FILE + 1)
                    require(len(data) == doc['size'], 'objects')
                    name = 'objects/' + str(len(entries))
                    files[name] = data
                    entries.append({**doc, 'file': name, 'sha256': sha(data)})
                require(fingerprint('pgsource') == before, 'inventory')
                manifest = {'format': 2, 'postgres_major': 17, 'schema': schema_hash(),
                            'fingerprint': before, 'documents': entries, 'dump_sha256': sha(dump)}
                files['manifest.json'] = json.dumps(manifest, sort_keys=True).encode()
                validate_documents(entries, files)
                require(len(files['manifest.json']) <= MAX_MANIFEST and sum(map(len, files.values())) < MAX_PLAIN - 1024 * 1024, 'space')
                inspect_dump(manifest, dump)
                archive = work / 'bundle.tar'
                with archive.open('xb') as handle:
                    write_tar(handle, files)
                pending = Path(tempfile.mkdtemp(prefix='.pending-', dir=ROOT))
                encrypted = pending / 'backup.age'
                run(['age', '-r', recipient, '-o', str(encrypted), str(archive)], 'encryption')
                checked, checked_files = read_bundle(identity, encrypted)
                require(checked == manifest and checked_files == files, 'integrity')
                with encrypted.open('rb') as handle:
                    os.fsync(handle.fileno())
                encrypted.chmod(0o400)
                sync_directory(pending)
                sets = ROOT / 'sets'
                sets.mkdir(mode=0o700, exist_ok=True)
                completed = sets / uuid.uuid4().hex
                pending.rename(completed)
                pending = None
                completed.chmod(0o500)
                sync_directory(completed)
                sync_directory(sets)
                sync_directory(ROOT)
                # Latest reference is authoritative for freshness and default restore.
                # An attempt-status error cannot overwrite the previous complete set/pointer.
                status_update(last_attempt=time.time(), last_result=1, duration=time.monotonic()-started)
                reference = {'file': str(completed.relative_to(ROOT) / 'backup.age'),
                             'sha256': file_sha(completed / 'backup.age'), 'completed_at': time.time()}
                atomic_json(ROOT / 'latest.json', reference)
        except BaseException as error:
            if pending:
                shutil.rmtree(pending, ignore_errors=True)
            try:
                failed(error.stage if isinstance(error, Failure) else 'interrupted', time.monotonic()-started)
            except (OSError, Failure):
                pass  # Preserve the primary error; latest still identifies the prior set.
            raise


def restore(identity, bundle=None):
    guard()
    started = time.monotonic()
    with lock():
        try:
            require(sql('pgtarget', "SELECT current_setting('server_version_num')::int / 10000") == '17', 'configuration')
            empty_target()
            require(not (ROOT / 'restored-objects').exists(), 'restore')
            manifest, files = read_bundle(identity, bundle or latest_bundle())
            inspect_dump(manifest, files['database.dump'])
            # No modification of the destination database occurs until all manifest,
            # inventory and dump checks above have passed in the independent inspection DB.
            empty_target()
            restore_dump(files['database.dump'], 'dentalcare_synthetic')
            require(fingerprint('pgtarget') == manifest['fingerprint'], 'validation')
            compare_inventory(manifest, inventory('pgtarget'))
            staging = Path(tempfile.mkdtemp(prefix='.restore-', dir=ROOT))
            try:
                for doc in manifest['documents']:
                    if doc['key'] is not None:
                        dest = staging / safe_key(doc['key'])
                        dest.parent.mkdir(parents=True, mode=0o700, exist_ok=True)
                        dest.write_bytes(files[doc['file']])
                        require(file_sha(dest) == doc['sha256'], 'validation')
                staging.rename(ROOT / 'restored-objects')
            finally:
                shutil.rmtree(staging, ignore_errors=True)
            status_update(last_attempt=time.time(), last_result=1, last_restore=time.time(), restore_duration=time.monotonic()-started)
            return {'tables': len(manifest['fingerprint']['rows']),
                    'documents': sum(d['key'] is not None for d in manifest['documents']),
                    'metadata_only': sum(d['key'] is None for d in manifest['documents']),
                    'recovery_seconds': round(time.monotonic()-started, 3)}
        except BaseException as error:
            try:
                failed(error.stage if isinstance(error, Failure) else 'interrupted', time.monotonic()-started)
            except (OSError, Failure):
                pass
            raise


def metrics():
    try:
        s = json.loads((ROOT / 'status.json').read_text())
    except (OSError, ValueError):
        s = {}
    if not isinstance(s, dict):
        s = {}
    try:
        s['last_complete'] = latest(require_payload=False)['completed_at']
    except (Failure, KeyError, TypeError):
        s['last_complete'] = 0
    fields = {'last_complete': 'last_complete_timestamp_seconds', 'last_attempt': 'last_attempt_timestamp_seconds', 'last_failure': 'last_failure_timestamp_seconds', 'last_result': 'last_result',
              'duration': 'duration_seconds', 'last_restore': 'last_restore_timestamp_seconds',
              'restore_duration': 'restore_duration_seconds'}
    lines = []
    for field, metric in fields.items():
        value = s.get(field, 0)
        if not isinstance(value, (int, float)) or not math.isfinite(value) or value < 0:
            value = 0
        if field.startswith('last_') and field != 'last_result' and value > time.time() + 60:
            value = 0
        if field == 'last_result' and value not in (0, 1):
            value = 0
        lines += [f'# TYPE dentalcare_backup_{metric} gauge', f'dentalcare_backup_{metric} {value}']
    lines.append('# TYPE dentalcare_backup_failures_total counter')
    for stage in STAGES:
        failures = s.get('failures', {})
        value = failures.get(stage, 0) if isinstance(failures, dict) else 0
        if not isinstance(value, int) or value < 0:
            value = 0
        lines.append(f'dentalcare_backup_failures_total{{stage="{stage}"}} {value}')
    return '\n'.join(lines) + '\n'


def serve():
    class Handler(http.server.BaseHTTPRequestHandler):
        def do_GET(self):
            if self.path != '/metrics':
                self.send_error(404)
                return
            body = metrics().encode()
            self.send_response(200)
            self.send_header('Content-Type', 'text/plain; version=0.0.4')
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, *args):
            pass
    http.server.HTTPServer(('0.0.0.0', 8000), Handler).serve_forever()


def main():
    os.umask(0o077)
    signal.signal(signal.SIGTERM, lambda *_: (_ for _ in ()).throw(Failure('interrupted')))
    command = sys.argv[1] if len(sys.argv) > 1 else ''
    if command == 'serve':
        serve()
    elif command == 'verify':
        import unittest
        suite = unittest.defaultTestLoader.discover('/opt/backup', 'test_backup.py')
        # Never emit assertion values, subprocess diagnostics or tracebacks to operational logs.
        result = unittest.TextTestRunner(stream=io.StringIO()).run(suite)
        print(json.dumps({'event': 'backup_tests_completed', 'tests': result.testsRun,
                          'failures': len(result.failures), 'errors': len(result.errors),
                          'failed_tests': [getattr(test, '_testMethodName', 'suite_setup') for test, _ in result.failures + result.errors]}))
        require(result.wasSuccessful(), 'validation')
    elif command == 'backup' and len(sys.argv) == 4:
        backup(sys.argv[2], Path(sys.argv[3]))
    elif command == 'restore' and len(sys.argv) == 3:
        print(json.dumps(restore(Path(sys.argv[2]))))
    else:
        raise Failure('configuration')


if __name__ == '__main__':
    try:
        main()
    except BaseException as error:
        print(json.dumps({'event': 'backup_operation_failed',
                          'stage': error.stage if isinstance(error, Failure) else 'validation'}))
        sys.exit(1)
