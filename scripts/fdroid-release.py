#!/usr/bin/env python3
"""Sign a beta and build a public F-Droid feed, keeping secrets outside output.

Run with Python from the pinned fdroidserver environment. The caller serializes
releases and persists --state between runs. No private input is logged.
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import html
import json
import os
import re
import shutil
import subprocess
import tempfile
import time
from pathlib import Path

PACKAGE = 'io.github.sivivatu.batterymonitor'
REPO_URL = 'https://sivivatu.github.io/battery-monitor/fdroid/beta/repo'
ROOT = Path(__file__).resolve().parents[1]
REPO_PATH = Path('fdroid/beta/repo')


def normalize_fingerprint(value: str) -> str:
    normalized = value.replace(':', '').lower()
    if not re.fullmatch(r'[a-f0-9]{64}', normalized):
        raise ValueError('Invalid pinned certificate fingerprint')
    return normalized


def validate_version(code: int, version: str, tag: str) -> None:
    if code < 2 or not re.fullmatch(r'\d+\.\d+\.\d+-beta\.\d+', version):
        raise ValueError('Only numbered beta releases with versionCode >= 2 are allowed')
    if tag != 'v' + version:
        raise ValueError('Tag must exactly match the embedded APK beta version')


def merge_release(previous: list[dict], candidate: dict) -> list[dict]:
    matches = [item for item in previous if item['versionCode'] == candidate['versionCode']]
    if matches:
        if matches != [candidate]:
            raise ValueError('A published versionCode cannot change its APK or metadata')
        if candidate['versionCode'] != max(item['versionCode'] for item in previous):
            raise ValueError('Cannot republish an older release')
        return list(previous)
    if previous and candidate['versionCode'] <= max(item['versionCode'] for item in previous):
        raise ValueError('Release versionCode must increase')
    if any(item['tag'] == candidate['tag'] for item in previous):
        raise ValueError('A release tag cannot be reused')
    return [*previous, candidate]


def validate_public_file(relative: Path) -> None:
    """Allow only fdroidserver's expected public payloads, never config files."""
    parts = relative.parts
    if not parts or relative.is_absolute() or '..' in parts:
        raise ValueError('Unsafe repository path')
    name = relative.name
    indexes = {'index.jar', 'index.xml', 'index-v1.jar', 'index-v1.json',
               'index-v2.json', 'entry.jar', 'entry.json', 'index.html', 'index.css', 'index.png'}
    if len(parts) == 1 and (name in indexes or re.fullmatch(re.escape(PACKAGE) + r'_\d+\.apk', name)):
        return
    if parts[0] == 'diff' and len(parts) == 2 and re.fullmatch(r'\d+\.json', name):
        return
    if (parts[0].startswith('icons') or parts[0] == PACKAGE) and len(parts) <= 4:
        if re.fullmatch(r'[a-zA-Z0-9_.+-]+\.(png|webp|jpg|jpeg)', name):
            return
    raise ValueError('Unexpected file in public repository: ' + str(relative))


def public_files(root: Path) -> list[Path]:
    if not root.exists():
        return []
    files = []
    for item in root.rglob('*'):
        if item.is_symlink():
            raise ValueError('Symlinks are forbidden in publication payloads')
        if item.is_file():
            validate_public_file(item.relative_to(root))
            files.append(item)
    return files


def digest(path: Path) -> str:
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def validate_retained_files(repo: Path, records: list[dict]) -> None:
    expected = {}
    for record in records:
        code = record['versionCode']
        if not isinstance(record.get('addedAt'), int) or record['addedAt'] <= 0:
            raise ValueError('Invalid retained publication date')
        if not re.fullmatch(r'[a-f0-9]{40}', record.get('sourceSha', '')):
            raise ValueError('Invalid retained source commit')
        validate_version(code, record['versionName'], record['tag'])
        expected_name = f'{PACKAGE}_{code}.apk'
        if record['apkName'] != expected_name or code in expected:
            raise ValueError('Invalid retained release manifest')
        expected[code] = expected_name
        apk = repo / expected_name
        if not apk.is_file() or apk.is_symlink() or digest(apk) != record['sha256']:
            raise ValueError('Missing or altered retained APK')
    actual = {item.name for item in repo.glob('*.apk')} if repo.exists() else set()
    if actual != set(expected.values()):
        raise ValueError('Untracked APKs in retained repository')
    public_files(repo)


