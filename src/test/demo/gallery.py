import datetime
import html
import os
import urllib.parse
from pathlib import Path

import evidence
import export_phase
import manifest

STYLE: str = ('<style>body{font-family:system-ui;margin:24px;background:#111;color:#eee}section{margin-bottom:32px}'
              'section.failed h2{color:#f66}video{margin-right:8px;background:#000}small{font-weight:normal;color:#aaa}'
              'code.ids{user-select:all}a{color:#8cf}</style>')
INDEX_STATUSES: tuple[str, ...] = ('exported', 'recorded', 'failed', 'skipped')


def videos(identifier: str, take: manifest.Take, docs_root: Path, thumbs: Path) -> str:
    demos: Path = docs_root.absolute() / 'adapt-assets' / 'demos' / take.skill
    tags: list[str] = []
    for angle in export_phase.ANGLES:
        clip: Path = demos / (identifier + '-' + angle + '.webm')
        poster: Path = thumbs.absolute() / (identifier + '-' + angle + '.png')
        tags.append('<video src="' + html.escape(clip.as_uri()) + '" poster="' + html.escape(poster.as_uri())
                    + '" controls muted loop playsinline width="480"></video>')
    return ''.join(tags)


def evidence_facts(take: manifest.Take) -> list[str]:
    if not take.visual:
        return ['evidence ' + ('ok' if take.evidence_ok else 'missing'), take.evidence_detail]
    label: str = evidence.visual_detail(take.visual)
    return [label] if take.evidence_detail == label else [label, take.evidence_detail]


def section(identifier: str, take: manifest.Take, docs_root: Path, thumbs: Path) -> str:
    facts: list[str] = [take.status, 'set ' + take.set, 'level ' + str(take.level), 'takes ' + str(take.takes)]
    if take.stop_tick > take.start_tick >= 0:
        facts.append(format(manifest.clip_seconds(take), '.1f') + ' s')
    facts.extend(evidence_facts(take))
    if take.export_error:
        facts.append('export error: ' + take.export_error)
    body: str = videos(identifier, take, docs_root, thumbs) if take.status == 'exported' else ''
    return ('<section class="' + html.escape(take.status) + '"><h2>' + html.escape(identifier) + ' <small>'
            + html.escape(' · '.join(facts)) + '</small></h2>' + body + '</section>')


def skipped_section(skipped: list[manifest.Take]) -> str:
    if not skipped:
        return ''
    items: str = ''.join('<li><code>' + html.escape(take.id) + '</code> ' + html.escape(take.evidence_detail) + '</li>' for take in skipped)
    return '<section class="skipped"><h2>Skipped</h2><ul>' + items + '</ul></section>'


def write(path: Path, takes: dict[str, manifest.Take], docs_root: Path, thumbs: Path, plate: str = '') -> None:
    ordered: list[str] = sorted(takes)
    failed: list[str] = [identifier for identifier in ordered if takes[identifier].status == 'failed']
    shown: list[str] = [identifier for identifier in ordered if takes[identifier].status != 'skipped']
    skipped: list[manifest.Take] = [takes[identifier] for identifier in ordered if takes[identifier].status == 'skipped']
    page: str = ('<!doctype html><meta charset="utf-8"><title>Adapt demo review</title>' + STYLE
                 + '<h1>Adapt demo review</h1>' + ('<p>Plate origin ' + html.escape(plate) + '</p>' if plate else '')
                 + '<p>Failed ids: <code class="ids">' + html.escape(','.join(failed) or 'none') + '</code></p>'
                 + ''.join(section(identifier, takes[identifier], docs_root, thumbs) for identifier in shown)
                 + skipped_section(skipped))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(page, encoding='utf-8')


def status_counts(manifest_path: Path) -> str:
    try:
        statuses: list[str] = [take.status for take in manifest.load(manifest_path).values()]
    except (AttributeError, OSError, TypeError, ValueError):
        return ''
    return ' · '.join(str(statuses.count(status)) + ' ' + status for status in INDEX_STATUSES if status in statuses)


def index_row(page: Path) -> str:
    skill: str = page.parent.name
    written: str = datetime.datetime.fromtimestamp(page.stat().st_mtime).strftime('%Y-%m-%d %H:%M')
    facts: list[str] = [fact for fact in (status_counts(page.parent / 'manifest.json'), 'updated ' + written) if fact]
    return ('<li><a href="' + html.escape(urllib.parse.quote(skill) + '/gallery.html') + '">' + html.escape(skill) + '</a> <small>'
            + html.escape(' · '.join(facts)) + '</small></li>')


def write_index(path: Path) -> None:
    rows: list[str] = [index_row(page) for page in sorted(path.parent.glob('*/gallery.html'))]
    body: str = '<ul>' + ''.join(rows) + '</ul>' if rows else '<p>No galleries yet.</p>'
    page: str = '<!doctype html><meta charset="utf-8"><title>Adapt demo galleries</title>' + STYLE + '<h1>Adapt demo galleries</h1>' + body
    path.parent.mkdir(parents=True, exist_ok=True)
    staging: Path = path.with_name(path.name + '.' + str(os.getpid()) + '.tmp')
    staging.write_text(page, encoding='utf-8')
    staging.replace(path)
