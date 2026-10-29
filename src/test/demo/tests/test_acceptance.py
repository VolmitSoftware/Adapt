import dataclasses
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

ROOT: Path = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / 'src' / 'test' / 'demo'))
import acceptance
import export_phase
import manifest


class AcceptanceTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary: tempfile.TemporaryDirectory[str] = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root: Path = Path(self.temporary.name)
        self.output: Path = self.root / 'run'
        self.docs: Path = self.root / 'docs'
        self.output.mkdir()
        self.page: Path = self.docs / 'adapt' / '01-skill-agility.md'
        self.page.parent.mkdir(parents=True)
        self.page.write_text('---\ntitle: Agility\ndate: 2026-01-01T00:00:00.000Z\n---\n\n### Wind Up (`agility-wind-up`)\n\nUsage.\n')
        self.take: manifest.Take = manifest.Take(id='agility-wind-up', skill='agility', set='runway', level=5,
                                                replay=str(self.output / 'replays' / 'agility-wind-up.zip'), start_tick=0, stop_tick=100,
                                                evidence_ok=True, evidence_detail='sound=True', takes=1, camera={}, actor_uuid='u', status='exported')
        self.takes: dict[str, manifest.Take] = {self.take.id: self.take}
        self.sources: list[Path] = []
        for name in ('replays/agility-wind-up.zip', 'intermediate/agility-wind-up-pov-live.mp4',
                     'intermediate/agility-wind-up-observer.mp4', 'thumbs/agility-wind-up-pov.png', 'review/old-contact-sheet.png'):
            self.sources.append(self.write(name, b'source'))
        for angle in export_phase.ANGLES:
            clip: Path = export_phase.final_clip(acceptance.review_root(self.output), self.take, angle)
            clip.parent.mkdir(parents=True, exist_ok=True)
            clip.write_bytes(angle.encode())
            self.sources.append(clip)
        manifest.save(self.output / 'manifest.json', self.takes)

    def write(self, name: str, body: bytes) -> Path:
        path: Path = self.output / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(body)
        return path

    def accept(self) -> dict[str, manifest.Take]:
        with mock.patch.object(acceptance, 'validate_clip'):
            return acceptance.accept(self.output, self.docs, self.takes, [self.take.id], Path('ffmpeg'), Path('ffprobe'))

    def test_success_publishes_syncs_and_removes_only_local_media(self) -> None:
        site: Path = self.write('site-cache.json', b'{}')
        unrelated: Path = self.docs / 'unrelated.png'
        unrelated.write_bytes(b'keep')
        updated: dict[str, manifest.Take] = self.accept()
        for source in self.sources:
            self.assertFalse(source.exists(), str(source))
        for angle in export_phase.ANGLES:
            self.assertEqual(export_phase.final_clip(self.docs, self.take, angle).read_bytes(), angle.encode())
        self.assertIn('/adapt-assets/demos/agility/agility-wind-up-pov.webm', self.page.read_text())
        self.assertEqual(updated[self.take.id].status, 'accepted')
        self.assertEqual(updated[self.take.id].replay, '')
        self.assertEqual(len(updated[self.take.id].published_files), 2)
        self.assertEqual(manifest.load(self.output / 'manifest.json'), updated)
        self.assertTrue(site.is_file())
        self.assertEqual(unrelated.read_bytes(), b'keep')

    def test_publication_failure_keeps_every_source_and_manifest(self) -> None:
        with mock.patch.object(acceptance, 'publish', side_effect=OSError('disk full')):
            with self.assertRaisesRegex(OSError, 'disk full'):
                self.accept()
        self.assertTrue(all(path.exists() for path in self.sources))
        self.assertEqual(manifest.load(self.output / 'manifest.json'), self.takes)

    def test_page_sync_failure_keeps_sources_for_retry(self) -> None:
        self.page.write_text('No matching heading\n')
        with self.assertRaises(RuntimeError):
            self.accept()
        self.assertTrue(all(path.exists() for path in self.sources))
        self.assertEqual(manifest.load(self.output / 'manifest.json'), self.takes)

    def test_validation_failure_never_publishes_or_removes_source(self) -> None:
        with mock.patch.object(acceptance, 'validate_clip', side_effect=ValueError('invalid clip')):
            with self.assertRaisesRegex(ValueError, 'invalid clip'):
                acceptance.accept(self.output, self.docs, self.takes, [self.take.id], Path('ffmpeg'), Path('ffprobe'))
        self.assertTrue(all(path.exists() for path in self.sources))
        self.assertFalse((self.docs / 'adapt-assets').exists())

    def test_partial_acceptance_preserves_pending_take_with_shared_prefix(self) -> None:
        identifier: str = self.take.id + '-longer'
        pending: manifest.Take = dataclasses.replace(self.take, id=identifier, status='recorded')
        self.takes[identifier] = pending
        source: Path = self.write('intermediate/' + identifier + '-pov-live.mp4', b'pending')
        self.accept()
        self.assertEqual(source.read_bytes(), b'pending')
        self.assertTrue((self.output / 'review' / 'old-contact-sheet.png').exists())

    def test_symlinked_media_directory_cannot_delete_outside_run(self) -> None:
        outside: Path = self.root / 'outside'
        outside.mkdir()
        sentinel: Path = outside / 'important.png'
        sentinel.write_bytes(b'keep')
        (self.output / 'stills').symlink_to(outside, target_is_directory=True)
        with self.assertRaisesRegex(ValueError, 'escapes'):
            self.accept()
        self.assertEqual(sentinel.read_bytes(), b'keep')
        self.assertTrue(all(path.exists() for path in self.sources))
        self.assertFalse((self.docs / 'adapt-assets').exists())

    def test_manifest_replay_path_is_never_used_as_a_delete_target(self) -> None:
        outside: Path = self.root / 'other.zip'
        outside.write_bytes(b'keep')
        self.takes[self.take.id] = dataclasses.replace(self.take, replay=str(outside))
        self.accept()
        self.assertEqual(outside.read_bytes(), b'keep')

    def test_path_traversal_identity_is_rejected(self) -> None:
        identifier: str = '../outside'
        self.takes = {identifier: dataclasses.replace(self.take, id=identifier)}
        with mock.patch.object(acceptance, 'validate_clip'):
            with self.assertRaisesRegex(ValueError, 'identity'):
                acceptance.accept(self.output, self.docs, self.takes, [identifier], Path('ffmpeg'), Path('ffprobe'))
        self.assertTrue(all(path.exists() for path in self.sources))

    def test_docs_cannot_be_inside_cleanup_output(self) -> None:
        with self.assertRaisesRegex(ValueError, 'separate'):
            acceptance.accept(self.output, self.output / 'docs', self.takes, [self.take.id], Path('ffmpeg'), Path('ffprobe'))

    def test_empty_selection_never_removes_orphan_media(self) -> None:
        acceptance.accept(self.output, self.docs, {}, [], Path('ffmpeg'), Path('ffprobe'))
        self.assertTrue(all(path.exists() for path in self.sources))

    def test_cleanup_retry_requires_published_clips_to_still_be_valid(self) -> None:
        self.takes[self.take.id] = dataclasses.replace(self.take, status='accepted')
        with self.assertRaisesRegex(ValueError, 'missing or changed'):
            acceptance.accept(self.output, self.docs, self.takes, [self.take.id], Path('ffmpeg'), Path('ffprobe'))
        self.assertTrue(all(path.exists() for path in self.sources))

    def test_cleanup_retry_rejects_changed_published_bytes(self) -> None:
        self.takes = self.accept()
        source: Path = self.write('intermediate/agility-wind-up-pov-live.mp4', b'pending cleanup')
        export_phase.final_clip(self.docs, self.take, 'pov').write_bytes(b'replacement')
        with self.assertRaisesRegex(ValueError, 'missing or changed'):
            self.accept()
        self.assertEqual(source.read_bytes(), b'pending cleanup')

    def test_cleanup_retry_uses_only_published_hashes_after_sources_are_gone(self) -> None:
        self.takes = self.accept()
        source: Path = self.write('intermediate/agility-wind-up-pov-live.mp4', b'pending cleanup')
        updated: dict[str, manifest.Take] = self.accept()
        self.assertFalse(source.exists())
        self.assertEqual(updated[self.take.id].published_files, self.takes[self.take.id].published_files)

    def test_discard_removes_pending_media_without_modifying_docs(self) -> None:
        published: Path = export_phase.final_clip(self.docs, self.take, 'pov')
        published.parent.mkdir(parents=True)
        published.write_bytes(b'previous accepted clip')
        page: str = self.page.read_text()
        updated: dict[str, manifest.Take] = acceptance.discard(self.output, self.docs, self.takes, [self.take.id])
        self.assertEqual(updated, {})
        self.assertTrue(all(not path.exists() for path in self.sources))
        self.assertEqual(published.read_bytes(), b'previous accepted clip')
        self.assertEqual(self.page.read_text(), page)

    def test_discard_cannot_remove_accepted_manifest(self) -> None:
        self.takes[self.take.id] = dataclasses.replace(self.take, status='accepted')
        with self.assertRaisesRegex(ValueError, 'pending'):
            acceptance.discard(self.output, self.docs, self.takes, [self.take.id])
        self.assertTrue(all(path.exists() for path in self.sources))

    def test_declared_still_is_preserved_in_docs_before_local_copy_is_removed(self) -> None:
        self.takes[self.take.id] = dataclasses.replace(self.take, stills=[{'name': 'action', 'angle': 'pov', 'at': 1.0}])
        still: Path = self.write('stills/agility-wind-up-action.png', b'png')
        self.accept()
        self.assertFalse(still.exists())
        self.assertEqual((self.docs / 'adapt-assets/demos/agility/agility-wind-up-action.png').read_bytes(), b'png')


