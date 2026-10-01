import dataclasses
import io
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / 'src' / 'test' / 'demo'))
sys.path.insert(0, str(ROOT / 'src' / 'test' / 'client'))
import choreography
import manifest
import record_phase


class FakeStudio:
    def __init__(self, replays: Path) -> None:
        self.rcon = mock.MagicMock()
        self.rcon.command.side_effect = self.reply
        self.bridge = mock.MagicMock()
        self.game = replays.parent.parent
        self.origin = (0.0, 64.0, 0.0)
        self.actor = 'AQAClient262'
        self.opponent = 'AQAOpponent'
        self.opponent_bridge: mock.MagicMock | None = None
        self.tick = 100
        self.record_start = 100
        self.recording = False
        self.evicted = False
        self.stops = 0
        self.replays = replays
        self.gates: list[int] = []
        self.bridge.state.side_effect = self.state
        self.bridge.command.side_effect = self.command

    def await_tps(self) -> None:
        self.gates.append(self.rcon.command.call_count + self.bridge.command.call_count)

    def reply(self, text: str) -> str:
        if text.startswith('adaptqa demo set'):
            return 'ADAPT_QA DEMO SET wall {"actor":{"x":0.5,"y":64.0,"z":0.5,"yaw":-90.0,"pitch":0.0},"camera":{"x":-2.5,"y":66.6,"z":-6.5,"yaw":-30.0,"pitch":14.0}}'
        return 'ADAPT_QA DEMO OK'

    def state(self) -> dict:
        self.tick += 1
        return {'ticks': self.tick, 'position': {'x': 0.5, 'y': 64.0, 'z': 0.5}, 'uuid': 'u-1',
                'recordStartTick': self.record_start, 'recordStopTick': self.tick, 'recording': self.recording,
                'events': [] if self.evicted else [{'type': 'sound', 'tick': self.record_start + 10, 'name': 'minecraft:block.ladder.step', 'channelPlaying': True},
                                                   {'type': 'particle', 'tick': self.record_start + 11, 'name': 'block', 'rendered': True}]}

    def command(self, operation: str, **values: object) -> dict:
        if operation == 'record' and values.get('action') == 'start':
            self.recording = True
            self.record_start = self.tick + 1
        if operation == 'record' and values.get('action') == 'stop':
            self.recording = False
            self.stops += 1
            (self.replays / ('2026-09-30T10_00_' + str(self.stops).zfill(2) + '.zip')).write_bytes(b'zip')
        return self.state()


def operations(studio: FakeStudio) -> list[str]:
    result: list[str] = []
    for call in studio.bridge.command.call_args_list:
        name: str = call.args[0]
        result.append(name + ':' + str(call.kwargs['action']) if name == 'record' else name)
    return result


