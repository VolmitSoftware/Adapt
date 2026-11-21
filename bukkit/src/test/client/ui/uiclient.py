"""Hidden modded Minecraft client harness for Adapt UI checks.

Usage (from the repository root; run long jobs detached under `caffeinate -ims`):

    python3 bukkit/src/test/client/ui/uiclient.py smoke <loader> <mc> [--server paper|<loader>]
    python3 bukkit/src/test/client/ui/uiclient.py exercise <loader> <mc> [--server ...]
    python3 bukkit/src/test/client/ui/uiclient.py hold <loader> <mc> [--server ...] [--minutes 30]
    python3 bukkit/src/test/client/ui/uiclient.py audit <loader> <mc>
    python3 bukkit/src/test/client/ui/uiclient.py cleanup

    loader: fabric | forge | neoforge     mc: 1.20.1 | 1.21.1 | 26.1.2 | 26.2 | 26.3  (no neoforge 1.20.1)

`smoke` builds the bridge, creates a Multiplexor server (the matching loader, or Paper with --server paper) with the
current Adapt jar, creates a temporary game directory under build/client-ui/instances with the current Adapt mod jar
and the bridge, starts the client JVM directly (launcher.py: Prism's metadata and its shared library and asset caches,
no launcher GUI) hidden and offline, joins, runs `/adapt gui`, screenshots the menu, quits and deletes everything it created. `exercise` drives every control op once (hover, click, gui scale,
resize, inventory drag, shift-click, scroll, chat typing) with screenshots. `hold` does the
same setup, prints the bridge port and token to <output>/session.json and keeps the client up for manual driving until
the time runs out or the file <output>/stop appears. `audit` checks the window calls of the game jar and the bridge
flags without launching. `cleanup` stops and deletes leftover cuh-adapt-* servers, instances and client processes.
Adapt jars default to adapters/<loader>/build/<mc>/libs and build/libs (Paper); override with --client-adapt and
--server-adapt (any path, for example a jar built in another worktree; --client-adapt none and --server-adapt none
leave Adapt out, for isolating a fault). --client-mod JAR and --server-mod JAR (repeatable) add other mods to the client or server, such as a recipe viewer and
a library it needs on both sides. --property KEY=VALUE (repeatable) adds a client
JVM system property, for example --property adapt.client.debugHud=true. Logs, screenshots and report.json go to build/client-ui/runs/<run>/.
Only one harness client runs on the machine at a time: launch() takes an exclusive flock on $ADAPT_UI_CLIENT_LOCK
(default <tmpdir>/adapt-ui-client.lock, the same lock `lockf -k <file>` takes, so do not also wrap a run in lockf on
that file) and then waits while any client from another checkout is running. Forge and NeoForge servers are created
from the installer of the exact loader build the client runs.

Scenario API, for other scripts:

    from uiclient import Target, launch
    with launch(Target('forge', '1.20.1'), singleplayer='qa') as session:   # integrated server, no Multiplexor server
        session.create_world()                                             # survival, cheats on; leave_world() quits to title
    with launch(Target('neoforge', '26.3')) as session:
        session.wait_connected()
        session.command('adapt gui')
        session.wait_screen(cls='ContainerScreen')
        session.click(100, 80, button='left', mods=['shift'])
        session.drag((100, 80), (140, 80))
        session.key('escape')
        session.screenshot('menu')
        session.assert_screen(none=True)

Control API (loopback HTTP, header X-Adapt-UI-Token): GET /state, POST /command with {"op": ...}:
state | command {command} | chat {text} | mouse-move {x,y} | mouse-down/mouse-up {x?,y?,button,mods} |
click {x,y,button,mods,count} | drag {from:[x,y],to:[x,y],button,mods,steps} | scroll {x?,y?,dx,dy} |
key {name,mods,action: tap|press|release} | type {text} | close-screen | gui-scale {scale} | resize {width,height} |
screenshot {path} | wait-screen {class?,title?,none?,timeoutMs} | quit. Coordinates are GUI-scaled; input is
synthetic and never touches the real pointer. Gameplay input (works with no grabbed pointer):
hold {action: attack|use|forward|back|left|right|jump|sneak|sprint, down: true|false, ticks?} keeps that key mapping
down every client tick until released or for `ticks` ticks (a held attack mines the block under the crosshair) |
release (lets go of every held action) | jump-sequence {presses: [tick offsets], tap} presses jump for tap
ticks at each offset (client ticks from the call) and records player.jumpTrace (per tick: jump key, ground, x/y/z,
mayfly, flying) until landing | look {yaw, pitch} turns the player (pitch 90 = straight down) |
clear-events (empties the recorded vanilla action bar messages and toasts) |
trace-entities {ticks, radius} records arrows and living entities within radius blocks every client tick for ticks
ticks as the client simulates them (player.entityTrace: t, id, arrow, x/y/z, vx/vy/vz, glowing, hurt, health).
wait-screen and assert_screen match `class` against the runtime class name (full or simple) or any entry of the
screen's `kinds`: the Mojang simple names of the vanilla screens it is an instance of (InventoryScreen,
CreativeModeInventoryScreen, ContainerScreen, CraftingScreen, AbstractContainerScreen, ChatScreen, PauseScreen,
TitleScreen, DeathScreen), so the same names work on intermediary-mapped Fabric 1.20.1/1.21.1 and Forge 1.20.1.
State reports the screen (class, kinds, title, widgets, container slots),
uiTree (art.arcane.adapt.client.qa.UiProbe.snapshot() JSON when the Adapt client provides it, else null), window
(size, gui scale, visible, focused, cursor capture), audio and player (position, yaw, pitch, held actions, and target:
the crosshair hit with block position and block id, or entity type) and events {actionbar: [{time, tick, text,
animated}], toasts: [{time, tick, kind: advancement|system|recipe|tutorial|other, type, class, title?, description?}]},
the last 64 of each vanilla action bar message and vanilla toast the client showed (time = epoch ms, tick = client tick).
"""
from __future__ import annotations

