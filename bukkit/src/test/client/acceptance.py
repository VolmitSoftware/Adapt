"""Resource pack menu acceptance run: hidden clients against Multiplexor servers, one folder of screenshots per server
platform, server version and client version. See --help."""
from __future__ import annotations

import argparse
import json
import shutil
import signal
import sys
import time
from pathlib import Path

HERE: Path = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE / 'ui'))
sys.path.insert(0, str(HERE))
import pack_scenarios  # noqa: E402
import uiclient  # noqa: E402

PREFIX: str = 'rp-acc-'
OUTPUT_ROOT: Path = Path.home() / 'Desktop' / 'Adapt' / 'resourcepack' / 'acceptance'
WIDTH: int = 1920
HEIGHT: int = 1080
JOIN_WATCH_SECONDS: float = 25.0
TOAST_SECONDS: float = 11.0
DEFAULT_PROFILES: tuple[str, ...] = ('pack', 'declined', 'vanilla', 'modded')

HELP: str = '''
One command, one folder per combination:
  <output>/<platform>-<server version>-<client version>/<profile>/
    screenshots/<scenario>-<step>-gui<scale>.png   what the owner judges by eye
    motion/                                       frame sequences, a contact sheet PNG and a GIF per animated page
    states/                                       title runs and slot items of every menu opened, as JSON
    steps.json                                    every step: pass, fail, skip (with the reason), shot, info
    summary.json, report.json, client.log, server.log, multiplexor.log
  <output>/<platform>-<server version>-<client version>/summary.json   all profiles of that combination

Profiles (each is one server and one hidden client):
  pack       client without the Adapt mod, accepts the pack: join, what a pack viewer gets (full book, top rows only
             or chest), every screen, animation, HUD, Mutate, safety
  declined   client without the Adapt mod, declines the pack: chest menus and safety
  The pack and declined profiles ask with /adapt pack offer when no pack came at join.
  vanilla    client without the Adapt mod, no prompt answered: chest menus and safety (regression); on a mod server
             also no pack prompt, no server pack and no Adapt pack line in the server log
  modded     client with the Adapt mod: never offered the pack, native screens unchanged, an XP line and a level
             toast on the mod's own HUD
  selftest   harness proof without Adapt: the spike plugin and test pack from --spike (Paper only)
Only Bukkit servers (paper, folia, spigot) serve the pack; pack, declined and selftest are not run on mod servers.

Clients: --client 26.3 joins natively. --client 26.1.2 joins a Bukkit server of --server-mc (default 26.3) through
ViaVersion and ViaBackwards, and a Fabric, Forge or NeoForge server of its own version 26.1.2.
The vanilla viewer client is Fabric loader plus the harness bridge only, so every server sees a client without Adapt.

Gate: pack, declined and vanilla start with server-health, which fails on any NoClassDefFoundError,
NoSuchMethodError, NoSuchFieldError, ClassNotFoundException or failed plugin enable in the server log, then opens the
Skills chest. The default platforms are paper,spigot so Spigot is always in the run.

Scales: --scale 2,3,auto. Per-scale scenarios (the screens) run once per scale on the same client.

Server jars: Bukkit platforms take build/libs/Adapt v* [CraftBukkit] *.jar of this checkout unless --server-adapt
names one; mod platforms take adapters/<loader>/build/<mc>/libs. A scenario whose Adapt feature is not in the server
jar (pack delivery, pack screens, platform pack surface, pack HUD) is skipped with that reason in steps.json.

Safety: every server and client instance is named rp-acc-*, is created by this run and is deleted when the profile
ends, also on failure, Ctrl-C or SIGTERM. Nothing else is touched. Clients are hidden, unfocused and muted, with a
watchdog that kills a client the moment its window shows or takes focus. --cleanup removes rp-acc-* leftovers only.

Examples:
  python3 bukkit/src/test/client/acceptance.py --platform paper --client 26.3 --profiles vanilla
  python3 bukkit/src/test/client/acceptance.py --platform paper,folia,spigot --client 26.1.2 --scale 2,3,auto
  python3 bukkit/src/test/client/acceptance.py --platform paper --profiles selftest --spike <dir with plugin/SpikeLook.jar, pack/, scenes/>
  python3 bukkit/src/test/client/acceptance.py --platform all --server-adapt /path/to/Adapt.jar --scenarios skills,tooltip
  python3 bukkit/src/test/client/acceptance.py --list
Run long matrices detached under caffeinate -ims; one hidden client runs on the machine at a time.
'''


