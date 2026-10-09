"""Executed only by the isolated Docker laboratory; all rows and objects are synthetic."""
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import tempfile
import time
import unittest
import urllib.request
import urllib.error
from unittest.mock import patch
import backup as b


class BackupLaboratoryTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        b.guard()
        cls.keys = Path('/work/keys')
        cls.keys.mkdir(mode=0o700)
        b.run(['age-keygen', '-o', str(cls.keys / 'identity')], 'configuration')
        cls.recipient = b.run(['age-keygen', '-y', str(cls.keys / 'identity')], 'configuration').decode().strip()
        b.run(['age-keygen', '-o', str(cls.keys / 'wrong')], 'configuration')
        b.sql('pgsource', "COMMENT ON DATABASE dentalcare_synthetic IS 'DENTALCARE_SYNTHETIC_ONLY'")
        b.sql('pgsource', """
        INSERT INTO users(id,username,email,password_hash,status,created_at,updated_at,cui,full_name)
        VALUES ('00000000-0000-4000-8000-000000000001','synthetic_lab','lab@example.invalid',
                'SYNTHETIC_NOT_A_PASSWORD','ACTIVE',now(),now(),'0000000000001','Synthetic operator');
        INSERT INTO patients(id,code,name,dpi,birth_date,gender,phone,created_at,updated_at)
        VALUES ('00000000-0000-4000-8000-000000000002','SYNTHETIC-1','Synthetic patient',
                '0000000000002','2000-01-01','OTHER','00000000',now(),now());
        SELECT nextval('patient_code_seq');
        INSERT INTO clinical_documents(id,patient_id,author_id,title,type,document_date,created_at,updated_at,
                                       storage_object_key,file_name,file_size,content_type)
        VALUES ('00000000-0000-4000-8000-000000000003','00000000-0000-4000-8000-000000000002',
                '00000000-0000-4000-8000-000000000001','Synthetic document','OTHER',current_date,now(),now(),
                'patients/00000000-0000-4000-8000-000000000002/documents/00000000-0000-4000-8000-000000000003.pdf',
                'synthetic.pdf',16,'application/pdf');
        INSERT INTO clinical_documents(id,patient_id,author_id,title,type,document_date,created_at,updated_at,file_size)
        VALUES ('00000000-0000-4000-8000-000000000004','00000000-0000-4000-8000-000000000002',
                '00000000-0000-4000-8000-000000000001','Synthetic metadata','OTHER',current_date,now(),now(),0);
        """
        )
        key = next(d['key'] for d in b.inventory('pgsource') if d['key'])
        cls.obj = b.ROOT / 'source-objects' / key
        cls.obj.parent.mkdir(parents=True)
        cls.obj.write_bytes(b'%PDF-synthetic\n!')
        b.backup(cls.recipient, cls.keys / 'identity')
        cls.reference = b.latest()
        cls.valid = b.latest_bundle().read_bytes()
        print(json.dumps({'event': 'encrypted_backup_created', 'plaintext_artifacts_published': False}))

    def setUp(self):
        b.sql('pgtarget', 'DROP SCHEMA public CASCADE; CREATE SCHEMA public')
        shutil.rmtree(b.ROOT / 'restored-objects', ignore_errors=True)
        b.atomic_json(b.ROOT / 'latest.json', self.reference)
        self.obj.write_bytes(b'%PDF-synthetic\n!')
        b.status_update(last_result=1)
        self.previous = b.latest()['completed_at']

    def assert_invalid_backup(self, operation, stage=None):
        with self.assertRaises(b.Failure) as caught:
            operation()
        if stage:
            self.assertEqual(stage, caught.exception.stage)
        self.assertEqual(self.previous, b.latest()['completed_at'])
        self.assertEqual(self.valid, b.latest_bundle().read_bytes())
        self.assertFalse(list(b.ROOT.glob('.pending-*')))

    def rewrite_bundle(self, transform):
        manifest, files = b.read_bundle(self.keys / 'identity', b.latest_bundle())
        transform(manifest, files)
        files['manifest.json'] = json.dumps(manifest).encode()
        content = io.BytesIO()
        with tarfile.open(fileobj=content, mode='w') as tar:
            for name, data in files.items():
                info = tarfile.TarInfo(name)
                info.size = len(data)
                info.mode = 0o600
                tar.addfile(info, io.BytesIO(data))
        path = Path('/work/tampered.age')
        path.unlink(missing_ok=True)
        b.run(['age', '-r', self.recipient, '-o', str(path)], 'encryption', content.getvalue())
        return path

    def test_01_isolated_restore(self):
        try:
            evidence = b.restore(self.keys / 'identity')
        except b.Failure as error:
            print(json.dumps({'event': 'restore_test_failed', 'stage': error.stage}))
            left, right = b.fingerprint('pgsource'), b.fingerprint('pgtarget')
            print(json.dumps({'event': 'restore_comparison',
                              'tables_differ': [t for t in left['rows'] if left['rows'][t] != right['rows'].get(t)],
                              'constraints_match': left['constraints'] == right['constraints'],
                              'sequences_match': left['sequences'] == right['sequences']}))
            raise
        self.assertGreaterEqual(evidence['tables'], 66)
        self.assertEqual(1, evidence['documents'])
        self.assertEqual(1, evidence['metadata_only'])
        self.assertEqual(b.fingerprint('pgsource'), b.fingerprint('pgtarget'))
        # Constraints still enforce FK integrity, not merely catalog presence.
        with self.assertRaises(b.Failure):
            b.sql('pgtarget', "DELETE FROM patients")
        print(json.dumps({'event': 'isolated_restore_verified', **evidence}))

    def test_02_missing_object(self):
        self.obj.unlink()
        self.assert_invalid_backup(lambda: b.backup(self.recipient, self.keys / 'identity'), 'objects')

    def test_03_corrupt_source_size(self):
        self.obj.write_bytes(b'broken')
        self.assert_invalid_backup(lambda: b.backup(self.recipient, self.keys / 'identity'), 'objects')

    def test_04_ciphertext_corruption(self):
        data = bytearray(self.valid)
        data[-10] ^= 1
        path = Path('/work/corrupt.age')
        path.write_bytes(data)
        with self.assertRaises(b.Failure):
            b.restore(self.keys / 'identity', path)
        self.assertEqual('0', b.sql('pgtarget', "SELECT count(*) FROM pg_tables WHERE schemaname='public'"))

    def test_05_wrong_identity(self):
        with self.assertRaises(b.Failure):
            b.restore(self.keys / 'wrong')

    def test_06_incomplete_encryption(self):
        original = b.run
        def interrupted(args, stage, *a, **kw):
            if args[0] == 'age':
                Path(args[args.index('-o') + 1]).write_bytes(b'incomplete')
                raise b.Failure('encryption')
            return original(args, stage, *a, **kw)
        with patch.object(b, 'run', interrupted):
            self.assert_invalid_backup(lambda: b.backup(self.recipient, self.keys / 'identity'), 'encryption')

    def test_07_connection_refused(self):
        original = b.run
        def disconnected(args, stage, *a, **kw):
            if args[0] == 'psql':
                args = args + ['-p', '1']
            return original(args, stage, *a, **kw)
        with patch.object(b, 'run', disconnected):
            self.assert_invalid_backup(lambda: b.backup(self.recipient, self.keys / 'identity'), 'connection')

    def test_08_insufficient_space(self):
        with patch.object(b.shutil, 'disk_usage', return_value=shutil._ntuple_diskusage(100, 100, 0)):
            self.assert_invalid_backup(lambda: b.backup(self.recipient, self.keys / 'identity'), 'space')

    def test_09_incompatible_format_and_schema(self):
        for field, value in [('format', 99), ('schema', 'incompatible'), ('postgres_major', 99)]:
            path = self.rewrite_bundle(lambda m, f: m.update({field: value}))
            with self.assertRaises(b.Failure) as caught:
                b.restore(self.keys / 'identity', path)
            self.assertEqual('configuration', caught.exception.stage)

    def test_10_missing_and_corrupt_bundled_objects(self):
        for remove in (True, False):
            def transform(manifest, files):
                name = next(d['file'] for d in manifest['documents'] if d['key'])
                if remove:
                    del files[name]
                else:
                    files[name] = b'X' * 16
            with self.assertRaises(b.Failure):
                b.restore(self.keys / 'identity', self.rewrite_bundle(transform))

    def test_11_partial_restore_rolls_back(self):
        def transform(manifest, files):
            files['database.dump'] = files['database.dump'][:len(files['database.dump']) // 2]
            manifest['dump_sha256'] = b.sha(files['database.dump'])
        with self.assertRaises(b.Failure):
            b.restore(self.keys / 'identity', self.rewrite_bundle(transform))
        self.assertEqual('0', b.sql('pgtarget', "SELECT count(*) FROM pg_tables WHERE schemaname='public'"))

    def test_12_concurrent_execution(self):
        code = "import backup as b, time\nwith b.lock():\n print('READY',flush=True)\n time.sleep(60)"
        child = subprocess.Popen(['python3', '-c', code], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        try:
            self.assertEqual(b'READY\n', child.stdout.readline())
            self.assert_invalid_backup(lambda: b.backup(self.recipient, self.keys / 'identity'), 'concurrency')
        finally:
            child.terminate()
            child.communicate(timeout=10)

    def test_13_process_termination(self):
        code = """import backup as b, time
from pathlib import Path
original=b.run
def pause(args, stage, *a, **kw):
    if args[0]=='age':
        assert any(Path('/work').glob('backup-*/bundle.tar'))
        print('READY', flush=True)
        time.sleep(60)
    return original(args, stage, *a, **kw)
b.run=pause
b.main()
"""
        child = subprocess.Popen(['python3', '-c', code, 'backup', self.recipient, str(self.keys / 'identity')], stdout=subprocess.PIPE,
                                 stderr=subprocess.PIPE)
        try:
            self.assertEqual(b'READY\n', child.stdout.readline())
            child.terminate()
            child.communicate(timeout=10)
            self.assertNotEqual(0, child.returncode)
        finally:
            if child.poll() is None:
                child.kill()
                child.communicate()
        self.assertEqual(self.valid, b.latest_bundle().read_bytes())
        self.assertEqual(self.previous, b.latest()['completed_at'])
        self.assertFalse(list(Path('/work').glob('backup-*')))
        self.assertFalse(list(b.ROOT.glob('.pending-*')))

    def test_14_safe_configuration_and_paths(self):
        with patch.dict(os.environ, {'DENTALCARE_SYNTHETIC_LAB': '0'}):
            with self.assertRaises(b.Failure):
                b.backup(self.recipient, self.keys / 'identity')
        for key in ('/etc/passwd', 'patients/../documents/x', 'patients/a/documents/../../x'):
            with self.assertRaises(b.Failure):
                b.safe_key(key)

    def test_15_metrics_have_only_allowed_fields(self):
        b.status_update(secret='SENSITIVE_SENTINEL', failures={'unexpected_key': 9, 'objects': 2})
        text = b.metrics()
        self.assertNotIn('SENSITIVE_SENTINEL', text)
        self.assertNotIn('unexpected_key', text)
        self.assertNotIn('patients/', text)
        self.assertIn('stage="objects"} 2', text)
        with patch.object(b, 'ROOT', Path('/work/nonexistent')):
            self.assertIn('last_complete_timestamp_seconds 0', b.metrics())

    def test_16_validation_failure_never_marks_restore(self):
        before = json.loads((b.ROOT / 'status.json').read_text()).get('last_restore')
        with patch.object(b, 'fingerprint', return_value={}):
            with self.assertRaises(b.Failure):
                b.restore(self.keys / 'identity')
        self.assertEqual(before, json.loads((b.ROOT / 'status.json').read_text()).get('last_restore'))

    def test_17_source_database_version(self):
        original = b.sql
        def incompatible(host, query, database='dentalcare_synthetic'):
            return '16' if 'server_version_num' in query else original(host, query, database)
        with patch.object(b, 'sql', incompatible):
            self.assert_invalid_backup(lambda: b.backup(self.recipient, self.keys / 'identity'), 'configuration')

    def test_18_private_http_metrics_and_restart(self):
        for _ in range(2):
            process = subprocess.Popen(['python3', '/opt/backup/backup.py', 'serve'],
                                       stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
            try:
                body = None
                for attempt in range(50):
                    try:
                        with urllib.request.urlopen('http://127.0.0.1:8000/metrics', timeout=1) as response:
                            body = response.read().decode()
                        break
                    except (OSError, urllib.error.URLError):
                        time.sleep(0.1)
                self.assertIsNotNone(body)
                self.assertIn('dentalcare_backup_last_complete_timestamp_seconds', body)
                self.assertNotIn('patients/', body)
                self.assertNotIn('SENSITIVE_SENTINEL', body)
                with self.assertRaises(urllib.error.HTTPError) as caught:
                    urllib.request.urlopen('http://127.0.0.1:8000/other', timeout=1)
                self.assertEqual(404, caught.exception.code)
            finally:
                process.terminate()
                process.communicate(timeout=10)

    def test_19_corrupt_status_fails_closed(self):
        original = (b.ROOT / 'status.json').read_bytes()
        try:
            for data in ('not-json', '[]', '{"last_result":7,"failures":"invalid"}'):
                (b.ROOT / 'status.json').write_text(data)
                self.assertIn('dentalcare_backup_last_result 0', b.metrics())
        finally:
            (b.ROOT / 'status.json').write_bytes(original)

    def test_20_constraint_normalization_retains_semantics(self):
        original = "CHECK (x = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[]))"
        restored = "CHECK (x = ANY (ARRAY[('ACTIVE'::character varying)::text, ('INACTIVE'::character varying)::text]))"
        self.assertEqual(b.normalize_constraint(original), b.normalize_constraint(restored))
        self.assertNotEqual(b.normalize_constraint(original), b.normalize_constraint(restored.replace('INACTIVE', 'LOCKED')))
        self.assertNotEqual(b.normalize_constraint('CHECK (x > 0)'), b.normalize_constraint('CHECK (x >= 0)'))

    def assert_rejected_before_target(self, transform):
        before = b.catalog('pgtarget')
        with self.assertRaises(b.Failure):
            b.restore(self.keys / 'identity', self.rewrite_bundle(transform))
        self.assertEqual(before, b.catalog('pgtarget'))
        self.assertFalse((b.ROOT / 'restored-objects').exists())

    def test_21_original_duplicate_id_exploit(self):
        def transform(m, files):
            doc = next(d for d in m['documents'] if d['key'])
            extra = {**doc, 'key': 'patients/synthetic/documents/extra.pdf', 'file': 'objects/999'}
            files[extra['file']] = files[doc['file']]
            m['documents'].insert(0, extra)
        self.assert_rejected_before_target(transform)

    def test_22_duplicate_object_keys_and_files(self):
        for duplicate in ('key', 'file'):
            def transform(m, files):
                doc = next(d for d in m['documents'] if d['key'])
                extra = {**doc, 'id': '00000000-0000-4000-8000-000000000099'}
                if duplicate == 'key':
                    extra['file'] = 'objects/999'
                    files[extra['file']] = files[doc['file']]
                else:
                    extra['key'] = 'patients/synthetic/documents/extra.pdf'
                m['documents'].append(extra)
            self.assert_rejected_before_target(transform)

    def test_23_missing_or_additional_manifest_row(self):
        for added in (False, True):
            def transform(m, files):
                if added:
                    m['documents'].append({'id': '00000000-0000-4000-8000-000000000099', 'key': None, 'size': None})
                else:
                    m['documents'] = [d for d in m['documents'] if d['key'] is not None]
            self.assert_rejected_before_target(transform)

    def test_24_unsafe_and_ambiguous_paths(self):
        for path in ('/objects/0', 'objects/../0', 'objects//0', 'objects/00', 'objects/./0'):
            def transform(m, files):
                doc = next(d for d in m['documents'] if d['key'])
                files[path] = files.pop(doc['file'])
                doc['file'] = path
            self.assert_rejected_before_target(transform)

    def test_25_additional_payload_and_metadata_only_confusion(self):
        self.assert_rejected_before_target(lambda m, f: f.update({'objects/999': b'extra'}))
        def confused(m, f):
            next(d for d in m['documents'] if d['key'] is None)['file'] = 'objects/0'
        self.assert_rejected_before_target(confused)

    def test_26_missing_index_after_real_restore(self):
        original = b.restore_dump
        def damaged(dump, database):
            original(dump, database)
            if database == 'dentalcare_synthetic':
                b.sql('pgtarget', 'DROP INDEX idx_patients_phone')
        previous = b.load_status().get('last_restore')
        with patch.object(b, 'restore_dump', damaged):
            with self.assertRaises(b.Failure) as caught:
                b.restore(self.keys / 'identity')
            self.assertEqual('validation', caught.exception.stage)
        self.assertEqual('0', b.sql('pgtarget', "SELECT count(*) FROM pg_indexes WHERE indexname='idx_patients_phone'"))
        self.assertEqual(previous, b.load_status().get('last_restore'))
        self.assertFalse((b.ROOT / 'restored-objects').exists())

    def test_27_column_properties_and_definitions(self):
        original = b.restore_dump
        for ddl in ('ALTER TABLE users ALTER COLUMN last_login_at TYPE text',
                    'ALTER TABLE users ALTER COLUMN email SET NOT NULL',
                    "ALTER TABLE users ALTER COLUMN status SET DEFAULT 'LOCKED'"):
            b.sql('pgtarget', 'DROP SCHEMA public CASCADE; CREATE SCHEMA public')
            def damaged(dump, database):
                original(dump, database)
                if database == 'dentalcare_synthetic':
                    b.sql('pgtarget', ddl)
            with patch.object(b, 'restore_dump', damaged):
                with self.assertRaises(b.Failure):
                    b.restore(self.keys / 'identity')

    def test_28_other_schema_is_preserved_and_rejected(self):
        b.sql('pgtarget', 'CREATE SCHEMA existing_data; CREATE TABLE existing_data.marker(value int); INSERT INTO existing_data.marker VALUES (1)')
        try:
            with self.assertRaises(b.Failure) as caught:
                b.restore(self.keys / 'identity')
            self.assertEqual('restore', caught.exception.stage)
            self.assertEqual('1', b.sql('pgtarget', 'SELECT value FROM existing_data.marker'))
        finally:
            b.sql('pgtarget', 'DROP SCHEMA existing_data CASCADE')

    def test_29_preexisting_views_sequences_and_functions(self):
        for ddl in ('CREATE VIEW public.extra AS SELECT 1 n', 'CREATE SEQUENCE public.extra',
                    "CREATE FUNCTION public.extra() RETURNS integer LANGUAGE SQL AS 'SELECT 1'"):
            b.sql('pgtarget', 'DROP SCHEMA public CASCADE; CREATE SCHEMA public')
            b.sql('pgtarget', ddl)
            before = b.catalog('pgtarget')
            with self.assertRaises(b.Failure):
                b.restore(self.keys / 'identity')
            self.assertEqual(before, b.catalog('pgtarget'))

    def test_30_corrupt_status_preserves_immutable_set_and_reference(self):
        pointer = (b.ROOT / 'latest.json').read_bytes()
        original_path = b.latest_bundle()
        (b.ROOT / 'status.json').write_text('invalid-json')
        self.assert_invalid_backup(lambda: b.backup(self.recipient, self.keys / 'identity'), 'configuration')
        self.assertEqual(pointer, (b.ROOT / 'latest.json').read_bytes())
        self.assertEqual(self.valid, original_path.read_bytes())
        b.restore(self.keys / 'identity')

    def test_31_status_write_failure_after_encryption(self):
        pointer = (b.ROOT / 'latest.json').read_bytes()
        with patch.object(b, 'status_update', side_effect=OSError('simulated status disk failure')):
            with self.assertRaises(OSError):
                b.backup(self.recipient, self.keys / 'identity')
        self.assertEqual(pointer, (b.ROOT / 'latest.json').read_bytes())
        self.assertEqual(self.valid, b.latest_bundle().read_bytes())
        self.assertFalse(list(b.ROOT.glob('.pending-*')))

    def test_32_successful_publication_is_immutable(self):
        old_path = b.latest_bundle()
        old_data = old_path.read_bytes()
        b.backup(self.recipient, self.keys / 'identity')
        self.assertNotEqual(old_path, b.latest_bundle())
        self.assertEqual(old_data, old_path.read_bytes())
        self.assertEqual(0, old_path.stat().st_mode & 0o222)

    def encrypt_bytes(self, data):
        path = Path('/work/resource-test.age')
        path.unlink(missing_ok=True)
        b.run(['age', '-r', self.recipient, '-o', str(path)], 'encryption', data)
        return path

    def test_33_ciphertext_limit_before_decrypt(self):
        path = b.latest_bundle()
        with patch.object(b, 'MAX_CIPHER', 16):
            with self.assertRaises(b.Failure) as caught:
                b.read_bundle(self.keys / 'identity', path)
            self.assertEqual('space', caught.exception.stage)

    def test_34_plaintext_limit_during_decrypt(self):
        path = self.encrypt_bytes(b'X' * 8192)
        with patch.object(b, 'MAX_PLAIN', 4096):
            with self.assertRaises(b.Failure) as caught:
                b.read_bundle(self.keys / 'identity', path)
            self.assertEqual('space', caught.exception.stage)
        self.assertFalse(list(Path('/work').glob('decrypt-*')))

    def test_35_compressed_expansion_is_rejected(self):
        content = io.BytesIO()
        with tarfile.open(fileobj=content, mode='w:gz') as tar:
            info = tarfile.TarInfo('objects/0')
            info.size = 1024 * 1024
            tar.addfile(info, io.BytesIO(b'X' * info.size))
        with self.assertRaises(b.Failure):
            b.read_bundle(self.keys / 'identity', self.encrypt_bytes(content.getvalue()))

    def test_36_entry_count_individual_and_accumulated_limits(self):
        path = b.latest_bundle()
        for field, limit in (('MAX_ENTRIES', 2), ('MAX_FILE', 8), ('MAX_MANIFEST', 8)):
            with patch.object(b, field, limit):
                with self.assertRaises(b.Failure):
                    b.read_bundle(self.keys / 'identity', path)

    def test_37_duplicate_tar_members_and_json_fields(self):
        _, files = b.read_bundle(self.keys / 'identity', b.latest_bundle())
        output = io.BytesIO()
        with tarfile.open(fileobj=output, mode='w') as tar:
            for _ in range(2):
                info = tarfile.TarInfo('manifest.json')
                info.size = 2
                tar.addfile(info, io.BytesIO(b'{}'))
        with self.assertRaises(b.Failure):
            b.read_bundle(self.keys / 'identity', self.encrypt_bytes(output.getvalue()))
        files['manifest.json'] = b'{"format":2,"format":2}'
        output = io.BytesIO()
        b.write_tar(output, files)
        with self.assertRaises(b.Failure):
            b.read_bundle(self.keys / 'identity', self.encrypt_bytes(output.getvalue()))

    def test_38_trailing_tar_payload_rejected(self):
        _, files = b.read_bundle(self.keys / 'identity', b.latest_bundle())
        output = io.BytesIO()
        b.write_tar(output, files)
        with self.assertRaises(b.Failure):
            b.read_bundle(self.keys / 'identity', self.encrypt_bytes(output.getvalue() + b'hidden'))

    def test_39_failure_signal_survives_immediate_success(self):
        b.failed('objects', 0.1)
        timestamp = b.load_status()['last_failure']
        b.status_update(last_result=1, last_attempt=time.time())
        self.assertEqual(timestamp, b.load_status()['last_failure'])
        self.assertIn('dentalcare_backup_last_failure_timestamp_seconds', b.metrics())

    def test_40_latest_reference_write_failure(self):
        pointer = (b.ROOT / 'latest.json').read_bytes()
        original = b.atomic_json
        def fail_reference(path, values):
            if path.name == 'latest.json':
                raise OSError('simulated reference write failure')
            original(path, values)
        with patch.object(b, 'atomic_json', fail_reference):
            with self.assertRaises(OSError):
                b.backup(self.recipient, self.keys / 'identity')
        self.assertEqual(pointer, (b.ROOT / 'latest.json').read_bytes())
        self.assertEqual(self.valid, b.latest_bundle().read_bytes())
        b.restore(self.keys / 'identity')

    def test_41_oversized_ciphertext_rejected_without_starting_age(self):
        path = Path('/work/oversized.age')
        try:
            with path.open('wb') as handle:
                handle.truncate(b.MAX_CIPHER + 1)  # sparse; no large allocation needed
            with patch.object(b.subprocess, 'Popen') as process:
                with self.assertRaises(b.Failure) as caught:
                    b.read_bundle(self.keys / 'identity', path)
                self.assertEqual('space', caught.exception.stage)
                process.assert_not_called()
        finally:
            path.unlink(missing_ok=True)

    def test_42_oversized_and_inconsistent_tar_headers(self):
        for size in (b.MAX_FILE + 1, 65536):
            info = tarfile.TarInfo('database.dump')
            info.size = size
            # Deliberately absent payload: reject declared oversize before extraction,
            # and reject a permitted declared size whose bytes are truncated.
            path = self.encrypt_bytes(info.tobuf() + bytes(1024))
            with self.assertRaises(b.Failure):
                b.read_bundle(self.keys / 'identity', path)
        self.assertFalse(list(Path('/work').glob('decrypt-*')))

    def test_43_metadata_only_exporter(self):
        with tempfile.TemporaryDirectory(dir='/work') as directory:
            metadata = Path(directory)
            for name in ('status.json', 'latest.json'):
                shutil.copyfile(b.ROOT / name, metadata / name)
            with patch.object(b, 'ROOT', metadata):
                self.assertIn(f'dentalcare_backup_last_complete_timestamp_seconds {self.previous}', b.metrics())
                with self.assertRaises(b.Failure):
                    b.latest_bundle()
                self.assertNotIn('sets/', b.metrics())

    def test_44_missing_payload_http_and_recovery(self):
        path = b.latest_bundle()
        held = path.with_name('temporarily-held.age')
        process = subprocess.Popen(['python3', '/opt/backup/backup.py', 'serve'],
                                   stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        def scrape():
            with urllib.request.urlopen('http://127.0.0.1:8000/metrics', timeout=1) as response:
                return response.read().decode()
        try:
            for _ in range(50):
                try:
                    present = scrape()
                    break
                except (OSError, urllib.error.URLError):
                    time.sleep(0.1)
            else:
                self.fail('Exporter did not start')
            self.assertIn('dentalcare_backup_payload_available 1', present)
            path.rename(held)
            missing = scrape()
            self.assertIn('dentalcare_backup_payload_available 0', missing)
            historical = f'dentalcare_backup_last_complete_timestamp_seconds {self.previous}'
            self.assertIn(historical, missing)
            with self.assertRaises(b.Failure):
                b.latest_bundle()
            held.rename(path)
            self.assertIn('dentalcare_backup_payload_available 1', scrape())
            with patch.object(b, 'file_sha', side_effect=AssertionError('scrape must not hash')):
                self.assertEqual(1, b.payload_available())
            print(json.dumps({'event': 'payload_availability_verified', 'present_missing_recovered': [1, 0, 1],
                              'completion_timestamp_preserved': True}))
        finally:
            if held.exists():
                held.rename(path)
            process.terminate()
            process.communicate(timeout=10)

    def test_45_separate_ciphertext_mount_and_invalid_payload(self):
        path = b.latest_bundle()
        with tempfile.TemporaryDirectory(dir='/work') as directory:
            metadata = Path(directory)
            for name in ('status.json', 'latest.json'):
                shutil.copyfile(b.ROOT / name, metadata / name)
            sets = b.ROOT / 'sets'
            with patch.object(b, 'ROOT', metadata), patch.dict(os.environ, {'BACKUP_SETS_PATH': str(sets)}):
                self.assertEqual(1, b.payload_available())
                held = path.with_name('temporarily-held.age')
                path.rename(held)
                try:
                    path.symlink_to(held)
                    self.assertEqual(0, b.payload_available())
                    path.unlink()
                    path.touch()
                    self.assertEqual(0, b.payload_available())
                    path.unlink()
                finally:
                    path.unlink(missing_ok=True)
                    held.rename(path)
                self.assertEqual(1, b.payload_available())
