import io
import json
import os
import shutil
import subprocess
import sys
import tempfile
import threading
import unittest
from pathlib import Path
from typing import Callable
from unittest import mock

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / 'src' / 'test' / 'demo'))
import studio

BASE_CONFIG: str = ('[General]\nConfigVersion=1.3\nInstanceType=OneSix\nJvmArgs="-Dadapt.qa.port=1 -Dadapt.qa.token=old -Dadapt.qa.output=/old"\n'
                    'MaxMemAlloc=4096\nname=Adapt Demo Studio\nuuid=570bc923ff9247aa82a1a4f68a731958\n\n[UI]\nmods_Page\\Columns="AAAA"\n')
BASE_FILES: dict[str, str] = {
    'instance.cfg': BASE_CONFIG,
    'mmc-pack.json': '{"formatVersion": 1}',
    '.minecraft/mods/sodium-fabric-0.9.2+mc26.2.jar': 'user mod',
    '.minecraft/shaderpacks/Complementary.zip': 'user shaders',
    '.minecraft/options.txt': 'renderDistance:12\n',
    '.minecraft/config/flashback/flashback.json': '{"configVersion": 2}',
    '.minecraft/flashback/editor_states/052ad47e.json': '{}',
    '.minecraft/flashback/replays/take.zip': 'replay',
    '.minecraft/flashback/temp/recording/chunk.bin': 'temp',
    '.minecraft/logs/latest.log': 'log',
    '.minecraft/saves/world/level.dat': 'save',
    '.minecraft/crash-reports/crash.txt': 'crash',
}
COPIED: list[str] = ['.minecraft/config/flashback/flashback.json', '.minecraft/flashback/editor_states/052ad47e.json',
                     '.minecraft/mods/sodium-fabric-0.9.2+mc26.2.jar', '.minecraft/options.txt', '.minecraft/shaderpacks/Complementary.zip',
                     'instance.cfg', 'mmc-pack.json']


def write_tree(root: Path, files: dict[str, str]) -> None:
    for relative, content in files.items():
        path: Path = root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)


def tree(root: Path) -> list[str]:
    return sorted(path.relative_to(root).as_posix() for path in root.rglob('*') if path.is_file())


class LaneNamingTest(unittest.TestCase):
    def test_lane_one_is_the_persistent_studio_instance(self) -> None:
        self.assertEqual((studio.lane_folder(1), studio.lane_title(1)), ('AdaptDemoStudio', 'Adapt Demo Studio'))

    def test_numbered_lanes_get_a_suffixed_folder_and_title(self) -> None:
        self.assertEqual((studio.lane_folder(2), studio.lane_title(2)), ('AdaptDemoStudio-2', 'Adapt Demo Studio 2'))
        self.assertEqual((studio.lane_folder(12), studio.lane_title(12)), ('AdaptDemoStudio-12', 'Adapt Demo Studio 12'))

    def test_studio_uses_the_lane_instance(self) -> None:
        demo = studio.Studio(Path('/tmp/unused'), skip_build=True, lane=3)
        self.assertEqual(demo.instance, studio.clientqa.PRISM / 'instances' / 'AdaptDemoStudio-3')
        self.assertEqual(demo.game, demo.instance / '.minecraft')
        self.assertEqual(demo.client_title, 'Adapt Demo Studio 3')

    def test_studio_defaults_to_lane_one(self) -> None:
        demo = studio.Studio(Path('/tmp/unused'), skip_build=True)
        self.assertEqual(demo.instance, studio.clientqa.PRISM / 'instances' / 'AdaptDemoStudio')

    def test_lane_locks_are_per_lane(self) -> None:
        with mock.patch.object(studio, 'LOCKS', Path('/locks')):
            self.assertEqual(studio.lane_lock(2), Path('/locks/lane-2.lock'))
            self.assertNotEqual(studio.lane_lock(1), studio.lane_lock(2))


