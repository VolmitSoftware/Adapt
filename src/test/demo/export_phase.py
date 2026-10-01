import dataclasses
import time
import zipfile
from pathlib import Path
from typing import Callable, Protocol

import editor_state
import encode
import manifest
from studio import BridgeTimeout

BITRATE: int = 40_000_000
WARM_UP_SECONDS: float = 6.0
SPECTATE_SECONDS: float = 1.0
READY_TIMEOUT: float = 120.0
OPEN_RETRY_SECONDS: float = 30.0
EXPORT_TIMEOUT: float = 1800.0
CLOSE_TIMEOUT: float = 60.0
DISCONNECT_RETRY_SECONDS: float = 10.0
ANGLES: tuple[str, ...] = ('pov', 'observer')
LIVE_TRIM_SECONDS: float = manifest.START_TICK / manifest.TICKS_PER_SECOND


class ExportBridge(Protocol):
    def command(self, operation: str, **values: object) -> dict: ...

    def export_status(self) -> dict: ...


class ExportStudio(Protocol):
    bridge: ExportBridge
    game: Path
    output: Path


def wait_status(bridge: ExportBridge, predicate: Callable[[dict], bool], label: str, timeout: float) -> dict:
    deadline: float = time.time() + timeout
    last: dict = {}
    while time.time() < deadline:
        last = bridge.export_status()
        if predicate(last):
            return last
        time.sleep(0.5)
    raise RuntimeError('Timed out waiting for ' + label + '; last status ' + str(last))


def export_one(studio: ExportStudio, take: manifest.Take, angle: str, intermediate: Path, audio: bool) -> None:
    replay: Path = Path(take.replay)
    uuid: str = editor_state.replay_uuid(replay)
    camera: dict | None = None
    if angle == 'pov':
        state: dict = editor_state.pov(replay)
    elif take.camera.get('follow') is True:
        state = editor_state.observer(replay, take.camera, take.actor_uuid)
    else:
        camera = dict(take.camera)
        camera['y'] = float(camera['y']) - manifest.PLAYER_EYE_HEIGHT
        state = editor_state.observer(replay, camera, take.actor_uuid)
    editor_state.write(studio.game, uuid, state)
    open_replay(studio.bridge, replay)
    time.sleep(WARM_UP_SECONDS)
    if angle == 'pov':
        studio.bridge.command('chat', command='spectate ' + take.actor_uuid)
        time.sleep(SPECTATE_SECONDS)
    intermediate.parent.mkdir(parents=True, exist_ok=True)
    intermediate.unlink(missing_ok=True)
    studio.bridge.command('export', name=take.id + '-' + angle, output=str(intermediate), width=encode.WIDTH, height=encode.HEIGHT, fps=encode.FPS,
                          container='MP4', codec='H264', bitrate=BITRATE, audio=audio, noGui=angle != 'pov', camera=camera, startTick=manifest.START_TICK)
    wait_status(studio.bridge, lambda status: bool(status.get('exportStarted')) and not status.get('exporting'), 'export ' + angle, EXPORT_TIMEOUT)
    if not intermediate.is_file() or intermediate.stat().st_size == 0:
        raise RuntimeError('Export produced no video at ' + str(intermediate))
    disconnect_replay(studio.bridge)


def open_replay(bridge: ExportBridge, replay: Path) -> None:
    swallowed: str = send(bridge, 'replay-open', path=str(replay))
    retried: str = ''
    if swallowed:
        try:
            wait_status(bridge, lambda status: bool(status.get('replayOpen')), 'replay open', OPEN_RETRY_SECONDS)
        except RuntimeError:
            retried = send(bridge, 'replay-open', path=str(replay))
    try:
        wait_status(bridge, lambda status: bool(status.get('replayReady')), 'replay ready', READY_TIMEOUT)
    except RuntimeError as failure:
        if not swallowed:
            raise
        raise RuntimeError(str(failure) + '; swallowed ' + '; '.join(text for text in (swallowed, retried) if text)) from failure


