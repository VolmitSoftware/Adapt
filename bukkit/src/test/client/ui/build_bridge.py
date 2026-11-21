"""Builds the Adapt UI bridge mod for one loader and Minecraft version.

    python3 bukkit/src/test/client/ui/build_bridge.py <fabric|forge|neoforge> <mc>
    python3 bukkit/src/test/client/ui/build_bridge.py --audit <mc>

Sources are layered: bridge/common, then the layers listed in TARGETS (later layers replace files at the same
relative path), then bridge/loaders/<loader>. The jar lands in build/client-ui/bridge/<loader>-<mc>/AdaptUiBridge.jar.
Compile inputs come from the shared Gradle caches (Loom's Mojang-named game jars and the version's libraries); the
obfuscated targets (Fabric 1.20.1 and 1.21.1, Forge 1.20.1) are remapped with tiny-remapper afterwards, so build Adapt
for forge 1.20.1 once first (its moddev artifacts hold the srg mappings).

--audit lists every call in the game jar that could show, focus or raise the window or capture the pointer and fails
when one is not neutralised by a bridge mixin of that version.
"""
import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

HERE: Path = Path(__file__).resolve().parent
ROOT: Path = HERE.parents[4]
BRIDGE: Path = HERE / 'bridge'
OUTPUT: Path = ROOT / 'build' / 'client-ui'
GRADLE_CACHE: Path = Path.home() / '.gradle' / 'caches'
MODULES: Path = GRADLE_CACHE / 'modules-2' / 'files-2.1'
LOOM: Path = GRADLE_CACHE / 'fabric-loom'
MOD_ID: str = 'adapt_ui_bridge'
MIXIN_CONFIG: str = 'adapt_ui_bridge.mixins.json'

TARGETS: dict[str, dict[str, object]] = {
    '1.20.1': {'java': 17, 'layers': ['glfw', 'legacy', '1.20.1'], 'obfuscated': True},
    '1.21.1': {'java': 21, 'layers': ['glfw', 'legacy'], 'obfuscated': True},
    '26.1.2': {'java': 25, 'layers': ['glfw', 'modern', 'events', '26.1.2'], 'obfuscated': False},
    '26.2': {'java': 25, 'layers': ['glfw', 'modern', 'events', '26.2'], 'obfuscated': False},
    '26.3': {'java': 25, 'layers': ['modern', 'sdl'], 'obfuscated': False},
}
LOADERS: tuple[str, ...] = ('fabric', 'forge', 'neoforge')

# Window, focus and pointer calls in the game jar, and the bridge mixin that neutralises each (class, method).
WINDOW_CALLS: tuple[str, ...] = (
    'glfwShowWindow', 'glfwFocusWindow', 'glfwRequestWindowAttention', 'glfwMaximizeWindow', 'glfwRestoreWindow',
    'glfwSetWindowMonitor', 'glfwSetInputMode', 'glfwSetCursorPos', 'SDL_ShowWindow', 'SDL_RaiseWindow',
    'SDL_SetWindowFullscreen', 'SDL_SetWindowMouseGrab', 'SDL_SetWindowRelativeMouseMode', 'SDL_RestoreWindow',
    'SDL_MaximizeWindow', 'SDL_FlashWindow', 'SDL_WarpMouseInWindow', 'SDL_SetWindowKeyboardGrab', 'SDL_CaptureMouse',
    'activateIgnoringOtherApps', 'requestUserAttention')
