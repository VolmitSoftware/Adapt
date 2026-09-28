import json
from pathlib import Path
import time

PLAYER = 'AQAClient262'
BOUNCE = 'minecraft:bounciness'


def require(condition: bool, message: str):
    if not condition:
        raise AssertionError(message)


def native_attribute(state):
    return state.get('attributes', {}).get(BOUNCE)


def wait_ticks(bridge, count: int):
    start = bridge.state()['ticks']
    return bridge.wait(lambda state: state['ticks'] >= start + count, f'{count} client ticks')


def learn(rcon, skill: str, name: str, level: int):
    response = rcon.command(f'adapt claim-adaptation {skill}:{name} {level} force=true player={PLAYER}')
    print('[LEVEL] ' + name + ' ' + str(level) + ': ' + response, flush=True)
    return response


def restore(bridge, rcon, x: float, y: float, z: float):
    bridge.command('release')
    rcon.command(f'effect clear {PLAYER}')
    rcon.command(f'effect give {PLAYER} minecraft:instant_health 1 4 true')
    rcon.command(f'tp {PLAYER} {x:.3f} {y:.3f} {z:.3f} 0 80')
    return bridge.wait(lambda state: state.get('connected') and abs(state['position']['x'] - x) < 0.2
                       and abs(state['position']['z'] - z) < 0.2, 'player teleported to test position')


def collect(bridge, seconds: float):
    deadline = time.monotonic() + seconds
    records = []
    previous_tick = -1
    while time.monotonic() < deadline:
        state = bridge.state()
        require(state.get('connected'), 'Real client remains connected')
        if state['ticks'] != previous_tick:
            previous_tick = state['ticks']
            records.append({key: state.get(key) for key in ['ticks', 'position', 'velocity', 'health', 'onGround', 'attributes']})
        time.sleep(0.025)
    return records


def screenshot(bridge, output: Path, name: str):
    bridge.command('screenshot', name=name)
    state = bridge.wait(lambda value: any(event.get('type') == 'screenshot' and name in event.get('path', '')
                                        for event in value['events']), 'framebuffer screenshot')
    event = next(event for event in reversed(state['events']) if event.get('type') == 'screenshot' and name in event.get('path', ''))
    path = Path(event['path'])
    require(path.exists() and path.stat().st_size > 1000, 'Rendered screenshot has image data')
    return str(path)


def fall(bridge, rcon, x: float, z: float, height: float, screenshot_name=None, output=None):
    restore(bridge, rcon, x, 100, z)
    bridge.wait(lambda state: state['onGround'], 'player settled before fall')
    wait_ticks(bridge, 25)
    bridge.command('clear-events')
    before = bridge.state()
    rcon.command(f'tp {PLAYER} {x:.3f} {100 + height:.3f} {z:.3f} 0 80')
    bridge.wait(lambda state: state['position']['y'] > 100 + height - 0.5, 'natural fall starts above ground')
    samples = []
    landing = None
    deadline = time.monotonic() + 7
    captured = None
    while time.monotonic() < deadline:
        state = bridge.state()
        require(state.get('connected'), 'Client remains connected during fall')
        samples.append({key: state.get(key) for key in ['ticks', 'position', 'velocity', 'health', 'onGround', 'attributes']})
        previous = samples[-2] if len(samples) > 1 else None
        rebound = previous is not None and previous['velocity']['y'] < 0 < state['velocity']['y'] \
            and 100 <= state['position']['y'] <= 101 and previous['position']['y'] <= 101 \
            and 0 < state['ticks'] - previous['ticks'] <= 3
        if landing is None and (state['position']['y'] <= 100.05 or rebound):
            landing = len(samples) - 1
            if screenshot_name:
                captured = screenshot(bridge, output, screenshot_name)
        if landing is not None and state['ticks'] - samples[landing]['ticks'] >= 45:
            break
        time.sleep(0.025)
    require(landing is not None, 'Natural fall reaches the real ground')
    after = bridge.state()
    return {'before': before, 'after': after, 'samples': samples, 'landingIndex': landing,
            'apexAfterLanding': max(sample['position']['y'] for sample in samples[landing:]),
            'upwardVelocityAfterLanding': max(sample['velocity']['y'] for sample in samples[landing:]),
            'minimumHealth': min(sample['health'] for sample in samples), 'screenshot': captured}


def sound_matches(event, name: str, volume: float, pitch: float):
    return event.get('type') == 'sound' and event.get('name') == 'minecraft:' + name \
        and abs(event.get('volume', -1) - volume) < 0.0001 and abs(event.get('pitch', -1) - pitch) < 0.0001


def playback(events, name: str, volume: float, pitch: float):
    return [event for event in events if sound_matches(event, name, volume, pitch)
            and event.get('result') == 'STARTED' and event.get('channelPlaying') is True]