def run(command: list[str], *, cwd: Path | None = None) -> str:
    process = subprocess.run(command, cwd=cwd, capture_output=True, text=True)
    if process.returncode:
        # Some third-party failures echo input/config; do not expose raw output.
        raise RuntimeError(f'{Path(command[0]).name} failed (exit {process.returncode}); private output suppressed')
    return process.stdout


def tools_paths() -> tuple[Path, Path]:
    sdk = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT', ''))
    tools = sdk / 'build-tools' / '36.1.0'
    if not all((tools / name).is_file() for name in ['apksigner', 'aapt2']):
        raise ValueError('Android build-tools 36.1.0 are required via ANDROID_HOME')
    return tools / 'apksigner', tools / 'aapt2'


def apk_metadata(apk: Path, aapt: Path) -> tuple[int, str]:
    output = run([str(aapt), 'dump', 'badging', str(apk)])
    match = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", output, re.M)
    if not match or match[1] != PACKAGE:
        raise ValueError('APK package does not match this feed')
    if "application-debuggable" in output:
        raise ValueError('Debuggable APKs cannot enter the beta feed')
    return int(match[2]), match[3]


def verify_apk(apk: Path, apksigner: Path, expected: str) -> None:
    output = run([str(apksigner), 'verify', '--verbose', '--print-certs', str(apk)])
    matches = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)$', output, re.M)
    if len(matches) != 1 or normalize_fingerprint(matches[0]) != expected:
        raise ValueError('APK signing certificate differs from the permanent pin')


def restore_keys(private: Path, pins: dict) -> tuple[Path, Path]:
    result = []
    for prefix, pin in [('APK', 'apkSha256'), ('FDROID', 'repoSha256')]:
        for suffix in ['KEYSTORE_BASE64', 'KEYSTORE_PASSWORD', 'KEY_ALIAS', 'KEY_PASSWORD']:
            if not os.environ.get(f'{prefix}_{suffix}'):
                raise ValueError(f'Missing signing input {prefix}_{suffix}')
        path = private / (prefix.lower() + '.jks')
        path.write_bytes(base64.b64decode(os.environ[f'{prefix}_KEYSTORE_BASE64'], validate=True))
        path.chmod(0o600)
        cert = private / (prefix.lower() + '.der')
        run(['keytool', '-exportcert', '-keystore', str(path),
             '-alias', os.environ[f'{prefix}_KEY_ALIAS'],
             '-storepass:env', f'{prefix}_KEYSTORE_PASSWORD', '-file', str(cert)])
        if digest(cert) != normalize_fingerprint(pins[pin]):
            raise ValueError(f'{prefix} keystore does not match permanent fingerprint')
        result.append(path)
    return tuple(result)


def write_config(work: Path, repo_key: Path) -> None:
    config = {
        'repo_url': REPO_URL, 'repo_name': 'Battery Monitor Beta',
        'repo_description': 'Public early beta builds. Android 14+, ADB grants or Shizuku required. No telemetry.',
        'repo_icon': 'icon.png', 'archive_older': 0,
        'make_current_version_link': False,
        'keystore': str(repo_key), 'repo_keyalias': os.environ['FDROID_KEY_ALIAS'],
        'keystorepass': {'env': 'FDROID_KEYSTORE_PASSWORD'},
        'keypass': {'env': 'FDROID_KEY_PASSWORD'},
        'sdk_path': os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT'),
    }
    (work / 'config.yml').write_text(json.dumps(config), encoding='utf-8')
    (work / 'config.yml').chmod(0o600)


