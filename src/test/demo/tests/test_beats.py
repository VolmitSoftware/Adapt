import sys
import unittest
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / 'src' / 'test' / 'demo'))
import beats
import choreography


class LookAnglesTest(unittest.TestCase):
    def test_target_due_north_gives_yaw_180_pitch_0(self) -> None:
        yaw, pitch = beats.look_angles((0.5, 65.62, 0.5), (0.5, 65.62, -10.5))
        self.assertAlmostEqual(abs(yaw), 180.0, places=3)
        self.assertAlmostEqual(pitch, 0.0, places=3)

    def test_target_due_east_gives_yaw_minus_90(self) -> None:
        yaw, _ = beats.look_angles((0.0, 0.0, 0.0), (10.0, 0.0, 0.0))
        self.assertAlmostEqual(yaw, -90.0, places=3)

    def test_target_below_gives_positive_pitch(self) -> None:
        _, pitch = beats.look_angles((0.0, 10.0, 0.0), (0.0, 0.0, 10.0))
        self.assertAlmostEqual(pitch, 45.0, places=3)


def entry_with(steps: list[dict]) -> choreography.Entry:
    return choreography.Entry(id='x', set='arena', level=5, pre=[], beats=steps,
                              expect_sound=None, expect_particle=None, max_ticks=200, camera=None)


