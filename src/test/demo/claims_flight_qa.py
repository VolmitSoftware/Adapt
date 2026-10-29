import argparse
import contextlib
import json
import signal
import sys
import time
import traceback
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable, Iterator

from studio import (CURSOR_NORMAL, LOCKS, ROOT, SEED, SITE_CACHE, DemoBridge, DemoRcon, LaneBusy,
                    Studio, cached_site, claim, iris_pack, lane_lock, pack_version, site_key)

SNAPSHOT_PREFIX: str = 'ADAPT_QA DEMO CLAIMS flight-snapshot '
APPROVED_SITE: tuple[int, int, str] = (-1280, 5120, 'temperate/plains')
POLL_SECONDS: float = 0.1


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def number(snapshot: dict, key: str) -> float:
    value: object = snapshot.get(key)
    require(type(value) in (int, float), 'Missing numeric observation: ' + key)
    return float(value)


def parse_snapshot(reply: str) -> dict:
    require(SNAPSHOT_PREFIX in reply, 'Claims flight snapshot missing: ' + reply)
    snapshot: object = json.loads(reply.split(SNAPSHOT_PREFIX, 1)[1].strip())
    require(isinstance(snapshot, dict), 'Claims flight snapshot must be an object')
    return snapshot


def hidden_state(state: dict) -> dict:
    for key, expected in (('ok', True), ('connected', True), ('hiddenRenderer', True),
                          ('windowVisible', False), ('windowFocused', False), ('mouseGrabbed', False), ('paused', False)):
        require(state.get(key) is expected, 'Hidden client requires ' + key + '=' + str(expected))
    require(state.get('cursorMode') == CURSOR_NORMAL, 'Hidden client captured the cursor')
    require(state.get('renderWidth') == 1920 and state.get('renderHeight') == 1080, 'Hidden framebuffer must remain 1920x1080')
    require(state.get('screen') is None, 'A client screen is blocking gameplay: ' + str(state.get('screen')))
    return {key: state.get(key) for key in ('processId', 'ticks', 'frames', 'connected', 'hiddenRenderer',
                                          'windowVisible', 'windowFocused', 'mouseGrabbed', 'cursorMode', 'renderWidth', 'renderHeight')}


def check_boundary(samples: list[dict]) -> None:
    require(bool(samples), 'No boundary observations')
    first: dict = samples[0]
    boundary: float = number(first, 'minimumX')
    for sample in samples:
        require(sample.get('bypass') is False, 'Boundary actor has bypass')
        require(sample.get('claimed') is False and number(sample, 'x') < boundary, 'Airborne actor entered the city')
        require(number(sample, 'boundaryEntries') == 0, 'A real airborne entry was allowed between observations')
        require(number(sample, 'minimumZ') <= number(sample, 'z') < number(sample, 'maximumZExclusive'),
                'Flight missed the city along Z')
        require(number(sample, 'health') >= number(first, 'health'), 'Boundary rejection caused damage')
    require(any(sample.get('gliding') is True for sample in samples), 'Real client never started gliding')
    require(number(samples[-1], 'boundaryDenials') > 0, 'No real airborne crossing was rejected')
    require(max(number(sample, 'x') for sample in samples) >= boundary - 1.75, 'Flight did not reach the boundary')
    require(max(number(sample, 'x') for sample in samples) - number(first, 'x') >= 4, 'Actor did not approach the boundary')
    require(number(samples[-1], 'rockets') < number(first, 'rockets'), 'Client did not use a firework while gliding')


def check_inside(samples: list[dict], mode: str) -> None:
    require(mode in ('flight', 'glide') and bool(samples), 'Invalid inside-flight observations')
    for sample in samples:
        require(sample.get('bypass') is False, 'Inside actor has bypass')
        require(sample.get('claimed') is True and sample.get('member') is True, 'Actor left the member-owned city')
        require(sample.get('flying') is False and sample.get('gliding') is False, 'Forbidden flight started inside the city')
        require(sample.get('allowFlight') is (mode == 'flight'), 'Fixture flight capability does not match the case')
    requests: float = number(samples[-1], mode + 'Requests')
    require(requests > 0, 'Real client made no ' + mode + ' request')
    require(number(samples[-1], mode + 'Denials') == requests, 'A real ' + mode + ' request was allowed inside the city')


@contextlib.contextmanager
def wall_limit(seconds: float) -> Iterator[None]:
    def expired(signum: int, frame: object) -> None:
        raise TimeoutError('Claims flight QA exceeded its wall-clock limit')

    previous: object = signal.signal(signal.SIGALRM, expired)
    signal.setitimer(signal.ITIMER_REAL, seconds)
    try:
        yield
    finally:
        signal.setitimer(signal.ITIMER_REAL, 0)
        signal.signal(signal.SIGALRM, previous)


