import dataclasses
import io
import itertools
import json
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from types import SimpleNamespace
from unittest import mock

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / 'src' / 'test' / 'demo'))
import export_phase
import manifest

READY: dict = {'replayOpen': True, 'replayReady': True, 'exporting': False, 'exportStarted': False}
EXPORTING: dict = {'replayOpen': True, 'replayReady': True, 'exporting': True, 'exportStarted': True}
DONE: dict = {'replayOpen': True, 'replayReady': True, 'exporting': False, 'exportStarted': True}
CLOSED: dict = {'replayOpen': False, 'replayReady': False, 'exporting': False, 'exportStarted': True}
LABELS: dict[str, dict] = {'ready': READY, 'exporting': EXPORTING, 'done': DONE, 'closed': CLOSED}


def recorded_take(replay: Path, identifier: str = 'agility-wall-jump', status: str = 'recorded') -> manifest.Take:
    return manifest.Take(id=identifier, skill='agility', set='wall', level=5, replay=str(replay), start_tick=0, stop_tick=50,
                         evidence_ok=True, evidence_detail='', takes=1,
                         camera={'x': 0.0, 'y': 66.0, 'z': 0.0, 'yaw': 0.0, 'pitch': 0.0}, actor_uuid='u-1', status=status)


class FakeBridge:
    def __init__(self, events: list[str], writes: bool = True, rejected: str = '', slow_disconnect: bool = False, lost_disconnects: int = 0,
                 slow_opens: int = 0, lost_opens: int = 0) -> None:
        self.events: list[str] = events
        self.writes: bool = writes
        self.rejected: str = rejected
        self.slow_disconnect: bool = slow_disconnect
        self.lost_disconnects: int = lost_disconnects
        self.slow_opens: int = slow_opens
        self.lost_opens: int = lost_opens
        self.replay_open: bool = False
        self.export_started: bool = False

    def export_status(self) -> dict:
        status: dict = {'replayOpen': self.replay_open, 'replayReady': self.replay_open, 'exporting': False, 'exportStarted': self.export_started}
        self.events.append('status')
        return status

    def command(self, operation: str, **values: object) -> dict:
        self.events.append(operation)
        if operation == 'replay-open':
            if self.lost_opens > 0:
                self.lost_opens -= 1
                raise export_phase.BridgeTimeout('replay-open timed out: Client did not process command within five seconds')
            self.replay_open = True
            self.export_started = False
            if self.slow_opens > 0:
                self.slow_opens -= 1
                raise export_phase.BridgeTimeout('replay-open timed out: Client did not process command within five seconds')
        elif operation == 'export':
            if self.rejected and str(values['name']).startswith(self.rejected):
                raise RuntimeError('export rejected with HTTP 400: Replay world is not ready')
            self.export_started = True
            if self.writes:
                Path(str(values['output'])).write_bytes(b'mp4')
        elif operation == 'disconnect':
            if self.lost_disconnects > 0:
                self.lost_disconnects -= 1
                raise export_phase.BridgeTimeout('disconnect timed out: Client did not process command within five seconds')
            self.replay_open = False
            if self.slow_disconnect:
                raise export_phase.BridgeTimeout('disconnect timed out: Client did not process command within five seconds')
        return {}


