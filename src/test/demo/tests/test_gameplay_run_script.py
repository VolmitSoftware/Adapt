import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
SCRIPT = ROOT / 'src' / 'test' / 'gameplay' / 'run.sh'


def run_script(*arguments: str, workspace: str | None = None) -> subprocess.CompletedProcess:
    environment = {key: value for key, value in os.environ.items() if key != 'ADAPT_DEMO_MULTIPLEXOR'}
    if workspace is not None:
        environment['ADAPT_DEMO_MULTIPLEXOR'] = workspace
    return subprocess.run(['bash', str(SCRIPT), *arguments], capture_output=True, text=True, env=environment, timeout=60, check=False)


class GameplayRunScriptTest(unittest.TestCase):
    def test_environment_names_the_workspace_when_no_argument_is_given(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            result = run_script('smoke', workspace=tmp)
        self.assertEqual(result.returncode, 2)
        self.assertIn('Multiplexor entrypoint missing: ' + tmp + '/start.sh', result.stderr)

    def test_the_workspace_argument_wins_over_the_environment(self) -> None:
        with tempfile.TemporaryDirectory() as argument, tempfile.TemporaryDirectory() as environment:
            result = run_script('smoke', argument, workspace=environment)
        self.assertEqual(result.returncode, 2)
        self.assertIn('Multiplexor entrypoint missing: ' + argument + '/start.sh', result.stderr)

    def test_the_sibling_workspace_is_still_the_default(self) -> None:
        expected = str(ROOT.parent.parent / '[Minecraft Server]')
        if (ROOT.parent.parent / '[Minecraft Server]' / 'start.sh').exists():
            self.skipTest('the sibling workspace exists here, so the script would start a run')
        result = run_script('smoke')
        self.assertEqual(result.returncode, 2)
        self.assertIn('Multiplexor entrypoint missing: ' + expected + '/start.sh', result.stderr)

    def test_help_mentions_the_variable(self) -> None:
        self.assertIn('ADAPT_DEMO_MULTIPLEXOR', run_script('--help').stdout)


if __name__ == '__main__':
    sys.exit(unittest.main())
