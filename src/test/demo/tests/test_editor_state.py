import json
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / 'src' / 'test' / 'demo'))
import editor_state


class EditorStateTest(unittest.TestCase):
    def test_replay_uuid_reads_metadata(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            replay = Path(tmp) / 'r.zip'
            with zipfile.ZipFile(replay, 'w') as z:
                z.writestr('metadata.json', json.dumps({'uuid': 'abc-123'}))
            self.assertEqual(editor_state.replay_uuid(replay), 'abc-123')

    def test_observer_state_holds_one_camera_keyframe(self) -> None:
        state = editor_state.observer(Path('/r.zip'), {'x': 1.5, 'y': 70.0, 'z': -4.5, 'yaw': 90.0, 'pitch': 12.0}, 'actor-1')
        track = state['scenes'][0]['keyframeTracks'][0]
        self.assertEqual(track['keyframeType'], 'CAMERA')
        self.assertTrue(track['enabled'])
        frame = track['keyframesByTick']['0']
        self.assertEqual(frame['type'], 'camera')
        self.assertEqual(frame['position'], [1.5, 70.0, -4.5])
        self.assertEqual(frame['interpolation_type'], 'HOLD')
        self.assertFalse(state['replayVisuals']['showHotbar'])
        self.assertFalse(state['replayVisuals']['renderNametags'])

    def test_follow_observer_state_tracks_the_actor_from_its_body(self) -> None:
        camera: dict = {'x': 3.0, 'y': 1.2, 'z': 4.5, 'yaw': -33.7, 'pitch': 12.5, 'follow': True}
        state: dict = editor_state.observer(Path('/r.zip'), camera, '6f1c2a4e-0000-4000-8000-00000000abcd')
        tracks: list[dict] = state['scenes'][0]['keyframeTracks']
        self.assertEqual(len(tracks), 1)
        self.assertEqual(tracks[0]['keyframeType'], 'TRACK_ENTITY')
        self.assertTrue(tracks[0]['enabled'])
        self.assertEqual(tracks[0]['keyframesByTick'], {'0': {'type': 'track_entity', 'target': '6f1c2a4e-0000-4000-8000-00000000abcd', 'bodyPart': 'BODY',
                                                              'yawOffset': -33.7, 'pitchOffset': 12.5, 'positionOffset': [3.0, 1.2, 4.5],
                                                              'viewOffset': [0.0, 0.0, 0.0], 'roll': 0.0, 'interpolation_type': 'HOLD'}})
        self.assertFalse(state['replayVisuals']['showHotbar'])
        self.assertFalse(state['replayVisuals']['renderNametags'])

    def test_follow_false_keeps_the_fixed_camera(self) -> None:
        camera: dict = {'x': 1.5, 'y': 70.0, 'z': -4.5, 'yaw': 90.0, 'pitch': 12.0, 'follow': False}
        state: dict = editor_state.observer(Path('/r.zip'), camera, 'actor-1')
        self.assertEqual(state['scenes'][0]['keyframeTracks'][0]['keyframeType'], 'CAMERA')

    def test_pov_state_has_no_camera_tracks_and_shows_hotbar(self) -> None:
        state = editor_state.pov(Path('/r.zip'))
        self.assertEqual(state['scenes'][0]['keyframeTracks'], [])
        self.assertTrue(state['replayVisuals']['showHotbar'])
        self.assertTrue(state['replayVisuals']['showActionBar'])
        self.assertFalse(state['replayVisuals']['showChat'])
        self.assertFalse(state['replayVisuals']['renderNametags'])

    def test_write_places_file_under_editor_states(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path = editor_state.write(Path(tmp), 'u-1', {'scenes': []})
            self.assertEqual(path, Path(tmp) / 'flashback' / 'editor_states' / 'u-1.json')
            self.assertTrue(path.is_file())


if __name__ == '__main__':
    unittest.main()
