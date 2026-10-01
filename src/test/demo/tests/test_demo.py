import io
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / 'src' / 'test' / 'demo'))
import choreography
import demo
import manifest
import studio as lanes


def exported(identifier: str) -> manifest.Take:
    return manifest.Take(id=identifier, skill='agility', set='runway', level=5, replay='/r.zip', start_tick=0, stop_tick=100,
                         evidence_ok=True, evidence_detail='sound=True particle=True', takes=1, camera={}, actor_uuid='u', status='exported')


class DemoMainTest(unittest.TestCase):
    def setUp(self) -> None:
        for name in ('sys.stdout', 'sys.stderr'):
            patcher = mock.patch(name, new_callable=io.StringIO)
            setattr(self, name.split('.')[1], patcher.start())
            self.addCleanup(patcher.stop)

    def run_main(self, tmp: str, stop_errors: list[str], record: mock.MagicMock | None = None, extra: list[str] | None = None,
                 output: str = '') -> tuple[int, mock.MagicMock]:
        session = mock.MagicMock()
        session.stop.return_value = stop_errors
        session.log_path.side_effect = lambda kind: Path(tmp) / 'logs' / ('1790793548-55cdd6-' + kind + '.log')
        takes: dict[str, manifest.Take] = {'agility-wind-up': exported('agility-wind-up')}
        argv: list[str] = ['demo.py', '--skill', 'agility', '--only', 'agility-wind-up', '--output', output or tmp] + (extra or [])
        with mock.patch.object(sys, 'argv', argv), mock.patch.object(demo, 'Studio', return_value=session) as construct, \
                mock.patch.object(lanes, 'LOCKS', Path(tmp) / 'locks'), mock.patch.object(demo, 'INDEX', Path(tmp) / 'index.html'), \
                mock.patch.object(demo.record_phase, 'record', new=record or mock.MagicMock(return_value=takes)), \
                mock.patch.object(demo.export_phase, 'export', return_value=takes), \
                mock.patch.object(demo.gallery, 'write', side_effect=lambda path, *rest: path.write_text('gallery')):
            code: int = demo.main()
        session.construct = construct
        return code, session

    def test_cleanup_errors_are_printed_and_fail_the_run(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            code, studio = self.run_main(tmp, ["Server cleanup: RuntimeError('boom')"])
        self.assertEqual(code, 1)
        studio.stop.assert_called_once_with()
        self.assertIn("[CLEANUP] Server cleanup: RuntimeError('boom')", self.stderr.getvalue())
        self.assertIn('failed: none', self.stdout.getvalue())

    def test_clean_run_exits_zero(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            code, _ = self.run_main(tmp, [])
        self.assertEqual(code, 0)
        self.assertEqual(self.stderr.getvalue(), '')

    def test_failure_stops_studio_as_failed_and_still_prints_cleanup(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaisesRegex(RuntimeError, 'bridge gone'):
                self.run_main(tmp, ['Client cleanup: OSError()'], record=mock.MagicMock(side_effect=RuntimeError('bridge gone')))
        self.assertIn('[CLEANUP] Client cleanup: OSError()', self.stderr.getvalue())


    def test_run_regenerates_the_gallery_index(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            (Path(tmp) / 'pickaxe').mkdir()
            (Path(tmp) / 'pickaxe' / 'gallery.html').write_text('gallery')
            code, _ = self.run_main(tmp, [], output=str(Path(tmp) / 'agility'))
            index: str = (Path(tmp) / 'index.html').read_text()
        self.assertEqual(code, 0)
        self.assertIn('href="agility/gallery.html"', index)
        self.assertIn('href="pickaxe/gallery.html"', index)
        self.assertIn('index: ' + str(Path(tmp) / 'index.html'), self.stdout.getvalue())

    def test_run_names_its_server_and_client_logs(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            self.run_main(tmp, [])
            logs: Path = Path(tmp) / 'logs'
        self.assertIn('logs: ' + str(logs / '1790793548-55cdd6-server.log') + ' ' + str(logs / '1790793548-55cdd6-client.log'), self.stdout.getvalue())
        self.assertTrue(self.stdout.getvalue().rstrip().endswith('failed: none'))

    def test_lane_defaults_to_one(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            _, session = self.run_main(tmp, [])
        self.assertEqual(session.construct.call_args.args, (Path(tmp).resolve(), False, 1))

    def test_lane_is_passed_to_the_studio(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            code, session = self.run_main(tmp, [], extra=['--lane', '3'])
        self.assertEqual(code, 0)
        self.assertEqual(session.construct.call_args.args, (Path(tmp).resolve(), False, 3))

    def test_studio_starts_with_the_batch_skill(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            _, session = self.run_main(tmp, [])
        session.start.assert_called_once_with('agility', opponent=False, world=choreography.OVERWORLD)

    def test_studio_launches_the_opponent_for_a_selected_opponent_entry(self) -> None:
        entries: dict[str, choreography.Entry] = {'agility-wind-up': sheet_entry('agility-wind-up', opponent=True)}
        with tempfile.TemporaryDirectory() as tmp:
            with mock.patch.object(demo.choreography, 'load_skill', return_value=choreography.Sheet(world=choreography.OVERWORLD, entries=entries)), \
                    mock.patch.object(demo.choreography, 'adaptation_ids', return_value={'agility-wind-up'}):
                code, session = self.run_main(tmp, [])
        self.assertEqual(code, 0)
        session.start.assert_called_once_with('agility', opponent=True, world=choreography.OVERWORLD)

    def test_export_only_run_never_launches_the_opponent(self) -> None:
        entries: dict[str, choreography.Entry] = {'agility-wind-up': sheet_entry('agility-wind-up', opponent=True)}
        with tempfile.TemporaryDirectory() as tmp:
            with mock.patch.object(demo.choreography, 'load_skill', return_value=choreography.Sheet(world=choreography.OVERWORLD, entries=entries)), \
                    mock.patch.object(demo.choreography, 'adaptation_ids', return_value={'agility-wind-up'}):
                _, session = self.run_main(tmp, [], extra=['--export-only'])
        session.start.assert_not_called()
        session.start_replay.assert_called_once_with()

    def test_studio_plates_the_world_the_sheet_declares(self) -> None:
        entries: dict[str, choreography.Entry] = {'agility-wind-up': sheet_entry('agility-wind-up', opponent=True)}
        with tempfile.TemporaryDirectory() as tmp:
            with mock.patch.object(demo.choreography, 'load_skill', return_value=choreography.Sheet(world='minecraft:the_nether', entries=entries)), \
                    mock.patch.object(demo.choreography, 'adaptation_ids', return_value={'agility-wind-up'}):
                code, session = self.run_main(tmp, [], extra=['--lane', '2'])
        self.assertEqual(code, 0)
        session.start.assert_called_once_with('agility', opponent=True, world='minecraft:the_nether')
        self.assertEqual(session.construct.call_args.args[2], 2)

    def test_unknown_world_exits_two_without_starting_a_studio(self) -> None:
        entries: dict[str, choreography.Entry] = {'agility-wind-up': sheet_entry('agility-wind-up')}
        with tempfile.TemporaryDirectory() as tmp:
            with mock.patch.object(demo.choreography, 'load_skill', return_value=choreography.Sheet(world='minecraft:the_end', entries=entries)), \
                    mock.patch.object(demo.choreography, 'adaptation_ids', return_value={'agility-wind-up'}):
                code, session = self.run_main(tmp, [])
        self.assertEqual(code, 2)
        session.construct.assert_not_called()
        self.assertIn('world must be iris:adapt_demo or minecraft:the_nether, not minecraft:the_end', self.stdout.getvalue())

    def test_lane_below_one_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(SystemExit) as raised:
                self.run_main(tmp, [], extra=['--lane', '0'])
        self.assertEqual(raised.exception.code, 2)
        self.assertIn('--lane must be 1 or higher', self.stderr.getvalue())

    def test_busy_lane_exits_without_starting_a_studio(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            with mock.patch.object(lanes, 'LOCKS', Path(tmp) / 'locks'), lanes.claim(lanes.lane_lock(2), 'Lane 2'):
                code, session = self.run_main(tmp, [], extra=['--lane', '2'])
        self.assertEqual(code, 2)
        session.construct.assert_not_called()
        self.assertIn('Lane 2 is in use by another demo run', self.stdout.getvalue())

    def test_busy_output_folder_exits_without_starting_a_studio(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            with lanes.claim(Path(tmp).resolve() / 'run.lock', 'Output folder'):
                code, session = self.run_main(tmp, [], extra=['--lane', '2'])
        self.assertEqual(code, 2)
        session.construct.assert_not_called()
        self.assertIn('Output folder ' + str(Path(tmp).resolve()) + ' is in use by another demo run', self.stdout.getvalue())

    def test_claims_are_released_after_the_run(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            self.run_main(tmp, [], extra=['--lane', '2'])
            with mock.patch.object(lanes, 'LOCKS', Path(tmp) / 'locks'), lanes.claim(lanes.lane_lock(2), 'Lane 2'), \
                    lanes.claim(Path(tmp).resolve() / 'run.lock', 'Output folder'):
                pass


def sheet_entry(identifier: str, opponent: bool = False, skip: str = '') -> choreography.Entry:
    return choreography.Entry(id=identifier, set='' if skip else 'arena', level=1, pre=[], beats=[] if skip else [{'verb': 'wait', 'ticks': 5}],
                              expect_sound=None if skip else 'minecraft:x', expect_particle=None, max_ticks=100, camera=None, skip=skip,
                              opponent=opponent)


class NeedsOpponentTest(unittest.TestCase):
    def setUp(self) -> None:
        self.entries: dict[str, choreography.Entry] = {'duel': sheet_entry('duel', opponent=True), 'solo': sheet_entry('solo'),
                                                       'benched': sheet_entry('benched', opponent=True, skip='nothing visible')}

    def test_selected_opponent_entry_needs_the_opponent(self) -> None:
        self.assertTrue(demo.needs_opponent(self.entries, ['solo', 'duel'], export_only=False))

    def test_opponent_entry_outside_the_selection_is_ignored(self) -> None:
        self.assertFalse(demo.needs_opponent(self.entries, ['solo'], export_only=False))

    def test_skipped_opponent_entry_records_nothing(self) -> None:
        self.assertFalse(demo.needs_opponent(self.entries, ['solo', 'benched'], export_only=False))

    def test_export_only_records_nothing(self) -> None:
        self.assertFalse(demo.needs_opponent(self.entries, ['duel'], export_only=True))


class DefaultToolTest(unittest.TestCase):
    def test_local_bin_is_preferred(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            local = Path(tmp) / '.local' / 'bin' / 'ffmpeg'
            local.parent.mkdir(parents=True)
            local.write_bytes(b'')
            with mock.patch.object(demo.Path, 'home', return_value=Path(tmp)), mock.patch.object(demo.shutil, 'which', return_value='/usr/bin/ffmpeg'):
                self.assertEqual(demo.default_tool('ffmpeg'), str(local))

    def test_path_lookup_is_used_when_local_bin_is_missing(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            with mock.patch.object(demo.Path, 'home', return_value=Path(tmp)), \
                    mock.patch.object(demo.shutil, 'which', return_value='/opt/homebrew/bin/ffprobe') as which:
                self.assertEqual(demo.default_tool('ffprobe'), '/opt/homebrew/bin/ffprobe')
            which.assert_called_once_with('ffprobe')

    def test_local_path_is_reported_when_tool_is_nowhere(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            with mock.patch.object(demo.Path, 'home', return_value=Path(tmp)), mock.patch.object(demo.shutil, 'which', return_value=None):
                self.assertEqual(demo.default_tool('ffmpeg'), str(Path(tmp) / '.local' / 'bin' / 'ffmpeg'))


if __name__ == '__main__':
    unittest.main()
