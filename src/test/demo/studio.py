import contextlib
import fcntl
import functools
import json
import os
import re
import secrets
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Iterator, TextIO

ROOT: Path = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / 'src' / 'test' / 'client'))
import choreography
import run as clientqa

MC_VERSION: str = '26.2'
IRIS_WORLD: str = 'adapt_demo'
WORLD_PENDING: str = 'ADAPT_QA DEMO WORLD PENDING'
LOCATE_PENDING: str = 'ADAPT_QA DEMO LOCATE PENDING'
IRIS_PACK: str = 'overworld'
IRIS_PACK_SOURCE: Path = ROOT.parent.parent / 'IrisDimensions' / IRIS_PACK
IRIS_PACK_ENV: str = 'ADAPT_DEMO_IRIS_PACK'
SEED: int = 78264193
LOCATE_GRIDS: dict[str, tuple[int, int]] = {choreography.OVERWORLD: (6144, 256), choreography.NETHER: (1536, 64)}
LOCATE_TIMEOUT: float = 600.0
SITE_CACHE: str = 'site-cache.json'
TPS_MINIMUM: float = 19.0
TPS_TIMEOUT: float = 180.0
TPS_LINE: re.Pattern[str] = re.compile(r'TPS from last ([^:]*):(.*)')
TPS_VALUE: re.Pattern[str] = re.compile(r'\d+(?:[.,]\d+)?')
COLOR_CODES: re.Pattern[str] = re.compile(r'\u00a7[0-9a-fk-orx]|\x1b\[[0-9;]*m', re.IGNORECASE)
ACTOR: str = 'AQAClient262'
CLIENT_NAME: str = 'AdaptDemoStudio'
CLIENT_TITLE: str = 'Adapt Demo Studio'
OPPONENT: str = 'AQAOpponent'
OPPONENT_NAME: str = 'AdaptDemoOpponent'
OPPONENT_TITLE: str = 'Adapt Demo Opponent'
LAUNCHER_LOGS: tuple[str, ...] = ('launcher.log', 'opponent-launcher.log')
LOCKS: Path = ROOT / 'build' / 'demo'
LANE_SKIPS: frozenset[str] = frozenset({'.minecraft/flashback/replays', '.minecraft/flashback/temp', '.minecraft/logs', '.minecraft/saves',
                                        '.minecraft/crash-reports'})
PORT_ATTEMPTS: int = 64
BRIDGE_JAR: str = 'adapt-client-qa.jar'
ADAPT_JAR: str = 'Adapt.jar'
FIXTURE_JAR: str = 'AdaptGameplayFixture.jar'
NOTIFY_KEYS: tuple[str, ...] = ('actionbarNotifyXp', 'actionbarNotifyLevel', 'actionbarNotifyMasterLevel')
DISCOVERY_SKILL: str = 'discovery'
SKILL_SWITCH: str = 'enabled'
TOML_TABLE: re.Pattern[str] = re.compile(r'^\[', re.MULTILINE)
RCON_TIMEOUT: float = 180.0
WORLD_TIMEOUT: float = 600.0
CLIENT_TIMEOUT: float = 180.0
SKIN_TIMEOUT: float = 30.0
SKIN_POLL: float = 0.5
RUNTIME_EXIT_TIMEOUT: float = 120.0
RUNTIME_POLL: float = 1.0
START_RETRY_SECONDS: float = 5.0
WARM_UP_SET: str = 'arena'
WARM_UP_TICKS: int = 400
PRISM_LOG: Path = clientqa.PRISM / 'logs' / 'PrismLauncher-0.log'
LAUNCH_WATCH: float = 15.0
RESCAN_WAIT: float = 30.0
IRIS_LIBS: Path = ROOT.parent / 'Iris' / 'build' / 'libs'
IRIS_DENIALS: tuple[str, ...] = ('denied', 'locked', 'cannot', 'restart', 'could not find', 'already exists', 'exception raised')


class BridgeTimeout(RuntimeError):
    pass


class LaneBusy(RuntimeError):
    pass


class DemoRcon(clientqa.Rcon):
    def __init__(self, server: Path, timeout: float = RCON_TIMEOUT) -> None:
        super().__init__(server)
        self.connection.settimeout(timeout)

    def command(self, text: str) -> str:
        response: str = super().command(text)
        if 'ADAPT_QA ERROR' in response:
            raise RuntimeError(text + ': ' + response.split('ADAPT_QA ERROR', 1)[1].strip())
        return response


