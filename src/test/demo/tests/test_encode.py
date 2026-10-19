import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / 'src' / 'test' / 'demo'))
import encode


class EncodeTest(unittest.TestCase):
    def test_normalize_uses_vp9_crf27_1080p30(self) -> None:
        command = encode.normalize_command(Path('/f'), Path('/in.mp4'), Path('/out.webm'), has_audio=True)
        joined = ' '.join(command)
        self.assertIn('-c:v libvpx-vp9', joined)
        self.assertIn('-crf 27', joined)
        self.assertIn('-b:v 0', joined)
        self.assertIn('scale=1920:1080', joined)
        self.assertIn('-r 30', joined)
        self.assertIn('-c:a libopus', joined)
        self.assertEqual(command[-1], '/out.webm')

    def test_normalize_without_audio_strips_audio(self) -> None:
        command = encode.normalize_command(Path('/f'), Path('/in.mp4'), Path('/out.webm'), has_audio=False)
        self.assertIn('-an', command)
        self.assertNotIn('libopus', ' '.join(command))

    def test_thumbnail_seeks_to_given_second(self) -> None:
        command = encode.thumbnail_command(Path('/f'), Path('/in.webm'), Path('/t.png'), 4.5)
        self.assertIn('4.5', command)
        self.assertIn('-frames:v', command)

    def test_normalize_takes_the_crf_it_is_given(self) -> None:
        command = encode.normalize_command(Path('/f'), Path('/in.mp4'), Path('/out.webm'), has_audio=False, crf=33)
        self.assertIn('-crf 33', ' '.join(command))
        self.assertNotIn('-crf 27', ' '.join(command))


class FitTest(unittest.TestCase):
    def fit(self, sizes: dict[int, int]) -> tuple[int, list[int]]:
        with tempfile.TemporaryDirectory() as tmp:
            target: Path = Path(tmp) / 'out.webm'
            used: list[int] = []

            def run(command: list[str]) -> None:
                crf: int = int(command[command.index('-crf') + 1])
                used.append(crf)
                target.write_bytes(b'x' * sizes[crf])

            with mock.patch.object(encode, 'run', side_effect=run):
                chosen: int = encode.encode_within(Path('/f'), Path('/in.mp4'), target, False, 0.5)
        return chosen, used

    def test_a_clip_that_fits_keeps_the_default_crf(self) -> None:
        self.assertEqual(self.fit({27: 24_999_999}), (27, [27]))

    def test_exactly_the_cap_fits(self) -> None:
        self.assertEqual(self.fit({27: encode.MAX_CLIP_BYTES}), (27, [27]))

    def test_an_oversized_clip_is_re_encoded_at_higher_crf_until_it_fits(self) -> None:
        self.assertEqual(self.fit({27: 26_500_000, 30: 25_100_000, 33: 21_000_000}), (33, [27, 30, 33]))

    def test_a_clip_that_never_fits_is_an_error(self) -> None:
        sizes: dict[int, int] = {crf: 30_000_000 for crf in encode.CRF_STEPS}
        with self.assertRaisesRegex(RuntimeError, 'did not fit 25000000 bytes'):
            self.fit(sizes)

    def test_the_steps_start_at_the_default_and_only_rise(self) -> None:
        self.assertEqual(encode.CRF_STEPS[0], encode.CRF)
        self.assertEqual(list(encode.CRF_STEPS), sorted(encode.CRF_STEPS))
        self.assertEqual(encode.MAX_CLIP_BYTES, 25_000_000)


if __name__ == '__main__':
    unittest.main()
