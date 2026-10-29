import json
import sys
import unittest
from pathlib import Path

ROOT: Path = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / 'src' / 'test' / 'demo'))
import claims_flight_qa as flight


def outside(x: float, gliding: bool, rockets: int, denied: int) -> dict:
    return {'x': x, 'z': 5118.5, 'minimumX': -1296, 'minimumZ': 5088, 'maximumZExclusive': 5136,
            'bypass': False, 'claimed': False, 'health': 20, 'gliding': gliding, 'rockets': rockets,
            'boundaryDenials': denied, 'boundaryEntries': 0}


def inside(mode: str) -> dict:
    return {'bypass': False, 'claimed': True, 'member': True, 'flying': False, 'gliding': False,
            'allowFlight': mode == 'flight', mode + 'Requests': 1, mode + 'Denials': 1}


class ClaimsFlightEvidenceTest(unittest.TestCase):
    def boundary(self) -> list[dict]:
        return [outside(-1304.5, False, 64, 0), outside(-1300, True, 63, 0), outside(-1296.5, True, 63, 3)]

    def test_real_glide_rocket_and_boundary_rejection_pass(self) -> None:
        flight.check_boundary(self.boundary())

    def test_stationary_actor_cannot_pass_without_reaching_boundary(self) -> None:
        with self.assertRaisesRegex(AssertionError, 'never started gliding'):
            flight.check_boundary([outside(-1304.5, False, 64, 0)])
        with self.assertRaisesRegex(AssertionError, 'No real airborne crossing'):
            flight.check_boundary([outside(-1304.5, True, 64, 0)])

    def test_teleporting_away_does_not_count_as_boundary_protection(self) -> None:
        samples: list[dict] = self.boundary()
        samples[-1]['z'] = 5000
        with self.assertRaisesRegex(AssertionError, 'missed the city'):
            flight.check_boundary(samples)

    def test_crossing_damage_and_missing_rocket_each_fail(self) -> None:
        for field, value, message in [('x', -1295.9, 'entered the city'), ('health', 19, 'caused damage'),
                                      ('rockets', 64, 'firework'), ('boundaryEntries', 1, 'between observations')]:
            with self.subTest(field=field):
                samples: list[dict] = self.boundary()
                samples[-1][field] = value
                with self.assertRaisesRegex(AssertionError, message):
                    flight.check_boundary(samples)

    def test_inside_restrictions_require_actual_denied_client_requests(self) -> None:
        for mode in ('flight', 'glide'):
            with self.subTest(mode=mode):
                sample: dict = inside(mode)
                flight.check_inside([sample], mode)
                sample[mode + 'Requests'] = 0
                sample[mode + 'Denials'] = 0
                with self.assertRaisesRegex(AssertionError, 'made no'):
                    flight.check_inside([sample], mode)

    def test_one_allowed_request_or_relocation_inside_fails(self) -> None:
        for field, value in [('flightDenials', 0), ('claimed', False), ('bypass', True), ('flying', True)]:
            with self.subTest(field=field):
                sample: dict = inside('flight')
                sample[field] = value
                with self.assertRaises(AssertionError):
                    flight.check_inside([sample], 'flight')

    def test_hidden_state_rejects_visible_window_or_stolen_cursor(self) -> None:
        state: dict = {'ok': True, 'connected': True, 'hiddenRenderer': True, 'windowVisible': False,
                       'windowFocused': False, 'mouseGrabbed': False, 'paused': False, 'cursorMode': 212993,
                       'renderWidth': 1920, 'renderHeight': 1080, 'frames': 12, 'ticks': 5, 'processId': 1}
        flight.hidden_state(state)
        for field, value in [('windowVisible', True), ('mouseGrabbed', True), ('cursorMode', 212995), ('connected', False)]:
            with self.subTest(field=field), self.assertRaises(AssertionError):
                flight.hidden_state({**state, field: value})

    def test_snapshot_prefix_is_required_and_payload_is_parsed(self) -> None:
        sample: dict = inside('flight')
        self.assertEqual(flight.parse_snapshot(flight.SNAPSHOT_PREFIX + json.dumps(sample)), sample)
        with self.assertRaises(AssertionError):
            flight.parse_snapshot('ADAPT_QA ERROR fixture failed')


if __name__ == '__main__':
    unittest.main()