def disconnect_replay(bridge: ExportBridge) -> None:
    swallowed: str = send(bridge, 'disconnect')
    if not swallowed:
        wait_closed(bridge, CLOSE_TIMEOUT)
        return
    try:
        wait_closed(bridge, DISCONNECT_RETRY_SECONDS)
        return
    except RuntimeError:
        pass
    retried: str = send(bridge, 'disconnect')
    try:
        wait_closed(bridge, CLOSE_TIMEOUT)
    except RuntimeError as failure:
        raise RuntimeError(str(failure) + '; swallowed ' + '; '.join(text for text in (swallowed, retried) if text)) from failure


def send(bridge: ExportBridge, operation: str, **values: object) -> str:
    try:
        bridge.command(operation, **values)
    except BridgeTimeout as failure:
        return str(failure)
    return ''


def wait_closed(bridge: ExportBridge, timeout: float) -> dict:
    return wait_status(bridge, lambda status: not status.get('replayOpen'), 'replay closed', timeout)


def close_replay(bridge: ExportBridge) -> None:
    status: dict = wait_status(bridge, lambda current: not current.get('exporting'), 'export to stop', EXPORT_TIMEOUT)
    if status.get('replayOpen'):
        disconnect_replay(bridge)


def final_clip(docs_root: Path, take: manifest.Take, angle: str) -> Path:
    return docs_root / 'adapt-assets' / 'demos' / take.skill / (take.id + '-' + angle + '.webm')


def live_source(studio: ExportStudio, take: manifest.Take) -> Path:
    live: Path = manifest.live_pov_path(studio.output, take.id)
    if not live.is_file() or live.stat().st_size == 0:
        raise RuntimeError('Live pov capture missing at ' + str(live) + '; record the take again')
    return live


def export_take(studio: ExportStudio, take: manifest.Take, docs_root: Path, ffmpeg: Path, ffprobe: Path, audio: bool) -> None:
    for angle in ANGLES:
        live: bool = angle == 'pov' and take.pov_capture
        intermediate: Path = live_source(studio, take) if live else studio.output / 'intermediate' / (take.id + '-' + angle + '.mp4')
        if not live:
            export_one(studio, take, angle, intermediate, audio)
        final: Path = final_clip(docs_root, take, angle)
        final.parent.mkdir(parents=True, exist_ok=True)
        encode.run(encode.normalize_command(ffmpeg, intermediate, final, encode.has_audio(ffprobe, intermediate), LIVE_TRIM_SECONDS if live else 0.0))
        thumb: Path = studio.output / 'thumbs' / (take.id + '-' + angle + '.png')
        thumb.parent.mkdir(parents=True, exist_ok=True)
        encode.run(encode.thumbnail_command(ffmpeg, final, thumb, max(0.5, manifest.clip_seconds(take) / 2)))


def export(studio: ExportStudio, takes: dict[str, manifest.Take], ids: list[str], docs_root: Path, ffmpeg: Path, ffprobe: Path,
           manifest_path: Path, audio: bool = True) -> dict[str, manifest.Take]:
    for identifier in ids:
        take: manifest.Take | None = takes.get(identifier)
        if take is None or take.status == 'skipped' or not take.evidence_ok or not take.replay or not Path(take.replay).is_file():
            continue
        try:
            export_take(studio, take, docs_root, ffmpeg, ffprobe, audio)
        except (RuntimeError, OSError, zipfile.BadZipFile) as failure:
            for angle in ANGLES:
                final_clip(docs_root, take, angle).unlink(missing_ok=True)
            takes[identifier] = dataclasses.replace(take, status='failed', export_error=str(failure))
            manifest.save(manifest_path, takes)
            print('[EXPORT-FAIL] ' + identifier + ' ' + str(failure), flush=True)
            close_replay(studio.bridge)
            continue
        takes[identifier] = dataclasses.replace(take, status='exported', export_error='')
        manifest.save(manifest_path, takes)
        print('[EXPORT] ' + identifier, flush=True)
    return takes
