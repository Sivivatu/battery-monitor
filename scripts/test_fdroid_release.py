"""Regression checks for publication invariants, independent of Android tooling."""
import importlib.util
import tempfile
import unittest
from pathlib import Path

SPEC = importlib.util.spec_from_file_location('fdroid_release', Path(__file__).with_name('fdroid-release.py'))
release = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release)


class ReleaseSafetyTest(unittest.TestCase):
    def record(self, code=2, digest='a' * 64):
        return {'versionCode': code, 'versionName': '0.1.0-beta.1', 'tag': 'v0.1.0-beta.1',
                'sha256': digest, 'apkName': f'{release.PACKAGE}_{code}.apk', 'sourceSha': 'f' * 40, 'addedAt': 1234567890}

    def test_new_release_retains_prior_versions(self):
        previous = [self.record()]
        candidate = self.record(3)
        candidate.update(versionName='0.1.0-beta.2', tag='v0.1.0-beta.2')
        self.assertEqual(release.merge_release(previous, candidate), previous + [candidate])
        self.assertEqual(len(previous), 1)

    def test_identical_rerun_is_idempotent(self):
        self.assertEqual(release.merge_release([self.record()], self.record()), [self.record()])

    def test_same_code_cannot_replace_apk(self):
        with self.assertRaises(ValueError):
            release.merge_release([self.record()], self.record(digest='b' * 64))

    def test_same_code_cannot_change_source(self):
        candidate = self.record()
        candidate['sourceSha'] = 'e' * 40
        with self.assertRaises(ValueError):
            release.merge_release([self.record()], candidate)

    def test_valid_retained_apk_is_preserved(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            record = self.record()
            apk = root / record['apkName']
            apk.write_bytes(b'immutable signed fixture')
            record['sha256'] = release.digest(apk)
            release.validate_retained_files(root, [record])
            self.assertEqual(apk.read_bytes(), b'immutable signed fixture')

    def test_retained_source_must_be_full_commit(self):
        record = self.record()
        record['sourceSha'] = 'short'
        with tempfile.TemporaryDirectory() as tmp, self.assertRaises(ValueError):
            release.validate_retained_files(Path(tmp), [record])

    def test_rollback_is_rejected(self):
        with self.assertRaises(ValueError):
            release.merge_release([self.record(3)], self.record())

    def test_tag_must_match_beta_version_exactly(self):
        release.validate_version(2, '0.1.0-beta.1', 'v0.1.0-beta.1')
        for tag in ['v0.1.0', 'v0.1.0-beta.2', '../bad']:
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                release.validate_version(2, '0.1.0-beta.1', tag)

    def test_private_files_cannot_be_published(self):
        for name in ['config.yml', 'release.jks', 'secret.env', 'private.json', '../secret.apk']:
            with self.subTest(name=name), self.assertRaises(ValueError):
                release.validate_public_file(Path(name))

    def test_public_index_and_icons_are_allowed(self):
        for name in ['index-v1.jar', 'entry.jar', 'index-v2.json', 'index.xml',
                     f'{release.PACKAGE}_2.apk', 'icons/icon.png',
                     f'{release.PACKAGE}/en-US/icon.png', 'diff/123.json']:
            release.validate_public_file(Path(name))

    def test_symlink_in_repository_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / 'icons').mkdir()
            (root / 'icons/icon.png').symlink_to('/etc/passwd')
            with self.assertRaises(ValueError):
                release.public_files(root)

    def test_unlisted_apk_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / f'{release.PACKAGE}_2.apk').write_bytes(b'apk')
            with self.assertRaises(ValueError):
                release.validate_retained_files(root, [])

    def test_tampered_retained_apk_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / self.record()['apkName']).write_bytes(b'changed')
            with self.assertRaises(ValueError):
                release.validate_retained_files(root, [self.record()])

    def test_known_signature_normalization(self):
        self.assertEqual(release.normalize_fingerprint(':'.join(['AB'] * 32)), 'ab' * 32)
        with self.assertRaises(ValueError):
            release.normalize_fingerprint('short')


if __name__ == '__main__':
    unittest.main()
