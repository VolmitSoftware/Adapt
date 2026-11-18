import dataclasses
import functools
import hashlib
import json
import re
import shutil
import subprocess
import tempfile
from pathlib import Path

import choreography
import docs_sync
import encode
import export_phase
import manifest

NAME: re.Pattern[str] = re.compile(r'[a-z0-9]+(?:-[a-z0-9]+)*')
MEDIA_FOLDERS: tuple[str, ...] = ('intermediate', 'replays', 'thumbs', 'stills', 'review')


def review_root(output: Path) -> Path:
    return output / 'review'


def contained(root: Path, path: Path) -> Path:
    if not path.resolve().is_relative_to(root.resolve()) or path.is_symlink():
        raise ValueError('Media path escapes its folder: ' + str(path))
    return path


def validate_clip(path: Path, ffmpeg: Path, ffprobe: Path) -> None:
    if not path.is_file() or not 0 < path.stat().st_size <= encode.MAX_CLIP_BYTES:
        raise ValueError('Missing, empty, or oversized clip: ' + str(path))
    result: subprocess.CompletedProcess[str] = subprocess.run(
        [str(ffprobe), '-v', 'error', '-select_streams', 'v:0', '-show_entries', 'stream=codec_name,width,height', '-of', 'json', str(path)],
        capture_output=True, text=True, check=True)
    streams: list[dict] = json.loads(result.stdout).get('streams', [])
    if not streams or streams[0].get('codec_name') != 'vp9' or streams[0].get('width') != encode.WIDTH or streams[0].get('height') != encode.HEIGHT:
        raise ValueError('Clip must contain 1920x1080 VP9 video: ' + str(path))
    encode.run([str(ffmpeg), '-v', 'error', '-xerror', '-i', str(path), '-map', '0:v:0', '-f', 'null', '-'])


def media_files(output: Path, takes: dict[str, manifest.Take], selected: set[str]) -> list[Path]:
    complete: bool = all(identifier in selected or take.status in ('accepted', 'skipped') for identifier, take in takes.items())
    identifiers: list[str] = sorted(takes, key=len, reverse=True)
    files: list[Path] = []
    for name in MEDIA_FOLDERS:
        folder: Path = contained(output, output / name)
        for path in folder.rglob('*'):
            contained(output, path)
            if not path.is_file():
                continue
            owner: str | None = next((identifier for identifier in identifiers if path.name.startswith(identifier + '-') or path.name == identifier + '.zip'), None)
            if owner in selected or (complete and owner is None):
                files.append(path)
    return files


def publish(source: Path, target: Path) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(prefix='.' + target.name + '.', suffix='.pending', dir=target.parent, delete=False) as handle:
        staging: Path = Path(handle.name)
    try:
        shutil.copyfile(source, staging)
        if digest(source) != digest(staging):
            raise OSError('Published media differs from its source: ' + str(target))
        staging.replace(target)
    finally:
        staging.unlink(missing_ok=True)


def digest(path: Path) -> str:
    with path.open('rb') as handle:
        return hashlib.file_digest(handle, 'sha256').hexdigest()


def accepted_copy(docs: Path, path: Path, take: manifest.Take) -> None:
    expected: str | None = take.published_files.get(path.relative_to(docs).as_posix())
    if expected is None or not path.is_file() or digest(path) != expected:
        raise ValueError('Accepted docs media is missing or changed: ' + str(path))


