import argparse
import hashlib
import json
import os
import re
from pathlib import Path
import secrets
import signal
import shutil
import socket
import struct
import subprocess
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[3]
PRISM = Path.home() / 'Library/Application Support/PrismLauncher'
LAUNCHER = Path('/Applications/Prism Launcher.app/Contents/MacOS/prismlauncher')
MULTIPLEXOR = ROOT.parent.parent / '[Minecraft Server]'


class Rcon:
    def __init__(self, server: Path):
        properties = {}
        for line in (server / 'server.properties').read_text().splitlines():
            if line and not line.startswith('#') and '=' in line:
                key, value = line.split('=', 1)
                properties[key] = value
        self.connection = socket.create_connection(('127.0.0.1', int(properties['rcon.port'])), timeout=10)
        self.identifier = 0
        self.history = []
        self.exchange(3, properties['rcon.password'])

    def receive(self, size: int) -> bytes:
        result = b''
        while len(result) < size:
            part = self.connection.recv(size - len(result))
            if not part:
                raise ConnectionError('RCON disconnected')
            result += part
        return result

    def exchange(self, kind: int, text: str) -> str:
        self.identifier += 1
        payload = struct.pack('<ii', self.identifier, kind) + text.encode() + b'\0\0'
        self.connection.sendall(struct.pack('<i', len(payload)) + payload)
        size = struct.unpack('<i', self.receive(4))[0]
        if size < 10 or size > 1048576:
            raise ValueError('Invalid RCON response length')
        response = self.receive(size)
        identifier, response_kind = struct.unpack('<ii', response[:8])
        if identifier != self.identifier:
            raise RuntimeError('RCON authentication or response correlation failed')
        return response[8:-2].decode()

    def command(self, text: str) -> str:
        response = self.exchange(2, text)
        self.history.append({'command': text, 'response': response})
        if any(message in response for message in ['Unexpected argument', 'Unknown or incomplete command', 'Incorrect argument', 'No player was found', 'Expected whitespace', 'Unknown adaptation', 'Unknown skill']):
            raise AssertionError(f'{text}: {response}')
        return response

    def close(self):
        self.connection.close()


class Bridge:
    def __init__(self, port: int, token: str):
        self.url = f'http://127.0.0.1:{port}'
        self.token = token

    def request(self, operation=None):
        data = None if operation is None else json.dumps(operation).encode()
        request = urllib.request.Request(self.url + ('/state' if data is None else '/command'), data=data,
                                         headers={'X-Adapt-QA-Token': self.token, 'Content-Type': 'application/json'})
        with urllib.request.urlopen(request, timeout=10) as response:
            result = json.load(response)
        if result.get('error'):
            raise RuntimeError(result['error'])
        return result

    def state(self):
        return self.request()

    def command(self, operation: str, **values):
        return self.request({'op': operation, **values})

    def wait(self, predicate, label: str, timeout: float = 10):
        deadline = time.monotonic() + timeout
        last = None
        while time.monotonic() < deadline:
            last = self.state()
            if predicate(last):
                return last
            time.sleep(0.04)
        raise AssertionError(f'Timed out: {label}; last state={json.dumps(last)[:1200]}')


def free_port():
    with socket.socket() as listener:
        listener.bind(('127.0.0.1', 0))
        return listener.getsockname()[1]


def mux(*arguments, timeout=180):
    result = subprocess.run([str(MULTIPLEXOR / 'start.sh'), '--consumer', 'plugin', *arguments],
                            cwd=MULTIPLEXOR, check=True, capture_output=True, text=True, timeout=timeout)
    return result.stdout.strip()


def configure_server(server: Path):
    properties = {}
    for line in (server / 'server.properties').read_text().splitlines():
        if line and not line.startswith('#') and '=' in line:
            key, value = line.split('=', 1)
            properties[key] = value
    properties.update({'use-native-transport': 'false', 'level-type': 'minecraft:flat', 'generate-structures': 'false',
                       'view-distance': '6', 'simulation-distance': '4', 'motd': 'Adapt automated client checks',
                       'generator-settings': json.dumps({'biome': 'minecraft:plains', 'layers': [
                           {'block': 'minecraft:bedrock', 'height': 1}, {'block': 'minecraft:dirt', 'height': 2},
                           {'block': 'minecraft:grass_block', 'height': 1}]}, separators=(',', ':'))})
    (server / 'server.properties').write_text(''.join(key + '=' + value + '\n' for key, value in properties.items()))
    shutil.copy2(ROOT / 'build/gameplay/plugins/Adapt.jar', server / 'plugins/Adapt.jar')