class DemoBridge(clientqa.Bridge):
    def request(self, operation: dict | None = None) -> dict:
        try:
            return super().request(operation)
        except urllib.error.HTTPError as failure:
            raise bridge_failure(failure, 'state' if operation is None else str(operation.get('op'))) from failure

    def export_status(self) -> dict:
        request: urllib.request.Request = urllib.request.Request(self.url + '/export-status', headers={'X-Adapt-QA-Token': self.token})
        try:
            with urllib.request.urlopen(request, timeout=10) as response:
                return json.load(response)
        except urllib.error.HTTPError as failure:
            raise bridge_failure(failure, 'export-status') from failure


def bridge_failure(failure: urllib.error.HTTPError, label: str) -> RuntimeError:
    body: str = failure.read().decode('utf-8', errors='replace')
    if failure.code == 504:
        return BridgeTimeout(label + ' timed out: ' + body)
    return RuntimeError(label + ' rejected with HTTP ' + str(failure.code) + ': ' + body)


def iris_jar(libs: Path = IRIS_LIBS) -> Path:
    candidates: list[Path] = list(libs.glob('Iris*-packed.jar'))
    if not candidates:
        raise RuntimeError('No packed Iris jar under ' + str(libs))
    return max(candidates, key=lambda path: path.stat().st_mtime)


def iris_pack() -> Path:
    pack: Path = Path(os.environ.get(IRIS_PACK_ENV) or IRIS_PACK_SOURCE)
    if not (pack / 'dimensions' / (IRIS_PACK + '.json')).is_file():
        raise RuntimeError('Iris ' + IRIS_PACK + ' pack missing at ' + str(pack) + '; set ' + IRIS_PACK_ENV + ' to its folder')
    return pack


def install_pack(source: Path, packs: Path) -> Path:
    target: Path = packs / IRIS_PACK
    shutil.copytree(source, target, ignore=shutil.ignore_patterns('.git', '.iris'), dirs_exist_ok=True)
    return target


def switch_off(config: Path, keys: tuple[str, ...]) -> None:
    text: str = config.read_text(encoding='utf-8')
    table: re.Match[str] | None = TOML_TABLE.search(text)
    split: int = len(text) if table is None else table.start()
    head: str = text[:split]
    for key in keys:
        head, count = re.subn(r'^' + re.escape(key) + r'\s*=.*$', key + ' = false', head, count=1, flags=re.MULTILINE)
        if count != 1:
            raise RuntimeError(key + ' missing from ' + str(config))
    config.write_text(head + text[split:], encoding='utf-8')


def parse_plate(reply: str) -> tuple[float, float, float]:
    marker: str = 'ADAPT_QA DEMO PLATE '
    if marker not in reply:
        raise RuntimeError('Unexpected plate reply: ' + reply)
    parts: list[str] = reply.split(marker, 1)[1].split()
    return float(parts[0]), float(parts[1]), float(parts[2])


def parse_locate(reply: str) -> tuple[int, int, str]:
    marker: str = 'ADAPT_QA DEMO LOCATE '
    if marker not in reply:
        raise RuntimeError('Unexpected locate reply: ' + reply)
    parts: list[str] = reply.split(marker, 1)[1].split()
    if len(parts) < 3:
        raise RuntimeError('Incomplete locate reply: ' + reply)
    return int(parts[0]), int(parts[1]), parts[2]


def await_world(rcon: DemoRcon, name: str, timeout: float, poll: float = 2.0) -> None:
    deadline: float = time.monotonic() + timeout
    while True:
        reply: str = rcon.command('adaptqa demo world ' + name)
        if WORLD_PENDING not in reply:
            return
        if time.monotonic() >= deadline:
            raise RuntimeError('Iris world ' + name + ' did not load within ' + str(timeout) + ' s: ' + reply.strip())
        time.sleep(poll)


def await_locate(rcon: DemoRcon, radius: int, step: int, timeout: float, poll: float = 1.0) -> tuple[int, int, str]:
    command: str = 'adaptqa demo locate ' + str(radius) + ' ' + str(step)
    deadline: float = time.monotonic() + timeout
    while True:
        reply: str = rcon.command(command)
        if LOCATE_PENDING not in reply:
            return parse_locate(reply)
        if time.monotonic() >= deadline:
            raise RuntimeError('Plate search did not finish within ' + str(timeout) + ' s: ' + reply.strip())
        time.sleep(poll)


def pack_version(pack: Path) -> str | None:
    data: object = json.loads((pack / 'dimensions' / (IRIS_PACK + '.json')).read_text(encoding='utf-8'))
    version: object = data.get('version') if isinstance(data, dict) else None
    return None if version is None else str(version)


def site_key(world: str, seed: int, version: str) -> str:
    return world + ':' + str(seed) + ':' + version


def read_sites(cache: Path) -> dict[str, object]:
    try:
        data: object = json.loads(cache.read_text(encoding='utf-8'))
    except (OSError, ValueError):
        return {}
    return data if isinstance(data, dict) else {}


