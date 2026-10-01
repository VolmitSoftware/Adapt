import argparse
import contextlib
import shutil
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import choreography
import export_phase
import gallery
import manifest
import record_phase
from studio import ROOT, LaneBusy, Studio, claim, lane_lock

DOCS: Path = ROOT.parent / 'docs'
INDEX: Path = ROOT / 'build' / 'demo' / 'index.html'


def default_tool(name: str) -> str:
    local: Path = Path.home() / '.local' / 'bin' / name
    if local.exists():
        return str(local)
    return shutil.which(name) or str(local)


def arguments() -> argparse.Namespace:
    parser: argparse.ArgumentParser = argparse.ArgumentParser(description='Record and export Adapt adaptation demo clips on a real Minecraft 26.2 client.')
    parser.add_argument('--skill', required=True)
    parser.add_argument('--lane', type=int, default=1)
    parser.add_argument('--only', default='')
    parser.add_argument('--failed', action='store_true')
    parser.add_argument('--record-only', action='store_true')
    parser.add_argument('--export-only', action='store_true')
    parser.add_argument('--output', default='')
    parser.add_argument('--skip-build', action='store_true')
    parser.add_argument('--no-audio', action='store_true')
    parser.add_argument('--ffmpeg', default=default_tool('ffmpeg'))
    parser.add_argument('--ffprobe', default=default_tool('ffprobe'))
    args: argparse.Namespace = parser.parse_args()
    if args.record_only and args.export_only:
        parser.error('--record-only and --export-only cannot be combined')
    if args.lane < 1:
        parser.error('--lane must be 1 or higher')
    return args


def needs_opponent(entries: dict[str, choreography.Entry], ids: list[str], export_only: bool) -> bool:
    return not export_only and any(entries[identifier].opponent and not entries[identifier].skip for identifier in ids)


def main() -> int:
    args: argparse.Namespace = arguments()
    output: Path = (Path(args.output) if args.output else ROOT / 'build' / 'demo' / args.skill).resolve()
    output.mkdir(parents=True, exist_ok=True)
    with contextlib.ExitStack() as claims:
        try:
            claims.enter_context(claim(lane_lock(args.lane), 'Lane ' + str(args.lane)))
            claims.enter_context(claim(output / 'run.lock', 'Output folder ' + str(output)))
        except LaneBusy as busy:
            print(str(busy))
            return 2
        return batch(args, output)


def batch(args: argparse.Namespace, output: Path) -> int:
    manifest_path: Path = output / 'manifest.json'
    sheet: choreography.Sheet = choreography.load_skill(ROOT, args.skill)
    entries: dict[str, choreography.Entry] = sheet.entries
    problems: list[str] = choreography.world_problems(sheet.world) + choreography.validate(entries, choreography.adaptation_ids(ROOT, args.skill))
    if problems:
        print('\n'.join(problems))
        return 2
    takes: dict[str, manifest.Take] = manifest.load(manifest_path)
    ids: list[str] = [identifier for identifier in args.only.split(',') if identifier] or sorted(entries)
    unknown: list[str] = [identifier for identifier in ids if identifier not in entries]
    if unknown:
        print('unknown adaptation ids: ' + ', '.join(unknown))
        return 2
    if args.failed:
        ids = [identifier for identifier in ids if identifier not in takes or takes[identifier].status == 'failed']
    studio: Studio = Studio(output, args.skip_build, args.lane)
    try:
        if args.export_only:
            studio.start_replay()
        else:
            studio.start(args.skill, opponent=needs_opponent(entries, ids, args.export_only), world=sheet.world)
        if not args.export_only:
            takes = record_phase.record(studio, entries, ids, manifest_path, args.skill, ffmpeg=Path(args.ffmpeg).resolve())
        if not args.record_only:
            takes = export_phase.export(studio, takes, ids, DOCS, Path(args.ffmpeg), Path(args.ffprobe), manifest_path, audio=not args.no_audio)
    finally:
        cleanup: list[str] = studio.stop()
        for error in cleanup:
            print('[CLEANUP] ' + error, file=sys.stderr, flush=True)
    gallery.write(output / 'gallery.html', takes, DOCS, output / 'thumbs', studio.plate_label())
    gallery.write_index(INDEX)
    failed: list[str] = [identifier for identifier in ids if identifier not in takes or takes[identifier].status == 'failed']
    print('gallery: ' + str(output / 'gallery.html'))
    print('index: ' + str(INDEX))
    print('logs: ' + str(studio.log_path('server')) + ' ' + str(studio.log_path('client')))
    print('failed: ' + (', '.join(failed) if failed else 'none'))
    return 1 if failed or cleanup else 0


if __name__ == '__main__':
    sys.exit(main())
