import dataclasses
import argparse
import io
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

ROOT: Path = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / 'src' / 'test' / 'demo'))
import choreography
import demo
import studio


def entry(identifier: str, plugins: tuple[str, ...] = (), skip: str = '') -> choreography.Entry:
    return choreography.Entry(id=identifier, set='discovery-pasture', level=5, pre=[],
                              beats=[{'verb': 'wait', 'ticks': 20}], expect_sound=None,
                              expect_particle=None, expect_visual='Hovering inspection details',
                              max_ticks=100, camera=None, plugins=plugins, skip=skip)


class SheetPluginsTest(unittest.TestCase):
    def test_sheet_loads_plugin_list_and_defaults_to_none(self) -> None:
        with tempfile.TemporaryDirectory() as folder:
            root: Path = Path(folder)
            sheets: Path = root / 'src/test/demo/sheets'
            sheets.mkdir(parents=True)
            (sheets / 'discovery.json').write_text(json.dumps({
                'discovery-insight': {'plugins': ['Gloss']}, 'discovery-field-notes': {}
            }))
            loaded: choreography.Sheet = choreography.load_skill(root, 'discovery')
        self.assertEqual(loaded.entries['discovery-insight'].plugins, ('Gloss',))
        self.assertEqual(loaded.entries['discovery-field-notes'].plugins, ())

    def test_validation_accepts_gloss_and_rejects_unknown_plugins(self) -> None:
        good: choreography.Entry = entry('discovery-insight', ('Gloss',))
        self.assertEqual(choreography.validate({good.id: good}, {good.id}), [])
        bad: choreography.Entry = dataclasses.replace(good, plugins=('Gloss', 'Other'))
        self.assertIn('discovery-insight: unsupported plugin Other', choreography.validate({bad.id: bad}, {bad.id}))

    def test_only_selected_non_skipped_entries_require_plugins(self) -> None:
        entries: dict[str, choreography.Entry] = {
            'insight': entry('insight', ('Gloss',)),
            'second': entry('second', ('Gloss',)),
            'plain': entry('plain'),
            'skip': dataclasses.replace(entry('skip', ('Gloss',), 'deferred'), beats=[]),
        }
        self.assertEqual(demo.required_plugins(entries, ['plain', 'skip']), ())
        self.assertEqual(demo.required_plugins(entries, ['insight', 'second']), ('Gloss',))

    def test_selected_dependency_is_passed_to_studio_start(self) -> None:
        entries: dict[str, choreography.Entry] = {'insight': entry('insight', ('Gloss',))}
        session: mock.MagicMock = mock.MagicMock()
        session.stop.return_value = []
        args: argparse.Namespace = argparse.Namespace(skill='discovery', skip_build=True, lane=1,
            only='insight', failed=False, export_only=False, record_only=True, ffmpeg='/tmp/ffmpeg')
        with tempfile.TemporaryDirectory() as folder, mock.patch('sys.stdout', new_callable=io.StringIO), \
                mock.patch.object(demo.choreography, 'load_skill', return_value=choreography.Sheet(choreography.OVERWORLD, entries)), \
                mock.patch.object(demo.choreography, 'adaptation_ids', return_value={'insight'}), \
                mock.patch.object(demo.manifest, 'load', return_value={}), \
                mock.patch.object(demo, 'Studio', return_value=session), \
                mock.patch.object(demo.record_phase, 'record', return_value={'insight': mock.Mock(status='recorded')}), \
                mock.patch.object(demo.gallery, 'write'), mock.patch.object(demo.gallery, 'write_index'):
            self.assertEqual(demo.batch(args, Path(folder)), 0)
        session.start.assert_called_once_with('discovery', opponent=False, world=choreography.OVERWORLD, plugins=('Gloss',))


class StudioPluginsTest(unittest.TestCase):
    def test_start_sets_dependencies_before_preparing_jars(self) -> None:
        with tempfile.TemporaryDirectory() as folder:
            session: studio.Studio = studio.Studio(Path(folder), True)
            def prepared() -> list[Path]:
                self.assertEqual(session.required_plugins, ('Gloss',))
                raise RuntimeError('preparation reached')
            with mock.patch.object(session, 'prepare_jars', side_effect=prepared):
                with self.assertRaisesRegex(RuntimeError, 'preparation reached'):
                    session.start('discovery', plugins=('Gloss',))

    def prepare(self, session: studio.Studio, root: Path) -> list[Path]:
        bridge: Path = root / 'bridge.jar'
        bridge.write_bytes(b'bridge')
        with mock.patch.object(studio, 'build_lock'), mock.patch.object(studio, 'ROOT', root), \
                mock.patch.object(studio.clientqa, 'ensure_mods', return_value=[]), \
                mock.patch.object(studio, 'built_jars', return_value=[bridge]), \
                mock.patch.object(session, 'build'):
            return session.prepare_jars()

    def test_missing_gloss_fails_preparation_with_build_instruction(self) -> None:
        with tempfile.TemporaryDirectory() as folder:
            root: Path = Path(folder) / 'Adapt'
            root.mkdir()
            session: studio.Studio = studio.Studio(root / 'output', True)
            session.required_plugins = ('Gloss',)
            with self.assertRaisesRegex(RuntimeError, 'Gloss.*shadowJar'):
                self.prepare(session, root)

    def test_newest_nonempty_gloss_build_is_snapshotted(self) -> None:
        with tempfile.TemporaryDirectory() as folder:
            root: Path = Path(folder) / 'Adapt'
            root.mkdir()
            libs: Path = root.parent / 'Gloss/build/libs'
            libs.mkdir(parents=True)
            old: Path = libs / 'Gloss-old-packed.jar'
            current: Path = libs / 'Gloss-current-packed.jar'
            empty: Path = libs / 'Gloss-empty-packed.jar'
            old.write_bytes(b'old')
            current.write_bytes(b'current')
            empty.write_bytes(b'')
            (libs / 'Gloss-other.jar').write_bytes(b'unpacked')
            os.utime(old, (10, 10))
            os.utime(current, (20, 20))
            os.utime(empty, (30, 30))
            session: studio.Studio = studio.Studio(root / 'output', True)
            session.required_plugins = ('Gloss',)
            self.prepare(session, root)
            current.write_bytes(b'changed after preparation')
            self.assertEqual((session.jars / 'Gloss.jar').read_bytes(), b'current')


if __name__ == '__main__':
    unittest.main()