def interrupt(signum: int, frame: object) -> None:
    raise KeyboardInterrupt('signal ' + str(signum))


def scale_value(scale: str) -> int:
    return 0 if scale == 'auto' else int(scale)


def server_version(platform: str, client: str, requested: str) -> str:
    return requested if platform in uiclient.BUKKIT_PLATFORMS else client


def server_jar(target: uiclient.Target, profile: str, override: Path | None) -> Path | None:
    if profile == 'selftest':
        return None
    if override is not None:
        return override
    if target.bukkit:
        return uiclient.adapt_plugin_jar(target.server_minecraft)
    return uiclient.adapt_mod_jar(uiclient.Target(target.server_platform, target.server_minecraft))


def client_target(platform: str, client: str, server_mc: str, profile: str) -> uiclient.Target:
    loader: str = platform if profile == 'modded' and platform not in uiclient.BUKKIT_PLATFORMS else 'fabric'
    return uiclient.Target(loader, client, platform, server_mc)


def watch_join(session: uiclient.Session, profile: str, output: Path, offered: bool) -> bool:
    """Joins and answers the server pack prompt the way the profile wants. Returns whether a pack is loaded. When the
    server delivers packs but none came at join, the pack and declined profiles ask once with /adapt pack offer."""
    deadline: float = time.monotonic() + 240
    joined_at: float = 0.0
    loaded: bool = False
    asked: bool = profile not in ('pack', 'declined')
    while time.monotonic() < deadline:
        state: dict = session.state()
        prompt: dict | None = state['screen'] if uiclient.is_pack_prompt(state['screen']) else None
        if prompt is not None:
            (output / 'prompt-seen').write_text(json.dumps(prompt, indent=1))
            accept: bool = profile == 'pack'
            session.op('screenshot', timeout=60, path=str(output / 'screenshots' / 'join-prompt.png'))
            session.answer_pack_prompt(accept=accept)
            (output / 'offer-path').write_text('command' if asked and profile in ('pack', 'declined') else 'join')
            if accept:
                loaded = True
            else:
                (output / 'prompt-declined').write_text('')
            continue
        connected: bool = state['player']['connected'] and state['player'].get('name') == uiclient.PLAYER
        if connected and not joined_at:
            joined_at = time.monotonic()
        if joined_at and offered and not loaded and not asked and time.monotonic() - joined_at > JOIN_WATCH_SECONDS:
            session.command('adapt pack offer')
            asked = True
            joined_at = time.monotonic()
            continue
        if joined_at and (loaded or (output / 'prompt-declined').exists() or not offered
                          or time.monotonic() - joined_at > JOIN_WATCH_SECONDS):
            return loaded
        time.sleep(0.5)
    raise TimeoutError('Client did not join')


def remembering(setup, holder: dict[str, Path]):
    def wrapped(server: Path) -> None:
        holder['server'] = server
        if setup is not None:
            setup(server)
    return wrapped


def spike_setup(spike: Path, output: Path):
    def setup(server: Path) -> None:
        data: Path = server / 'plugins' / 'SpikeLook'
        data.mkdir(parents=True, exist_ok=True)
        (data / 'port.txt').write_text(str(uiclient.free_port()))
        shutil.copytree(spike / 'scenes', data / 'scenes')
        (output / 'spike-pack-dir').write_text(str((spike / 'pack').resolve()))
    return setup