def configure_client(instance: Path, port: int, token: str, output: Path):
    game = instance / '.minecraft'
    (game / 'mods').mkdir(parents=True)
    (instance / 'mmc-pack.json').write_text(json.dumps({'formatVersion': 1, 'components': [
        {'uid': 'org.lwjgl3', 'version': '3.4.1'}, {'uid': 'net.minecraft', 'version': '26.2'},
        {'uid': 'net.fabricmc.intermediary', 'version': '26.2'},
        {'uid': 'net.fabricmc.fabric-loader', 'version': '0.19.5'}]}, indent=2) + '\n')
    java = PRISM / 'java/java-runtime-epsilon/bin/java'
    (instance / 'instance.cfg').write_text(
        '[General]\nInstanceType=OneSix\nname=Adapt Client QA\nOverrideJavaLocation=true\nJavaPath=' + str(java)
        + '\nOverrideJavaArgs=true\nJvmArgs=-Dadapt.qa.port=' + str(port) + ' -Dadapt.qa.token=' + token
        + ' -Dadapt.qa.output="' + str(output) + '"\nOverrideMemory=true\nMinMemAlloc=512\nMaxMemAlloc=2048\n'
        + 'OverrideWindow=true\nMinecraftWinWidth=960\nMinecraftWinHeight=540\nShowConsole=false\nShowConsoleOnError=false\n')
    (game / 'options.txt').write_text('autoJump:false\npauseOnLostFocus:false\nsoundCategory_master:1.0\n'
                                    'soundCategory_music:0.0\nparticles:0\nrenderDistance:6\nsimulationDistance:5\n'
                                    'maxFps:30\nenableVsync:false\n')


def wait_server(server: Path):
    deadline = time.monotonic() + 120
    while time.monotonic() < deadline:
        log = server / 'logs/latest.log'
        if log.exists() and 'Deferred recipe registration completed' in log.read_text(errors='replace'):
            return
        time.sleep(0.5)
    raise TimeoutError('Adapt did not complete startup')


def run_scenarios(bridge: Bridge, rcon: Rcon, output: Path, cases):
    from scenarios import run
    return run(bridge, rcon, output, cases)


def client_processes(client: Path):
    listing = subprocess.run(['ps', '-axo', 'pid=,args='], capture_output=True, text=True, check=True).stdout
    result = []
    for line in listing.splitlines():
        fields = line.strip().split(None, 1)
        if len(fields) == 2 and str(client) in fields[1] and 'org.prismlauncher.EntryPoint' in fields[1]:
            result.append(int(fields[0]))
    return result


def shutdown_client(bridge: Bridge, client: Path):
    errors = []
    try:
        bridge.command('quit')
    except (OSError, urllib.error.URLError):
        pass
    deadline = time.monotonic() + 15
    while client_processes(client) and time.monotonic() < deadline:
        time.sleep(0.2)
    remaining = client_processes(client)
    if remaining:
        errors.append('Client required termination after quit')
        for process_id in remaining:
            try:
                os.kill(process_id, signal.SIGTERM)
            except ProcessLookupError:
                pass
        deadline = time.monotonic() + 5
        while client_processes(client) and time.monotonic() < deadline:
            time.sleep(0.2)
        for process_id in client_processes(client):
            try:
                os.kill(process_id, signal.SIGKILL)
            except ProcessLookupError:
                pass
        deadline = time.monotonic() + 5
        while client_processes(client) and time.monotonic() < deadline:
            time.sleep(0.2)
        if client_processes(client):
            errors.append('Owned Minecraft client process remains live')
    return errors


def client_log_errors(path: Path):
    if not path.exists():
        return {'errors': ['Client log missing'], 'offlineAuthentication': []}
    blocks = re.split(r'(?=^\[\d{2}:\d{2}:\d{2}\])', path.read_text(errors='replace'), flags=re.MULTILINE)
    result = {'errors': [], 'offlineAuthentication': []}
    for block in blocks:
        if '/ERROR]' not in block.split('\n', 1)[0]:
            continue
        expected_auth = (('Failed to fetch user properties' in block or 'Failed to retrieve profile key pair' in block)
                         and ('status=401' in block or 'Status: 401' in block))
        expected_realms = (('Failed to fetch Realms feature flags' in block or "Couldn't connect to realms" in block)
                           and 'Realms authentication error' in block and 'Failed to parse into SignedJWT: 0' in block)
        result['offlineAuthentication' if expected_auth or expected_realms else 'errors'].append(block.strip())
    return result