import argparse
import contextlib
import fcntl
import hashlib
import json
import os
import re
import secrets
import shutil
import signal
import socket
import struct
import subprocess
import sys
import tempfile
import threading
import time
import urllib.error
import urllib.request
import uuid
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable, Iterator

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_bridge  # noqa: E402
import launcher  # noqa: E402

ROOT: Path = build_bridge.ROOT
PREFIX: str = 'cuh-adapt-'
INSTANCES: Path = build_bridge.OUTPUT / 'instances'
MULTIPLEXOR: Path = Path(os.environ.get('ADAPT_UI_MULTIPLEXOR', str(ROOT.parent / 'ServerMultiplexor')))
RUNS: Path = build_bridge.OUTPUT / 'runs'
WATCHER: Path = build_bridge.OUTPUT / 'tools' / 'window-watch'
CLIENT_LOCK: Path = Path(os.environ.get('ADAPT_UI_CLIENT_LOCK', str(Path(tempfile.gettempdir()) / 'adapt-ui-client.lock')))
PLAYER: str = 'CuhAdaptQa'
WIDTH: int = 1280
HEIGHT: int = 720
LWJGL: dict[str, str] = {'1.20.1': '3.3.1', '1.21.1': '3.3.3', '26.1.2': '3.4.1', '26.2': '3.4.1', '26.3': '3.4.3'}
SOUND_CATEGORIES: tuple[str, ...] = ('master', 'music', 'record', 'weather', 'block', 'hostile', 'neutral', 'player',
                                     'ambient', 'voice', 'ui')


def log(message: str) -> None:
    print(time.strftime('%H:%M:%S ') + message, flush=True)


@dataclass(frozen=True)
class Target:
    loader: str
    minecraft: str
    server: str = ''

    @property
    def server_platform(self) -> str:
        return self.server or self.loader

    @property
    def java(self) -> int:
        return int(build_bridge.TARGETS[self.minecraft]['java'])

    @property
    def label(self) -> str:
        server: str = '' if self.server_platform == self.loader else '-' + self.server_platform
        return f'{self.loader}-{self.minecraft.replace(".", "")}{server}'


def targets_json() -> dict:
    return json.loads((ROOT / 'gradle' / 'targets.json').read_text())


def loader_version(target: Target) -> str:
    entry: dict = targets_json()[target.minecraft]
    if target.loader == 'fabric':
        return entry['fabricLoader']
    if target.loader == 'forge':
        return entry['forge'].split('-', 1)[1]
    if target.minecraft == '1.20.1':
        raise SystemExit('NeoForge 1.20.1 is not a target; use forge 1.20.1')
    return entry['neoforge']


def server_installer(target: Target) -> Path:
    """The installer of the exact Forge or NeoForge build the client runs. The Multiplexor ignores --loader unless it
    rebuilds and otherwise creates from its newest cached installer, so a server built from it can differ from the client
    (NeoForge 26.3.0.115 server against a 26.3.0.33-beta client crashes the client on join)."""
    version: str = loader_version(target)
    if target.loader == 'neoforge':
        coordinate: str = f'net/neoforged/neoforge/{version}/neoforge-{version}-installer.jar'
        repository: str = 'https://maven.neoforged.net/releases/'
    else:
        coordinate = f'net/minecraftforge/forge/{target.minecraft}-{version}/forge-{target.minecraft}-{version}-installer.jar'
        repository = 'https://maven.minecraftforge.net/'
    return launcher.fetch(repository + coordinate, launcher.LIBRARIES / coordinate)


def adapt_mod_jar(target: Target) -> Path:
    loader_name: str = {'fabric': 'Fabric', 'forge': 'Forge', 'neoforge': 'NeoForge'}[target.loader]
    jars: list[Path] = sorted((ROOT / 'adapters' / target.loader / 'build' / target.minecraft / 'libs')
                              .glob(f'Adapt v* [[]{loader_name}] {target.minecraft}+*.jar'), key=lambda path: path.stat().st_mtime)
    if not jars:
        raise SystemExit(f'Build Adapt first: cd adapters/{target.loader} && ../../gradlew --no-daemon -p . '
                         f'-PminecraftVersion={target.minecraft} assemble')
    return jars[-1]


def adapt_plugin_jar(minecraft: str) -> Path:
    directories: list[Path] = [ROOT / 'build' / 'libs', ROOT / 'build' / f'bukkit-{minecraft}' / 'libs']
    jars: list[Path] = sorted((jar for directory in directories for jar in directory.glob('Adapt v* [[]CraftBukkit] *.jar')),
                              key=lambda path: path.stat().st_mtime)
    covering: list[Path] = [jar for jar in jars if minecraft in jar.name or (minecraft.startswith('26.') and '26.' in jar.name)]
    if not covering:
        raise SystemExit('Build the Adapt plugin first: ./gradlew --no-daemon :bukkit:packedJar')
    return covering[-1]


def offline_uuid(name: str) -> str:
    digest: bytearray = bytearray(hashlib.md5(('OfflinePlayer:' + name).encode()).digest())
    digest[6] = (digest[6] & 0x0F) | 0x30
    digest[8] = (digest[8] & 0x3F) | 0x80
    return str(uuid.UUID(bytes=bytes(digest)))


def free_port() -> int:
    with socket.socket() as listener:
        listener.bind(('127.0.0.1', 0))
        return listener.getsockname()[1]


def processes() -> list[tuple[int, str]]:
    listing: str = subprocess.run(['ps', '-axo', 'pid=,args='], capture_output=True, text=True, check=True).stdout
    result: list[tuple[int, str]] = []
    for line in listing.splitlines():
        fields: list[str] = line.strip().split(None, 1)
        if len(fields) == 2:
            result.append((int(fields[0]), fields[1]))
    return result


def client_pids(instance: Path) -> list[int]:
    marker: str = '--gameDir ' + str(instance)
    return [pid for pid, args in processes() if marker in args and args.split(None, 1)[0].endswith('/java')]


