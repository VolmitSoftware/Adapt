import io
import json
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest import mock

ROOT: Path = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / 'src' / 'test' / 'demo'))
sys.path.insert(0, str(ROOT / 'src' / 'test' / 'client'))
import choreography
import encode
import export_phase
import manifest
import record_phase
import studio

FFMPEG: Path = Path('/tools/ffmpeg')


class CaptureStudio:
    def __init__(self, replays: Path) -> None:
        self.rcon: mock.MagicMock = mock.MagicMock()
        self.rcon.command.return_value = ('ADAPT_QA DEMO SET arena {"actor":{"x":0.5,"y":64.0,"z":0.5,"yaw":180.0,"pitch":0.0},'
                                          '"camera":{"x":-2.5,"y":66.6,"z":-6.5,"yaw":-30.0,"pitch":14.0}}')
        self.bridge: mock.MagicMock = mock.MagicMock()
        self.bridge.state.side_effect = self.state
        self.bridge.command.side_effect = self.command
        self.game: Path = replays.parent.parent
        self.origin: tuple[float, float, float] = (0.0, 64.0, 0.0)
        self.actor: str = 'AQAClient262'
        self.opponent: str = 'AQAOpponent'
        self.opponent_bridge: mock.MagicMock | None = None
        self.replays: Path = replays
        self.tick: int = 100
        self.record_start: int = 100
        self.recording: bool = False
        self.capturing: bool = False
        self.frames: int = 0
        self.log: list[str] = []
        self.fail_on: str = ''

    def await_tps(self) -> None:
        pass

    def state(self) -> dict:
        self.tick += 1
        return {'ticks': self.tick, 'uuid': 'u-1', 'recordStartTick': self.record_start, 'recordStopTick': self.tick, 'recording': self.recording,
                'capturing': self.capturing, 'captureFrames': self.frames, 'captureSeconds': self.frames / 30.0, 'captureSource': '1920x1080',
                'events': [{'type': 'sound', 'tick': self.record_start + 10, 'name': 'minecraft:block.chest.open', 'channelPlaying': True}]}

    def command(self, operation: str, **values: object) -> dict:
        label: str = operation + (':' + str(values['action']) if operation in ('record', 'capture') else '')
        self.log.append(label)
        if label == self.fail_on:
            raise RuntimeError(operation + ' rejected with HTTP 400: ffmpeg is not an executable file')
        if label == 'record:start':
            self.recording = True
            self.record_start = self.tick + 1
        elif label == 'record:stop':
            self.recording = False
            (self.replays / '2026-09-30T10_00_00.zip').write_bytes(b'zip')
        elif label == 'capture:start':
            self.capturing = True
            self.frames = 0
            Path(str(values['path'])).write_bytes(b'mp4')
        elif label == 'capture:stop':
            self.capturing = False
            self.frames = 297
        elif operation == 'wait':
            self.frames += 1
        return self.state()


def capture_entry(pov_capture: bool) -> choreography.Entry:
    return choreography.Entry(id='rift-enderchest', set='arena', level=3, pre=[], beats=[{'verb': 'wait', 'ticks': 2}],
                              expect_sound='minecraft:block.chest.open', expect_particle=None, max_ticks=100, camera=None, pov_capture=pov_capture)


