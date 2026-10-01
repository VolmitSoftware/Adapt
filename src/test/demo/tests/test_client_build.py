import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT: Path = Path(__file__).resolve().parents[4]
SCRIPT: Path = ROOT / 'src' / 'test' / 'client' / 'build.sh'
COMPONENTS: list[tuple[str, str]] = [('net.minecraft', '26.2'), ('net.fabricmc.fabric-loader', '0.19.5'), ('org.lwjgl3', '3.4.1')]
JAVAC: str = ('#!/bin/bash\n'
              'while [ $# -gt 0 ]; do\n'
              '  if [ "$1" = -d ]; then target="$2"; shift; fi\n'
              '  shift\n'
              'done\n'
              'echo "javac $target" >> "$FAKE_JDK_LOG"\n'
              '[ -z "$FAKE_JAVAC_FAIL" ] || exit 1\n'
              'mkdir -p "$target/art"\n'
              'printf class > "$target/art/Bridge.class"\n')
JAR: str = ('#!/bin/bash\n'
            'while [ $# -gt 0 ]; do\n'
            '  case "$1" in\n'
            '    --file) file="$2"; shift ;;\n'
            '    -C) folder="$2"; shift ;;\n'
            '  esac\n'
            '  shift\n'
            'done\n'
            'echo "jar $file" >> "$FAKE_JDK_LOG"\n'
            '(cd "$folder" && find . -type f | sort) > "$file"\n')


class ClientBuildTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp: tempfile.TemporaryDirectory = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root: Path = Path(self.tmp.name).resolve()
        client: Path = self.root / 'src' / 'test' / 'client'
        (client / 'java' / 'art').mkdir(parents=True)
        (client / 'java' / 'art' / 'Bridge.java').write_text('class Bridge {}\n')
        (client / 'resources').mkdir()
        (client / 'resources' / 'fabric.mod.json').write_text('{}\n')
        shutil.copy2(SCRIPT, client / 'build.sh')
        for component, version in COMPONENTS:
            (self.root / 'prism' / 'meta' / component).mkdir(parents=True)
            (self.root / 'prism' / 'meta' / component / (version + '.json')).write_text('{"libraries": []}')
        (self.root / 'prism' / 'libraries').mkdir(parents=True)
        (self.root / 'jdk' / 'bin').mkdir(parents=True)
        for name, script in (('javac', JAVAC), ('jar', JAR)):
            (self.root / 'jdk' / 'bin' / name).write_text(script)
            (self.root / 'jdk' / 'bin' / name).chmod(0o755)
        self.output: Path = self.root / 'build' / 'client-qa'
        self.artifact: Path = self.output / 'adapt-client-qa.jar'
        self.log: Path = self.root / 'jdk.log'
        self.output.mkdir(parents=True)
        self.artifact.write_text('old bridge')

    def build(self, extra: dict[str, str]) -> subprocess.CompletedProcess:
        environment: dict[str, str] = {key: value for key, value in os.environ.items() if key != 'FAKE_JAVAC_FAIL'}
        environment.update({'JAVA_HOME': str(self.root / 'jdk'), 'PRISM_LIBRARIES': str(self.root / 'prism' / 'libraries'),
                            'PRISM_META': str(self.root / 'prism' / 'meta'), 'FAKE_JDK_LOG': str(self.log)})
        environment.update(extra)
        return subprocess.run(['bash', str(self.root / 'src' / 'test' / 'client' / 'build.sh')], env=environment, capture_output=True, text=True,
                              timeout=60)

    def calls(self) -> dict[str, Path]:
        return {line.split(' ', 1)[0]: Path(line.split(' ', 1)[1]).resolve() for line in self.log.read_text().splitlines()}

    def test_jar_is_built_in_a_private_folder_and_renamed_into_place(self) -> None:
        with self.artifact.open() as reader:
            result: subprocess.CompletedProcess = self.build({})
            kept: str = reader.read()
        calls: dict[str, Path] = self.calls()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(Path(result.stdout.strip()).resolve(), self.artifact)
        self.assertEqual(self.artifact.read_text().splitlines(), ['./art/Bridge.class', './fabric.mod.json'])
        self.assertEqual(kept, 'old bridge')
        self.assertEqual(calls['javac'].parent.parent, self.output)
        self.assertNotEqual(calls['javac'], self.output / 'classes')
        self.assertEqual(calls['jar'], calls['javac'].parent / 'adapt-client-qa.jar.tmp')
        self.assertEqual(sorted(path.name for path in self.output.iterdir()), ['adapt-client-qa.jar', 'compile-classpath.json'])

    def test_failed_compile_keeps_the_published_jar_and_leaves_no_build_folder(self) -> None:
        result: subprocess.CompletedProcess = self.build({'FAKE_JAVAC_FAIL': '1'})
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.artifact.read_text(), 'old bridge')
        self.assertEqual(sorted(path.name for path in self.output.iterdir()), ['adapt-client-qa.jar', 'compile-classpath.json'])


if __name__ == '__main__':
    unittest.main()
