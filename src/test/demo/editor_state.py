import json
import zipfile
from pathlib import Path

HIDDEN_HUD: dict = {'showChat': False, 'showBossBar': False, 'showTitleText': False, 'showScoreboard': False, 'showActionBar': False, 'showHotbar': False,
              'renderNametags': False}
PLAYER_HUD: dict = {'showChat': False, 'showBossBar': False, 'showTitleText': True, 'showScoreboard': False, 'showActionBar': True, 'showHotbar': True,
              'renderNametags': False}


def replay_uuid(replay: Path) -> str:
    with zipfile.ZipFile(replay) as archive:
        metadata: dict = json.loads(archive.read('metadata.json'))
    return str(metadata['uuid'])


def observer(replay: Path, camera: dict, actor: str) -> dict:
    track: dict = following_track(camera, actor) if camera.get('follow') is True else fixed_track(camera)
    return document(replay, [track], HIDDEN_HUD)


def fixed_track(camera: dict) -> dict:
    frame: dict = {'type': 'camera', 'position': [float(camera['x']), float(camera['y']), float(camera['z'])],
                   'yaw': float(camera['yaw']), 'pitch': float(camera['pitch']), 'roll': 0.0, 'interpolation_type': 'HOLD'}
    return {'keyframeType': 'CAMERA', 'enabled': True, 'customColour': 0, 'keyframesByTick': {'0': frame}}


def following_track(camera: dict, actor: str) -> dict:
    frame: dict = {'type': 'track_entity', 'target': actor, 'bodyPart': 'BODY', 'yawOffset': float(camera['yaw']), 'pitchOffset': float(camera['pitch']),
                   'positionOffset': [float(camera['x']), float(camera['y']), float(camera['z'])], 'viewOffset': [0.0, 0.0, 0.0], 'roll': 0.0,
                   'interpolation_type': 'HOLD'}
    return {'keyframeType': 'TRACK_ENTITY', 'enabled': True, 'customColour': 0, 'keyframesByTick': {'0': frame}}


def pov(replay: Path) -> dict:
    return document(replay, [], PLAYER_HUD)


def write(game: Path, uuid: str, state: dict) -> Path:
    folder: Path = game / 'flashback' / 'editor_states'
    folder.mkdir(parents=True, exist_ok=True)
    path: Path = folder / (uuid + '.json')
    path.write_text(json.dumps(state, indent=2))
    return path


def document(replay: Path, tracks: list[dict], visuals: dict) -> dict:
    scene: dict = {'name': 'Scene 1', 'exportStartTicks': -1, 'exportEndTicks': -1, 'history': {'entries': [], 'position': 0}, 'keyframeTracks': tracks}
    return {'scenes': [scene], 'sceneIndex': 0, 'usedByPaths': [str(replay)], 'replayVisuals': dict(visuals)}
