#!/usr/bin/env bash
set -euo pipefail
CLIENT_SOURCE_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
export CLIENT_SOURCE_DIR
python3 - <<'PY'
import os
import json
import pathlib
import shutil
import subprocess

source = pathlib.Path(os.environ['CLIENT_SOURCE_DIR'])
project = source.parents[2]
output = project / 'build' / 'client-qa'
classes = output / 'classes'
libraries = pathlib.Path(os.environ.get('PRISM_LIBRARIES', str(pathlib.Path.home() / 'Library/Application Support/PrismLauncher/libraries')))
metadata = pathlib.Path(os.environ.get('PRISM_META', str(libraries.parent / 'meta')))
components = [('net.minecraft', '26.2'), ('net.fabricmc.fabric-loader', '0.19.5'), ('org.lwjgl3', '3.4.1')]
dependencies = {}
for component, version in components:
    document = metadata / component / f'{version}.json'
    if not document.is_file():
        raise SystemExit(f'Missing cached client metadata: {document}')
    data = json.loads(document.read_text())
    entries = ([data['mainJar']] if 'mainJar' in data else []) + data['libraries']
    for entry in entries:
        coordinate = entry['name']
        parts = coordinate.split(':')
        if len(parts) not in (3, 4):
            raise SystemExit(f'Unsupported Maven coordinate: {coordinate}')
        group, artifact, dependency_version = parts[:3]
        classifier = parts[3] if len(parts) == 4 else ''
        if '-natives-' in artifact or classifier.startswith('natives-'):
            continue
        if group == 'io.netty' and artifact.startswith('netty-transport-native-') and classifier:
            continue
        suffix = f'-{classifier}' if classifier else ''
        relative = entry.get('downloads', {}).get('artifact', {}).get('path')
        if relative is None:
            relative = f'{group.replace(".", "/")}/{artifact}/{dependency_version}/{artifact}-{dependency_version}{suffix}.jar'
        dependency = libraries / relative
        if not dependency.is_file():
            raise SystemExit(f'Missing cached class library {coordinate}: {dependency}')
        key = (group, artifact, classifier)
        if key in dependencies and dependencies[key] != dependency:
            raise SystemExit(f'Conflicting metadata versions for {group}:{artifact}:{classifier}')
        dependencies[key] = dependency
if classes.exists():
    shutil.rmtree(classes)
classes.mkdir(parents=True)
classpath = os.pathsep.join(str(path) for path in dependencies.values())
(output / 'compile-classpath.json').write_text(json.dumps({
    'components': dict(components),
    'libraries': [str(path.relative_to(libraries)) for path in dependencies.values()],
}, indent=2) + '\n')
java_home = os.environ.get('JAVA_HOME')
javac = str(pathlib.Path(java_home) / 'bin/javac') if java_home else 'javac'
jar = str(pathlib.Path(java_home) / 'bin/jar') if java_home else 'jar'
subprocess.run([javac, '--release', '25', '-proc:none', '-parameters', '-classpath', classpath,
                '-d', str(classes), *map(str, sorted((source / 'java').rglob('*.java')))], check=True)
shutil.copytree(source / 'resources', classes, dirs_exist_ok=True)
artifact = output / 'adapt-client-qa.jar'
subprocess.run([jar, '--create', '--file', str(artifact), '-C', str(classes), '.'], check=True)
print(artifact)
PY
