"""Starts a Minecraft client JVM directly, without the Prism Launcher GUI.

Component metadata comes from Prism's meta service (the same JSON Prism resolves instances from) and is cached in
build/client-ui/meta. Libraries and assets use Prism's own shared caches (PrismLauncher/libraries and
PrismLauncher/assets, Maven and Mojang layouts), downloading only missing files from the URLs in that metadata and
checking their SHA-1. Forge and NeoForge start through ForgeWrapper, which runs the loader installer into the same
library cache on first use.
"""
from __future__ import annotations

import concurrent.futures
import hashlib
import json
import os
import platform
import subprocess
import threading
import urllib.request
from dataclasses import dataclass
from pathlib import Path

PRISM: Path = Path.home() / 'Library/Application Support/PrismLauncher'
LIBRARIES: Path = PRISM / 'libraries'
ASSETS: Path = PRISM / 'assets'
META_URL: str = 'https://meta.prismlauncher.org/v1'
RESOURCES_URL: str = 'https://resources.download.minecraft.net'
OS_NAME: str = 'osx-arm64' if platform.machine() == 'arm64' else 'osx'


@dataclass(frozen=True)
class Component:
    uid: str
    version: str


@dataclass
class LaunchPlan:
    java: Path
    jvm_args: list[str]
    main_class: str
    game_args: list[str]
    classpath: list[Path]


def fetch(url: str, target: Path, sha1: str | None = None) -> Path:
    if target.is_file() and (sha1 is None or digest(target) == sha1):
        return target
    target.parent.mkdir(parents=True, exist_ok=True)
    partial: Path = target.with_name(f'{target.name}.part-{os.getpid()}-{threading.get_ident()}')
    request = urllib.request.Request(url, headers={'User-Agent': 'adapt-ui-harness'})
    with urllib.request.urlopen(request, timeout=120) as response, partial.open('wb') as sink:
        while True:
            chunk: bytes = response.read(1 << 20)
            if not chunk:
                break
            sink.write(chunk)
    if sha1 is not None and digest(partial) != sha1:
        partial.unlink()
        raise RuntimeError('Checksum mismatch for ' + url)
    os.replace(partial, target)
    return target


def digest(path: Path) -> str:
    with path.open('rb') as handle:
        return hashlib.file_digest(handle, 'sha1').hexdigest() if hasattr(hashlib, 'file_digest') else hashlib.sha1(handle.read()).hexdigest()


def metadata(component: Component, cache: Path) -> dict:
    path: Path = cache / component.uid / (component.version + '.json')
    if not path.is_file():
        fetch(f'{META_URL}/{component.uid}/{component.version}.json', path)
    return json.loads(path.read_text())


def allowed(library: dict) -> bool:
    rules: list[dict] | None = library.get('rules')
    if not rules:
        return True
    result: bool = False
    for rule in rules:
        system: dict | None = rule.get('os')
        if system is None or system.get('name') == OS_NAME:
            result = rule['action'] == 'allow'
    return result


def library_path(name: str) -> str:
    parts: list[str] = name.split(':')
    group, artifact, version = parts[0], parts[1], parts[2]
    classifier: str = '-' + parts[3] if len(parts) > 3 else ''
    extension: str = 'jar'
    if '@' in version:
        version, extension = version.split('@', 1)
    return f'{group.replace(".", "/")}/{artifact}/{version}/{artifact}-{version}{classifier}.{extension}'


def resolve_library(library: dict) -> tuple[Path, str, str | None]:
    artifact: dict = library.get('downloads', {}).get('artifact', {})
    relative: str = artifact.get('path') or library_path(library['name'])
    url: str = artifact.get('url') or (library.get('url', 'https://libraries.minecraft.net/').rstrip('/') + '/' + relative)
    return LIBRARIES / relative, url, artifact.get('sha1')


def library_key(name: str) -> str:
    parts: list[str] = name.split(':')
    return ':'.join([parts[0], parts[1], *parts[3:]])