def adapt_setup():
    """Gives the Adapt pack listener a free loopback port, so parallel servers never collide on the default, and serves the
    baked pack from it instead of the released one."""
    def setup(server: Path) -> None:
        config: Path = server / 'plugins' / 'Adapt' / 'adapt.toml'
        config.parent.mkdir(parents=True, exist_ok=True)
        port: int = uiclient.free_port()
        config.write_text(f'[pack]\nbind = "127.0.0.1"\nport = {port}\npublicUrl = "http://127.0.0.1:{port}"\nreleaseUrl = ""\n')
    return setup


def run_profile(platform: str, client: str, server_mc: str, profile: str, scales: list[str], names: tuple[str, ...],
                args: argparse.Namespace, combo: Path) -> dict[str, dict]:
    output: Path = combo / profile
    if output.exists():
        shutil.rmtree(output)
    (output / 'screenshots').mkdir(parents=True)
    target: uiclient.Target = client_target(platform, client, server_mc, profile)
    try:
        jar: Path | None = server_jar(target, profile, args.server_adapt)
        client_jar: Path | None = None
        if profile == 'modded':
            client_jar = args.client_adapt or uiclient.adapt_mod_jar(uiclient.Target(target.loader, client))
    except SystemExit as missing:
        return pack_scenarios.skipped_all(names, f'jar missing: {missing}')
    extra: list[Path] = []
    setup = adapt_setup() if target.bukkit else None
    if profile == 'selftest':
        if args.spike is None:
            return pack_scenarios.skipped_all(names, 'no --spike folder given')
        if platform != 'paper':
            return pack_scenarios.skipped_all(names, 'the spike plugin is Paper-only')
        extra = [args.spike / 'plugin' / 'SpikeLook.jar']
        setup = spike_setup(args.spike, output)
    features: pack_scenarios.Features = pack_scenarios.Features.of(jar)
    (output / 'features.json').write_text(json.dumps(features.__dict__, indent=1))
    results: dict[str, dict] = {}
    holder: dict[str, Path] = {}
    setup = remembering(setup, holder)
    try:
        with uiclient.launch(target, output=output, client_adapt=client_jar or Path('none'), server_adapt=jar or Path('none'),
                             server_mods=extra, prefix=PREFIX, gui_scale=scale_value(scales[0]), server_setup=setup) as session:
            loaded: bool = watch_join(session, profile, output, features.delivery or profile == 'modded')
            session.rcon('difficulty peaceful')
            session.resize(WIDTH, HEIGHT)
            session.wait(lambda state: state['window']['width'] == WIDTH, 'resize', 15)
            time.sleep(TOAST_SECONDS)
            session.screenshot('joined')
            context = pack_scenarios.Context(session, output, profile, features, pack_loaded=loaded, server=holder.get('server'))

            def apply_scale(scale: str) -> None:
                if session.state()['window']['guiScaleOption'] != scale_value(scale):
                    session.gui_scale(scale_value(scale))
                    time.sleep(0.5)

            results = pack_scenarios.run(context, names, scales, apply_scale)
    except Exception as failure:  # noqa: BLE001 - the profile's report.json holds the trace; the matrix goes on
        uiclient.log(f'{profile} failed: {type(failure).__name__}: {failure}')
        results['launch'] = {'status': 'fail', 'error': f'{type(failure).__name__}: {failure}'[:1500]}
    (output / 'summary.json').write_text(json.dumps(results, indent=1) + '\n')
    return results


