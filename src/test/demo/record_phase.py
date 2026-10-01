import dataclasses
import json
import shutil
import time
from pathlib import Path
from typing import Callable, Protocol

import beats
import choreography
import encode
import evidence
import manifest
from studio import BridgeTimeout

PRE_SETTLE_TICKS: int = 60
SETTLE_TICKS: int = 20
REPLAY_TIMEOUT: float = 60.0
STOP_STATE_TIMEOUT: float = 30.0
BRIDGE_RETRY_SECONDS: float = 1.0
LABELS: dict[str, str] = {'recorded': '[TAKE] ', 'failed': '[FAIL] ', 'skipped': '[SKIP] '}


class BridgeLost(RuntimeError):
    pass


class RecordStudio(Protocol):
    rcon: beats.RconClient
    bridge: beats.BridgeClient
    game: Path
    origin: tuple[float, float, float]
    actor: str
    opponent: str
    opponent_bridge: beats.BridgeClient | None

    def await_tps(self) -> None:
        ...


def wait_ticks_factory(bridge: beats.BridgeClient, record_start: int | None = None, max_ticks: int = 0) -> Callable[[int], None]:
    def wait_ticks(count: int) -> None:
        target: int = int(bridge.state()['ticks']) + count
        while True:
            current: int = int(bridge.state()['ticks'])
            if record_start is not None and current > record_start + max_ticks:
                raise beats.BeatTimeout('maxTicks ' + str(max_ticks) + ' exceeded')
            if current >= target:
                return
            time.sleep(0.03)
    return wait_ticks


def events_since_factory(bridge: beats.BridgeClient) -> Callable[[int], list[dict]]:
    def events_since(tick: int) -> list[dict]:
        return [event for event in bridge.state().get('events', []) if int(event.get('tick', -1)) >= tick]
    return events_since


def parse_set_reply(reply: str) -> dict:
    marker: str = 'ADAPT_QA DEMO SET '
    if marker not in reply:
        raise RuntimeError('Unexpected set reply: ' + reply)
    body: str = reply.split(marker, 1)[1]
    return json.loads(body[body.index('{'):])


def replay_files(game: Path) -> set[Path]:
    folder: Path = game / 'flashback' / 'replays'
    return set(folder.glob('*.zip')) if folder.is_dir() else set()


def newest_replay(game: Path, known: set[Path]) -> Path | None:
    fresh: list[Path] = [path for path in replay_files(game) if path not in known]
    if not fresh:
        return None
    return max(fresh, key=lambda path: path.stat().st_mtime)


def wait_replay(game: Path, known: set[Path], timeout: float) -> Path:
    deadline: float = time.time() + timeout
    while time.time() < deadline:
        found: Path | None = newest_replay(game, known)
        if found is not None:
            size: int = found.stat().st_size
            time.sleep(1.0)
            if found.stat().st_size == size and size > 0:
                return found
        time.sleep(0.5)
    raise RuntimeError('No replay appeared within ' + str(timeout) + ' s')


def keep_replay(replay: Path, folder: Path, identifier: str) -> Path:
    folder.mkdir(parents=True, exist_ok=True)
    target: Path = folder / (identifier + '.zip')
    shutil.move(str(replay), str(target))
    return target


def settled_state(bridge: beats.BridgeClient, timeout: float) -> dict:
    deadline: float = time.monotonic() + timeout
    while True:
        try:
            return bridge.state()
        except BridgeTimeout:
            if time.monotonic() >= deadline:
                raise
        time.sleep(0.5)


def stop_recording(bridge: beats.BridgeClient) -> dict:
    try:
        return bridge.command('record', action='stop')
    except BridgeTimeout:
        state: dict = settled_state(bridge, STOP_STATE_TIMEOUT)
        if state.get('recording'):
            return bridge.command('record', action='stop')
        return state


