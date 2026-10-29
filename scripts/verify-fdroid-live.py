#!/usr/bin/env python3
"""Verify the public feed through the same signatures/hashes used by clients."""
import argparse
import hashlib
import json
import subprocess
import tempfile
import time
from pathlib import Path
from urllib.request import urlopen


def fetch(url, target):
    with urlopen(url, timeout=30) as response, target.open('wb') as output:
        while chunk := response.read(1024 * 1024):
            output.write(chunk)


def verify(base, expected_source):
    from fdroidserver import common, index
    root = Path(__file__).resolve().parents[1]
    pins = json.loads((root / 'fdroid/signing-certificates.json').read_text())
    repo = base.rstrip('/') + '/fdroid/beta/repo'
    # common's default config discovers JDK tooling without any private config.
    common.config = {}
    common.fill_config_defaults(common.config)
    with tempfile.TemporaryDirectory(prefix='battery-feed-verification-') as tmp:
        work = Path(tmp)
        fetch(repo + '/index-v1.jar', work / 'index-v1.jar')
        v1, _, _ = index.get_index_from_jar(str(work / 'index-v1.jar'), pins['repoSha256'], allow_deprecated=True)
        fetch(repo + '/entry.jar', work / 'entry.jar')
        entry, _, _ = index.get_index_from_jar(str(work / 'entry.jar'), pins['repoSha256'])
        fetch(repo + '/index-v2.json', work / 'index-v2.json')
        if hashlib.sha256((work / 'index-v2.json').read_bytes()).hexdigest() != entry['index']['sha256']:
            raise ValueError('Public v2 index hash differs from signed entry')
        package = 'io.github.sivivatu.batterymonitor'
        versions = v1['packages'][package]
        fetch(base.rstrip('/') + '/release-manifest.json', work / 'manifest.json')
        manifest = json.loads((work / 'manifest.json').read_text())
        latest = max(manifest, key=lambda item: item['versionCode'])
        if latest['sourceSha'] != expected_source:
            raise ValueError('Live feed has not reached the requested source commit')
        import os
        signer = Path(os.environ['ANDROID_HOME']) / 'build-tools/36.1.0/apksigner'
        for record in manifest:
            version = next(v for v in versions if v['versionCode'] == record['versionCode'])
            if version['apkName'] != record['apkName'] or version['hash'] != record['sha256']:
                raise ValueError('Unsigned manifest disagrees with signed index')
            apk = work / record['apkName']
            fetch(repo + '/' + record['apkName'], apk)
            if hashlib.sha256(apk.read_bytes()).hexdigest() != version['hash']:
                raise ValueError('Public APK differs from signed index hash')
            result = subprocess.run([str(signer), 'verify', '--print-certs', str(apk)], check=True, capture_output=True, text=True)
            if 'Signer #1 certificate SHA-256 digest: ' + pins['apkSha256'] not in result.stdout:
                raise ValueError('Public APK signer differs from permanent pin')
        print(f'Live v1/v2 signatures, {len(manifest)} APK hash(es), signing identity and source verified.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--expected-source', required=True)
    parser.add_argument('--base-url', default='https://sivivatu.github.io/battery-monitor')
    args = parser.parse_args()
    for attempt in range(12):
        try:
            verify(args.base_url, args.expected_source)
            return
        except Exception:
            if attempt == 11:
                raise
            time.sleep(5)


if __name__ == '__main__':
    main()