def main() -> None:
    signal.signal(signal.SIGTERM, interrupt)
    signal.signal(signal.SIGINT, interrupt)
    parser = argparse.ArgumentParser(description='Resource pack menu acceptance run with hidden real clients.',
                                     epilog=HELP, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--platform', default='paper,spigot',
                        help='comma list of ' + ', '.join(uiclient.SERVER_PLATFORMS) + ', or all (default paper,spigot)')
    parser.add_argument('--client', default='26.3', help='comma list of client versions: 26.3, 26.2, 26.1.2, 1.21.1, 1.20.1 (default 26.3)')
    parser.add_argument('--server-mc', default='26.3', help='Bukkit server version (default 26.3); mod servers match the client')
    parser.add_argument('--scale', default='2', help='comma list of GUI scales: 2, 3, auto (default 2)')
    parser.add_argument('--profiles', default=','.join(DEFAULT_PROFILES),
                        help='comma list of ' + ', '.join(pack_scenarios.PROFILES) + ' (default ' + ','.join(DEFAULT_PROFILES) + ')')
    parser.add_argument('--scenarios', default='', help='comma list; runs only these scenarios inside the chosen profiles')
    parser.add_argument('--server-adapt', type=Path, help='Adapt jar for the server instead of this checkout\'s build')
    parser.add_argument('--client-adapt', type=Path, help='Adapt mod jar for the modded profile\'s client')
    parser.add_argument('--spike', type=Path, help='folder with plugin/SpikeLook.jar, pack/ and scenes/ for the selftest profile')
    parser.add_argument('--output', type=Path, default=OUTPUT_ROOT, help='output root (default ' + str(OUTPUT_ROOT) + ')')
    parser.add_argument('--list', action='store_true', help='print the profiles and their scenarios, then exit')
    parser.add_argument('--cleanup', action='store_true', help='delete rp-acc-* servers, client instances and clients, then exit')
    args = parser.parse_args()
    if args.list:
        for name, scenarios in pack_scenarios.PROFILES.items():
            print(f'{name}: {", ".join(scenarios)}')
        return
    if args.cleanup:
        uiclient.cleanup(PREFIX)
        return
    platforms: list[str] = list(uiclient.SERVER_PLATFORMS) if args.platform == 'all' else pack_scenarios.pattern_list(args.platform)
    clients: list[str] = pack_scenarios.pattern_list(args.client)
    scales: list[str] = pack_scenarios.pattern_list(args.scale)
    profiles: list[str] = pack_scenarios.pattern_list(args.profiles)
    only: set[str] = set(pack_scenarios.pattern_list(args.scenarios))
    for value, allowed, label in ((platforms, uiclient.SERVER_PLATFORMS, 'platform'), (clients, tuple(uiclient.build_bridge.TARGETS), 'client'),
                                  (scales, ('2', '3', 'auto'), 'scale'), (profiles, tuple(pack_scenarios.PROFILES), 'profile'),
                                  (sorted(only), tuple(pack_scenarios.SCENARIOS), 'scenario')):
        unknown: list[str] = [entry for entry in value if entry not in allowed]
        if unknown:
            parser.error(f'unknown {label}: {", ".join(unknown)}')
    overall: dict[str, dict] = {}
    for platform in platforms:
        for client in clients:
            server_mc: str = server_version(platform, client, args.server_mc)
            combo: Path = args.output / f'{platform}-{server_mc}-{client}'
            combo.mkdir(parents=True, exist_ok=True)
            summary: dict[str, dict] = {}
            for profile in profiles:
                if profile in pack_scenarios.BUKKIT_PROFILES and platform not in uiclient.BUKKIT_PLATFORMS:
                    uiclient.log(f'{combo.name} {profile}: not run, mod servers serve no pack')
                    continue
                names: tuple[str, ...] = tuple(name for name in pack_scenarios.PROFILES[profile] if not only or name in only)
                if names:
                    uiclient.log(f'{combo.name} {profile}: {", ".join(names)}')
                    summary[profile] = run_profile(platform, client, server_mc, profile, scales, names, args, combo)
            (combo / 'summary.json').write_text(json.dumps(summary, indent=1) + '\n')
            overall[combo.name] = summary
    for combo_name, summary in overall.items():
        for profile, results in summary.items():
            counts: dict[str, int] = {}
            for result in results.values():
                counts[result['status']] = counts.get(result['status'], 0) + 1
            print(f'{combo_name} {profile}: ' + ', '.join(f'{count} {status}' for status, count in sorted(counts.items())))
    print('Output: ' + str(args.output))


if __name__ == '__main__':
    main()