def cached_site(cache: Path, key: str) -> tuple[int, int, str] | None:
    entry: object = read_sites(cache).get(key)
    if not isinstance(entry, dict):
        return None
    x: object = entry.get('x')
    z: object = entry.get('z')
    biome: object = entry.get('biome')
    if type(x) is not int or type(z) is not int or not isinstance(biome, str) or not biome:
        return None
    return x, z, biome


def store_site(cache: Path, key: str, site: tuple[int, int, str]) -> None:
    cache.parent.mkdir(parents=True, exist_ok=True)
    with mux_lock():
        sites: dict[str, object] = read_sites(cache)
        sites[key] = {'x': site[0], 'z': site[1], 'biome': site[2]}
        staging: Path = cache.with_name(cache.name + '.tmp')
        staging.write_text(json.dumps(sites, indent=2, sort_keys=True) + '\n', encoding='utf-8')
        staging.replace(cache)


def seed_server(properties: Path, seed: int) -> None:
    lines: list[str] = [line for line in properties.read_text(encoding='utf-8').splitlines() if line.split('=', 1)[0] != 'level-seed']
    properties.write_text('\n'.join(lines + ['level-seed=' + str(seed)]) + '\n', encoding='utf-8')


def parse_tps(reply: str) -> float | None:
    line: re.Match[str] | None = TPS_LINE.search(COLOR_CODES.sub('', reply))
    if line is None:
        return None
    labels: list[str] = [label.strip() for label in line.group(1).split(',')]
    values: list[str] = TPS_VALUE.findall(line.group(2))
    column: int = labels.index('1m') if '1m' in labels else 0
    return float(values[column].replace(',', '.')) if column < len(values) else None


def await_tps(rcon: DemoRcon, minimum: float, timeout: float, poll: float = 2.0) -> float | None:
    deadline: float = time.monotonic() + timeout
    while True:
        tps: float | None = parse_tps(rcon.command('tps'))
        if tps is None or tps >= minimum or time.monotonic() >= deadline:
            return tps
        time.sleep(poll)


def log_since(log: Path, offset: int) -> str:
    if not log.is_file():
        return ''
    with log.open('rb') as handle:
        size: int = handle.seek(0, os.SEEK_END)
        handle.seek(offset if offset <= size else 0)
        return handle.read().decode('utf-8', errors='replace')


def launch_prism(command: list[str], instance: str, accepted: str, sink: TextIO, log: Path, watch: float = LAUNCH_WATCH,
                 poll: float = 0.25) -> subprocess.Popen:
    offset: int = log.stat().st_size if log.is_file() else 0
    process: subprocess.Popen = subprocess.Popen(command, stdout=sink, stderr=subprocess.STDOUT)
    rejection: str = '"' + instance + '" resolves to nothing'
    deadline: float = time.monotonic() + watch
    while time.monotonic() < deadline:
        text: str = log_since(log, offset)
        if rejection in text:
            await_rescan(log, offset, instance, poll)
            print('[PRISM] running launcher rejected ' + instance + ' before rescanning; launching again', flush=True)
            return subprocess.Popen(command, stdout=sink, stderr=subprocess.STDOUT)
        if accepted in text:
            return process
        time.sleep(poll)
    return process


def await_rescan(log: Path, offset: int, instance: str, poll: float) -> None:
    found: str = 'Found instance ID "' + instance + '"'
    deadline: float = time.monotonic() + RESCAN_WAIT
    while found not in log_since(log, offset) and time.monotonic() < deadline:
        time.sleep(poll)


def prism_running() -> bool:
    listing: str = subprocess.run(['ps', '-axo', 'comm='], capture_output=True, text=True, check=True).stdout
    return any(line.strip() == str(clientqa.LAUNCHER) for line in listing.splitlines())


def await_instance_reload(log: Path, offset: int, instance: Path, *, removed: bool) -> None:
    deadline: float = time.monotonic() + RESCAN_WAIT
    previous: str = ''
    stable_since: float = time.monotonic()
    while time.monotonic() < deadline:
        text: str = log_since(log, offset)
        if text != previous:
            previous = text
            stable_since = time.monotonic()
        if removed:
            scan: str = text.rsplit('Discovering instances in "' + str(instance.parent) + '"', 1)[-1]
            if scan != text and 'Found instance ID "' + instance.name + '"' not in scan and time.monotonic() - stable_since >= 0.5:
                return
        elif any('Loaded instance ' in line and 'from "' + str(instance) + '"' in line for line in text.splitlines()):
            return
        time.sleep(0.1)
    phase: str = 'removal' if removed else 'reload'
    raise RuntimeError('Prism did not confirm ' + instance.name + ' ' + phase)


