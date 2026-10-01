import sys
import unittest
from pathlib import Path

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


if __name__ == '__main__':
    unittest.main()
