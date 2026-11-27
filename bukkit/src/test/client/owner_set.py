"""Owner comparison set: the same player progress shown by the Adapt client mod (native) and by a vanilla client with the
server pack (pack), one Paper server and hidden client per mode, screenshots side by side in one folder.

    python3 bukkit/src/test/client/owner_set.py [--output DIR] [--server-adapt JAR] [--client-adapt JAR] [--only SHOT,...]

Shots (SHOTS): Skills pages 1 and 2, Herbalism, Hunter and Chronos, the adaptation page's three tabs, Mutate lead and
partner picks, a mutated skill on both leaves, and a level-up toast with the HUD strip and no menu open; pack-only shots
have no native twin because the native spread shows the same without clicking. Native at GUI scale auto on 1920x1080
(<shot>-native.png); pack at GUI scale auto on 1920x1080 and 2560x1440 windows (<shot>-pack-<width>x<height>.png) and at
scales 2 and 3 on 1920x1080 (<shot>-pack-gui2.png, -gui3.png); on pack skill pages and the adaptation page one slot
hovered with its tooltip (<shot>-pack-hover.png); for a shot with frames, the same pack page again after each delay in
ticks (<shot>-pack-t<ticks>.png). Also commands.json with every console reply and build.txt with the checkout commit,
the jars' SHA-1 and the pack each server baked. Stops when a server baked a pack smaller than --min-pack-mb, which means
a stale jar.
"""
from __future__ import annotations

import argparse
from dataclasses import dataclass
import hashlib
import json
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path
from typing import Callable

HERE: Path = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE / 'ui'))
sys.path.insert(0, str(HERE))
import acceptance  # noqa: E402
import pack_scenarios  # noqa: E402
import uiclient  # noqa: E402

WIDTH: int = 1920
HEIGHT: int = 1080


@dataclass(frozen=True)
class Variant:
    """A window size and GUI scale (0 is auto) a shot is taken at, and the suffix of its file name."""
    suffix: str
    width: int
    height: int
    scale: int


VARIANTS: dict[str, tuple[Variant, ...]] = {
    'native': (Variant('', WIDTH, HEIGHT, 0),),
    'pack': (Variant('-1920x1080', WIDTH, HEIGHT, 0), Variant('-2560x1440', 2560, 1440, 0), Variant('-gui2', WIDTH, HEIGHT, 2),
             Variant('-gui3', WIDTH, HEIGHT, 3)),
}
TOASTS_SETTLE_SECONDS: float = 45.0
BAKED: re.Pattern = re.compile(r'Adapt pack for (\S+) in [\d.]+ s: ([\d.]+) MB, sha1 ([0-9a-f]{40})')
HIDDEN_CHAT: str = 'chatVisibility:2'
NO_CLOUDS: str = 'renderClouds:"false"'
MUTATE_PAIR: str = 'adapt mutate lead=chronos partner=hunter'
MUTATED: str = 'Feral Timekeeper'
CARD_PAGES: int = 4


@dataclass(frozen=True)
class Step:
    """One action on a pack page before its shot: press the book control at a layout role (screen, role, index), or
    press the card named name, turning the page with screen's foot.next until it shows."""
    layout: tuple[str, str, int] | None = None
    card: str = ''
    pages: str = ''


@dataclass(frozen=True)
class Shot:
    """One screenshot: before_command run first if set, then the menu command opens, then on the pack page the steps,
    then the shot, and with hover a second pack shot pointing at the slot named hover. Native-only steps do not exist:
    a shot whose steps the native screens show without clicking (recipes, settings, both leaves) is the opened native
    page, and native=False leaves pack-only shots out. A once shot runs its command one time (mutating a pair cannot be
    repeated) and is shot at every variant without closing. A notice shot opens no menu: its command is a console
    command with {level} replaced by a new level per variant, run right after digging the block underfoot with a
    shovel earns XP, shot once the toast and the HUD strip arrive. frames
    are delays in ticks after the first pack shot at which the same page is shot again."""
    name: str
    command: str
    steps: tuple[Step, ...] = ()
    hover: str = ''
    native: bool = True
    once: bool = False
    before_command: str = ''
    notice: bool = False
    frames: tuple[int, ...] = ()