def reload_prism_instance(instance: Path, log: Path = PRISM_LOG) -> None:
    if not re.fullmatch(r'AdaptDemo(?:Studio|Opponent)(?:-[1-9][0-9]*)?', instance.name):
        raise RuntimeError('Cannot reload an unowned Prism instance: ' + instance.name)
    if clientqa.client_processes(instance):
        raise RuntimeError(instance.name + ' is running; cannot reload its settings')
    if not prism_running():
        return
    config: bytes = (instance / 'instance.cfg').read_bytes()
    staging: Path = instance.parent.parent / ('.' + instance.name + '-reload-' + secrets.token_hex(6))
    offset: int = log.stat().st_size if log.is_file() else 0
    instance.rename(staging)
    try:
        await_instance_reload(log, offset, instance, removed=True)
    finally:
        try:
            (staging / 'instance.cfg').write_bytes(config)
        finally:
            offset = log.stat().st_size if log.is_file() else 0
            staging.rename(instance)
    await_instance_reload(log, offset, instance, removed=False)


def build_environment() -> dict[str, str]:
    environment: dict[str, str] = dict(os.environ)
    environment.pop('GIT_CONFIG_COUNT', None)
    return environment


def lane_folder(lane: int, base: str = CLIENT_NAME) -> str:
    return base if lane == 1 else base + '-' + str(lane)


def lane_title(lane: int, base: str = CLIENT_TITLE) -> str:
    return base if lane == 1 else base + ' ' + str(lane)


def lane_lock(lane: int) -> Path:
    return LOCKS / ('lane-' + str(lane) + '.lock')


def clone_lane(base: Path, target: Path, title: str) -> bool:
    if target.exists():
        return False
    if not (base / 'instance.cfg').is_file():
        raise RuntimeError(str(base) + ' has no instance.cfg; run lane 1 once before cloning it to ' + target.name)
    staging: Path = target.with_name('.' + target.name + '.copy')
    shutil.rmtree(staging, ignore_errors=True)
    copy: Path = staging / target.name
    shutil.copytree(base, copy, symlinks=True, ignore=functools.partial(lane_skips, base))
    retitle(copy / 'instance.cfg', title)
    copy.rename(target)
    staging.rmdir()
    return True


def lane_skips(base: Path, folder: str, names: list[str]) -> set[str]:
    prefix: str = Path(folder).relative_to(base).as_posix()
    return {name for name in names if (name if prefix == '.' else prefix + '/' + name) in LANE_SKIPS}


def retitle(config: Path, title: str) -> None:
    lines: list[str] = [line for line in config.read_text(errors='replace').splitlines() if line.split('=', 1)[0].strip() != 'uuid']
    config.write_text('\n'.join(lines) + '\n')
    clientqa.set_instance_keys(config, {'name': title})