def verify_indexes(work: Path, expected: str, records: list[dict]) -> None:
    from fdroidserver import common, index
    previous_cwd = Path.cwd()
    try:
        os.chdir(work)
        common.read_config()
        repo = work / 'repo'
        v1, _, _ = index.get_index_from_jar(str(repo / 'index-v1.jar'), expected, allow_deprecated=True)
        entry, _, _ = index.get_index_from_jar(str(repo / 'entry.jar'), expected)
        if entry['index']['sha256'] != digest(repo / 'index-v2.json'):
            raise ValueError('Signed entry does not authenticate index-v2.json')
        packages = v1['packages']
        if set(packages) != {PACKAGE}:
            raise ValueError('Signed index does not contain exactly this application')
        indexed = {(item['versionCode'], item['apkName'], item['hash']) for item in packages[PACKAGE]}
        wanted = {(item['versionCode'], item['apkName'], item['sha256']) for item in records}
        if indexed != wanted:
            raise ValueError('Signed index differs from immutable release manifest')
    finally:
        os.chdir(previous_cwd)


def landing(fingerprint: str) -> str:
    add_url = REPO_URL + '?fingerprint=' + fingerprint.upper()
    return f'''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Battery Monitor beta</title><style>body{{font:18px system-ui;max-width:760px;margin:4rem auto;padding:1rem;line-height:1.6}}code{{overflow-wrap:anywhere}}a{{color:#17663a}}</style>
<h1>Battery Monitor beta</h1><p>Free, ad-free Android battery estimates and local usage history. Requires Android 14 or later.</p>
<p><a href="fdroidrepos://{html.escape(add_url.removeprefix('https://'))}">Add this beta repository to F-Droid</a></p>
<p>In F-Droid, open Settings → Repositories → Add repository and paste:</p><p><code>{html.escape(add_url)}</code></p>
<p><img src="{REPO_URL}/index.png" width="192" height="192" alt="QR code for the authenticated beta repository URL"></p>
<p>Repository SHA-256 fingerprint:</p><p><code>{fingerprint.upper()}</code></p>
<p>This is the developer-operated public beta feed. No account is required. It is separate from F-Droid’s default catalogue.</p>
<p>Collection requires ADB permission grants or Shizuku. <a href="https://github.com/Sivivatu/battery-monitor#install-and-enable-collection">Setup, source and limitations</a> · <a href="https://github.com/Sivivatu/battery-monitor/releases">APK releases</a> · <a href="https://github.com/Sivivatu/battery-monitor/issues">Feedback</a></p>
<p>No telemetry or internet permission is included. Upgrading from a debug preview requires uninstalling it first, which removes saved history.</p></html>'''


