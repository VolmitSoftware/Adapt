import re
import sys
import unittest
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / 'src' / 'test' / 'demo'))
sys.path.insert(0, str(ROOT / 'src' / 'test' / 'client'))
import choreography
import manifest
import proximity
import record_phase


def near(blocks: str) -> str:
    return 'ADAPT_QA DEMO NEAR nearest=' + blocks


def watch(replies: list[str], allow: tuple[choreography.AllowClose, ...] = (), start_tick: int = 100) -> proximity.Proximity:
    rcon: mock.MagicMock = mock.MagicMock()
    rcon.command.side_effect = replies
    return proximity.Proximity(rcon, 'Magic_Psycho', start_tick, allow)


class NearestReplyTest(unittest.TestCase):
    def test_a_distance_is_read_as_blocks(self) -> None:
        self.assertEqual(proximity.parse_nearest(near('3.46')), 3.46)

    def test_no_golem_reads_as_none(self) -> None:
        self.assertIsNone(proximity.parse_nearest(near('none')))

    def test_the_probe_header_carries_the_same_field(self) -> None:
        self.assertEqual(proximity.parse_nearest('ADAPT_QA DEMO PROBE refunds=0 golems=1 nearest=2.50 | golem COPPER WORK'), 2.5)

    def test_a_reply_without_the_field_is_an_error(self) -> None:
        with self.assertRaisesRegex(RuntimeError, 'Unexpected near reply'):
            proximity.parse_nearest('ADAPT_QA DEMO OK')


class SamplingTest(unittest.TestCase):
    def test_a_sample_is_taken_every_ten_ticks_and_dated_in_clip_seconds(self) -> None:
        samples: proximity.Proximity = watch([near('5.00'), near('4.00'), near('3.00')])
        for tick in range(110, 131):
            samples.sample(tick)
        self.assertEqual(samples.samples, [(0.0, 5.0), (0.5, 4.0), (1.0, 3.0)])
        self.assertEqual(samples.rcon.command.call_count, 3)
        self.assertEqual(samples.rcon.command.call_args_list[0].args[0], 'adaptqa demo near Magic_Psycho')

    def test_samples_before_the_clip_starts_are_ignored(self) -> None:
        samples: proximity.Proximity = watch([near('1.00'), near('4.00')])
        samples.sample(100)
        samples.sample(110)
        self.assertEqual(samples.samples, [(0.0, 4.0)])

    def test_a_take_without_golems_has_no_minimum(self) -> None:
        samples: proximity.Proximity = watch([near('none'), near('none')])
        samples.sample(110)
        samples.sample(120)
        self.assertIsNone(samples.minimum())
        self.assertIsNone(samples.violation())

    def test_the_minimum_is_the_closest_sample(self) -> None:
        samples: proximity.Proximity = watch([near('5.00'), near('2.60'), near('4.00')])
        for tick in (110, 120, 130):
            samples.sample(tick)
        self.assertEqual(samples.minimum(), 2.6)


class ViolationTest(unittest.TestCase):
    def test_exactly_the_limit_passes_after_rounding(self) -> None:
        samples: proximity.Proximity = watch([near('2.50')])
        samples.sample(110)
        self.assertIsNone(samples.violation())

    def test_closer_than_the_limit_fails_with_the_time_and_distance(self) -> None:
        samples: proximity.Proximity = watch([near('4.00'), near('1.40')])
        samples.sample(110)
        samples.sample(120)
        self.assertEqual(samples.violation(), (0.5, 1.4))

    def test_an_allowed_window_excuses_the_samples_inside_it(self) -> None:
        window: choreography.AllowClose = choreography.AllowClose(start=0.0, end=1.0, reason='bind-ritual')
        samples: proximity.Proximity = watch([near('2.00'), near('2.00'), near('4.00')], (window,))
        for tick in (110, 120, 130):
            samples.sample(tick)
        self.assertIsNone(samples.violation())
        self.assertEqual(samples.minimum(), 2.0)

    def test_a_sample_after_the_window_still_fails(self) -> None:
        window: choreography.AllowClose = choreography.AllowClose(start=0.0, end=0.5, reason='bind-ritual')
        samples: proximity.Proximity = watch([near('2.00'), near('2.00'), near('2.00')], (window,))
        for tick in (110, 120, 130):
            samples.sample(tick)
        self.assertEqual(samples.violation(), (1.0, 2.0))

    def test_the_message_names_the_limit(self) -> None:
        self.assertEqual(proximity.message((12.5, 1.4)), 'golem 1.40 blocks from the camera at 12.5 s, closer than 2.5')


