import argparse
import datetime
import functools
import html
import re
import shutil
import sys
from pathlib import Path
from typing import Callable

import choreography
import manifest

ROOT: Path = Path(__file__).resolve().parents[3]
FRONTMATTER: re.Pattern[str] = re.compile(r'\A---\n.*?\n---\n', re.DOTALL)
DATE_LINE: re.Pattern[str] = re.compile(r'^date: .*$', re.MULTILINE)
EXISTING_BLOCK: re.Pattern[str] = re.compile(r'\n\n<div class="adapt-demo">\n(?:<video [^\n]*\n)*</div>(?=\n|\Z)')
EXISTING_BLOCKS: re.Pattern[str] = re.compile(r'(?:' + EXISTING_BLOCK.pattern + r')+')
VIDEO_TAG: re.Pattern[str] = re.compile(r'<video [^>\n]*>')
VIDEO_SRC: re.Pattern[str] = re.compile(r' src="([^"]*)"')
VIDEO_LABEL: re.Pattern[str] = re.compile(r' aria-label="([^"]*)"')
ANGLE_LABELS: dict[str, str] = {'pov': 'First-person', 'observer': 'Third-person'}


class HeadingNotFound(RuntimeError):
    pass


class HeadingAmbiguous(RuntimeError):
    pass


class PageNotFound(RuntimeError):
    pass


def block(skill: str, identifier: str, title: str, labels: dict[str, str] | None = None) -> str:
    base: str = '/adapt-assets/demos/' + skill + '/' + identifier
    kept: dict[str, str] = labels or {}
    videos: list[str] = []
    for angle in choreography.ANGLES:
        source: str = base + '-' + angle + '.webm'
        label: str = kept.get(source) or ANGLE_LABELS[angle] + ' demonstration of ' + html.escape(title)
        videos.append('<video aria-label="' + label + '" src="' + source + '" muted loop playsinline controls preload="none"></video>\n')
    return '<div class="adapt-demo">\n' + ''.join(videos) + '</div>\n'


def existing_labels(text: str) -> dict[str, str]:
    labels: dict[str, str] = {}
    for tag in VIDEO_TAG.findall(text):
        source: re.Match[str] | None = VIDEO_SRC.search(tag)
        label: re.Match[str] | None = VIDEO_LABEL.search(tag)
        if source is not None and label is not None:
            labels[source.group(1)] = label.group(1)
    return labels


def heading(identifier: str) -> re.Pattern[str]:
    return re.compile(r'^### (?P<name>.*?)[ \t]*\(`' + re.escape(identifier) + r'`\)[ \t]*$', re.MULTILINE)


def showcase_heading(text: str, target: str, page: Path) -> re.Match[str]:
    matches: list[re.Match[str]] = list(re.finditer(r'^#{2,3} ' + re.escape(target) + r'[ \t]*$', text, re.MULTILINE))
    if not matches:
        raise HeadingNotFound('Showcase heading "' + target + '" does not exist in ' + str(page))
    if len(matches) > 1:
        raise HeadingAmbiguous('Showcase heading "' + target + '" appears ' + str(len(matches)) + ' times in ' + str(page))
    return matches[0]


def skill_page(docs: Path, ids: set[str]) -> Path:
    counts: dict[Path, int] = {}
    for page in sorted((docs / 'adapt').glob('*-skill-*.md')):
        text: str = page.read_text(encoding='utf-8')
        counts[page] = sum(1 for identifier in ids if heading(identifier).search(text))
    best: int = max(counts.values(), default=0)
    pages: list[Path] = [page for page, count in counts.items() if count == best]
    if best == 0:
        raise PageNotFound('No ' + str(docs / 'adapt' / '*-skill-*.md') + ' page has a heading for any of ' + ', '.join(sorted(ids)))
    if len(pages) > 1:
        raise PageNotFound('Pages ' + ', '.join(page.name for page in pages) + ' each have ' + str(best) + ' of the headings; expected one page')
    return pages[0]


def bump_date(text: str, now: str) -> str:
    front: re.Match[str] | None = FRONTMATTER.match(text)
    if front is None:
        return text
    return DATE_LINE.sub('date: ' + now, front.group(0), count=1) + text[front.end():]


def replace_blocks(text: str, start: int, existing: re.Match[str] | None, build: Callable[[dict[str, str]], str]) -> str | None:
    fresh: str = build(existing_labels(existing.group(0)) if existing is not None else {})
    if existing is not None and existing.group(0) == fresh:
        return None
    return text[:start] + fresh + text[existing.end() if existing is not None else start:]


def adaptation_heading_id(target: str) -> str | None:
    named: re.Match[str] | None = choreography.ADAPTATION_HEADING_END.search(target)
    return None if named is None else named.group(1)


def showcase_groups(showcase: tuple[choreography.Entry, ...] | list[choreography.Entry]) -> dict[str, list[choreography.Entry]]:
    groups: dict[str, list[choreography.Entry]] = {}
    for entry in showcase:
        groups.setdefault(entry.showcase.heading, []).append(entry)
    return groups


