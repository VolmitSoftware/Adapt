import json
from dataclasses import dataclass, field
from pathlib import Path

VERBS: frozenset[str] = frozenset({'look', 'lookAt', 'keys', 'press', 'tap', 'click', 'wait', 'waitFor', 'command', 'hit', 'slot', 'window',
                                   'anvilName'})
KEYS: frozenset[str] = frozenset({'forward', 'back', 'left', 'right', 'jump', 'sneak', 'sprint', 'attack', 'use', 'swapHands', 'drop', 'inventory'})
CLICK_KEYS: tuple[str, ...] = ('attack', 'use', 'swapHands', 'drop', 'inventory')
MIN_LEASE_TICKS: int = 1
MAX_LEASE_TICKS: int = 200
HOTBAR_SLOTS: int = 9
WINDOW_ACTIONS: tuple[str, ...] = ('list', 'click', 'shift', 'drop', 'hover', 'button', 'close')
WINDOW_SLOT_ACTIONS: frozenset[str] = frozenset({'click', 'shift', 'drop', 'hover'})
WINDOW_BUTTONS: tuple[int, ...] = (0, 1)
MAX_ANVIL_NAME: int = 50
OPPONENT: str = 'opponent'
OPPONENT_VERBS: frozenset[str] = frozenset({'look', 'lookAt', 'keys', 'press', 'tap', 'click', 'slot', 'window', 'anvilName'})
OPPONENT_NAME: str = '{opponent}'
OPPONENT_MISSING: str = 'the entry does not set "opponent": true'
WORLD_FIELD: str = 'world'
OVERWORLD: str = 'iris:adapt_demo'
NETHER: str = 'minecraft:the_nether'
WORLDS: tuple[str, ...] = (OVERWORLD, NETHER)


@dataclass(frozen=True)
class Entry:
    id: str
    set: str
    level: int
    pre: list[str]
    beats: list[dict]
    expect_sound: str | None
    expect_particle: str | None
    max_ticks: int
    camera: dict | None
    prelude: list[dict] = field(default_factory=list)
    skip: str = ''
    expect_visual: str | None = None
    opponent: bool = False
    pov_capture: bool = False
    plugins: tuple[str, ...] = ()


@dataclass(frozen=True)
class Sheet:
    world: str
    entries: dict[str, Entry]


def adaptation_ids(root: Path, skill: str) -> set[str]:
    data: dict = json.loads((root / 'src' / 'test' / 'gameplay' / 'adaptation-matrix.json').read_text())
    return {entry['name'] for entry in data['adaptations'] if entry['skill'] == skill}


def load_skill(root: Path, skill: str) -> Sheet:
    path: Path = root / 'src' / 'test' / 'demo' / 'sheets' / (skill + '.json')
    raw: dict = json.loads(path.read_text())
    world: object = raw.pop(WORLD_FIELD, OVERWORLD)
    entries: dict[str, Entry] = {}
    for identifier, body in raw.items():
        expect: dict = body.get('expect', {})
        plugins: object = body.get('plugins', [])
        if not isinstance(plugins, list) or not all(isinstance(plugin, str) for plugin in plugins):
            raise ValueError(identifier + ': plugins must be a list of names')
        entries[identifier] = Entry(
            id=identifier,
            set=body.get('set', ''),
            level=int(body.get('level', 0)),
            pre=list(body.get('pre', [])),
            beats=list(body.get('beats', [])),
            expect_sound=expect.get('sound'),
            expect_particle=expect.get('particle'),
            expect_visual=expect.get('visual'),
            max_ticks=int(body.get('maxTicks', 400)),
            camera=body.get('camera'),
            prelude=list(body.get('prelude', [])),
            skip=str(body.get('skip', '')),
            opponent=body.get('opponent') is True,
            pov_capture=body.get('povCapture') is True,
            plugins=tuple(plugins),
        )
    return Sheet(world=world if isinstance(world, str) else json.dumps(world), entries=entries)


def validate(entries: dict[str, Entry], matrix_ids: set[str]) -> list[str]:
    problems: list[str] = []
    for missing in sorted(matrix_ids - set(entries)):
        problems.append('missing choreography for ' + missing)
    for extra in sorted(set(entries) - matrix_ids):
        problems.append('choreography for unknown adaptation ' + extra)
    for identifier, entry in entries.items():
        for plugin in entry.plugins:
            if plugin != 'Gloss':
                problems.append(identifier + ': unsupported plugin ' + plugin)
        if entry.skip:
            if entry.beats:
                problems.append(identifier + ': skip cannot be combined with beats')
            continue
        if not entry.set:
            problems.append(identifier + ': set is required')
        problems.extend(identifier + ': ' + problem for problem in expect_problems(entry))
        if not entry.beats:
            problems.append(identifier + ': beats is empty')
        for index, command in enumerate(entry.pre):
            if OPPONENT_NAME in command and not entry.opponent:
                problems.append(identifier + ': pre ' + str(index) + ' names ' + OPPONENT_NAME + ', but ' + OPPONENT_MISSING)
        for index, beat in enumerate(entry.prelude):
            problems.extend(identifier + ': prelude ' + str(index) + ' ' + problem for problem in beat_problems(beat, entry.opponent))
        for index, beat in enumerate(entry.beats):
            problems.extend(identifier + ': beat ' + str(index) + ' ' + problem for problem in beat_problems(beat, entry.opponent))
    return problems