class LaneCloneTest(unittest.TestCase):
    def test_clone_copies_the_instance_without_run_output(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp) / 'AdaptDemoStudio'
            write_tree(base, BASE_FILES)
            target = Path(tmp) / 'AdaptDemoStudio-2'
            created = studio.clone_lane(base, target, 'Adapt Demo Studio 2')
            copied = tree(target)
            config = (target / 'instance.cfg').read_text()
            leftovers = sorted(path.name for path in Path(tmp).iterdir())
            base_after = tree(base)
        self.assertTrue(created)
        self.assertEqual(copied, COPIED)
        self.assertIn('name=Adapt Demo Studio 2\n', config)
        self.assertNotIn('uuid=', config)
        self.assertIn('MaxMemAlloc=4096\n', config)
        self.assertIn('[UI]\nmods_Page\\Columns="AAAA"\n', config)
        self.assertEqual(leftovers, ['AdaptDemoStudio', 'AdaptDemoStudio-2'])
        self.assertEqual(base_after, sorted(BASE_FILES))

    def test_existing_lane_folder_is_never_overwritten(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp) / 'AdaptDemoStudio'
            write_tree(base, BASE_FILES)
            target = Path(tmp) / 'AdaptDemoStudio-2'
            write_tree(target, {'instance.cfg': '[General]\nname=Lane two, edited\n', '.minecraft/mods/extra.jar': 'lane mod'})
            created = studio.clone_lane(base, target, 'Adapt Demo Studio 2')
            copied = tree(target)
            config = (target / 'instance.cfg').read_text()
        self.assertFalse(created)
        self.assertEqual(copied, ['.minecraft/mods/extra.jar', 'instance.cfg'])
        self.assertEqual(config, '[General]\nname=Lane two, edited\n')

    def test_missing_base_instance_fails_without_creating_the_lane(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            target = Path(tmp) / 'AdaptDemoStudio-2'
            with self.assertRaisesRegex(RuntimeError, 'run lane 1 once before cloning it to AdaptDemoStudio-2'):
                studio.clone_lane(Path(tmp) / 'AdaptDemoStudio', target, 'Adapt Demo Studio 2')
            exists = target.exists()
        self.assertFalse(exists)

    def test_stale_staging_copy_is_replaced(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp) / 'AdaptDemoStudio'
            write_tree(base, BASE_FILES)
            write_tree(Path(tmp) / '.AdaptDemoStudio-2.copy', {'AdaptDemoStudio-2/instance.cfg': '[General]\n', 'AdaptDemoStudio-2/half.jar': 'partial'})
            studio.clone_lane(base, Path(tmp) / 'AdaptDemoStudio-2', 'Adapt Demo Studio 2')
            copied = tree(Path(tmp) / 'AdaptDemoStudio-2')
            staging = (Path(tmp) / '.AdaptDemoStudio-2.copy').exists()
        self.assertEqual(copied, COPIED)
        self.assertFalse(staging)


class LaneLaunchTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        for patcher in (mock.patch('sys.stdout', new_callable=io.StringIO), mock.patch.object(studio, 'LOCKS', self.root / 'locks'),
                        mock.patch.object(studio.clientqa, 'client_processes', return_value=[]),
                        mock.patch.object(studio.clientqa, 'free_port', return_value=51234)):
            patcher.start()
            self.addCleanup(patcher.stop)
        mock.patch.object(studio, 'reload_prism_instance').start()
        self.launch = mock.patch.object(studio, 'launch_prism').start()
        self.addCleanup(mock.patch.stopall)
        bridge = mock.MagicMock()
        bridge.state.return_value = {'connected': True}
        mock.patch.object(studio, 'DemoBridge', return_value=bridge).start()
        self.bridge_jar = self.root / 'adapt-client-qa.jar'
        self.bridge_jar.write_text('bridge')

    def studio_for(self, lane: int) -> studio.Studio:
        demo = studio.Studio(self.root / 'out', skip_build=True, lane=lane)
        demo.output.mkdir(exist_ok=True)
        demo.instance = self.root / 'instances' / studio.lane_folder(lane)
        demo.game = demo.instance / '.minecraft'
        demo.server = self.root / 'server'
        demo.server.mkdir(exist_ok=True)
        (demo.server / 'server.properties').write_text('server-port=52000\n')
        return demo

    def launch_client(self, demo: studio.Studio) -> None:
        write_tree(demo.output / 'jars', {'adapt-client-qa.jar': 'bridge'})
        demo.launch_client([])

    def test_new_lane_is_cloned_then_refreshed_and_launched_by_folder(self) -> None:
        write_tree(self.root / 'instances' / 'AdaptDemoStudio', BASE_FILES)
        demo = self.studio_for(2)
        self.launch_client(demo)
        config = (demo.instance / 'instance.cfg').read_text()
        command: list[str] = self.launch.call_args.args[0]
        self.assertIn('name=Adapt Demo Studio 2\n', config)
        self.assertIn('JvmArgs=-Dadapt.qa.port=51234 -Dadapt.qa.token=' + demo.token + ' -Dadapt.qa.output="' + str(demo.output) + '"\n', config)
        self.assertIn('OverrideJavaArgs=true\n', config)
        self.assertEqual((demo.game / 'mods' / 'AdaptClientQa.jar').read_text(), 'bridge')
        self.assertEqual((demo.game / 'mods' / 'sodium-fabric-0.9.2+mc26.2.jar').read_text(), 'user mod')
        self.assertIn('pauseOnLostFocus:false', (demo.game / 'options.txt').read_text())
        self.assertIn('renderDistance:12', (demo.game / 'options.txt').read_text())
        self.assertTrue(json.loads((demo.game / 'config' / 'flashback' / 'flashback.json').read_text())['recording']['recordHotbar'])
        self.assertFalse((demo.game / 'logs').exists())
        self.assertEqual(command[command.index('--launch') + 1], 'AdaptDemoStudio-2')
        self.assertEqual(self.launch.call_args.args[1:3], ('AdaptDemoStudio-2', demo.token))
        self.assertIn('[PRISM] cloned AdaptDemoStudio to ' + str(demo.instance), sys.stdout.getvalue())
        self.assertEqual(demo.bridge_port, 51234)

    def test_existing_lane_is_refreshed_without_copying(self) -> None:
        write_tree(self.root / 'instances' / 'AdaptDemoStudio', BASE_FILES)
        write_tree(self.root / 'instances' / 'AdaptDemoStudio-2', {'instance.cfg': '[General]\nname=Adapt Demo Studio 2\n'})
        demo = self.studio_for(2)
        self.launch_client(demo)
        copied = tree(demo.instance)
        self.assertNotIn('.minecraft/mods/sodium-fabric-0.9.2+mc26.2.jar', copied)
        self.assertIn('.minecraft/mods/AdaptClientQa.jar', copied)
        self.assertNotIn('cloned', sys.stdout.getvalue())

    def test_actor_window_is_fitted_to_1080p_after_the_client_connects(self) -> None:
        demo: studio.Studio = self.studio_for(1)
        self.launch_client(demo)
        calls: list[mock._Call] = demo.bridge.mock_calls
        self.assertIn(mock.call.command('fit-window', width=1920, height=1080), calls)
        self.assertLess(calls.index(mock.call.state()), calls.index(mock.call.command('fit-window', width=1920, height=1080)))

    def test_client_connect_time_starts_the_skin_clock(self) -> None:
        demo: studio.Studio = self.studio_for(1)
        with mock.patch.object(studio.time, 'monotonic', return_value=4321.0):
            self.launch_client(demo)
        self.assertEqual(demo.connected_at, 4321.0)

    def test_lane_one_never_clones(self) -> None:
        demo = self.studio_for(1)
        with mock.patch.object(studio, 'clone_lane') as clone:
            self.launch_client(demo)
        clone.assert_not_called()
        self.assertIn('name=Adapt Demo Studio\n', (demo.instance / 'instance.cfg').read_text())
        self.assertEqual(self.launch.call_args.args[1], 'AdaptDemoStudio')


class OpponentLaunchTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.running: list[Path] = []
        for patcher in (mock.patch('sys.stdout', new_callable=io.StringIO), mock.patch.object(studio, 'LOCKS', self.root / 'locks'),
                        mock.patch.object(studio.clientqa, 'client_processes', side_effect=lambda folder: [4242] if folder in self.running else []),
                        mock.patch.object(studio.clientqa, 'free_port', side_effect=iter(range(51234, 51300)))):
            patcher.start()
            self.addCleanup(patcher.stop)
        mock.patch.object(studio, 'reload_prism_instance').start()
        self.launch = mock.patch.object(studio, 'launch_prism').start()
        self.addCleanup(mock.patch.stopall)
        self.bridge = mock.MagicMock()
        self.bridge.state.return_value = {'connected': True}
        mock.patch.object(studio, 'DemoBridge', return_value=self.bridge).start()
        self.instances: Path = self.root / 'instances'
        write_tree(self.instances / 'AdaptDemoStudio', BASE_FILES)

    def studio_for(self, lane: int) -> studio.Studio:
        demo = studio.Studio(self.root / 'out', skip_build=True, lane=lane)
        demo.output.mkdir(exist_ok=True)
        write_tree(demo.output / 'jars', {'adapt-client-qa.jar': 'bridge'})
        demo.instance = self.instances / studio.lane_folder(lane)
        demo.game = demo.instance / '.minecraft'
        demo.opponent_instance = self.instances / studio.lane_folder(lane, studio.OPPONENT_NAME)
        demo.server = self.root / 'server'
        demo.server.mkdir(exist_ok=True)
        (demo.server / 'server.properties').write_text('server-port=52000\n')
        return demo

    def test_opponent_names_follow_the_lane(self) -> None:
        self.assertEqual((studio.lane_folder(1, studio.OPPONENT_NAME), studio.lane_title(1, studio.OPPONENT_TITLE)),
                         ('AdaptDemoOpponent', 'Adapt Demo Opponent'))
        demo = studio.Studio(Path('/tmp/unused'), skip_build=True, lane=4)
        self.assertEqual((demo.opponent_name, demo.opponent_title, demo.opponent), ('AdaptDemoOpponent-4', 'Adapt Demo Opponent 4', 'AQAOpponent'))
        self.assertEqual(demo.opponent_instance, studio.clientqa.PRISM / 'instances' / 'AdaptDemoOpponent-4')

    def test_lane_one_opponent_is_copied_from_the_studio_and_joins_as_the_opponent(self) -> None:
        demo = self.studio_for(1)
        demo.launch_opponent([])
        config: str = (demo.opponent_instance / 'instance.cfg').read_text()
        command: list[str] = self.launch.call_args.args[0]
        self.assertIn('name=Adapt Demo Opponent\n', config)
        self.assertNotIn('uuid=', config)
        self.assertIn('JvmArgs=-Dadapt.qa.port=' + str(demo.opponent_port) + ' -Dadapt.qa.token=' + demo.opponent_token, config)
        self.assertIn('.minecraft/mods/sodium-fabric-0.9.2+mc26.2.jar', tree(demo.opponent_instance))
        self.assertNotIn('.minecraft/flashback/replays/take.zip', tree(demo.opponent_instance))
        self.assertEqual((demo.opponent_instance / '.minecraft' / 'mods' / 'AdaptClientQa.jar').read_text(), 'bridge')
        self.assertEqual(command[command.index('--launch') + 1:], ['AdaptDemoOpponent', '--offline', 'AQAOpponent', '--server', '127.0.0.1:52000'])
        self.assertEqual(self.launch.call_args.args[1:3], ('AdaptDemoOpponent', demo.opponent_token))
        self.assertTrue((demo.output / 'opponent-launcher.log').is_file())
        self.assertIs(demo.opponent_bridge, self.bridge)
        self.assertTrue(demo.launched_opponent)
        self.assertEqual(tree(self.instances / 'AdaptDemoStudio'), sorted(BASE_FILES))
        self.assertIn('[PRISM] cloned AdaptDemoStudio to ' + str(demo.opponent_instance), sys.stdout.getvalue())

    def test_numbered_lane_opponent_is_copied_from_the_lane_one_opponent(self) -> None:
        write_tree(self.instances / 'AdaptDemoOpponent', {'instance.cfg': '[General]\nname=Adapt Demo Opponent\n', '.minecraft/mods/opponent-skin.jar': 'skin'})
        demo = self.studio_for(2)
        demo.launch_opponent([])
        copied: list[str] = tree(demo.opponent_instance)
        command: list[str] = self.launch.call_args.args[0]
        self.assertIn('.minecraft/mods/opponent-skin.jar', copied)
        self.assertNotIn('.minecraft/mods/sodium-fabric-0.9.2+mc26.2.jar', copied)
        self.assertIn('name=Adapt Demo Opponent 2\n', (demo.opponent_instance / 'instance.cfg').read_text())
        self.assertEqual(command[command.index('--launch') + 1], 'AdaptDemoOpponent-2')
        self.assertIn('[PRISM] cloned AdaptDemoOpponent to ' + str(demo.opponent_instance), sys.stdout.getvalue())
        self.assertNotIn('cloned AdaptDemoStudio', sys.stdout.getvalue())

    def test_numbered_lane_creates_the_lane_one_opponent_first(self) -> None:
        demo = self.studio_for(3)
        demo.launch_opponent([])
        first: Path = self.instances / 'AdaptDemoOpponent'
        self.assertIn('.minecraft/mods/sodium-fabric-0.9.2+mc26.2.jar', tree(first))
        self.assertIn('name=Adapt Demo Opponent\n', (first / 'instance.cfg').read_text())
        self.assertIn('.minecraft/mods/sodium-fabric-0.9.2+mc26.2.jar', tree(demo.opponent_instance))
        self.assertIn('name=Adapt Demo Opponent 3\n', (demo.opponent_instance / 'instance.cfg').read_text())

    def test_existing_opponent_is_refreshed_and_never_replaced(self) -> None:
        write_tree(self.instances / 'AdaptDemoOpponent', {'instance.cfg': '[General]\nname=Rival, edited\n', '.minecraft/mods/opponent-skin.jar': 'skin'})
        demo = self.studio_for(1)
        demo.launch_opponent([])
        copied: list[str] = tree(demo.opponent_instance)
        self.assertIn('.minecraft/mods/opponent-skin.jar', copied)
        self.assertIn('.minecraft/mods/AdaptClientQa.jar', copied)
        self.assertNotIn('.minecraft/mods/sodium-fabric-0.9.2+mc26.2.jar', copied)
        self.assertIn('name=Rival, edited\n', (demo.opponent_instance / 'instance.cfg').read_text())
        self.assertNotIn('cloned', sys.stdout.getvalue())

    def test_opponent_gets_its_own_bridge_port_and_token(self) -> None:
        demo = self.studio_for(1)
        demo.launch_client([])
        demo.launch_opponent([])
        opponent_config: str = (demo.opponent_instance / 'instance.cfg').read_text()
        self.assertNotEqual(demo.opponent_port, demo.bridge_port)
        self.assertNotEqual(demo.opponent_token, demo.token)
        self.assertIn('-Dadapt.qa.token=' + demo.opponent_token, opponent_config)
        self.assertNotIn(demo.token, opponent_config)
        self.assertEqual(studio.DemoBridge.call_args_list, [mock.call(demo.bridge_port, demo.token), mock.call(demo.opponent_port, demo.opponent_token)])
        self.assertEqual(sorted(json.loads((self.root / 'locks' / 'ports.json').read_text())), sorted([str(demo.bridge_port), str(demo.opponent_port)]))

    def test_running_opponent_blocks_the_launch(self) -> None:
        demo = self.studio_for(1)
        self.running.append(demo.opponent_instance)
        with self.assertRaisesRegex(RuntimeError, 'AdaptDemoOpponent is already running'):
            demo.launch_opponent([])
        self.launch.assert_not_called()
        self.assertFalse(demo.launched_opponent)
        self.assertIsNone(demo.opponent_bridge)

    def test_opponent_that_never_connects_fails_the_start(self) -> None:
        demo = self.studio_for(1)
        self.bridge.state.return_value = {'connected': False}
        with mock.patch.object(studio.time, 'sleep'), mock.patch.object(studio.time, 'monotonic', side_effect=iter(range(0, 1000, 60))):
            with self.assertRaisesRegex(RuntimeError, 'AdaptDemoOpponent did not connect within 180 s'):
                demo.launch_opponent([])
        self.assertTrue(demo.launched_opponent)
        self.assertIsNone(demo.opponent_bridge)


class MuxLockTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.held: bool = False
        self.calls: list[tuple[str, bool]] = []
        self.locked: list[int] = []
        for patcher in (mock.patch.object(studio, 'LOCKS', Path(self.tmp.name) / 'locks'),
                        mock.patch.object(studio.fcntl, 'flock', side_effect=self.flock)):
            patcher.start()
            self.addCleanup(patcher.stop)

    def flock(self, descriptor: int, operation: int) -> None:
        self.assertNotEqual(operation & studio.fcntl.LOCK_NB, studio.fcntl.LOCK_NB)
        if operation == studio.fcntl.LOCK_EX:
            self.assertFalse(self.held)
            self.held = True
            self.locked.append(os.fstat(descriptor).st_ino)
        elif operation == studio.fcntl.LOCK_UN:
            self.held = False

    def lock_inode(self) -> int:
        return (Path(self.tmp.name) / 'locks' / 'mux.lock').stat().st_ino

    def mux(self, *arguments: str, timeout: int = 180) -> str:
        self.calls.append((' '.join(arguments[:2]), self.held))
        return str(Path(self.tmp.name) / 'server') if arguments[:2] == ('instance', 'path') else ''

    def test_single_call_holds_the_mux_lock(self) -> None:
        with mock.patch.object(studio.clientqa, 'mux', side_effect=self.mux):
            studio.mux('instance', 'list')
        self.assertEqual(self.calls, [('instance list', True)])
        self.assertEqual(self.locked, [self.lock_inode()])
        self.assertFalse(self.held)

    def test_lock_is_released_when_the_call_fails(self) -> None:
        with mock.patch.object(studio.clientqa, 'mux', side_effect=RuntimeError('Multiplexor instance list failed')):
            with self.assertRaises(RuntimeError):
                studio.mux('instance', 'list')
        self.assertFalse(self.held)

    def test_every_studio_multiplexor_call_holds_the_lock(self) -> None:
        server = Path(self.tmp.name) / 'server'
        (server / 'plugins' / 'Adapt').mkdir(parents=True)
        (server / 'plugins' / 'Adapt' / 'skills').mkdir()
        (server / 'plugins' / 'Adapt' / 'adapt.toml').write_text('actionbarNotifyXp = true\nactionbarNotifyLevel = true\nactionbarNotifyMasterLevel = true\n')
        (server / 'plugins' / 'Adapt' / 'skills' / 'discovery.toml').write_text('enabled = true\n')
        (server / 'server.properties').write_text('server-port=52000\n')
        demo = studio.Studio(Path(self.tmp.name) / 'out', skip_build=True, lane=2)
        demo.output.mkdir()
        with mock.patch.object(studio.clientqa, 'mux', side_effect=self.mux), mock.patch.object(studio.clientqa, 'free_port', return_value=50001), \
                mock.patch.object(studio.clientqa, 'configure_server'), mock.patch.object(studio.clientqa, 'wait_server'), \
                mock.patch.object(studio.shutil, 'copy2'), mock.patch.object(studio, 'iris_jar'), mock.patch.object(studio, 'iris_pack'), \
                mock.patch.object(studio, 'install_pack'), mock.patch.object(studio, 'DemoRcon'), mock.patch('sys.stdout', new_callable=io.StringIO):
            demo.start_server('agility')
            errors = demo.stop()
        self.assertEqual(errors, [])
        self.assertEqual([name for name, _ in self.calls], ['server create', 'instance port', 'gameplay prepare', 'instance path', 'runtime start',
                                                           'runtime status', 'runtime stop', 'runtime status', 'runtime start', 'runtime stop',
                                                           'instance delete'])
        self.assertTrue(all(held for _, held in self.calls))
        self.assertEqual(set(self.locked), {self.lock_inode()})


BUILT_JARS: dict[str, str] = {'build/client-qa/adapt-client-qa.jar': 'bridge', 'build/gameplay/plugins/Adapt.jar': 'adapt',
                               'build/gameplay/plugins/AdaptGameplayFixture.jar': 'fixture'}
RUN_JARS: dict[str, str] = {'Adapt.jar': 'adapt', 'AdaptGameplayFixture.jar': 'fixture', 'adapt-client-qa.jar': 'bridge'}
BUILD_STEPS: list[str] = ['gradle', 'mods', 'bridge', 'copy adapt-client-qa.jar', 'copy Adapt.jar', 'copy AdaptGameplayFixture.jar']


def contents(folder: Path) -> dict[str, str]:
    return {path.name: path.read_text() for path in sorted(folder.iterdir())}


class BuildLockTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp: tempfile.TemporaryDirectory = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root: Path = Path(self.tmp.name)
        self.guard: threading.Lock = threading.Lock()
        self.locks: dict[int, threading.Lock] = {}
        self.holders: dict[int, str] = {}
        self.requests: list[str] = []
        self.second_request: threading.Event = threading.Event()
        self.first_inside: threading.Event = threading.Event()
        self.pause_first: bool = False
        self.steps: list[tuple[str, str, list[str]]] = []
        self.real_copy: Callable[[Path, Path], object] = shutil.copy2
        self.mods: list[Path] = [self.root / 'build' / 'client-qa' / 'mods' / 'flashback.jar']
        write_tree(self.root, BUILT_JARS)
        for patcher in (mock.patch.object(studio, 'ROOT', self.root), mock.patch.object(studio, 'LOCKS', self.root / 'locks'),
                        mock.patch.object(studio.fcntl, 'flock', side_effect=self.flock),
                        mock.patch.object(studio.subprocess, 'run', side_effect=self.process),
                        mock.patch.object(studio.clientqa, 'ensure_mods', side_effect=self.ensure_mods),
                        mock.patch.object(studio.shutil, 'copy2', side_effect=self.copy)):
            patcher.start()
            self.addCleanup(patcher.stop)

    def flock(self, descriptor: int, operation: int) -> None:
        inode: int = os.fstat(descriptor).st_ino
        with self.guard:
            lock: threading.Lock = self.locks.setdefault(inode, threading.Lock())
        if operation == studio.fcntl.LOCK_EX:
            self.requests.append(threading.current_thread().name)
            if len(self.requests) == 2:
                self.second_request.set()
            lock.acquire()
            self.holders[inode] = threading.current_thread().name
        elif operation == studio.fcntl.LOCK_UN:
            del self.holders[inode]
            lock.release()

    def held(self) -> list[str]:
        name: str = threading.current_thread().name
        return sorted(path.name for path in (self.root / 'locks').iterdir() if self.holders.get(path.stat().st_ino) == name)

    def step(self, label: str) -> None:
        self.steps.append((threading.current_thread().name, label, self.held()))
        if self.pause_first and not self.first_inside.is_set():
            self.first_inside.set()
            self.second_request.wait(5)

    def process(self, command: list[str], **values: object) -> subprocess.CompletedProcess:
        self.step('gradle' if command[0] == './gradlew' else 'bridge')
        return subprocess.CompletedProcess(command, 0)

    def ensure_mods(self, root: Path) -> list[Path]:
        self.step('mods')
        return self.mods

    def copy(self, source: Path, target: Path) -> object:
        self.step('copy ' + Path(source).name)
        return self.real_copy(source, target)

    def studio_for(self, lane: int, skip_build: bool) -> studio.Studio:
        demo: studio.Studio = studio.Studio(self.root / ('out-' + str(lane)), skip_build=skip_build, lane=lane)
        demo.output.mkdir()
        return demo

    def test_every_build_step_holds_the_build_lock_and_never_the_mux_lock(self) -> None:
        demo: studio.Studio = self.studio_for(1, False)
        mods: list[Path] = demo.prepare_jars()
        self.assertEqual(mods, self.mods)
        self.assertEqual([label for _, label, _ in self.steps], BUILD_STEPS)
        self.assertEqual([held for _, _, held in self.steps], [['build.lock']] * len(BUILD_STEPS))
        self.assertEqual(self.holders, {})
        self.assertEqual(contents(demo.output / 'jars'), RUN_JARS)

    def test_skip_build_still_rebuilds_the_bridge_and_copies_the_jars(self) -> None:
        demo: studio.Studio = self.studio_for(2, True)
        demo.prepare_jars()
        self.assertEqual([label for _, label, _ in self.steps], BUILD_STEPS[1:])
        self.assertEqual([held for _, _, held in self.steps], [['build.lock']] * (len(BUILD_STEPS) - 1))
        self.assertEqual(contents(demo.output / 'jars'), RUN_JARS)

    def test_two_build_phases_never_overlap(self) -> None:
        self.pause_first = True
        first: studio.Studio = self.studio_for(1, False)
        second: studio.Studio = self.studio_for(2, True)
        threads: list[threading.Thread] = [threading.Thread(target=first.prepare_jars, name='lane-1'),
                                           threading.Thread(target=second.prepare_jars, name='lane-2')]
        threads[0].start()
        entered: bool = self.first_inside.wait(5)
        threads[1].start()
        for thread in threads:
            thread.join(10)
        alive: list[str] = [thread.name for thread in threads if thread.is_alive()]
        self.assertTrue(entered)
        self.assertEqual(alive, [])
        self.assertEqual(self.requests, ['lane-1', 'lane-2'])
        self.assertEqual([(name, label) for name, label, _ in self.steps],
                         [('lane-1', label) for label in BUILD_STEPS] + [('lane-2', label) for label in BUILD_STEPS[1:]])
        self.assertTrue(all(held == ['build.lock'] for _, _, held in self.steps))
        self.assertEqual(contents(second.output / 'jars'), RUN_JARS)


class RunJarsTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp: tempfile.TemporaryDirectory = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root: Path = Path(self.tmp.name)
        write_tree(self.root, BUILT_JARS)
        write_tree(self.root, {'iris/Iris-4.0-packed.jar': 'iris', 'server/server.properties': 'server-port=52000\n',
                               'server/plugins/Adapt/adapt.toml': 'actionbarNotifyXp = true\nactionbarNotifyLevel = true\nactionbarNotifyMasterLevel = true\n',
                               'server/plugins/Adapt/skills/discovery.toml': 'enabled = true\n'})
        self.bridge: mock.MagicMock = mock.MagicMock()
        self.bridge.state.return_value = {'connected': True}
        for patcher in (mock.patch.object(studio, 'ROOT', self.root), mock.patch.object(studio, 'LOCKS', self.root / 'locks'),
                        mock.patch('sys.stdout', new_callable=io.StringIO),
                        mock.patch.object(studio.subprocess, 'run', return_value=subprocess.CompletedProcess([], 0)),
                        mock.patch.object(studio.clientqa, 'ensure_mods', return_value=[]),
                        mock.patch.object(studio.clientqa, 'mux', side_effect=self.mux),
                        mock.patch.object(studio.clientqa, 'free_port', side_effect=[52100, 52101]),
                        mock.patch.object(studio.clientqa, 'wait_server'), mock.patch.object(studio.clientqa, 'client_processes', return_value=[]),
                        mock.patch.object(studio, 'iris_jar', return_value=self.root / 'iris' / 'Iris-4.0-packed.jar'),
                        mock.patch.object(studio, 'iris_pack'), mock.patch.object(studio, 'install_pack'), mock.patch.object(studio, 'DemoRcon'),
                        mock.patch.object(studio, 'DemoBridge', return_value=self.bridge), mock.patch.object(studio, 'launch_prism'),
                        mock.patch.object(studio, 'reload_prism_instance')):
            patcher.start()
            self.addCleanup(patcher.stop)

    def mux(self, *arguments: str, timeout: int = 180) -> str:
        return str(self.root / 'server') if arguments[:2] == ('instance', 'path') else ''

    def test_server_and_client_take_the_copies_made_under_the_build_lock(self) -> None:
        demo: studio.Studio = studio.Studio(self.root / 'out', skip_build=True, lane=1)
        demo.output.mkdir()
        demo.instance = self.root / 'instances' / 'AdaptDemoStudio'
        demo.game = demo.instance / '.minecraft'
        demo.prepare_jars()
        write_tree(self.root, {path: text + ' rebuilt by another lane' for path, text in BUILT_JARS.items()})
        demo.start_server('agility')
        demo.launch_client([])
        plugins: Path = self.root / 'server' / 'plugins'
        installed: dict[str, str] = {'Adapt.jar': (plugins / 'Adapt.jar').read_text(),
                                     'AdaptGameplayFixture.jar': (plugins / 'AdaptGameplayFixture.jar').read_text(),
                                     'adapt-client-qa.jar': (demo.game / 'mods' / 'AdaptClientQa.jar').read_text()}
        self.assertEqual(installed, RUN_JARS)


class LaneClaimTest(unittest.TestCase):
    def test_second_claim_on_the_same_file_is_refused(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'locks' / 'lane-2.lock'
            with studio.claim(path, 'Lane 2'):
                with self.assertRaisesRegex(studio.LaneBusy, 'Lane 2 is in use by another demo run'):
                    studio.claim(path, 'Lane 2')
            with studio.claim(path, 'Lane 2'):
                pass

    def test_claims_on_different_lanes_do_not_conflict(self) -> None:
        with tempfile.TemporaryDirectory() as tmp, mock.patch.object(studio, 'LOCKS', Path(tmp)):
            with studio.claim(studio.lane_lock(1), 'Lane 1'), studio.claim(studio.lane_lock(2), 'Lane 2'):
                pass


class PortReservationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.locks = Path(self.tmp.name) / 'locks'
        self.held: bool = False
        for patcher in (mock.patch.object(studio, 'LOCKS', self.locks), mock.patch.object(studio.fcntl, 'flock', side_effect=self.flock)):
            patcher.start()
            self.addCleanup(patcher.stop)

    def flock(self, descriptor: int, operation: int) -> None:
        self.held = operation == studio.fcntl.LOCK_EX

    def ledger(self) -> dict[str, int]:
        return json.loads((self.locks / 'ports.json').read_text())

    def test_two_lanes_never_receive_the_same_port(self) -> None:
        offered: list[int] = [50000, 50000, 50001]
        with mock.patch.object(studio.clientqa, 'free_port', side_effect=lambda: offered.pop(0)), mock.patch.object(studio.os, 'kill'):
            with mock.patch.object(studio.os, 'getpid', return_value=111):
                first = studio.reserve_port()
            with mock.patch.object(studio.os, 'getpid', return_value=222):
                second = studio.reserve_port()
        self.assertEqual((first, second), (50000, 50001))
        self.assertEqual(self.ledger(), {'50000': 111, '50001': 222})

    def test_free_port_is_probed_inside_the_lock(self) -> None:
        probes: list[bool] = []

        def probe() -> int:
            probes.append(self.held)
            return 50010

        with mock.patch.object(studio.clientqa, 'free_port', side_effect=probe):
            studio.reserve_port()
        self.assertEqual(probes, [True])
        self.assertFalse(self.held)

    def test_reservations_of_ended_runs_are_dropped(self) -> None:
        self.locks.mkdir(parents=True)
        (self.locks / 'ports.json').write_text(json.dumps({'50000': 999999}))
        with mock.patch.object(studio.clientqa, 'free_port', return_value=50000), mock.patch.object(studio.os, 'kill', side_effect=ProcessLookupError):
            port = studio.reserve_port()
        self.assertEqual(port, 50000)
        self.assertEqual(self.ledger(), {'50000': studio.os.getpid()})

    def test_unreadable_ledger_starts_empty(self) -> None:
        self.locks.mkdir(parents=True)
        (self.locks / 'ports.json').write_text('{"50000": ')
        with mock.patch.object(studio.clientqa, 'free_port', return_value=50000):
            self.assertEqual(studio.reserve_port(), 50000)

    def test_exhausted_probes_fail(self) -> None:
        self.locks.mkdir(parents=True)
        (self.locks / 'ports.json').write_text(json.dumps({'50000': 111}))
        with mock.patch.object(studio.clientqa, 'free_port', return_value=50000), mock.patch.object(studio.os, 'kill'):
            with self.assertRaisesRegex(RuntimeError, 'No free port outside the 1 reserved lane ports after 64 attempts'):
                studio.reserve_port()

    def test_release_drops_only_this_run_ports(self) -> None:
        self.locks.mkdir(parents=True)
        (self.locks / 'ports.json').write_text(json.dumps({'50000': studio.os.getpid(), '50001': 111, '50002': studio.os.getpid()}))
        studio.release_ports([50000, 50001])
        self.assertEqual(self.ledger(), {'50001': 111, '50002': studio.os.getpid()})

    def test_studio_releases_its_ports_on_stop(self) -> None:
        demo = studio.Studio(Path(self.tmp.name) / 'out', skip_build=True, lane=2)
        with mock.patch.object(studio.clientqa, 'free_port', side_effect=[50003, 50004]):
            demo.claim_port()
            demo.claim_port()
        self.assertEqual(sorted(self.ledger()), ['50003', '50004'])
        self.assertEqual(demo.stop(), [])
        self.assertEqual(self.ledger(), {})
        self.assertEqual(demo.ports, [])


PS_LISTING: str = ('  4101 /java -Djava.library.path=/prism/instances/AdaptDemoStudio/natives -cp x org.prismlauncher.EntryPoint\n'
                   '  4202 /java -Djava.library.path=/prism/instances/AdaptDemoStudio-2/natives -cp x org.prismlauncher.EntryPoint\n'
                   '  4303 /java -Djava.library.path=/prism/instances/AdaptDemoStudio-12/natives -cp x org.prismlauncher.EntryPoint\n'
                   '  4404 /bin/zsh /prism/instances/AdaptDemoStudio/notes.sh\n')


class LaneProcessTest(unittest.TestCase):
    def processes(self, folder: str) -> list[int]:
        listing = mock.MagicMock(stdout=PS_LISTING)
        with mock.patch.object(studio.clientqa.subprocess, 'run', return_value=listing):
            return studio.clientqa.client_processes(Path('/prism/instances') / folder)

    def test_each_lane_finds_only_its_own_client(self) -> None:
        self.assertEqual(self.processes('AdaptDemoStudio'), [4101])
        self.assertEqual(self.processes('AdaptDemoStudio-2'), [4202])
        self.assertEqual(self.processes('AdaptDemoStudio-12'), [4303])
        self.assertEqual(self.processes('AdaptDemoStudio-1'), [])


if __name__ == '__main__':
    unittest.main()