def claim(path: Path, label: str) -> TextIO:
    path.parent.mkdir(parents=True, exist_ok=True)
    handle: TextIO = path.open('a')
    try:
        fcntl.flock(handle.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError:
        handle.close()
        raise LaneBusy(label + ' is in use by another demo run') from None
    return handle


@contextlib.contextmanager
def file_lock(name: str) -> Iterator[None]:
    LOCKS.mkdir(parents=True, exist_ok=True)
    with (LOCKS / name).open('a') as handle:
        fcntl.flock(handle.fileno(), fcntl.LOCK_EX)
        try:
            yield
        finally:
            fcntl.flock(handle.fileno(), fcntl.LOCK_UN)


def mux_lock() -> contextlib.AbstractContextManager[None]:
    return file_lock('mux.lock')


def build_lock() -> contextlib.AbstractContextManager[None]:
    return file_lock('build.lock')


def built_jars() -> list[Path]:
    plugins: Path = ROOT / 'build' / 'gameplay' / 'plugins'
    return [ROOT / 'build' / 'client-qa' / BRIDGE_JAR, plugins / ADAPT_JAR, plugins / FIXTURE_JAR]


def mux(*arguments: str) -> str:
    with mux_lock():
        return clientqa.mux(*arguments)


def runtime_state(reply: str) -> tuple[bool, int | None]:
    fields: dict[str, str] = {}
    for line in reply.splitlines():
        label, separator, value = line.partition(':')
        if separator:
            fields[label.strip()] = value.strip()
    pid: str = fields.get('server pid', '')
    return fields.get('mode', 'stopped') != 'stopped', int(pid) if pid.isdigit() else None


def await_runtime_exit(name: str, pid: int | None, timeout: float, poll: float = RUNTIME_POLL) -> None:
    deadline: float = time.monotonic() + timeout
    while True:
        running: bool = runtime_state(mux('runtime', 'status', name))[0]
        if not running and (pid is None or not process_alive(pid)):
            return
        if time.monotonic() >= deadline:
            raise RuntimeError('Server ' + name + ' was still running ' + format(timeout, 'g') + ' s after runtime stop'
                               + ('' if pid is None else ' (pid ' + str(pid) + ')'))
        time.sleep(poll)


def reserve_port() -> int:
    with mux_lock():
        ledger: dict[str, int] = {port: pid for port, pid in read_ports().items() if process_alive(pid)}
        for _ in range(PORT_ATTEMPTS):
            port: int = clientqa.free_port()
            if str(port) not in ledger:
                ledger[str(port)] = os.getpid()
                write_ports(ledger)
                return port
    raise RuntimeError('No free port outside the ' + str(len(ledger)) + ' reserved lane ports after ' + str(PORT_ATTEMPTS) + ' attempts')


def release_ports(ports: list[int]) -> None:
    with mux_lock():
        ledger: dict[str, int] = read_ports()
        for port in ports:
            if ledger.get(str(port)) == os.getpid():
                del ledger[str(port)]
        write_ports(ledger)


def read_ports() -> dict[str, int]:
    try:
        data: object = json.loads((LOCKS / 'ports.json').read_text())
        return {str(port): int(pid) for port, pid in dict(data).items()}
    except (OSError, TypeError, ValueError):
        return {}


def write_ports(ledger: dict[str, int]) -> None:
    staging: Path = LOCKS / 'ports.json.tmp'
    staging.write_text(json.dumps(ledger, indent=2, sort_keys=True) + '\n')
    staging.replace(LOCKS / 'ports.json')


def await_connected(bridge: DemoBridge, label: str, timeout: float = CLIENT_TIMEOUT) -> None:
    deadline: float = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            if bridge.state().get('connected'):
                print('[READY] Minecraft 26.2 client ' + label, flush=True)
                return
        except (OSError, BridgeTimeout):
            pass
        time.sleep(0.5)
    raise RuntimeError(label + ' did not connect within ' + format(timeout, 'g') + ' s')


def await_bridge(bridge: DemoBridge, label: str, timeout: float = CLIENT_TIMEOUT) -> None:
    deadline: float = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            bridge.state()
            print('[READY] Minecraft 26.2 replay client ' + label, flush=True)
            return
        except (OSError, BridgeTimeout):
            pass
        time.sleep(0.5)
    raise RuntimeError(label + ' bridge did not respond within ' + format(timeout, 'g') + ' s')


def await_skin(bridge: DemoBridge, deadline: float, poll: float = SKIN_POLL) -> bool:
    while True:
        try:
            if bridge.state().get('skinLoaded'):
                return True
        except (OSError, BridgeTimeout):
            pass
        if time.monotonic() >= deadline:
            return False
        time.sleep(poll)


def quit_client(port: int, token: str, instance: Path, label: str) -> list[str]:
    try:
        return clientqa.shutdown_client(clientqa.Bridge(port, token), instance)
    except Exception as failure:
        return [label + ' shutdown: ' + repr(failure)]


def process_alive(pid: int) -> bool:
    try:
        os.kill(pid, 0)
    except ProcessLookupError:
        return False
    except PermissionError:
        return True
    return True


class Studio:
    def __init__(self, output: Path, skip_build: bool, lane: int = 1) -> None:
        suffix: str = str(int(time.time())) + '-' + secrets.token_hex(3)
        self.output: Path = output
        self.jars: Path = output / 'jars'
        self.skip_build: bool = skip_build
        self.lane: int = lane
        self.client_name: str = lane_folder(lane)
        self.client_title: str = lane_title(lane)
        self.suffix: str = suffix
        self.server_name: str = 'adapt-demo-' + suffix
        self.instance: Path = clientqa.PRISM / 'instances' / self.client_name
        self.ports: list[int] = []
        self.game: Path = self.instance / '.minecraft'
        self.server: Path | None = None
        self.token: str = secrets.token_hex(24)
        self.bridge_port: int = 0
        self.rcon: DemoRcon | None = None
        self.bridge: DemoBridge | None = None
        self.connected_at: float = 0.0
        self.origin: tuple[float, float, float] = (0.0, 0.0, 0.0)
        self.biome: str = ''
        self.actor: str = ACTOR
        self.opponent: str = OPPONENT
        self.opponent_name: str = lane_folder(lane, OPPONENT_NAME)
        self.opponent_title: str = lane_title(lane, OPPONENT_TITLE)
        self.opponent_instance: Path = clientqa.PRISM / 'instances' / self.opponent_name
        self.opponent_token: str = secrets.token_hex(24)
        self.opponent_port: int = 0
        self.opponent_bridge: DemoBridge | None = None
        self.created_server: bool = False
        self.launched_client: bool = False
        self.launched_opponent: bool = False

    def start(self, skill: str, opponent: bool = False, world: str = choreography.OVERWORLD) -> None:
        self.output.mkdir(parents=True, exist_ok=True)
        mods: list[Path] = self.prepare_jars()
        self.start_server(skill)
        self.prepare_world(world)
        self.launch_client(mods)
        if opponent:
            self.launch_opponent(mods)
        self.warm_up()
        self.await_skin()

    def start_replay(self) -> None:
        self.output.mkdir(parents=True, exist_ok=True)
        mods: list[Path] = self.prepare_jars(client_only=True)
        self.launch_client(mods, replay_only=True)

    def prepare_jars(self, *, client_only: bool = False) -> list[Path]:
        with build_lock():
            if not self.skip_build and not client_only:
                self.build(['./gradlew', 'build', 'prepareGameplay'], 'build.log')
            mods: list[Path] = clientqa.ensure_mods(ROOT)
            self.build(['bash', str(ROOT / 'src' / 'test' / 'client' / 'build.sh')], 'client-build.log')
            self.jars.mkdir(parents=True, exist_ok=True)
            jars: list[Path] = [ROOT / 'build' / 'client-qa' / BRIDGE_JAR] if client_only else built_jars()
            for jar in jars:
                shutil.copy2(jar, self.jars / jar.name)
        return mods

    def build(self, command: list[str], log_name: str) -> None:
        with (self.output / log_name).open('w') as log:
            subprocess.run(command, cwd=ROOT, env=build_environment(), stdout=log, stderr=subprocess.STDOUT, check=True)

    def start_server(self, skill: str) -> None:
        mux('server', 'create', self.server_name, '--type', 'paper', '--mc', MC_VERSION, '--isolated')
        self.created_server = True
        mux('instance', 'port', self.server_name, str(self.claim_port()))
        mux('gameplay', 'prepare', self.server_name)
        self.server = Path(mux('instance', 'path', self.server_name))
        clientqa.configure_server(self.server, self.jars / ADAPT_JAR)
        seed_server(self.server / 'server.properties', SEED)
        plugins: Path = self.server / 'plugins'
        shutil.copy2(self.jars / FIXTURE_JAR, plugins / FIXTURE_JAR)
        shutil.copy2(iris_jar(), plugins / 'Iris.jar')
        install_pack(iris_pack(), plugins / 'Iris' / 'packs')
        mux('runtime', 'start', self.server_name, '--no-console')
        clientqa.wait_server(self.server)
        self.restart_server(skill)
        self.rcon = DemoRcon(self.server)
        print('[READY] Isolated Paper 26.2 server ' + self.server_name, flush=True)

    def restart_server(self, skill: str) -> None:
        pid: int | None = runtime_state(mux('runtime', 'status', self.server_name))[1]
        mux('runtime', 'stop', self.server_name, '--graceful')
        await_runtime_exit(self.server_name, pid, RUNTIME_EXIT_TIMEOUT)
        latest: Path = self.server / 'logs' / 'latest.log'
        if latest.is_file():
            install_log: Path = self.log_path('server-install')
            install_log.parent.mkdir(parents=True, exist_ok=True)
            shutil.move(str(latest), str(install_log))
        adapt: Path = self.server / 'plugins' / 'Adapt'
        switch_off(adapt / 'adapt.toml', NOTIFY_KEYS)
        if skill != DISCOVERY_SKILL:
            switch_off(adapt / 'skills' / (DISCOVERY_SKILL + '.toml'), (SKILL_SWITCH,))
        self.start_runtime()
        clientqa.wait_server(self.server)

    def start_runtime(self) -> None:
        try:
            mux('runtime', 'start', self.server_name, '--no-console')
        except RuntimeError as failure:
            print('[RESTART] ' + str(failure) + '; starting again in ' + format(START_RETRY_SECONDS, 'g') + ' s', flush=True)
            time.sleep(START_RETRY_SECONDS)
            mux('runtime', 'start', self.server_name, '--no-console')

    def prepare_world(self, world: str) -> None:
        if world == choreography.OVERWORLD:
            self.create_iris_world()
        await_world(self.rcon, world, WORLD_TIMEOUT)
        located: tuple[int, int, str] = self.plate_site(world)
        self.origin = parse_plate(self.rcon.command('adaptqa demo plate ' + str(located[0]) + ' ' + str(located[1])))
        self.biome = located[2]
        print('[READY] World ' + world + ' plated at ' + self.plate_label(), flush=True)

    def create_iris_world(self) -> None:
        reply: str = self.rcon.command('iris create name=' + IRIS_WORLD + ' type=' + IRIS_PACK + ' seed=' + str(SEED))
        print('[IRIS] ' + (reply.strip() or 'create accepted'), flush=True)
        if any(denial in reply.lower() for denial in IRIS_DENIALS):
            raise RuntimeError('Iris refused world creation: ' + reply.strip())

    def plate_site(self, world: str) -> tuple[int, int, str]:
        version: str | None = pack_version(iris_pack()) if world == choreography.OVERWORLD else MC_VERSION
        key: str | None = None if version is None else site_key(world, SEED, version)
        cache: Path = LOCKS / SITE_CACHE
        cached: tuple[int, int, str] | None = None if key is None else cached_site(cache, key)
        if cached is not None:
            print('[LOCATE] ' + ' '.join(str(value) for value in cached) + ' from ' + str(cache), flush=True)
            return cached
        started: float = time.monotonic()
        grid: tuple[int, int] = LOCATE_GRIDS[world]
        located: tuple[int, int, str] = await_locate(self.rcon, grid[0], grid[1], LOCATE_TIMEOUT)
        print('[LOCATE] ' + ' '.join(str(value) for value in located) + ' in ' + format(time.monotonic() - started, '.1f') + ' s', flush=True)
        if key is not None:
            store_site(cache, key, located)
        return located

    def plate_label(self) -> str:
        if not self.biome:
            return ''
        return ' '.join(format(value, 'g') for value in self.origin) + ' in ' + self.biome

    def launch_client(self, mods: list[Path], *, replay_only: bool = False) -> None:
        if clientqa.client_processes(self.instance):
            raise RuntimeError(self.client_name + ' is already running; close it before recording')
        if self.lane > 1 and clone_lane(self.instance.with_name(CLIENT_NAME), self.instance, self.client_title):
            print('[PRISM] cloned ' + CLIENT_NAME + ' to ' + str(self.instance), flush=True)
        self.bridge_port = self.claim_port()
        self.bridge = DemoBridge(self.bridge_port, self.token)
        self.launched_client = True
        self.open_client(self.instance, self.client_title, self.bridge_port, self.token, self.actor, mods, LAUNCHER_LOGS[0], replay_only=replay_only)
        if replay_only:
            await_bridge(self.bridge, self.client_name)
        else:
            await_connected(self.bridge, self.client_name)
        self.connected_at = time.monotonic()
        self.fit_window()

    def fit_window(self) -> None:
        try:
            self.bridge.command('fit-window', width=clientqa.WINDOW_WIDTH, height=clientqa.WINDOW_HEIGHT)
        except RuntimeError as failure:
            print('[WINDOW] warning: ' + str(failure) + '; live pov captures are cropped and scaled to ' + str(clientqa.WINDOW_WIDTH) + 'x'
                  + str(clientqa.WINDOW_HEIGHT), flush=True)

    def launch_opponent(self, mods: list[Path]) -> None:
        if clientqa.client_processes(self.opponent_instance):
            raise RuntimeError(self.opponent_name + ' is already running; close it before recording')
        base: Path = self.opponent_instance.with_name(OPPONENT_NAME)
        if clone_lane(self.opponent_instance.with_name(CLIENT_NAME), base, OPPONENT_TITLE):
            print('[PRISM] cloned ' + CLIENT_NAME + ' to ' + str(base), flush=True)
        if self.lane > 1 and clone_lane(base, self.opponent_instance, self.opponent_title):
            print('[PRISM] cloned ' + OPPONENT_NAME + ' to ' + str(self.opponent_instance), flush=True)
        self.opponent_port = self.claim_port()
        bridge: DemoBridge = DemoBridge(self.opponent_port, self.opponent_token)
        self.launched_opponent = True
        self.open_client(self.opponent_instance, self.opponent_title, self.opponent_port, self.opponent_token, self.opponent, mods, LAUNCHER_LOGS[1])
        await_connected(bridge, self.opponent_name)
        self.opponent_bridge = bridge

    def open_client(self, instance: Path, title: str, port: int, token: str, player: str, mods: list[Path], launcher_log: str, *, replay_only: bool = False) -> None:
        created: bool = clientqa.prepare_instance(instance, title, port, token, self.output, mods, self.jars / BRIDGE_JAR)
        reload_prism_instance(instance)
        print('[PRISM] ' + ('created ' if created else 'refreshed ') + str(instance), flush=True)
        command: list[str] = [str(clientqa.LAUNCHER), '--launch', instance.name, '--offline', player]
        if not replay_only:
            command.extend(['--server', '127.0.0.1:' + self.server_port()])
        with (self.output / launcher_log).open('w') as sink:
            launch_prism(command, instance.name, token, sink, PRISM_LOG)

    def warm_up(self) -> None:
        self.rcon.command('adaptqa demo set ' + WARM_UP_SET)
        self.rcon.command('adaptqa demo actor ' + self.actor)
        if self.opponent_bridge is not None:
            self.rcon.command('adaptqa demo actor ' + self.opponent + ' opponent')
        target: int = int(self.bridge.state()['ticks']) + WARM_UP_TICKS
        while int(self.bridge.state()['ticks']) < target:
            time.sleep(0.25)
        print('[READY] Plate chunks loaded after a ' + str(WARM_UP_TICKS) + '-tick warm-up', flush=True)

    def await_skin(self) -> None:
        loaded: bool = await_skin(self.bridge, self.connected_at + SKIN_TIMEOUT)
        waited: str = format(time.monotonic() - self.connected_at, '.1f') + ' s after the client connected'
        if loaded:
            print('[SKIN] loaded ' + waited, flush=True)
        else:
            print('[SKIN] default ' + waited + ': the session lookup did not answer, so the actor wears a default skin', flush=True)

    def await_tps(self) -> None:
        started: float = time.monotonic()
        tps: float | None = await_tps(self.rcon, TPS_MINIMUM, TPS_TIMEOUT)
        waited: str = format(time.monotonic() - started, '.1f') + ' s'
        if tps is None:
            print('[TPS] warning: no 1-minute TPS in the tps reply; recording without waiting', flush=True)
        elif tps < TPS_MINIMUM:
            print('[TPS] warning: 1-minute TPS ' + format(tps, 'g') + ' is below ' + format(TPS_MINIMUM, 'g') + ' after ' + waited + '; recording anyway',
                  flush=True)
        else:
            print('[TPS] 1-minute TPS ' + format(tps, 'g') + ' after ' + waited, flush=True)

    def claim_port(self) -> int:
        port: int = reserve_port()
        self.ports.append(port)
        return port

    def log_path(self, kind: str) -> Path:
        return self.output / 'logs' / (self.suffix + '-' + kind + '.log')

    def server_port(self) -> str:
        for line in (self.server / 'server.properties').read_text().splitlines():
            if line.startswith('server-port='):
                return line.split('=', 1)[1]
        raise RuntimeError('server-port missing from ' + str(self.server / 'server.properties'))

    def stop(self) -> list[str]:
        errors: list[str] = []
        if self.rcon is not None:
            self.rcon.close()
            self.rcon = None
        if self.launched_client:
            errors.extend(quit_client(self.bridge_port, self.token, self.instance, 'Client'))
        if self.launched_opponent:
            errors.extend('Opponent: ' + error for error in quit_client(self.opponent_port, self.opponent_token, self.opponent_instance, 'Client'))
            self.opponent_bridge = None
        if self.created_server:
            errors.extend(self.remove_server())
        if self.launched_client:
            self.launched_client = False
            errors.extend(self.collect_client_log(self.game, 'client'))
        if self.launched_opponent:
            self.launched_opponent = False
            errors.extend(self.collect_client_log(self.opponent_instance / '.minecraft', 'opponent'))
        errors.extend(self.release_ports())
        self.redact_launcher_logs()
        return errors

    def remove_server(self) -> list[str]:
        self.created_server = False
        try:
            mux('runtime', 'stop', self.server_name, '--graceful')
            if self.server is not None and (self.server / 'logs' / 'latest.log').is_file():
                self.log_path('server').parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(self.server / 'logs' / 'latest.log', self.log_path('server'))
            mux('instance', 'delete', self.server_name)
        except Exception as failure:
            return ['Server cleanup: ' + repr(failure)]
        return []

    def release_ports(self) -> list[str]:
        if not self.ports:
            return []
        ports: list[int] = self.ports
        self.ports = []
        try:
            release_ports(ports)
        except OSError as failure:
            return ['Port release: ' + repr(failure)]
        return []

    def collect_client_log(self, game: Path, kind: str) -> list[str]:
        try:
            if (game / 'logs' / 'latest.log').is_file():
                self.log_path(kind).parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(game / 'logs' / 'latest.log', self.log_path(kind))
        except OSError as failure:
            return [kind.capitalize() + ' log: ' + repr(failure)]
        return []

    def redact_launcher_logs(self) -> None:
        for name in LAUNCHER_LOGS:
            launcher_log: Path = self.output / name
            if launcher_log.is_file():
                text: str = launcher_log.read_text(errors='replace')
                launcher_log.write_text(text.replace(self.token, '[REDACTED]').replace(self.opponent_token, '[REDACTED]'))