class FlightQa:
    def __init__(self, studio: Studio, report: dict) -> None:
        require(studio.bridge is not None and studio.rcon is not None, 'Studio has no connected client')
        self.studio: Studio = studio
        self.bridge: DemoBridge = studio.bridge
        self.rcon: DemoRcon = studio.rcon
        self.report: dict = report
        self.first_client: dict | None = None
        self.last_client: dict | None = None
        self.frame_changed_at: float = time.monotonic()
        self.samples: list[dict] = []
        self.inputs: list[dict] = []
        self.case_started: float = time.monotonic()

    def client(self) -> dict:
        state: dict = self.bridge.state(timeout=3)
        observation: dict = hidden_state(state)
        if self.first_client is None:
            self.first_client = observation
            self.report['clientStart'] = observation
        if self.last_client is not None:
            require(state.get('processId') == self.last_client.get('processId'), 'Client process changed during QA')
            require(number(state, 'frames') >= number(self.last_client, 'frames'), 'Render frame counter went backwards')
            if number(state, 'frames') > number(self.last_client, 'frames'):
                self.frame_changed_at = time.monotonic()
            require(time.monotonic() - self.frame_changed_at < 3, 'Hidden client stopped rendering')
        self.last_client = observation
        self.report['clientEnd'] = observation
        return state

    def wait_ticks(self, ticks: int) -> None:
        target: float = number(self.client(), 'ticks') + ticks
        deadline: float = time.monotonic() + max(3, ticks / 10)
        while number(self.client(), 'ticks') < target:
            require(time.monotonic() < deadline, 'Client tick wait timed out')
            time.sleep(0.025)

    def snapshot(self) -> dict:
        self.client()
        result: dict = parse_snapshot(self.rcon.command('adaptqa demo claims ' + self.studio.actor + ' flight-snapshot'))
        require(not result.get('error'), 'Fixture failed: ' + str(result.get('error')))
        require(result.get('bypass') is False, 'Claims actor acquired bypass')
        result['elapsedSeconds'] = round(time.monotonic() - self.case_started, 3)
        self.samples.append(result)
        return result

    def until(self, predicate: Callable[[dict], bool], label: str, timeout: float) -> dict:
        deadline: float = time.monotonic() + timeout
        while True:
            observed: dict = self.snapshot()
            if predicate(observed):
                return observed
            require(time.monotonic() < deadline, label + ' timed out')
            time.sleep(POLL_SECONDS)

    def prepare(self, phase: str) -> dict:
        self.bridge.command('release')
        reply: str = self.rcon.command('adaptqa demo claims ' + self.studio.actor + ' ' + phase)
        require('PREPARING' in reply, 'Fixture did not start preparation: ' + reply)
        ready: dict = self.until(lambda state: state.get('ready') is True and state.get('phase') == phase,
                                 phase + ' readiness', 8)
        self.samples.clear()
        self.samples.append(ready)
        deadline: float = time.monotonic() + 1
        chest: str = 'minecraft:iron_chestplate' if phase == 'flight-inside-prepare' else 'minecraft:elytra'
        while True:
            state: dict = self.client()
            if any(item.get('index') == 38 and item.get('item') == chest for item in state.get('inventory', [])):
                break
            require(time.monotonic() < deadline, 'Real client has not equipped ' + chest)
            time.sleep(0.025)
        self.bridge.command('slot', index=0)
        return ready

    def jump(self) -> None:
        self.bridge.command('keys', jump=True, leaseTicks=2)
        self.wait_ticks(2)
        self.bridge.command('keys', jump=False, leaseTicks=2)
        self.wait_ticks(2)

    def double_jump(self) -> None:
        first: dict = self.bridge.command('keys', jump=True, leaseTicks=1)
        deadline: float = time.monotonic() + 2
        while number(self.client(), 'ticks') < number(first, 'ticks') + 1:
            require(time.monotonic() < deadline, 'First jump lease did not expire')
            time.sleep(0.01)
        second: dict = self.bridge.command('keys', jump=True, leaseTicks=1)
        gap: float = number(second, 'ticks') - number(first, 'ticks')
        self.inputs.append({'action': 'double-jump', 'firstTick': first['ticks'], 'secondTick': second['ticks'], 'gapTicks': gap})
        require(2 <= gap <= 6, 'Double-jump input missed the flight toggle window: ' + str(gap) + ' ticks')

    def boundary(self) -> None:
        start: dict = self.prepare('flight-prepare')
        require(start.get('claimed') is False and start.get('allowFlight') is False, 'Launch must be outside with flight disabled')
        self.bridge.command('look', yaw=-90, pitch=-12)
        self.jump()
        self.until(lambda state: state.get('gliding') is True, 'Elytra launch', 2)
        self.bridge.command('click', key='use')
        self.bridge.command('keys', forward=True, leaseTicks=100)
        self.until(lambda state: number(state, 'boundaryDenials') >= 2, 'Airborne boundary rejection', 5)
        deadline: float = time.monotonic() + 1
        while time.monotonic() < deadline:
            self.snapshot()
            time.sleep(POLL_SECONDS)
        check_boundary(self.samples)

    def inside(self, mode: str) -> None:
        self.prepare('flight-inside-prepare' if mode == 'flight' else 'glide-inside-prepare')
        if mode == 'flight':
            self.until(lambda state: state.get('onGround') is True, 'Ordinary flight grounded setup', 2)
            self.wait_ticks(4)
            self.double_jump()
        else:
            self.jump()
        self.until(lambda state: number(state, mode + 'Denials') > 0, 'Inside ' + mode + ' denial', 2)
        self.wait_ticks(8)
        self.snapshot()
        check_inside(self.samples, mode)

    def run_case(self, name: str, action: Callable[[], None]) -> None:
        self.samples = []
        self.inputs = []
        self.case_started = time.monotonic()
        case: dict = {'name': name, 'status': 'failed', 'samples': self.samples, 'inputs': self.inputs}
        self.report['cases'].append(case)
        try:
            with wall_limit(30):
                action()
            case['status'] = 'passed'
        except Exception as failure:
            case['error'] = str(failure)
            raise
        finally:
            self.bridge.command('release')
            case['elapsedSeconds'] = round(time.monotonic() - self.case_started, 3)

    def run(self) -> None:
        self.client()
        self.wait_ticks(4)
        require(number(self.last_client, 'frames') > number(self.first_client, 'frames'), 'Hidden frames did not advance')
        self.rcon.command('adaptqa demo set claims-founding')
        self.rcon.command('adaptqa demo actor ' + self.studio.actor)
        self.rcon.command('adapt clear adaptations player=' + self.studio.actor)
        self.bridge.command('clear-events')
        self.run_case('elytra-boundary', self.boundary)
        self.run_case('glide-inside', lambda: self.inside('glide'))
        self.run_case('ordinary-flight-inside', lambda: self.inside('flight'))


