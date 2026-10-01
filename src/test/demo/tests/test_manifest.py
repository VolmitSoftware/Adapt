import dataclasses
import json
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / 'src' / 'test' / 'demo'))
import manifest


class ManifestTest(unittest.TestCase):
    def test_round_trip(self) -> None:
        take = manifest.Take(id='a', skill='agility', set='wall', level=5, replay='/r.zip', start_tick=1, stop_tick=2,
                             evidence_ok=True, evidence_detail='ok', takes=1, camera={'x': 0.0}, actor_uuid='u', status='recorded')
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'manifest.json'
            manifest.save(path, {'a': take})
            loaded = manifest.load(path)
        self.assertEqual(loaded, {'a': take})

    def test_failed_take_round_trips(self) -> None:
        take = manifest.Take(id='b', skill='agility', set='ladder', level=1, replay='', start_tick=-1, stop_tick=-1,
                             evidence_ok=False, evidence_detail='waitFor timed out', takes=3, camera={}, actor_uuid='', status='failed')
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'nested' / 'manifest.json'
            manifest.save(path, {'b': take})
            loaded = manifest.load(path)
        self.assertEqual(loaded, {'b': take})

    def test_export_error_defaults_empty_and_round_trips(self) -> None:
        take = manifest.Take(id='c', skill='agility', set='wall', level=5, replay='/r.zip', start_tick=1, stop_tick=2,
                             evidence_ok=True, evidence_detail='sound=True particle=True', takes=1, camera={}, actor_uuid='u', status='failed',
                             export_error='export rejected with HTTP 400')
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'manifest.json'
            manifest.save(path, {'c': take})
            self.assertEqual(manifest.load(path), {'c': take})
            raw = json.loads(path.read_text())
            del raw['c']['export_error']
            path.write_text(json.dumps(raw))
            self.assertEqual(manifest.load(path)['c'].export_error, '')

    def test_visual_defaults_empty_and_round_trips(self) -> None:
        take: manifest.Take = manifest.Take(id='e', skill='kinetics', set='arena', level=5, replay='/r.zip', start_tick=1, stop_tick=2,
                                            evidence_ok=True, evidence_detail='visual-only: The actor lunges forward.', takes=1, camera={},
                                            actor_uuid='u', status='recorded', visual='The actor lunges forward.')
        with tempfile.TemporaryDirectory() as tmp:
            path: Path = Path(tmp) / 'manifest.json'
            manifest.save(path, {'e': take})
            self.assertEqual(manifest.load(path), {'e': take})
            self.assertEqual(json.loads(path.read_text())['e']['visual'], 'The actor lunges forward.')
            raw: dict = json.loads(path.read_text())
            del raw['e']['visual']
            path.write_text(json.dumps(raw))
            self.assertEqual(manifest.load(path)['e'].visual, '')

    def test_follow_camera_round_trips(self) -> None:
        camera: dict = {'x': 3.0, 'y': 1.2, 'z': 4.5, 'yaw': -33.7, 'pitch': 12.5, 'follow': True}
        take: manifest.Take = manifest.Take(id='f', skill='agility', set='runway', level=5, replay='/r.zip', start_tick=1, stop_tick=2,
                                            evidence_ok=True, evidence_detail='sound=True particle=True', takes=1, camera=camera, actor_uuid='u',
                                            status='recorded')
        with tempfile.TemporaryDirectory() as tmp:
            path: Path = Path(tmp) / 'manifest.json'
            manifest.save(path, {'f': take})
            loaded: dict[str, manifest.Take] = manifest.load(path)
            self.assertEqual(json.loads(path.read_text())['f']['camera']['follow'], True)
        self.assertEqual(loaded, {'f': take})
        self.assertIs(loaded['f'].camera['follow'], True)

    def test_clip_seconds_trims_the_export_start_tick(self) -> None:
        take = manifest.Take(id='d', skill='agility', set='wall', level=5, replay='/r.zip', start_tick=100, stop_tick=200,
                             evidence_ok=True, evidence_detail='', takes=1, camera={}, actor_uuid='u', status='recorded')
        self.assertEqual(manifest.clip_seconds(take), (100 - manifest.START_TICK) / 20.0)
        self.assertEqual(manifest.clip_seconds(dataclasses.replace(take, stop_tick=105)), 0.0)

    def test_load_missing_returns_empty(self) -> None:
        self.assertEqual(manifest.load(Path('/nonexistent/manifest.json')), {})


if __name__ == '__main__':
    unittest.main()