def main():
    parser = argparse.ArgumentParser(description='Run an automated Minecraft 26.2 client against isolated Adapt test scenes.')
    parser.add_argument('--output', type=Path)
    parser.add_argument('--skip-build', action='store_true')
    parser.add_argument('--cases', nargs='+', choices=['rubber-soul', 'soft-fall'], default=['rubber-soul', 'soft-fall'])
    args = parser.parse_args()
    suffix = str(int(time.time())) + '-' + secrets.token_hex(3)
    output = (args.output or ROOT / 'build/client-qa' / suffix).resolve()
    output.mkdir(parents=True, exist_ok=True)
    token = secrets.token_hex(24)
    port = free_port()
    server_name = 'adapt-client-' + suffix
    client_name = 'AdaptClientQA-' + suffix
    client = PRISM / 'instances' / client_name
    server = None
    bridge = Bridge(port, token)
    report = {'status': 'failed', 'scope': 'Automated vanilla Minecraft client physics, audio playback, particle rendering, and screenshots',
              'minecraft': '26.2', 'serverInstance': server_name, 'clientInstance': client_name, 'startedAt': time.time()}
    created_server = False
    created_client = False
    rcon = None
    try:
        if not args.skip_build:
            environment = dict(os.environ)
            environment.pop('GIT_CONFIG_COUNT', None)
            with (output / 'build.log').open('w') as log:
                subprocess.run(['./gradlew', 'build', 'prepareGameplay'], cwd=ROOT, env=environment, stdout=log, stderr=subprocess.STDOUT, check=True)
        with (output / 'client-build.log').open('w') as log:
            subprocess.run(['bash', str(ROOT / 'src/test/client/build.sh')], cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, check=True)
        mux('server', 'create', server_name, '--type', 'paper', '--mc', '26.2', '--isolated')
        created_server = True
        mux('instance', 'port', server_name, str(free_port()))
        mux('gameplay', 'prepare', server_name)
        server = Path(mux('instance', 'path', server_name))
        configure_server(server)
        mux('runtime', 'start', server_name, '--no-console')
        wait_server(server)
        print('[READY] Isolated Paper 26.2 server', flush=True)
        client.mkdir()
        created_client = True
        configure_client(client, port, token, output)
        shutil.copy2(ROOT / 'build/client-qa/adapt-client-qa.jar', client / '.minecraft/mods/AdaptClientQa.jar')
        properties = dict(line.split('=', 1) for line in (server / 'server.properties').read_text().splitlines()
                          if line and not line.startswith('#') and '=' in line)
        report['artifactSha256'] = hashlib.sha256((server / 'plugins/Adapt.jar').read_bytes()).hexdigest()
        launch_log = (output / 'launcher.log').open('w')
        try:
            subprocess.Popen([str(LAUNCHER), '--launch', client_name, '--offline', 'AQAClient262', '--server',
                              '127.0.0.1:' + properties['server-port']], stdout=launch_log, stderr=subprocess.STDOUT)
        finally:
            launch_log.close()
        deadline = time.monotonic() + 180
        while True:
            try:
                state = bridge.state()
                break
            except (OSError, urllib.error.URLError):
                if time.monotonic() >= deadline:
                    raise TimeoutError('Automated client bridge did not start')
                time.sleep(0.5)
        report['initialState'] = state
        print('[READY] Automated Minecraft 26.2 client', flush=True)
        rcon = Rcon(server)
        report['cases'] = run_scenarios(bridge, rcon, output, args.cases)
        report['status'] = 'passed'
    except BaseException as failure:
        report['error'] = f'{type(failure).__name__}: {failure}'
        try:
            report['failureState'] = bridge.state()
        except Exception:
            report['failureState'] = None
        if rcon:
            try:
                report['failureServerState'] = {key: rcon.command('data get entity AQAClient262 ' + key)
                                                for key in ['OnGround', 'Pos', 'Motion']}
            except Exception as observation_failure:
                report['failureServerState'] = {'error': str(observation_failure)}
        raise
    finally:
        if rcon:
            report['commands'] = rcon.history
            rcon.close()
        cleanup_errors = []
        try:
            cleanup_errors.extend(shutdown_client(bridge, client))
        except Exception as failure:
            cleanup_errors.append('Client shutdown: ' + str(failure))
        if created_server:
            try:
                mux('runtime', 'stop', server_name, '--graceful')
                if server and (server / 'logs/latest.log').exists():
                    shutil.copy2(server / 'logs/latest.log', output / 'server.log')
                mux('instance', 'delete', server_name)
            except Exception as failure:
                cleanup_errors.append('Server cleanup: ' + str(failure))
        if created_client and not client_processes(client):
            try:
                if (client / '.minecraft/logs/latest.log').exists():
                    shutil.copy2(client / '.minecraft/logs/latest.log', output / 'client.log')
                shutil.rmtree(client)
            except Exception as failure:
                cleanup_errors.append('Client cleanup: ' + str(failure))
        launcher_log = output / 'launcher.log'
        if launcher_log.exists():
            launcher_log.write_text(launcher_log.read_text(errors='replace').replace(token, '[REDACTED]'))
        report['clientLog'] = client_log_errors(output / 'client.log')
        if report['clientLog']['errors']:
            cleanup_errors.append('Client runtime log contains unexpected errors')
        if (output / 'server.log').exists():
            check = subprocess.run(['node', str(ROOT / 'src/test/gameplay/runtime-log.mjs'), str(output / 'server.log')], capture_output=True, text=True)
            (output / 'server-errors.log').write_text(check.stdout + check.stderr)
            report['serverLogPassed'] = check.returncode == 0
            if check.returncode:
                cleanup_errors.append('Server runtime log contains errors')
        report['cleanupErrors'] = cleanup_errors
        if cleanup_errors:
            report['status'] = 'failed'
        report['finishedAt'] = time.time()
        (output / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
        print(str(output / 'report.json'), flush=True)
    if report['status'] != 'passed':
        raise RuntimeError('Client validation or cleanup failed')


if __name__ == '__main__':
    main()