def publish(args: argparse.Namespace) -> None:
    state, output, apk = args.state.resolve(), args.output.resolve(), args.apk.resolve()
    if not re.fullmatch(r'[a-f0-9]{40}', args.source_sha):
        raise ValueError('Full source commit SHA is required')
    if output == state or output in state.parents or state in output.parents:
        raise ValueError('Public output and persistent state must be separate directories')
    pins = json.loads((ROOT / 'fdroid/signing-certificates.json').read_text())
    app_pin = normalize_fingerprint(pins['apkSha256'])
    repo_pin = normalize_fingerprint(pins['repoSha256'])
    apksigner, aapt = tools_paths()
    code, version = apk_metadata(apk, aapt)
    validate_version(code, version, args.tag)
    manifest = state / 'release-manifest.json'
    previous = json.loads(manifest.read_text()) if manifest.exists() else []
    old_repo = state / REPO_PATH
    validate_retained_files(old_repo, previous)
    for record in previous:
        retained = old_repo / record['apkName']
        verify_apk(retained, apksigner, app_pin)
        if apk_metadata(retained, aapt) != (record['versionCode'], record['versionName']):
            raise ValueError('Retained APK metadata differs from manifest')
    with tempfile.TemporaryDirectory(prefix='battery-release-private-') as tmp:
        private = Path(tmp)
        private.chmod(0o700)
        app_key, repo_key = restore_keys(private, pins)
        work = private / 'work'
        work.mkdir()
        repo = work / 'repo'
        if old_repo.exists():
            shutil.copytree(old_repo, repo)
        else:
            repo.mkdir()
        apk_name = f'{PACKAGE}_{code}.apk'
        signed = private / apk_name
        existing = next((item for item in previous if item['versionCode'] == code), None)
        if existing:
            if (existing['sourceSha'], existing['tag'], existing['versionName']) != (args.source_sha, args.tag, version):
                raise ValueError('A published versionCode cannot be reused for different source')
            # Build timestamps can differ. Reuse the verified immutable published APK.
            shutil.copy2(old_repo / existing['apkName'], signed)
        else:
            run([str(apksigner), 'sign', '--ks', str(app_key), '--ks-key-alias', os.environ['APK_KEY_ALIAS'],
                 '--ks-pass', 'env:APK_KEYSTORE_PASSWORD', '--key-pass', 'env:APK_KEY_PASSWORD',
                 '--out', str(signed), str(apk)])
        verify_apk(signed, apksigner, app_pin)
        candidate = {'versionCode': code, 'versionName': version, 'tag': args.tag,
                     'sha256': digest(signed), 'apkName': apk_name, 'sourceSha': args.source_sha,
                     'addedAt': existing['addedAt'] if existing else int(time.time())}
        records = merge_release(previous, candidate)
        shutil.copy2(signed, repo / apk_name)
        # Git checkout resets mtimes. Restore original addition dates explicitly.
        for record in records:
            os.utime(repo / record['apkName'], (record['addedAt'], record['addedAt']))
        shutil.copytree(ROOT / 'fdroid/metadata', work / 'metadata')
        # Repository identity icon; app icons are extracted by fdroidserver.
        from PIL import Image, ImageDraw
        (repo / 'icons').mkdir(exist_ok=True)
        icon = Image.new('RGB', (192, 192), '#153c2c')
        draw = ImageDraw.Draw(icon)
        draw.rounded_rectangle((38, 54, 146, 139), radius=10, outline='#e5fff1', width=9)
        draw.rectangle((146, 79, 157, 113), fill='#e5fff1')
        draw.rectangle((55, 71, 110, 122), fill='#62dda0')
        icon.save(repo / 'icons/icon.png')
        write_config(work, repo_key)
        run(['fdroid', 'update', '--pretty', '--use-date-from-apk'], cwd=work)
        verify_indexes(work, repo_pin, records)
        # fdroidserver's operational status includes local tool paths; omit it.
        if (repo / 'status').exists():
            shutil.rmtree(repo / 'status')
        validate_retained_files(repo, records)
        page = landing(repo_pin)
        (repo / 'index.html').write_text(page, encoding='utf-8')
        public_files(repo)
        # Construct the complete public payload before changing persistent state.
        staging = private / 'public'
        shutil.copytree(repo, staging / REPO_PATH)
        (staging / 'index.html').write_text(page, encoding='utf-8')
        (staging / '.nojekyll').touch()
        (staging / 'release-manifest.json').write_text(json.dumps(records, indent=2) + '\n')
        if output.exists():
            if any(output.iterdir()):
                raise ValueError('Output directory must be empty')
            output.rmdir()
        shutil.copytree(staging, output)
        state.mkdir(parents=True, exist_ok=True)
        target = state / REPO_PATH
        if target.exists():
            shutil.rmtree(target)
        shutil.copytree(repo, target)
        for name in ['index.html', '.nojekyll', 'release-manifest.json']:
            shutil.copy2(staging / name, state / name)
    print(f'Validated {version} ({code}); retained {len(records)} signed release(s).')


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', required=True, type=Path)
    parser.add_argument('--state', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--tag', required=True)
    parser.add_argument('--source-sha', required=True)
    args = parser.parse_args()
    try:
        publish(args)
    except (ValueError, RuntimeError, OSError) as error:
        parser.exit(1, f'Publication stopped: {error}\n')


if __name__ == '__main__':
    main()