def sync_page(page: Path, skill: str, ids: list[str], available: Callable[[str], bool], now: str,
              showcase: tuple[choreography.Entry, ...] | list[choreography.Entry] = ()) -> int:
    text: str = page.read_text(encoding='utf-8')
    changed: int = 0
    groups: dict[str, list[choreography.Entry]] = showcase_groups(showcase)
    shared: set[str] = {target for target in groups if adaptation_heading_id(target) in ids}
    for identifier in ids:
        match: re.Match[str] | None = heading(identifier).search(text)
        if match is None:
            raise HeadingNotFound(identifier + ' has no heading in ' + str(page))
        if not available(identifier):
            continue
        name: str = match.group('name')
        target: str = match.group(0).removeprefix('### ')
        extra: list[choreography.Entry] = [entry for entry in groups.get(target, []) if available(entry.id)] if target in shared else []
        replaced: str | None = replace_blocks(text, match.end(), (EXISTING_BLOCKS if extra else EXISTING_BLOCK).match(text, match.end()),
                                              lambda labels: '\n\n' + block(skill, identifier, name, labels).rstrip('\n')
                                              + ''.join('\n\n' + block(skill, entry.id, entry.showcase.title, labels).rstrip('\n') for entry in extra))
        if replaced is not None:
            text = replaced
            changed += 1
    for target, group in groups.items():
        clips: list[choreography.Entry] = [entry for entry in group if available(entry.id)]
        if not clips or target in shared:
            continue
        match = showcase_heading(text, target, page)
        replaced = replace_blocks(text, match.end(), EXISTING_BLOCKS.match(text, match.end()),
                                  lambda labels: ''.join('\n\n' + block(skill, entry.id, entry.showcase.title, labels).rstrip('\n') for entry in clips))
        if replaced is not None:
            text = replaced
            changed += 1
    if changed:
        page.write_text(bump_date(text, now), encoding='utf-8')
    return changed


def utc_now() -> str:
    return datetime.datetime.now(datetime.timezone.utc).strftime('%Y-%m-%dT%H:%M:%S.000Z')


def clips_present(demos: Path, identifier: str) -> bool:
    return (demos / (identifier + '-pov.webm')).is_file() and (demos / (identifier + '-observer.webm')).is_file()


def sheet_entries(skill: str) -> dict[str, choreography.Entry]:
    try:
        return choreography.load_skill(ROOT, skill).entries
    except FileNotFoundError:
        return {}


def copy_stills(output: Path, demos: Path, entries: dict[str, choreography.Entry]) -> int:
    copied: int = 0
    for entry in entries.values():
        for still in entry.stills:
            source: Path = manifest.still_path(output, entry.id, still.name)
            target: Path = demos / source.name
            if not source.is_file() or (target.is_file() and target.read_bytes() == source.read_bytes()):
                continue
            demos.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, target)
            copied += 1
    return copied


def main(argv: list[str] | None = None) -> int:
    parser: argparse.ArgumentParser = argparse.ArgumentParser(description='Embed exported Adapt demo clips under their adaptation headings in a docs skill page.')
    parser.add_argument('--skill', required=True)
    parser.add_argument('--docs', default=str(ROOT.parent / 'docs'))
    parser.add_argument('--output', default='')
    args: argparse.Namespace = parser.parse_args(argv)
    docs: Path = Path(args.docs)
    skill_ids: set[str] = choreography.adaptation_ids(ROOT, args.skill)
    if not skill_ids:
        print('Skill ' + args.skill + ' has no adaptations in adaptation-matrix.json')
        return 2
    try:
        page: Path = skill_page(docs, skill_ids)
    except PageNotFound as error:
        print('Skill ' + args.skill + ': ' + str(error))
        return 2
    demos: Path = docs / 'adapt-assets' / 'demos' / args.skill
    entries: dict[str, choreography.Entry] = sheet_entries(args.skill)
    showcase: list[choreography.Entry] = choreography.showcase_entries(entries)
    showcase_ids: set[str] = {entry.id for entry in showcase}
    ids: list[str] = sorted({clip.name.removesuffix('-pov.webm') for clip in demos.glob('*-pov.webm')} - showcase_ids)
    try:
        changed: int = sync_page(page, args.skill, ids, functools.partial(clips_present, demos), utc_now(), showcase)
    except (HeadingNotFound, HeadingAmbiguous) as error:
        print(str(error))
        return 2
    print('updated ' + str(changed) + ' block(s) in ' + str(page))
    copied: int = copy_stills((Path(args.output) if args.output else ROOT / 'build' / 'demo' / args.skill).resolve(), demos, entries)
    if copied:
        print('copied ' + str(copied) + ' still(s) to ' + str(demos))
    return 0


if __name__ == '__main__':
    sys.exit(main())