class ClipValidationTest(unittest.TestCase):
    def test_empty_clip_is_rejected_before_tools_run(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path: Path = Path(temporary) / 'clip.webm'
            path.touch()
            with mock.patch.object(acceptance.subprocess, 'run') as probe:
                with self.assertRaisesRegex(ValueError, 'empty'):
                    acceptance.validate_clip(path, Path('ffmpeg'), Path('ffprobe'))
            probe.assert_not_called()

    def test_full_decode_failure_rejects_otherwise_valid_metadata(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path: Path = Path(temporary) / 'clip.webm'
            path.write_bytes(b'broken')
            metadata: str = json.dumps({'streams': [{'codec_name': 'vp9', 'width': 1920, 'height': 1080}]})
            result: subprocess.CompletedProcess[str] = subprocess.CompletedProcess([], 0, stdout=metadata)
            with mock.patch.object(acceptance.subprocess, 'run', return_value=result), \
                    mock.patch.object(acceptance.encode, 'run', side_effect=RuntimeError('decode failed')) as decode:
                with self.assertRaisesRegex(RuntimeError, 'decode failed'):
                    acceptance.validate_clip(path, Path('ffmpeg'), Path('ffprobe'))
            self.assertIn('-xerror', decode.call_args.args[0])
            self.assertEqual(decode.call_args.args[0][-3:], ['-f', 'null', '-'])


if __name__ == '__main__':
    unittest.main()
