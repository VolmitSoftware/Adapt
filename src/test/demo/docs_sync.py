import argparse
import datetime
import functools
import re
import sys
from pathlib import Path
from typing import Callable

import choreography

ROOT: Path = Path(__file__).resolve().parents[3]
FRONTMATTER: re.Pattern[str] = re.compile(r'\A---\n.*?\n---\n', re.DOTALL)
DATE_LINE: re.Pattern[str] = re.compile(r'^date: .*$', re.MULTILINE)
EXISTING_BLOCK: re.Pattern[str] = re.compile(r'\n\n<div class="adapt-demo">\n(?:<video [^\n]*\n)*</div>(?=\n|\Z)')


class HeadingNotFound(RuntimeError):
    pass


class PageNotFound(RuntimeError):
    pass


def block(skill: str, identifier: str) -> str:
    base: str = '/adapt-assets/demos/' + skill + '/' + identifier
    return ('<div class="adapt-demo">\n'
            '<video src="' + base + '-pov.webm" autoplay muted loop playsinline controls preload="metadata"></video>\n'
            '<video src="' + base + '-observer.webm" autoplay muted loop playsinline controls preload="metadata"></video>\n'
            '</div>\n')


def heading(identifier: str) -> re.Pattern[str]:
    return re.compile(r'^### .*\(`' + re.escape(identifier) + r'`\)[ \t]*$', re.MULTILINE)


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


def sync_page(page: Path, skill: str, ids: list[str], available: Callable[[str], bool], now: str) -> int:
    text: str = page.read_text(encoding='utf-8')
    changed: int = 0
    for identifier in ids:
        match: re.Match[str] | None = heading(identifier).search(text)
        if match is None:
            raise HeadingNotFound(identifier + ' has no heading in ' + str(page))
        if not available(identifier):
            continue
        fresh: str = '\n\n' + block(skill, identifier).rstrip('\n')
        start: int = match.end()
        end: int = start
        existing: re.Match[str] | None = EXISTING_BLOCK.match(text, start)
        if existing is not None:
            if existing.group(0) == fresh:
                continue
            end = existing.end()
        text = text[:start] + fresh + text[end:]
        changed += 1
    if changed:
        page.write_text(bump_date(text, now), encoding='utf-8')
    return changed


def utc_now() -> str:
    return datetime.datetime.now(datetime.timezone.utc).strftime('%Y-%m-%dT%H:%M:%S.000Z')


def clips_present(demos: Path, identifier: str) -> bool:
    return (demos / (identifier + '-pov.webm')).is_file() and (demos / (identifier + '-observer.webm')).is_file()


def main(argv: list[str] | None = None) -> int:
    parser: argparse.ArgumentParser = argparse.ArgumentParser(description='Embed exported Adapt demo clips under their adaptation headings in a docs skill page.')
    parser.add_argument('--skill', required=True)
    parser.add_argument('--docs', default=str(ROOT.parent / 'docs'))
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
    ids: list[str] = sorted({clip.name.removesuffix('-pov.webm') for clip in demos.glob('*-pov.webm')})
    try:
        changed: int = sync_page(page, args.skill, ids, functools.partial(clips_present, demos), utc_now())
    except HeadingNotFound as error:
        print(str(error))
        return 2
    print('updated ' + str(changed) + ' block(s) in ' + str(page))
    return 0


if __name__ == '__main__':
    sys.exit(main())