class RunBeatsTest(unittest.TestCase):
    def make_ctx(self) -> beats.BeatContext:
        bridge = mock.MagicMock()
        bridge.state.return_value = {'position': {'x': 0.5, 'y': 64.0, 'z': 0.5}, 'ticks': 100, 'events': []}
        rcon = mock.MagicMock()
        return beats.BeatContext(bridge=bridge, rcon=rcon, actor='AQAClient262', origin=(0.0, 64.0, 0.0),
                                 wait_ticks=mock.MagicMock(), events_since=lambda tick: [])

    def test_smooth_look_runs_as_one_native_animation(self) -> None:
        ctx: beats.BeatContext = self.make_ctx()
        beats.run_beats(entry_with([{'verb': 'look', 'yaw': -179, 'pitch': 30, 'ticks': 6}]), ctx)
        ctx.bridge.command.assert_called_once_with('look', yaw=-179.0, pitch=30.0, ticks=6)
        ctx.wait_ticks.assert_called_once_with(6)

    def test_unfinished_turn_prevents_attack(self) -> None:
        ctx: beats.BeatContext = self.make_ctx()
        ctx.bridge.state.return_value['turning'] = True
        with self.assertRaises(beats.BeatTimeout):
            beats.run_beats(entry_with([{'verb': 'look', 'yaw': 90, 'pitch': 0, 'ticks': 5},
                                       {'verb': 'click', 'key': 'attack'}]), ctx)
        self.assertEqual(ctx.bridge.command.call_count, 1)
        self.assertEqual(ctx.wait_ticks.call_args_list, [mock.call(5)] + [mock.call(1)] * 4)

    def test_smooth_target_aim_finishes_on_target_before_attack(self) -> None:
        ctx: beats.BeatContext = self.make_ctx()
        ctx.bridge.state.return_value.update(yaw=90.0, pitch=20.0)
        beats.run_beats(entry_with([{'verb': 'lookAt', 'offset': [0.5, 1.62, -10.5], 'ticks': 5},
                                   {'verb': 'click', 'key': 'attack'}]), ctx)
        calls: list = ctx.bridge.command.call_args_list
        self.assertEqual(len(calls), 2)
        self.assertAlmostEqual(calls[-2].kwargs['yaw'], -180.0)
        self.assertAlmostEqual(calls[-2].kwargs['pitch'], 0.0)
        self.assertEqual(calls[-1], mock.call('click', key='attack'))
        ctx.wait_ticks.assert_called_once_with(5)

    def test_turn_duration_rejects_invalid_values(self) -> None:
        for ticks in (0, -1, 1.5, True, 201):
            self.assertTrue(choreography.beat_problems({'verb': 'look', 'yaw': 0, 'pitch': 0, 'ticks': ticks}))
        self.assertEqual(choreography.beat_problems({'verb': 'lookAt', 'offset': [0, 0, 0], 'ticks': 6}), [])

    def test_keys_beat_holds_then_releases(self) -> None:
        ctx = self.make_ctx()
        beats.run_beats(entry_with([{'verb': 'keys', 'hold': ['forward', 'sprint'], 'ticks': 12}]), ctx)
        ctx.bridge.command.assert_any_call('keys', forward=True, sprint=True, leaseTicks=12)
        ctx.bridge.command.assert_any_call('release')

    def test_press_beat_does_not_wait_or_release(self) -> None:
        ctx = self.make_ctx()
        beats.run_beats(entry_with([{'verb': 'press', 'hold': ['forward', 'sprint'], 'off': ['jump'], 'ticks': 40}]), ctx)
        ctx.bridge.command.assert_called_once_with('keys', forward=True, sprint=True, jump=False, leaseTicks=40)
        ctx.wait_ticks.assert_not_called()

    def test_swap_hands_and_drop_reach_the_bridge_by_name(self) -> None:
        ctx: beats.BeatContext = self.make_ctx()
        beats.run_beats(entry_with([{'verb': 'click', 'key': 'swapHands'}, {'verb': 'tap', 'key': 'drop'},
                                    {'verb': 'press', 'hold': ['swapHands'], 'off': ['drop'], 'ticks': 3}]), ctx)
        self.assertEqual(ctx.bridge.command.mock_calls, [mock.call('click', key='swapHands'), mock.call('keys', drop=True, leaseTicks=2),
                                                         mock.call('release'), mock.call('keys', swapHands=True, drop=False, leaseTicks=3)])

    def test_command_beat_substitutes_actor(self) -> None:
        ctx = self.make_ctx()
        beats.run_beats(entry_with([{'verb': 'command', 'text': 'effect give {actor} speed 5'}]), ctx)
        ctx.rcon.command.assert_called_once_with('effect give AQAClient262 speed 5')

    def test_wait_for_times_out(self) -> None:
        ctx = self.make_ctx()
        with self.assertRaises(beats.BeatTimeout):
            beats.run_beats(entry_with([{'verb': 'waitFor', 'sound': 'minecraft:x', 'timeout': 3}]), ctx)

    def test_wait_for_waits_exactly_timeout_ticks(self) -> None:
        ctx = self.make_ctx()
        with self.assertRaises(beats.BeatTimeout):
            beats.run_beats(entry_with([{'verb': 'waitFor', 'particle': 'cloud', 'timeout': 3}]), ctx)
        self.assertEqual(ctx.wait_ticks.call_count, 3)

    def test_wait_for_returns_on_matching_event(self) -> None:
        ctx = self.make_ctx()
        ctx.events_since = lambda tick: [{'type': 'sound', 'tick': tick, 'name': 'minecraft:x'}]
        beats.run_beats(entry_with([{'verb': 'waitFor', 'sound': 'minecraft:x', 'timeout': 3}]), ctx)
        ctx.wait_ticks.assert_not_called()

    def test_wait_for_bare_particle_matches_namespaced_event(self) -> None:
        ctx = self.make_ctx()
        ctx.events_since = lambda tick: [{'type': 'particle', 'tick': tick, 'name': 'minecraft:cloud'}]
        beats.run_beats(entry_with([{'verb': 'waitFor', 'particle': 'cloud', 'timeout': 3}]), ctx)
        ctx.wait_ticks.assert_not_called()


    def test_look_at_aims_from_player_eye_height(self) -> None:
        ctx = self.make_ctx()
        beats.run_beats(entry_with([{'verb': 'lookAt', 'offset': [0.5, 1.62, -10.5]}]), ctx)
        name, values = ctx.bridge.command.call_args.args[0], ctx.bridge.command.call_args.kwargs
        self.assertEqual(name, 'look')
        self.assertAlmostEqual(values['pitch'], 0.0, places=6)
        self.assertEqual(beats.manifest.PLAYER_EYE_HEIGHT, 1.62)

    def test_wait_for_other_particle_times_out(self) -> None:
        ctx = self.make_ctx()
        ctx.events_since = lambda tick: [{'type': 'particle', 'tick': tick, 'name': 'minecraft:flame'}]
        with self.assertRaises(beats.BeatTimeout):
            beats.run_beats(entry_with([{'verb': 'waitFor', 'particle': 'cloud', 'timeout': 3}]), ctx)
        self.assertEqual(ctx.wait_ticks.call_count, 3)


    def test_slot_beat_selects_the_hotbar_index(self) -> None:
        ctx: beats.BeatContext = self.make_ctx()
        beats.run_beats(entry_with([{'verb': 'slot', 'index': 3}]), ctx)
        ctx.bridge.command.assert_called_once_with('slot', index=3)

    def test_inventory_cursor_moves_before_click_and_settles(self) -> None:
        ctx: beats.BeatContext = self.make_ctx()
        ctx.bridge.state.return_value.update(cursor={'x': 10.0, 'y': 20.0},
            container={'id': 7, 'slots': [{'index': 38, 'screenX': 190.0, 'screenY': 110.0}]})
        beats.run_beats(entry_with([{'verb': 'window', 'action': 'click', 'slot': 38, 'button': 1}]), ctx)
        calls: list = ctx.bridge.command.call_args_list
        cursor_calls: list = [call for call in calls if call.args == ('cursor',)]
        self.assertEqual(cursor_calls, [mock.call('cursor', x=190.0, y=110.0, containerId=7, ticks=6)])
        self.assertEqual(calls[-1], mock.call('window', action='click', slot=38, button=1, containerId=7))
        self.assertEqual(ctx.wait_ticks.call_args_list, [mock.call(6), mock.call(1), mock.call(2)])

    def test_inventory_short_moves_take_less_time_and_keep_action(self) -> None:
        ctx: beats.BeatContext = self.make_ctx()
        ctx.bridge.state.return_value.update(cursor={'x': 10.0, 'y': 20.0},
            container={'id': 8, 'slots': [{'index': 0, 'screenX': 28.0, 'screenY': 20.0}]})
        for action in ('shift', 'drop'):
            ctx.bridge.command.reset_mock()
            ctx.wait_ticks.reset_mock()
            beats.run_beats(entry_with([{'verb': 'window', 'action': action, 'slot': 0}]), ctx)
            self.assertEqual(ctx.bridge.command.call_count, 2)
            ctx.bridge.command.assert_called_with('window', action=action, slot=0, containerId=8)

    def test_unfinished_cursor_prevents_inventory_click(self) -> None:
        ctx: beats.BeatContext = self.make_ctx()
        ctx.bridge.state.return_value.update(cursor={'x': 10.0, 'y': 20.0}, cursorMoving=True,
            container={'id': 8, 'slots': [{'index': 0, 'screenX': 28.0, 'screenY': 20.0}]})
        with self.assertRaises(beats.BeatTimeout):
            beats.run_beats(entry_with([{'verb': 'window', 'action': 'click', 'slot': 0}]), ctx)
        ctx.bridge.command.assert_called_once_with('cursor', x=28.0, y=20.0, containerId=8, ticks=2)

    def test_inventory_missing_slot_fails_before_moving_or_clicking(self) -> None:
        ctx: beats.BeatContext = self.make_ctx()
        ctx.bridge.state.return_value.update(cursor={'x': 0, 'y': 0}, container={'id': 7, 'slots': []})
        with self.assertRaises(RuntimeError):
            beats.run_beats(entry_with([{'verb': 'window', 'action': 'click', 'slot': 38}]), ctx)
        ctx.bridge.command.assert_not_called()

    def test_inventory_hover_moves_without_clicking_and_holds_tooltip(self) -> None:
        ctx: beats.BeatContext = self.make_ctx()
        ctx.bridge.state.return_value.update(cursor={'x': 10.0, 'y': 20.0},
            container={'id': 8, 'slots': [{'index': 0, 'screenX': 28.0, 'screenY': 20.0}]})
        beats.run_beats(entry_with([{'verb': 'window', 'action': 'hover', 'slot': 0}]), ctx)
        self.assertEqual(ctx.bridge.command.call_count, 1)
        self.assertTrue(all(call.args == ('cursor',) for call in ctx.bridge.command.call_args_list))
        ctx.wait_ticks.assert_called_with(12)

    def test_menu_button_uses_visible_control_position(self) -> None:
        ctx: beats.BeatContext = self.make_ctx()
        ctx.bridge.state.return_value.update(cursor={'x': 10.0, 'y': 20.0},
            container={'id': 9, 'slots': [], 'controls': [{'index': 1, 'screenX': 28.0, 'screenY': 20.0}]})
        beats.run_beats(entry_with([{'verb': 'window', 'action': 'button', 'index': 1}]), ctx)
        self.assertEqual(ctx.bridge.command.call_count, 2)
        ctx.bridge.command.assert_called_with('window', action='button', index=1, containerId=9)

    def test_inventory_list_and_close_do_not_move_cursor(self) -> None:
        ctx: beats.BeatContext = self.make_ctx()
        beats.run_beats(entry_with([{'verb': 'window', 'action': 'list'}, {'verb': 'window', 'action': 'close'}]), ctx)
        self.assertEqual(ctx.bridge.command.call_args_list, [mock.call('window', action='list'), mock.call('window', action='close')])
        ctx.wait_ticks.assert_not_called()

    def test_anvil_name_beat_sends_the_text(self) -> None:
        ctx: beats.BeatContext = self.make_ctx()
        beats.run_beats(entry_with([{'verb': 'anvilName', 'text': 'Blade of Dawn'}]), ctx)
        ctx.bridge.command.assert_called_once_with('anvil-name', text='Blade of Dawn')