def approved_site() -> None:
    version: str | None = pack_version(iris_pack())
    require(version == '4013', 'Claims flight QA requires the approved Iris pack version 4013')
    cached: tuple[int, int, str] | None = cached_site(LOCKS / SITE_CACHE, site_key('iris:adapt_demo', SEED, version))
    require(cached == APPROVED_SITE, 'Claims flight QA requires the approved site cache at -1280,5120')


def execute(args: argparse.Namespace, output: Path) -> int:
    report: dict = {'version': 1, 'proof': 'real-client-input', 'startedAt': datetime.now(timezone.utc).isoformat(),
                    'status': 'failed', 'cases': [], 'cleanupErrors': []}
    studio: Studio = Studio(output, args.skip_build, args.lane)
    try:
        approved_site()
        with wall_limit(1800):
            studio.start('claims')
        require((studio.origin[0], studio.origin[2], studio.biome) == APPROVED_SITE, 'Studio used a different scene')
        require(studio.rcon is not None, 'Studio RCON unavailable')
        studio.rcon.connection.settimeout(5)
        report['server'] = studio.server_name
        report['origin'] = studio.origin
        FlightQa(studio, report).run()
        report['status'] = 'passed'
    except (Exception, KeyboardInterrupt) as failure:
        report['error'] = repr(failure)
        traceback.print_exc()
    finally:
        errors: list[str] = report['cleanupErrors']
        if studio.bridge is not None:
            try:
                studio.bridge.command('release')
            except Exception as failure:
                errors.append('Release input: ' + repr(failure))
                traceback.print_exc()
        if studio.rcon is not None:
            try:
                studio.rcon.command('adaptqa demo reset')
            except Exception as failure:
                errors.append('Reset scene: ' + repr(failure))
                traceback.print_exc()
        try:
            with wall_limit(300):
                errors.extend(studio.stop())
        except Exception as failure:
            errors.append('Studio cleanup: ' + repr(failure))
            traceback.print_exc()
        if errors:
            report['status'] = 'failed'
        report['finishedAt'] = datetime.now(timezone.utc).isoformat()
        target: Path = output / 'report.json'
        staging: Path = target.with_suffix('.json.part')
        staging.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
        staging.replace(target)
        print('Claims flight QA: ' + report['status'] + '; report: ' + str(target), flush=True)
    return 0 if report['status'] == 'passed' else 1


def main() -> int:
    parser: argparse.ArgumentParser = argparse.ArgumentParser(description='Check Claims flight restrictions through the hidden real-client studio.')
    parser.add_argument('--lane', type=int, default=1)
    parser.add_argument('--output', type=Path, default=ROOT / 'build' / 'demo' / 'claims-flight-qa')
    parser.add_argument('--skip-build', action='store_true')
    args: argparse.Namespace = parser.parse_args()
    if args.lane < 1:
        parser.error('--lane must be positive')
    output: Path = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    try:
        with contextlib.ExitStack() as locks:
            locks.enter_context(claim(lane_lock(args.lane), 'Lane ' + str(args.lane)))
            locks.enter_context(claim(output / 'run.lock', 'Output folder ' + str(output)))
            return execute(args, output)
    except LaneBusy as failure:
        print(str(failure), file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
