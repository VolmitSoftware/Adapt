import dataclasses
import io
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / 'src' / 'test' / 'demo'))
import choreography
import docs_sync

PAGE = """---
title: "Skill - Agility"
description: "x"
published: true
date: 2026-09-28T10:36:37.000Z
tags: "adapt"
editor: markdown
dateCreated: 2026-08-09T00:00:00.000Z
---

## Adaptations

### Wind Up (`agility-wind-up`)

5 levels

Body text.

### Wall Jump (`agility-wall-jump`)

5 levels

More text.
"""

PICKAXE_PAGE = """---
title: "Skill - Pickaxes"
date: 2026-09-28T10:36:37.000Z
---

## Adaptations

### Ore Chisel (`pickaxe-chisel`)

5 levels

### Veinminer (`pickaxe-veinminer`)

5 levels
"""


class SkillPageTest(unittest.TestCase):
    def docs(self, tmp: str, pages: dict[str, str]) -> Path:
        docs: Path = Path(tmp)
        (docs / 'adapt').mkdir()
        for name, body in pages.items():
            (docs / 'adapt' / name).write_text(body)
        return docs

    def test_page_with_the_most_adaptation_headings_is_chosen(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            docs: Path = self.docs(tmp, {'25-skill-pickaxes.md': PICKAXE_PAGE, '11-skill-agility.md': PAGE,
                                         '10-skills-catalog.md': '### Ore Chisel (`pickaxe-chisel`)\n### Veinminer (`pickaxe-veinminer`)\n',
                                         '12-skill-mixed.md': '### Ore Chisel (`pickaxe-chisel`)\n'})
            page: Path = docs_sync.skill_page(docs, {'pickaxe-chisel', 'pickaxe-veinminer', 'pickaxe-autosmelt'})
        self.assertEqual(page.name, '25-skill-pickaxes.md')

    def test_mention_outside_a_heading_does_not_count(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            docs: Path = self.docs(tmp, {'25-skill-pickaxes.md': PICKAXE_PAGE,
                                         '26-skill-ranged.md': 'See `pickaxe-chisel` and `pickaxe-veinminer`.\n### Ore Chisel (`pickaxe-chisel`)\n'})
            page: Path = docs_sync.skill_page(docs, {'pickaxe-chisel', 'pickaxe-veinminer'})
        self.assertEqual(page.name, '25-skill-pickaxes.md')

    def test_no_page_with_a_heading_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            docs: Path = self.docs(tmp, {'11-skill-agility.md': PAGE})
            with self.assertRaisesRegex(docs_sync.PageNotFound, 'No .*-skill-.*md page .* pickaxe-chisel'):
                docs_sync.skill_page(docs, {'pickaxe-chisel'})

    def test_tied_pages_fail_and_name_both(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            docs: Path = self.docs(tmp, {'25-skill-pickaxes.md': PICKAXE_PAGE, '26-skill-copy.md': PICKAXE_PAGE})
            with self.assertRaisesRegex(docs_sync.PageNotFound, '25-skill-pickaxes.md.*26-skill-copy.md'):
                docs_sync.skill_page(docs, {'pickaxe-chisel', 'pickaxe-veinminer'})


class DocsSyncTest(unittest.TestCase):
    def write_page(self, tmp: str) -> Path:
        page = Path(tmp) / '11-skill-agility.md'
        page.write_text(PAGE)
        return page

    def test_inserts_block_under_heading_and_bumps_date(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            page = self.write_page(tmp)
            changed = docs_sync.sync_page(page, 'agility', ['agility-wall-jump'], lambda i: True, '2026-09-30T12:00:00.000Z')
            text = page.read_text()
        self.assertEqual(changed, 1)
        self.assertIn('date: 2026-09-30T12:00:00.000Z', text)
        self.assertIn('### Wall Jump (`agility-wall-jump`)\n\n<div class="adapt-demo">', text)
        self.assertIn('<video aria-label="First-person demonstration of Wall Jump" src="/adapt-assets/demos/agility/agility-wall-jump-pov.webm" '
                      'muted loop playsinline controls preload="none"></video>\n', text)
        self.assertIn('<video aria-label="Third-person demonstration of Wall Jump" src="/adapt-assets/demos/agility/agility-wall-jump-observer.webm" '
                      'muted loop playsinline controls preload="none"></video>\n', text)
        self.assertNotIn('agility-wind-up-pov', text)

    def test_second_run_is_idempotent(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            page = self.write_page(tmp)
            docs_sync.sync_page(page, 'agility', ['agility-wall-jump'], lambda i: True, '2026-09-30T12:00:00.000Z')
            first = page.read_text()
            changed = docs_sync.sync_page(page, 'agility', ['agility-wall-jump'], lambda i: True, '2026-09-30T13:00:00.000Z')
            second = page.read_text()
        self.assertEqual(changed, 0)
        self.assertEqual(first, second)
        self.assertEqual(second.count('adapt-demo'), 1)

    def test_skips_ids_without_clips(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            page = self.write_page(tmp)
            changed = docs_sync.sync_page(page, 'agility', ['agility-wall-jump', 'agility-wind-up'], lambda i: i == 'agility-wind-up', '2026-09-30T12:00:00.000Z')
            text = page.read_text()
        self.assertEqual(changed, 1)
        self.assertIn('agility-wind-up-pov', text)
        self.assertNotIn('agility-wall-jump-pov', text)

    def test_missing_heading_is_reported(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            page = self.write_page(tmp)
            with self.assertRaises(docs_sync.HeadingNotFound):
                docs_sync.sync_page(page, 'agility', ['agility-nope'], lambda i: True, '2026-09-30T12:00:00.000Z')

    def test_missing_heading_leaves_page_untouched(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            page = self.write_page(tmp)
            with self.assertRaises(docs_sync.HeadingNotFound):
                docs_sync.sync_page(page, 'agility', ['agility-wall-jump', 'agility-nope'], lambda i: True, '2026-09-30T12:00:00.000Z')
            text = page.read_text()
        self.assertEqual(text, PAGE)

    def test_only_block_and_date_change(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            page = self.write_page(tmp)
            docs_sync.sync_page(page, 'agility', ['agility-wind-up', 'agility-wall-jump'], lambda i: True, '2026-09-30T12:00:00.000Z')
            text = page.read_text()
        restored = text.replace('date: 2026-09-30T12:00:00.000Z', 'date: 2026-09-28T10:36:37.000Z')
        for identifier, title in (('agility-wind-up', 'Wind Up'), ('agility-wall-jump', 'Wall Jump')):
            restored = restored.replace('\n\n' + docs_sync.block('agility', identifier, title).rstrip('\n'), '', 1)
        self.assertEqual(restored, PAGE)

    def test_block_sits_between_single_blank_lines(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            page = self.write_page(tmp)
            docs_sync.sync_page(page, 'agility', ['agility-wall-jump'], lambda i: True, '2026-09-30T12:00:00.000Z')
            text = page.read_text()
        self.assertIn('### Wall Jump (`agility-wall-jump`)\n\n' + docs_sync.block('agility', 'agility-wall-jump', 'Wall Jump') + '\n5 levels\n', text)

    def test_heading_on_last_line_is_idempotent(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            page = self.write_page(tmp)
            page.write_text(PAGE + '\n### Kip-Up (`agility-kip-up`)')
            first_changed = docs_sync.sync_page(page, 'agility', ['agility-kip-up'], lambda i: True, '2026-09-30T12:00:00.000Z')
            first = page.read_text()
            second_changed = docs_sync.sync_page(page, 'agility', ['agility-kip-up'], lambda i: True, '2026-09-30T13:00:00.000Z')
            second = page.read_text()
        self.assertEqual((first_changed, second_changed), (1, 0))
        self.assertEqual(first, second)
        self.assertTrue(second.endswith('### Kip-Up (`agility-kip-up`)\n\n' + docs_sync.block('agility', 'agility-kip-up', 'Kip-Up').rstrip('\n')))

    def test_stale_block_is_replaced(self) -> None:
        stale = '### Wall Jump (`agility-wall-jump`)\n\n<div class="adapt-demo">\n<video src="/old.webm"></video>\n</div>\n'
        with tempfile.TemporaryDirectory() as tmp:
            page = self.write_page(tmp)
            page.write_text(PAGE.replace('### Wall Jump (`agility-wall-jump`)\n', stale))
            changed = docs_sync.sync_page(page, 'agility', ['agility-wall-jump'], lambda i: True, '2026-09-30T12:00:00.000Z')
            text = page.read_text()
        self.assertEqual(changed, 1)
        self.assertNotIn('/old.webm', text)
        self.assertEqual(text.count('adapt-demo'), 1)
        self.assertIn('</div>\n\n5 levels\n\nMore text.', text)

    def test_block_without_preload_is_replaced(self) -> None:
        base: str = '/adapt-assets/demos/agility/agility-wall-jump'
        old: str = ('### Wall Jump (`agility-wall-jump`)\n\n<div class="adapt-demo">\n'
                    '<video src="' + base + '-pov.webm" controls muted loop playsinline></video>\n'
                    '<video src="' + base + '-observer.webm" controls muted loop playsinline></video>\n'
                    '</div>\n')
        with tempfile.TemporaryDirectory() as tmp:
            page: Path = self.write_page(tmp)
            page.write_text(PAGE.replace('### Wall Jump (`agility-wall-jump`)\n', old))
            first_changed: int = docs_sync.sync_page(page, 'agility', ['agility-wall-jump'], lambda i: True, '2026-09-30T12:00:00.000Z')
            second_changed: int = docs_sync.sync_page(page, 'agility', ['agility-wall-jump'], lambda i: True, '2026-09-30T13:00:00.000Z')
            text: str = page.read_text()
        self.assertEqual((first_changed, second_changed), (1, 0))
        self.assertEqual(text.count('preload="none"'), 2)
        self.assertEqual(text.count('<video '), 2)
        self.assertIn('</div>\n\n5 levels\n\nMore text.', text)

    def test_date_outside_frontmatter_is_untouched(self) -> None:
        body_date = PAGE.replace('date: 2026-09-28T10:36:37.000Z\n', '').replace('More text.', 'date: keep me')
        with tempfile.TemporaryDirectory() as tmp:
            page = self.write_page(tmp)
            page.write_text(body_date)
            docs_sync.sync_page(page, 'agility', ['agility-wall-jump'], lambda i: True, '2026-09-30T12:00:00.000Z')
            text = page.read_text()
        self.assertIn('date: keep me', text)
        self.assertNotIn('2026-09-30T12:00:00.000Z', text)

    def test_main_embeds_ids_with_both_clips(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            docs = Path(tmp)
            (docs / 'adapt').mkdir()
            page = docs / 'adapt' / '11-skill-agility.md'
            page.write_text(PAGE)
            demos = docs / 'adapt-assets' / 'demos' / 'agility'
            demos.mkdir(parents=True)
            for name in ('agility-wall-jump-pov.webm', 'agility-wall-jump-observer.webm', 'agility-wind-up-pov.webm'):
                (demos / name).write_bytes(b'')
            with mock.patch('sys.stdout', new_callable=io.StringIO) as stdout:
                code = docs_sync.main(['--skill', 'agility', '--docs', str(docs)])
            text = page.read_text()
        self.assertEqual(code, 0)
        self.assertIn('updated 1 block(s)', stdout.getvalue())
        self.assertIn('agility-wall-jump-pov.webm', text)
        self.assertNotIn('agility-wind-up-pov.webm', text)

    def test_main_never_embeds_skipped_adaptation_without_clips(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            docs = Path(tmp)
            (docs / 'adapt').mkdir()
            page = docs / 'adapt' / '11-skill-agility.md'
            page.write_text(PAGE + '\n### Marathoner (`agility-marathoner`)\n\n5 levels\n')
            demos = docs / 'adapt-assets' / 'demos' / 'agility'
            demos.mkdir(parents=True)
            for name in ('agility-wall-jump-pov.webm', 'agility-wall-jump-observer.webm'):
                (demos / name).write_bytes(b'')
            with mock.patch('sys.stdout', new_callable=io.StringIO):
                code = docs_sync.main(['--skill', 'agility', '--docs', str(docs)])
            text = page.read_text()
        self.assertEqual(code, 0)
        self.assertNotIn('agility-marathoner-pov', text)
        self.assertTrue(text.endswith('### Marathoner (`agility-marathoner`)\n\n5 levels\n'))

    def test_main_finds_the_page_by_its_adaptation_headings_when_the_file_name_differs(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            docs: Path = Path(tmp)
            (docs / 'adapt').mkdir()
            (docs / 'adapt' / '11-skill-agility.md').write_text(PAGE)
            (docs / 'adapt' / '24-skill-pickaxe.md').write_text('## Adaptations\n\n### Not the pickaxe page (`agility-wind-up`)\n')
            page: Path = docs / 'adapt' / '25-skill-pickaxes.md'
            page.write_text(PICKAXE_PAGE)
            demos: Path = docs / 'adapt-assets' / 'demos' / 'pickaxe'
            demos.mkdir(parents=True)
            for name in ('pickaxe-chisel-pov.webm', 'pickaxe-chisel-observer.webm'):
                (demos / name).write_bytes(b'')
            with mock.patch('sys.stdout', new_callable=io.StringIO) as stdout:
                code: int = docs_sync.main(['--skill', 'pickaxe', '--docs', str(docs)])
            text: str = page.read_text()
            decoy: str = (docs / 'adapt' / '24-skill-pickaxe.md').read_text()
        self.assertEqual(code, 0)
        self.assertIn('updated 1 block(s) in ' + str(page), stdout.getvalue())
        self.assertIn('/adapt-assets/demos/pickaxe/pickaxe-chisel-pov.webm', text)
        self.assertNotIn('adapt-demo', decoy)

    def test_main_rejects_a_skill_without_adaptations(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            (Path(tmp) / 'adapt').mkdir()
            (Path(tmp) / 'adapt' / '11-skill-agility.md').write_text(PAGE)
            with mock.patch('sys.stdout', new_callable=io.StringIO) as stdout:
                code: int = docs_sync.main(['--skill', 'nope', '--docs', tmp])
        self.assertEqual(code, 2)
        self.assertIn('Skill nope has no adaptations in adaptation-matrix.json', stdout.getvalue())

    def test_main_rejects_missing_page(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            with mock.patch('sys.stdout', new_callable=io.StringIO) as stdout:
                code = docs_sync.main(['--skill', 'agility', '--docs', tmp])
        self.assertEqual(code, 2)
        self.assertIn('agility', stdout.getvalue())

    def test_main_reports_missing_heading(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            docs = Path(tmp)
            (docs / 'adapt').mkdir()
            (docs / 'adapt' / '11-skill-agility.md').write_text(PAGE)
            demos = docs / 'adapt-assets' / 'demos' / 'agility'
            demos.mkdir(parents=True)
            for name in ('agility-nope-pov.webm', 'agility-nope-observer.webm'):
                (demos / name).write_bytes(b'')
            with mock.patch('sys.stdout', new_callable=io.StringIO) as stdout:
                code = docs_sync.main(['--skill', 'agility', '--docs', str(docs)])
        self.assertEqual(code, 2)
        self.assertIn('agility-nope', stdout.getvalue())


LABELLED: str = """### Wall Jump (`agility-wall-jump`)

<div class="adapt-demo">
<video aria-label="First-person demonstration of Wall Jump" src="/adapt-assets/demos/agility/agility-wall-jump-pov.webm" muted loop playsinline controls preload="none"></video>
<video aria-label="Third-person demonstration of Wall Jump" src="/adapt-assets/demos/agility/agility-wall-jump-observer.webm" muted loop playsinline controls preload="none"></video>
</div>
"""


class AriaLabelTest(unittest.TestCase):
    def sync(self, page_text: str, identifier: str = 'agility-wall-jump', now: str = '2026-09-30T12:00:00.000Z') -> tuple[str, int]:
        with tempfile.TemporaryDirectory() as tmp:
            page: Path = Path(tmp) / '11-skill-agility.md'
            page.write_text(page_text)
            changed: int = docs_sync.sync_page(page, 'agility', [identifier], lambda i: True, now)
            return page.read_text(), changed

    def test_blocks_name_both_angles_after_the_heading_name(self) -> None:
        text, _ = self.sync(PAGE)
        self.assertIn('aria-label="First-person demonstration of Wall Jump" src="/adapt-assets/demos/agility/agility-wall-jump-pov.webm"', text)
        self.assertIn('aria-label="Third-person demonstration of Wall Jump" src="/adapt-assets/demos/agility/agility-wall-jump-observer.webm"', text)

    def test_generated_block_matches_the_published_page_markup(self) -> None:
        self.assertEqual(docs_sync.block('agility', 'agility-wall-jump', 'Wall Jump'), LABELLED.split('\n\n', 1)[1])

    def test_rerun_over_a_labelled_block_is_byte_identical(self) -> None:
        page: str = PAGE.replace('### Wall Jump (`agility-wall-jump`)\n', LABELLED)
        text, changed = self.sync(page)
        self.assertEqual(changed, 0)
        self.assertEqual(text, page)

    def test_existing_labels_survive_when_the_block_is_refreshed(self) -> None:
        custom: str = LABELLED.replace('First-person demonstration of Wall Jump', 'Custom first label').replace('-observer.webm', '-observer.webm?v=1')
        page: str = PAGE.replace('### Wall Jump (`agility-wall-jump`)\n', custom)
        text, changed = self.sync(page)
        self.assertEqual(changed, 1)
        self.assertIn('aria-label="Custom first label" src="/adapt-assets/demos/agility/agility-wall-jump-pov.webm"', text)
        self.assertIn('aria-label="Third-person demonstration of Wall Jump" src="/adapt-assets/demos/agility/agility-wall-jump-observer.webm"', text)
        again, repeated = self.sync(text)
        self.assertEqual((repeated, again), (0, text))

    def test_a_hand_edited_label_is_kept_and_the_block_is_not_rewritten(self) -> None:
        page: str = PAGE.replace('### Wall Jump (`agility-wall-jump`)\n', LABELLED.replace('Third-person demonstration of Wall Jump', 'Camera orbiting the jump'))
        text, changed = self.sync(page)
        self.assertEqual((changed, text), (0, page))

    def test_unlabelled_blocks_gain_labels_once(self) -> None:
        base: str = '/adapt-assets/demos/agility/agility-wall-jump'
        old: str = ('### Wall Jump (`agility-wall-jump`)\n\n<div class="adapt-demo">\n<video src="' + base + '-pov.webm" muted loop playsinline controls preload="none"></video>\n'
                    '<video src="' + base + '-observer.webm" muted loop playsinline controls preload="none"></video>\n</div>\n')
        text, changed = self.sync(PAGE.replace('### Wall Jump (`agility-wall-jump`)\n', old))
        self.assertEqual(changed, 1)
        self.assertEqual(text.count('aria-label='), 2)
        self.assertEqual(self.sync(text)[1], 0)

    def test_names_are_html_escaped_like_the_published_pages(self) -> None:
        page: str = PAGE.replace('### Wall Jump (`agility-wall-jump`)', "### Mason's Harvest & Replant (`agility-wall-jump`)")
        text, _ = self.sync(page)
        self.assertIn('aria-label="First-person demonstration of Mason&#x27;s Harvest &amp; Replant"', text)

    def test_names_with_parentheses_keep_them(self) -> None:
        page: str = PAGE.replace('### Wall Jump (`agility-wall-jump`)', '### Armor (Up) (`agility-wall-jump`)')
        text, _ = self.sync(page)
        self.assertIn('aria-label="Third-person demonstration of Armor (Up)"', text)


def clip(identifier: str, title: str, heading: str) -> choreography.Entry:
    return choreography.Entry(id=identifier, set='arena', level=0, pre=[], beats=[], expect_sound=None, expect_particle=None, max_ticks=100,
                              camera=None, showcase=choreography.Showcase(title=title, heading=heading))


SHOWCASE_PAGE: str = """---
title: "Skill - Golemancy"
date: 2026-09-28T10:36:37.000Z
---

## Animus Plinth

The plinth is a lodestone.

### Infusion recipes

Table.

### Golem GUI

Menu.

### Frost Frame (`golem-frost-frame`)

Snow.
"""
RITUAL: choreography.Entry = clip('golemancy-plinth-ritual', 'Plinth ritual', 'Animus Plinth')
INFUSION: choreography.Entry = clip('golemancy-infusion', 'Infusion', 'Animus Plinth')
GUI: choreography.Entry = clip('golemancy-gui', 'Golem menu', 'Golem GUI')


class ShowcaseSyncTest(unittest.TestCase):
    def sync(self, page_text: str, showcase: list[choreography.Entry], ids: list[str] | None = None, now: str = '2026-09-30T12:00:00.000Z',
             available: set[str] | None = None) -> tuple[str, int]:
        with tempfile.TemporaryDirectory() as tmp:
            page: Path = Path(tmp) / '34-skill-golemancy.md'
            page.write_text(page_text)
            changed: int = docs_sync.sync_page(page, 'golemancy', ids or [], lambda i: available is None or i in available, now, showcase)
            return page.read_text(), changed

    def test_clip_pair_sits_directly_under_a_double_hash_heading(self) -> None:
        text, changed = self.sync(SHOWCASE_PAGE, [RITUAL])
        self.assertEqual(changed, 1)
        self.assertIn('## Animus Plinth\n\n' + docs_sync.block('golemancy', RITUAL.id, 'Plinth ritual') + '\nThe plinth is a lodestone.', text)
        self.assertIn('aria-label="First-person demonstration of Plinth ritual" src="/adapt-assets/demos/golemancy/golemancy-plinth-ritual-pov.webm"', text)
        self.assertIn('date: 2026-09-30T12:00:00.000Z', text)

    def test_clip_pair_sits_directly_under_a_triple_hash_heading(self) -> None:
        text, _ = self.sync(SHOWCASE_PAGE, [GUI])
        self.assertIn('### Golem GUI\n\n' + docs_sync.block('golemancy', GUI.id, 'Golem menu') + '\nMenu.', text)

    def test_clips_under_one_heading_follow_sheet_order(self) -> None:
        text, _ = self.sync(SHOWCASE_PAGE, [INFUSION, RITUAL])
        self.assertLess(text.index('golemancy-infusion-pov.webm'), text.index('golemancy-plinth-ritual-pov.webm'))
        reversed_text, _ = self.sync(SHOWCASE_PAGE, [RITUAL, INFUSION])
        self.assertLess(reversed_text.index('golemancy-plinth-ritual-pov.webm'), reversed_text.index('golemancy-infusion-pov.webm'))
        self.assertEqual(text.count('<div class="adapt-demo">'), 2)

    def test_rerun_is_byte_identical_for_one_clip_and_for_several(self) -> None:
        for showcase in ([RITUAL], [INFUSION, RITUAL, GUI]):
            first, _ = self.sync(SHOWCASE_PAGE, showcase)
            second, changed = self.sync(first, showcase, now='2026-10-01T00:00:00.000Z')
            self.assertEqual((changed, second), (0, first))

    def test_a_dropped_clip_is_removed_from_its_run(self) -> None:
        first, _ = self.sync(SHOWCASE_PAGE, [INFUSION, RITUAL])
        second, changed = self.sync(first, [INFUSION, RITUAL], available={'golemancy-plinth-ritual'})
        self.assertEqual(changed, 1)
        self.assertNotIn('golemancy-infusion', second)
        self.assertEqual(second.count('<div class="adapt-demo">'), 1)

    def test_labels_of_an_existing_run_are_kept(self) -> None:
        first, _ = self.sync(SHOWCASE_PAGE, [RITUAL])
        edited: str = first.replace('Third-person demonstration of Plinth ritual', 'Orbiting the plinth')
        second, changed = self.sync(edited, [RITUAL])
        self.assertEqual((changed, second), (0, edited))

    def test_clips_that_are_not_available_leave_the_page_alone(self) -> None:
        text, changed = self.sync(SHOWCASE_PAGE, [RITUAL], available=set())
        self.assertEqual((changed, text), (0, SHOWCASE_PAGE))

    def test_missing_heading_is_reported_and_leaves_the_page_untouched(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            page: Path = Path(tmp) / '34-skill-golemancy.md'
            page.write_text(SHOWCASE_PAGE)
            with self.assertRaisesRegex(docs_sync.HeadingNotFound, 'Showcase heading "Nowhere"'):
                docs_sync.sync_page(page, 'golemancy', [], lambda i: True, 'now', [RITUAL, clip('golemancy-lost', 'Lost', 'Nowhere')])
            self.assertEqual(page.read_text(), SHOWCASE_PAGE)

    def test_the_heading_must_match_exactly_at_level_two_or_three(self) -> None:
        for page_text in ('#### Animus Plinth\n', '# Animus Plinth\n', '## Animus Plinth and more\n', 'Animus Plinth\n', '## Animus plinth\n'):
            with self.subTest(page_text), tempfile.TemporaryDirectory() as tmp:
                page: Path = Path(tmp) / '34-skill-golemancy.md'
                page.write_text(page_text)
                with self.assertRaises(docs_sync.HeadingNotFound):
                    docs_sync.sync_page(page, 'golemancy', [], lambda i: True, 'now', [RITUAL])

    def test_a_heading_that_appears_twice_is_ambiguous(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            page: Path = Path(tmp) / '34-skill-golemancy.md'
            page.write_text(SHOWCASE_PAGE + '\n### Animus Plinth\n')
            with self.assertRaisesRegex(docs_sync.HeadingAmbiguous, 'appears 2 times'):
                docs_sync.sync_page(page, 'golemancy', [], lambda i: True, 'now', [RITUAL])

    def test_adaptation_blocks_and_showcase_blocks_coexist(self) -> None:
        text, changed = self.sync(SHOWCASE_PAGE, [RITUAL], ids=['golem-frost-frame'])
        self.assertEqual(changed, 2)
        self.assertIn('### Frost Frame (`golem-frost-frame`)\n\n' + docs_sync.block('golemancy', 'golem-frost-frame', 'Frost Frame') + '\nSnow.', text)
        again, repeated = self.sync(text, [RITUAL], ids=['golem-frost-frame'])
        self.assertEqual((repeated, again), (0, text))

    def test_a_showcase_clip_under_an_adaptation_heading_follows_that_adaptations_clips(self) -> None:
        node: choreography.Entry = clip('golemancy-node-config', 'Node clicks', 'Frost Frame (`golem-frost-frame`)')
        text, changed = self.sync(SHOWCASE_PAGE, [node], ids=['golem-frost-frame'])
        self.assertEqual(changed, 1)
        self.assertIn('### Frost Frame (`golem-frost-frame`)\n\n' + docs_sync.block('golemancy', 'golem-frost-frame', 'Frost Frame').rstrip('\n')
                      + '\n\n' + docs_sync.block('golemancy', node.id, 'Node clicks').rstrip('\n') + '\n\nSnow.', text)
        again, repeated = self.sync(text, [node], ids=['golem-frost-frame'], now='2026-10-01T00:00:00.000Z')
        self.assertEqual((repeated, again), (0, text))

    def test_a_shared_showcase_clip_is_embedded_when_the_adaptation_clip_is_missing(self) -> None:
        node: choreography.Entry = clip('golemancy-node-config', 'Node clicks', 'Frost Frame (`golem-frost-frame`)')
        text, changed = self.sync(SHOWCASE_PAGE, [node], ids=['golem-frost-frame'], available={'golemancy-node-config'})
        self.assertEqual(changed, 1)
        self.assertIn('### Frost Frame (`golem-frost-frame`)\n\n' + docs_sync.block('golemancy', node.id, 'Node clicks').rstrip('\n') + '\n\nSnow.', text)
        self.assertNotIn('golem-frost-frame-pov.webm', text)
        again, repeated = self.sync(text, [node], ids=['golem-frost-frame'], available={'golemancy-node-config'}, now='2026-10-01T00:00:00.000Z')
        self.assertEqual((repeated, again), (0, text))

    def test_the_adaptation_block_survives_when_only_the_shared_clip_is_available(self) -> None:
        node: choreography.Entry = clip('golemancy-node-config', 'Node clicks', 'Frost Frame (`golem-frost-frame`)')
        both, _ = self.sync(SHOWCASE_PAGE, [node], ids=['golem-frost-frame'])
        text, changed = self.sync(both, [node], ids=['golem-frost-frame'], available={'golemancy-node-config'}, now='2026-10-01T00:00:00.000Z')
        self.assertEqual((changed, text), (0, both))

    def test_a_shared_showcase_clip_keeps_an_existing_adaptation_block_when_the_adaptation_is_not_listed(self) -> None:
        node: choreography.Entry = clip('golemancy-node-config', 'Node clicks', 'Frost Frame (`golem-frost-frame`)')
        both, _ = self.sync(SHOWCASE_PAGE, [node], ids=['golem-frost-frame'])
        text, changed = self.sync(both, [node], ids=[], now='2026-10-01T00:00:00.000Z')
        self.assertEqual((changed, text), (0, both))

    def test_a_padded_adaptation_heading_still_receives_its_shared_showcase_clip(self) -> None:
        node: choreography.Entry = clip('golemancy-node-config', 'Node clicks', 'Frost Frame (`golem-frost-frame`)')
        page: str = SHOWCASE_PAGE.replace('### Frost Frame (`golem-frost-frame`)', '### Frost Frame (`golem-frost-frame`)   ')
        text, changed = self.sync(page, [node], ids=['golem-frost-frame'])
        self.assertEqual(changed, 1)
        self.assertIn(docs_sync.block('golemancy', node.id, 'Node clicks').rstrip('\n') + '\n\nSnow.', text)

    def test_adaptation_clips_alone_leave_the_shared_showcase_blocks_in_place(self) -> None:
        node: choreography.Entry = clip('golemancy-node-config', 'Node clicks', 'Frost Frame (`golem-frost-frame`)')
        text, _ = self.sync(SHOWCASE_PAGE, [node], ids=['golem-frost-frame'])
        again, changed = self.sync(text, [node], ids=['golem-frost-frame'], available={'golem-frost-frame'})
        self.assertEqual((changed, again), (0, text))

    def test_only_blocks_and_the_date_change(self) -> None:
        text, _ = self.sync(SHOWCASE_PAGE, [INFUSION, RITUAL])
        restored: str = text.replace('date: 2026-09-30T12:00:00.000Z', 'date: 2026-09-28T10:36:37.000Z')
        for entry in (INFUSION, RITUAL):
            restored = restored.replace('\n\n' + docs_sync.block('golemancy', entry.id, entry.showcase.title).rstrip('\n'), '', 1)
        self.assertEqual(restored, SHOWCASE_PAGE)


class StillsSyncTest(unittest.TestCase):
    def setUp(self) -> None:
        self.entries: dict[str, choreography.Entry] = {
            RITUAL.id: dataclasses.replace(RITUAL, stills=(choreography.Still(angle='observer', at=3.5, name='plinth-ritual'),
                                                           choreography.Still(angle='pov', at=1.0, name='later'))),
            'golem-frost-frame': choreography.Entry(id='golem-frost-frame', set='arena', level=5, pre=[], beats=[], expect_sound=None, expect_particle=None,
                                                    max_ticks=100, camera=None, stills=(choreography.Still(angle='pov', at=2.0, name='frost'),))}

    def folders(self, tmp: str) -> tuple[Path, Path]:
        output: Path = Path(tmp) / 'build' / 'demo' / 'golemancy'
        (output / 'stills').mkdir(parents=True)
        return output, Path(tmp) / 'docs' / 'adapt-assets' / 'demos' / 'golemancy'

    def test_extracted_stills_are_copied_next_to_the_clips(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            output, demos = self.folders(tmp)
            (output / 'stills' / 'golemancy-plinth-ritual-plinth-ritual.png').write_bytes(b'ritual')
            (output / 'stills' / 'golem-frost-frame-frost.png').write_bytes(b'frost')
            copied: int = docs_sync.copy_stills(output, demos, self.entries)
            names: list[str] = sorted(path.name for path in demos.iterdir())
            payload: bytes = (demos / 'golemancy-plinth-ritual-plinth-ritual.png').read_bytes()
        self.assertEqual(copied, 2)
        self.assertEqual(names, ['golem-frost-frame-frost.png', 'golemancy-plinth-ritual-plinth-ritual.png'])
        self.assertEqual(payload, b'ritual')

    def test_unchanged_stills_are_not_copied_again_and_changed_ones_are(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            output, demos = self.folders(tmp)
            source: Path = output / 'stills' / 'golem-frost-frame-frost.png'
            source.write_bytes(b'frost')
            self.assertEqual(docs_sync.copy_stills(output, demos, self.entries), 1)
            self.assertEqual(docs_sync.copy_stills(output, demos, self.entries), 0)
            source.write_bytes(b'frost 2')
            self.assertEqual(docs_sync.copy_stills(output, demos, self.entries), 1)
            self.assertEqual((demos / 'golem-frost-frame-frost.png').read_bytes(), b'frost 2')

    def test_missing_stills_copy_nothing_and_create_no_folder(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            output, demos = self.folders(tmp)
            self.assertEqual(docs_sync.copy_stills(output, demos, self.entries), 0)
            self.assertFalse(demos.exists())

    def test_stills_are_never_embedded_in_the_page(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            docs: Path = Path(tmp) / 'docs'
            (docs / 'adapt').mkdir(parents=True)
            page: Path = docs / 'adapt' / '34-skill-golemancy.md'
            page.write_text(SHOWCASE_PAGE)
            output, demos = self.folders(tmp)
            demos.mkdir(parents=True)
            for name in ('golemancy-plinth-ritual-pov.webm', 'golemancy-plinth-ritual-observer.webm'):
                (demos / name).write_bytes(b'')
            (output / 'stills' / 'golemancy-plinth-ritual-plinth-ritual.png').write_bytes(b'ritual')
            with mock.patch.object(docs_sync, 'sheet_entries', return_value=self.entries), mock.patch.object(docs_sync.choreography, 'adaptation_ids',
                                                                                                           return_value={'golem-frost-frame'}), \
                    mock.patch('sys.stdout', new_callable=io.StringIO) as stdout:
                code: int = docs_sync.main(['--skill', 'golemancy', '--docs', str(docs), '--output', str(output)])
            text: str = page.read_text()
            copied: bool = (demos / 'golemancy-plinth-ritual-plinth-ritual.png').is_file()
        self.assertEqual(code, 0)
        self.assertTrue(copied)
        self.assertIn('copied 1 still(s) to ' + str(demos), stdout.getvalue())
        self.assertNotIn('.png', text)
        self.assertIn('golemancy-plinth-ritual-pov.webm', text)
        self.assertIn('## Animus Plinth\n\n<div class="adapt-demo">', text)

    def test_main_treats_showcase_clip_files_as_showcase_and_not_as_adaptations(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            docs: Path = Path(tmp) / 'docs'
            (docs / 'adapt').mkdir(parents=True)
            (docs / 'adapt' / '34-skill-golemancy.md').write_text(SHOWCASE_PAGE)
            demos: Path = docs / 'adapt-assets' / 'demos' / 'golemancy'
            demos.mkdir(parents=True)
            for name in ('golemancy-plinth-ritual-pov.webm', 'golemancy-plinth-ritual-observer.webm', 'golem-frost-frame-pov.webm', 'golem-frost-frame-observer.webm'):
                (demos / name).write_bytes(b'')
            with mock.patch.object(docs_sync, 'sheet_entries', return_value=self.entries), mock.patch.object(docs_sync.choreography, 'adaptation_ids',
                                                                                                           return_value={'golem-frost-frame'}), \
                    mock.patch('sys.stdout', new_callable=io.StringIO) as stdout:
                code: int = docs_sync.main(['--skill', 'golemancy', '--docs', str(docs), '--output', str(Path(tmp) / 'none')])
        self.assertEqual(code, 0)
        self.assertIn('updated 2 block(s)', stdout.getvalue())
        self.assertNotIn('copied', stdout.getvalue())

    def test_main_reports_an_ambiguous_showcase_heading(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            docs: Path = Path(tmp) / 'docs'
            (docs / 'adapt').mkdir(parents=True)
            (docs / 'adapt' / '34-skill-golemancy.md').write_text(SHOWCASE_PAGE + '\n## Animus Plinth\n')
            demos: Path = docs / 'adapt-assets' / 'demos' / 'golemancy'
            demos.mkdir(parents=True)
            for name in ('golemancy-plinth-ritual-pov.webm', 'golemancy-plinth-ritual-observer.webm'):
                (demos / name).write_bytes(b'')
            with mock.patch.object(docs_sync, 'sheet_entries', return_value=self.entries), mock.patch.object(docs_sync.choreography, 'adaptation_ids',
                                                                                                           return_value={'golem-frost-frame'}), \
                    mock.patch('sys.stdout', new_callable=io.StringIO) as stdout:
                code: int = docs_sync.main(['--skill', 'golemancy', '--docs', str(docs)])
        self.assertEqual(code, 2)
        self.assertIn('appears 2 times', stdout.getvalue())


if __name__ == '__main__':
    unittest.main()