SHOTS: tuple[Shot, ...] = (
    Shot('skills', 'adapt gui target=main'),
    Shot('skills-page2', 'adapt gui target=main', (Step(layout=('skills', 'foot.next', 0)),), native=False),
    Shot('herbalism', 'adapt gui target=skill:herbalism', hover='Bee Shepherd', frames=(10, 40)),
    Shot('hunter', 'adapt gui target=skill:hunter', hover='Adrenaline'),
    Shot('chronos', 'adapt gui target=skill:chronos', hover='Accelerat'),
    Shot('adaptation-levels', 'adapt gui target=adaptation:hunter/adrenaline', hover='Level III'),
    Shot('adaptation-recipes', 'adapt gui target=adaptation:herbalism/myconid', (Step(layout=('adaptation.levels', 'foot.tabs', 1)),)),
    Shot('adaptation-settings', 'adapt gui target=adaptation:hunter/resistance', (Step(layout=('adaptation.levels', 'foot.tabs', 2)),)),
    Shot('mutate-lead', 'adapt mutate'),
    Shot('mutate-partner', 'adapt mutate', (Step(card='Chronos'),), native=False),
    Shot('mutated', MUTATE_PAIR, once=True),
    Shot('mutated-leaf2', 'adapt gui target=main', (Step(card=MUTATED, pages='skills'), Step(layout=('skill', 'foot.next', 0))),
         native=False),
    Shot('toast-hud', 'adapt claim-skill skillTarget=agility level={level} player=' + uiclient.PLAYER, notice=True),
)
NOTICE_LEVEL: int = 30
NOTICE_SETTLE_SECONDS: float = 0.8
DIG_TICKS: int = 12
MUTATE_LEVELS: dict[str, int] = {'chronos': 60, 'hunter': 60}
SKILLS: dict[str, int] = {'herbalism': 46, 'hunter': 27, 'chronos': 23, 'agility': 24, 'brewing': 35, 'excavation': 27,
                          'architect': 12, 'pickaxe': 18, 'stealth': 31}
ADAPTATIONS: dict[str, int] = {'herbalism/bee_shepherd': 4, 'herbalism/luck': 2, 'herbalism/growth_aura': 2, 'herbalism/myconid': 1,
                               'hunter/big_game': 3, 'hunter/adrenaline': 2, 'hunter/speed': 4, 'chronos/accelerate': 2}


def progress(session: uiclient.Session) -> list[dict[str, str]]:
    replies: list[dict[str, str]] = []
    for skill, level in SKILLS.items():
        command: str = f'adapt claim-skill skillTarget={skill} level={level} player={uiclient.PLAYER}'
        replies.append({'command': command, 'reply': session.rcon(command)})
    for adaptation, level in ADAPTATIONS.items():
        command = f'adapt claim-adaptation adaptationTarget={adaptation} level={level} force=true player={uiclient.PLAYER}'
        replies.append({'command': command, 'reply': session.rcon(command)})
    time.sleep(2.0)
    return replies


def toast_shown(mode: str) -> Callable[[dict], bool]:
    """The mod draws its toasts on its own HUD, so a native toast never reaches the vanilla toast events."""
    if mode == 'native':
        return lambda state: bool(pack_scenarios.native_hud_model(state).get('toasts'))
    return lambda state: bool(state['events']['toasts'])


def close(session: uiclient.Session) -> None:
    for _ in range(3):
        if session.state()['screen'] is None:
            return
        session.key('escape')
        time.sleep(0.5)


