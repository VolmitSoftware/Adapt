import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / 'src' / 'test' / 'demo'))
import clip_check

PIXELS: int = clip_check.SCAN_WIDTH * clip_check.SCAN_HEIGHT


def frame(green_pixels: int, green: tuple[int, int, int] = (85, 255, 85), background: tuple[int, int, int] = (90, 140, 60)) -> bytes:
    return bytes(green) * green_pixels + bytes(background) * (PIXELS - green_pixels)


class GreenFractionTest(unittest.TestCase):
    def test_a_frame_of_grass_has_no_saturated_green(self) -> None:
        self.assertEqual(clip_check.green_fraction(frame(0)), 0.0)

    def test_the_fraction_counts_saturated_green_pixels(self) -> None:
        self.assertAlmostEqual(clip_check.green_fraction(frame(PIXELS // 10)), 0.1, places=3)

    def test_pure_green_counts(self) -> None:
        self.assertEqual(clip_check.green_fraction(frame(PIXELS, green=(0, 255, 0))), 1.0)

    def test_dark_leaf_green_does_not_count(self) -> None:
        self.assertEqual(clip_check.green_fraction(frame(PIXELS, green=(60, 140, 40))), 0.0)

    def test_yellow_green_orbs_do_not_count(self) -> None:
        self.assertEqual(clip_check.green_fraction(frame(PIXELS, green=(160, 255, 20))), 0.0)


class FindingsTest(unittest.TestCase):
    def test_frames_over_the_limit_are_reported_with_their_time(self) -> None:
        raw: bytes = frame(0) + frame(0) + frame(int(PIXELS * 0.05)) + frame(0) + frame(PIXELS // 2)
        found: list[tuple[float, float]] = clip_check.findings(raw)
        self.assertEqual([seconds for seconds, _ in found], [0.25, 0.5])
        self.assertAlmostEqual(found[0][1], 0.05, places=3)

    def test_exactly_three_percent_passes(self) -> None:
        self.assertEqual(clip_check.findings(frame(int(PIXELS * 0.03))), [])

    def test_a_clean_clip_has_no_findings(self) -> None:
        self.assertEqual(clip_check.findings(frame(0) * 8), [])

    def test_a_trailing_partial_frame_is_ignored(self) -> None:
        self.assertEqual(clip_check.findings(frame(0) + b'\x00' * 10), [])


class ScanCommandTest(unittest.TestCase):
    def test_the_clip_is_decoded_at_eight_frames_per_second_into_small_rgb(self) -> None:
        command: list[str] = clip_check.scan_command(Path('/f'), Path('/clip.webm'))
        joined: str = ' '.join(command)
        self.assertIn('-i /clip.webm', joined)
        self.assertIn('fps=8', joined)
        self.assertIn('scale=192:108', joined)
        self.assertIn('-pix_fmt rgb24', joined)
        self.assertEqual(command[-1], '-')


class CorruptFramesTest(unittest.TestCase):
    def test_a_missing_clip_has_nothing_to_scan(self) -> None:
        with mock.patch.object(clip_check.subprocess, 'run') as run:
            self.assertEqual(clip_check.corrupt_frames(Path('/f'), Path('/missing.webm')), [])
        run.assert_not_called()

    def test_the_decoded_frames_are_checked(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            clip: Path = Path(tmp) / 'c.webm'
            clip.write_bytes(b'x')
            done: mock.MagicMock = mock.MagicMock(returncode=0, stdout=frame(0) + frame(PIXELS // 4), stderr=b'')
            with mock.patch.object(clip_check.subprocess, 'run', return_value=done):
                found: list[tuple[float, float]] = clip_check.corrupt_frames(Path('/f'), clip)
        self.assertEqual([seconds for seconds, _ in found], [0.125])

    def test_a_failed_decode_is_an_error(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            clip: Path = Path(tmp) / 'c.webm'
            clip.write_bytes(b'x')
            done: mock.MagicMock = mock.MagicMock(returncode=1, stdout=b'', stderr=b'boom')
            with mock.patch.object(clip_check.subprocess, 'run', return_value=done):
                with self.assertRaisesRegex(RuntimeError, 'could not decode'):
                    clip_check.corrupt_frames(Path('/f'), clip)

    def test_the_message_names_the_first_hit(self) -> None:
        self.assertEqual(clip_check.message('golem-rancher-core-pov.webm', [(16.75, 0.271), (17.0, 0.3)]),
                         'golem-rancher-core-pov.webm has 2 frames with saturated green over 3% of the frame, the first at 16.75 s (27.1%)')


if __name__ == '__main__':
    unittest.main()