class ExportPhaseTest(unittest.TestCase):
    def setUp(self) -> None:
        patcher = mock.patch.object(export_phase.time, 'sleep')
        self.sleep = patcher.start()
        self.addCleanup(patcher.stop)
        output = mock.patch('sys.stdout', new_callable=io.StringIO)
        self.stdout = output.start()
        self.addCleanup(output.stop)

    def studio(self, root: Path, events: list[str] | None = None) -> mock.MagicMock:
        log: list[str] = events if events is not None else []
        studio = mock.MagicMock()
        studio.game = root / 'game'
        studio.output = root / 'out'
        statuses = iter(['ready', 'exporting', 'done', 'closed'] * 2)

        def status() -> dict:
            label: str = next(statuses)
            log.append('status:' + label)
            return LABELS[label]

        def fake_command(operation: str, **values: object) -> dict:
            log.append(operation + (':' + str(values['command']) if operation == 'chat' else ''))
            if operation == 'export':
                Path(str(values['output'])).write_bytes(b'mp4')
            return {}

        self.sleep.side_effect = lambda seconds: log.append('sleep:' + str(seconds))
        studio.bridge.export_status.side_effect = status
        studio.bridge.command.side_effect = fake_command
        return studio

    def replay(self, root: Path) -> Path:
        replay = root / 'r.zip'
        with zipfile.ZipFile(replay, 'w') as z:
            z.writestr('metadata.json', json.dumps({'uuid': 'u-1'}))
        return replay

    def test_export_phase_polls_status_not_state(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            studio = self.studio(root)
            take = recorded_take(self.replay(root))
            with mock.patch.object(export_phase.encode, 'run') as run, mock.patch.object(export_phase.encode, 'has_audio', return_value=False):
                result = export_phase.export(studio, {'agility-wall-jump': take}, ['agility-wall-jump'], root / 'docs', Path('/ffmpeg'), Path('/ffprobe'),
                                             root / 'out' / 'manifest.json', audio=False)
            self.assertEqual(result['agility-wall-jump'].status, 'exported')
            studio.bridge.state.assert_not_called()
            self.assertEqual(run.call_count, 4)
            export_calls = [c for c in studio.bridge.command.call_args_list if c.args[0] == 'export']
            self.assertEqual(len(export_calls), 2)
            self.assertIsNone(export_calls[0].kwargs['camera'])
            self.assertEqual(export_calls[1].kwargs['camera']['yaw'], 0.0)
            self.assertAlmostEqual(export_calls[1].kwargs['camera']['y'], 66.0 - manifest.PLAYER_EYE_HEIGHT)
            self.assertEqual(manifest.PLAYER_EYE_HEIGHT, 1.62)
            spectate_calls = [c for c in studio.bridge.command.call_args_list if c.args[0] == 'chat']
            self.assertEqual(spectate_calls[0].kwargs['command'], 'spectate u-1')
            self.assertEqual(manifest.load(root / 'out' / 'manifest.json'), result)

    def test_follow_observer_tracks_the_actor_without_a_fixed_camera(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root: Path = Path(tmp)
            studio: mock.MagicMock = self.studio(root)
            take: manifest.Take = dataclasses.replace(recorded_take(self.replay(root)), actor_uuid='actor-7',
                                                      camera={'x': 3.0, 'y': 1.2, 'z': 4.5, 'yaw': -33.7, 'pitch': 12.5, 'follow': True})
            with mock.patch.object(export_phase.encode, 'run'), mock.patch.object(export_phase.encode, 'has_audio', return_value=False):
                result: dict[str, manifest.Take] = export_phase.export(studio, {take.id: take}, [take.id], root / 'docs', Path('/ffmpeg'),
                                                                       Path('/ffprobe'), root / 'out' / 'manifest.json', audio=False)
            state: dict = json.loads((root / 'game' / 'flashback' / 'editor_states' / 'u-1.json').read_text())
        self.assertEqual(result[take.id].status, 'exported')
        export_calls: list = [c for c in studio.bridge.command.call_args_list if c.args[0] == 'export']
        self.assertIsNone(export_calls[1].kwargs['camera'])
        track: dict = state['scenes'][0]['keyframeTracks'][0]
        self.assertEqual(track['keyframeType'], 'TRACK_ENTITY')
        self.assertEqual(track['keyframesByTick']['0']['target'], 'actor-7')
        self.assertEqual(track['keyframesByTick']['0']['positionOffset'], [3.0, 1.2, 4.5])

    def test_export_requests_1080p30_at_40_mbps_with_a_30_minute_budget(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            studio = self.studio(root)
            take = recorded_take(self.replay(root))
            with mock.patch.object(export_phase.encode, 'run'), mock.patch.object(export_phase.encode, 'has_audio', return_value=False):
                export_phase.export(studio, {'agility-wall-jump': take}, ['agility-wall-jump'], root / 'docs', Path('/ffmpeg'), Path('/ffprobe'),
                                    root / 'out' / 'manifest.json', audio=False)
            export_calls = [c for c in studio.bridge.command.call_args_list if c.args[0] == 'export']
        for call in export_calls:
            self.assertEqual((call.kwargs['width'], call.kwargs['height'], call.kwargs['fps']), (1920, 1080, 30))
            self.assertEqual((call.kwargs['codec'], call.kwargs['bitrate']), ('H264', 40_000_000))
        self.assertEqual(export_phase.EXPORT_TIMEOUT, 1800.0)

    def test_pov_angle_runs_in_order(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            events: list[str] = []
            studio = self.studio(root, events)
            take = recorded_take(self.replay(root))
            with mock.patch.object(export_phase.encode, 'run'), mock.patch.object(export_phase.encode, 'has_audio', return_value=False):
                export_phase.export(studio, {'agility-wall-jump': take}, ['agility-wall-jump'], root / 'docs', Path('/ffmpeg'), Path('/ffprobe'),
                                    root / 'out' / 'manifest.json', audio=False)
        pov: list[str] = events[:events.index('disconnect') + 1]
        self.assertEqual(pov, ['replay-open', 'status:ready', 'sleep:6.0', 'chat:spectate u-1', 'sleep:1.0', 'export',
                               'status:exporting', 'sleep:0.5', 'status:done', 'disconnect'])

    def test_export_trims_leading_ticks_and_warms_up(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            studio = self.studio(root)
            take = recorded_take(self.replay(root))
            with mock.patch.object(export_phase.encode, 'run'), mock.patch.object(export_phase.encode, 'has_audio', return_value=False):
                export_phase.export(studio, {'agility-wall-jump': take}, ['agility-wall-jump'], root / 'docs', Path('/ffmpeg'), Path('/ffprobe'),
                                    root / 'out' / 'manifest.json', audio=False)
            export_calls = [c for c in studio.bridge.command.call_args_list if c.args[0] == 'export']
            for call in export_calls:
                self.assertEqual(call.kwargs['startTick'], 10)
                self.assertNotIn('endTick', call.kwargs)
            self.assertEqual([c.args[0] for c in self.sleep.call_args_list].count(6.0), 2)

    def test_export_writes_finals_intermediates_and_editor_states(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            studio = self.studio(root)
            take = recorded_take(self.replay(root))
            with mock.patch.object(export_phase.encode, 'run') as run, mock.patch.object(export_phase.encode, 'has_audio', return_value=False):
                export_phase.export(studio, {'agility-wall-jump': take}, ['agility-wall-jump'], root / 'docs', Path('/ffmpeg'), Path('/ffprobe'),
                                    root / 'out' / 'manifest.json', audio=False)
            self.assertTrue((root / 'out' / 'intermediate' / 'agility-wall-jump-pov.mp4').is_file())
            self.assertTrue((root / 'out' / 'intermediate' / 'agility-wall-jump-observer.mp4').is_file())
            self.assertTrue((root / 'game' / 'flashback' / 'editor_states' / 'u-1.json').is_file())
            thumbs = [c.args[0] for c in run.call_args_list if c.args[0][-1].endswith('.png')]
            self.assertEqual([command[command.index('-ss') + 1] for command in thumbs], [str(manifest.clip_seconds(take) / 2)] * 2)
            targets = [c.args[0][-1] for c in run.call_args_list]
            self.assertEqual(targets, [str(root / 'docs' / 'adapt-assets' / 'demos' / 'agility' / 'agility-wall-jump-pov.webm'),
                                       str(root / 'out' / 'thumbs' / 'agility-wall-jump-pov.png'),
                                       str(root / 'docs' / 'adapt-assets' / 'demos' / 'agility' / 'agility-wall-jump-observer.webm'),
                                       str(root / 'out' / 'thumbs' / 'agility-wall-jump-observer.png')])

    def test_stale_intermediate_is_never_encoded(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            stale = root / 'out' / 'intermediate' / 'agility-wall-jump-pov.mp4'
            stale.parent.mkdir(parents=True)
            stale.write_bytes(b'stale')
            events: list[str] = []
            studio = SimpleNamespace(bridge=FakeBridge(events, writes=False), game=root / 'game', output=root / 'out')
            take = recorded_take(self.replay(root))
            with mock.patch.object(export_phase.encode, 'run') as run, mock.patch.object(export_phase.encode, 'has_audio', return_value=False):
                result = export_phase.export(studio, {'agility-wall-jump': take}, ['agility-wall-jump'], root / 'docs', Path('/ffmpeg'), Path('/ffprobe'),
                                             root / 'out' / 'manifest.json', audio=False)
            self.assertFalse(stale.exists())
            run.assert_not_called()
            self.assertEqual(result['agility-wall-jump'].status, 'failed')
            self.assertIn('agility-wall-jump-pov.mp4', result['agility-wall-jump'].export_error)
            self.assertFalse(studio.bridge.replay_open)

    def test_empty_intermediate_fails_the_take(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            studio = self.studio(root)
            original = studio.bridge.command.side_effect

            def empty(operation: str, **values: object) -> dict:
                result: dict = original(operation, **values)
                if operation == 'export':
                    Path(str(values['output'])).write_bytes(b'')
                return result

            studio.bridge.command.side_effect = empty
            take = recorded_take(self.replay(root))
            with self.assertRaisesRegex(RuntimeError, 'agility-wall-jump-pov.mp4'):
                export_phase.export_one(studio, take, 'pov', root / 'out' / 'intermediate' / 'agility-wall-jump-pov.mp4', False)

    def test_failed_export_does_not_abort_batch(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            events: list[str] = []
            studio = SimpleNamespace(bridge=FakeBridge(events, rejected='agility-wall-jump'), game=root / 'game', output=root / 'out')
            replay = self.replay(root)
            takes = {'agility-wall-jump': recorded_take(replay), 'agility-vault': recorded_take(replay, 'agility-vault')}
            manifest_path = root / 'out' / 'manifest.json'
            with mock.patch.object(export_phase.encode, 'run') as run, mock.patch.object(export_phase.encode, 'has_audio', return_value=False):
                result = export_phase.export(studio, takes, ['agility-wall-jump', 'agility-vault'], root / 'docs', Path('/ffmpeg'), Path('/ffprobe'),
                                             manifest_path, audio=False)
            self.assertEqual(result['agility-wall-jump'].status, 'failed')
            self.assertIn('Replay world is not ready', result['agility-wall-jump'].export_error)
            self.assertEqual(result['agility-wall-jump'].evidence_detail, '')
            self.assertEqual(result['agility-vault'].status, 'exported')
            self.assertEqual(run.call_count, 4)
            self.assertEqual(manifest.load(manifest_path), result)
            self.assertIn('[EXPORT-FAIL] agility-wall-jump', self.stdout.getvalue())
            self.assertIn('[EXPORT] agility-vault', self.stdout.getvalue())
            second_open: int = events.index('replay-open', 1)
            self.assertIn('disconnect', events[:second_open])

    def test_takes_without_replay_or_evidence_and_unknown_takes_are_skipped(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            studio = self.studio(root)
            missing = recorded_take(root / 'gone.zip', status='failed')
            unproven = dataclasses.replace(recorded_take(self.replay(root), 'agility-vault', status='failed'), evidence_ok=False)
            takes = {'agility-wall-jump': missing, 'agility-vault': unproven}
            with mock.patch.object(export_phase.encode, 'run') as run:
                result = export_phase.export(studio, takes, ['agility-wall-jump', 'agility-vault', 'agility-wind-up'], root / 'docs', Path('/ffmpeg'),
                                             Path('/ffprobe'), root / 'out' / 'manifest.json')
            self.assertEqual(result['agility-wall-jump'].status, 'failed')
            self.assertEqual(result['agility-vault'].status, 'failed')
            self.assertNotIn('agility-wind-up', result)
            run.assert_not_called()
            studio.bridge.command.assert_not_called()

    def test_take_whose_export_failed_is_exported_again(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            studio = self.studio(root)
            take = dataclasses.replace(recorded_take(self.replay(root), status='failed'), evidence_detail='sound=True particle=True',
                                       export_error='disconnect timed out')
            with mock.patch.object(export_phase.encode, 'run'), mock.patch.object(export_phase.encode, 'has_audio', return_value=False):
                result = export_phase.export(studio, {'agility-wall-jump': take}, ['agility-wall-jump'], root / 'docs', Path('/ffmpeg'), Path('/ffprobe'),
                                             root / 'out' / 'manifest.json', audio=False)
            self.assertEqual(result['agility-wall-jump'].status, 'exported')
            self.assertEqual(result['agility-wall-jump'].export_error, '')
            self.assertEqual(result['agility-wall-jump'].evidence_detail, 'sound=True particle=True')

    def test_angle_failure_leaves_no_final_clip_for_that_id(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            events: list[str] = []
            studio = SimpleNamespace(bridge=FakeBridge(events, rejected='agility-wall-jump-observer'), game=root / 'game', output=root / 'out')
            demos = root / 'docs' / 'adapt-assets' / 'demos' / 'agility'
            demos.mkdir(parents=True)
            (demos / 'agility-wall-jump-observer.webm').write_bytes(b'older run')
            (demos / 'agility-vault-pov.webm').write_bytes(b'other id')
            take = recorded_take(self.replay(root))

            def encoded(command: list[str]) -> None:
                Path(command[-1]).write_bytes(b'webm')

            with mock.patch.object(export_phase.encode, 'run', side_effect=encoded), mock.patch.object(export_phase.encode, 'has_audio', return_value=False):
                result = export_phase.export(studio, {'agility-wall-jump': take}, ['agility-wall-jump'], root / 'docs', Path('/ffmpeg'), Path('/ffprobe'),
                                             root / 'out' / 'manifest.json', audio=False)
            self.assertEqual(result['agility-wall-jump'].status, 'failed')
            self.assertFalse((demos / 'agility-wall-jump-pov.webm').exists())
            self.assertFalse((demos / 'agility-wall-jump-observer.webm').exists())
            self.assertTrue((demos / 'agility-vault-pov.webm').is_file())

    def test_skipped_takes_are_never_exported(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            studio = self.studio(root)
            skipped = dataclasses.replace(recorded_take(self.replay(root), 'agility-marathoner', status='skipped'), evidence_detail='Nothing visible.')
            with mock.patch.object(export_phase.encode, 'run') as run:
                result = export_phase.export(studio, {'agility-marathoner': skipped}, ['agility-marathoner'], root / 'docs', Path('/ffmpeg'),
                                             Path('/ffprobe'), root / 'out' / 'manifest.json')
            self.assertEqual(result['agility-marathoner'], skipped)
            run.assert_not_called()
            studio.bridge.command.assert_not_called()

    def test_slow_disconnect_waits_for_the_replay_to_close(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            events: list[str] = []
            studio = SimpleNamespace(bridge=FakeBridge(events, slow_disconnect=True), game=root / 'game', output=root / 'out')
            take = recorded_take(self.replay(root))
            with mock.patch.object(export_phase.encode, 'run'), mock.patch.object(export_phase.encode, 'has_audio', return_value=False):
                result = export_phase.export(studio, {'agility-wall-jump': take}, ['agility-wall-jump'], root / 'docs', Path('/ffmpeg'), Path('/ffprobe'),
                                             root / 'out' / 'manifest.json', audio=False)
            self.assertEqual(result['agility-wall-jump'].status, 'exported')
            self.assertEqual(events.count('disconnect'), 2)
            self.assertEqual(events[events.index('disconnect') + 1], 'status')

    def test_first_replay_open_that_times_out_while_the_client_saves_still_exports(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root: Path = Path(tmp)
            events: list[str] = []
            studio: SimpleNamespace = SimpleNamespace(bridge=FakeBridge(events, lost_opens=1), game=root / 'game', output=root / 'out')
            take: manifest.Take = recorded_take(self.replay(root))
            with mock.patch.object(export_phase.encode, 'run'), mock.patch.object(export_phase.encode, 'has_audio', return_value=False), \
                    mock.patch.object(export_phase.time, 'time', side_effect=itertools.count(0.0, 1.0)):
                result: dict[str, manifest.Take] = export_phase.export(studio, {take.id: take}, [take.id], root / 'docs', Path('/ffmpeg'),
                                                                       Path('/ffprobe'), root / 'out' / 'manifest.json', audio=False)
        self.assertEqual(result[take.id].status, 'exported')
        self.assertEqual(result[take.id].export_error, '')
        self.assertEqual(events.count('replay-open'), 3)
        self.assertEqual(events.count('export'), 2)


class OpenReplayTest(unittest.TestCase):
    def setUp(self) -> None:
        for patcher in (mock.patch.object(export_phase.time, 'sleep'), mock.patch.object(export_phase.time, 'time', side_effect=itertools.count(0.0, 1.0))):
            patcher.start()
            self.addCleanup(patcher.stop)

    def test_open_answered_in_time_waits_only_for_the_replay_to_be_ready(self) -> None:
        events: list[str] = []
        export_phase.open_replay(FakeBridge(events), Path('/replays/r.zip'))
        self.assertEqual(events, ['replay-open', 'status'])

    def test_slow_open_is_awaited_without_a_second_open(self) -> None:
        events: list[str] = []
        bridge: FakeBridge = FakeBridge(events, slow_opens=1)
        export_phase.open_replay(bridge, Path('/replays/r.zip'))
        self.assertTrue(bridge.replay_open)
        self.assertEqual(events.count('replay-open'), 1)

    def test_lost_open_is_resent_once_after_the_bounded_wait(self) -> None:
        events: list[str] = []
        bridge: FakeBridge = FakeBridge(events, lost_opens=1)
        export_phase.open_replay(bridge, Path('/replays/r.zip'))
        self.assertTrue(bridge.replay_open)
        self.assertEqual(events.count('replay-open'), 2)
        between: list[str] = events[events.index('replay-open') + 1:len(events) - 1 - events[::-1].index('replay-open')]
        self.assertGreaterEqual(between.count('status'), export_phase.OPEN_RETRY_SECONDS - 1)
        self.assertEqual(export_phase.OPEN_RETRY_SECONDS, 30.0)

    def test_failed_open_reports_both_swallowed_timeouts(self) -> None:
        events: list[str] = []
        bridge: FakeBridge = FakeBridge(events, lost_opens=2)
        with self.assertRaisesRegex(RuntimeError, 'replay ready.*swallowed replay-open timed out.*; replay-open timed out'):
            export_phase.open_replay(bridge, Path('/replays/r.zip'))
        self.assertEqual(events.count('replay-open'), 2)

    def test_open_rejection_is_not_retried(self) -> None:
        bridge: mock.MagicMock = mock.MagicMock()
        bridge.command.side_effect = RuntimeError('replay-open rejected with HTTP 400: Cannot open a replay while recording')
        with self.assertRaisesRegex(RuntimeError, 'while recording'):
            export_phase.open_replay(bridge, Path('/replays/r.zip'))
        self.assertEqual(bridge.command.call_count, 1)
        bridge.export_status.assert_not_called()


class DisconnectReplayTest(unittest.TestCase):
    def setUp(self) -> None:
        for patcher in (mock.patch.object(export_phase.time, 'sleep'), mock.patch.object(export_phase.time, 'time', side_effect=itertools.count(0.0, 1.0))):
            patcher.start()
            self.addCleanup(patcher.stop)

    def test_lost_disconnect_is_resent_once_after_ten_seconds(self) -> None:
        events: list[str] = []
        bridge = FakeBridge(events, lost_disconnects=1)
        bridge.replay_open = True
        export_phase.disconnect_replay(bridge)
        self.assertFalse(bridge.replay_open)
        self.assertEqual(events.count('disconnect'), 2)
        between: list[str] = events[events.index('disconnect') + 1:len(events) - 1 - events[::-1].index('disconnect')]
        self.assertGreaterEqual(between.count('status'), 9)

    def test_failed_close_reports_the_swallowed_timeout(self) -> None:
        events: list[str] = []
        bridge = FakeBridge(events, lost_disconnects=2)
        bridge.replay_open = True
        with self.assertRaisesRegex(RuntimeError, 'replay closed.*Client did not process command within five seconds'):
            export_phase.disconnect_replay(bridge)
        self.assertEqual(events.count('disconnect'), 2)


if __name__ == '__main__':
    unittest.main()