def shoot(mode: str, target: uiclient.Target, jar: Path, client_adapt: Path, output: Path, min_pack_mb: float,
          shots_wanted: tuple[Shot, ...] = SHOTS) -> dict:
    run: Path = output / f'run-{mode}'
    if run.exists():
        shutil.rmtree(run)
    (run / 'screenshots').mkdir(parents=True)
    with uiclient.launch(target, output=run, client_adapt=client_adapt, server_adapt=jar, server_mods=[], prefix=acceptance.PREFIX,
                         gui_scale=2, options=[HIDDEN_CHAT, NO_CLOUDS], server_setup=acceptance.adapt_setup()) as session:
        loaded: bool = acceptance.watch_join(session, 'pack' if mode == 'pack' else 'modded', run, mode == 'pack')
        session.rcon('difficulty peaceful')
        session.rcon('time set noon')
        # Facing the sky: facing the ground puts the outline of the block underfoot behind the page.
        session.rcon(f'tp {uiclient.PLAYER} ~ ~ ~ 0 -90')
        session.resize(WIDTH, HEIGHT)
        session.wait(lambda state: state['window']['width'] == WIDTH, 'resize', 15)
        replies: list[dict[str, str]] = progress(session)
        time.sleep(TOASTS_SETTLE_SECONDS)
        shots: list[str] = []
        missed: list[str] = []
        mutating: bool = False
        for shot in shots_wanted:
            if mode == 'native' and not shot.native:
                continue
            if shot.command.startswith('adapt mutate') and not mutating:
                for skill, level in MUTATE_LEVELS.items():
                    replies.append({'command': f'claim-skill {skill} {level}',
                                    'reply': session.rcon(f'adapt claim-skill skillTarget={skill} level={level} player={uiclient.PLAYER}')})
                time.sleep(TOASTS_SETTLE_SECONDS)
                mutating = True
            if shot.before_command:
                close(session)
                session.command(shot.before_command)
                time.sleep(1.0)
            for index, variant in enumerate(VARIANTS[mode]):
                suffix: str = variant.suffix
                window(session, variant)
                if shot.notice:
                    close(session)
                    time.sleep(TOASTS_SETTLE_SECONDS / 3)
                    session.rcon(f'tp {uiclient.PLAYER} ~ ~ ~ 0 90')
                    session.rcon(f'give {uiclient.PLAYER} minecraft:iron_shovel')
                    time.sleep(1.0)
                    session.clear_events()
                    session.hold('attack', True, DIG_TICKS)
                    time.sleep(DIG_TICKS / 20.0)
                    level: int = NOTICE_LEVEL + index * 5
                    replies.append({'command': shot.command.format(level=level), 'reply': session.rcon(shot.command.format(level=level))})
                    try:
                        session.wait(toast_shown(mode), 'toast', 6)
                    except (AssertionError, RuntimeError, TimeoutError):
                        missed.append(f'{shot.name}{suffix} (no toast event)')
                    time.sleep(NOTICE_SETTLE_SECONDS)
                elif not (shot.once and index > 0):
                    close(session)
                    session.command(shot.command)
                    try:
                        session.wait(lambda state: state['screen'] is not None, f'{shot.name} screen', 20)
                    except AssertionError:
                        missed.append(f'{shot.name}{suffix} (no screen opened)')
                        continue
                    time.sleep(1.5)
                    if mode == 'pack' and not all(step_done(session, step) for step in shot.steps):
                        missed.append(f'{shot.name}{suffix} (control not found)')
                        continue
                elif session.state()['screen'] is None:
                    missed.append(f'{shot.name}{suffix} (closed)')
                    continue
                if not shot.notice:
                    session.op('mouse-move', x=1, y=1)
                    time.sleep(1.0)
                path: Path = output / f'{shot.name}-{mode}{suffix}.png'
                session.op('screenshot', timeout=60, path=str(path))
                shots.append(path.name)
                if shot.notice:
                    session.rcon(f'clear {uiclient.PLAYER}')
                    session.rcon(f'tp {uiclient.PLAYER} ~ ~ ~ 0 -90')
                if mode == 'pack' and index == 0:
                    waited: int = 0
                    for ticks in shot.frames:
                        time.sleep((ticks - waited) / 20.0)
                        waited = ticks
                        frame: Path = output / f'{shot.name}-pack-t{ticks}.png'
                        session.op('screenshot', timeout=60, path=str(frame))
                        shots.append(frame.name)
                if mode == 'pack' and index == 0 and shot.hover:
                    hovered: str | None = hover(session, shot.hover, output / f'{shot.name}-pack-hover.png')
                    if hovered is None:
                        missed.append(f'{shot.name} hover')
                    else:
                        shots.append(hovered)
        close(session)
    baked: re.Match | None = BAKED.search((run / 'server.log').read_text(errors='replace'))
    if baked is None or float(baked.group(2)) < min_pack_mb:
        raise SystemExit(f'{mode}: the server baked {baked.group(0) if baked else "no pack"}; build a current jar first')
    return {'mode': mode, 'packLoaded': loaded, 'shots': shots, 'missed': missed, 'commands': replies,
            'pack': {'target': baked.group(1), 'megabytes': float(baked.group(2)), 'sha1': baked.group(3)}}