class PovCaptureRecordTest(unittest.TestCase):
    def setUp(self) -> None:
        for patcher in (mock.patch.object(record_phase.time, 'sleep'), mock.patch('sys.stdout', new_callable=io.StringIO)):
            patcher.start()
            self.addCleanup(patcher.stop)

    def shoot(self, entry: choreography.Entry, fail_on: str = '', ffmpeg: Path | None = FFMPEG) -> tuple[manifest.Take, CaptureStudio, Path]:
        tmp: tempfile.TemporaryDirectory = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        root: Path = Path(tmp.name)
        replays: Path = root / 'game' / 'flashback' / 'replays'
        replays.mkdir(parents=True)
        studio: CaptureStudio = CaptureStudio(replays)
        studio.fail_on = fail_on
        output: Path = root / 'out'
        takes: dict[str, manifest.Take] = record_phase.record(studio, {entry.id: entry}, [entry.id], output / 'manifest.json', 'rift', retakes=1,
                                                              ffmpeg=ffmpeg)
        return takes[entry.id], studio, output

    def test_capture_starts_after_record_start_and_stops_after_record_stop(self) -> None:
        take, studio, _ = self.shoot(capture_entry(True))
        self.assertEqual(take.status, 'recorded')
        self.assertEqual([label for label in studio.log if label.startswith(('record:', 'capture:'))],
                         ['record:start', 'capture:start', 'record:stop', 'capture:stop'])
        self.assertEqual(studio.log.index('capture:start'), studio.log.index('record:start') + 1)
        self.assertEqual(studio.log.index('capture:stop'), studio.log.index('record:stop') + 1)

    def test_capture_writes_1080p30_into_the_intermediate_folder_with_the_given_ffmpeg(self) -> None:
        _, studio, output = self.shoot(capture_entry(True))
        start: mock._Call = next(call for call in studio.bridge.command.call_args_list if call.args[0] == 'capture' and call.kwargs['action'] == 'start')
        self.assertEqual(start.kwargs, {'action': 'start', 'path': str(output / 'intermediate' / 'rift-enderchest-pov-live.mp4'), 'ffmpeg': str(FFMPEG),
                                        'width': 1920, 'height': 1080, 'fps': 30})
        self.assertEqual(manifest.live_pov_path(output, 'rift-enderchest'), output / 'intermediate' / 'rift-enderchest-pov-live.mp4')

    def test_capture_take_is_marked_in_the_manifest(self) -> None:
        take, _, output = self.shoot(capture_entry(True))
        self.assertTrue(take.pov_capture)
        self.assertTrue(manifest.load(output / 'manifest.json')['rift-enderchest'].pov_capture)

    def test_capture_pacing_is_printed(self) -> None:
        self.shoot(capture_entry(True))
        self.assertIn('[CAPTURE] rift-enderchest 297 frames in 9.90 s (30.0 fps), source 1920x1080\n', sys.stdout.getvalue())

    def test_entry_without_capture_never_touches_the_capture(self) -> None:
        take, studio, _ = self.shoot(capture_entry(False))
        self.assertEqual(take.status, 'recorded')
        self.assertFalse(take.pov_capture)
        self.assertFalse([label for label in studio.log if label.startswith('capture')])

    def test_rejected_capture_start_fails_the_take_and_stops_the_recording(self) -> None:
        take, studio, _ = self.shoot(capture_entry(True), fail_on='capture:start')
        self.assertEqual(take.status, 'failed')
        self.assertIn('ffmpeg is not an executable file', take.evidence_detail)
        self.assertEqual([label for label in studio.log if label.startswith(('record:', 'capture:'))],
                         ['record:start', 'capture:start', 'record:stop'])
        self.assertFalse(studio.recording)
        self.assertFalse(studio.capturing)

    def test_abandoned_take_stops_a_running_capture(self) -> None:
        with mock.patch.object(record_phase.beats, 'run_beats', side_effect=RuntimeError('beat exploded')):
            take, studio, _ = self.shoot(capture_entry(True))
        self.assertEqual(take.status, 'failed')
        self.assertIn('beat exploded', take.evidence_detail)
        self.assertEqual([label for label in studio.log if label.startswith(('record:', 'capture:'))],
                         ['record:start', 'capture:start', 'record:stop', 'capture:stop'])
        self.assertFalse(studio.capturing)

    def test_capture_entry_without_ffmpeg_fails_before_the_set(self) -> None:
        take, studio, _ = self.shoot(capture_entry(True), ffmpeg=None)
        self.assertEqual(take.status, 'failed')
        self.assertIn('rift-enderchest needs an ffmpeg path for povCapture', take.evidence_detail)
        studio.rcon.command.assert_not_called()