PROBE_FORMAT: Path = ROOT / 'src' / 'test' / 'gameplay' / 'fixture' / 'java' / 'art' / 'arcane' / 'adapt' / 'gameplay' / 'demo' / 'ProbeFormat.java'


def java_constant(name: str) -> str:
    found: re.Match[str] | None = re.search(r'static final String ' + name + r' = "([^"]*)";', PROBE_FORMAT.read_text())
    if found is None:
        raise AssertionError(name + ' is missing from ProbeFormat.java')
    return found.group(1)


def sample(template: str, values: tuple[object, ...]) -> str:
    return re.sub(r'%\.?\d*[ds]', '{}', template).format(*values)


class ProbeFormatContractTest(unittest.TestCase):
    def test_the_probe_header_matches_the_studio_regex_and_both_counts_and_the_nearest_field(self) -> None:
        header: str = sample(java_constant('HEADER'), (3, 2, '4.25'))
        counts: re.Match[str] | None = record_phase.PROBE_COUNTS.search(header)
        self.assertIsNotNone(counts)
        self.assertEqual((int(counts.group(1)), int(counts.group(2))), (3, 2))
        self.assertEqual(proximity.parse_nearest(header), 4.25)

    def test_the_probe_header_names_no_golem_in_the_words_the_studio_reads(self) -> None:
        header: str = sample(java_constant('HEADER'), (0, 0, java_constant('NO_GOLEM')))
        self.assertIsNone(proximity.parse_nearest(header))

    def test_the_near_reply_matches_the_studio_regex(self) -> None:
        self.assertEqual(proximity.parse_nearest(sample(java_constant('NEAR'), ('2.50',))), 2.5)
        self.assertIsNone(proximity.parse_nearest(sample(java_constant('NEAR'), (java_constant('NO_GOLEM'),))))

    def test_the_command_the_studio_sends_is_the_one_the_fixture_dispatches(self) -> None:
        dispatch: str = (ROOT / 'src' / 'test' / 'gameplay' / 'fixture' / 'java' / 'art' / 'arcane' / 'adapt' / 'gameplay' / 'AdaptGameplayFixture.java').read_text()
        self.assertIn('case "near" ->', dispatch)
        self.assertIn('case "probe" ->', dispatch)
        rcon: mock.MagicMock = mock.MagicMock()
        rcon.command.return_value = near('none')
        proximity.Proximity(rcon, 'Magic_Psycho', 0).sample(10)
        self.assertEqual(rcon.command.call_args.args[0], 'adaptqa demo near Magic_Psycho')

    def test_the_decimal_format_is_two_places(self) -> None:
        self.assertEqual(java_constant('DISTANCE'), '%.2f')


class ManifestFieldTest(unittest.TestCase):
    def test_the_minimum_defaults_to_none_and_round_trips(self) -> None:
        take: manifest.Take = manifest.Take(id='a', skill='golemancy', set='taming-field', level=5, replay='', start_tick=1, stop_tick=2, evidence_ok=True,
                                            evidence_detail='', takes=1, camera={}, actor_uuid='', status='recorded')
        self.assertIsNone(take.min_golem_distance)
        self.assertEqual(take.crf, {})
        measured: manifest.Take = manifest.Take(**{**take.__dict__, 'min_golem_distance': 2.5, 'crf': {'pov': 30}})
        self.assertEqual(measured.min_golem_distance, 2.5)


if __name__ == '__main__':
    unittest.main()