def window(session: uiclient.Session, variant: Variant) -> None:
    if session.state()['window']['width'] != variant.width:
        session.resize(variant.width, variant.height)
        session.wait(lambda state: state['window']['width'] == variant.width, 'resize', 15)
    session.gui_scale(variant.scale)
    time.sleep(0.5)


def hover(session: uiclient.Session, name: str, path: Path) -> str | None:
    """Points at the first slot whose item is named name and shoots its tooltip; None when the page has no such slot."""
    entry: dict | None = next((slot for slot in pack_scenarios.slots(session.state()['screen'])
                               if slot.get('name', '').startswith(name)), None)
    if entry is None:
        return None
    session.op('mouse-move', x=entry['x'], y=entry['y'])
    time.sleep(0.8)
    session.op('screenshot', timeout=60, path=str(path))
    return path.name


def step_done(session: uiclient.Session, step: Step) -> bool:
    """Presses the step's control or card; false when the page has neither."""
    for _ in range(CARD_PAGES if step.pages else 1):
        current: dict = session.state()['screen'] or {}
        control: dict | None = (pack_scenarios.layout_control(current, *step.layout) if step.layout is not None
                                else pack_scenarios.named_card(current, step.card))
        if control is not None:
            session.click(control['x'], control['y'])
            time.sleep(1.5)
            return True
        turn: dict | None = pack_scenarios.layout_control(current, step.pages, 'foot.next') if step.pages else None
        if turn is None:
            return False
        session.click(turn['x'], turn['y'])
        time.sleep(1.5)
    return False


def sha1(path: Path) -> str:
    return hashlib.sha1(path.read_bytes()).hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser(description='Native and pack screenshots of the same progress, side by side.')
    parser.add_argument('--output', type=Path, default=Path('/Users/creepshow/Desktop/Adapt/resourcepack/owner'))
    parser.add_argument('--server-adapt', type=Path, help='Adapt plugin jar; default this checkout\'s build')
    parser.add_argument('--client-adapt', type=Path, help='Adapt Fabric 26.3 mod jar; default this checkout\'s build')
    parser.add_argument('--only', default='', help='comma list of shot names to take (default all)')
    parser.add_argument('--min-pack-mb', type=float, default=5.0, help='smallest baked pack accepted as current (default 5)')
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    names: set[str] = {name for name in args.only.split(',') if name}
    wanted: tuple[Shot, ...] = tuple(shot for shot in SHOTS if not names or shot.name in names)
    jar: Path = args.server_adapt or uiclient.adapt_plugin_jar('26.3')
    mod: Path = args.client_adapt or uiclient.adapt_mod_jar(uiclient.Target('fabric', '26.3'))
    results: list[dict] = [
        shoot('native', uiclient.Target('fabric', '26.3', 'paper', '26.3'), jar, mod, args.output, args.min_pack_mb, wanted),
        shoot('pack', uiclient.Target('fabric', '26.3', 'paper', '26.3'), jar, Path('none'), args.output, args.min_pack_mb, wanted),
    ]
    (args.output / 'commands.json').write_text(json.dumps(results, indent=1) + '\n')
    commit: str = subprocess.run(['git', '-C', str(HERE), 'rev-parse', '--short', 'HEAD'], capture_output=True, text=True).stdout.strip()
    lines: list[str] = [f'checkout {commit}', f'server jar {jar.name} sha1 {sha1(jar)}', f'client mod {mod.name} sha1 {sha1(mod)}']
    lines += [f"{result['mode']} pack {result['pack']['target']} {result['pack']['megabytes']} MB sha1 {result['pack']['sha1']}"
              for result in results]
    lines += [f"{result['mode']} not shot: {', '.join(result['missed'])}" for result in results if result['missed']]
    (args.output / 'build.txt').write_text('\n'.join(lines) + '\n')


if __name__ == '__main__':
    main()
