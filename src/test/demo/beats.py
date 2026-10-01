import math
from dataclasses import dataclass
from typing import Callable, Protocol

import choreography
import evidence
import manifest


class BeatTimeout(RuntimeError):
    pass


class BridgeClient(Protocol):
    def state(self) -> dict: ...

    def command(self, operation: str, **values: object) -> dict: ...


class RconClient(Protocol):
    def command(self, text: str) -> str: ...


@dataclass
class BeatContext:
    bridge: BridgeClient
    rcon: RconClient
    actor: str
    origin: tuple[float, float, float]
    wait_ticks: Callable[[int], None]
    events_since: Callable[[int], list[dict]]
    opponent: str = ''
    opponent_bridge: BridgeClient | None = None


def look_angles(eye: tuple[float, float, float], target: tuple[float, float, float]) -> tuple[float, float]:
    dx: float = target[0] - eye[0]
    dy: float = target[1] - eye[1]
    dz: float = target[2] - eye[2]
    yaw: float = math.degrees(math.atan2(-dx, dz))
    pitch: float = math.degrees(-math.atan2(dy, math.hypot(dx, dz)))
    return yaw, pitch


def run_beats(entry: choreography.Entry, ctx: BeatContext) -> None:
    run_steps(entry.beats, ctx)


def run_steps(steps: list[dict], ctx: BeatContext) -> None:
    for beat in steps:
        verb: str = beat['verb']
        bridge: BridgeClient = client_for(beat, ctx)
        if verb == 'look':
            turn(bridge, float(beat['yaw']), float(beat['pitch']), int(beat.get('ticks', 0)), ctx.wait_ticks)
        elif verb == 'lookAt':
            look_at(beat, bridge, ctx.origin, ctx.wait_ticks)
        elif verb == 'keys':
            hold_keys(bridge, ctx.wait_ticks, list(beat['hold']), int(beat['ticks']))
        elif verb == 'press':
            press_keys(bridge, list(beat['hold']), list(beat.get('off', [])), int(beat['ticks']))
        elif verb == 'tap':
            hold_keys(bridge, ctx.wait_ticks, [str(beat['key'])], int(beat.get('ticks', 2)))
        elif verb == 'click':
            bridge.command('click', key=beat['key'])
        elif verb == 'wait':
            ctx.wait_ticks(int(beat['ticks']))
        elif verb == 'waitFor':
            wait_for(beat, ctx)
        elif verb == 'command':
            ctx.rcon.command(fill_names(str(beat['text']), ctx.actor, ctx.opponent))
        elif verb == 'hit':
            ctx.rcon.command('adaptqa demo hit ' + ctx.actor + ' ' + str(beat['amount']))
        elif verb == 'slot':
            bridge.command('slot', index=int(beat['index']))
        elif verb == 'window':
            window(beat, bridge, ctx.wait_ticks)
        elif verb == 'anvilName':
            bridge.command('anvil-name', text=str(beat['text']))
        else:
            raise ValueError('Unknown beat verb ' + verb)


def client_for(beat: dict, ctx: BeatContext) -> BridgeClient:
    if beat.get('actor') != choreography.OPPONENT:
        return ctx.bridge
    if ctx.opponent_bridge is None:
        raise RuntimeError(str(beat.get('verb')) + ' beat targets the opponent, but no opponent client is running')
    return ctx.opponent_bridge


def fill_names(text: str, actor: str, opponent: str) -> str:
    return text.replace('{actor}', actor).replace(choreography.OPPONENT_NAME, opponent)


def look_at(beat: dict, bridge: BridgeClient, origin: tuple[float, float, float], wait_ticks: Callable[[int], None]) -> None:
    position: dict = bridge.state()['position']
    eye: tuple[float, float, float] = (position['x'], position['y'] + manifest.PLAYER_EYE_HEIGHT, position['z'])
    offset: list[float] = beat['offset']
    target: tuple[float, float, float] = (origin[0] + offset[0], origin[1] + offset[1], origin[2] + offset[2])
    angles: tuple[float, float] = look_angles(eye, target)
    turn(bridge, angles[0], angles[1], int(beat.get('ticks', 0)), wait_ticks)


