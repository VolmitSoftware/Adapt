import hashlib
import json
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / 'src' / 'test' / 'client'))
import run as clientqa


class EnsureModsTest(unittest.TestCase):
    def test_ensure_mods_skips_download_when_sha_matches(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            mods = root / 'build' / 'client-qa' / 'mods'
            mods.mkdir(parents=True)
            payload = b'jar-bytes'
            (mods / 'A.jar').write_bytes(payload)
            manifest = {'mods': [{'id': 'a', 'file': 'A.jar', 'url': 'https://x/A.jar', 'sha1': hashlib.sha1(payload).hexdigest()}]}
            (root / 'src' / 'test' / 'client').mkdir(parents=True)
            (root / 'src' / 'test' / 'client' / 'mods.json').write_text(json.dumps(manifest))
            with mock.patch.object(clientqa.urllib.request, 'urlopen') as urlopen:
                result = clientqa.ensure_mods(root)
            urlopen.assert_not_called()
            self.assertEqual(result, [mods / 'A.jar'])

    def test_ensure_mods_rejects_sha_mismatch_after_download(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / 'src' / 'test' / 'client').mkdir(parents=True)
            manifest = {'mods': [{'id': 'a', 'file': 'A.jar', 'url': 'https://x/A.jar', 'sha1': '00'}]}
            (root / 'src' / 'test' / 'client' / 'mods.json').write_text(json.dumps(manifest))
            response = mock.MagicMock()
            response.__enter__.return_value.read.side_effect = [b'bytes', b'']
            with mock.patch.object(clientqa.urllib.request, 'urlopen', return_value=response):
                with self.assertRaises(RuntimeError):
                    clientqa.ensure_mods(root)
            self.assertFalse((root / 'build' / 'client-qa' / 'mods' / 'A.jar').exists())

    def test_ensure_mods_download_has_a_timeout(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / 'src' / 'test' / 'client').mkdir(parents=True)
            payload = b'jar-bytes'
            manifest = {'mods': [{'id': 'a', 'file': 'A.jar', 'url': 'https://x/A.jar', 'sha1': hashlib.sha1(payload).hexdigest()}]}
            (root / 'src' / 'test' / 'client' / 'mods.json').write_text(json.dumps(manifest))
            response = mock.MagicMock()
            response.__enter__.return_value.read.side_effect = [payload, b'']
            with mock.patch.object(clientqa.urllib.request, 'urlopen', return_value=response) as urlopen:
                result = clientqa.ensure_mods(root)
            urlopen.assert_called_once_with('https://x/A.jar', timeout=60)
            self.assertEqual(result[0].read_bytes(), payload)

    def test_ensure_mods_leaves_no_jar_when_download_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / 'src' / 'test' / 'client').mkdir(parents=True)
            manifest = {'mods': [{'id': 'a', 'file': 'A.jar', 'url': 'https://x/A.jar', 'sha1': '00'}]}
            (root / 'src' / 'test' / 'client' / 'mods.json').write_text(json.dumps(manifest))
            response = mock.MagicMock()
            response.__enter__.return_value.read.side_effect = [b'bytes', OSError('connection reset')]
            with mock.patch.object(clientqa.urllib.request, 'urlopen', return_value=response):
                with self.assertRaises(OSError):
                    clientqa.ensure_mods(root)
            self.assertFalse((root / 'build' / 'client-qa' / 'mods' / 'A.jar').exists())


def mod_jar(path: Path, mod_id: str, version: str) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, 'w') as archive:
        archive.writestr('fabric.mod.json', json.dumps({'id': mod_id, 'version': version}))
    return path


