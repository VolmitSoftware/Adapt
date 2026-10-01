import contextlib
import io
import json
import os
import struct
import sys
import tempfile
import unittest
import urllib.error
from pathlib import Path
from typing import Iterator
from unittest import mock

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / 'src' / 'test' / 'demo'))
import studio

INSTANCE: str = 'AdaptDemoStudio'
OVERWORLD: str = 'iris:adapt_demo'
NETHER: str = 'minecraft:the_nether'
TOKEN: str = '5f' * 24
REJECTED: str = '  5454.288 Warning: Launch command requires an valid instance ID.  "' + INSTANCE + '" resolves to nothing. (unknown:0)\n'
FOUND: str = '  5454.554 Info: Found instance ID "' + INSTANCE + '" (unknown:0)\n'


def launched(token: str) -> str:
    return ('  6021.490 Debug: [launcher.task] Task "LauncherPartLaunch(0x1 ID: 2)" starting for the first time (unknown:0)\n'
            '  6021.512 Debug: "-Duser.language=en -Dadapt.qa.port=1 -Dadapt.qa.token=' + token + ' -Dadapt.qa.output=/out" (unknown:0)\n')


LAUNCHED: str = launched(TOKEN)


class PrismLaunchTest(unittest.TestCase):
    def setUp(self) -> None:
        patcher = mock.patch('sys.stdout', new_callable=io.StringIO)
        self.stdout = patcher.start()
        self.addCleanup(patcher.stop)

    def launch(self, log: Path, writes: list[str]) -> mock.MagicMock:
        def start(command: list[str], **values: object) -> mock.MagicMock:
            if writes:
                with log.open('a') as handle:
                    handle.write(writes.pop(0))
            return mock.MagicMock()

        with mock.patch.object(studio.subprocess, 'Popen', side_effect=start) as popen:
            studio.launch_prism(['/launcher', '--launch', INSTANCE], INSTANCE, TOKEN, mock.MagicMock(), log, watch=0.3, poll=0.01)
        return popen

    def test_rejected_launch_is_retried_once(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            log = Path(tmp) / 'PrismLauncher-0.log'
            log.write_text('  1.000 Info: earlier session\n')
            popen = self.launch(log, [REJECTED + FOUND])
        self.assertEqual(popen.call_count, 2)
        self.assertEqual(popen.call_args_list[0].args[0], popen.call_args_list[1].args[0])
        self.assertIn('[PRISM]', self.stdout.getvalue())

    def test_second_rejection_does_not_launch_a_third_time(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            log = Path(tmp) / 'PrismLauncher-0.log'
            log.write_text('')
            popen = self.launch(log, [REJECTED + FOUND, REJECTED])
        self.assertEqual(popen.call_count, 2)

    def test_accepted_launch_is_not_retried(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            log = Path(tmp) / 'PrismLauncher-0.log'
            log.write_text('')
            popen = self.launch(log, [LAUNCHED])
        self.assertEqual(popen.call_count, 1)

    def test_quiet_log_is_not_retried(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            popen = self.launch(Path(tmp) / 'missing.log', [])
        self.assertEqual(popen.call_count, 1)

    def test_rejection_of_other_instance_is_ignored(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            log = Path(tmp) / 'PrismLauncher-0.log'
            log.write_text('')
            popen = self.launch(log, [REJECTED.replace(INSTANCE, 'AdaptDemo-other')])
        self.assertEqual(popen.call_count, 1)

    def test_rejection_of_a_numbered_lane_is_ignored_by_lane_one(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            log = Path(tmp) / 'PrismLauncher-0.log'
            log.write_text('')
            popen = self.launch(log, [REJECTED.replace(INSTANCE, INSTANCE + '-2')])
        self.assertEqual(popen.call_count, 1)

    def test_launch_of_another_lane_does_not_end_the_watch(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            log = Path(tmp) / 'PrismLauncher-0.log'
            log.write_text('')
            late: list[str] = [REJECTED + FOUND]

            def pause(seconds: float) -> None:
                if late:
                    with log.open('a') as handle:
                        handle.write(late.pop(0))

            with mock.patch.object(studio.time, 'sleep', side_effect=pause):
                popen = self.launch(log, [launched('6e' * 24)])
        self.assertEqual(popen.call_count, 2)

    def test_rejection_logged_before_launch_is_ignored(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            log = Path(tmp) / 'PrismLauncher-0.log'
            log.write_text(REJECTED)
            popen = self.launch(log, [])
        self.assertEqual(popen.call_count, 1)

    def test_rotated_log_is_read_from_start(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            log = Path(tmp) / 'PrismLauncher-0.log'
            log.write_text('x' * 4096)
            launches: list[list[str]] = []

            def start(command: list[str], **values: object) -> mock.MagicMock:
                if not launches:
                    log.write_text(REJECTED + FOUND)
                launches.append(command)
                return mock.MagicMock()

            with mock.patch.object(studio.subprocess, 'Popen', side_effect=start) as popen:
                studio.launch_prism(['/launcher'], INSTANCE, TOKEN, mock.MagicMock(), log, watch=0.3, poll=0.01)
        self.assertEqual(popen.call_count, 2)


class FakeConnection:
    def __init__(self, replies: list[str]) -> None:
        self.replies: list[str] = replies
        self.buffer: bytes = b''
        self.timeout: float | None = None

    def settimeout(self, value: float) -> None:
        self.timeout = value

    def sendall(self, data: bytes) -> None:
        identifier, kind = struct.unpack('<ii', data[4:12])
        body: bytes = b'' if kind == 3 else self.replies.pop(0).encode()
        payload: bytes = struct.pack('<ii', identifier, 2) + body + b'\0\0'
        self.buffer += struct.pack('<i', len(payload)) + payload

    def recv(self, size: int) -> bytes:
        chunk: bytes = self.buffer[:size]
        self.buffer = self.buffer[size:]
        return chunk

    def close(self) -> None:
        pass


class DemoRconTest(unittest.TestCase):
    def connect(self, tmp: str, replies: list[str]) -> tuple[studio.DemoRcon, FakeConnection]:
        server = Path(tmp)
        (server / 'server.properties').write_text('rcon.port=25575\nrcon.password=secret\n')
        connection = FakeConnection(replies)
        with mock.patch.object(studio.clientqa.socket, 'create_connection', return_value=connection):
            rcon = studio.DemoRcon(server)
        return rcon, connection

    def test_command_timeout_allows_long_fixture_builds(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            _, connection = self.connect(tmp, [])
        self.assertGreaterEqual(connection.timeout, 180.0)

    def test_fixture_error_reply_raises(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            rcon, _ = self.connect(tmp, ['ADAPT_QA ERROR World not loaded: adapt_demo'])
            with self.assertRaisesRegex(RuntimeError, 'World not loaded: adapt_demo'):
                rcon.command('adaptqa demo world adapt_demo')

    def test_plain_reply_is_returned(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            rcon, _ = self.connect(tmp, ['ADAPT_QA DEMO PLATE 0 71 0'])
            self.assertEqual(rcon.command('adaptqa demo plate 0 0'), 'ADAPT_QA DEMO PLATE 0 71 0')


def http_error(code: int, error: str) -> urllib.error.HTTPError:
    return urllib.error.HTTPError('http://127.0.0.1:1/command', code, 'failure', {}, io.BytesIO(json.dumps({'ok': False, 'error': error}).encode()))


class DemoBridgeTest(unittest.TestCase):
    def test_state_respects_the_remaining_window_fit_timeout(self) -> None:
        bridge: studio.DemoBridge = studio.DemoBridge(4321, 'token')
        response = mock.MagicMock()
        response.__enter__.return_value = io.BytesIO(b'{"hiddenRenderer":true}')
        with mock.patch.object(studio.urllib.request, 'urlopen', return_value=response) as urlopen:
            state: dict = bridge.state(timeout=0.25)
        self.assertEqual(state, {'hiddenRenderer': True})
        self.assertEqual(urlopen.call_args.args[0].full_url, 'http://127.0.0.1:4321/state')
        self.assertEqual(urlopen.call_args.kwargs['timeout'], 0.25)

    def test_gateway_timeout_becomes_bridge_timeout(self) -> None:
        bridge = studio.DemoBridge(1, 'token')
        with mock.patch.object(studio.urllib.request, 'urlopen', side_effect=http_error(504, 'Client did not process command within five seconds')):
            with self.assertRaises(studio.BridgeTimeout):
                bridge.command('record', action='stop')

    def test_rejection_is_runtime_error_with_bridge_message(self) -> None:
        bridge = studio.DemoBridge(1, 'token')
        with mock.patch.object(studio.urllib.request, 'urlopen', side_effect=http_error(400, 'Flashback is not recording')):
            with self.assertRaises(RuntimeError) as raised:
                bridge.command('record', action='stop')
        self.assertNotIsInstance(raised.exception, studio.BridgeTimeout)
        self.assertIn('Flashback is not recording', str(raised.exception))

    def test_export_status_reads_status_path_with_token(self) -> None:
        bridge = studio.DemoBridge(4321, 'token')
        response = mock.MagicMock()
        response.__enter__.return_value = io.BytesIO(json.dumps({'ok': True, 'exporting': False}).encode())
        with mock.patch.object(studio.urllib.request, 'urlopen', return_value=response) as urlopen:
            status = bridge.export_status()
        request = urlopen.call_args.args[0]
        self.assertEqual(request.full_url, 'http://127.0.0.1:4321/export-status')
        self.assertEqual(request.get_header('X-adapt-qa-token'), 'token')
        self.assertEqual(status, {'ok': True, 'exporting': False})


class StudioHelpersTest(unittest.TestCase):
    def test_parse_plate_reads_origin(self) -> None:
        self.assertEqual(studio.parse_plate('ADAPT_QA DEMO PLATE 0 71 -16'), (0.0, 71.0, -16.0))

    def test_parse_plate_skips_leading_output(self) -> None:
        self.assertEqual(studio.parse_plate('[Iris] done\nADAPT_QA DEMO PLATE 8 64 8'), (8.0, 64.0, 8.0))

    def test_parse_plate_rejects_other_reply(self) -> None:
        with self.assertRaises(RuntimeError):
            studio.parse_plate('ADAPT_QA DEMO RESET')

    def test_parse_locate_reads_origin_and_biome(self) -> None:
        self.assertEqual(studio.parse_locate('ADAPT_QA DEMO LOCATE 128 -64 overworld:plains'), (128, -64, 'overworld:plains'))

    def test_parse_locate_skips_leading_output(self) -> None:
        self.assertEqual(studio.parse_locate('[Iris] sampled\nADAPT_QA DEMO LOCATE -320 0 savanna'), (-320, 0, 'savanna'))

    def test_parse_locate_rejects_other_reply(self) -> None:
        with self.assertRaisesRegex(RuntimeError, 'Unexpected locate reply'):
            studio.parse_locate('ADAPT_QA DEMO PLATE 0 71 0')

    def test_parse_locate_rejects_incomplete_reply(self) -> None:
        with self.assertRaisesRegex(RuntimeError, 'Incomplete locate reply'):
            studio.parse_locate('ADAPT_QA DEMO LOCATE 128 -64')

    def test_await_world_retries_while_pending(self) -> None:
        rcon = mock.MagicMock()
        rcon.command.side_effect = ['ADAPT_QA DEMO WORLD PENDING adapt_demo', 'ADAPT_QA DEMO WORLD PENDING adapt_demo', 'ADAPT_QA DEMO WORLD adapt_demo']
        with mock.patch.object(studio.time, 'sleep'):
            studio.await_world(rcon, 'adapt_demo', 60.0)
        self.assertEqual(rcon.command.call_count, 3)
        self.assertEqual(rcon.command.call_args.args[0], 'adaptqa demo world adapt_demo')

    def test_await_world_times_out_while_pending(self) -> None:
        rcon = mock.MagicMock()
        rcon.command.return_value = 'ADAPT_QA DEMO WORLD PENDING adapt_demo'
        with mock.patch.object(studio.time, 'sleep'):
            with self.assertRaisesRegex(RuntimeError, 'did not load within 0.0 s: ADAPT_QA DEMO WORLD PENDING adapt_demo'):
                studio.await_world(rcon, 'adapt_demo', 0.0)

    def test_await_locate_polls_while_the_search_is_pending(self) -> None:
        rcon = mock.MagicMock()
        rcon.command.side_effect = ['ADAPT_QA DEMO LOCATE PENDING 0/441', 'ADAPT_QA DEMO LOCATE PENDING 200/441', 'ADAPT_QA DEMO LOCATE -1280 5120 temperate/plains']
        with mock.patch.object(studio.time, 'sleep'):
            located = studio.await_locate(rcon, 6144, 256, 60.0)
        self.assertEqual(located, (-1280, 5120, 'temperate/plains'))
        self.assertEqual([call.args[0] for call in rcon.command.call_args_list], ['adaptqa demo locate 6144 256'] * 3)

    def test_await_locate_times_out_while_pending(self) -> None:
        rcon = mock.MagicMock()
        rcon.command.return_value = 'ADAPT_QA DEMO LOCATE PENDING 5/441'
        with mock.patch.object(studio.time, 'sleep'):
            with self.assertRaisesRegex(RuntimeError, 'Plate search did not finish within 0.0 s: ADAPT_QA DEMO LOCATE PENDING 5/441'):
                studio.await_locate(rcon, 6144, 256, 0.0)

    def test_await_world_fixture_error_is_not_retried(self) -> None:
        rcon = mock.MagicMock()
        rcon.command.side_effect = RuntimeError('adaptqa demo world adapt_demo: console or operator player required')
        with mock.patch.object(studio.time, 'sleep'):
            with self.assertRaisesRegex(RuntimeError, 'operator player required'):
                studio.await_world(rcon, 'adapt_demo', 60.0)
        self.assertEqual(rcon.command.call_count, 1)



class IrisJarTest(unittest.TestCase):
    def write_jars(self, libs: Path, names: list[str]) -> None:
        libs.mkdir(parents=True)
        for age, name in enumerate(names):
            jar: Path = libs / name
            jar.write_bytes(b'jar')
            os.utime(jar, (1000 + age, 1000 + age))

    def test_newest_packed_jar_is_chosen_over_newer_intermediate(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            libs = Path(tmp) / 'libs'
            self.write_jars(libs, ['Iris v4.1.2-26.2 [CraftBukkit] 26.1.2-26.3-packed.jar', 'Iris v4.2.0-26.2 [CraftBukkit] 26.1.2-26.3-packed.jar',
                                   'Iris v4.2.0-26.2 [CraftBukkit] 26.1.2-26.3.jar', 'Iris v4.2.0-26.2 [CraftBukkit] 26.1.2-26.3-sources.jar'])
            self.assertEqual(studio.iris_jar(libs).name, 'Iris v4.2.0-26.2 [CraftBukkit] 26.1.2-26.3-packed.jar')

    def test_missing_packed_jar_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            libs = Path(tmp) / 'libs'
            self.write_jars(libs, ['Iris v4.2.0-26.2 [CraftBukkit] 26.1.2-26.3.jar'])
            with self.assertRaisesRegex(RuntimeError, 'No packed Iris jar'):
                studio.iris_jar(libs)


ADAPT_TOML: str = ('# Shows aggregated skill XP gains on the action bar.\nactionbarNotifyXp = true\nactionbarXpDurationMillis = 1500\n'
                   'actionbarNotifyLevel = true\n# Mentions actionbarNotifyMasterLevel in a comment.\nactionbarNotifyMasterLevel=true\n'
                   '\n[effects]\nsoundsEnabled = true\n')
DISCOVERY_TOML: str = ('# Adapt configuration - skill:discovery\n\n# Enables or disables this feature.\nenabled = true\nskillColor = "&b"\n'
                       'showParticles = true\n\n[playerPreferences]\n\n[playerPreferences.enabled]\nenabled = true\ndefaultValue = "ON"\n')


class StudioRestartTest(unittest.TestCase):
    def restart(self, skill: str, plugins: tuple[str, ...] = ()) -> tuple[list[str], str, str]:
        with tempfile.TemporaryDirectory() as tmp, mock.patch.object(studio, 'LOCKS', Path(tmp) / 'locks'):
            root = Path(tmp)
            demo = studio.Studio(root / 'out', skip_build=True)
            demo.required_plugins = plugins
            demo.output.mkdir()
            demo.server = root / 'server'
            (demo.server / 'logs').mkdir(parents=True)
            (demo.server / 'logs' / 'latest.log').write_text('Deferred recipe registration completed\n')
            config = demo.server / 'plugins' / 'Adapt' / 'adapt.toml'
            discovery = demo.server / 'plugins' / 'Adapt' / 'skills' / 'discovery.toml'
            discovery.parent.mkdir(parents=True)
            config.write_text(ADAPT_TOML)
            discovery.write_text(DISCOVERY_TOML)
            events: list[str] = []

            def mux(*arguments: str, timeout: int = 180) -> str:
                muted: bool = 'actionbarNotifyXp = false' in config.read_text()
                quiet: bool = discovery.read_text().startswith('# Adapt configuration - skill:discovery\n\n# Enables or disables this feature.\nenabled = false\n')
                events.append(' '.join(arguments[:2]) + (' log-present' if (demo.server / 'logs' / 'latest.log').exists() else '')
                              + (' muted' if muted else '') + (' discovery-off' if quiet else ''))
                return ''

            with mock.patch.object(studio.clientqa, 'mux', side_effect=mux), \
                    mock.patch.object(studio.clientqa, 'wait_server', side_effect=lambda server: events.append('wait')):
                demo.restart_server(skill)
            return events, (demo.output / 'logs' / (demo.suffix + '-server-install.log')).read_text(), discovery.read_text()

    def test_restart_moves_first_boot_log_and_mutes_popups_before_starting_again(self) -> None:
        events, install_log, _ = self.restart('agility')
        self.assertEqual(events, ['runtime status log-present', 'runtime stop log-present', 'runtime status log-present',
                                  'runtime start muted discovery-off', 'wait'])
        self.assertIn('Deferred recipe registration completed', install_log)

    def test_restart_turns_the_discovery_skill_off_for_another_batch_skill(self) -> None:
        _, _, discovery = self.restart('pickaxe')
        self.assertEqual(discovery, DISCOVERY_TOML.replace('this feature.\nenabled = true', 'this feature.\nenabled = false'))

    def test_restart_keeps_the_discovery_skill_on_for_the_discovery_batch(self) -> None:
        events, _, discovery = self.restart('discovery')
        self.assertEqual(events, ['runtime status log-present', 'runtime stop log-present', 'runtime status log-present', 'runtime start muted', 'wait'])
        self.assertEqual(discovery, DISCOVERY_TOML)

    def test_gloss_comparisons_keep_discovery_available_for_insight(self) -> None:
        _, _, discovery = self.restart('kinetics', ('Gloss',))
        self.assertEqual(discovery, DISCOVERY_TOML)


RUNNING_STATUS: str = ('[OK] Runtime running: adapt-demo-1\nstate:        running\nmode:         tmux\ntmux session: mc-plugin-adapt-demo-1\n'
                       'server port:  25590\nconsole pid:  none\nserver pid:   4242\nlog:          /mux/state/runtime/adapt-demo-1.log')
STOPPED_STATUS: str = ('[WARN] Runtime stopped: adapt-demo-1\nstate:        stopped\nmode:         stopped\ntmux session: none\n'
                       'server port:  25590\nconsole pid:  none\nserver pid:   none\nlog:          /mux/state/runtime/adapt-demo-1.log')
START_FAILURE: str = 'Multiplexor runtime start adapt-demo-1 --no-console failed with exit 1: '


class RuntimeStateTest(unittest.TestCase):
    def test_running_runtime_reports_its_server_pid(self) -> None:
        self.assertEqual(studio.runtime_state(RUNNING_STATUS), (True, 4242))

    def test_stopped_runtime_has_no_pid(self) -> None:
        self.assertEqual(studio.runtime_state(STOPPED_STATUS), (False, None))

    def test_background_runtime_is_running(self) -> None:
        self.assertEqual(studio.runtime_state(RUNNING_STATUS.replace('mode:         tmux', 'mode:         background')), (True, 4242))


class StudioRestartWaitTest(unittest.TestCase):
    def setUp(self) -> None:
        output = mock.patch('sys.stdout', new_callable=io.StringIO)
        self.stdout = output.start()
        self.addCleanup(output.stop)
        self.clock: list[float] = [0.0]
        self.sleeps: list[float] = []

        def sleep(seconds: float) -> None:
            self.sleeps.append(seconds)
            self.clock[0] += seconds

        for patcher in (mock.patch.object(studio.time, 'sleep', side_effect=sleep),
                        mock.patch.object(studio.time, 'monotonic', side_effect=lambda: self.clock[0])):
            patcher.start()
            self.addCleanup(patcher.stop)

    def restart(self, statuses: list[str], alive: list[bool] | None = None, start_failures: int = 0) -> tuple[list[str], list[int]]:
        events: list[str] = []
        probed: list[int] = []
        remaining: list[int] = [start_failures]
        pending: list[bool] = list(alive or [])

        def mux(*arguments: str, timeout: int = 180) -> str:
            events.append(' '.join(arguments[:2]))
            if arguments[:2] == ('runtime', 'status'):
                return statuses.pop(0) if len(statuses) > 1 else statuses[0]
            if arguments[:2] == ('runtime', 'start') and remaining[0] > 0:
                remaining[0] -= 1
                raise RuntimeError(START_FAILURE)
            return ''

        def process_alive(pid: int) -> bool:
            probed.append(pid)
            return pending.pop(0) if pending else False

        with tempfile.TemporaryDirectory() as tmp, mock.patch.object(studio, 'LOCKS', Path(tmp) / 'locks'):
            root: Path = Path(tmp)
            demo: studio.Studio = studio.Studio(root / 'out', skip_build=True)
            demo.server_name = 'adapt-demo-1'
            demo.server = root / 'server'
            adapt: Path = demo.server / 'plugins' / 'Adapt'
            (adapt / 'skills').mkdir(parents=True)
            (adapt / 'adapt.toml').write_text(ADAPT_TOML)
            (adapt / 'skills' / 'discovery.toml').write_text(DISCOVERY_TOML)
            with mock.patch.object(studio.clientqa, 'mux', side_effect=mux), mock.patch.object(studio, 'process_alive', side_effect=process_alive), \
                    mock.patch.object(studio.clientqa, 'wait_server', side_effect=lambda server: events.append('wait')):
                demo.restart_server('agility')
        return events, probed

    def test_start_waits_until_multiplexor_reports_the_runtime_stopped(self) -> None:
        events, _ = self.restart([RUNNING_STATUS, RUNNING_STATUS, RUNNING_STATUS, STOPPED_STATUS])
        self.assertEqual(events, ['runtime status', 'runtime stop', 'runtime status', 'runtime status', 'runtime status', 'runtime start', 'wait'])
        self.assertEqual(self.sleeps, [studio.RUNTIME_POLL] * 2)

    def test_start_waits_until_the_old_server_process_has_exited(self) -> None:
        events, probed = self.restart([RUNNING_STATUS, STOPPED_STATUS], alive=[True, True, False])
        self.assertEqual(probed, [4242, 4242, 4242])
        self.assertEqual(events, ['runtime status', 'runtime stop', 'runtime status', 'runtime status', 'runtime status', 'runtime start', 'wait'])

    def test_runtime_still_running_after_two_minutes_fails_without_starting(self) -> None:
        with self.assertRaisesRegex(RuntimeError, 'adapt-demo-1 was still running 120 s after runtime stop'):
            self.restart([RUNNING_STATUS])
        self.assertEqual(studio.RUNTIME_EXIT_TIMEOUT, 120.0)
        self.assertGreaterEqual(self.clock[0], 120.0)

    def test_failed_start_is_retried_once(self) -> None:
        events, _ = self.restart([STOPPED_STATUS], start_failures=1)
        self.assertEqual(events[-3:], ['runtime start', 'runtime start', 'wait'])
        self.assertEqual(self.sleeps, [studio.START_RETRY_SECONDS])
        self.assertIn('[RESTART] ' + START_FAILURE, self.stdout.getvalue())

    def test_second_failed_start_fails_the_restart(self) -> None:
        with self.assertRaisesRegex(RuntimeError, 'failed with exit 1'):
            self.restart([STOPPED_STATUS], start_failures=2)


class SwitchOffTest(unittest.TestCase):
    def test_popup_keys_are_disabled_and_the_rest_is_preserved(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            config = Path(tmp) / 'adapt.toml'
            config.write_text(ADAPT_TOML)
            studio.switch_off(config, studio.NOTIFY_KEYS)
            text = config.read_text()
        self.assertEqual(text, ADAPT_TOML.replace('actionbarNotifyXp = true', 'actionbarNotifyXp = false')
                         .replace('actionbarNotifyLevel = true', 'actionbarNotifyLevel = false')
                         .replace('actionbarNotifyMasterLevel=true', 'actionbarNotifyMasterLevel = false'))

    def test_missing_key_fails_and_leaves_the_file_untouched(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            config = Path(tmp) / 'adapt.toml'
            config.write_text('actionbarNotifyXp = true\n')
            with self.assertRaisesRegex(RuntimeError, 'actionbarNotifyLevel missing from'):
                studio.switch_off(config, studio.NOTIFY_KEYS)
            text = config.read_text()
        self.assertEqual(text, 'actionbarNotifyXp = true\n')

    def test_only_the_top_level_key_is_switched_off(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            config = Path(tmp) / 'discovery.toml'
            config.write_text(DISCOVERY_TOML)
            studio.switch_off(config, (studio.SKILL_SWITCH,))
            text = config.read_text()
        self.assertEqual(text, DISCOVERY_TOML.replace('this feature.\nenabled = true', 'this feature.\nenabled = false'))
        self.assertTrue(text.endswith('[playerPreferences.enabled]\nenabled = true\ndefaultValue = "ON"\n'))

    def test_key_found_only_inside_a_table_fails_and_leaves_the_file_untouched(self) -> None:
        nested: str = 'skillColor = "&b"\n\n[playerPreferences.enabled]\nenabled = true\n'
        with tempfile.TemporaryDirectory() as tmp:
            config = Path(tmp) / 'discovery.toml'
            config.write_text(nested)
            with self.assertRaisesRegex(RuntimeError, 'enabled missing from'):
                studio.switch_off(config, (studio.SKILL_SWITCH,))
            text = config.read_text()
        self.assertEqual(text, nested)


class IrisPackTest(unittest.TestCase):
    def write_pack(self, pack: Path) -> None:
        for relative in ('dimensions/overworld.json', 'biomes/temperate/plains.json', '.git/HEAD', '.iris/cache.bin', 'regions/.git'):
            file: Path = pack / relative
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_text('{}')

    def test_environment_overrides_the_pack_source(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            pack = Path(tmp) / 'overworld'
            self.write_pack(pack)
            with mock.patch.dict(studio.os.environ, {'ADAPT_DEMO_IRIS_PACK': str(pack)}):
                self.assertEqual(studio.iris_pack(), pack)

    def test_missing_pack_names_the_environment_variable(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            with mock.patch.dict(studio.os.environ, {'ADAPT_DEMO_IRIS_PACK': tmp}):
                with self.assertRaisesRegex(RuntimeError, 'ADAPT_DEMO_IRIS_PACK'):
                    studio.iris_pack()

    def test_install_copies_the_pack_without_git_or_iris_folders(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / 'source'
            self.write_pack(source)
            target = studio.install_pack(source, Path(tmp) / 'server' / 'plugins' / 'Iris' / 'packs')
            copied = sorted(str(path.relative_to(target)) for path in target.rglob('*') if path.is_file())
        self.assertEqual(target.name, 'overworld')
        self.assertEqual(copied, ['biomes/temperate/plains.json', 'dimensions/overworld.json'])


class StudioStopTest(unittest.TestCase):
    def setUp(self) -> None:
        for name in ('sys.stdout', 'sys.stderr'):
            patcher = mock.patch(name, new_callable=io.StringIO)
            setattr(self, name.split('.')[1], patcher.start())
            self.addCleanup(patcher.stop)
        locks = tempfile.TemporaryDirectory()
        self.addCleanup(locks.cleanup)
        patcher = mock.patch.object(studio, 'LOCKS', Path(locks.name))
        patcher.start()
        self.addCleanup(patcher.stop)

    def make_studio(self, root: Path) -> studio.Studio:
        demo = studio.Studio(root / 'out', skip_build=True)
        demo.output.mkdir()
        demo.instance = root / 'AdaptDemoStudio'
        demo.game = demo.instance / '.minecraft'
        (demo.game / 'logs').mkdir(parents=True)
        (demo.game / 'logs' / 'latest.log').write_text('[Render thread/INFO]: Stopping!\n')
        (demo.instance / 'instance.cfg').write_text('[General]\nJvmArgs=-Dadapt.qa.port=1 -Dadapt.qa.token=' + demo.token + ' -Dadapt.qa.output="/x"\n')
        demo.launched_client = True
        return demo

    def stop(self, demo: studio.Studio, shutdown: list[str]) -> list[str]:
        with mock.patch.object(studio.clientqa, 'shutdown_client', return_value=shutdown):
            return demo.stop()

    def test_stop_keeps_the_instance_and_collects_the_client_log(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            demo = self.make_studio(Path(tmp))
            before = (demo.instance / 'instance.cfg').read_text()
            errors = self.stop(demo, [])
            kept = demo.instance.is_dir()
            after = (demo.instance / 'instance.cfg').read_text()
            log = (demo.output / 'logs' / (demo.suffix + '-client.log')).read_text()
        self.assertEqual(errors, [])
        self.assertTrue(kept)
        self.assertEqual(after, before)
        self.assertIn('Stopping!', log)

    def test_shutdown_errors_are_returned_and_the_instance_is_kept(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            demo = self.make_studio(Path(tmp))
            errors = self.stop(demo, ['Owned Minecraft client process remains live'])
            kept = demo.instance.is_dir()
        self.assertTrue(kept)
        self.assertEqual(errors, ['Owned Minecraft client process remains live'])

    def test_unlaunched_client_is_left_alone(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            demo = self.make_studio(Path(tmp))
            demo.launched_client = False
            with mock.patch.object(studio.clientqa, 'shutdown_client') as shutdown:
                errors = demo.stop()
            copied = (demo.output / 'logs').exists()
        self.assertEqual(errors, [])
        shutdown.assert_not_called()
        self.assertFalse(copied)

    def test_every_run_keeps_its_own_server_and_client_logs(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root: Path = Path(tmp)
            suffixes: list[str] = []
            first: studio.Studio = self.make_studio(root)
            for run in ('first', 'second'):
                demo: studio.Studio = first if run == 'first' else studio.Studio(root / 'out', skip_build=True)
                demo.instance = first.instance
                demo.game = first.game
                demo.launched_client = True
                demo.server = root / ('server-' + run)
                (demo.server / 'logs').mkdir(parents=True)
                (demo.server / 'logs' / 'latest.log').write_text('server ' + run + '\n')
                (demo.game / 'logs' / 'latest.log').write_text('client ' + run + '\n')
                demo.created_server = True
                with mock.patch.object(studio.clientqa, 'mux', return_value='') as mux:
                    self.assertEqual(self.stop(demo, []), [])
                self.assertEqual([call.args[:2] for call in mux.call_args_list], [('runtime', 'stop'), ('instance', 'delete')])
                suffixes.append(demo.suffix)
            logs: Path = root / 'out' / 'logs'
            names: list[str] = sorted(path.name for path in logs.iterdir())
            contents: list[str] = [(logs / (suffix + '-' + kind + '.log')).read_text() for suffix in suffixes for kind in ('server', 'client')]
            overwritten: bool = (root / 'out' / 'server.log').exists() or (root / 'out' / 'client.log').exists()
        self.assertNotEqual(suffixes[0], suffixes[1])
        self.assertEqual(names, sorted(suffix + '-' + kind + '.log' for suffix in suffixes for kind in ('server', 'client')))
        self.assertEqual(contents, ['server first\n', 'client first\n', 'server second\n', 'client second\n'])
        self.assertFalse(overwritten)

    def test_run_suffix_names_the_server_and_the_logs(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            demo: studio.Studio = self.make_studio(Path(tmp))
        self.assertEqual(demo.server_name, 'adapt-demo-' + demo.suffix)
        self.assertEqual(demo.log_path('server'), demo.output / 'logs' / (demo.suffix + '-server.log'))

    def test_cleanup_errors_are_returned_not_printed(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            demo = self.make_studio(Path(tmp))
            demo.created_server = True
            with mock.patch.object(studio.clientqa, 'mux', side_effect=RuntimeError('Multiplexor runtime stop failed')):
                errors = self.stop(demo, [])
        self.assertEqual(errors, ["Server cleanup: RuntimeError('Multiplexor runtime stop failed')"])
        self.assertEqual(self.stderr.getvalue(), '')

    def test_running_studio_client_blocks_a_second_launch(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            demo = self.make_studio(Path(tmp))
            demo.launched_client = False
            with mock.patch.object(studio.clientqa, 'client_processes', return_value=[4242]), \
                    mock.patch.object(studio.clientqa, 'prepare_instance') as prepare:
                with self.assertRaisesRegex(RuntimeError, 'AdaptDemoStudio is already running'):
                    demo.launch_client([])
        prepare.assert_not_called()
        self.assertFalse(demo.launched_client)


class StudioWarmUpTest(unittest.TestCase):
    def test_warm_up_places_the_actor_on_the_plate_and_waits_client_ticks(self) -> None:
        demo = studio.Studio(Path('/tmp/unused'), skip_build=True)
        demo.rcon = mock.MagicMock()
        demo.bridge = mock.MagicMock()
        ticks: list[int] = [1000, 1100, 1399, 1400]
        demo.bridge.state.side_effect = lambda: {'ticks': ticks.pop(0)}
        with mock.patch.object(studio.time, 'sleep'), mock.patch('sys.stdout', new_callable=io.StringIO):
            demo.warm_up()
        self.assertEqual([call.args[0] for call in demo.rcon.command.call_args_list], ['adaptqa demo set arena', 'adaptqa demo actor AQAClient262'])
        self.assertEqual(ticks, [])


    def test_warm_up_places_a_running_opponent_at_its_start(self) -> None:
        demo = studio.Studio(Path('/tmp/unused'), skip_build=True)
        demo.rcon = mock.MagicMock()
        demo.bridge = mock.MagicMock()
        demo.opponent_bridge = mock.MagicMock()
        demo.bridge.state.return_value = {'ticks': 1000}
        with mock.patch.object(studio.time, 'sleep'), mock.patch.object(studio, 'WARM_UP_TICKS', 0), mock.patch('sys.stdout', new_callable=io.StringIO):
            demo.warm_up()
        self.assertEqual([call.args[0] for call in demo.rcon.command.call_args_list],
                         ['adaptqa demo set arena', 'adaptqa demo actor AQAClient262', 'adaptqa demo actor AQAOpponent opponent'])


class StudioStartTest(unittest.TestCase):
    MODS: list[Path] = [Path('/mods/fabric-api.jar')]

    def start(self, opponent: bool, world: str | None = None) -> list[object]:
        output = tempfile.TemporaryDirectory()
        self.addCleanup(output.cleanup)
        demo = studio.Studio(Path(output.name), skip_build=True)
        steps: mock.MagicMock = mock.MagicMock()
        steps.prepare_jars.return_value = self.MODS
        for name in ('prepare_jars', 'start_server', 'prepare_world', 'launch_client', 'launch_opponent', 'warm_up', 'await_skin'):
            setattr(demo, name, getattr(steps, name))
        if world is None:
            demo.start('agility', opponent=opponent)
        else:
            demo.start('agility', opponent=opponent, world=world)
        return steps.mock_calls

    def test_opponent_client_launches_only_when_asked(self) -> None:
        self.assertEqual(self.start(False), [mock.call.prepare_jars(), mock.call.start_server('agility'), mock.call.prepare_world(OVERWORLD),
                                             mock.call.launch_client(self.MODS), mock.call.warm_up(), mock.call.await_skin()])

    def test_opponent_client_joins_after_the_actor_and_before_the_warm_up(self) -> None:
        self.assertEqual(self.start(True), [mock.call.prepare_jars(), mock.call.start_server('agility'), mock.call.prepare_world(OVERWORLD),
                                            mock.call.launch_client(self.MODS), mock.call.launch_opponent(self.MODS), mock.call.warm_up(),
                                            mock.call.await_skin()])

    def test_world_of_the_sheet_is_plated_and_the_opponent_joins_it_the_same_way(self) -> None:
        self.assertEqual(self.start(True, NETHER), [mock.call.prepare_jars(), mock.call.start_server('agility'), mock.call.prepare_world(NETHER),
                                                    mock.call.launch_client(self.MODS), mock.call.launch_opponent(self.MODS), mock.call.warm_up(),
                                                    mock.call.await_skin()])


class StudioOpponentStopTest(unittest.TestCase):
    def setUp(self) -> None:
        for name in ('sys.stdout', 'sys.stderr'):
            patcher = mock.patch(name, new_callable=io.StringIO)
            patcher.start()
            self.addCleanup(patcher.stop)
        locks = tempfile.TemporaryDirectory()
        self.addCleanup(locks.cleanup)
        patcher = mock.patch.object(studio, 'LOCKS', Path(locks.name))
        patcher.start()
        self.addCleanup(patcher.stop)

    def make_studio(self, root: Path) -> studio.Studio:
        demo = studio.Studio(root / 'out', skip_build=True)
        demo.output.mkdir()
        demo.instance = root / 'AdaptDemoStudio'
        demo.game = demo.instance / '.minecraft'
        demo.opponent_instance = root / 'AdaptDemoOpponent'
        for game, line in ((demo.game, 'actor client\n'), (demo.opponent_instance / '.minecraft', 'opponent client\n')):
            (game / 'logs').mkdir(parents=True)
            (game / 'logs' / 'latest.log').write_text(line)
        demo.bridge_port = 51001
        demo.opponent_port = 51002
        demo.opponent_bridge = mock.MagicMock()
        demo.launched_client = True
        demo.launched_opponent = True
        return demo

    def test_stop_quits_both_clients_and_keeps_both_logs(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            demo = self.make_studio(Path(tmp))
            with mock.patch.object(studio.clientqa, 'shutdown_client', return_value=[]) as shutdown:
                errors = demo.stop()
            logs: Path = demo.output / 'logs'
            actor_log: str = (logs / (demo.suffix + '-client.log')).read_text()
            opponent_log: str = (logs / (demo.suffix + '-opponent.log')).read_text()
            kept: bool = demo.opponent_instance.is_dir()
        self.assertEqual(errors, [])
        self.assertEqual([(call.args[0].url, call.args[0].token, call.args[1]) for call in shutdown.call_args_list],
                         [('http://127.0.0.1:51001', demo.token, demo.instance), ('http://127.0.0.1:51002', demo.opponent_token, demo.opponent_instance)])
        self.assertEqual((actor_log, opponent_log), ('actor client\n', 'opponent client\n'))
        self.assertTrue(kept)
        self.assertIsNone(demo.opponent_bridge)
        self.assertFalse(demo.launched_opponent)

    def test_opponent_shutdown_errors_name_the_opponent(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            demo = self.make_studio(Path(tmp))
            outcomes: list[object] = [[], ['Owned Minecraft client process remains live']]
            with mock.patch.object(studio.clientqa, 'shutdown_client', side_effect=lambda bridge, folder: outcomes.pop(0)):
                errors = demo.stop()
            with mock.patch.object(studio.clientqa, 'shutdown_client', side_effect=OSError('refused')):
                demo.launched_opponent = True
                failed = demo.stop()
        self.assertEqual(errors, ['Opponent: Owned Minecraft client process remains live'])
        self.assertEqual(failed, ["Opponent: Client shutdown: OSError('refused')"])

    def test_unlaunched_opponent_is_left_alone(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            demo = self.make_studio(Path(tmp))
            demo.launched_opponent = False
            with mock.patch.object(studio.clientqa, 'shutdown_client', return_value=[]) as shutdown:
                demo.stop()
            copied: bool = (demo.output / 'logs' / (demo.suffix + '-opponent.log')).exists()
        self.assertEqual([call.args[1] for call in shutdown.call_args_list], [demo.instance])
        self.assertFalse(copied)

    def test_both_launcher_logs_lose_both_tokens(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            demo = self.make_studio(Path(tmp))
            for name in ('launcher.log', 'opponent-launcher.log'):
                (demo.output / name).write_text('args ' + demo.token + ' ' + demo.opponent_token + '\n')
            with mock.patch.object(studio.clientqa, 'shutdown_client', return_value=[]):
                demo.stop()
            texts: list[str] = [(demo.output / name).read_text() for name in ('launcher.log', 'opponent-launcher.log')]
        self.assertEqual(texts, ['args [REDACTED] [REDACTED]\n', 'args [REDACTED] [REDACTED]\n'])


class StudioWorldTest(unittest.TestCase):
    def setUp(self) -> None:
        sleep = mock.patch.object(studio.time, 'sleep')
        sleep.start()
        self.addCleanup(sleep.stop)
        stdout = mock.patch('sys.stdout', new_callable=io.StringIO)
        self.stdout: io.StringIO = stdout.start()
        self.addCleanup(stdout.stop)
        folder = tempfile.TemporaryDirectory()
        self.addCleanup(folder.cleanup)
        self.root: Path = Path(folder.name)
        self.pack: Path = self.root / 'overworld'
        (self.pack / 'dimensions').mkdir(parents=True)
        (self.pack / 'dimensions' / 'overworld.json').write_text('{"name": "Overworld", "version": 4013}')
        self.cache: Path = self.root / 'locks' / 'site-cache.json'
        for patcher in (mock.patch.object(studio, 'LOCKS', self.root / 'locks'), mock.patch.dict(studio.os.environ, {'ADAPT_DEMO_IRIS_PACK': str(self.pack)})):
            patcher.start()
            self.addCleanup(patcher.stop)

    def studio_with(self, replies: list[object]) -> studio.Studio:
        demo = studio.Studio(Path('/tmp/unused'), skip_build=True)
        demo.rcon = mock.MagicMock()
        demo.rcon.command.side_effect = replies
        return demo

    def write_cache(self, sites: dict) -> None:
        self.cache.parent.mkdir(parents=True, exist_ok=True)
        self.cache.write_text(json.dumps(sites))

    def test_iris_denial_fails_without_waiting_for_world(self) -> None:
        demo = self.studio_with(['Iris installed or updated a dimension pack that requires a server RESTART before world creation or Studio open.'])
        with self.assertRaisesRegex(RuntimeError, 'Iris refused world creation'):
            demo.prepare_world(OVERWORLD)
        demo.rcon.command.assert_called_once()

    def test_accepted_create_waits_for_world_then_locates_and_plates(self) -> None:
        demo = self.studio_with(['', 'ADAPT_QA DEMO WORLD PENDING iris:adapt_demo', 'ADAPT_QA DEMO WORLD iris:adapt_demo',
                                 'ADAPT_QA DEMO LOCATE PENDING 12/441', 'ADAPT_QA DEMO LOCATE 128 -64 overworld:plains', 'ADAPT_QA DEMO PLATE 128 71 -64'])
        demo.prepare_world(OVERWORLD)
        commands = [call.args[0] for call in demo.rcon.command.call_args_list]
        self.assertEqual(commands[0], 'iris create name=adapt_demo type=overworld seed=78264193')
        self.assertEqual(commands[1], 'adaptqa demo world iris:adapt_demo')
        self.assertEqual(commands[3:], ['adaptqa demo locate 6144 256', 'adaptqa demo locate 6144 256', 'adaptqa demo plate 128 -64'])
        self.assertEqual(demo.origin, (128.0, 71.0, -64.0))
        self.assertEqual(demo.biome, 'overworld:plains')
        self.assertEqual(demo.plate_label(), '128 71 -64 in overworld:plains')

    def test_located_site_is_written_to_the_cache_under_seed_and_pack_version(self) -> None:
        demo = self.studio_with(['', 'ADAPT_QA DEMO WORLD iris:adapt_demo', 'ADAPT_QA DEMO LOCATE -1280 5120 temperate/plains', 'ADAPT_QA DEMO PLATE -1280 56 5120'])
        demo.prepare_world(OVERWORLD)
        self.assertEqual(json.loads(self.cache.read_text()), {'iris:adapt_demo:78264193:4013': {'x': -1280, 'z': 5120, 'biome': 'temperate/plains'}})
        self.assertEqual(sorted(path.name for path in self.cache.parent.iterdir()), ['mux.lock', 'site-cache.json'])

    def test_cached_site_is_plated_without_locating(self) -> None:
        self.write_cache({'iris:adapt_demo:78264193:4013': {'x': -1280, 'z': 5120, 'biome': 'temperate/plains'}})
        demo = self.studio_with(['', 'ADAPT_QA DEMO WORLD iris:adapt_demo', 'ADAPT_QA DEMO PLATE -1280 56 5120'])
        demo.prepare_world(OVERWORLD)
        commands = [call.args[0] for call in demo.rcon.command.call_args_list]
        self.assertEqual(commands, ['iris create name=adapt_demo type=overworld seed=78264193', 'adaptqa demo world iris:adapt_demo', 'adaptqa demo plate -1280 5120'])
        self.assertEqual(demo.plate_label(), '-1280 56 5120 in temperate/plains')
        self.assertIn('[LOCATE] -1280 5120 temperate/plains from ' + str(self.cache), self.stdout.getvalue())

    def test_cache_of_another_pack_version_is_ignored_and_kept(self) -> None:
        self.write_cache({'iris:adapt_demo:78264193:4012': {'x': 0, 'z': 0, 'biome': 'old/plains'}})
        demo = self.studio_with(['', 'ADAPT_QA DEMO WORLD iris:adapt_demo', 'ADAPT_QA DEMO LOCATE 256 512 temperate/meadow', 'ADAPT_QA DEMO PLATE 256 70 512'])
        demo.prepare_world(OVERWORLD)
        self.assertIn('adaptqa demo locate 6144 256', [call.args[0] for call in demo.rcon.command.call_args_list])
        self.assertEqual(json.loads(self.cache.read_text()), {'iris:adapt_demo:78264193:4012': {'x': 0, 'z': 0, 'biome': 'old/plains'},
                                                              'iris:adapt_demo:78264193:4013': {'x': 256, 'z': 512, 'biome': 'temperate/meadow'}})

    def test_pack_without_version_locates_and_leaves_the_cache_alone(self) -> None:
        (self.pack / 'dimensions' / 'overworld.json').write_text('{"name": "Overworld"}')
        demo = self.studio_with(['', 'ADAPT_QA DEMO WORLD iris:adapt_demo', 'ADAPT_QA DEMO LOCATE 256 512 temperate/meadow', 'ADAPT_QA DEMO PLATE 256 70 512'])
        demo.prepare_world(OVERWORLD)
        self.assertIn('adaptqa demo locate 6144 256', [call.args[0] for call in demo.rcon.command.call_args_list])
        self.assertFalse(self.cache.exists())

    def test_nether_plates_the_vanilla_dimension_without_creating_an_iris_world(self) -> None:
        demo = self.studio_with(['ADAPT_QA DEMO WORLD minecraft:the_nether', 'ADAPT_QA DEMO LOCATE PENDING 3/805',
                                 'ADAPT_QA DEMO LOCATE 64 -128 minecraft:nether_wastes', 'ADAPT_QA DEMO PLATE 64 41 -128'])
        demo.prepare_world(NETHER)
        self.assertEqual([call.args[0] for call in demo.rcon.command.call_args_list],
                         ['adaptqa demo world minecraft:the_nether', 'adaptqa demo locate 1536 64', 'adaptqa demo locate 1536 64',
                          'adaptqa demo plate 64 -128'])
        self.assertEqual(demo.origin, (64.0, 41.0, -128.0))
        self.assertEqual(json.loads(self.cache.read_text()), {'minecraft:the_nether:78264193:26.2': {'x': 64, 'z': -128, 'biome': 'minecraft:nether_wastes'}})
        self.assertIn('[READY] World minecraft:the_nether plated at 64 41 -128 in minecraft:nether_wastes', self.stdout.getvalue())

    def test_overworld_site_is_never_used_for_the_nether(self) -> None:
        self.write_cache({'iris:adapt_demo:78264193:4013': {'x': -1280, 'z': 5120, 'biome': 'temperate/plains'}})
        demo = self.studio_with(['ADAPT_QA DEMO WORLD minecraft:the_nether', 'ADAPT_QA DEMO LOCATE 64 -128 minecraft:nether_wastes',
                                 'ADAPT_QA DEMO PLATE 64 41 -128'])
        demo.prepare_world(NETHER)
        self.assertIn('adaptqa demo locate 1536 64', [call.args[0] for call in demo.rcon.command.call_args_list])
        self.assertEqual(json.loads(self.cache.read_text()), {'iris:adapt_demo:78264193:4013': {'x': -1280, 'z': 5120, 'biome': 'temperate/plains'},
                                                              'minecraft:the_nether:78264193:26.2': {'x': 64, 'z': -128, 'biome': 'minecraft:nether_wastes'}})

    def test_cached_nether_site_is_plated_without_locating(self) -> None:
        self.write_cache({'minecraft:the_nether:78264193:26.2': {'x': 64, 'z': -128, 'biome': 'minecraft:nether_wastes'},
                          'iris:adapt_demo:78264193:4013': {'x': -1280, 'z': 5120, 'biome': 'temperate/plains'}})
        demo = self.studio_with(['ADAPT_QA DEMO WORLD minecraft:the_nether', 'ADAPT_QA DEMO PLATE 64 41 -128'])
        demo.prepare_world(NETHER)
        self.assertEqual([call.args[0] for call in demo.rcon.command.call_args_list], ['adaptqa demo world minecraft:the_nether', 'adaptqa demo plate 64 -128'])
        self.assertIn('[LOCATE] 64 -128 minecraft:nether_wastes from ' + str(self.cache), self.stdout.getvalue())

    def test_nether_site_is_cached_under_the_game_version_without_an_iris_pack_version(self) -> None:
        (self.pack / 'dimensions' / 'overworld.json').write_text('{"name": "Overworld"}')
        demo = self.studio_with(['ADAPT_QA DEMO WORLD minecraft:the_nether', 'ADAPT_QA DEMO LOCATE 64 -128 minecraft:nether_wastes',
                                 'ADAPT_QA DEMO PLATE 64 41 -128'])
        demo.prepare_world(NETHER)
        self.assertEqual(list(json.loads(self.cache.read_text())), ['minecraft:the_nether:78264193:26.2'])

    def test_failed_locate_does_not_plate(self) -> None:
        demo = self.studio_with(['', 'ADAPT_QA DEMO WORLD iris:adapt_demo', RuntimeError('adaptqa demo locate 6144 256: no grassland origin within 6144 blocks')])
        with self.assertRaisesRegex(RuntimeError, 'no grassland origin'):
            demo.prepare_world(OVERWORLD)
        self.assertEqual(demo.rcon.command.call_count, 3)
        self.assertEqual(demo.plate_label(), '')
        self.assertFalse(self.cache.exists())


class DemoSkinProfileTest(unittest.TestCase):
    def test_signed_profile_is_deployed_to_the_fixture_without_changes(self) -> None:
        with tempfile.TemporaryDirectory() as folder:
            root: Path = Path(folder)
            profile: Path = root / 'profile.json'
            payload: str = json.dumps({'properties': [{'name': 'textures', 'value': 'texture', 'signature': 'signed'}]})
            profile.write_text(payload)
            with mock.patch.dict(studio.os.environ, {'ADAPT_DEMO_SKIN_PROFILE': str(profile)}):
                studio.install_skin_profile(root / 'plugins')
            self.assertEqual((root / 'plugins/AdaptGameplayFixture/skin-profile.json').read_text(), payload)

    def test_no_profile_keeps_normal_login_profiles(self) -> None:
        with tempfile.TemporaryDirectory() as folder:
            with mock.patch.dict(studio.os.environ, {'ADAPT_DEMO_SKIN_PROFILE': ''}):
                studio.install_skin_profile(Path(folder) / 'plugins')
            self.assertFalse((Path(folder) / 'plugins').exists())

    def test_missing_or_unsigned_textures_fail_before_recording(self) -> None:
        for properties in ([], [{'name': 'textures', 'value': 'texture'}], [{'name': 'textures', 'value': '', 'signature': 'signed'}]):
            with self.subTest(properties=properties), tempfile.TemporaryDirectory() as folder:
                root: Path = Path(folder)
                profile: Path = root / 'profile.json'
                profile.write_text(json.dumps({'properties': properties}))
                with mock.patch.dict(studio.os.environ, {'ADAPT_DEMO_SKIN_PROFILE': str(profile)}):
                    with self.assertRaisesRegex(RuntimeError, 'signed textures'):
                        studio.install_skin_profile(root / 'plugins')
                self.assertFalse((root / 'plugins').exists())


class SeedServerTest(unittest.TestCase):
    def setUp(self) -> None:
        folder = tempfile.TemporaryDirectory()
        self.addCleanup(folder.cleanup)
        self.properties: Path = Path(folder.name) / 'server.properties'

    def test_empty_seed_is_replaced_and_the_other_properties_are_kept(self) -> None:
        self.properties.write_text('#Minecraft server properties\nlevel-seed=\nlevel-type=minecraft\\:flat\nserver-port=52000\n')
        studio.seed_server(self.properties, 78264193)
        self.assertEqual(self.properties.read_text(), '#Minecraft server properties\nlevel-type=minecraft\\:flat\nserver-port=52000\nlevel-seed=78264193\n')

    def test_missing_seed_is_added(self) -> None:
        self.properties.write_text('server-port=52000\n')
        studio.seed_server(self.properties, 78264193)
        self.assertEqual(self.properties.read_text(), 'server-port=52000\nlevel-seed=78264193\n')

    def test_server_is_seeded_before_its_first_boot(self) -> None:
        server: Path = self.properties.parent / 'server'
        (server / 'plugins' / 'Adapt' / 'skills').mkdir(parents=True)
        (server / 'plugins' / 'Adapt' / 'adapt.toml').write_text('actionbarNotifyXp = true\nactionbarNotifyLevel = true\nactionbarNotifyMasterLevel = true\n')
        (server / 'plugins' / 'Adapt' / 'skills' / 'discovery.toml').write_text('enabled = true\n')
        (server / 'server.properties').write_text('level-seed=\nserver-port=52000\n')
        booted: list[str] = []

        def mux(*arguments: str, timeout: int = 180) -> str:
            if arguments[:2] == ('runtime', 'start'):
                booted.append((server / 'server.properties').read_text())
            return str(server) if arguments[:2] == ('instance', 'path') else ''

        demo = studio.Studio(self.properties.parent / 'out', skip_build=True)
        with mock.patch.object(studio, 'LOCKS', self.properties.parent / 'locks'), mock.patch.object(studio.clientqa, 'mux', side_effect=mux), \
                mock.patch.object(studio.clientqa, 'free_port', return_value=52001), mock.patch.object(studio.clientqa, 'configure_server'), \
                mock.patch.object(studio.clientqa, 'wait_server'), mock.patch.object(studio.shutil, 'copy2'), mock.patch.object(studio, 'iris_jar'), \
                mock.patch.object(studio, 'iris_pack'), mock.patch.object(studio, 'install_pack'), mock.patch.object(studio, 'DemoRcon'), \
                mock.patch('sys.stdout', new_callable=io.StringIO):
            demo.start_server('agility')
        self.assertEqual(booted, ['server-port=52000\nlevel-seed=78264193\n'] * 2)


class SiteCacheTest(unittest.TestCase):
    def setUp(self) -> None:
        folder = tempfile.TemporaryDirectory()
        self.addCleanup(folder.cleanup)
        self.root: Path = Path(folder.name)
        self.cache: Path = self.root / 'site-cache.json'
        patcher = mock.patch.object(studio, 'LOCKS', self.root)
        patcher.start()
        self.addCleanup(patcher.stop)

    def write_pack(self, dimension: str) -> Path:
        pack: Path = self.root / 'overworld'
        (pack / 'dimensions').mkdir(parents=True, exist_ok=True)
        (pack / 'dimensions' / 'overworld.json').write_text(dimension)
        return pack

    def test_pack_version_is_the_dimension_version_field(self) -> None:
        self.assertEqual(studio.pack_version(self.write_pack('{"name": "Overworld", "version": 4013}')), '4013')

    def test_text_pack_version_is_kept_verbatim(self) -> None:
        self.assertEqual(studio.pack_version(self.write_pack('{"version": "4013-dev"}')), '4013-dev')

    def test_pack_without_version_has_none(self) -> None:
        self.assertIsNone(studio.pack_version(self.write_pack('{"name": "Overworld"}')))
        self.assertIsNone(studio.pack_version(self.write_pack('[]')))

    def test_site_key_joins_seed_and_pack_version(self) -> None:
        self.assertEqual(studio.site_key(OVERWORLD, 78264193, '4013'), 'iris:adapt_demo:78264193:4013')
        self.assertEqual(studio.site_key(NETHER, 78264193, '26.2'), 'minecraft:the_nether:78264193:26.2')

    def test_cached_site_reads_the_entry_of_its_key(self) -> None:
        self.cache.write_text(json.dumps({'78264193:4013': {'x': -1280, 'z': 5120, 'biome': 'temperate/plains'}, '1:1': {'x': 1, 'z': 1, 'biome': 'b'}}))
        self.assertEqual(studio.cached_site(self.cache, '78264193:4013'), (-1280, 5120, 'temperate/plains'))

    def test_missing_or_malformed_cache_is_a_miss(self) -> None:
        self.assertIsNone(studio.cached_site(self.cache, '78264193:4013'))
        for content in ('not json', '[]', '{"78264193:4012": {"x": 1, "z": 2, "biome": "b"}}', '{"78264193:4013": [1, 2]}',
                        '{"78264193:4013": {"x": 1, "z": 2}}', '{"78264193:4013": {"x": "1", "z": 2, "biome": "b"}}',
                        '{"78264193:4013": {"x": true, "z": 2, "biome": "b"}}', '{"78264193:4013": {"x": 1, "z": 2, "biome": ""}}'):
            self.cache.write_text(content)
            self.assertIsNone(studio.cached_site(self.cache, '78264193:4013'), content)

    def test_store_site_adds_its_key_and_keeps_the_others(self) -> None:
        self.cache.write_text(json.dumps({'1:1': {'x': 1, 'z': 1, 'biome': 'b'}}))
        studio.store_site(self.cache, '78264193:4013', (-1280, 5120, 'temperate/plains'))
        self.assertEqual(json.loads(self.cache.read_text()), {'1:1': {'x': 1, 'z': 1, 'biome': 'b'},
                                                              '78264193:4013': {'x': -1280, 'z': 5120, 'biome': 'temperate/plains'}})
        self.assertFalse((self.root / 'site-cache.json.tmp').exists())

    def test_store_site_replaces_an_unreadable_cache(self) -> None:
        self.cache.write_text('{broken')
        studio.store_site(self.cache, '78264193:4013', (8, 16, 'savanna'))
        self.assertEqual(json.loads(self.cache.read_text()), {'78264193:4013': {'x': 8, 'z': 16, 'biome': 'savanna'}})

    def test_store_site_holds_the_studio_lock_while_writing(self) -> None:
        held: list[bool] = []
        real_replace = Path.replace

        @contextlib.contextmanager
        def lock() -> Iterator[None]:
            held.append(True)
            try:
                yield
            finally:
                held.append(False)

        def replace(source: Path, target: Path) -> Path:
            self.assertEqual(held, [True])
            return real_replace(source, target)

        with mock.patch.object(studio, 'mux_lock', lock), mock.patch.object(studio.Path, 'replace', replace):
            studio.store_site(self.cache, '78264193:4013', (8, 16, 'savanna'))
        self.assertEqual(held, [True, False])


class FakeRcon:
    def __init__(self, replies: list[str]) -> None:
        self.replies: list[str] = replies
        self.commands: list[str] = []

    def command(self, text: str) -> str:
        self.commands.append(text)
        return self.replies.pop(0) if len(self.replies) > 1 else self.replies[0]


def tps_reply(one: str, five: str = '19.8', fifteen: str = '19.9') -> str:
    return 'TPS from last 1m, 5m, 15m: ' + one + ', ' + five + ', ' + fifteen


class TpsParseTest(unittest.TestCase):
    def test_plain_paper_reply_gives_the_one_minute_value(self) -> None:
        self.assertEqual(studio.parse_tps(tps_reply('16.8', '18.2', '19.5')), 16.8)

    def test_legacy_color_codes_are_ignored(self) -> None:
        self.assertEqual(studio.parse_tps('\u00a76TPS from last 1m, 5m, 15m: \u00a7e16.9, \u00a7a19.1, \u00a7a19.8'), 16.9)
        self.assertEqual(studio.parse_tps('\u00a7x\u00a7f\u00a7f\u00a7a\u00a70\u00a70\u00a70TPS from last 1m, 5m, 15m: \u00a7c9.5, \u00a7a19.1, \u00a7a19.8'), 9.5)

    def test_ansi_color_codes_are_ignored(self) -> None:
        self.assertEqual(studio.parse_tps('\x1b[0;33mTPS from last 1m, 5m, 15m: \x1b[0;32;1m19.4\x1b[m, \x1b[0;32;1m19.9\x1b[m, 20.0'), 19.4)

    def test_capped_and_integer_values_are_read(self) -> None:
        self.assertEqual(studio.parse_tps(tps_reply('*20.0', '*20.0', '*20.0')), 20.0)
        self.assertEqual(studio.parse_tps(tps_reply('20', '20', '20')), 20.0)

    def test_comma_decimal_separator_is_read(self) -> None:
        self.assertEqual(studio.parse_tps(tps_reply('17,3', '19,0', '20,0')), 17.3)

    def test_one_minute_column_is_found_when_a_shorter_window_leads(self) -> None:
        self.assertEqual(studio.parse_tps('TPS from last 5s, 1m, 5m, 15m: 20.0, 17.2, 19.0, 19.6'), 17.2)

    def test_leading_output_is_skipped(self) -> None:
        self.assertEqual(studio.parse_tps('[Iris] tick\n' + tps_reply('18.5')), 18.5)

    def test_reply_without_a_tps_line_is_none(self) -> None:
        self.assertIsNone(studio.parse_tps('Unknown command. Type "/help" for help.'))
        self.assertIsNone(studio.parse_tps('TPS from last 1m, 5m, 15m:'))


class AwaitTpsTest(unittest.TestCase):
    def setUp(self) -> None:
        patcher = mock.patch.object(studio.time, 'sleep')
        self.sleep = patcher.start()
        self.addCleanup(patcher.stop)

    def test_polls_until_the_one_minute_value_reaches_the_minimum(self) -> None:
        rcon = FakeRcon([tps_reply('16.6'), tps_reply('16.9'), tps_reply('18.99'), tps_reply('19.0')])
        self.assertEqual(studio.await_tps(rcon, 19.0, 180.0), 19.0)
        self.assertEqual(rcon.commands, ['tps'] * 4)
        self.assertEqual(self.sleep.call_count, 3)

    def test_healthy_server_is_read_once(self) -> None:
        rcon = FakeRcon([tps_reply('20.0')])
        self.assertEqual(studio.await_tps(rcon, 19.0, 180.0), 20.0)
        self.assertEqual(rcon.commands, ['tps'])
        self.sleep.assert_not_called()

    def test_timeout_returns_the_last_low_value(self) -> None:
        rcon = FakeRcon([tps_reply('16.7')])
        clock: list[float] = [0.0, 0.0, 90.0, 179.0, 180.0]
        with mock.patch.object(studio.time, 'monotonic', side_effect=clock):
            self.assertEqual(studio.await_tps(rcon, 19.0, 180.0), 16.7)
        self.assertEqual(rcon.commands, ['tps'] * 4)

    def test_unreadable_reply_ends_the_wait_at_once(self) -> None:
        rcon = FakeRcon(['Unknown command. Type "/help" for help.'])
        self.assertIsNone(studio.await_tps(rcon, 19.0, 180.0))
        self.assertEqual(rcon.commands, ['tps'])


class StudioTpsGateTest(unittest.TestCase):
    def setUp(self) -> None:
        sleep = mock.patch.object(studio.time, 'sleep')
        sleep.start()
        self.addCleanup(sleep.stop)
        stdout = mock.patch('sys.stdout', new_callable=io.StringIO)
        self.stdout: io.StringIO = stdout.start()
        self.addCleanup(stdout.stop)

    def gate(self, replies: list[str]) -> FakeRcon:
        demo = studio.Studio(Path('/tmp/unused'), skip_build=True)
        demo.rcon = FakeRcon(replies)
        demo.await_tps()
        return demo.rcon

    def test_gate_waits_for_nineteen_for_up_to_180_seconds(self) -> None:
        self.assertEqual(studio.TPS_MINIMUM, 19.0)
        self.assertEqual(studio.TPS_TIMEOUT, 180.0)

    def test_gate_waits_until_the_minimum_and_reports_the_value(self) -> None:
        rcon = self.gate([tps_reply('16.8'), tps_reply('19.3')])
        self.assertEqual(rcon.commands, ['tps', 'tps'])
        self.assertIn('[TPS] 1-minute TPS 19.3 after ', self.stdout.getvalue())
        self.assertNotIn('warning', self.stdout.getvalue())

    def test_gate_timeout_warns_and_proceeds(self) -> None:
        with mock.patch.object(studio, 'TPS_TIMEOUT', 0.0):
            rcon = self.gate([tps_reply('16.9')])
        self.assertEqual(rcon.commands, ['tps'])
        self.assertIn('[TPS] warning: 1-minute TPS 16.9 is below 19 after ', self.stdout.getvalue())

    def test_unreadable_tps_reply_warns_and_proceeds(self) -> None:
        rcon = self.gate(['Unknown command. Type "/help" for help.'])
        self.assertEqual(rcon.commands, ['tps'])
        self.assertIn('[TPS] warning: no 1-minute TPS in the tps reply', self.stdout.getvalue())


class FakeClock:
    def __init__(self, now: float) -> None:
        self.now: float = now

    def monotonic(self) -> float:
        return self.now

    def sleep(self, seconds: float) -> None:
        self.now += seconds


class FakeSkinBridge:
    def __init__(self, replies: list[dict | Exception]) -> None:
        self.replies: list[dict | Exception] = replies
        self.requests: int = 0

    def state(self) -> dict:
        reply: dict | Exception = self.replies[min(self.requests, len(self.replies) - 1)]
        self.requests += 1
        if isinstance(reply, Exception):
            raise reply
        return reply


class SkinWaitTest(unittest.TestCase):
    def setUp(self) -> None:
        self.clock: FakeClock = FakeClock(1000.0)
        for patcher in (mock.patch.object(studio.time, 'monotonic', side_effect=self.clock.monotonic),
                        mock.patch.object(studio.time, 'sleep', side_effect=self.clock.sleep)):
            patcher.start()
            self.addCleanup(patcher.stop)
        stdout = mock.patch('sys.stdout', new_callable=io.StringIO)
        self.stdout: io.StringIO = stdout.start()
        self.addCleanup(stdout.stop)

    def wait(self, replies: list[dict | Exception], connected_at: float = 1000.0) -> FakeSkinBridge:
        demo: studio.Studio = studio.Studio(Path('/tmp/unused'), skip_build=True)
        bridge: FakeSkinBridge = FakeSkinBridge(replies)
        demo.bridge = bridge
        demo.connected_at = connected_at
        demo.await_skin()
        return bridge

    def test_wait_lasts_up_to_30_s_after_the_client_connects(self) -> None:
        self.assertEqual(studio.SKIN_TIMEOUT, 30.0)

    def test_loaded_skin_ends_the_wait(self) -> None:
        bridge: FakeSkinBridge = self.wait([{'skinLoaded': False}, {'skinLoaded': False}, {'skinLoaded': True}])
        self.assertEqual(bridge.requests, 3)
        self.assertEqual(self.clock.now, 1001.0)
        self.assertEqual(self.stdout.getvalue(), '[SKIN] loaded 1.0 s after the client connected\n')

    def test_skin_that_never_loads_is_reported_as_default_30_s_after_connect(self) -> None:
        bridge: FakeSkinBridge = self.wait([{'skinLoaded': False}])
        self.assertEqual(self.clock.now, 1030.0)
        self.assertEqual(bridge.requests, 61)
        self.assertEqual(self.stdout.getvalue(), '[SKIN] default 30.0 s after the client connected: the session lookup did not answer, '
                                                 'so the actor wears a default skin\n')

    def test_time_since_the_connect_counts_toward_the_wait(self) -> None:
        self.clock.now = 1025.0
        bridge: FakeSkinBridge = self.wait([{'skinLoaded': False}])
        self.assertEqual(self.clock.now, 1030.0)
        self.assertEqual(bridge.requests, 11)
        self.assertIn('[SKIN] default 30.0 s after the client connected', self.stdout.getvalue())

    def test_skin_is_read_once_when_the_deadline_has_passed(self) -> None:
        self.clock.now = 1040.0
        bridge: FakeSkinBridge = self.wait([{'skinLoaded': True}])
        self.assertEqual(bridge.requests, 1)
        self.assertEqual(self.stdout.getvalue(), '[SKIN] loaded 40.0 s after the client connected\n')

    def test_missing_field_counts_as_the_default_skin(self) -> None:
        self.clock.now = 1040.0
        bridge: FakeSkinBridge = self.wait([{'connected': True}])
        self.assertEqual(bridge.requests, 1)
        self.assertEqual(self.clock.now, 1040.0)
        self.assertIn('[SKIN] default 40.0 s after the client connected', self.stdout.getvalue())

    def test_unanswered_state_requests_are_retried(self) -> None:
        bridge: FakeSkinBridge = self.wait([studio.BridgeTimeout('state timed out'), ConnectionRefusedError(61, 'Connection refused'),
                                            {'skinLoaded': True}])
        self.assertEqual(bridge.requests, 3)
        self.assertIn('[SKIN] loaded 1.0 s after the client connected', self.stdout.getvalue())

    def test_rejected_state_request_fails_the_start(self) -> None:
        with self.assertRaisesRegex(RuntimeError, 'state rejected with HTTP 403'):
            self.wait([RuntimeError('state rejected with HTTP 403: token')])


class PrismReloadTest(unittest.TestCase):
    def test_reload_restores_files_and_desired_settings(self) -> None:
        self.exercise_reload(False)

    def test_failed_removal_wait_restores_files_and_desired_settings(self) -> None:
        self.exercise_reload(True)

    def exercise_reload(self, fail: bool) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            instance: Path = Path(tmp) / 'instances' / 'AdaptDemoStudio-3'
            instance.mkdir(parents=True)
            (instance / 'instance.cfg').write_bytes(b'new settings')
            (instance / 'mod.jar').write_bytes(b'preserved')
            def wait(log: Path, offset: int, target: Path, *, removed: bool) -> None:
                if removed:
                    self.assertFalse(instance.exists())
                    staged: Path = next(Path(tmp).glob('.AdaptDemoStudio-3-reload-*'))
                    (staged / 'instance.cfg').write_bytes(b'cached settings')
                    if fail:
                        raise RuntimeError('removal timeout')
                else:
                    self.assertEqual((instance / 'instance.cfg').read_bytes(), b'new settings')
            with mock.patch.object(studio.clientqa, 'client_processes', return_value=[]), \
                    mock.patch.object(studio, 'prism_running', return_value=True), \
                    mock.patch.object(studio, 'await_instance_reload', side_effect=wait):
                if fail:
                    with self.assertRaisesRegex(RuntimeError, 'removal timeout'):
                        studio.reload_prism_instance(instance, Path(tmp) / 'log')
                else:
                    studio.reload_prism_instance(instance, Path(tmp) / 'log')
            self.assertEqual((instance / 'instance.cfg').read_bytes(), b'new settings')
            self.assertEqual((instance / 'mod.jar').read_bytes(), b'preserved')
            self.assertEqual(list(Path(tmp).glob('.*reload-*')), [])

    def test_running_client_is_never_moved(self) -> None:
        with mock.patch.object(studio.clientqa, 'client_processes', return_value=[42]), \
                mock.patch.object(studio, 'prism_running') as running:
            with self.assertRaisesRegex(RuntimeError, 'is running'):
                studio.reload_prism_instance(Path('/unused/AdaptDemoStudio-3'))
        running.assert_not_called()

    def test_no_gui_skips_watcher(self) -> None:
        with mock.patch.object(studio.clientqa, 'client_processes', return_value=[]), \
                mock.patch.object(studio, 'prism_running', return_value=False), \
                mock.patch.object(studio, 'await_instance_reload') as wait:
            studio.reload_prism_instance(Path('/unused/AdaptDemoStudio-3'))
        wait.assert_not_called()


class ReplayStartupTest(unittest.TestCase):
    def test_replay_start_does_not_prepare_server_or_actor(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            demo: studio.Studio = studio.Studio(Path(tmp), skip_build=False)
            with mock.patch.object(demo, 'prepare_jars', return_value=[]) as prepare, \
                    mock.patch.object(demo, 'launch_client') as launch, \
                    mock.patch.object(demo, 'start_server') as server, \
                    mock.patch.object(demo, 'prepare_world') as world, \
                    mock.patch.object(demo, 'warm_up') as warm, \
                    mock.patch.object(demo, 'await_skin') as skin:
                demo.start_replay()
            prepare.assert_called_once_with(client_only=True)
            launch.assert_called_once_with([], replay_only=True)
            for unused in (server, world, warm, skin):
                unused.assert_not_called()
            self.assertFalse(demo.created_server)

    def test_main_menu_bridge_is_ready_without_world(self) -> None:
        bridge: mock.Mock = mock.Mock()
        bridge.state.side_effect = [ConnectionRefusedError(), {'connected': False, 'inWorld': False}]
        with mock.patch.object(studio.time, 'sleep'):
            studio.await_bridge(bridge, 'replay', timeout=1)
        self.assertEqual(bridge.state.call_count, 2)

    def test_replay_launch_omits_server_argument(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            demo: studio.Studio = studio.Studio(Path(tmp), skip_build=True)
            with mock.patch.object(studio.clientqa, 'prepare_instance'), \
                    mock.patch.object(studio, 'reload_prism_instance'), \
                    mock.patch.object(studio, 'launch_prism') as launch, \
                    mock.patch.object(demo, 'server_port', side_effect=AssertionError('server queried')):
                demo.open_client(demo.instance, demo.client_title, 1234, 'test', demo.actor, [], 'launcher.log', replay_only=True)
            self.assertNotIn('--server', launch.call_args.args[0])

    def test_client_only_preparation_does_not_build_or_copy_plugins(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            demo: studio.Studio = studio.Studio(Path(tmp), skip_build=False)
            with mock.patch.object(studio, 'build_lock'), \
                    mock.patch.object(studio.clientqa, 'ensure_mods', return_value=[]), \
                    mock.patch.object(demo, 'build') as build, \
                    mock.patch.object(studio.shutil, 'copy2') as copy:
                demo.prepare_jars(client_only=True)
            self.assertEqual(build.call_count, 1)
            self.assertEqual(build.call_args.args[1], 'client-build.log')
            self.assertEqual(copy.call_count, 1)
            self.assertEqual(copy.call_args.args[0].name, studio.BRIDGE_JAR)


if __name__ == '__main__':
    unittest.main()