class OpponentBeatsTest(unittest.TestCase):
    def make_ctx(self, opponent_running: bool = True) -> beats.BeatContext:
        bridge: mock.MagicMock = mock.MagicMock()
        bridge.state.return_value = {'position': {'x': 0.5, 'y': 64.0, 'z': 0.5}, 'ticks': 100, 'events': []}
        opponent: mock.MagicMock = mock.MagicMock()
        opponent.state.return_value = {'position': {'x': 0.5, 'y': 64.0, 'z': 3.5}, 'ticks': 900, 'events': []}
        return beats.BeatContext(bridge=bridge, rcon=mock.MagicMock(), actor='AQAClient262', origin=(0.0, 64.0, 0.0), wait_ticks=mock.MagicMock(),
                                 events_since=lambda tick: [], opponent='AQAOpponent', opponent_bridge=opponent if opponent_running else None)

    def test_opponent_beats_drive_only_the_opponent_client(self) -> None:
        ctx: beats.BeatContext = self.make_ctx()
        beats.run_beats(entry_with([{'verb': 'look', 'yaw': 0, 'pitch': 5, 'actor': 'opponent'},
                                    {'verb': 'press', 'hold': ['forward'], 'ticks': 6, 'actor': 'opponent'},
                                    {'verb': 'click', 'key': 'attack', 'actor': 'opponent'}, {'verb': 'slot', 'index': 2, 'actor': 'opponent'},
                                    {'verb': 'window', 'action': 'close', 'actor': 'opponent'},
                                    {'verb': 'anvilName', 'text': 'Dawn', 'actor': 'opponent'}]), ctx)
        self.assertEqual(ctx.opponent_bridge.command.call_args_list, [mock.call('look', yaw=0.0, pitch=5.0), mock.call('keys', forward=True, leaseTicks=6),
                                                                      mock.call('click', key='attack'), mock.call('slot', index=2),
                                                                      mock.call('window', action='close'), mock.call('anvil-name', text='Dawn')])
        ctx.bridge.command.assert_not_called()

    def test_opponent_key_holds_wait_on_the_recording_clock(self) -> None:
        ctx: beats.BeatContext = self.make_ctx()
        beats.run_beats(entry_with([{'verb': 'keys', 'hold': ['forward'], 'ticks': 8, 'actor': 'opponent'},
                                    {'verb': 'tap', 'key': 'jump', 'actor': 'opponent'}]), ctx)
        self.assertEqual(ctx.opponent_bridge.command.call_args_list, [mock.call('keys', forward=True, leaseTicks=8), mock.call('release'),
                                                                      mock.call('keys', jump=True, leaseTicks=2), mock.call('release')])
        self.assertEqual(ctx.wait_ticks.call_args_list, [mock.call(8), mock.call(2)])
        ctx.bridge.command.assert_not_called()

    def test_opponent_look_at_aims_from_the_opponent_eye(self) -> None:
        ctx: beats.BeatContext = self.make_ctx()
        beats.run_beats(entry_with([{'verb': 'lookAt', 'offset': [0.5, 1.62, 0.5], 'actor': 'opponent'}]), ctx)
        values: dict = ctx.opponent_bridge.command.call_args.kwargs
        self.assertAlmostEqual(abs(values['yaw']), 180.0, places=3)
        self.assertAlmostEqual(values['pitch'], 0.0, places=6)
        ctx.bridge.state.assert_not_called()

    def test_actor_beats_stay_on_the_actor_client(self) -> None:
        ctx: beats.BeatContext = self.make_ctx()
        beats.run_beats(entry_with([{'verb': 'keys', 'hold': ['jump'], 'ticks': 5}, {'verb': 'click', 'key': 'use'}]), ctx)
        self.assertEqual(ctx.bridge.command.call_args_list, [mock.call('keys', jump=True, leaseTicks=5), mock.call('release'), mock.call('click', key='use')])
        ctx.opponent_bridge.command.assert_not_called()

    def test_command_beat_substitutes_actor_and_opponent(self) -> None:
        ctx: beats.BeatContext = self.make_ctx()
        beats.run_beats(entry_with([{'verb': 'command', 'text': 'tp {opponent} {actor}'}]), ctx)
        ctx.rcon.command.assert_called_once_with('tp AQAOpponent AQAClient262')

    def test_opponent_beat_without_the_opponent_client_fails(self) -> None:
        ctx: beats.BeatContext = self.make_ctx(opponent_running=False)
        with self.assertRaisesRegex(RuntimeError, 'click beat targets the opponent, but no opponent client is running'):
            beats.run_beats(entry_with([{'verb': 'click', 'key': 'attack', 'actor': 'opponent'}]), ctx)
        ctx.bridge.command.assert_not_called()


if __name__ == '__main__':
    unittest.main()