class FlashbackConfigTest(unittest.TestCase):
    def test_write_flashback_config_sets_quicksave_and_hotbar(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            game = Path(tmp)
            clientqa.write_flashback_config(game)
            data = json.loads((game / 'config' / 'flashback' / 'flashback.json').read_text())
            self.assertTrue(data['recordingControls']['quicksave'])
            self.assertTrue(data['recording']['recordHotbar'])
            self.assertEqual(data['recording']['localPlayerUpdatesPerSecond'], 60)
            self.assertEqual(data['configVersion'], 2)


class PrepareInstanceTest(unittest.TestCase):
    @unittest.skipUnless(sys.platform == 'darwin' and shutil.which('clang++')
                         and (clientqa.LAUNCHER.parent.parent / 'Frameworks' / 'QtCore.framework').is_dir(),
                         'Prism QtCore and clang++ are required for the native settings parser')
    def test_native_prism_settings_preserve_hidden_argument_and_output_path(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root: Path = Path(tmp)
            instance: Path = root / 'instances' / 'AdaptDemoStudio'
            self.prepare(root, instance, 18762, 'native-settings-test')
            executable: Path = root / 'qsettings-arguments'
            frameworks: str = str(clientqa.LAUNCHER.parent.parent / 'Frameworks')
            source: Path = Path(__file__).with_name('qsettings_arguments.cpp')
            subprocess.run(['clang++', '-std=c++17', '-F' + frameworks, '-framework', 'QtCore', '-Wl,-rpath,' + frameworks,
                            str(source), '-o', str(executable)], capture_output=True, text=True, check=True, timeout=30)
            result: subprocess.CompletedProcess = subprocess.run([str(executable), str(instance / 'instance.cfg')],
                                                                   capture_output=True, text=True, check=True, timeout=5)
            arguments: list[str] = json.loads(result.stdout)
        self.assertEqual(arguments, ['-Dadapt.qa.hidden=true', '-Dadapt.qa.port=18762', '-Dadapt.qa.token=native-settings-test',
                                     '-Dadapt.qa.output=' + str(root / 'out')])

    def pinned(self, root: Path) -> tuple[list[Path], Path]:
        cache = root / 'cache'
        mods = [mod_jar(cache / 'Flashback-0.43.6-for-MC26.2.jar', 'flashback', '0.43.6'),
                mod_jar(cache / 'fabric-api-0.161.0+26.2.jar', 'fabric-api', '0.161.0+26.2')]
        return mods, mod_jar(cache / 'adapt-client-qa.jar', 'adapt_client_qa', '1.0.0')

    def prepare(self, root: Path, instance: Path, port: int, token: str) -> bool:
        mods, bridge = self.pinned(root)
        return clientqa.prepare_instance(instance, 'Adapt Demo Studio', port, token, root / 'out', mods, bridge)

    def test_creation_from_nothing_writes_a_complete_instance(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            instance = root / 'instances' / 'AdaptDemoStudio'
            created = self.prepare(root, instance, 18762, 't' * 48)
            config = (instance / 'instance.cfg').read_text().splitlines()
            pack = json.loads((instance / 'mmc-pack.json').read_text())
            mods = sorted(path.name for path in (instance / '.minecraft' / 'mods').iterdir())
            flashback = json.loads((instance / '.minecraft' / 'config' / 'flashback' / 'flashback.json').read_text())
            options = (instance / '.minecraft' / 'options.txt').read_text().splitlines()
        self.assertTrue(created)
        self.assertEqual(config[0], '[General]')
        self.assertIn('name=Adapt Demo Studio', config)
        self.assertIn('MaxMemAlloc=4096', config)
        self.assertIn('OverrideJavaArgs=true', config)
        self.assertIn('JvmArgs=-Dadapt.qa.hidden=true -Dadapt.qa.port=18762 -Dadapt.qa.token=' + 't' * 48 + ' -Dadapt.qa.output="' + str(root / 'out') + '"', config)
        self.assertEqual([line for line in config if line.startswith(('OverrideWindow=', 'MinecraftWin'))],
                         ['OverrideWindow=true', 'MinecraftWinWidth=1920', 'MinecraftWinHeight=1080'])
        self.assertEqual([component['uid'] for component in pack['components']],
                         ['org.lwjgl3', 'net.minecraft', 'net.fabricmc.intermediary', 'net.fabricmc.fabric-loader'])
        self.assertEqual(mods, ['AdaptClientQa.jar', 'Flashback-0.43.6-for-MC26.2.jar', 'fabric-api-0.161.0+26.2.jar'])
        self.assertEqual(flashback, {'configVersion': 2, 'recording': {'recordHotbar': True, 'localPlayerUpdatesPerSecond': 60},
                                     'recordingControls': {'quicksave': True}})
        self.assertIn('tutorialStep:none', options)
        self.assertIn('renderDistance:6', options)

    def test_refresh_preserves_foreign_settings_and_mods(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            instance = root / 'instances' / 'AdaptDemoStudio'
            game = instance / '.minecraft'
            (game / 'config' / 'flashback').mkdir(parents=True)
            (instance / 'instance.cfg').write_text('[General]\nJvmArgs="-Dadapt.qa.port=1 -Dadapt.qa.token=old -Dadapt.qa.hidden=false"\nMaxMemAlloc=8192\n'
                                                   'name=Adapt Demo Studio\nOverrideJavaArgs=true\n\n[UI]\nmods_Page\\Columns="AAAA"\n')
            (instance / 'mmc-pack.json').write_text('{"components": []}')
            mod_jar(game / 'mods' / 'sodium-0.8.jar', 'sodium', '0.8.0')
            mod_jar(game / 'mods' / 'fabric-api-0.150.0+26.2.jar', 'fabric-api', '0.150.0+26.2')
            mod_jar(game / 'mods' / 'flashback.jar', 'flashback', '0.43.6')
            (game / 'mods' / 'iris-shaders.jar.disabled').write_bytes(b'disabled')
            (game / 'mods' / 'AdaptClientQa.jar').write_bytes(b'old bridge')
            (game / 'config' / 'flashback' / 'flashback.json').write_text(json.dumps(
                {'configVersion': 2, 'recording': {'recordHotbar': False, 'markDimensionChanges': True},
                 'recordingControls': {'automaticallyFinish': True}, 'exporting': {'defaultDirectory': '/clips'}}))
            (game / 'options.txt').write_text('version:4671\nrenderDistance:16\npauseOnLostFocus:true\nkey_key.jump:key.keyboard.space\n')
            created = self.prepare(root, instance, 40111, 'n' * 48)
            config = (instance / 'instance.cfg').read_text().splitlines()
            pack = (instance / 'mmc-pack.json').read_text()
            mods = sorted(path.name for path in (game / 'mods').iterdir())
            bridge = (game / 'mods' / 'AdaptClientQa.jar').read_bytes()
            flashback = json.loads((game / 'config' / 'flashback' / 'flashback.json').read_text())
            options = (game / 'options.txt').read_text().splitlines()
        self.assertFalse(created)
        self.assertEqual(config, ['[General]', 'JvmArgs=-Dadapt.qa.hidden=true -Dadapt.qa.port=40111 -Dadapt.qa.token=' + 'n' * 48 + ' -Dadapt.qa.output="' + str(root / 'out') + '"',
                                  'MaxMemAlloc=8192', 'name=Adapt Demo Studio', 'OverrideJavaArgs=true', '', 'OverrideWindow=true',
                                  'MinecraftWinWidth=1920', 'MinecraftWinHeight=1080', '[UI]', 'mods_Page\\Columns="AAAA"'])
        self.assertEqual(pack, '{"components": []}')
        self.assertEqual(mods, ['AdaptClientQa.jar', 'fabric-api-0.161.0+26.2.jar', 'flashback.jar', 'iris-shaders.jar.disabled', 'sodium-0.8.jar'])
        self.assertNotEqual(bridge, b'old bridge')
        self.assertEqual(flashback, {'configVersion': 2, 'recording': {'recordHotbar': True, 'markDimensionChanges': True, 'localPlayerUpdatesPerSecond': 60},
                                     'recordingControls': {'automaticallyFinish': True, 'quicksave': True}, 'exporting': {'defaultDirectory': '/clips'}})
        self.assertEqual(options, ['version:4671', 'renderDistance:16', 'pauseOnLostFocus:false', 'key_key.jump:key.keyboard.space',
                                   'tutorialStep:none', 'autoJump:false'])

    def test_refresh_owns_the_window_size_and_keeps_the_window_mode(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root: Path = Path(tmp)
            instance: Path = root / 'instances' / 'AdaptDemoStudio-2'
            (instance / '.minecraft').mkdir(parents=True)
            (instance / 'instance.cfg').write_text('[General]\nLaunchMaximized=false\nMinecraftWinHeight=540\nMinecraftWinWidth=960\n'
                                                   'OverrideWindow=false\nname=Adapt Demo Studio 2\n')
            (instance / '.minecraft' / 'options.txt').write_text('fullscreen:false\n')
            self.prepare(root, instance, 40112, 'w' * 48)
            config: list[str] = (instance / 'instance.cfg').read_text().splitlines()
            options: list[str] = (instance / '.minecraft' / 'options.txt').read_text().splitlines()
        self.assertEqual(config[:6], ['[General]', 'LaunchMaximized=false', 'MinecraftWinHeight=1080', 'MinecraftWinWidth=1920', 'OverrideWindow=true',
                                      'name=Adapt Demo Studio 2'])
        self.assertIn('fullscreen:false', options)

    def test_missing_general_keys_are_added_inside_the_general_section(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            config = Path(tmp) / 'instance.cfg'
            config.write_text('[General]\nname=Adapt Demo Studio\n[UI]\nwidth=1\n')
            clientqa.set_instance_keys(config, {'OverrideJavaArgs': 'true', 'JvmArgs': '-Da=1'})
            lines = config.read_text().splitlines()
        self.assertEqual(lines, ['[General]', 'name=Adapt Demo Studio', 'OverrideJavaArgs=true', 'JvmArgs=-Da=1', '[UI]', 'width=1'])

    def test_different_flashback_version_is_refused_and_left_in_place(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            instance = root / 'instances' / 'AdaptDemoStudio'
            installed = mod_jar(instance / '.minecraft' / 'mods' / 'Flashback-0.44.0.jar', 'flashback', '0.44.0')
            with self.assertRaisesRegex(RuntimeError, 'flashback 0.44.0 is installed at .*Flashback-0.44.0.jar, but the client bridge is built against flashback 0.43.6'):
                self.prepare(root, instance, 40111, 'n' * 48)
            kept = installed.is_file()
            copied = (instance / '.minecraft' / 'mods' / 'Flashback-0.43.6-for-MC26.2.jar').exists()
        self.assertTrue(kept)
        self.assertFalse(copied)


class ConfigureServerTest(unittest.TestCase):
    def test_given_adapt_jar_is_installed_with_the_studio_properties(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            server: Path = Path(tmp) / 'server'
            (server / 'plugins').mkdir(parents=True)
            (server / 'server.properties').write_text('server-port=52000\nview-distance=10\n')
            adapt: Path = Path(tmp) / 'out' / 'jars' / 'Adapt.jar'
            adapt.parent.mkdir(parents=True)
            adapt.write_text('run copy')
            clientqa.configure_server(server, adapt)
            installed: str = (server / 'plugins' / 'Adapt.jar').read_text()
            properties: list[str] = (server / 'server.properties').read_text().splitlines()
        self.assertEqual(installed, 'run copy')
        self.assertIn('server-port=52000', properties)
        self.assertIn('view-distance=6', properties)


class MuxTest(unittest.TestCase):
    def test_failure_reports_multiplexor_output(self) -> None:
        failed = clientqa.subprocess.CompletedProcess(['start.sh'], 1, stdout='[SYNC] ok\n', stderr='[ERR] Failed to start runtime for adapt-demo-x\n')
        with mock.patch.object(clientqa.subprocess, 'run', return_value=failed):
            with self.assertRaisesRegex(RuntimeError, '(?s)runtime start adapt-demo-x failed with exit 1: .*Failed to start runtime for adapt-demo-x'):
                clientqa.mux('runtime', 'start', 'adapt-demo-x')

    def test_success_returns_stripped_stdout(self) -> None:
        done = clientqa.subprocess.CompletedProcess(['start.sh'], 0, stdout='/servers/adapt-demo-x\n', stderr='')
        with mock.patch.object(clientqa.subprocess, 'run', return_value=done):
            self.assertEqual(clientqa.mux('instance', 'path', 'adapt-demo-x'), '/servers/adapt-demo-x')


if __name__ == '__main__':
    unittest.main()