class PovCaptureExportTest(unittest.TestCase):
    def setUp(self) -> None:
        patcher: mock._patch = mock.patch.object(export_phase.time, 'sleep')
        patcher.start()
        self.addCleanup(patcher.stop)
        console: mock._patch = mock.patch('sys.stdout', new_callable=io.StringIO)
        console.start()
        self.addCleanup(console.stop)

    def studio(self, root: Path) -> mock.MagicMock:
        studio: mock.MagicMock = mock.MagicMock()
        studio.game = root / 'game'
        studio.output = root / 'out'
        studio.bridge.export_status.side_effect = [{'replayOpen': True, 'replayReady': True, 'exporting': False, 'exportStarted': False},
                                                   {'replayOpen': True, 'replayReady': True, 'exporting': False, 'exportStarted': True},
                                                   {'replayOpen': False, 'replayReady': False, 'exporting': False, 'exportStarted': True}]

        def command(operation: str, **values: object) -> dict:
            if operation == 'export':
                Path(str(values['output'])).write_bytes(b'mp4')
            return {}

        studio.bridge.command.side_effect = command
        return studio

    def take(self, root: Path) -> manifest.Take:
        replay: Path = root / 'r.zip'
        with zipfile.ZipFile(replay, 'w') as archive:
            archive.writestr('metadata.json', json.dumps({'uuid': 'u-1'}))
        return manifest.Take(id='rift-enderchest', skill='rift', set='arena', level=3, replay=str(replay), start_tick=0, stop_tick=210,
                             evidence_ok=True, evidence_detail='sound=True particle=n/a', takes=1,
                             camera={'x': 0.0, 'y': 66.0, 'z': 0.0, 'yaw': 0.0, 'pitch': 0.0}, actor_uuid='u-1', status='recorded', pov_capture=True)

    def export(self, root: Path, studio: mock.MagicMock, take: manifest.Take) -> tuple[dict[str, manifest.Take], list[list[str]]]:
        with mock.patch.object(export_phase.encode, 'run') as run, mock.patch.object(export_phase.encode, 'has_audio', return_value=False):
            result: dict[str, manifest.Take] = export_phase.export(studio, {take.id: take}, [take.id], root / 'docs', Path('/ffmpeg'), Path('/ffprobe'),
                                                                   root / 'out' / 'manifest.json')
        return result, [call.args[0] for call in run.call_args_list]

    def test_pov_comes_from_the_live_capture_trimmed_by_half_a_second(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root: Path = Path(tmp)
            live: Path = manifest.live_pov_path(root / 'out', 'rift-enderchest')
            live.parent.mkdir(parents=True)
            live.write_bytes(b'mp4')
            result, commands = self.export(root, self.studio(root), self.take(root))
        self.assertEqual(result['rift-enderchest'].status, 'exported')
        pov: list[str] = commands[0]
        self.assertEqual(pov[pov.index('-i') + 1], str(live))
        self.assertEqual(pov[pov.index('-ss') + 1], '0.5')
        self.assertLess(pov.index('-ss'), pov.index('-i'))
        self.assertEqual(pov[-1], str(root / 'docs' / 'adapt-assets' / 'demos' / 'rift' / 'rift-enderchest-pov.webm'))
        self.assertEqual(export_phase.LIVE_TRIM_SECONDS, manifest.START_TICK / manifest.TICKS_PER_SECOND)

    def test_only_the_observer_is_exported_from_flashback(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root: Path = Path(tmp)
            live: Path = manifest.live_pov_path(root / 'out', 'rift-enderchest')
            live.parent.mkdir(parents=True)
            live.write_bytes(b'mp4')
            studio: mock.MagicMock = self.studio(root)
            _, commands = self.export(root, studio, self.take(root))
        exports: list[mock._Call] = [call for call in studio.bridge.command.call_args_list if call.args[0] == 'export']
        self.assertEqual([call.kwargs['name'] for call in exports], ['rift-enderchest-observer'])
        self.assertNotIn('chat', [call.args[0] for call in studio.bridge.command.call_args_list])
        observer: list[str] = commands[2]
        self.assertEqual(observer[observer.index('-i') + 1], str(root / 'out' / 'intermediate' / 'rift-enderchest-observer.mp4'))
        self.assertNotIn('-ss', observer)

    def test_missing_live_capture_fails_the_export(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root: Path = Path(tmp)
            studio: mock.MagicMock = self.studio(root)
            studio.bridge.export_status.side_effect = None
            studio.bridge.export_status.return_value = {'replayOpen': False, 'replayReady': False, 'exporting': False, 'exportStarted': False}
            result, commands = self.export(root, studio, self.take(root))
        self.assertEqual(result['rift-enderchest'].status, 'failed')
        self.assertIn('Live pov capture missing at', result['rift-enderchest'].export_error)
        self.assertEqual(commands, [])


class PovCaptureSheetTest(unittest.TestCase):
    def test_pov_capture_flag_is_read_from_the_sheet(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root: Path = Path(tmp)
            folder: Path = root / 'src' / 'test' / 'demo' / 'sheets'
            folder.mkdir(parents=True)
            body: dict = {'x-one': {'set': 'arena', 'expect': {'sound': 'a'}, 'beats': [{'verb': 'wait', 'ticks': 2}], 'povCapture': True},
                          'x-two': {'set': 'arena', 'expect': {'sound': 'a'}, 'beats': [{'verb': 'wait', 'ticks': 2}]}}
            (folder / 'x.json').write_text(json.dumps(body))
            entries: dict[str, choreography.Entry] = choreography.load_skill(root, 'x').entries
        self.assertTrue(entries['x-one'].pov_capture)
        self.assertFalse(entries['x-two'].pov_capture)
        self.assertEqual(choreography.validate(entries, {'x-one', 'x-two'}), [])

    def test_pov_capture_defaults_false_and_round_trips_in_the_manifest(self) -> None:
        take: manifest.Take = manifest.Take(id='g', skill='rift', set='arena', level=3, replay='/r.zip', start_tick=1, stop_tick=2, evidence_ok=True,
                                            evidence_detail='sound=True particle=n/a', takes=1, camera={}, actor_uuid='u', status='recorded',
                                            pov_capture=True)
        with tempfile.TemporaryDirectory() as tmp:
            path: Path = Path(tmp) / 'manifest.json'
            manifest.save(path, {'g': take})
            self.assertEqual(manifest.load(path), {'g': take})
            raw: dict = json.loads(path.read_text())
            del raw['g']['pov_capture']
            path.write_text(json.dumps(raw))
            self.assertFalse(manifest.load(path)['g'].pov_capture)


class WindowFitTest(unittest.TestCase):
    HIDDEN_STATE: dict = {'hiddenRenderer': True, 'windowVisible': False, 'windowFocused': False, 'mouseGrabbed': False,
                          'cursorMode': 212993, 'renderWidth': 1920, 'renderHeight': 1080}

    def setUp(self) -> None:
        console: mock._patch = mock.patch('sys.stdout', new_callable=io.StringIO)
        console.start()
        self.addCleanup(console.stop)

    def fitted_studio(self, state: dict) -> studio.Studio:
        demo: studio.Studio = studio.Studio(Path('/unused'), skip_build=True)
        demo.bridge = mock.MagicMock(spec=studio.DemoBridge)
        demo.bridge.state.return_value = state
        return demo

    def test_visible_focused_or_grabbed_client_is_rejected_immediately(self) -> None:
        for field, value in [('hiddenRenderer', False), ('windowVisible', True), ('windowFocused', True),
                             ('mouseGrabbed', True), ('cursorMode', 212995)]:
            with self.subTest(field=field):
                state: dict = dict(self.HIDDEN_STATE)
                state[field] = value
                demo: studio.Studio = self.fitted_studio(state)
                with mock.patch.object(studio.time, 'sleep') as sleep:
                    with self.assertRaisesRegex(RuntimeError, field):
                        demo.fit_window(demo.bridge)
                sleep.assert_not_called()
                demo.bridge.state.assert_called_once()

    def test_missing_visibility_state_is_rejected(self) -> None:
        state: dict = dict(self.HIDDEN_STATE)
        del state['windowVisible']
        demo: studio.Studio = self.fitted_studio(state)
        with self.assertRaisesRegex(RuntimeError, 'windowVisible'):
            demo.fit_window(demo.bridge)

    def test_clipped_framebuffer_times_out_after_five_seconds(self) -> None:
        state: dict = dict(self.HIDDEN_STATE)
        state['renderHeight'] = 1022
        demo: studio.Studio = self.fitted_studio(state)
        with mock.patch.object(studio.time, 'monotonic', side_effect=[0.0, 0.0, 5.0]), mock.patch.object(studio.time, 'sleep'):
            with self.assertRaisesRegex(RuntimeError, '1920x1022'):
                demo.fit_window(demo.bridge)
        demo.bridge.state.assert_called_once_with(timeout=5.0)

    def test_native_resize_can_settle_before_capture(self) -> None:
        clipped: dict = dict(self.HIDDEN_STATE)
        clipped['renderHeight'] = 1022
        demo: studio.Studio = self.fitted_studio(clipped)
        demo.bridge.state.side_effect = [clipped, dict(self.HIDDEN_STATE)]
        with mock.patch.object(studio.time, 'monotonic', side_effect=[0.0, 0.0, 0.05]), mock.patch.object(studio.time, 'sleep'):
            demo.fit_window(demo.bridge)
        self.assertEqual(demo.bridge.state.call_args_list, [mock.call(timeout=5.0), mock.call(timeout=4.95)])

    def test_rejected_hidden_window_fit_stops_startup(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            demo: studio.Studio = studio.Studio(Path(tmp) / 'out', skip_build=True, lane=1)
            demo.bridge = mock.MagicMock()
            demo.bridge.command.side_effect = RuntimeError('fit-window rejected with HTTP 400: Framebuffer is 1920x1022, not 1920x1080')
            with self.assertRaisesRegex(RuntimeError, 'Framebuffer is 1920x1022'):
                demo.fit_window(demo.bridge)
        demo.bridge.command.assert_called_once_with('fit-window', width=1920, height=1080)


class LiveTrimEncodeTest(unittest.TestCase):
    def test_start_seeks_the_input_before_decoding(self) -> None:
        command: list[str] = encode.normalize_command(Path('/f'), Path('/live.mp4'), Path('/out.webm'), has_audio=False, start=0.5)
        self.assertEqual(command[:6], ['/f', '-y', '-ss', '0.5', '-i', '/live.mp4'])
        self.assertIn('-an', command)

    def test_default_start_does_not_seek(self) -> None:
        command: list[str] = encode.normalize_command(Path('/f'), Path('/in.mp4'), Path('/out.webm'), has_audio=True)
        self.assertNotIn('-ss', command)
        self.assertEqual(command[:4], ['/f', '-y', '-i', '/in.mp4'])


if __name__ == '__main__':
    unittest.main()