NEUTRALISED: dict[str, dict[tuple[str, str], str]] = {
    'legacy': {
        ('com/mojang/blaze3d/platform/InputConstants', 'grabOrReleaseMouse'): 'InputConstantsMixin cancels',
        ('com/mojang/blaze3d/platform/InputConstants', 'updateRawMouseInput'): 'InputConstantsMixin cancels',
        ('com/mojang/blaze3d/platform/Window', 'setMode'): 'WindowMixin cancels',
    },
    'events': {
        ('com/mojang/blaze3d/platform/InputConstants', 'grabOrReleaseMouse'): 'InputConstantsMixin cancels',
        ('com/mojang/blaze3d/platform/InputConstants', 'updateRawMouseInput'): 'InputConstantsMixin cancels',
        ('com/mojang/blaze3d/platform/TextInputManager', 'setIMEInputMode'): 'IME input mode only, no pointer or focus change',
        ('com/mojang/blaze3d/platform/Window', 'setMode'): 'WindowMixin cancels',
        ('net/minecraft/client/Minecraft', '<init>'): 'MinecraftShowMixin skips glfwShowWindow (26.2)',
    },
    'sdl': {
        ('com/mojang/blaze3d/platform/InputConstants', 'grabMouse'): 'InputConstantsMixin cancels',
        ('com/mojang/blaze3d/platform/InputConstants', 'releaseMouse'): 'InputConstantsMixin cancels',
        ('com/mojang/blaze3d/platform/Window', 'applyBorderlessFullscreenWindow'): 'WindowMixin cancels',
        ('com/mojang/blaze3d/platform/Window', 'applyFullscreen'): 'WindowMixin cancels',
        ('com/mojang/blaze3d/platform/Window', 'applySdlBorderlessFullscreen'): 'WindowMixin cancels',
        ('com/mojang/blaze3d/platform/Window', 'applyWindowed'): 'WindowMixin cancels',
        ('com/mojang/blaze3d/platform/Window', 'restoreWindow'): 'WindowMixin cancels',
        ('com/mojang/blaze3d/platform/Window', 'updateWindowMouseGrab'): 'WindowMixin cancels',
    },
}


def family(minecraft: str) -> str:
    layers: list[str] = TARGETS[minecraft]['layers']
    return 'sdl' if 'sdl' in layers else 'events' if 'events' in layers else 'legacy'


def jdk(major: int) -> Path:
    for candidate in (os.environ.get(f'JAVA_HOME_{major}'), f'/opt/homebrew/opt/openjdk@{major}'):
        if candidate and (Path(candidate) / 'bin' / 'java').is_file():
            return Path(candidate)
    homes: list[Path] = sorted((GRADLE_CACHE.parent / 'jdks').glob(f'*-{major}-*/*/Contents/Home'))
    if homes:
        return homes[0]
    raise SystemExit(f'No Java {major} found: set JAVA_HOME_{major}')


def named_game_jar(minecraft: str) -> Path:
    maven: Path = LOOM / 'minecraftMaven' / 'net' / 'minecraft'
    if TARGETS[minecraft]['obfuscated']:
        candidates: list[Path] = sorted(maven.glob(f'minecraft-merged/{minecraft}-loom.mappings.*/minecraft-merged-{minecraft}-*.jar'))
    else:
        candidates = [maven / 'minecraft-merged-deobf' / minecraft / f'minecraft-merged-deobf-{minecraft}.jar']
    candidates = [path for path in candidates if path.is_file() and not path.name.endswith('-sources.jar')]
    if not candidates:
        raise SystemExit(f'No Mojang-named {minecraft} game jar in the Loom cache; build adapters/fabric for {minecraft} once')
    return candidates[-1]


def cached_jar(group: str, artifact: str, version: str, classifier: str = '') -> Path:
    suffix: str = '-' + classifier if classifier else ''
    found: list[Path] = sorted((MODULES / group / artifact / version).glob(f'*/{artifact}-{version}{suffix}.jar'))
    if not found:
        raise SystemExit(f'Missing cached library {group}:{artifact}:{version}{":" + classifier if classifier else ""}')
    return found[0]


def libraries(minecraft: str) -> list[Path]:
    document: dict = json.loads((LOOM / minecraft / 'mojang_minecraft_info.json').read_text())
    result: list[Path] = []
    for entry in document['libraries']:
        parts: list[str] = entry['name'].split(':')
        if len(parts) == 4 and parts[3].startswith('natives-'):
            continue
        result.append(cached_jar(*parts))
    return result