def world_problems(world: str) -> list[str]:
    if world in WORLDS:
        return []
    return ['world must be ' + ', '.join(WORLDS[:-1]) + ' or ' + WORLDS[-1] + ', not ' + world]


def expect_problems(entry: Entry) -> list[str]:
    heard: bool = entry.expect_sound is not None or entry.expect_particle is not None
    visual: object = entry.expect_visual
    if visual is None:
        return [] if heard else ['expect.sound, expect.particle or expect.visual is required']
    if not isinstance(visual, str) or not visual.strip():
        return ['expect.visual must be a non-empty string']
    if heard:
        return ['expect.visual cannot be combined with expect.sound or expect.particle']
    return []


def beat_problems(beat: dict, opponent: bool = False) -> list[str]:
    problems: list[str] = []
    verb: str = str(beat.get('verb', ''))
    if verb not in VERBS:
        problems.append('has unknown verb ' + verb)
    if 'actor' in beat:
        problems.extend(role_problems(verb, beat['actor'], opponent))
    if verb == 'command' and OPPONENT_NAME in str(beat.get('text', '')) and not opponent:
        problems.append('names ' + OPPONENT_NAME + ', but ' + OPPONENT_MISSING)
    for key in beat.get('hold', []):
        if key not in KEYS:
            problems.append('holds unknown key ' + key)
    for key in beat.get('off', []):
        if key not in KEYS:
            problems.append('releases unknown key ' + key)
    if verb in ('keys', 'press') and not beat.get('hold'):
        problems.append(verb + ' needs a non-empty hold')
    if 'off' in beat and verb != 'press':
        problems.append('off is only allowed on press')
    if (verb in ('keys', 'press') or (verb == 'tap' and 'ticks' in beat)) and not lease_ticks(beat.get('ticks')):
        problems.append(verb + ' ticks must be an integer between ' + str(MIN_LEASE_TICKS) + ' and ' + str(MAX_LEASE_TICKS))
    if verb in ('look', 'lookAt') and 'ticks' in beat and not (integer(beat['ticks']) and 1 <= beat['ticks'] <= 200):
        problems.append(verb + ' ticks must be an integer between 1 and 200')
    if verb == 'tap' and str(beat.get('key', '')) not in KEYS:
        problems.append('taps unknown key ' + str(beat.get('key')))
    if verb == 'click' and str(beat.get('key', '')) not in CLICK_KEYS:
        problems.append('click key must be ' + ', '.join(CLICK_KEYS[:-1]) + ' or ' + CLICK_KEYS[-1] + ', not ' + str(beat.get('key')))
    if verb == 'waitFor' and beat.get('sound') is None and beat.get('particle') is None:
        problems.append('waitFor needs a sound or particle')
    if verb == 'waitFor' and 'timeout' in beat and not (integer(beat['timeout']) and beat['timeout'] >= 1):
        problems.append('waitFor timeout must be an integer of at least 1')
    if verb == 'slot' and not hotbar_index(beat.get('index')):
        problems.append('slot index must be an integer from 0 to ' + str(HOTBAR_SLOTS - 1))
    if verb == 'window':
        problems.extend(window_problems(beat))
    if verb == 'anvilName' and not anvil_name(beat.get('text')):
        problems.append('anvilName text must be a string of 1 to ' + str(MAX_ANVIL_NAME) + ' characters')
    return problems


def role_problems(verb: str, role: object, opponent: bool) -> list[str]:
    if role != OPPONENT:
        return ['actor must be ' + OPPONENT + ', not ' + str(role)]
    if verb not in OPPONENT_VERBS:
        return [verb + ' cannot target the opponent']
    return [] if opponent else ['targets the opponent, but ' + OPPONENT_MISSING]


def window_problems(beat: dict) -> list[str]:
    action: object = beat.get('action')
    if not isinstance(action, str) or action not in WINDOW_ACTIONS:
        return ['window action must be ' + ', '.join(WINDOW_ACTIONS[:-1]) + ' or ' + WINDOW_ACTIONS[-1] + ', not ' + str(action)]
    if action == 'button':
        return [] if integer(beat.get('index')) and beat['index'] >= 0 else ['window button needs a control index of at least 0']
    if action not in WINDOW_SLOT_ACTIONS:
        return ['window ' + action + ' takes no slot or button'] if 'slot' in beat or 'button' in beat else []
    problems: list[str] = []
    if not (integer(beat.get('slot')) and beat['slot'] >= 0):
        problems.append('window ' + action + ' needs a slot index of at least 0')
    if 'button' in beat and not (integer(beat['button']) and beat['button'] in WINDOW_BUTTONS):
        problems.append('window button must be 0 or 1')
    return problems


def integer(value: object) -> bool:
    return isinstance(value, int) and not isinstance(value, bool)


def lease_ticks(value: object) -> bool:
    return integer(value) and MIN_LEASE_TICKS <= value <= MAX_LEASE_TICKS


def hotbar_index(value: object) -> bool:
    return integer(value) and 0 <= value < HOTBAR_SLOTS


def anvil_name(value: object) -> bool:
    return isinstance(value, str) and 1 <= len(value) <= MAX_ANVIL_NAME
