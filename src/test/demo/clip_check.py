import subprocess
from pathlib import Path

SCAN_FPS: int = 4
SCAN_WIDTH: int = 192
SCAN_HEIGHT: int = 108
GREEN_LIMIT: float = 0.03
GREEN_MINIMUM: int = 180
OTHER_MAXIMUM: int = 100
FRAME_BYTES: int = SCAN_WIDTH * SCAN_HEIGHT * 3


def green_fraction(rgb: bytes) -> float:
    hits: int = sum(1 for red, green, blue in zip(rgb[0::3], rgb[1::3], rgb[2::3]) if green >= GREEN_MINIMUM and red <= OTHER_MAXIMUM and blue <= OTHER_MAXIMUM)
    return hits / (len(rgb) // 3)


def findings(raw: bytes) -> list[tuple[float, float]]:
    found: list[tuple[float, float]] = []
    for index in range(len(raw) // FRAME_BYTES):
        fraction: float = green_fraction(raw[index * FRAME_BYTES:(index + 1) * FRAME_BYTES])
        if fraction > GREEN_LIMIT:
            found.append((index / SCAN_FPS, fraction))
    return found


def scan_command(ffmpeg: Path, clip: Path) -> list[str]:
    return [str(ffmpeg), '-v', 'error', '-i', str(clip), '-vf', 'fps=' + str(SCAN_FPS) + ',scale=' + str(SCAN_WIDTH) + ':' + str(SCAN_HEIGHT) + ':flags=area',
            '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-']


def corrupt_frames(ffmpeg: Path, clip: Path) -> list[tuple[float, float]]:
    if not clip.is_file():
        return []
    result: subprocess.CompletedProcess = subprocess.run(scan_command(ffmpeg, clip), capture_output=True, check=False)
    if result.returncode != 0:
        raise RuntimeError('ffmpeg could not decode ' + clip.name + ' for the frame scan: ' + result.stderr.decode(errors='replace')[-500:])
    return findings(result.stdout)


def message(name: str, found: list[tuple[float, float]]) -> str:
    return (name + ' has ' + str(len(found)) + ' frames with saturated green over ' + format(GREEN_LIMIT * 100, 'g') + '% of the frame, the first at '
            + format(found[0][0], 'g') + ' s (' + format(found[0][1] * 100, '.1f') + '%)')