def compile_classpath(minecraft: str) -> list[Path]:
    return [named_game_jar(minecraft), *libraries(minecraft), cached_jar('org.spongepowered', 'mixin', '0.8.5')]


def layered_sources(minecraft: str, loader: str) -> dict[Path, Path]:
    sources: dict[Path, Path] = {}
    layers: list[str] = TARGETS[minecraft]['layers']
    for layer in ['common', *layers, 'loaders/' + loader, *(f'loaders/{loader}/{name}' for name in layers)]:
        directory: Path = BRIDGE / layer / 'java'
        if directory.is_dir():
            sources.update({path.relative_to(directory): path for path in directory.rglob('*.java')})
    return sources


def annotation_stubs(workspace: Path) -> Path:
    stubs: Path = workspace / 'stubs'
    for package in ('net/minecraftforge/fml/common', 'net/neoforged/fml/common'):
        (stubs / package).mkdir(parents=True)
        (stubs / package / 'Mod.java').write_text(
            f'package {package.replace("/", ".")};\n\nimport java.lang.annotation.*;\n\n'
            '@Retention(RetentionPolicy.RUNTIME)\n@Target(ElementType.TYPE)\npublic @interface Mod {\n    String value();\n}\n')
    return stubs


def metadata(classes: Path, loader: str, minecraft: str, mixins: list[str]) -> None:
    (classes / MIXIN_CONFIG).write_text(json.dumps({
        'required': True, 'minVersion': '0.8', 'package': 'art.arcane.adapt.clientui.mixin',
        'compatibilityLevel': 'JAVA_17', 'client': mixins, 'injectors': {'defaultRequire': 1}}, indent=2) + '\n')
    if loader == 'fabric':
        (classes / 'fabric.mod.json').write_text(json.dumps({
            'schemaVersion': 1, 'id': MOD_ID, 'version': '1.0.0', 'name': 'Adapt UI Bridge', 'environment': 'client',
            'mixins': [MIXIN_CONFIG], 'depends': {'minecraft': minecraft}}, indent=2) + '\n')
        return
    meta: Path = classes / 'META-INF'
    meta.mkdir(exist_ok=True)
    pack_format: int = json.loads((ROOT / 'gradle' / 'targets.json').read_text())[minecraft]['dataPackFormat']
    (classes / 'pack.mcmeta').write_text(json.dumps({'pack': {'description': 'Adapt UI Bridge', 'pack_format': pack_format}}) + '\n')
    body: str = (f'modLoader = "javafml"\nloaderVersion = "[1,)"\nlicense = "GPL-3.0"\n\n[[mods]]\nmodId = "{MOD_ID}"\n'
                 'version = "1.0.0"\ndisplayName = "Adapt UI Bridge"\ndisplayTest = "IGNORE_ALL_VERSION"\n')
    if loader == 'neoforge':
        (meta / 'neoforge.mods.toml').write_text(body + f'\n[[mixins]]\nconfig = "{MIXIN_CONFIG}"\n')
    else:
        (meta / 'mods.toml').write_text(body)
    (meta / 'MANIFEST.MF').write_text('Manifest-Version: 1.0\n' + ('MixinConfigs: ' + MIXIN_CONFIG + '\n' if loader == 'forge' else '') + '\n')


def remap_tool(workspace: Path) -> list[str]:
    tools: Path = OUTPUT / 'tools'
    remapper: Path = cached_jar('net.fabricmc', 'tiny-remapper', '0.12.0', 'fat')
    mapping_io: Path = cached_jar('net.fabricmc', 'mapping-io', '0.8.0')
    classpath: str = os.pathsep.join([str(remapper), str(mapping_io)])
    compiled: Path = tools / 'Remap.class'
    source: Path = HERE / 'tools' / 'Remap.java'
    if not compiled.is_file() or compiled.stat().st_mtime < source.stat().st_mtime:
        tools.mkdir(parents=True, exist_ok=True)
        subprocess.run([str(jdk(25) / 'bin' / 'javac'), '--release', '21', '-cp', classpath, '-d', str(tools), str(source)], check=True)
    return [str(jdk(25) / 'bin' / 'java'), '-cp', os.pathsep.join([str(tools), classpath]), 'Remap']