def turn(bridge: BridgeClient, yaw: float, pitch: float, ticks: int, wait_ticks: Callable[[int], None]) -> None:
    if ticks == 0:
        bridge.command('look', yaw=yaw, pitch=pitch)
        return
    bridge.command('look', yaw=yaw, pitch=pitch, ticks=ticks)
    wait_motion(bridge, 'turning', ticks, wait_ticks)


def wait_motion(bridge: BridgeClient, field: str, ticks: int, wait_ticks: Callable[[int], None]) -> None:
    wait_ticks(ticks)
    for attempt in range(5):
        if not bridge.state().get(field, False):
            return
        if attempt < 4:
            wait_ticks(1)
    raise BeatTimeout(field + ' did not finish within its animation budget')


def hold_keys(bridge: BridgeClient, wait_ticks: Callable[[int], None], names: list[str], ticks: int) -> None:
    bridge.command('keys', **{name: True for name in names}, leaseTicks=ticks)
    wait_ticks(ticks)
    bridge.command('release')


def press_keys(bridge: BridgeClient, hold: list[str], off: list[str], ticks: int) -> None:
    states: dict[str, bool] = {name: True for name in hold}
    states.update({name: False for name in off})
    bridge.command('keys', **states, leaseTicks=ticks)


def window(beat: dict, bridge: BridgeClient, wait_ticks: Callable[[int], None]) -> None:
    values: dict[str, int] = {name: int(beat[name]) for name in ('slot', 'button', 'index') if name in beat}
    action: str = str(beat['action'])
    if action in choreography.WINDOW_SLOT_ACTIONS:
        values['containerId'] = move_to_slot(bridge, values['slot'], wait_ticks)
        if action == 'hover':
            wait_ticks(12)
            return
        wait_ticks(1)
    elif action == 'button':
        values['containerId'] = move_to_slot(bridge, values['index'], wait_ticks, 'controls')
        wait_ticks(1)
    bridge.command('window', action=action, **values)
    if action in choreography.WINDOW_SLOT_ACTIONS or action == 'button':
        wait_ticks(2)


def move_to_slot(bridge: BridgeClient, index: int, wait_ticks: Callable[[int], None], targets: str = 'slots') -> int:
    state: dict = bridge.state()
    container: dict | None = state.get('container')
    if container is None:
        raise RuntimeError('Inventory interaction requires an open container')
    slot: dict | None = next((slot for slot in container.get(targets, []) if slot['index'] == index), None)
    if slot is None:
        raise RuntimeError('Open container has no visible ' + targets + ' entry ' + str(index))
    cursor: dict = state['cursor']
    start_x: float = float(cursor['x'])
    start_y: float = float(cursor['y'])
    delta_x: float = float(slot['screenX']) - start_x
    delta_y: float = float(slot['screenY']) - start_y
    ticks: int = max(2, min(6, math.ceil(math.hypot(delta_x, delta_y) / 36.0)))
    container_id: int = int(container['id'])
    bridge.command('cursor', x=float(slot['screenX']), y=float(slot['screenY']), containerId=container_id, ticks=ticks)
    wait_motion(bridge, 'cursorMoving', ticks, wait_ticks)
    return container_id


def wait_for(beat: dict, ctx: BeatContext) -> None:
    start_tick: int = int(ctx.bridge.state()['ticks'])
    timeout: int = int(beat.get('timeout', 100))
    sound: str | None = beat.get('sound')
    particle: str | None = beat.get('particle')
    waited: int = 0
    while not any(matches(event, sound, particle) for event in ctx.events_since(start_tick)):
        if waited >= timeout:
            raise BeatTimeout('waitFor ' + str(sound or particle) + ' timed out after ' + str(timeout) + ' ticks')
        ctx.wait_ticks(1)
        waited += 1


def matches(event: dict, sound: str | None, particle: str | None) -> bool:
    if sound is not None and evidence.named(event, 'sound', sound):
        return True
    return particle is not None and evidence.named(event, 'particle', particle)
