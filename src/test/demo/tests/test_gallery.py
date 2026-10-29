import dataclasses
import datetime
import os
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / 'src' / 'test' / 'demo'))
import gallery
import manifest


def exported_take() -> manifest.Take:
    return manifest.Take(id='agility-wall-jump', skill='agility', set='wall', level=5, replay='/r.zip', start_tick=0, stop_tick=100,
                         evidence_ok=True, evidence_detail='sound=True particle=True', takes=1, camera={}, actor_uuid='u', status='exported')


def failed_take(identifier: str, detail: str) -> manifest.Take:
    return manifest.Take(id=identifier, skill='agility', set='fence', level=1, replay='', start_tick=-1, stop_tick=-1,
                         evidence_ok=False, evidence_detail=detail, takes=3, camera={}, actor_uuid='', status='failed')


def render(takes: dict[str, manifest.Take]) -> str:
    with tempfile.TemporaryDirectory() as tmp:
        path = Path(tmp) / 'nested' / 'gallery.html'
        gallery.write(path, takes, Path('/docs'), Path('/thumbs'))
        return path.read_text()


class AcceptedGalleryTest(unittest.TestCase):
    def test_accepted_videos_and_stills_use_docs_without_local_media(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root: Path = Path(temporary)
            docs: Path = root / 'docs'
            output: Path = root / 'run'
            take: manifest.Take = dataclasses.replace(exported_take(), status='accepted', replay='',
                                                      stills=[{'name': 'action', 'angle': 'pov', 'at': 1.0}])
            still: Path = docs / 'adapt-assets' / 'demos' / take.skill / (take.id + '-action.png')
            still.parent.mkdir(parents=True)
            still.write_bytes(b'png')
            page: Path = output / 'gallery.html'
            gallery.write(page, {take.id: take}, output / 'review', output / 'thumbs', '', docs)
            body: str = page.read_text()
            self.assertIn(still.as_uri(), body)
            self.assertIn((still.parent / (take.id + '-pov.webm')).as_uri(), body)
            self.assertNotIn((output / 'review').as_uri(), body)
            self.assertNotIn((output / 'thumbs').as_uri(), body)


class GalleryIndexTest(unittest.TestCase):
    def skill(self, root: Path, name: str, takes: dict[str, manifest.Take] | None = None) -> Path:
        folder: Path = root / name
        folder.mkdir()
        (folder / 'gallery.html').write_text('<!doctype html>')
        if takes is not None:
            manifest.save(folder / 'manifest.json', takes)
        return folder

    def test_index_links_every_skill_gallery_in_name_order(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root: Path = Path(tmp)
            self.skill(root, 'pickaxe')
            self.skill(root, 'agility')
            (root / 'jars').mkdir()
            (root / 'site-cache.json').write_text('{}')
            gallery.write_index(root / 'index.html')
            page: str = (root / 'index.html').read_text()
        self.assertIn('<title>Adapt demo galleries</title>', page)
        self.assertLess(page.index('<a href="agility/gallery.html">agility</a>'), page.index('<a href="pickaxe/gallery.html">pickaxe</a>'))
        self.assertNotIn('jars', page)

    def test_index_counts_each_skill_takes_by_status(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root: Path = Path(tmp)
            skipped: manifest.Take = dataclasses.replace(failed_take('agility-marathoner', 'invisible'), status='skipped')
            self.skill(root, 'agility', {'agility-wall-jump': exported_take(), 'agility-vault': failed_take('agility-vault', 'sound=False'),
                                         'agility-marathoner': skipped, 'agility-roll': dataclasses.replace(exported_take(), id='agility-roll')})
            gallery.write_index(root / 'index.html')
            page: str = (root / 'index.html').read_text()
        self.assertIn('2 exported · 1 failed · 1 skipped', page)

    def test_skill_without_a_readable_manifest_is_still_linked(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root: Path = Path(tmp)
            (self.skill(root, 'ranged') / 'manifest.json').write_text('not json')
            gallery.write_index(root / 'index.html')
            page: str = (root / 'index.html').read_text()
        self.assertIn('<a href="ranged/gallery.html">ranged</a>', page)
        self.assertNotIn('exported', page)

    def test_index_shows_when_each_gallery_was_written(self) -> None:
        stamp: float = datetime.datetime(2026, 9, 30, 14, 5).timestamp()
        with tempfile.TemporaryDirectory() as tmp:
            root: Path = Path(tmp)
            os.utime(self.skill(root, 'stealth') / 'gallery.html', (stamp, stamp))
            gallery.write_index(root / 'index.html')
            page: str = (root / 'index.html').read_text()
        self.assertIn('updated 2026-09-30 14:05', page)

    def test_rewrite_lists_new_galleries_and_leaves_no_staging_file(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root: Path = Path(tmp)
            self.skill(root, 'axes')
            gallery.write_index(root / 'index.html')
            self.skill(root, 'herbalism')
            gallery.write_index(root / 'index.html')
            page: str = (root / 'index.html').read_text()
            files: list[str] = sorted(path.name for path in root.iterdir() if path.is_file())
        self.assertIn('herbalism/gallery.html', page)
        self.assertIn('axes/gallery.html', page)
        self.assertEqual(files, ['index.html'])

    def test_empty_root_says_there_are_no_galleries(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            gallery.write_index(Path(tmp) / 'index.html')
            page: str = (Path(tmp) / 'index.html').read_text()
        self.assertIn('No galleries yet.', page)

    def test_names_are_escaped(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root: Path = Path(tmp)
            self.skill(root, 'a&b')
            gallery.write_index(root / 'index.html')
            page: str = (root / 'index.html').read_text()
        self.assertIn('<a href="a%26b/gallery.html">a&amp;b</a>', page)


class GalleryTest(unittest.TestCase):
    def test_gallery_lists_each_take_with_two_videos_and_status(self) -> None:
        take = manifest.Take(id='agility-wall-jump', skill='agility', set='wall', level=5, replay='/r.zip', start_tick=0, stop_tick=100,
                             evidence_ok=True, evidence_detail='sound=True particle=True', takes=1, camera={}, actor_uuid='u', status='exported')
        failed = manifest.Take(id='agility-vault', skill='agility', set='fence', level=1, replay='', start_tick=-1, stop_tick=-1,
                               evidence_ok=False, evidence_detail='sound=False particle=False', takes=3, camera={}, actor_uuid='', status='failed')
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'gallery.html'
            gallery.write(path, {'agility-wall-jump': take, 'agility-vault': failed}, Path('/docs'), Path('/thumbs'))
            html = path.read_text()
        self.assertIn('agility-wall-jump-pov.webm', html)
        self.assertIn('agility-wall-jump-observer.webm', html)
        self.assertIn('exported', html)
        self.assertIn('failed', html)
        self.assertIn('agility-vault', html)

    def test_header_names_the_plate_origin_and_biome(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'gallery.html'
            gallery.write(path, {'agility-wall-jump': exported_take()}, Path('/docs'), Path('/thumbs'), '128 71 -64 in overworld:plains')
            html = path.read_text()
        self.assertIn('<h1>Adapt demo review</h1><p>Plate origin 128 71 -64 in overworld:plains</p>', html)

    def test_header_omits_the_plate_when_unknown(self) -> None:
        self.assertNotIn('Plate origin', render({'agility-wall-jump': exported_take()}))

    def test_videos_are_file_uris_into_docs_tree_with_thumbnail_posters(self) -> None:
        html = render({'agility-wall-jump': exported_take()})
        self.assertIn('src="file:///docs/adapt-assets/demos/agility/agility-wall-jump-pov.webm"', html)
        self.assertIn('src="file:///docs/adapt-assets/demos/agility/agility-wall-jump-observer.webm"', html)
        self.assertIn('poster="file:///thumbs/agility-wall-jump-pov.png"', html)
        self.assertIn('poster="file:///thumbs/agility-wall-jump-observer.png"', html)
        self.assertIn('4.5 s', html)
        self.assertIn('evidence ok', html)

    def test_failed_takes_show_evidence_detail_and_copyable_id_list(self) -> None:
        html = render({'agility-wall-jump': exported_take(), 'agility-vault': failed_take('agility-vault', 'waitFor timed out'),
                       'agility-air-dash': failed_take('agility-air-dash', 'sound=False particle=True')})
        self.assertIn('<code class="ids">agility-air-dash,agility-vault</code>', html)
        self.assertIn('waitFor timed out', html)
        self.assertIn('sound=False particle=True', html)
        self.assertNotIn('agility-vault-pov.webm', html)
        self.assertIn('evidence missing', html)

    def test_no_failures_reports_none(self) -> None:
        html = render({'agility-wall-jump': exported_take()})
        self.assertIn('<code class="ids">none</code>', html)

    def test_skipped_takes_are_listed_under_skipped_heading_with_reason(self) -> None:
        skipped = manifest.Take(id='agility-marathoner', skill='agility', set='', level=0, replay='', start_tick=-1, stop_tick=-1,
                                evidence_ok=False, evidence_detail='Reduces hunger drain while sprinting.', takes=0, camera={}, actor_uuid='',
                                status='skipped')
        html = render({'agility-wall-jump': exported_take(), 'agility-marathoner': skipped})
        skipped_at: int = html.index('<h2>Skipped</h2>')
        self.assertIn('<code>agility-marathoner</code> Reduces hunger drain while sprinting.', html[skipped_at:])
        self.assertNotIn('agility-marathoner', html[:skipped_at])
        self.assertNotIn('agility-marathoner-pov.webm', html)
        self.assertIn('<code class="ids">none</code>', html)

    def test_no_skipped_heading_without_skipped_takes(self) -> None:
        self.assertNotIn('Skipped', render({'agility-wall-jump': exported_take()}))

    def test_export_error_is_shown_beside_evidence_detail(self) -> None:
        take = dataclasses.replace(exported_take(), status='failed', export_error='export rejected with HTTP 400: Replay world is not ready')
        html = render({'agility-wall-jump': take})
        self.assertIn('sound=True particle=True', html)
        self.assertIn('export error: export rejected with HTTP 400: Replay world is not ready', html)
        self.assertNotIn('export error', render({'agility-wall-jump': exported_take()}))

    def test_visual_only_take_is_labelled_with_the_text(self) -> None:
        take: manifest.Take = dataclasses.replace(exported_take(), evidence_detail='visual-only: The actor lunges forward.',
                                                  visual='The actor lunges forward.')
        html: str = render({'agility-wall-jump': take})
        self.assertIn('visual-only: The actor lunges forward.', html)
        self.assertEqual(html.count('visual-only'), 1)
        self.assertNotIn('evidence ok', html)
        self.assertIn('agility-wall-jump-pov.webm', html)
        self.assertNotIn('visual-only', render({'agility-wall-jump': exported_take()}))

    def test_failed_visual_only_take_shows_the_label_and_the_failure(self) -> None:
        take: manifest.Take = dataclasses.replace(failed_take('agility-vault', 'maxTicks 400 exceeded'), visual='Blocks <fall> & settle.')
        html: str = render({'agility-vault': take})
        self.assertIn('visual-only: Blocks &lt;fall&gt; &amp; settle.', html)
        self.assertIn('maxTicks 400 exceeded', html)
        self.assertIn('<code class="ids">agility-vault</code>', html)

    def test_extracted_stills_are_shown_beside_the_clips(self) -> None:
        stills: list[dict] = [{'angle': 'observer', 'at': 3.5, 'name': 'plinth-ritual'}, {'angle': 'pov', 'at': 1.0, 'name': 'not-extracted'}]
        take: manifest.Take = dataclasses.replace(exported_take(), stills=stills)
        with tempfile.TemporaryDirectory() as tmp:
            path: Path = Path(tmp) / 'gallery.html'
            (Path(tmp) / 'stills').mkdir()
            (Path(tmp) / 'stills' / 'agility-wall-jump-plinth-ritual.png').write_bytes(b'png')
            gallery.write(path, {take.id: take}, Path('/docs'), Path('/thumbs'))
            html: str = path.read_text()
            image: str = (Path(tmp).absolute() / 'stills' / 'agility-wall-jump-plinth-ritual.png').as_uri()
        self.assertIn('<img src="' + image + '" alt="plinth-ritual" width="480">', html)
        self.assertIn('<figcaption>plinth-ritual · observer 3.5 s</figcaption>', html)
        self.assertNotIn('not-extracted', html)
        self.assertLess(html.index('agility-wall-jump-observer.webm'), html.index(image))

    def test_the_nearest_golem_distance_and_the_crf_are_shown_when_known(self) -> None:
        take: manifest.Take = dataclasses.replace(exported_take(), min_golem_distance=2.5, crf={'pov': 30, 'observer': 27})
        page: str = render({take.id: take})
        self.assertIn('nearest golem 2.50 blocks', page)
        self.assertIn('crf pov 30, observer 27', page)

    def test_a_take_without_golems_names_neither(self) -> None:
        page: str = render({'agility-wall-jump': exported_take()})
        self.assertNotIn('nearest golem', page)
        self.assertNotIn('crf ', page)

    def test_a_take_without_stills_shows_no_figures(self) -> None:
        self.assertNotIn('<figure>', render({'agility-wall-jump': exported_take()}))

    def test_text_is_escaped(self) -> None:
        html = render({'agility-vault': failed_take('agility-vault', 'expected <sound> & particle')})
        self.assertIn('expected &lt;sound&gt; &amp; particle', html)
        self.assertNotIn('<sound>', html)


if __name__ == '__main__':
    unittest.main()
