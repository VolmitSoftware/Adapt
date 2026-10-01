import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / 'src' / 'test' / 'demo'))
import evidence


def sound(tick: int, name: str) -> dict:
    return {'type': 'sound', 'tick': tick, 'name': name, 'channelPlaying': True}


def particle(tick: int, name: str, rendered: bool) -> dict:
    return {'type': 'particle', 'tick': tick, 'name': name, 'rendered': rendered}


class EvidenceTest(unittest.TestCase):
    def test_passes_when_both_inside_window(self) -> None:
        result = evidence.check([sound(50, 'minecraft:a'), particle(60, 'cloud', True)], 40, 100, 'minecraft:a', 'cloud')
        self.assertTrue(result.ok)

    def test_evidence_ignores_events_outside_window(self) -> None:
        result = evidence.check([sound(10, 'minecraft:a'), particle(150, 'cloud', True)], 40, 100, 'minecraft:a', 'cloud')
        self.assertFalse(result.ok)
        self.assertFalse(result.sound_found)
        self.assertFalse(result.particle_found)

    def test_evidence_requires_rendered_particle(self) -> None:
        result = evidence.check([sound(50, 'minecraft:a'), particle(60, 'cloud', False)], 40, 100, 'minecraft:a', 'cloud')
        self.assertFalse(result.particle_found)

    def test_bare_expectations_match_namespaced_client_events(self) -> None:
        result = evidence.check([sound(50, 'minecraft:item.armor.equip_leather'), particle(60, 'minecraft:cloud', True)], 40, 100,
                                'item.armor.equip_leather', 'cloud')
        self.assertTrue(result.sound_found)
        self.assertTrue(result.particle_found)

    def test_other_namespace_does_not_match_bare_expectation(self) -> None:
        result = evidence.check([particle(60, 'custom:cloud', True)], 40, 100, None, 'cloud')
        self.assertFalse(result.particle_found)

    def test_near_miss_particle_names_do_not_match(self) -> None:
        result = evidence.check([particle(60, 'minecraft:cloud_x', True)], 40, 100, None, 'cloud')
        self.assertFalse(result.particle_found)
        result = evidence.check([particle(60, 'minecraft:soul_fire_flame', True)], 40, 100, None, 'flame')
        self.assertFalse(result.particle_found)

    def test_none_expectation_is_not_required(self) -> None:
        result = evidence.check([], 40, 100, None, None)
        self.assertTrue(result.ok)

    def test_detail_prints_na_for_missing_expectation(self) -> None:
        self.assertEqual(evidence.check([], 40, 100, None, 'cloud').detail, 'sound=n/a particle=False')
        self.assertEqual(evidence.check([sound(50, 'minecraft:a')], 40, 100, 'minecraft:a', None).detail, 'sound=True particle=n/a')
        self.assertEqual(evidence.check([], 40, 100, None, None).detail, 'sound=n/a particle=n/a')


    def test_visual_only_passes_without_events(self) -> None:
        result: evidence.EvidenceResult = evidence.check([], 40, 100, None, None, 'The actor lunges forward.')
        self.assertTrue(result.ok)
        self.assertEqual(result.detail, 'visual-only: The actor lunges forward.')

    def test_visual_only_ignores_events(self) -> None:
        result: evidence.EvidenceResult = evidence.check([sound(50, 'minecraft:a'), particle(60, 'cloud', True)], 40, 100, None, None, 'Blocks fall.')
        self.assertTrue(result.ok)
        self.assertEqual(result.detail, 'visual-only: Blocks fall.')

    def test_visual_does_not_replace_a_sound_or_particle_expectation(self) -> None:
        result: evidence.EvidenceResult = evidence.check([], 40, 100, 'minecraft:a', None, 'Blocks fall.')
        self.assertFalse(result.ok)
        self.assertEqual(result.detail, 'sound=False particle=n/a')
        result = evidence.check([], 40, 100, None, 'cloud', 'Blocks fall.')
        self.assertFalse(result.ok)
        self.assertEqual(result.detail, 'sound=n/a particle=False')

    def test_empty_visual_keeps_the_plain_detail(self) -> None:
        self.assertEqual(evidence.check([], 40, 100, None, None, '').detail, 'sound=n/a particle=n/a')

if __name__ == '__main__':
    unittest.main()
