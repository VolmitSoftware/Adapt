"""Checks a built resource pack zip without a client. See --help."""
from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

HERE: Path = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE / 'ui'))
import build_bridge  # noqa: E402

VALIDATOR_PACKAGE: str = 'pack/src/main/java/art/arcane/adapt/pack/bake'
SOURCE_PATH: tuple[str, ...] = ('pack/src/main/java', 'adapters/client-common/src/main/java', 'core/src/main/java', 'api/src/main/java')
RESOURCE_PATH: re.Pattern = re.compile(r'^assets/[a-z0-9_.-]+/[a-z0-9_./-]+$')
JSON_SUFFIXES: tuple[str, ...] = ('.json', '.mcmeta')
PNG_SIGNATURE: bytes = b'\x89PNG\r\n\x1a\n'

HELP: str = '''
Checks, in order:
  zip       the archive opens, every entry's CRC matches, no entry is a directory escape or uses backslashes
  paths     every file under assets/ has a lowercase resource path ([a-z0-9_.-/]), as the client requires
  json      every .json and .mcmeta file is UTF-8 without a BOM and parses strictly: no duplicate keys, no NaN or
            Infinity, no trailing data
  png       every .png starts with the PNG signature
  validator the pack bake's PackValidator (font glyph sizes and ascents, unique codepoints, item definitions, models,
            textures and animation strips resolve, pack.mcmeta formats). Its sources are compiled with
            plain javac from <root>/pack/src/main/java; when they are absent this check is reported as skipped.

Exit 0 when every check that ran is clean, 1 otherwise. --json prints the full report.

Examples:
  python3 bukkit/src/test/client/packcheck.py build/pack/adapt-bukkit.zip
  python3 bukkit/src/test/client/packcheck.py pack.zip --root .worktrees/rp-bake --json
'''


def duplicate_free(pairs: list[tuple[str, object]]) -> dict:
    keys: list[str] = [key for key, _ in pairs]
    duplicates: list[str] = sorted({key for key in keys if keys.count(key) > 1})
    if duplicates:
        raise ValueError('duplicate keys ' + ', '.join(duplicates))
    return dict(pairs)


def reject_constant(name: str) -> object:
    raise ValueError('non-standard number ' + name)


def strict_json(data: bytes) -> str:
    """Empty when the bytes are strict JSON, else the reason."""
    if data.startswith(b'\xef\xbb\xbf'):
        return 'UTF-8 byte order mark'
    try:
        text: str = data.decode('utf-8')
        json.loads(text, object_pairs_hook=duplicate_free, parse_constant=reject_constant)
    except (UnicodeDecodeError, ValueError) as failure:
        return str(failure)
    return ''


def archive_checks(pack: Path) -> tuple[dict[str, list[str]], dict[str, bytes]]:
    problems: dict[str, list[str]] = {'zip': [], 'paths': [], 'json': [], 'png': []}
    files: dict[str, bytes] = {}
    try:
        with zipfile.ZipFile(pack) as archive:
            broken: str | None = archive.testzip()
            if broken is not None:
                problems['zip'].append(broken + ': CRC mismatch')
            for info in archive.infolist():
                if info.is_dir():
                    continue
                name: str = info.filename
                if '\\' in name or name.startswith('/') or '..' in name.split('/'):
                    problems['zip'].append(name + ': unsafe entry name')
                    continue
                files[name] = archive.read(info)
    except zipfile.BadZipFile as failure:
        problems['zip'].append(str(failure))
        return problems, files
    if 'pack.mcmeta' not in files:
        problems['zip'].append('pack.mcmeta missing at the root')
    for name, data in sorted(files.items()):
        if name.startswith('assets/') and not RESOURCE_PATH.match(name):
            problems['paths'].append(name + ': not a valid resource path')
        if name.endswith(JSON_SUFFIXES):
            reason: str = strict_json(data)
            if reason:
                problems['json'].append(name + ': ' + reason)
        if name.endswith('.png') and not data.startswith(PNG_SIGNATURE):
            problems['png'].append(name + ': not a PNG')
    return problems, files


def validator(pack: Path, root: Path) -> dict:
    sources: Path = root / VALIDATOR_PACKAGE
    if not (sources / 'PackValidator.java').is_file():
        return {'status': 'skipped', 'reason': f'no {VALIDATOR_PACKAGE}/PackValidator.java under {root}'}
    gson: Path = build_bridge.cached_jar('com.google.code.gson', 'gson', '2.13.2')
    javac: Path = build_bridge.jdk(25) / 'bin' / 'javac'
    java: Path = build_bridge.jdk(25) / 'bin' / 'java'
    with tempfile.TemporaryDirectory() as classes:
        source_path: str = ':'.join(str(root / directory) for directory in SOURCE_PATH)
        compiled = subprocess.run([str(javac), '-proc:none', '-nowarn', '-d', classes, '-cp', str(gson), '-sourcepath', source_path,
                                   '-implicit:class', str(sources / 'PackValidator.java'), str(HERE / 'ui' / 'tools' / 'PackCheck.java')],
                                  capture_output=True, text=True)
        if compiled.returncode != 0:
            return {'status': 'error', 'reason': 'validator does not compile: ' + compiled.stderr.strip()[-1200:]}
        result = subprocess.run([str(java), '-cp', classes + ':' + str(gson), 'PackCheck', str(pack)],
                                capture_output=True, text=True)
    if result.returncode not in (0, 1):
        return {'status': 'error', 'reason': result.stderr.strip()[-1200:]}
    problems: list[str] = [line for line in result.stdout.splitlines() if line.strip()]
    return {'status': 'clean' if not problems else 'problems', 'problems': problems, 'sources': str(sources)}


def main() -> None:
    parser = argparse.ArgumentParser(description='Check a built resource pack zip without a client.', epilog=HELP,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('pack', type=Path, help='the pack zip')
    parser.add_argument('--root', type=Path, default=build_bridge.ROOT, help='checkout holding pack/ (default this checkout)')
    parser.add_argument('--json', action='store_true', help='print the full report as JSON')
    args = parser.parse_args()
    problems, files = archive_checks(args.pack)
    report: dict = {'pack': str(args.pack.resolve()), 'files': len(files),
                    'checks': {name: {'status': 'clean' if not found else 'problems', 'problems': found}
                               for name, found in problems.items()}}
    if not problems['zip']:
        report['checks']['validator'] = validator(args.pack.resolve(), args.root.resolve())
    clean: bool = all(check['status'] in ('clean', 'skipped') for check in report['checks'].values())
    report['ok'] = clean
    if args.json:
        print(json.dumps(report, indent=1))
    else:
        for name, check in report['checks'].items():
            print(f'{name}: {check["status"]}' + (f' ({check["reason"]})' if check.get('reason') else ''))
            for problem in check.get('problems', [])[:40]:
                print('  ' + problem)
        print('ok' if clean else 'FAILED')
    sys.exit(0 if clean else 1)


if __name__ == '__main__':
    main()