class RecordPhaseTest(unittest.TestCase):
    def setUp(self) -> None:
        for patcher in (mock.patch.object(record_phase.time, 'sleep'), mock.patch('sys.stdout', new_callable=io.StringIO)):
            patcher.start()
            self.addCleanup(patcher.stop)

    def entry(self) -> choreography.Entry:
        return choreography.Entry(id='agility-wall-jump', set='wall', level=5, pre=[], beats=[{'verb': 'wait', 'ticks': 2}],
                                  expect_sound='minecraft:block.ladder.step', expect_particle='block', max_ticks=100, camera=None)

    def test_successful_take_is_recorded_with_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            replays = Path(tmp) / 'flashback' / 'replays'
            replays.mkdir(parents=True)
            studio = FakeStudio(replays)
            takes = record_phase.record(studio, {'agility-wall-jump': self.entry()}, ['agility-wall-jump'], Path(tmp) / 'manifest.json', 'agility')
        take = takes['agility-wall-jump']
        self.assertEqual(take.status, 'recorded')
        self.assertTrue(take.evidence_ok)
        self.assertTrue(take.replay.endswith('.zip'))
        self.assertEqual(take.camera['yaw'], -30.0)
        self.assertEqual(take.actor_uuid, 'u-1')

    def test_replay_is_kept_beside_manifest_and_manifest_saved(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            replays = Path(tmp) / 'flashback' / 'replays'
            replays.mkdir(parents=True)
            studio = FakeStudio(replays)
            manifest_path = Path(tmp) / 'out' / 'manifest.json'
            takes = record_phase.record(studio, {'agility-wall-jump': self.entry()}, ['agility-wall-jump'], manifest_path, 'agility')
            kept = Path(tmp) / 'out' / 'replays' / 'agility-wall-jump.zip'
            self.assertEqual(takes['agility-wall-jump'].replay, str(kept))
            self.assertTrue(kept.is_file())
            self.assertEqual(list(replays.glob('*.zip')), [])
            self.assertEqual(manifest.load(manifest_path), takes)

    def test_adaptations_are_cleared_before_claim(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            replays = Path(tmp) / 'flashback' / 'replays'
            replays.mkdir(parents=True)
            studio = FakeStudio(replays)
            record_phase.record(studio, {'agility-wall-jump': self.entry()}, ['agility-wall-jump'], Path(tmp) / 'manifest.json', 'agility')
        sent = [call.args[0] for call in studio.rcon.command.call_args_list]
        clear = sent.index('adapt clear adaptations player=AQAClient262')
        claim = sent.index('adapt claim-adaptation agility:agility-wall-jump 5 force=true player=AQAClient262')
        self.assertLess(clear, claim)

    def test_keys_are_released_after_beats_before_stop(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            replays = Path(tmp) / 'flashback' / 'replays'
            replays.mkdir(parents=True)
            studio = FakeStudio(replays)
            entry = choreography.Entry(id='agility-wall-jump', set='wall', level=5, pre=[],
                                       beats=[{'verb': 'press', 'hold': ['forward', 'sprint'], 'ticks': 8}, {'verb': 'wait', 'ticks': 2}],
                                       expect_sound='minecraft:block.ladder.step', expect_particle=None, max_ticks=100, camera=None)
            record_phase.record(studio, {'agility-wall-jump': entry}, ['agility-wall-jump'], Path(tmp) / 'manifest.json', 'agility')
        ops = operations(studio)
        self.assertLess(ops.index('keys'), ops.index('release'))
        self.assertLess(ops.index('release'), ops.index('record:stop'))

    def test_beat_timeout_releases_stops_and_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            replays = Path(tmp) / 'flashback' / 'replays'
            replays.mkdir(parents=True)
            studio = FakeStudio(replays)
            entry = choreography.Entry(id='agility-wall-jump', set='wall', level=5, pre=[],
                                       beats=[{'verb': 'press', 'hold': ['sprint'], 'ticks': 10}, {'verb': 'waitFor', 'sound': 'minecraft:never', 'timeout': 2}],
                                       expect_sound='minecraft:block.ladder.step', expect_particle=None, max_ticks=100, camera=None)
            takes = record_phase.record(studio, {'agility-wall-jump': entry}, ['agility-wall-jump'], Path(tmp) / 'manifest.json', 'agility', retakes=1)
        take = takes['agility-wall-jump']
        self.assertEqual(take.status, 'failed')
        self.assertFalse(take.evidence_ok)
        self.assertEqual(take.takes, 1)
        self.assertIn('timed out', take.evidence_detail)
        ops = operations(studio)
        self.assertLess(ops.index('keys'), ops.index('release'))
        self.assertLess(ops.index('release'), ops.index('record:stop'))
        self.assertFalse(studio.recording)

    def test_record_stop_tolerates_timeout(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            replays = Path(tmp) / 'flashback' / 'replays'
            replays.mkdir(parents=True)
            studio = FakeStudio(replays)
            original = studio.command

            def slow(operation: str, **values: object) -> dict:
                if operation == 'record' and values.get('action') == 'stop':
                    studio.recording = False
                    (replays / 'late.zip').write_bytes(b'zip')
                    raise record_phase.BridgeTimeout('504')
                return original(operation, **values)

            studio.bridge.command.side_effect = slow
            takes = record_phase.record(studio, {'agility-wall-jump': self.entry()}, ['agility-wall-jump'], Path(tmp) / 'manifest.json', 'agility')
        self.assertEqual(takes['agility-wall-jump'].status, 'recorded')

    def test_record_stop_resent_when_timeout_dropped_it(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            replays = Path(tmp) / 'flashback' / 'replays'
            replays.mkdir(parents=True)
            studio = FakeStudio(replays)
            original = studio.command
            dropped: list[bool] = []

            def dropping(operation: str, **values: object) -> dict:
                if operation == 'record' and values.get('action') == 'stop' and not dropped:
                    dropped.append(True)
                    raise record_phase.BridgeTimeout('504')
                return original(operation, **values)

            studio.bridge.command.side_effect = dropping
            takes = record_phase.record(studio, {'agility-wall-jump': self.entry()}, ['agility-wall-jump'], Path(tmp) / 'manifest.json', 'agility')
        self.assertEqual(takes['agility-wall-jump'].status, 'recorded')
        self.assertEqual(operations(studio).count('record:stop'), 2)

    def test_state_timeouts_after_stop_timeout_are_retried(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            replays = Path(tmp) / 'flashback' / 'replays'
            replays.mkdir(parents=True)
            studio = FakeStudio(replays)
            original_command = studio.command
            original_state = studio.state
            busy: list[int] = []

            def slow(operation: str, **values: object) -> dict:
                if operation == 'record' and values.get('action') == 'stop':
                    studio.recording = False
                    (replays / 'late.zip').write_bytes(b'zip')
                    busy.extend([1, 1])
                    raise record_phase.BridgeTimeout('504')
                return original_command(operation, **values)

            def state() -> dict:
                if busy:
                    busy.pop()
                    raise record_phase.BridgeTimeout('504')
                return original_state()

            studio.bridge.command.side_effect = slow
            studio.bridge.state.side_effect = state
            takes = record_phase.record(studio, {'agility-wall-jump': self.entry()}, ['agility-wall-jump'], Path(tmp) / 'manifest.json', 'agility')
        self.assertEqual(takes['agility-wall-jump'].status, 'recorded')
        self.assertEqual(busy, [])

    def test_evidence_comes_from_snapshot_taken_at_stop(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            replays = Path(tmp) / 'flashback' / 'replays'
            replays.mkdir(parents=True)
            studio = FakeStudio(replays)
            original = studio.command

            def evicting(operation: str, **values: object) -> dict:
                result: dict = original(operation, **values)
                if operation == 'record' and values.get('action') == 'stop':
                    studio.evicted = True
                return result

            studio.bridge.command.side_effect = evicting
            takes = record_phase.record(studio, {'agility-wall-jump': self.entry()}, ['agility-wall-jump'], Path(tmp) / 'manifest.json', 'agility')
        self.assertEqual(takes['agility-wall-jump'].status, 'recorded')
        self.assertEqual(takes['agility-wall-jump'].takes, 1)

    def test_missing_evidence_retakes_then_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            replays = Path(tmp) / 'flashback' / 'replays'
            replays.mkdir(parents=True)
            studio = FakeStudio(replays)
            entry = choreography.Entry(id='agility-wall-jump', set='wall', level=5, pre=[], beats=[{'verb': 'wait', 'ticks': 1}],
                                       expect_sound='minecraft:nope', expect_particle=None, max_ticks=100, camera=None)
            takes = record_phase.record(studio, {'agility-wall-jump': entry}, ['agility-wall-jump'], Path(tmp) / 'manifest.json', 'agility', retakes=2)
        self.assertEqual(takes['agility-wall-jump'].status, 'failed')
        self.assertEqual(takes['agility-wall-jump'].takes, 2)
        self.assertEqual(takes['agility-wall-jump'].evidence_detail, 'sound=False particle=n/a')

    def ordered_take(self, entry: choreography.Entry) -> list[str]:
        with tempfile.TemporaryDirectory() as tmp:
            replays = Path(tmp) / 'flashback' / 'replays'
            replays.mkdir(parents=True)
            studio = FakeStudio(replays)
            log: list[str] = []
            original = studio.command

            def logged(operation: str, **values: object) -> dict:
                log.append(operation + (':' + str(values['action']) if operation == 'record' else ''))
                return original(operation, **values)

            def waiter(bridge: object, record_start: int | None = None, max_ticks: int = 0) -> object:
                return lambda count: log.append('wait:' + str(count))

            def sent(text: str) -> str:
                log.append('rcon:' + text)
                return studio.reply(text)

            studio.bridge.command.side_effect = logged
            studio.rcon.command.side_effect = sent
            with mock.patch.object(record_phase, 'wait_ticks_factory', side_effect=waiter):
                record_phase.record(studio, {entry.id: entry}, [entry.id], Path(tmp) / 'manifest.json', 'agility', retakes=1)
        return log

    def test_pre_commands_settle_sixty_ticks_before_record_start(self) -> None:
        log = self.ordered_take(self.entry())
        start: int = log.index('record:start')
        self.assertEqual(log[:start].count('wait:60'), 1)
        self.assertEqual(log[start + 1], 'wait:20')

    def test_inventory_sync_follows_the_pre_commands_and_precedes_the_settle(self) -> None:
        entry = dataclasses.replace(self.entry(), pre=['give {actor} minecraft:iron_axe', 'gamerule random_tick_speed 0'])
        log = self.ordered_take(entry)
        sync: int = log.index('rcon:adaptqa demo sync AQAClient262')
        self.assertEqual(log.count('rcon:adaptqa demo sync AQAClient262'), 1)
        self.assertLess(log.index('rcon:give AQAClient262 minecraft:iron_axe'), sync)
        self.assertLess(log.index('rcon:gamerule random_tick_speed 0'), sync)
        self.assertLess(sync, log.index('wait:60'))

    def test_inventory_sync_follows_the_claim_when_there_are_no_pre_commands(self) -> None:
        log = self.ordered_take(self.entry())
        sync: int = log.index('rcon:adaptqa demo sync AQAClient262')
        self.assertLess(log.index('rcon:adapt claim-adaptation agility:agility-wall-jump 5 force=true player=AQAClient262'), sync)
        self.assertLess(sync, log.index('wait:60'))

    def test_prelude_beats_run_after_settle_and_before_record_start(self) -> None:
        entry = choreography.Entry(id='agility-wall-jump', set='wall', level=5, pre=[], beats=[{'verb': 'wait', 'ticks': 2}],
                                   expect_sound='minecraft:block.ladder.step', expect_particle='block', max_ticks=100, camera=None,
                                   prelude=[{'verb': 'look', 'yaw': 180.0, 'pitch': 55.0}])
        log = self.ordered_take(entry)
        self.assertLess(log.index('wait:60'), log.index('look'))
        self.assertLess(log.index('look'), log.index('record:start'))
        self.assertEqual(log.count('look'), 1)

    def test_max_ticks_ends_the_beats_as_a_failed_take(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            replays = Path(tmp) / 'flashback' / 'replays'
            replays.mkdir(parents=True)
            studio = FakeStudio(replays)
            entry = choreography.Entry(id='agility-wall-jump', set='wall', level=5, pre=[],
                                       beats=[{'verb': 'press', 'hold': ['forward'], 'ticks': 10}, {'verb': 'wait', 'ticks': 400}],
                                       expect_sound='minecraft:block.ladder.step', expect_particle='block', max_ticks=100, camera=None)
            takes = record_phase.record(studio, {entry.id: entry}, [entry.id], Path(tmp) / 'manifest.json', 'agility', retakes=1)
        take = takes[entry.id]
        self.assertEqual(take.status, 'failed')
        self.assertFalse(take.evidence_ok)
        self.assertEqual(take.evidence_detail, 'maxTicks 100 exceeded')
        self.assertTrue(take.replay.endswith('.zip'))
        self.assertLess(studio.tick, 500)
        ops = operations(studio)
        self.assertLess(ops.index('keys'), ops.index('release'))
        self.assertLess(ops.index('release'), ops.index('record:stop'))
        self.assertFalse(studio.recording)

    def test_beats_within_max_ticks_are_not_cut(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            replays = Path(tmp) / 'flashback' / 'replays'
            replays.mkdir(parents=True)
            studio = FakeStudio(replays)
            entry = dataclasses.replace(self.entry(), beats=[{'verb': 'wait', 'ticks': 10}], max_ticks=100)
            takes = record_phase.record(studio, {entry.id: entry}, [entry.id], Path(tmp) / 'manifest.json', 'agility', retakes=1)
        self.assertEqual(takes[entry.id].status, 'recorded')

    def test_visual_only_entry_passes_the_text_through(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            replays: Path = Path(tmp) / 'flashback' / 'replays'
            replays.mkdir(parents=True)
            studio: FakeStudio = FakeStudio(replays)
            studio.evicted = True
            entry: choreography.Entry = dataclasses.replace(self.entry(), expect_sound=None, expect_particle=None,
                                                            expect_visual='The actor lunges forward.')
            manifest_path: Path = Path(tmp) / 'manifest.json'
            takes: dict[str, manifest.Take] = record_phase.record(studio, {entry.id: entry}, [entry.id], manifest_path, 'agility', retakes=1)
            saved: dict[str, manifest.Take] = manifest.load(manifest_path)
        take: manifest.Take = takes[entry.id]
        self.assertEqual(take.status, 'recorded')
        self.assertTrue(take.evidence_ok)
        self.assertEqual(take.evidence_detail, 'visual-only: The actor lunges forward.')
        self.assertEqual(take.visual, 'The actor lunges forward.')
        self.assertEqual(saved, takes)
        self.assertIn('[TAKE] agility-wall-jump visual-only: The actor lunges forward. (takes=1)', sys.stdout.getvalue())

    def test_failed_visual_only_take_keeps_the_text(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            replays: Path = Path(tmp) / 'flashback' / 'replays'
            replays.mkdir(parents=True)
            studio: FakeStudio = FakeStudio(replays)
            studio.rcon.command.side_effect = RuntimeError('adaptqa demo set arena: ADAPT_QA ERROR unknown set')
            entry: choreography.Entry = dataclasses.replace(self.entry(), expect_sound=None, expect_particle=None,
                                                            expect_visual='The actor lunges forward.')
            takes: dict[str, manifest.Take] = record_phase.record(studio, {entry.id: entry}, [entry.id], Path(tmp) / 'manifest.json', 'agility', retakes=1)
        self.assertEqual(takes[entry.id].status, 'failed')
        self.assertEqual(takes[entry.id].visual, 'The actor lunges forward.')
        self.assertIn('unknown set', takes[entry.id].evidence_detail)

    def test_sound_entry_records_no_visual_text(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            replays: Path = Path(tmp) / 'flashback' / 'replays'
            replays.mkdir(parents=True)
            studio: FakeStudio = FakeStudio(replays)
            takes: dict[str, manifest.Take] = record_phase.record(studio, {'agility-wall-jump': self.entry()}, ['agility-wall-jump'],
                                                                  Path(tmp) / 'manifest.json', 'agility')
        self.assertEqual(takes['agility-wall-jump'].visual, '')
        self.assertEqual(takes['agility-wall-jump'].evidence_detail, 'sound=True particle=True')

    def test_skipped_entry_is_recorded_as_skipped_without_touching_the_game(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            replays = Path(tmp) / 'flashback' / 'replays'
            replays.mkdir(parents=True)
            studio = FakeStudio(replays)
            entry = choreography.Entry(id='agility-marathoner', set='', level=0, pre=[], beats=[], expect_sound=None, expect_particle=None,
                                       max_ticks=400, camera=None, skip='Nothing visible.')
            manifest_path = Path(tmp) / 'manifest.json'
            takes = record_phase.record(studio, {entry.id: entry}, [entry.id], manifest_path, 'agility')
            saved = manifest.load(manifest_path)
        take = takes[entry.id]
        self.assertEqual(take.status, 'skipped')
        self.assertEqual(take.evidence_detail, 'Nothing visible.')
        self.assertEqual(take.replay, '')
        self.assertFalse(take.evidence_ok)
        self.assertEqual(saved, takes)
        studio.rcon.command.assert_not_called()
        studio.bridge.command.assert_not_called()
        self.assertEqual(studio.gates, [])
        self.assertIn('[SKIP] agility-marathoner Nothing visible.', sys.stdout.getvalue())

    def test_take_error_fails_that_take_and_batch_continues(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            replays = Path(tmp) / 'flashback' / 'replays'
            replays.mkdir(parents=True)
            studio = FakeStudio(replays)
            original = studio.reply

            def rejecting(text: str) -> str:
                if text == 'kill @e[type=nope]':
                    raise RuntimeError('kill @e[type=nope]: ADAPT_QA ERROR unknown entity')
                return original(text)

            studio.rcon.command.side_effect = rejecting
            broken = dataclasses.replace(self.entry(), beats=[{'verb': 'press', 'hold': ['forward'], 'ticks': 10},
                                                              {'verb': 'command', 'text': 'kill @e[type=nope]'}])
            fine = dataclasses.replace(self.entry(), id='agility-vault')
            manifest_path = Path(tmp) / 'manifest.json'
            takes = record_phase.record(studio, {broken.id: broken, fine.id: fine}, [broken.id, fine.id], manifest_path, 'agility')
            saved = manifest.load(manifest_path)
        self.assertEqual(takes[broken.id].status, 'failed')
        self.assertEqual(takes[broken.id].takes, 1)
        self.assertIn('ADAPT_QA ERROR unknown entity', takes[broken.id].evidence_detail)
        self.assertEqual(takes[fine.id].status, 'recorded')
        self.assertEqual(saved, takes)
        ops = operations(studio)
        first_stop: int = ops.index('record:stop')
        self.assertLess(first_stop, ops.index('record:start', ops.index('record:start') + 1))
        self.assertIn('[FAIL] agility-wall-jump', sys.stdout.getvalue())

    def test_vanilla_command_error_fails_that_take_and_batch_continues(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            replays = Path(tmp) / 'flashback' / 'replays'
            replays.mkdir(parents=True)
            studio = FakeStudio(replays)
            original = studio.reply

            def rejecting(text: str) -> str:
                if text == 'effect give AQAClient262 minecraft:nope':
                    raise AssertionError(text + ': Unknown or incomplete command, see below for error')
                return original(text)

            studio.rcon.command.side_effect = rejecting
            broken = dataclasses.replace(self.entry(), beats=[{'verb': 'command', 'text': 'effect give {actor} minecraft:nope'}])
            fine = dataclasses.replace(self.entry(), id='agility-vault')
            manifest_path = Path(tmp) / 'manifest.json'
            takes = record_phase.record(studio, {broken.id: broken, fine.id: fine}, [broken.id, fine.id], manifest_path, 'agility')
        self.assertEqual(takes[broken.id].status, 'failed')
        self.assertEqual(takes[broken.id].takes, 1)
        self.assertIn('Unknown or incomplete command', takes[broken.id].evidence_detail)
        self.assertEqual(takes[fine.id].status, 'recorded')
        self.assertIn('record:stop', operations(studio))

    def test_bridge_rejection_and_missing_replay_fail_only_their_take(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            replays = Path(tmp) / 'flashback' / 'replays'
            replays.mkdir(parents=True)
            studio = FakeStudio(replays)
            original = studio.command
            calls: list[str] = []

            def flaky(operation: str, **values: object) -> dict:
                if operation == 'clear-events':
                    calls.append(operation)
                    if len(calls) == 1:
                        raise RuntimeError('clear-events rejected with HTTP 400: busy')
                return original(operation, **values)

            studio.bridge.command.side_effect = flaky
            first = self.entry()
            second = dataclasses.replace(self.entry(), id='agility-vault')
            with mock.patch.object(record_phase, 'wait_replay', side_effect=[RuntimeError('No replay appeared within 60.0 s'), replays / 'x.zip']), \
                    mock.patch.object(record_phase, 'keep_replay', return_value=Path(tmp) / 'kept.zip'):
                takes = record_phase.record(studio, {first.id: first, second.id: second}, [first.id, second.id], Path(tmp) / 'manifest.json', 'agility')
        self.assertIn('HTTP 400', takes[first.id].evidence_detail)
        self.assertEqual(takes[first.id].status, 'failed')
        self.assertIn('No replay appeared', takes[second.id].evidence_detail)
        self.assertEqual(takes[second.id].status, 'failed')

    def test_bridge_that_stops_answering_aborts_the_batch(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            replays = Path(tmp) / 'flashback' / 'replays'
            replays.mkdir(parents=True)
            studio = FakeStudio(replays)
            original = studio.command
            probes: list[int] = []

            def refused() -> dict:
                probes.append(1)
                raise ConnectionRefusedError(61, 'Connection refused')

            def dying(operation: str, **values: object) -> dict:
                if operation == 'clear-events':
                    studio.bridge.state.side_effect = refused
                    raise ConnectionRefusedError(61, 'Connection refused')
                return original(operation, **values)

            studio.bridge.command.side_effect = dying
            second = dataclasses.replace(self.entry(), id='agility-vault')
            manifest_path = Path(tmp) / 'manifest.json'
            with self.assertRaisesRegex(record_phase.BridgeLost, 'Connection refused'):
                record_phase.record(studio, {'agility-wall-jump': self.entry(), second.id: second}, ['agility-wall-jump', second.id],
                                    manifest_path, 'agility')
            self.assertFalse(manifest_path.exists())
        self.assertEqual(len(probes), 2)
        self.assertEqual(operations(studio).count('clear-events'), 1)

    def test_single_refused_state_probe_does_not_abort(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            replays = Path(tmp) / 'flashback' / 'replays'
            replays.mkdir(parents=True)
            studio = FakeStudio(replays)
            original_command = studio.command
            original_state = studio.state
            refusals: list[int] = []

            def refusing(operation: str, **values: object) -> dict:
                if operation == 'clear-events' and not refusals:
                    refusals.append(1)
                    studio.bridge.state.side_effect = refuse_once
                    raise ConnectionRefusedError(61, 'Connection refused')
                return original_command(operation, **values)

            def refuse_once() -> dict:
                studio.bridge.state.side_effect = original_state
                raise ConnectionRefusedError(61, 'Connection refused')

            studio.bridge.command.side_effect = refusing
            second = dataclasses.replace(self.entry(), id='agility-vault')
            takes = record_phase.record(studio, {'agility-wall-jump': self.entry(), second.id: second}, ['agility-wall-jump', second.id],
                                        Path(tmp) / 'manifest.json', 'agility')
        self.assertEqual(takes['agility-wall-jump'].status, 'failed')
        self.assertIn('Connection refused', takes['agility-wall-jump'].evidence_detail)
        self.assertEqual(takes[second.id].status, 'recorded')

    def test_keyboard_interrupt_aborts_the_batch(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            replays = Path(tmp) / 'flashback' / 'replays'
            replays.mkdir(parents=True)
            studio = FakeStudio(replays)
            studio.rcon.command.side_effect = KeyboardInterrupt
            with self.assertRaises(KeyboardInterrupt):
                record_phase.record(studio, {'agility-wall-jump': self.entry()}, ['agility-wall-jump'], Path(tmp) / 'manifest.json', 'agility')

    def test_entry_camera_is_placed_relative_to_the_plate_origin(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            replays = Path(tmp) / 'flashback' / 'replays'
            replays.mkdir(parents=True)
            studio = FakeStudio(replays)
            entry = choreography.Entry(id='agility-wall-jump', set='wall', level=5, pre=[], beats=[{'verb': 'wait', 'ticks': 2}],
                                       expect_sound='minecraft:block.ladder.step', expect_particle='block', max_ticks=100,
                                       camera={'x': 12.5, 'y': 4.0, 'z': -20.5, 'yaw': 90.0, 'pitch': 6.0})
            takes = record_phase.record(studio, {entry.id: entry}, [entry.id], Path(tmp) / 'manifest.json', 'agility')
        self.assertEqual(takes[entry.id].camera, {'x': 12.5, 'y': 68.0, 'z': -20.5, 'yaw': 90.0, 'pitch': 6.0})

    def test_set_reply_keeps_the_follow_flag(self) -> None:
        reply: str = ('ADAPT_QA DEMO SET runway {"actor":{"x":0.5,"y":64.0,"z":0.5,"yaw":180.0,"pitch":0.0},'
                      '"camera":{"x":3.0,"y":1.2,"z":4.5,"yaw":-33.7,"pitch":12.5,"follow":true}}')
        camera: dict = record_phase.parse_set_reply(reply)['camera']
        self.assertIs(camera['follow'], True)
        self.assertEqual(camera['z'], 4.5)

    def test_follow_set_camera_is_stored_as_an_actor_offset(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            replays: Path = Path(tmp) / 'flashback' / 'replays'
            replays.mkdir(parents=True)
            studio: FakeStudio = FakeStudio(replays)
            reply: str = ('ADAPT_QA DEMO SET runway {"actor":{"x":0.5,"y":64.0,"z":0.5,"yaw":180.0,"pitch":0.0},'
                          '"camera":{"x":3.0,"y":1.2,"z":4.5,"yaw":-33.7,"pitch":12.5,"follow":true}}')
            studio.rcon.command.side_effect = lambda text: reply if text.startswith('adaptqa demo set') else 'ADAPT_QA DEMO OK'
            takes: dict[str, manifest.Take] = record_phase.record(studio, {'agility-wall-jump': self.entry()}, ['agility-wall-jump'],
                                                                  Path(tmp) / 'manifest.json', 'agility')
        self.assertEqual(takes['agility-wall-jump'].camera, {'x': 3.0, 'y': 1.2, 'z': 4.5, 'yaw': -33.7, 'pitch': 12.5, 'follow': True})

    def test_follow_entry_camera_stays_relative_to_the_actor(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            replays: Path = Path(tmp) / 'flashback' / 'replays'
            replays.mkdir(parents=True)
            studio: FakeStudio = FakeStudio(replays)
            entry: choreography.Entry = choreography.Entry(id='agility-wall-jump', set='wall', level=5, pre=[], beats=[{'verb': 'wait', 'ticks': 2}],
                                                           expect_sound='minecraft:block.ladder.step', expect_particle='block', max_ticks=100,
                                                           camera={'x': -2.0, 'y': 1.0, 'z': 5.0, 'yaw': 20.0, 'pitch': 10.0, 'follow': True})
            takes: dict[str, manifest.Take] = record_phase.record(studio, {entry.id: entry}, [entry.id], Path(tmp) / 'manifest.json', 'agility')
        self.assertEqual(takes[entry.id].camera, {'x': -2.0, 'y': 1.0, 'z': 5.0, 'yaw': 20.0, 'pitch': 10.0, 'follow': True})


    def test_tps_gate_runs_once_before_the_first_take(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            replays = Path(tmp) / 'flashback' / 'replays'
            replays.mkdir(parents=True)
            studio = FakeStudio(replays)
            first = self.entry()
            second = dataclasses.replace(first, id='agility-wind-up')
            takes = record_phase.record(studio, {first.id: first, second.id: second}, [first.id, second.id], Path(tmp) / 'manifest.json', 'agility')
        self.assertEqual(studio.gates, [0])
        self.assertEqual([takes[identifier].status for identifier in (first.id, second.id)], ['recorded', 'recorded'])

    def test_tps_gate_runs_before_a_take_that_follows_a_skip(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            replays = Path(tmp) / 'flashback' / 'replays'
            replays.mkdir(parents=True)
            studio = FakeStudio(replays)
            skipped = choreography.Entry(id='agility-marathoner', set='', level=0, pre=[], beats=[], expect_sound=None, expect_particle=None,
                                         max_ticks=400, camera=None, skip='Nothing visible.')
            shot = self.entry()
            record_phase.record(studio, {skipped.id: skipped, shot.id: shot}, [skipped.id, shot.id], Path(tmp) / 'manifest.json', 'agility')
        self.assertEqual(studio.gates, [0])

    def test_run_without_takes_skips_the_tps_gate(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            replays = Path(tmp) / 'flashback' / 'replays'
            replays.mkdir(parents=True)
            studio = FakeStudio(replays)
            record_phase.record(studio, {'agility-wall-jump': self.entry()}, [], Path(tmp) / 'manifest.json', 'agility')
        self.assertEqual(studio.gates, [])


class FakeOpponent:
    def __init__(self, log: list[str]) -> None:
        self.log: list[str] = log
        self.bridge: mock.MagicMock = mock.MagicMock()
        self.bridge.state.return_value = {'ticks': 5000, 'position': {'x': 0.5, 'y': 64.0, 'z': 3.5}}
        self.bridge.command.side_effect = self.command

    def command(self, operation: str, **values: object) -> dict:
        self.log.append('opponent:' + operation)
        return self.bridge.state()


class OpponentTakeTest(unittest.TestCase):
    def setUp(self) -> None:
        for patcher in (mock.patch.object(record_phase.time, 'sleep'), mock.patch('sys.stdout', new_callable=io.StringIO)):
            patcher.start()
            self.addCleanup(patcher.stop)

    def entry(self, opponent: bool) -> choreography.Entry:
        beats: list[dict] = [{'verb': 'press', 'hold': ['forward'], 'ticks': 6, 'actor': 'opponent'}, {'verb': 'click', 'key': 'attack', 'actor': 'opponent'},
                             {'verb': 'keys', 'hold': ['jump'], 'ticks': 5}] if opponent else [{'verb': 'wait', 'ticks': 2}]
        return choreography.Entry(id='agility-kip-up', set='arena', level=4, pre=['give {opponent} minecraft:stick 1'] if opponent else [], beats=beats,
                                  expect_sound='minecraft:block.ladder.step', expect_particle=None, max_ticks=100, camera=None, opponent=opponent,
                                  prelude=[{'verb': 'lookAt', 'offset': [0.5, 1.2, 0.5], 'actor': 'opponent'}] if opponent else [])

    def shoot(self, entry: choreography.Entry, opponent_running: bool) -> tuple[manifest.Take, list[str], list[str]]:
        log: list[str] = []
        with tempfile.TemporaryDirectory() as tmp:
            replays = Path(tmp) / 'flashback' / 'replays'
            replays.mkdir(parents=True)
            studio = FakeStudio(replays)
            if opponent_running:
                studio.opponent_bridge = FakeOpponent(log).bridge
            original = studio.command

            def logged(operation: str, **values: object) -> dict:
                log.append('actor:' + operation + (':' + str(values['action']) if operation == 'record' else ''))
                return original(operation, **values)

            studio.bridge.command.side_effect = logged
            takes = record_phase.record(studio, {entry.id: entry}, [entry.id], Path(tmp) / 'manifest.json', 'agility', retakes=1)
        return takes[entry.id], [call.args[0] for call in studio.rcon.command.call_args_list], log

    def test_opponent_entry_places_equips_and_syncs_the_opponent(self) -> None:
        take, sent, _ = self.shoot(self.entry(True), opponent_running=True)
        self.assertEqual(take.status, 'recorded')
        self.assertEqual(sent, ['adaptqa demo set arena', 'adaptqa demo actor AQAClient262', 'adaptqa demo actor AQAOpponent opponent',
                                'adaptqa demo sparring', 'adapt clear adaptations player=AQAClient262',
                                'adapt claim-adaptation agility:agility-kip-up 4 force=true player=AQAClient262', 'give AQAOpponent minecraft:stick 1',
                                'adaptqa demo sync AQAClient262', 'adaptqa demo sync AQAOpponent'])

    def test_opponent_beats_reach_the_opponent_and_both_clients_release_before_stop(self) -> None:
        _, _, log = self.shoot(self.entry(True), opponent_running=True)
        start: int = log.index('actor:record:start')
        self.assertLess(log.index('opponent:look'), start)
        self.assertEqual(log[start:].count('opponent:keys'), 1)
        self.assertLess(log.index('opponent:keys'), log.index('opponent:click'))
        self.assertLess(log.index('opponent:click'), log.index('actor:keys'))
        stop: int = log.index('actor:record:stop')
        self.assertLess(log.index('opponent:release'), stop)
        self.assertLess(log.index('actor:release'), stop)

    def test_entry_without_opponent_parks_the_running_opponent(self) -> None:
        take, sent, log = self.shoot(self.entry(False), opponent_running=True)
        self.assertEqual(take.status, 'recorded')
        self.assertEqual(sent[:4], ['adaptqa demo set arena', 'adaptqa demo actor AQAClient262', 'adaptqa demo park AQAOpponent', 'adaptqa demo sparring'])
        self.assertNotIn('adaptqa demo sync AQAOpponent', sent)
        self.assertEqual([entry for entry in log if entry.startswith('opponent:')], ['opponent:release'])

    def test_entry_without_opponent_leaves_an_absent_opponent_alone(self) -> None:
        take, sent, _ = self.shoot(self.entry(False), opponent_running=False)
        self.assertEqual(take.status, 'recorded')
        self.assertFalse([command for command in sent if 'AQAOpponent' in command])

    def test_opponent_entry_without_the_opponent_client_fails_before_the_set(self) -> None:
        take, sent, _ = self.shoot(self.entry(True), opponent_running=False)
        self.assertEqual(take.status, 'failed')
        self.assertIn('agility-kip-up needs the opponent client, but it is not running', take.evidence_detail)
        self.assertEqual(sent, [])


if __name__ == '__main__':
    unittest.main()
