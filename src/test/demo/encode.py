import subprocess
from pathlib import Path

WIDTH: int = 1920
HEIGHT: int = 1080
FPS: int = 30
CRF: int = 27


def normalize_command(ffmpeg: Path, source: Path, target: Path, has_audio: bool, start: float = 0.0) -> list[str]:
    command: list[str] = [str(ffmpeg), '-y'] + (['-ss', format(start, 'g')] if start > 0 else [])
    command += ['-i', str(source), '-vf', 'scale=' + str(WIDTH) + ':' + str(HEIGHT) + ':flags=lanczos', '-r', str(FPS),
                '-c:v', 'libvpx-vp9', '-crf', str(CRF), '-b:v', '0', '-row-mt', '1', '-deadline', 'good', '-cpu-used', '2', '-pix_fmt', 'yuv420p']
    command += ['-c:a', 'libopus', '-b:a', '96k'] if has_audio else ['-an']
    command.append(str(target))
    return command


def thumbnail_command(ffmpeg: Path, source: Path, target: Path, seconds: float) -> list[str]:
    return [str(ffmpeg), '-y', '-ss', str(seconds), '-i', str(source), '-frames:v', '1', '-vf', 'scale=640:-1', str(target)]


def has_audio(ffprobe: Path, source: Path) -> bool:
    result: subprocess.CompletedProcess = subprocess.run(
        [str(ffprobe), '-v', 'error', '-select_streams', 'a', '-show_entries', 'stream=codec_type', '-of', 'csv=p=0', str(source)],
        capture_output=True, text=True, check=False)
    return 'audio' in result.stdout


def run(command: list[str]) -> None:
    result: subprocess.CompletedProcess = subprocess.run(command, capture_output=True, text=True, check=False)
    if result.returncode != 0:
        raise RuntimeError('ffmpeg failed: ' + ' '.join(command) + '\n' + result.stderr[-2000:])
