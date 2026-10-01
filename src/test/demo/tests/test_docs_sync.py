import io
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / 'src' / 'test' / 'demo'))
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
        self.assertIn('<video src="/adapt-assets/demos/agility/agility-wall-jump-pov.webm" autoplay muted loop playsinline controls preload="metadata"></video>\n', text)
        self.assertIn('<video src="/adapt-assets/demos/agility/agility-wall-jump-observer.webm" autoplay muted loop playsinline controls preload="metadata"></video>\n', text)
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
        for identifier in ('agility-wind-up', 'agility-wall-jump'):
            restored = restored.replace('\n\n' + docs_sync.block('agility', identifier).rstrip('\n'), '', 1)
        self.assertEqual(restored, PAGE)

    def test_block_sits_between_single_blank_lines(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            page = self.write_page(tmp)
            docs_sync.sync_page(page, 'agility', ['agility-wall-jump'], lambda i: True, '2026-09-30T12:00:00.000Z')
            text = page.read_text()
        self.assertIn('### Wall Jump (`agility-wall-jump`)\n\n' + docs_sync.block('agility', 'agility-wall-jump') + '\n5 levels\n', text)

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
        self.assertTrue(second.endswith('### Kip-Up (`agility-kip-up`)\n\n' + docs_sync.block('agility', 'agility-kip-up').rstrip('\n')))

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
        self.assertEqual(text.count('preload="metadata"'), 2)
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


if __name__ == '__main__':
    unittest.main()