def remap(loader: str, minecraft: str, jar: Path, workspace: Path) -> Path:
    if loader == 'fabric':
        mappings: list[Path] = sorted((LOOM / minecraft).glob('loom.mappings.*/mappings.tiny'))
        if not mappings:
            raise SystemExit(f'No Loom mappings for {minecraft}; build adapters/fabric for {minecraft} once')
        source, target, mapping = 'named', 'intermediary', mappings[-1]
    elif loader == 'forge' and minecraft == '1.20.1':
        mapping = ROOT / 'adapters/forge/build/1.20.1/moddev/artifacts/namedToIntermediate.tsrg'
        if not mapping.is_file():
            raise SystemExit('Missing ' + str(mapping) + '; build adapters/forge for 1.20.1 once')
        source, target = 'source', 'target'
    else:
        return jar
    remapped: Path = workspace / 'remapped.jar'
    subprocess.run([*remap_tool(workspace), str(jar), str(remapped), str(mapping), source, target,
                    *map(str, compile_classpath(minecraft))], check=True)
    return remapped


def build(loader: str, minecraft: str) -> Path:
    if minecraft not in TARGETS or loader not in LOADERS:
        raise SystemExit(f'Unsupported target {loader} {minecraft}')
    if loader == 'neoforge' and minecraft == '1.20.1':
        raise SystemExit('NeoForge 1.20.1 is not a target; Forge 1.20.1 covers it')
    output: Path = OUTPUT / 'bridge' / f'{loader}-{minecraft}'
    output.mkdir(parents=True, exist_ok=True)
    artifact: Path = output / 'AdaptUiBridge.jar'
    workspace: Path = Path(tempfile.mkdtemp(prefix='.build-', dir=output))
    try:
        classes: Path = workspace / 'classes'
        classes.mkdir()
        sources: dict[Path, Path] = layered_sources(minecraft, loader)
        info: Path = workspace / 'generated' / 'art/arcane/adapt/clientui/BuildInfo.java'
        info.parent.mkdir(parents=True)
        info.write_text('package art.arcane.adapt.clientui;\n\nfinal class BuildInfo {\n'
                        f'    static final String MINECRAFT = "{minecraft}";\n    static final String LOADER = "{loader}";\n\n'
                        '    private BuildInfo() {\n    }\n}\n')
        sources[Path('art/arcane/adapt/clientui/BuildInfo.java')] = info
        stubs: Path = annotation_stubs(workspace)
        stub_classes: Path = workspace / 'stub-classes'
        javac: str = str(jdk(25) / 'bin' / 'javac')
        classpath: list[Path] = compile_classpath(minecraft)
        subprocess.run([javac, '--release', '17', '-d', str(stub_classes), *map(str, stubs.rglob('*.java'))], check=True)
        subprocess.run([javac, '--release', '17', '-proc:none', '-parameters', '-nowarn', '-classpath',
                        os.pathsep.join([*map(str, classpath), str(stub_classes)]), '-d', str(classes),
                        *map(str, sorted(sources.values()))], check=True)
        mixin_package: Path = classes / 'art/arcane/adapt/clientui/mixin'
        mixins: list[str] = sorted(path.stem for path in mixin_package.glob('*.class') if '$' not in path.stem)
        metadata(classes, loader, minecraft, mixins)
        staged: Path = workspace / 'bridge.jar'
        jar_tool: str = str(jdk(25) / 'bin' / 'jar')
        manifest: Path = classes / 'META-INF' / 'MANIFEST.MF'
        if manifest.is_file():
            manifest.rename(workspace / 'MANIFEST.MF')
            subprocess.run([jar_tool, '--create', '--file', str(staged), '--manifest', str(workspace / 'MANIFEST.MF'),
                            '-C', str(classes), '.'], check=True)
        else:
            subprocess.run([jar_tool, '--create', '--file', str(staged), '-C', str(classes), '.'], check=True)
        final: Path = remap(loader, minecraft, staged, workspace) if TARGETS[minecraft]['obfuscated'] else staged
        os.replace(final, artifact)
        (output / 'bridge.json').write_text(json.dumps({'loader': loader, 'minecraft': minecraft, 'mixins': mixins,
                                                        'remapped': final != staged}, indent=2) + '\n')
    finally:
        shutil.rmtree(workspace, ignore_errors=True)
    return artifact