def start_capture(bridge: beats.BridgeClient, path: Path, ffmpeg: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    bridge.command('capture', action='start', path=str(path), ffmpeg=str(ffmpeg), width=encode.WIDTH, height=encode.HEIGHT, fps=encode.FPS)


def stop_capture(bridge: beats.BridgeClient) -> dict:
    try:
        return bridge.command('capture', action='stop')
    except BridgeTimeout:
        state: dict = settled_state(bridge, STOP_STATE_TIMEOUT)
        if state.get('capturing'):
            return bridge.command('capture', action='stop')
        return state


def capture_pacing(identifier: str, state: dict) -> str:
    frames: int = int(state.get('captureFrames', 0))
    seconds: float = float(state.get('captureSeconds', 0.0))
    rate: str = format(frames / seconds if seconds > 0 else 0.0, '.1f')
    return ('[CAPTURE] ' + identifier + ' ' + str(frames) + ' frames in ' + format(seconds, '.2f') + ' s (' + rate + ' fps), source '
            + str(state.get('captureSource')))


def placed_camera(camera: dict, origin: tuple[float, float, float]) -> dict:
    if camera.get('follow') is True:
        return {'x': float(camera['x']), 'y': float(camera['y']), 'z': float(camera['z']), 'yaw': float(camera['yaw']), 'pitch': float(camera['pitch']),
                'follow': True}
    return {'x': origin[0] + float(camera['x']), 'y': origin[1] + float(camera['y']), 'z': origin[2] + float(camera['z']),
            'yaw': float(camera['yaw']), 'pitch': float(camera['pitch'])}


def prepare_take(studio: RecordStudio, entry: choreography.Entry, skill: str) -> dict:
    rcon: beats.RconClient = studio.rcon
    actor: str = studio.actor
    if entry.opponent and studio.opponent_bridge is None:
        raise RuntimeError(entry.id + ' needs the opponent client, but it is not running')
    set_info: dict = parse_set_reply(rcon.command('adaptqa demo set ' + entry.set))
    rcon.command('adaptqa demo actor ' + actor)
    if entry.opponent:
        rcon.command('adaptqa demo actor ' + studio.opponent + ' opponent')
    elif studio.opponent_bridge is not None:
        rcon.command('adaptqa demo park ' + studio.opponent)
    rcon.command('adaptqa demo sparring')
    rcon.command('adapt clear adaptations player=' + actor)
    rcon.command('adapt claim-adaptation ' + skill + ':' + entry.id + ' ' + str(entry.level) + ' force=true player=' + actor)
    for command in entry.pre:
        rcon.command(beats.fill_names(command, actor, studio.opponent))
    rcon.command('adaptqa demo sync ' + actor)
    if entry.opponent:
        rcon.command('adaptqa demo sync ' + studio.opponent)
    return set_info


def release_keys(studio: RecordStudio) -> None:
    studio.bridge.command('release')
    if studio.opponent_bridge is not None:
        studio.opponent_bridge.command('release')


def take_once(studio: RecordStudio, entry: choreography.Entry, skill: str, attempt: int, output: Path, ffmpeg: Path | None) -> manifest.Take:
    if entry.pov_capture and ffmpeg is None:
        raise RuntimeError(entry.id + ' needs an ffmpeg path for povCapture')
    bridge: beats.BridgeClient = studio.bridge
    set_info: dict = prepare_take(studio, entry, skill)
    wait_ticks: Callable[[int], None] = wait_ticks_factory(bridge)
    context: beats.BeatContext = beats.BeatContext(bridge=bridge, rcon=studio.rcon, actor=studio.actor, origin=studio.origin,
                                                   wait_ticks=wait_ticks, events_since=events_since_factory(bridge), opponent=studio.opponent,
                                                   opponent_bridge=studio.opponent_bridge)
    wait_ticks(PRE_SETTLE_TICKS)
    beats.run_steps(entry.prelude, context)
    known: set[Path] = replay_files(studio.game)
    bridge.command('clear-events')
    start_tick: int = int(bridge.command('record', action='start')['recordStartTick'])
    if entry.pov_capture:
        try:
            start_capture(bridge, manifest.live_pov_path(output, entry.id), ffmpeg)
        except RuntimeError:
            stop_recording(bridge)
            raise
    wait_ticks(SETTLE_TICKS)
    bounded: beats.BeatContext = dataclasses.replace(context, wait_ticks=wait_ticks_factory(bridge, start_tick, entry.max_ticks))
    timeout: str | None = None
    try:
        beats.run_beats(entry, bounded)
    except beats.BeatTimeout as failure:
        timeout = str(failure)
    finally:
        release_keys(studio)
    wait_ticks(SETTLE_TICKS)
    stopped: dict = stop_recording(bridge)
    if entry.pov_capture:
        print(capture_pacing(entry.id, stop_capture(bridge)), flush=True)
    stop_tick: int = int(stopped['recordStopTick'])
    result: evidence.EvidenceResult = evidence.check(stopped.get('events', []), start_tick, stop_tick, entry.expect_sound, entry.expect_particle,
                                                     entry.expect_visual)
    replay: Path = keep_replay(wait_replay(studio.game, known, REPLAY_TIMEOUT), output / 'replays', entry.id)
    ok: bool = timeout is None and result.ok
    camera: dict = placed_camera(entry.camera, studio.origin) if entry.camera is not None else set_info['camera']
    return manifest.Take(id=entry.id, skill=skill, set=entry.set, level=entry.level, replay=str(replay), start_tick=start_tick, stop_tick=stop_tick,
                         evidence_ok=ok, evidence_detail=result.detail if timeout is None else timeout, takes=attempt, camera=camera,
                         actor_uuid=str(stopped.get('uuid', '')), status='recorded' if ok else 'failed', visual=entry.expect_visual or '',
                         pov_capture=entry.pov_capture)


def unrecorded_take(entry: choreography.Entry, skill: str, attempt: int, status: str, detail: str) -> manifest.Take:
    return manifest.Take(id=entry.id, skill=skill, set=entry.set, level=entry.level, replay='', start_tick=-1, stop_tick=-1, evidence_ok=False,
                         evidence_detail=detail, takes=attempt, camera={}, actor_uuid='', status=status, visual=entry.expect_visual or '',
                         pov_capture=entry.pov_capture)


def bridge_answers(bridge: beats.BridgeClient) -> bool:
    for attempt in range(2):
        try:
            bridge.state()
            return True
        except RuntimeError:
            return True
        except OSError:
            if attempt == 0:
                time.sleep(BRIDGE_RETRY_SECONDS)
    return False


def abandon_take(studio: RecordStudio, failure: Exception) -> str:
    bridge: beats.BridgeClient = studio.bridge
    if not bridge_answers(bridge):
        raise BridgeLost('Client bridge stopped answering after: ' + str(failure)) from failure
    try:
        release_keys(studio)
        state: dict = bridge.state()
        if state.get('recording'):
            stop_recording(bridge)
        if state.get('capturing'):
            stop_capture(bridge)
    except (RuntimeError, OSError) as cleanup:
        return str(failure) + '; cleanup failed: ' + str(cleanup)
    return str(failure)


def shoot(studio: RecordStudio, entry: choreography.Entry, skill: str, retakes: int, output: Path, ffmpeg: Path | None) -> manifest.Take:
    attempt: int = 1
    while True:
        try:
            take: manifest.Take = take_once(studio, entry, skill, attempt, output, ffmpeg)
        except (RuntimeError, OSError, AssertionError) as failure:
            return unrecorded_take(entry, skill, attempt, 'failed', abandon_take(studio, failure))
        if take.status == 'recorded' or attempt >= retakes:
            return take
        attempt += 1


def record(studio: RecordStudio, entries: dict[str, choreography.Entry], ids: list[str], manifest_path: Path, skill: str, retakes: int = 3,
           ffmpeg: Path | None = None) -> dict[str, manifest.Take]:
    if retakes < 1:
        raise ValueError('retakes must be at least 1')
    takes: dict[str, manifest.Take] = manifest.load(manifest_path)
    if any(not entries[identifier].skip for identifier in ids):
        studio.await_tps()
    for identifier in ids:
        entry: choreography.Entry = entries[identifier]
        take: manifest.Take = unrecorded_take(entry, skill, 0, 'skipped', entry.skip) if entry.skip else shoot(studio, entry, skill, retakes, manifest_path.parent, ffmpeg)
        takes[identifier] = take
        manifest.save(manifest_path, takes)
        print(LABELS[take.status] + identifier + ' ' + take.evidence_detail + ' (takes=' + str(take.takes) + ')', flush=True)
    return takes