def run(bridge, rcon, output: Path, cases):
    state = bridge.state()
    if not state.get('connected'):
        if state.get('screen'):
            bridge.command('dismiss')
        state = bridge.wait(lambda value: value.get('connected'), 'real client connected', 90)
    bridge.command('dismiss')
    bridge.command('release')
    for command in [
        'forceload add -32 -32 47 47', 'fill -20 98 -20 36 99 36 minecraft:stone',
        'fill -4 99 -4 4 99 4 minecraft:honey_block', 'fill 12 99 -4 20 99 4 minecraft:dirt',
        'setworldspawn 0 100 0', 'time set day', 'weather clear', f'gamemode survival {PLAYER}',
        f'clear {PLAYER}', f'adapt clear adaptations player={PLAYER}']:
        rcon.command(command)
    bridge.wait(lambda value: native_attribute(value) is not None, 'native 26.2 bounciness attribute')
    results = []

    for name in cases:
        results.extend((run_rubber if name == 'rubber-soul' else run_soft_fall)(bridge, rcon, output))
        (output / 'cases.json').write_text(json.dumps(results, indent=2) + '\n')
    return results


def run_rubber(bridge, rcon, output: Path):
    results = []
    learn(rcon, 'kinetics', 'kinetics-rubber-soul', 0)
    bridge.wait(lambda value: native_attribute(value) == 0, 'unlearned bounciness zero')
    control = fall(bridge, rcon, -12, 0, 6)
    (output / 'rubber-stone-control.json').write_text(json.dumps(control, indent=2) + '\n')
    require(control['apexAfterLanding'] < 100.1 and control['upwardVelocityAfterLanding'] < 0.05,
            'Unlearned stone landing does not rebound')
    learn(rcon, 'kinetics', 'kinetics-rubber-soul', 5)
    bridge.wait(lambda value: abs(native_attribute(value) - 0.5) < 0.000001, 'learned passive bounciness 0.5')
    active = fall(bridge, rcon, -12, 0, 6, 'rubber-soul-stone', output)
    (output / 'rubber-stone-active.json').write_text(json.dumps(active, indent=2) + '\n')
    require(active['apexAfterLanding'] > control['apexAfterLanding'] + 0.3, 'Rubber Soul causes a higher real native stone rebound')
    require(active['upwardVelocityAfterLanding'] > 0.1, 'Native client supplies upward rebound velocity')
    results.append({'name': 'kinetics-rubber-soul-native-bounce', 'status': 'passed', 'control': control, 'active': active})
    print('[PASS] Rubber Soul native stone bounce', flush=True)

    learn(rcon, 'kinetics', 'kinetics-rubber-soul', 0)
    bridge.wait(lambda value: native_attribute(value) == 0, 'remove passive bounce before honey control')
    restore(bridge, rcon, 0, 100, 0)
    bridge.wait(lambda value: value['onGround'], 'honey control standing')
    wait_ticks(bridge, 25)
    bridge.command('clear-events')
    bridge.command('keys', jump=True, leaseTicks=4)
    control_samples = collect(bridge, 2)
    control = bridge.state()
    require(any(not sample['onGround'] and sample['position']['y'] > 100 for sample in control_samples),
            'Unlearned real key input produces a honey jump')
    require(control['onGround'] and abs(control['position']['y'] - 99.9375) < 0.01,
            'Unlearned honey jump returns to the actual surface')
    require(all(native_attribute(sample) == 0 for sample in control_samples), 'Unlearned honey landing has no bounciness bonus')
    require(not any(sound_matches(event, 'block.slime_block.fall', 0.5, 1.4) for event in control['events']),
            'Unlearned honey landing has no Rubber Soul sound')
    require(not any(event.get('type') == 'particle' and event.get('name') == 'minecraft:item_slime' for event in control['events']),
            'Unlearned honey landing has no slime particles')
    learn(rcon, 'kinetics', 'kinetics-rubber-soul', 5)
    bridge.wait(lambda value: abs(native_attribute(value) - 0.5) < 0.000001, 'learned honey baseline 0.5')
    wait_ticks(bridge, 25)
    bridge.command('clear-events')
    before = bridge.state()
    bridge.command('keys', jump=True, leaseTicks=4)
    bridge.wait(lambda value: not value['onGround'], 'real key input causes honey jump')
    landed = bridge.wait(lambda value: native_attribute(value) > 0.99, 'honey landing applies native capped springload')
    require(abs(native_attribute(landed) - 1.0) < 0.000001, 'Springload respects native maximum bounciness 1.0')
    captured = screenshot(bridge, output, 'rubber-soul-honey-particles')
    played = bridge.wait(lambda value: bool(playback(value['events'], 'block.slime_block.fall', 0.5, 1.4)),
                         'Rubber Soul sound plays through an actual OpenAL channel')
    require(any(event.get('type') == 'particle' and event.get('name') == 'minecraft:item_slime' and event.get('created')
                for event in played['events']), 'Native particle factory creates Rubber Soul slime particles')
    rendered = bridge.wait(lambda value: any(event.get('type') == 'particle' and event.get('name') == 'minecraft:item_slime'
                           and event.get('created') and event.get('rendered') and event.get('renderedFrames', 0) > 0 for event in value['events']),
                           'Native renderer builds Rubber Soul slime particle geometry')
    expired = bridge.wait(lambda value: abs(native_attribute(value) - 0.5) < 0.000001, 'springload expires back to passive value', 5)
    surface_trials = []
    for surface, x, z, material in [('slime', 0.5, 16.5, 'minecraft:slime_block'), ('bed', 16.5, 16.5, 'minecraft:red_bed')]:
        if surface == 'slime':
            rcon.command('fill -4 99 12 4 99 20 minecraft:slime_block')
        else:
            rcon.command('setblock 16 99 16 minecraft:red_bed[part=foot,facing=south]')
            rcon.command('setblock 16 99 17 minecraft:red_bed[part=head,facing=south]')
        require('Test passed' in rcon.command(f'execute if block {int(x)} 99 {int(z)} {material}'),
                surface + ' landing surface exists')
        restore(bridge, rcon, -12, 100, 0)
        bridge.wait(lambda value: value['onGround'] and abs(native_attribute(value) - 0.5) < 0.000001,
                    'springload expired before ' + surface + ' trial', 5)
        bridge.command('clear-events')
        rcon.command(f'tp {PLAYER} {x:.3f} 104.000 {z:.3f} 0 80')
        landing = bridge.wait(lambda value: native_attribute(value) > 0.99, surface + ' rebound landing activates springload')
        visible = screenshot(bridge, output, 'rubber-soul-' + surface + '-particles')
        feedback = bridge.wait(lambda value: bool(playback(value['events'], 'block.slime_block.fall', 0.5, 1.4)),
                               surface + ' landing sound actually plays')
        surface_trials.append({'surface': surface, 'landing': landing, 'feedback': feedback, 'screenshot': visible})
    restore(bridge, rcon, -12, 100, 0)
    bridge.wait(lambda value: value['onGround'] and abs(native_attribute(value) - 0.5) < 0.000001,
                'springload expires after bouncy surface trials', 5)
    learn(rcon, 'kinetics', 'kinetics-rubber-soul', 0)
    removed = bridge.wait(lambda value: native_attribute(value) == 0, 'unlearning removes native bounciness')
    results.append({'name': 'kinetics-rubber-soul-feedback', 'status': 'passed', 'control': control,
                    'controlSamples': control_samples, 'before': before, 'landed': landed, 'played': played,
                    'rendered': rendered, 'expired': expired, 'removed': removed, 'surfaceTrials': surface_trials, 'screenshot': captured})
    print('[PASS] Rubber Soul springload, playback, particles, expiry, and unlearning', flush=True)

    return results