def call_sites(jar: Path, names: tuple[str, ...]) -> list[tuple[str, str, str]]:
    encoded: list[bytes] = [name.encode() for name in names]
    found: list[tuple[str, str, str]] = []
    javap: str = str(jdk(25) / 'bin' / 'javap')
    with zipfile.ZipFile(jar) as archive, tempfile.TemporaryDirectory() as scratch:
        for entry in archive.namelist():
            if not entry.endswith('.class'):
                continue
            data: bytes = archive.read(entry)
            hits: list[str] = [name for name, raw in zip(names, encoded) if raw in data]
            if not hits:
                continue
            path: Path = Path(scratch) / 'Probe.class'
            path.write_bytes(data)
            listing: str = subprocess.run([javap, '-c', '-p', str(path)], capture_output=True, text=True, check=True).stdout
            method: str = '?'
            owner_name: str = entry[:-6].replace('/', '.')
            for line in listing.splitlines():
                constructor = re.match(r'^  (?:public |private |protected )?([\w.$]+)\(.*\)( throws [\w.$, ]+)?;$', line)
                header = re.match(r'^  (?:[\w.$<>\[\], ?]+ )?([\w$<>]+)\(.*\)( throws [\w.$, ]+)?;$', line)
                if constructor and constructor.group(1) == owner_name:
                    method = '<init>'
                elif header:
                    method = header.group(1)
                elif line.strip() == 'static {};':
                    method = '<clinit>'
                for hit in hits:
                    if re.search(r'[./]' + hit + r':\(', line) or ' ' + hit + ':(' in line:
                        found.append((entry[:-6], method, hit))
    return sorted(set(found))


def audit(minecraft: str) -> dict[str, object]:
    allowed: dict[tuple[str, str], str] = NEUTRALISED[family(minecraft)]
    report: dict[str, object] = {'minecraft': minecraft, 'family': family(minecraft), 'calls': [], 'unhandled': []}
    for owner, method, call in call_sites(named_game_jar(minecraft), WINDOW_CALLS):
        reason: str | None = allowed.get((owner, method))
        if owner.endswith('/Minecraft') and method == '<init>' and minecraft != '26.2':
            reason = None
        entry: dict[str, str] = {'class': owner, 'method': method, 'call': call, 'handledBy': reason or ''}
        report['calls'].append(entry)
        if reason is None:
            report['unhandled'].append(entry)
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description='Build or audit the Adapt UI bridge mod.')
    parser.add_argument('--audit', action='store_true', help='only audit the window calls of the game jar')
    parser.add_argument('target', nargs='+', help='<loader> <mc>, or <mc> with --audit')
    args = parser.parse_args()
    if args.audit:
        report: dict[str, object] = audit(args.target[-1])
        print(json.dumps(report, indent=2))
        sys.exit(1 if report['unhandled'] else 0)
    if len(args.target) != 2:
        parser.error('expected <loader> <mc>')
    print(build(args.target[0], args.target[1]))


if __name__ == '__main__':
    main()