@contextlib.contextmanager
def machine_lock(name: str, wait: int = 1800) -> Iterator[None]:
    lock: Path = Path(tempfile.gettempdir()) / f'adapt-qa-{name}.lock'
    deadline: float = time.monotonic() + wait
    while True:
        try:
            lock.mkdir()
            (lock / 'pid').write_text(str(os.getpid()))
            break
        except FileExistsError:
            holder: str = (lock / 'pid').read_text().strip() if (lock / 'pid').is_file() else ''
            if holder.isdigit():
                try:
                    os.kill(int(holder), 0)
                except ProcessLookupError:
                    shutil.rmtree(lock, ignore_errors=True)
                    continue
            if time.monotonic() > deadline:
                raise TimeoutError(f'Lock held too long: {lock}')
            time.sleep(0.2)
    try:
        yield
    finally:
        shutil.rmtree(lock, ignore_errors=True)


def foreign_clients() -> list[int]:
    return [pid for pid, args in processes() if args.split(None, 1)[0].endswith('/java') and '/client-ui/instances/' in args]


@contextlib.contextmanager
def client_slot(wait: int = 7200) -> Iterator[None]:
    """Holds the machine-wide client slot: an exclusive flock on CLIENT_LOCK (the same file `lockf -k` takes, so shell
    wrappers and this harness agree), then waits until no harness client from any checkout is running."""
    deadline: float = time.monotonic() + wait
    CLIENT_LOCK.parent.mkdir(parents=True, exist_ok=True)
    with CLIENT_LOCK.open('a') as handle:
        announced: bool = False
        while True:
            try:
                fcntl.flock(handle.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
                break
            except BlockingIOError:
                if time.monotonic() > deadline:
                    raise TimeoutError('Client lock held too long: ' + str(CLIENT_LOCK))
                if not announced:
                    log('Waiting for the client lock ' + str(CLIENT_LOCK))
                    announced = True
                time.sleep(2)
        try:
            while foreign_clients():
                if time.monotonic() > deadline:
                    raise TimeoutError('Another harness client kept running: ' + ', '.join(map(str, foreign_clients())))
                if not announced:
                    log('Waiting for another harness client to finish')
                    announced = True
                time.sleep(5)
            yield
        finally:
            fcntl.flock(handle.fileno(), fcntl.LOCK_UN)


class Multiplexor:
    def __init__(self, consumer: str, output: Path):
        self.consumer: str = consumer
        self.output: Path = output

    def run(self, *arguments: str, timeout: int = 1800) -> str:
        result = subprocess.run([str(MULTIPLEXOR / 'start.sh'), '--consumer', self.consumer, *arguments], cwd=MULTIPLEXOR,
                                capture_output=True, text=True, timeout=timeout)
        with (self.output / 'multiplexor.log').open('a') as handle:
            handle.write('$ ' + ' '.join(arguments) + '\n' + result.stdout + result.stderr + '\n')
        if result.returncode != 0:
            raise RuntimeError('Multiplexor ' + ' '.join(arguments) + ' failed: ' + (result.stdout + result.stderr).strip()[-1500:])
        return result.stdout.strip()

    def instances(self) -> list[str]:
        with machine_lock('multiplexor', 600):
            listing: str = self.run('instance', 'list')
        return [re.sub(r'\s*\(active\)$', '', line).strip() for line in listing.splitlines() if line.strip()]


class Bridge:
    def __init__(self, port: int, token: str):
        self.url: str = f'http://127.0.0.1:{port}'
        self.token: str = token

    def request(self, operation: dict | None, timeout: float) -> dict:
        data: bytes | None = None if operation is None else json.dumps(operation).encode()
        request = urllib.request.Request(self.url + ('/state' if data is None else '/command'), data=data,
                                         headers={'X-Adapt-UI-Token': self.token, 'Content-Type': 'application/json'})
        try:
            with urllib.request.urlopen(request, timeout=timeout) as response:
                result: dict = json.load(response)
        except urllib.error.HTTPError as failure:
            result = json.load(failure)
        if result.get('error'):
            raise RuntimeError(result['error'] + ('' if 'state' not in result else ' ' + json.dumps(result['state'])[:1500]))
        return result


def screen_is(screen: dict | None, cls: str) -> bool:
    """True when the screen's runtime class is cls (full or simple name) or a mapping-independent kind matches it."""
    if not screen:
        return False
    name: str = screen['class']
    return name == cls or name.endswith('.' + cls) or cls in screen.get('kinds', [])


@dataclass
class Session:
    target: Target
    bridge: Bridge
    output: Path
    watch: Callable[[], None]
    steps: list[dict] = field(default_factory=list)

    def state(self) -> dict:
        self.watch()
        return self.bridge.request(None, 15)

    def op(self, operation_name: str, /, timeout: float = 30, **values: object) -> dict:
        self.watch()
        operation: dict = {'op': operation_name, **values}
        self.steps.append(operation)
        return self.bridge.request(operation, timeout)

    def wait(self, predicate: Callable[[dict], bool], label: str, timeout: float = 60) -> dict:
        deadline: float = time.monotonic() + timeout
        last: dict = {}
        while time.monotonic() < deadline:
            last = self.state()
            if predicate(last):
                return last
            time.sleep(0.25)
        raise AssertionError(f'Timed out waiting for {label}: {json.dumps(last)[:1500]}')

    def wait_connected(self, timeout: float = 240) -> dict:
        return self.wait(lambda state: state['player']['connected'] and state['player'].get('name') == PLAYER, 'join', timeout)

    def command(self, text: str) -> dict:
        return self.op('command', command=text)

    def chat(self, text: str) -> dict:
        return self.op('chat', text=text)

    def wait_screen(self, cls: str | None = None, title: str | None = None, none: bool = False, timeout: float = 30) -> dict:
        values: dict = {'timeoutMs': int(timeout * 1000), 'none': none}
        if cls:
            values['class'] = cls
        if title:
            values['title'] = title
        return self.op('wait-screen', timeout=timeout + 10, **values)

    def assert_screen(self, cls: str | None = None, title: str | None = None, none: bool = False) -> dict:
        state: dict = self.state()
        screen: dict | None = state['screen']
        if none:
            assert screen is None, f'Expected no screen, found {screen and screen["class"]}'
            return state
        assert screen is not None, 'Expected a screen, found none'
        if cls:
            assert screen_is(screen, cls), f'Screen {screen["class"]} {screen.get("kinds", [])} is not {cls}'
        if title:
            assert title in screen['title'], f'Screen title {screen["title"]!r} lacks {title!r}'
        return state

    def key(self, name: str, mods: list[str] | None = None, action: str = 'tap') -> dict:
        return self.op('key', name=name, mods=mods or [], action=action)

    def type(self, text: str) -> dict:
        return self.op('type', text=text)

    def click(self, x: float, y: float, button: str = 'left', mods: list[str] | None = None, count: int = 1) -> dict:
        return self.op('click', x=x, y=y, button=button, mods=mods or [], count=count)

    def drag(self, start: tuple[float, float], end: tuple[float, float], button: str = 'left', steps: int = 8) -> dict:
        return self.op('drag', **{'from': list(start), 'to': list(end), 'button': button, 'steps': steps})

    def scroll(self, x: float, y: float, dy: float, dx: float = 0.0) -> dict:
        return self.op('scroll', x=x, y=y, dx=dx, dy=dy)

    def hold(self, action: str, down: bool = True, ticks: int = 0) -> dict:
        return self.op('hold', action=action, down=down, ticks=ticks)

    def release(self) -> dict:
        return self.op('release')

    def jump_sequence(self, presses: list[int], tap: int = 2) -> dict:
        return self.op('jump-sequence', presses=presses, tap=tap)

    def look(self, yaw: float, pitch: float) -> dict:
        return self.op('look', yaw=yaw, pitch=pitch)

    def trace_entities(self, ticks: int, radius: float) -> dict:
        return self.op('trace-entities', ticks=ticks, radius=radius)

    def events(self) -> dict:
        return self.state()['events']

    def clear_events(self) -> dict:
        return self.op('clear-events')

    def gui_scale(self, scale: int) -> dict:
        return self.op('gui-scale', scale=scale)

    def resize(self, width: int, height: int) -> dict:
        return self.op('resize', width=width, height=height)

    def press(self, widget: dict) -> None:
        self.click(widget['x'] + widget['width'] / 2, widget['y'] + widget['height'] / 2)
        time.sleep(1.0)

    def create_world(self, cheats: bool = True, timeout: float = 240) -> dict:
        """From the failed quick play, the title or the world list, creates a default survival world on the integrated
        server and waits until the player is in it."""
        deadline: float = time.monotonic() + timeout
        settled: bool = False
        while time.monotonic() < deadline:
            state: dict = self.state()
            if state['player']['connected']:
                return state
            widgets: dict[str, dict] = {widget.get('message', ''): widget for widget in (state['screen'] or {}).get('widgets', [])
                                        if widget.get('active', True)}
            toggle: dict | None = next((widget for message, widget in widgets.items() if message.startswith('Allow Cheats')), None)
            if toggle is not None and not settled:
                if toggle['message'].endswith('ON') != cheats:
                    self.press(toggle)
                    continue
                settled = True
            if toggle is not None and settled and 'Create New World' in widgets:
                self.press(widgets['Create New World'])
                time.sleep(5)
                continue
            if toggle is None:
                for name in ('Back to World List', 'Singleplayer', 'Create New World'):
                    if name in widgets:
                        self.press(widgets[name])
                        break
            time.sleep(1)
        raise AssertionError('Timed out creating a singleplayer world')

    def leave_world(self) -> dict:
        self.key('escape')
        screen: dict = self.wait_screen(cls='PauseScreen', timeout=20)['screen']
        self.press(next(widget for widget in screen['widgets'] if widget.get('message', '').startswith('Save and Quit')))
        return self.wait(lambda state: not state['player']['connected'] and screen_is(state['screen'], 'TitleScreen'), 'title', 120)

    def screenshot(self, name: str) -> Path:
        path: Path = self.output / 'screenshots' / (name + '.png')
        self.op('screenshot', timeout=60, path=str(path))
        return path


class Watchdog:
    """Polls macOS every 250 ms for on-screen windows, Dock policy or focus taken by the owned client and kills it on
    the first violation."""

    def __init__(self, instance: Path, output: Path):
        self.instance: Path = instance
        self.pid: int = 0
        self.output: Path = output
        self.samples: int = 0
        self.violation: dict | None = None
        self.policies: dict[str, int] = {}
        self.running: bool = False
        self.thread: threading.Thread | None = None

    def start(self, pid: int) -> None:
        self.pid = pid
        self.running = True
        self.thread = threading.Thread(target=self.loop, name='window watchdog', daemon=True)
        self.thread.start()

    def stop(self) -> None:
        self.running = False
        if self.thread is not None:
            self.thread.join(5)

    def loop(self) -> None:
        while self.running and self.violation is None:
            pids: list[int] = sorted({self.pid, *client_pids(self.instance)})
            result = subprocess.run([str(WATCHER), *map(str, pids)], capture_output=True, text=True)
            if result.returncode == 0:
                report: dict = json.loads(result.stdout)
                self.samples += 1
                self.policies.update(report.get('policies', {}))
                if report['visible'] or report['frontmostOwned']:
                    for pid in pids:
                        with contextlib.suppress(ProcessLookupError):
                            os.kill(pid, signal.SIGKILL)
                    self.violation = report
                    (self.output / 'violation.json').write_text(json.dumps(report, indent=2))
            time.sleep(0.25)

    def check(self) -> None:
        if self.violation:
            raise RuntimeError('Hidden-window violation, client killed: ' + json.dumps(self.violation))


def ensure_watcher() -> None:
    source: Path = Path(__file__).resolve().parent / 'tools' / 'WindowWatch.swift'
    if not WATCHER.is_file() or WATCHER.stat().st_mtime < source.stat().st_mtime:
        WATCHER.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run(['swiftc', '-O', '-o', str(WATCHER), str(source)], check=True, capture_output=True)


def audit(target: Target) -> dict:
    report: dict = build_bridge.audit(target.minecraft)
    bridge: Path = build_bridge.build(target.loader, target.minecraft)
    mixins: list[str] = json.loads((bridge.parent / 'bridge.json').read_text())['mixins']
    required: set[str] = {'WindowMixin', 'InputConstantsMixin', 'MouseHandlerMixin', 'MinecraftTickMixin'}
    if build_bridge.family(target.minecraft) == 'sdl':
        required |= {'SdlHintsMixin', 'HiddenPresentationMixin'}
    else:
        required |= {'GlxMixin'}
    if target.minecraft == '26.2':
        required |= {'HiddenPresentationMixin'}
    report['bridge'] = str(bridge)
    report['missingMixins'] = sorted(required - set(mixins))
    report['earlyWindow'] = 'config/fml.toml earlyWindowControl=false' if target.loader != 'fabric' else 'none (Fabric)'
    report['ok'] = not report['unhandled'] and not report['missingMixins']
    return report


LOADER_CALLS: dict[tuple[str, str], str] = {
    ('net/neoforged/neoforge/client/loading/earlydisplay/EarlyWindowHandoff', 'completeWindowHandoff'): 'NeoForgeHandoffMixin skips show and maximize',
}


def audit_classpath(classpath: list[Path]) -> dict:
    """Scans the loader and library jars of a launch for window, focus and pointer calls before the JVM starts."""
    calls: list[dict] = []
    unhandled: list[dict] = []
    for jar in classpath:
        text: str = str(jar)
        if '/com/mojang/minecraft/' in text or '/org/lwjgl/' in text:
            continue
        early: bool = 'earlydisplay' in jar.name
        for owner, method, call in build_bridge.call_sites(jar, build_bridge.WINDOW_CALLS):
            reason: str = 'early loading window, off through config/fml.toml earlyWindowControl=false' if early else LOADER_CALLS.get((owner, method), '')
            entry: dict = {'jar': jar.name, 'class': owner, 'method': method, 'call': call, 'handledBy': reason}
            calls.append(entry)
            if not reason:
                unhandled.append(entry)
    return {'calls': calls, 'unhandled': unhandled}


def options_text() -> str:
    lines: list[str] = ['fullscreen:false', 'enableVsync:false', 'maxFps:60', 'pauseOnLostFocus:false', 'tutorialStep:none',
                        'onboardAccessibility:false', 'narrator:0', 'joinedFirstServer:true', 'skipMultiplayerWarning:true',
                        'renderDistance:4', 'simulationDistance:5', 'guiScale:2', 'lang:en_us', 'autoJump:false',
                        'realmsNotifications:false', 'rawMouseInput:false']
    lines += [f'soundCategory_{category}:0.0' for category in SOUND_CATEGORIES]
    return '\n'.join(lines) + '\n'


def components(target: Target) -> list[launcher.Component]:
    result: list[launcher.Component] = [launcher.Component('org.lwjgl3', LWJGL[target.minecraft]),
                                        launcher.Component('net.minecraft', target.minecraft)]
    version: str = loader_version(target)
    if target.loader == 'fabric':
        result += [launcher.Component('net.fabricmc.intermediary', target.minecraft),
                   launcher.Component('net.fabricmc.fabric-loader', version)]
    elif target.loader == 'forge':
        result.append(launcher.Component('net.minecraftforge', version))
    else:
        result.append(launcher.Component('net.neoforged', version))
    return result


def prepare_game(target: Target, game: Path, mod: Path | None, bridge: Path, extra_mods: list[Path]) -> None:
    (game / 'mods').mkdir(parents=True)
    (game / 'options.txt').write_text(options_text())
    if target.loader != 'fabric':
        (game / 'config').mkdir()
        (game / 'config' / 'fml.toml').write_text('earlyWindowControl = false\nversionCheck = false\n')
    if mod is not None:
        shutil.copy2(mod, game / 'mods' / 'Adapt.jar')
    shutil.copy2(bridge, game / 'mods' / 'AdaptUiBridge.jar')
    for extra in extra_mods:
        shutil.copy2(extra, game / 'mods' / extra.name)


def launch_client(target: Target, game: Path, output: Path, port: int, token: str, quick_play: list[str],
                  properties: list[str]) -> subprocess.Popen:
    hidden: list[str] = ['-Dadapt.ui.hidden=true', f'-Dadapt.ui.port={port}', f'-Dadapt.ui.token={token}',
                         '-Djava.awt.headless=true', '-Dapple.awt.UIElement=true', '-Dfml.earlyWindowControl=false',
                         *('-D' + entry for entry in properties)]
    game_args: list[str] = ['--width', str(WIDTH), '--height', str(HEIGHT), *quick_play]
    java: Path = build_bridge.jdk(target.java) / 'bin' / 'java'
    launch_plan: launcher.LaunchPlan = launcher.plan(components(target), java, game, PLAYER, offline_uuid(PLAYER),
                                                     build_bridge.OUTPUT / 'meta', hidden, game_args)
    runtime_audit: dict = audit_classpath(launch_plan.classpath)
    (output / 'runtime-audit.json').write_text(json.dumps(runtime_audit, indent=2))
    if runtime_audit['unhandled']:
        raise RuntimeError('Loader jars call window functions the bridge does not neutralise; refusing to launch: '
                           + json.dumps(runtime_audit['unhandled'])[:1500])
    (output / 'launch.json').write_text(json.dumps({'mainClass': launch_plan.main_class, 'jvmArgs': launch_plan.jvm_args,
                                                    'gameArgs': launch_plan.game_args, 'classpath': len(launch_plan.classpath)},
                                                   indent=2).replace(token, '[token]'))
    return launcher.start(launch_plan, game, output / 'client-stdout.log')


def server_properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for line in path.read_text().splitlines():
        if line and not line.startswith('#') and '=' in line:
            key, value = line.split('=', 1)
            values[key] = value
    return values


def configure_server(server: Path, target: Target, adapt: Path | None, port: int, extra_mods: list[Path]) -> None:
    properties: dict[str, str] = server_properties(server / 'server.properties')
    properties.update({'online-mode': 'false', 'use-native-transport': 'false', 'server-port': str(port),
                       'level-type': 'minecraft:flat', 'generate-structures': 'false', 'view-distance': '4',
                       'simulation-distance': '4', 'spawn-protection': '0', 'motd': 'Adapt UI harness',
                       'enforce-secure-profile': 'false'})
    (server / 'server.properties').write_text(''.join(f'{key}={value}\n' for key, value in properties.items()))
    content: Path = server / ('plugins' if target.server_platform == 'paper' else 'mods')
    content.mkdir(parents=True, exist_ok=True)
    if adapt is not None:
        shutil.copy2(adapt, content / 'Adapt.jar')
    for extra in extra_mods:
        shutil.copy2(extra, content / extra.name)
    (server / 'ops.json').write_text(json.dumps([{'uuid': offline_uuid(PLAYER), 'name': PLAYER, 'level': 4,
                                                  'bypassesPlayerLimit': False}], indent=2))


def wait_server(server: Path, timeout: float = 600) -> None:
    log_file: Path = server / 'logs' / 'latest.log'
    deadline: float = time.monotonic() + timeout
    while time.monotonic() < deadline:
        text: str = log_file.read_text(errors='replace') if log_file.is_file() else ''
        if 'Done (' in text:
            return
        if re.search(r'Crash report|---- Minecraft Crash|Exception in server tick loop|Stopping server', text):
            raise RuntimeError('Server crashed during start; see ' + str(log_file))
        time.sleep(0.5)
    raise TimeoutError('Server did not start within ' + str(timeout) + ' s')


@contextlib.contextmanager
def launch(target: Target, output: Path | None = None, client_adapt: Path | None = None,
           server_adapt: Path | None = None, properties: list[str] | None = None,
           client_mods: list[Path] | None = None, server_mods: list[Path] | None = None,
           singleplayer: str = '') -> Iterator[Session]:
    """Creates the server and the hidden client, yields a connected-ready Session, then tears everything down. With
    singleplayer set no server is created and the client quick-plays that local world on its integrated server; a world
    that does not exist yet fails quick play, and Session.create_world then makes a new one from the world list."""
    run_name: str = f'{target.label}-{int(time.time())}-{secrets.token_hex(2)}'
    output = (output or RUNS / run_name).resolve()
    output.mkdir(parents=True, exist_ok=True)
    report: dict = {'target': target.__dict__, 'run': run_name, 'output': str(output), 'status': 'failed', 'started': time.time()}
    instance: Path = INSTANCES / (PREFIX + run_name)
    server_name: str = PREFIX + run_name
    consumer: str = 'plugin' if target.server_platform == 'paper' else target.server_platform
    mux = Multiplexor(consumer, output)
    created_server: bool = False
    created_client: bool = False
    token: str = secrets.token_hex(24)
    watchdog = Watchdog(instance, output)
    session: Session | None = None
    slot = client_slot()
    slot.__enter__()
    try:
        ensure_watcher()
        checks: dict = audit(target)
        report['audit'] = checks
        if not checks['ok']:
            raise RuntimeError('Hidden audit failed; refusing to launch: ' + json.dumps(checks)[:1500])
        client_jar: Path | None = None if str(client_adapt) == 'none' else client_adapt or adapt_mod_jar(target)
        server_jar: Path | None = None if str(server_adapt) == 'none' else server_adapt or (adapt_plugin_jar(target.minecraft) if target.server_platform == 'paper'
                                            else adapt_mod_jar(Target(target.server_platform, target.minecraft)))
        report['clientAdapt'] = str(client_jar)
        report['serverAdapt'] = str(server_jar)
        server_port: int = 0
        quick_play: list[str] = ['--quickPlaySingleplayer', singleplayer]
        if not singleplayer:
            created_server = True
            server_port = start_server(target, mux, server_name, server_jar, server_mods or [], report)
            quick_play = ['--quickPlayMultiplayer', f'127.0.0.1:{server_port}']
        bridge_jar: Path = Path(checks['bridge'])
        port: int = free_port()
        created_client = True
        prepare_game(target, instance, client_jar, bridge_jar, client_mods or [])
        report['clientInstance'] = str(instance)
        log(f'Launching hidden client {instance.name}')
        client: subprocess.Popen = launch_client(target, instance, output, port, token, quick_play, properties or [])
        watchdog.start(client.pid)
        bridge = Bridge(port, token)
        session = Session(target, bridge, output, watchdog.check)
        deadline: float = time.monotonic() + 1500
        while True:
            watchdog.check()
            try:
                state: dict = bridge.request(None, 5)
                break
            except (OSError, urllib.error.URLError):
                if client.poll() is not None:
                    raise RuntimeError(f'Client exited with {client.returncode} before the bridge came up; see client-stdout.log')
                if time.monotonic() > deadline:
                    raise TimeoutError('Client bridge did not come up; see client-stdout.log')
                time.sleep(1)
        report['initialState'] = state
        window: dict = state['window']
        if window.get('visible') or window.get('focused') or window.get('cursorDisabled'):
            raise RuntimeError('Client window is not hidden: ' + json.dumps(window))
        log('Client bridge up, window hidden and unfocused')
        (output / 'session.json').write_text(json.dumps({'port': port, 'token': token, 'server': server_name,
                                                         'serverPort': server_port, 'instance': str(instance)}, indent=2))
        yield session
        report['status'] = 'passed'
    except BaseException as failure:
        report['error'] = f'{type(failure).__name__}: {failure}'
        if session is not None:
            with contextlib.suppress(Exception):
                report['failureState'] = session.bridge.request(None, 5)
            with contextlib.suppress(Exception):
                session.bridge.request({'op': 'screenshot', 'path': str(output / 'screenshots' / 'failure.png')}, 30)
        raise
    finally:
        report['steps'] = session.steps if session else []
        watchdog.stop()
        report['watchdog'] = {'samples': watchdog.samples, 'violation': watchdog.violation, 'policies': watchdog.policies}
        report['cleanupErrors'] = teardown(target, mux, server_name if created_server else None, instance if created_client else None,
                                           session, output, token)
        if report['cleanupErrors'] and report['status'] == 'passed':
            report['status'] = 'failed'
        report['finished'] = time.time()
        (output / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
        log(f'Report {output / "report.json"} status={report["status"]}')
        slot.__exit__(None, None, None)


def start_server(target: Target, mux: Multiplexor, server_name: str, server_jar: Path | None, server_mods: list[Path],
                 report: dict) -> int:
    log(f'Server {server_name} ({target.server_platform} {target.minecraft})')
    create_lock: str = f'create-{target.server_platform}' if target.server_platform in ('forge', 'neoforge') else 'multiplexor'
    with machine_lock(create_lock):
        create: list[str] = ['server', 'create', server_name, '--type', target.server_platform, '--mc', target.minecraft, '--isolated']
        if target.server_platform in ('forge', 'neoforge'):
            installer: Path = server_installer(Target(target.server_platform, target.minecraft))
            report['serverInstaller'] = installer.name
            mux.run(*create, '--jar', str(installer))
        else:
            try:
                mux.run(*create)
            except RuntimeError:
                mux.run(*create, '--auto-build')
    with machine_lock('multiplexor', 600):
        server: Path = Path(mux.run('instance', 'path', server_name).splitlines()[-1])
        if target.java != 25:
            mux.run('runtime', 'settings', 'set-java', str(build_bridge.jdk(target.java) / 'bin' / 'java'), '--instance', server_name)
        mux.run('gameplay', 'prepare', server_name)
    server_port: int = free_port()
    configure_server(server, target, server_jar, server_port, server_mods)
    environment: dict[str, str] = {**os.environ, 'JVM_ARGS': '-XX:+UseG1GC', 'HEAP_SIZE': '3G'}
    with machine_lock('multiplexor', 600):
        subprocess.run([str(MULTIPLEXOR / 'start.sh'), '--consumer', mux.consumer, 'runtime', 'start', server_name, '--no-console'],
                       cwd=MULTIPLEXOR, env=environment, check=True, capture_output=True, timeout=300)
    wait_server(server)
    server_port = int(server_properties(server / 'server.properties')['server-port'])
    log(f'Server ready on {server_port}')
    return server_port


def teardown(target: Target, mux: Multiplexor, server_name: str | None, instance: Path | None, session: Session | None,
             output: Path, token: str) -> list[str]:
    errors: list[str] = []
    if instance is not None:
        if session is not None:
            with contextlib.suppress(Exception):
                session.bridge.request({'op': 'quit'}, 5)
        deadline: float = time.monotonic() + 40
        while client_pids(instance) and time.monotonic() < deadline:
            time.sleep(0.5)
        for sig in (signal.SIGTERM, signal.SIGKILL):
            remaining: list[int] = client_pids(instance)
            if not remaining:
                break
            errors.append(f'Client needed {sig.name}')
            for pid in remaining:
                with contextlib.suppress(ProcessLookupError):
                    os.kill(pid, sig)
            time.sleep(3)
        if client_pids(instance):
            errors.append('Client process still alive')
        else:
            game: Path = instance
            for source, name in ((game / 'logs' / 'latest.log', 'client.log'), (game / 'logs' / 'debug.log', 'client-debug.log')):
                if source.is_file():
                    (output / name).write_text(source.read_text(errors='replace').replace(token, '[token]'))
            for crash in sorted((game / 'crash-reports').glob('*.txt')) if (game / 'crash-reports').is_dir() else []:
                shutil.copy2(crash, output / crash.name)
            if instance.name.startswith(PREFIX):
                shutil.rmtree(instance, ignore_errors=True)
            if instance.exists():
                errors.append('Client instance still present')
    if server_name is not None and server_name.startswith(PREFIX):
        try:
            with machine_lock('multiplexor', 600):
                if server_name in [name.split()[0] for name in mux.run('instance', 'list').splitlines() if name.strip()]:
                    server: Path = Path(mux.run('instance', 'path', server_name).splitlines()[-1])
                    with contextlib.suppress(Exception):
                        mux.run('runtime', 'stop', server_name, '--graceful', timeout=180)
                    if (server / 'logs' / 'latest.log').is_file():
                        shutil.copy2(server / 'logs' / 'latest.log', output / 'server.log')
                    mux.run('instance', 'delete', server_name)
        except Exception as failure:
            errors.append('Server cleanup: ' + str(failure))
    return errors


def black_frame(path: Path) -> dict:
    small: Path = path.with_suffix('.probe.bmp')
    subprocess.run(['sips', '-s', 'format', 'bmp', '-Z', '64', str(path), '--out', str(small)], check=True, capture_output=True)
    data: bytes = small.read_bytes()
    small.unlink()
    offset, = struct.unpack_from('<I', data, 10)
    width, height = struct.unpack_from('<ii', data, 18)
    bits, = struct.unpack_from('<H', data, 28)
    stride: int = ((width * bits // 8) + 3) & ~3
    values: list[float] = []
    for row in range(abs(height)):
        for column in range(width):
            base: int = offset + row * stride + column * (bits // 8)
            blue, green, red = data[base], data[base + 1], data[base + 2]
            values.append(0.2126 * red + 0.7152 * green + 0.0722 * blue)
    mean: float = sum(values) / len(values)
    spread: float = (sum((value - mean) ** 2 for value in values) / len(values)) ** 0.5
    return {'meanLuma': round(mean, 1), 'lumaStdDev': round(spread, 1), 'black': mean < 8 or spread < 3}


def smoke(target: Target, args: argparse.Namespace) -> dict:
    with launch(target, client_adapt=args.client_adapt, server_adapt=args.server_adapt, properties=args.property,
                client_mods=args.client_mod, server_mods=args.server_mod) as session:
        joined: dict = session.wait_connected()
        log('Joined as ' + joined['player']['name'])
        time.sleep(3)
        if session.state()['screen'] is not None:
            session.op('close-screen')
        session.screenshot('world')
        session.command('adapt gui')
        menu: dict = session.wait_screen(timeout=30)
        screen: dict = menu['screen']
        log(f'Screen {screen["class"]} "{screen["title"]}" with {len(screen.get("slots", []))} slots')
        time.sleep(1.0)
        shot: Path = session.screenshot('adapt-gui')
        frame: dict = black_frame(shot)
        (session.output / 'adapt-gui-state.json').write_text(json.dumps(session.state(), indent=2))
        if frame['black']:
            raise AssertionError('Screenshot is a black frame: ' + json.dumps(frame))
        session.key('escape')
        session.wait_screen(none=True, timeout=15)
        return {'screen': screen['class'], 'title': screen['title'], 'screenshot': str(shot), 'frame': frame}


def exercise(target: Target, args: argparse.Namespace) -> dict:
    """Drives every control op once against the Adapt menu and records what each changed."""
    results: dict = {}
    with launch(target, client_adapt=args.client_adapt, server_adapt=args.server_adapt, properties=args.property,
                client_mods=args.client_mod, server_mods=args.server_mod) as session:
        session.wait_connected()
        time.sleep(3)
        session.command('adapt gui')
        screen: dict = session.wait_screen(timeout=30)['screen']
        slot: dict = next(entry for entry in screen['slots'] if entry.get('item'))
        session.op('mouse-move', x=slot['x'], y=slot['y'])
        results['hover'] = session.screenshot('hover')
        session.click(slot['x'], slot['y'])
        time.sleep(1.5)
        clicked: dict = session.state()['screen']
        results['afterClick'] = clicked and {'class': clicked['class'], 'title': clicked['title']}
        session.screenshot('after-click')
        scaled: dict = session.gui_scale(3)['window']
        assert scaled['guiScale'] == 3, f'GUI scale not applied: {scaled}'
        session.screenshot('gui-scale-3')
        session.gui_scale(2)
        resized: dict = session.resize(1600, 900)['window']
        session.wait(lambda state: state['window']['width'] == 1600 and state['window']['height'] == 900, 'resize', 15)
        session.screenshot('resized-1600x900')
        results['resize'] = {'requested': [1600, 900], 'reported': [resized['width'], resized['height']]}
        session.resize(WIDTH, HEIGHT)
        session.key('escape')
        session.wait_screen(none=True, timeout=15)
        session.key('e')
        inventory: dict = session.wait_screen(timeout=15)['screen']
        results['inventory'] = inventory['class']
        first: dict = inventory['slots'][9]
        last: dict = inventory['slots'][17]
        session.drag((first['x'], first['y']), (last['x'], last['y']))
        session.click(first['x'], first['y'], mods=['shift'])
        session.scroll(first['x'], first['y'], dy=-1.0)
        session.key('escape')
        session.wait_screen(none=True, timeout=15)
        session.key('t')
        chat: dict = session.wait_screen(timeout=15)['screen']
        session.type('/adapt')
        session.screenshot('chat-typed')
        session.key('escape')
        session.wait_screen(none=True, timeout=15)
        results['chat'] = chat['class']
        final: dict = session.state()
        assert not final['window']['visible'] and not final['window']['focused'], 'Window became visible or focused'
        results['finalWindow'] = final['window']
    return {key: str(value) if isinstance(value, Path) else value for key, value in results.items()}


def cleanup() -> None:
    for pid, args in processes():
        if PREFIX in args and args.split(None, 1)[0].endswith('/java') and str(INSTANCES) in args:
            log(f'Killing leftover client {pid}')
            with contextlib.suppress(ProcessLookupError):
                os.kill(pid, signal.SIGKILL)
    for instance in INSTANCES.glob(PREFIX + '*'):
        log(f'Deleting leftover instance {instance.name}')
        shutil.rmtree(instance, ignore_errors=True)
    for consumer in ('plugin', 'fabric', 'forge', 'neoforge'):
        mux = Multiplexor(consumer, Path(tempfile.gettempdir()))
        for name in mux.instances():
            if name.startswith(PREFIX):
                log(f'Deleting leftover server {name} ({consumer})')
                with machine_lock('multiplexor', 600):
                    with contextlib.suppress(Exception):
                        mux.run('runtime', 'stop', name, '--graceful', timeout=180)
                    mux.run('instance', 'delete', name)


def interrupt(signum: int, frame: object) -> None:
    raise KeyboardInterrupt('signal ' + str(signum))


def main() -> None:
    signal.signal(signal.SIGTERM, interrupt)
    signal.signal(signal.SIGINT, interrupt)
    parser = argparse.ArgumentParser(description='Hidden modded Minecraft client harness for Adapt UI checks.')
    parser.add_argument('action', choices=['smoke', 'exercise', 'hold', 'audit', 'cleanup'])
    parser.add_argument('loader', nargs='?', choices=build_bridge.LOADERS)
    parser.add_argument('minecraft', nargs='?', choices=sorted(build_bridge.TARGETS))
    parser.add_argument('--server', choices=['paper', *build_bridge.LOADERS], default='')
    parser.add_argument('--client-adapt', type=Path)
    parser.add_argument('--server-adapt', type=Path)
    parser.add_argument('--minutes', type=float, default=30.0)
    parser.add_argument('--property', action='append', default=[], metavar='KEY=VALUE',
                        help='extra client JVM system property, repeatable (for example adapt.client.debugHud=true)')
    parser.add_argument('--client-mod', action='append', default=[], type=Path, metavar='JAR',
                        help='extra mod jar for the client mods folder, repeatable (for example a recipe viewer)')
    parser.add_argument('--server-mod', action='append', default=[], type=Path, metavar='JAR',
                        help='extra mod or plugin jar for the server, repeatable (for example a library the client mod needs there)')
    args = parser.parse_args()
    if args.action == 'cleanup':
        cleanup()
        return
    if not args.loader or not args.minecraft:
        parser.error('loader and minecraft are required')
    target = Target(args.loader, args.minecraft, args.server)
    if args.action == 'audit':
        report: dict = audit(target)
        print(json.dumps(report, indent=2))
        sys.exit(0 if report['ok'] else 1)
    if args.action in ('smoke', 'exercise'):
        print(json.dumps((smoke if args.action == 'smoke' else exercise)(target, args), indent=2))
        return
    with launch(target, client_adapt=args.client_adapt, server_adapt=args.server_adapt, properties=args.property,
                client_mods=args.client_mod, server_mods=args.server_mod) as session:
        session.wait_connected()
        stop: Path = session.output / 'stop'
        log(f'Holding; bridge details in {session.output / "session.json"}; touch {stop} to finish')
        deadline: float = time.monotonic() + args.minutes * 60
        while time.monotonic() < deadline and not stop.exists():
            session.watch()
            time.sleep(1)


if __name__ == '__main__':
    main()