def plan(components: list[Component], java: Path, game_dir: Path, player: str, player_uuid: str, cache: Path,
         extra_jvm: list[str], extra_game: list[str], memory_mb: int = 3072) -> LaunchPlan:
    documents: list[dict] = [metadata(component, cache) for component in components]
    libraries: dict[str, dict] = {}
    downloads: list[tuple[Path, str, str | None]] = []
    main_class: str = ''
    arguments: str = ''
    jvm_args: list[str] = []
    traits: set[str] = set()
    main_jar: dict | None = None
    asset_index: dict | None = None
    for document in documents:
        for library in document.get('libraries', []):
            if allowed(library):
                libraries[library_key(library['name'])] = library
        for library in document.get('mavenFiles', []):
            if allowed(library):
                downloads.append(resolve_library(library))
        main_class = document.get('mainClass', main_class)
        arguments = document.get('minecraftArguments', arguments)
        jvm_args.extend(document.get('+jvmArgs', []))
        traits.update(document.get('+traits', []))
        main_jar = document.get('mainJar', main_jar)
        asset_index = document.get('assetIndex', asset_index)
    if main_jar is None or asset_index is None:
        raise RuntimeError('No Minecraft component in ' + str(components))
    classpath: list[Path] = []
    for library in libraries.values():
        path, url, sha1 = resolve_library(library)
        downloads.append((path, url, sha1))
        classpath.append(path)
    minecraft: Component = next(component for component in components if component.uid == 'net.minecraft')
    client_jar: Path = LIBRARIES / f'com/mojang/minecraft/{minecraft.version}/minecraft-{minecraft.version}-client.jar'
    downloads.append((client_jar, main_jar['downloads']['artifact']['url'], main_jar['downloads']['artifact'].get('sha1')))
    classpath.append(client_jar)
    unique: dict[Path, tuple[Path, str, str | None]] = {item[0]: item for item in downloads}
    with concurrent.futures.ThreadPoolExecutor(8) as pool:
        list(pool.map(lambda item: fetch(item[1], item[0], item[2]), unique.values()))
    ensure_assets(asset_index)
    if main_class == 'io.github.zekerzhayard.forgewrapper.installer.Main':
        installer: Path = next(path for path, url, _ in downloads if path.name.endswith('-installer.jar'))
        jvm_args += [f'-Dforgewrapper.librariesDir={LIBRARIES}', f'-Dforgewrapper.installer={installer}',
                     f'-Dforgewrapper.minecraft={client_jar}']
    if 'FirstThreadOnMacOS' in traits:
        jvm_args.insert(0, '-XstartOnFirstThread')
    values: dict[str, str] = {'auth_player_name': player, 'version_name': minecraft.version, 'game_directory': str(game_dir),
                              'assets_root': str(ASSETS), 'assets_index_name': asset_index['id'], 'auth_uuid': player_uuid.replace('-', ''),
                              'auth_access_token': '0', 'user_type': 'legacy', 'version_type': 'release', 'user_properties': '{}'}
    game_args: list[str] = []
    for token in arguments.split():
        for key, value in values.items():
            token = token.replace('${' + key + '}', value)
        game_args.append(token)
    return LaunchPlan(java, ['-Xms512m', f'-Xmx{memory_mb}m', *jvm_args, *extra_jvm], main_class, game_args + extra_game, classpath)


def ensure_assets(index: dict) -> None:
    index_path: Path = fetch(index['url'], ASSETS / 'indexes' / (index['id'] + '.json'), index.get('sha1'))
    objects: dict = json.loads(index_path.read_text())['objects']
    missing: list[tuple[str, Path]] = []
    for entry in objects.values():
        hashed: str = entry['hash']
        target: Path = ASSETS / 'objects' / hashed[:2] / hashed
        if not target.is_file():
            missing.append((hashed, target))
    with concurrent.futures.ThreadPoolExecutor(16) as pool:
        list(pool.map(lambda item: fetch(f'{RESOURCES_URL}/{item[0][:2]}/{item[0]}', item[1], item[0]), missing))


def start(launch: LaunchPlan, game_dir: Path, log_path: Path) -> subprocess.Popen:
    command: list[str] = [str(launch.java), *launch.jvm_args, '-cp', os.pathsep.join(map(str, launch.classpath)),
                          launch.main_class, *launch.game_args]
    environment: dict[str, str] = {key: value for key, value in os.environ.items() if not key.startswith('JAVA_TOOL')}
    with log_path.open('w') as sink:
        return subprocess.Popen(command, cwd=game_dir, stdout=sink, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL,
                                env=environment, start_new_session=True)