def run_soft_fall(bridge, rcon, output: Path):
    results = []
    learn(rcon, 'excavation', 'excavation-soft-fall', 0)
    control = fall(bridge, rcon, 16, 0, 8)
    (output / 'soft-fall-control.json').write_text(json.dumps(control, indent=2) + '\n')
    require(control['minimumHealth'] < control['before']['health'], 'Unlearned dirt fall deals real fall damage')
    learn(rcon, 'excavation', 'excavation-soft-fall', 5)
    active = fall(bridge, rcon, 16, 0, 8, 'soft-fall-particles', output)
    (output / 'soft-fall-active.json').write_text(json.dumps(active, indent=2) + '\n')
    require(active['minimumHealth'] >= active['before']['health'], 'Learned dirt fall prevents all damage')
    events = active['after']['events']
    for name, volume, pitch in [('block.rooted_dirt.break', 0.6, 0.8), ('block.sand.break', 0.4, 0.8), ('block.wool.fall', 0.5, 1.2)]:
        require(bool(playback(events, name, volume, pitch)), name + ' plays on an actual OpenAL channel')
        require(not any(sound_matches(event, name, volume, pitch) for event in control['after']['events']),
                name + ' authored note is absent in control')
    for particle in ['minecraft:cloud', 'minecraft:block']:
        require(any(event.get('type') == 'particle' and event.get('name') == particle and event.get('created') and event.get('rendered') and event.get('renderedFrames', 0) > 0 for event in events),
                'Native renderer builds geometry for ' + particle)
    require(active['after']['particleRenderLayers'] > active['before']['particleRenderLayers'], 'Soft Fall particle geometry renders')
    learn(rcon, 'excavation', 'excavation-soft-fall', 0)
    results.append({'name': 'excavation-soft-fall-client', 'status': 'passed', 'control': control, 'active': active})
    print('[PASS] Soft Fall damage, all three real playback channels, and rendered particles', flush=True)
    return results