def accept(output: Path, docs: Path, takes: dict[str, manifest.Take], ids: list[str], ffmpeg: Path, ffprobe: Path) -> dict[str, manifest.Take]:
    if output.resolve().is_relative_to(docs.resolve()) or docs.resolve().is_relative_to(output.resolve()):
        raise ValueError('Run output and docs must be separate folders')
    if not ids:
        return takes
    selected: set[str] = set(ids)
    files: list[tuple[str, Path, Path]] = []
    published: dict[str, dict[str, str]] = {}
    for identifier in ids:
        take: manifest.Take | None = takes.get(identifier)
        if take is None or take.status not in ('exported', 'accepted', 'skipped'):
            raise ValueError('Take is not ready for acceptance: ' + identifier)
        if not NAME.fullmatch(identifier) or take.id != identifier or not NAME.fullmatch(take.skill):
            raise ValueError('Invalid take identity: ' + identifier)
        if take.status == 'skipped':
            continue
        for angle in export_phase.ANGLES:
            target: Path = contained(docs, export_phase.final_clip(docs, take, angle))
            source: Path = target if take.status == 'accepted' else contained(output, export_phase.final_clip(review_root(output), take, angle))
            if take.status == 'accepted':
                accepted_copy(docs, target, take)
            validate_clip(source, ffmpeg, ffprobe)
            if take.status != 'accepted':
                files.append((identifier, source, target))
        for still in take.stills:
            name: str = still['name']
            if not NAME.fullmatch(name):
                raise ValueError('Invalid still name: ' + name)
            local: Path = contained(output, manifest.still_path(output, identifier, name))
            target = contained(docs, docs / 'adapt-assets' / 'demos' / take.skill / local.name)
            source = target if take.status == 'accepted' else local
            if take.status == 'accepted':
                accepted_copy(docs, target, take)
            if not source.is_file() or source.stat().st_size == 0:
                raise ValueError('Missing still: ' + str(source))
            if take.status != 'accepted':
                files.append((identifier, source, target))
    removable: list[Path] = media_files(output, takes, selected)
    for identifier, source, target in files:
        publish(source, target)
    for skill in sorted({takes[identifier].skill for identifier in ids if takes[identifier].status == 'exported'}):
        entries: dict[str, choreography.Entry] = docs_sync.sheet_entries(skill)
        showcase: list[choreography.Entry] = choreography.showcase_entries(entries)
        showcase_ids: set[str] = {entry.id for entry in showcase}
        page: Path = docs_sync.skill_page(docs, choreography.adaptation_ids(docs_sync.ROOT, skill))
        demos: Path = docs / 'adapt-assets' / 'demos' / skill
        adaptations: list[str] = [identifier for identifier in ids if takes[identifier].skill == skill and identifier not in showcase_ids and takes[identifier].status == 'exported']
        docs_sync.sync_page(page, skill, adaptations, functools.partial(docs_sync.clips_present, demos), docs_sync.utc_now(), showcase)
    for identifier, source, target in files:
        source_hash: str = digest(source)
        if source_hash != digest(target):
            raise OSError('Published media changed before cleanup: ' + str(target))
        published.setdefault(identifier, {})[target.relative_to(docs).as_posix()] = source_hash
    updated: dict[str, manifest.Take] = {identifier: dataclasses.replace(take, status='accepted', replay='', published_files=published[identifier]) if identifier in selected and take.status == 'exported' else take
                                        for identifier, take in takes.items()}
    manifest.save(output / 'manifest.json', updated)
    for path in removable:
        contained(output, path).unlink()
    return updated


def discard(output: Path, docs: Path, takes: dict[str, manifest.Take], ids: list[str]) -> dict[str, manifest.Take]:
    if output.resolve().is_relative_to(docs.resolve()) or docs.resolve().is_relative_to(output.resolve()):
        raise ValueError('Run output and docs must be separate folders')
    if not ids:
        return takes
    for identifier in ids:
        if identifier not in takes or takes[identifier].status not in ('recorded', 'exported', 'failed'):
            raise ValueError('Only pending takes can be discarded: ' + identifier)
        take: manifest.Take = takes[identifier]
        if not NAME.fullmatch(identifier) or take.id != identifier or not NAME.fullmatch(take.skill):
            raise ValueError('Invalid take identity: ' + identifier)
    for path in media_files(output, takes, set(ids)):
        contained(output, path).unlink()
    updated: dict[str, manifest.Take] = {identifier: take for identifier, take in takes.items() if identifier not in ids}
    manifest.save(output / 'manifest.json', updated)
    return updated
