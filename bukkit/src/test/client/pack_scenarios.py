"""Resource pack menu scenarios for a hidden client, driven by acceptance.py.

Each scenario takes a Context, records named screenshots and assertions in its step log, and raises Skip with a reason
when the server or client lacks what it exercises. Profiles group the scenarios one server and client launch runs.
"""
from __future__ import annotations

import hashlib
import io
import json
import lzma
import re
import shutil
import subprocess
import time
import zipfile
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable

import uiclient

PLAYER: str = uiclient.PLAYER
PACKED_RUNTIME: str = 'META-INF/volmit/runtime.jar.xz'
DEFAULT_FONTS: tuple[str, ...] = ('minecraft:default', 'minecraft:uniform', 'None', 'null')
SKILL_PAGES: tuple[str, ...] = ('herbalism', 'hunter', 'chronos')
RECIPE_ADAPTATION: str = 'herbalism/cobweb'
PREFERENCE_ADAPTATION: str = 'hunter/resistance'
NEXT_PAGE: tuple[str, ...] = ('next', 'page', '>>', '→')
LEARN: tuple[str, ...] = ('learn', 'unlock')
UNLEARN: tuple[str, ...] = ('unlearn', 'forget', 'refund')
SEAL: tuple[str, ...] = ('seal', 'mutate', 'confirm')
POWER_CHIP: re.Pattern = re.compile(r'^(\d+)/(\d+) ')
PACK_MODEL: str = 'adapt:adapt_ui/'
LAYOUT: Path = Path(__file__).resolve().parents[4] / 'core/src/main/resources/art/arcane/adapt/pack/layout/layout.json'
CHEST_SLOTS: int = 54
CARRIER: str = 'minecraft:paper'
PARTNER_PAGES: int = 4
PERMANENT: str = 'Permanent'
MOTION_TICKS: tuple[int, ...] = (40, 100)
MOTION_PIXELS: int = 20
NATIVE_DIG_TICKS: int = 12


class Skip(Exception):
    """The scenario cannot run here; the message says why."""


class Failed(AssertionError):
    """An assertion of the scenario did not hold."""


@dataclass
class Features:
    """What the server's Adapt jar contains, read from its class entries."""
    adapt: bool = False
    delivery: bool = False
    screens: bool = False
    surface: bool = False
    hud: bool = False

    @staticmethod
    def of(jar: Path | None) -> 'Features':
        if jar is None or not jar.is_file():
            return Features()
        with zipfile.ZipFile(jar) as archive:
            names: list[str] = archive.namelist()
            if PACKED_RUNTIME in names:
                with zipfile.ZipFile(io.BytesIO(lzma.decompress(archive.read(PACKED_RUNTIME)))) as runtime:
                    names += runtime.namelist()
        return Features(adapt=any(name.startswith('art/arcane/adapt/') for name in names),
                        delivery=any(name.endswith('/pack/link/PackDelivery.class') for name in names),
                        screens=any('/adapt/pack/screen/' in name for name in names),
                        surface=any(name.endswith('BukkitPackWindows.class') for name in names),
                        hud=any('/adapt/pack/hud/' in name for name in names))


@dataclass
class Context:
    session: uiclient.Session
    output: Path
    profile: str
    features: Features
    scale: str = '2'
    pack_loaded: bool = False
    server: Path | None = None
    current: str = ''
    steps: list[dict] = field(default_factory=list)

    def record(self, step: str, status: str, detail: object = None, screenshot: Path | None = None) -> dict:
        entry: dict = {'scenario': self.current, 'step': step, 'gui': self.scale, 'status': status, 'time': time.time()}
        if detail is not None:
            entry['detail'] = detail
        if screenshot is not None:
            entry['screenshot'] = str(screenshot)
        self.steps.append(entry)
        (self.output / 'steps.json').write_text(json.dumps(self.steps, indent=1) + '\n')
        return entry

    def check(self, step: str, condition: bool, detail: object = None) -> None:
        self.record(step, 'pass' if condition else 'fail', detail)
        if not condition:
            raise Failed(f'{step}: {json.dumps(detail)[:600]}')

    def shot(self, name: str, settle: float = 0.6, pointer: bool = False) -> Path:
        """Screenshots the window; the pointer is moved off the menu first unless the shot is of a hovered slot."""
        if not pointer:
            self.session.op('mouse-move', x=1, y=1)
        time.sleep(settle)
        path: Path = self.output / 'screenshots' / f'{self.current}-{name}-gui{self.scale}.png'
        self.session.op('screenshot', timeout=60, path=str(path))
        self.record(name, 'shot', screenshot=path)
        return path


def require(condition: bool, reason: str) -> None:
    if not condition:
        raise Skip(reason)


def screen(context: Context) -> dict | None:
    return context.session.state()['screen']


def slots(found: dict | None) -> list[dict]:
    return [entry for entry in (found or {}).get('slots', []) if entry.get('item')]


def menu_slots(found: dict | None) -> list[dict]:
    """Slots of the menu itself, without the 36 player inventory slots below it."""
    every: list[dict] = (found or {}).get('slots', [])
    top: list[dict] = every[:max(0, len(every) - 36)]
    return [entry for entry in top if entry.get('item')]


def layout_control(found: dict | None, screen_name: str, role: str, index: int = 0) -> dict | None:
    """The slot the pack layout gives role on screen_name. Book controls are painted in the title over blank items, so
    their place in the layout is what finds them."""
    reference: str = json.loads(LAYOUT.read_text())['screens'][screen_name]['slots'][role][index]
    area, number = reference.split(':')
    window: int = int(number) + (CHEST_SLOTS if area == 'lower' else 0)
    return next((entry for entry in slots(found) if entry['index'] == window), None)


def layout_slots(screen_name: str) -> set[int]:
    """The window slots the pack layout gives any control role on screen_name (search, chips, foot ...)."""
    found: set[int] = set()
    for references in json.loads(LAYOUT.read_text())['screens'][screen_name]['slots'].values():
        for reference in references:
            area, number = reference.split(':')
            found.add(int(number) + (CHEST_SLOTS if area == 'lower' else 0))
    return found


def named(found: dict | None, needles: tuple[str, ...]) -> dict | None:
    for entry in slots(found):
        name: str = entry.get('name', '').lower()
        if any(needle in name for needle in needles):
            return entry
    return None


def summary(found: dict | None) -> dict:
    if not found:
        return {'screen': None}
    return {'class': found['class'], 'title': found['title'], 'titleRuns': found.get('titleRuns', []),
            'slots': [{key: entry[key] for key in ('index', 'item', 'count', 'name', 'model') if key in entry}
                      for entry in slots(found)]}


def fonts(found: dict | None) -> list[str]:
    return sorted({run.get('font', '') for run in (found or {}).get('titleRuns', [])} - set(DEFAULT_FONTS))


def models(found: dict | None) -> list[str]:
    """Item models that differ from the stack's own item, which every 26.x stack carries by default. Clients before 26.x
    report no item model: there a pack slot is the carrier item, which draws through custom model data."""
    entries: list[dict] = menu_slots(found)
    if entries and not any('model' in entry for entry in entries):
        return sorted({f"{entry['item']} (carrier)" for entry in entries if entry['item'] == CARRIER})
    return sorted({entry['model'] for entry in entries if entry.get('model') and entry['model'] != entry['item']})


def close(context: Context) -> None:
    """Closes the open screen the way a player does (Escape), so the server sees the menu close too."""
    for _ in range(3):
        current: dict | None = screen(context)
        if current is None:
            return
        if uiclient.is_pack_prompt(current) or uiclient.screen_is(current, 'DeathScreen'):
            return
        context.session.key('escape')
        time.sleep(0.5)
    context.session.op('close-screen')


def open_menu(context: Context, command: str, label: str) -> dict:
    close(context)
    time.sleep(0.3)
    context.session.command(command)
    try:
        found: dict = context.session.wait_screen(cls='AbstractContainerScreen', timeout=15)['screen']
    except RuntimeError as failure:
        raise Failed(f'{label}: /{command} opened no menu: {failure}') from failure
    time.sleep(0.8)
    found = screen(context) or found
    (context.output / 'states').mkdir(exist_ok=True)
    (context.output / 'states' / f'{context.current}-{label}-gui{context.scale}.json').write_text(json.dumps(summary(found), indent=1))
    return found


def plain_chest_titles(context: Context) -> bool:
    """Spigot's API opens chest windows with legacy titles, so a pack window that is a plain chest there loses its fonts."""
    return context.session.target.server_platform == 'spigot'


def check_title(context: Context, label: str, found: dict, chest: bool) -> None:
    if chest and plain_chest_titles(context) and not fonts(found):
        context.check(f'{label} title is readable plain text on Spigot',
                      not any('\ue000' <= character <= '\uf8ff' for character in found['title']), found['title'])
    else:
        context.check(f'{label} title has pack font runs', bool(fonts(found)), {'fonts': fonts(found), 'title': found['title']})


def check_look(context: Context, label: str, found: dict, chest: bool = False) -> None:
    """Book pages carry pack fonts in the title and pack item models in the slots; chest menus carry neither."""
    if context.pack_loaded:
        check_title(context, label, found, chest)
        context.check(f'{label} slots use pack item models', bool(models(found)), {'models': models(found)})
    else:
        # Before 26.x a chest menu's own paper items read as carriers too; there the title fonts tell the two apart.
        drawn: list[str] = [model for model in models(found) if not model.endswith('(carrier)')]
        context.check(f'{label} is a plain chest menu', not fonts(found) and not drawn,
                      {'fonts': fonts(found), 'models': models(found), 'class': found['class']})


def require_pages(context: Context) -> None:
    require(context.features.adapt, 'server has no Adapt jar')
    require(context.profile != 'pack' or context.pack_loaded, 'no pack was offered at join; the vanilla profile covers chest menus')
    require(context.profile != 'declined' or (context.output / 'prompt-declined').exists(),
            'no pack was offered at join; the vanilla profile covers chest menus')
    if context.pack_loaded:
        require(context.features.screens, 'server Adapt jar has no pack screens (art.arcane.adapt.pack.screen)')


def admin(context: Context, command: str) -> str:
    """Runs an Adapt admin command on the console and fails the scenario when Adapt rejects it."""
    response: str = context.session.rcon(command)
    if re.search(r'Unexpected argument|Unknown|Incorrect argument|No player|Missing', response):
        raise Failed(f'/{command}: {response[:400]}')
    return response


def server_inventory(context: Context) -> str:
    """Every inventory entry the server holds, one console query per entry (the whole list comes back truncated).
    Folia has no /data command; there the client's synced inventory stands in, and the step log says so."""
    entries: list[str] = []
    for index in range(64):
        response: str = context.session.rcon(f'data get entity {PLAYER} Inventory[{index}]')
        if 'entity data' not in response:
            if 'Unknown or incomplete command' in response:
                context.record('server inventory', 'info', 'no /data command on this server; using the client inventory')
                return 'client ' + json.dumps(client_items(context))
            if not re.search(r'(?i)no elements|not found', response):
                raise Failed('the server console cannot read the inventory: ' + response[:300])
            break
        entries.append(response.split('entity data:', 1)[1].strip())
    return '\n'.join(entries)


def inventory_hash(context: Context) -> str:
    return hashlib.sha1(server_inventory(context).encode()).hexdigest()


def client_items(context: Context) -> list[tuple[str, int]]:
    return sorted((entry['item'], entry['count']) for entry in context.session.state()['player'].get('inventory', []))


def seed_inventory(context: Context) -> None:
    context.session.rcon(f'clear {PLAYER}')
    context.session.rcon(f'give {PLAYER} minecraft:oak_log 7')
    context.session.rcon(f'give {PLAYER} minecraft:iron_sword 1')
    context.session.rcon(f'give {PLAYER} minecraft:bread 12')
    time.sleep(0.5)


def wait_ticks(context: Context, count: int) -> None:
    start: int = context.session.state()['ticks']
    context.session.wait(lambda state: state['ticks'] >= start + count, f'{count} client ticks', 30)


def hover(context: Context, entry: dict) -> dict:
    context.session.op('mouse-move', x=entry['x'], y=entry['y'])
    time.sleep(0.4)
    return screen(context) or {}


def click(context: Context, entry: dict, button: str = 'left', mods: list[str] | None = None) -> dict:
    context.session.click(entry['x'], entry['y'], button=button, mods=mods or [])
    time.sleep(1.0)
    return screen(context) or {}


# Server health and join -------------------------------------------------------------------------------------------

LINKAGE: re.Pattern = re.compile(r'NoClassDefFoundError|NoSuchMethodError|NoSuchFieldError|ClassNotFoundException|'
                                 r'Error occurred while enabling|Platform failed to start|has failed to register events|'
                                 r'Could not load .*Adapt|Failed to load .*adapt', re.IGNORECASE)


def server_health(context: Context) -> None:
    """Adapt enabled on this server: no linkage error and no failed enable anywhere in the server log."""
    require(context.features.adapt, 'server has no Adapt jar')
    require(context.server is not None, 'no server folder for this session')
    log_text: str = (context.server / 'logs' / 'latest.log').read_text(errors='replace')
    problems: list[str] = [line.strip()[:300] for line in log_text.splitlines() if LINKAGE.search(line)]
    opened: str = ''
    try:
        found: dict = open_menu(context, 'adapt gui target=main', 'skills')
        opened = found['class']
    except Failed as failure:
        context.record('Skills menu', 'info', str(failure)[:300])
    context.shot('skills')
    context.check('server log has no linkage error or failed Adapt enable', not problems, problems[:12])
    context.check('Skills menu opens', bool(opened), opened)


def pack_viewer_mode(context: Context) -> None:
    """Records what a client with the pack gets here: the full book (pack art in the menu and the 36 lower slots), the
    top rows only, or the chest menu."""
    require(context.features.adapt, 'server has no Adapt jar')
    require(context.features.delivery, 'server Adapt jar has no pack delivery (art.arcane.adapt.pack.link)')
    if not context.pack_loaded:
        context.record('pack viewer gets', 'info', 'chest: no pack was offered')
        raise Skip('no pack was offered, so a pack viewer gets the chest menus')
    found: dict = open_menu(context, 'adapt gui target=main', 'skills')
    every: list[dict] = [entry for entry in found.get('slots', []) if entry.get('item')]
    lower: list[dict] = every[-36:] if len(found.get('slots', [])) > 36 else []
    lower_models: list[str] = sorted({entry['model'] for entry in lower if entry.get('model') and entry['model'] != entry['item']})
    top: bool = bool(fonts(found) or models(found))
    mode: str = 'full book' if top and lower_models else 'top rows only' if top else 'chest'
    context.record('pack viewer gets', 'info', {'mode': mode, 'fonts': fonts(found), 'models': models(found)[:12],
                                                'lowerModels': lower_models[:12]})
    context.shot(mode.replace(' ', '-'))


# Join and prompt ------------------------------------------------------------------------------------------------------

def join_pack_accepted(context: Context) -> None:
    require(context.features.delivery, 'server Adapt jar has no pack delivery (art.arcane.adapt.pack.link)')
    require(context.pack_loaded, 'no pack was offered at join')
    state: dict = context.session.state()
    context.check('server pack selected on the client', any(uiclient.is_server_pack(pack) for pack in state['resourcePacks']),
                  state['resourcePacks'])
    context.shot('after-reload')


def join_pack_declined(context: Context) -> None:
    require(context.features.delivery, 'server Adapt jar has no pack delivery (art.arcane.adapt.pack.link)')
    require(context.session.output.joinpath('prompt-declined').exists(), 'no pack was offered at join')
    state: dict = context.session.state()
    context.check('no server pack on the client', not any(uiclient.is_server_pack(pack) for pack in state['resourcePacks']),
                  state['resourcePacks'])
    found: dict = open_menu(context, 'adapt gui target=main', 'skills')
    check_look(context, 'skills after decline', found)
    context.shot('skills-chest')


PACK_LINE: re.Pattern = re.compile(r'Adapt pack|Adapt resource pack', re.IGNORECASE)


def mod_server_no_pack(context: Context) -> None:
    """A mod server does no pack work: a vanilla client gets no prompt and no server pack, and the log has no pack line."""
    require(not context.session.target.bukkit, 'Bukkit servers offer the pack')
    require(context.server is not None, 'no server folder for this session')
    state: dict = context.session.state()
    context.check('no pack prompt for a vanilla client', not context.session.output.joinpath('prompt-seen').exists())
    context.check('no server pack on the client', not any(uiclient.is_server_pack(pack) for pack in state['resourcePacks']),
                  state['resourcePacks'])
    log_text: str = (context.server / 'logs' / 'latest.log').read_text(errors='replace')
    lines: list[str] = [line.strip()[:300] for line in log_text.splitlines() if PACK_LINE.search(line)]
    context.check('server log has no Adapt pack line', not lines, lines[:12])


def modded_not_offered(context: Context) -> None:
    prompted: bool = context.session.output.joinpath('prompt-seen').exists()
    state: dict = context.session.state()
    context.check('no pack prompt for a client with the Adapt mod', not prompted)
    context.check('no server pack on the client', not any(uiclient.is_server_pack(pack) for pack in state['resourcePacks']),
                  state['resourcePacks'])
    close(context)
    context.session.command('adapt gui target=main')
    found: dict = context.session.wait(lambda current: current['screen'] is not None, 'native Adapt screen', 20)['screen']
    time.sleep(1.0)
    context.check('native screen, not a chest', not uiclient.screen_is(found, 'ContainerScreen'), found['class'])
    context.shot('native-skills')


def native_hud_model(state: dict) -> dict:
    """What the Adapt client's HUD holds, from its test probe; empty when it reported no HUD."""
    tree: dict | None = state.get('uiTree')
    hud: dict | None = tree.get('hud') if isinstance(tree, dict) else None
    return hud.get('model', {}) if isinstance(hud, dict) else {}


def native_hud(context: Context) -> None:
    """A client with the Adapt mod gets an XP line on its own HUD for XP earned by digging and a toast for a level
    change, both drawn by the mod (the vanilla toast queue stays empty)."""
    close(context)
    context.session.rcon(f'tp {PLAYER} ~ ~ ~ 0 90')
    context.session.rcon(f'give {PLAYER} minecraft:iron_shovel')
    time.sleep(1.0)
    context.session.hold('attack', True, NATIVE_DIG_TICKS)
    try:
        xp: list = native_hud_model(context.session.wait(lambda state: bool(native_hud_model(state).get('xp')),
                                                         'native HUD xp line', 10))['xp']
    except (AssertionError, RuntimeError, TimeoutError):
        xp = native_hud_model(context.session.state()).get('xp', [])
    context.check('digging puts an xp line on the native HUD', bool(xp), xp)
    admin(context, f'adapt claim-skill skillTarget=agility level=30 player={PLAYER}')
    try:
        toasts: list = native_hud_model(context.session.wait(lambda state: bool(native_hud_model(state).get('toasts')),
                                                             'native HUD toast', 10))['toasts']
    except (AssertionError, RuntimeError, TimeoutError):
        toasts = native_hud_model(context.session.state()).get('toasts', [])
    context.shot('toast', settle=0.3)
    context.check('a level change shows a native toast', bool(toasts), toasts)
    context.session.rcon(f'clear {PLAYER}')
    context.session.rcon(f'tp {PLAYER} ~ ~ ~ 0 0')


# Screens ---------------------------------------------------------------------------------------------------------------

def skills(context: Context) -> None:
    require_pages(context)
    found: dict = open_menu(context, 'adapt gui target=main', 'skills')
    check_look(context, 'skills', found)
    context.shot('page')


def skill_pages(context: Context) -> None:
    require_pages(context)
    for skill in SKILL_PAGES:
        found: dict = open_menu(context, f'adapt gui target=skill:{skill}', skill)
        check_look(context, skill, found)
        context.shot(skill)


def mutated_page(context: Context) -> None:
    require_pages(context)
    # A pair of its own: the mutate scenario may already have merged Chronos and Hunter.
    admin(context, f'adapt claim-skill skillTarget=herbalism level=60 player={PLAYER}')
    admin(context, f'adapt claim-skill skillTarget=agility level=60 player={PLAYER}')
    close(context)
    context.session.command('adapt mutate lead=herbalism partner=agility')
    time.sleep(2.0)
    found: dict | None = screen(context)
    require(found is not None, 'mutating the pair opened no page')
    (context.output / 'states').mkdir(exist_ok=True)
    (context.output / 'states' / f'{context.current}-mutated-gui{context.scale}.json').write_text(json.dumps(summary(found), indent=1))
    plain: str = re.sub('[\ue000-\uf8ff]+', ' ', found['title']).lower()
    context.check('the pair command opens the mutated skill, not the lead', 'skills / herbalism' not in plain, plain[:200])
    check_look(context, 'mutated page', found)
    context.shot('page')


def adaptation_recipes(context: Context) -> None:
    require_pages(context)
    found: dict = open_menu(context, f'adapt gui target=adaptation:{RECIPE_ADAPTATION}', 'recipes')
    check_look(context, 'adaptation with recipes', found)
    if context.pack_loaded:
        tab: dict | None = layout_control(found, 'adaptation.levels', 'foot.tabs', 1)
        require(tab is not None, 'adaptation page has no recipes tab')
        found = click(context, tab)
    context.shot('page')
    context.check('recipe ingredients are real item stacks', any(entry.get('model') in (None, entry['item']) for entry in menu_slots(found)),
                  [(entry['item'], entry.get('model')) for entry in menu_slots(found)])


def adaptation_preferences(context: Context) -> None:
    require_pages(context)
    found: dict = open_menu(context, f'adapt gui target=adaptation:{PREFERENCE_ADAPTATION}', 'preferences')
    check_look(context, 'adaptation with preferences', found)
    context.shot('page')


def mutate_arm_confirm(context: Context) -> None:
    require_pages(context)
    require(context.pack_loaded, 'seal arming is a pack page behaviour')
    admin(context, f'adapt claim-skill skillTarget=chronos level=60 player={PLAYER}')
    admin(context, f'adapt claim-skill skillTarget=hunter level=60 player={PLAYER}')
    found: dict = open_menu(context, 'adapt mutate', 'mutate')
    check_look(context, 'mutate', found, chest=True)
    context.shot('pick')
    found = choose_pair(context, found, 'Chronos', 'Hunter')
    context.shot('open')
    seal: dict | None = layout_control(found, 'mutate', 'seal.mark') if context.pack_loaded else named(found, SEAL)
    require(seal is not None, 'no seal control on the mutate page')
    armed: dict = click(context, seal)
    context.shot('armed', settle=0.2)
    context.check('arming keeps the page open', armed.get('class') == found['class'], armed.get('class'))
    confirmed: dict = click(context, seal)
    context.shot('confirmed')
    context.record('after confirm', 'info', summary(confirmed))


def choose_pair(context: Context, found: dict, lead: str, partner: str) -> dict:
    """Picks the lead's card on the Mutate pick page, then pages the partner list until the partner's card shows and
    picks it; returns the Mutate page for the pair."""
    card: dict | None = named_card(found, lead)
    require(card is not None, f'no {lead} card on the Mutate pick page')
    found = click(context, card)
    for _ in range(PARTNER_PAGES):
        card = named_card(found, partner)
        if card is not None:
            return click(context, card)
        turn: dict | None = layout_control(found, 'mutate.partners', 'foot.next')
        require(turn is not None, f'no {partner} card among the partners')
        found = click(context, turn)
    raise Skip(f'no {partner} card among the partners')


def named_card(found: dict | None, name: str) -> dict | None:
    """The icon slot of the card named name: the first slot with that name, chest rows first, as a card's grid runs on into
    the player rows."""
    return next((entry for entry in slots(found) if entry.get('name') == name), None)


def ledger_config(context: Context) -> None:
    require_pages(context)
    found: dict = open_menu(context, 'adapt configure', 'config')
    context.shot('page')
    if not context.pack_loaded:
        check_look(context, 'config ledger', found)
        return
    check_title(context, 'config ledger', found, True)
    own: list[dict] = [entry for entry in menu_slots(found) if entry.get('model') in (None, entry['item'])]
    chrome: list[str] = sorted({entry['model'] for entry in menu_slots(found) if entry.get('model') not in (None, entry['item'])})
    context.check('config ledger shows the menu\'s own items', bool(own), [entry['item'] for entry in own])
    context.check('config ledger uses pack models only for chrome', all(model.startswith(PACK_MODEL) for model in chrome), chrome)


def page_turn(context: Context) -> None:
    require_pages(context)
    found: dict = open_menu(context, 'adapt gui target=skill:herbalism', 'herbalism')
    turn: dict | None = layout_control(found, 'skill', 'foot.next') if context.pack_loaded else named(found, NEXT_PAGE)
    require(turn is not None, 'herbalism page has no next-page control')
    before: str = json.dumps(summary(found)['slots'])
    context.shot('before')
    turned: dict = click(context, turn)
    context.shot('after')
    context.check('page turn changed the slots', json.dumps(summary(turned).get('slots')) != before)
    context.check('page turn kept the menu open', turned.get('class') == found['class'], turned.get('class'))


def tooltip(context: Context) -> None:
    require_pages(context)
    found: dict = open_menu(context, 'adapt gui target=main', 'skills')
    entries: list[dict] = menu_slots(found)
    require(bool(entries), 'skills menu has no items')
    entry: dict = next((candidate for candidate in entries if candidate.get('name')), entries[0])
    hovered: dict = hover(context, entry)
    context.check('pointer is over the slot', hovered.get('hoveredSlot') == entry['index'],
                  {'expected': entry['index'], 'hovered': hovered.get('hoveredSlot'), 'name': entry.get('name')})
    context.shot('hover', pointer=True)


def power_used(context: Context) -> int:
    """The power in use: the book's power chip ("used/max Power"), else the chest menu title ("Level N (used/max Power Used)")."""
    found: dict = open_menu(context, 'adapt gui target=main', 'skills')
    close(context)
    for entry in slots(found):
        chip: re.Match | None = POWER_CHIP.match(entry.get('name', ''))
        if chip is not None:
            return int(chip.group(1))
    match: re.Match | None = re.search(r'\((\d+)/\d+', found['title'])
    if match is None:
        raise Failed('Skills menu shows no power in use: ' + found['title'])
    return int(match.group(1))


def level_control(found: dict, needles: tuple[str, ...]) -> dict | None:
    """A labelled learn or unlearn control, else the first level slot ("<name> I") the chest menu learns through."""
    return named(found, needles) or next((entry for entry in menu_slots(found) if entry.get('name', '').endswith(' I')), None)


def click_confirmed(context: Context, entry: dict, button: str = 'left', mods: list[str] | None = None) -> bool:
    """Clicks, and clicks again when the server asks for a confirming click. Returns whether it asked."""
    context.session.clear_events()
    after: dict = click(context, entry, button, mods)
    armed: dict | None = next((slot for slot in slots(after) if slot['index'] == entry['index']), None)
    asked: bool = (any('confirm' in notice['text'].lower() for notice in context.session.state()['events']['actionbar'])
                   or 'confirm' in (armed or {}).get('name', '').lower())
    if asked:
        context.shot('confirm-asked', settle=0.1)
        click(context, entry, button, mods)
    return asked


def learn_unlearn(context: Context) -> None:
    require_pages(context)
    admin(context, f'adapt claim-skill skillTarget=herbalism level=40 player={PLAYER}')
    time.sleep(1.0)
    before: int = power_used(context)
    found: dict = open_menu(context, f'adapt gui target=adaptation:{RECIPE_ADAPTATION}', 'adaptation')
    learn: dict | None = level_control(found, LEARN)
    require(learn is not None, 'adaptation page has no learn control')
    context.shot('before')
    permanent: bool = click_confirmed(context, learn)
    context.shot('learned')
    refreshed: dict = screen(context) or {}
    context.check('the page stays open and refreshes after learning',
                  refreshed.get('class') == found['class'] and json.dumps(summary(refreshed).get('slots')) != json.dumps(summary(found)['slots']),
                  {'class': refreshed.get('class'), 'control': (named(refreshed, LEARN + UNLEARN) or {}).get('name')})
    learned: int = power_used(context)
    context.check('learning by click raised the power in use', learned > before, {'before': before, 'after': learned})
    require(not permanent, 'learning is permanent in this server config, so there is nothing to unlearn')
    found = open_menu(context, f'adapt gui target=adaptation:{RECIPE_ADAPTATION}', 'adaptation')
    unlearn: dict | None = named(found, UNLEARN)
    if unlearn is not None:
        click_confirmed(context, unlearn)
    else:
        unlearn = level_control(found, LEARN)
        require(unlearn is not None, 'adaptation page has no unlearn control after learning')
        click_confirmed(context, unlearn, button='right')
    context.shot('unlearned')
    require(PERMANENT not in (screen(context) or {}).get('title', ''), 'learning is permanent in this server config, so there is nothing to unlearn')
    after: int = power_used(context)
    context.check('unlearning by click restored the power in use', after == before, {'before': before, 'after': after})


def toast(context: Context) -> None:
    require_pages(context)
    close(context)
    context.session.clear_events()
    admin(context, f'adapt claim-skill skillTarget=chronos level=70 player={PLAYER}')
    wanted: Callable[[dict], bool] = ((lambda state: bool(state['events']['toasts'])) if context.pack_loaded
                                      else (lambda state: bool(state['events']['toasts'] or state['events']['actionbar'])))
    try:
        events: dict = context.session.wait(wanted, 'toast' if context.pack_loaded else 'toast or action bar notice', 12)['events']
    except (AssertionError, RuntimeError, TimeoutError):
        events = context.session.state()['events']
        if not context.pack_loaded and not events['actionbar']:
            raise Failed('no toast and no action bar notice after a level change')
    context.shot('notice', settle=0.3)
    shown: dict = {'toasts': events['toasts'][-3:], 'actionbar': events['actionbar'][-3:]}
    if context.pack_loaded:
        context.check('level change shows a toast', bool(events['toasts']), shown)
    else:
        context.check('level change shows a toast or an action bar notice', True, shown)


def hud_strip(context: Context) -> None:
    require(context.pack_loaded, 'the HUD strip is a pack feature')
    require(context.features.hud, 'server Adapt jar has no pack HUD (art.arcane.adapt.pack.hud)')
    close(context)
    context.session.clear_events()
    admin(context, f'adapt claim-skill skillTarget=herbalism level=75 player={PLAYER}')
    events: dict = context.session.wait(lambda state: bool(state['events']['actionbar']), 'action bar', 15)['events']
    context.shot('hud', settle=0.2)
    context.record('action bar', 'info', events['actionbar'][-3:])


# Animation -------------------------------------------------------------------------------------------------------------



def bmp_luma(path: Path, box: tuple[int, int, int, int]) -> list[int]:
    """Full resolution luma of box (x, y, width, height) in a screenshot."""
    cropped: Path = path.with_suffix(f'.{box[0]}-{box[1]}.bmp')
    subprocess.run(['sips', '-s', 'format', 'bmp', '-c', str(box[3]), str(box[2]), '--cropOffset', str(box[1]), str(box[0]),
                    str(path), '--out', str(cropped)], check=True, capture_output=True)
    data: bytes = cropped.read_bytes()
    cropped.unlink()
    offset: int = int.from_bytes(data[10:14], 'little')
    width: int = int.from_bytes(data[18:22], 'little', signed=True)
    height: int = abs(int.from_bytes(data[22:26], 'little', signed=True))
    step: int = int.from_bytes(data[28:30], 'little') // 8
    stride: int = ((width * step) + 3) & ~3
    return [int(0.2126 * data[offset + row * stride + column * step + 2] + 0.7152 * data[offset + row * stride + column * step + 1]
                + 0.0722 * data[offset + row * stride + column * step])
            for row in range(height) for column in range(width)]


def pixels_changed(first: Path, second: Path, box: tuple[int, int, int, int]) -> int:
    """Pixels of box whose luma moved by more than 10 between two screenshots."""
    return sum(1 for left, right in zip(bmp_luma(first, box), bmp_luma(second, box)) if abs(left - right) > 10)


def dressing_regions(context: Context, found: dict) -> dict[str, tuple[int, int, int, int]]:
    """Screen boxes of the page dressing around the 176x222 container: the walls, the top band and the foot. The book
    page reaches about 30 GUI units past the container's sides, 32 above it and 24 below it."""
    window: dict = context.session.state()['window']
    factor: float = window['width'] / max(1, window['scaledWidth'])

    def box(x: int, y: int, width: int, height: int) -> tuple[int, int, int, int]:
        left: int = max(0, int((found['left'] + x) * factor))
        top: int = max(0, int((found['top'] + y) * factor))
        return (left, top, min(window['width'] - left, int(width * factor)), min(window['height'] - top, int(height * factor)))

    return {'left wall': box(-30, -20, 14, 266), 'right wall': box(192, -20, 14, 266), 'top': box(-30, -32, 236, 22),
            'foot': box(-30, 222, 236, 24)}






def motion_box(context: Context, found: dict) -> tuple[int, int, int, int]:
    """The menu with a margin around it, where oversized tiles and title art reach past the container edge."""
    window: dict = context.session.state()['window']
    factor: float = window['width'] / max(1, window['scaledWidth'])
    margin: int = int(48 * factor)
    left: int = max(0, int(found['left'] * factor) - margin)
    top: int = max(0, int(found['top'] * factor) - margin)
    width: int = min(window['width'] - left, int(176 * factor) + 2 * margin)
    height: int = min(window['height'] - top, int(222 * factor) + 2 * margin)
    return (left, top, width - width % 2, height - height % 2)


def contact_sheet(frames: list[Path], box: tuple[int, int, int, int], sheet: Path, gif: Path) -> list[str]:
    """Writes a contact sheet PNG and a looping GIF of the frames, cropped to box, with ffmpeg; returns what it could not
    make."""
    ffmpeg: str | None = shutil.which('ffmpeg')
    if ffmpeg is None:
        return ['ffmpeg not found; frames kept as PNG only']
    pattern: str = str(frames[0].parent / (frames[0].stem.rsplit('-', 1)[0] + '-%02d.png'))
    columns: int = 4
    rows: int = (len(frames) + columns - 1) // columns
    problems: list[str] = []
    crop: str = f'crop={box[2]}:{box[3]}:{box[0]}:{box[1]}'
    for arguments in (['-vf', f'{crop},tile={columns}x{rows}:padding=6', '-frames:v', '1', str(sheet)],
                      ['-vf', f'{crop},split[a][b];[a]palettegen[p];[b][p]paletteuse', '-loop', '0', str(gif)]):
        framerate: list[str] = ['-framerate', '6'] if arguments[-1] == str(gif) else []
        result = subprocess.run([ffmpeg, '-y', '-loglevel', 'error', *framerate, '-start_number', '0', '-i', pattern, *arguments],
                                capture_output=True, text=True)
        if result.returncode != 0:
            problems.append(result.stderr.strip()[-400:])
    return problems


def capture_motion(context: Context, label: str, found: dict, count: int = 12, ticks: int = 3) -> None:
    context.session.op('mouse-move', x=1, y=1)
    regions: dict[str, tuple[int, int, int, int]] = dressing_regions(context, found)
    first: Path = context.shot(f'{label}-t0', settle=0.3)
    changes: dict[str, dict[str, int]] = {}
    elapsed: int = 0
    for offset in MOTION_TICKS:
        wait_ticks(context, offset - elapsed)
        elapsed = offset
        later: Path = context.shot(f'{label}-t{offset}', settle=0.0)
        changes[f'+{offset}'] = {name: pixels_changed(first, later, box) for name, box in regions.items()}
    moved: int = max(sum(counts.values()) for counts in changes.values())
    context.check(f'{label}: the page dressing moves within {MOTION_TICKS[-1]} ticks', moved >= MOTION_PIXELS,
                  {'changedPixels': changes, 'regions': regions})
    directory: Path = context.output / 'motion'
    directory.mkdir(exist_ok=True)
    frames: list[Path] = []
    for index in range(count):
        path: Path = directory / f'{context.current}-{label}-gui{context.scale}-{index:02d}.png'
        context.session.op('screenshot', timeout=60, path=str(path))
        frames.append(path)
        wait_ticks(context, ticks)
    sheet: Path = directory / f'{context.current}-{label}-gui{context.scale}-sheet.png'
    gif: Path = directory / f'{context.current}-{label}-gui{context.scale}.gif'
    problems: list[str] = contact_sheet(frames, motion_box(context, found), sheet, gif)
    context.record(f'{label} frame sequence', 'shot' if not problems else 'info',
                   {'frames': len(frames), 'everyTicks': ticks, 'problems': problems}, sheet if sheet.is_file() else None)


def animation(context: Context) -> None:
    require_pages(context)
    require(context.pack_loaded, 'page animation is a pack feature')
    close(context)
    context.session.rcon(f'tp {PLAYER} ~ ~ ~ 0 90')
    found: dict = open_menu(context, 'adapt gui target=skill:herbalism', 'herbalism')
    check_look(context, 'herbalism', found)
    capture_motion(context, 'herbalism', found)


# Safety ----------------------------------------------------------------------------------------------------------------

def safety_inventory(context: Context) -> None:
    require_pages(context)
    seed_inventory(context)
    before: str = inventory_hash(context)
    items: list[tuple[str, int]] = client_items(context)
    found: dict = open_menu(context, 'adapt gui target=main', 'skills')
    controls: set[int] = layout_slots('skills')
    entries: list[dict] = [entry for entry in menu_slots(found) if entry['index'] not in controls]
    if entries:
        click(context, entries[0])
    context.shot('open')
    close(context)
    time.sleep(1.0)
    context.check('server inventory unchanged after close', inventory_hash(context) == before)
    context.check('client inventory unchanged after close', client_items(context) == items, {'before': items, 'after': client_items(context)})


def safety_death(context: Context) -> None:
    require_pages(context)
    seed_inventory(context)
    for rule in ('gamerule keep_inventory true', 'gamerule keepInventory true'):
        response: str = context.session.rcon(rule)
        if 'nknown' not in response and 'ncorrect' not in response:
            break
    before: str = inventory_hash(context)
    open_menu(context, 'adapt gui target=main', 'skills')
    context.session.rcon(f'kill {PLAYER}')
    dead: dict = context.session.wait_screen(cls='DeathScreen', timeout=20)['screen']
    context.shot('death')
    respawn: dict = next(widget for widget in dead['widgets'] if widget.get('message', '').lower().startswith('respawn'))
    context.session.wait(lambda state: any(widget.get('active') for widget in (state['screen'] or {}).get('widgets', [])
                                           if widget.get('message', '').lower().startswith('respawn')), 'respawn button', 20)
    context.session.press(respawn)
    context.session.wait(lambda state: state['screen'] is None, 'respawned', 20)
    time.sleep(1.0)
    context.check('server inventory unchanged after death with the menu open', inventory_hash(context) == before)


def safety_disconnect(context: Context) -> None:
    require_pages(context)
    seed_inventory(context)
    before: str = inventory_hash(context)
    open_menu(context, 'adapt gui target=main', 'skills')
    context.session.rcon(f'kick {PLAYER}')
    context.session.wait(lambda state: not state['player']['connected'], 'disconnect', 20)
    context.shot('kicked')
    rejoin(context)
    context.check('server inventory unchanged after a disconnect with the menu open', inventory_hash(context) == before)
    context.shot('rejoined')


def safety_add_item(context: Context) -> None:
    require_pages(context)
    seed_inventory(context)
    items: list[tuple[str, int]] = client_items(context)
    open_menu(context, 'adapt gui target=main', 'skills')
    context.session.rcon(f'give {PLAYER} minecraft:diamond 3')
    time.sleep(1.0)
    context.shot('open-after-give')
    close(context)
    time.sleep(1.5)
    expected: list[tuple[str, int]] = sorted([*items, ('minecraft:diamond', 3)])
    context.check('given item arrives in the real inventory, nothing else changes', client_items(context) == expected,
                  {'expected': expected, 'after': client_items(context)})
    held: str = server_inventory(context)
    context.check('server holds the given item', 'minecraft:diamond' in held, held[-400:])


# Harness self test with the spike plugin --------------------------------------------------------------------------------

def rejoin(context: Context) -> None:
    """Connects the client to its server again and answers any pack prompt the way this run already did."""
    try:
        context.session.connect(f'127.0.0.1:{context.session.server_port}')
    except RuntimeError as failure:
        raise Skip('this client cannot rejoin through the bridge: ' + str(failure)) from failure
    deadline: float = time.monotonic() + 120
    while time.monotonic() < deadline:
        state: dict = context.session.state()
        if context.session.pack_prompt() is not None:
            context.session.answer_pack_prompt(accept=context.pack_loaded)
        elif state['player']['connected'] and state['player'].get('name') == PLAYER and state['overlay'] is None:
            break
        time.sleep(0.5)
    context.session.wait_connected(30)
    time.sleep(2.0)


def spike_pack(context: Context) -> None:
    pack: Path = context.output / 'spike-pack-dir'
    require(pack.is_file(), 'no spike pack configured')
    if not context.pack_loaded:
        context.session.command('spike pack ' + pack.read_text().strip())
        context.session.answer_pack_prompt(accept=True, timeout=30)
        context.pack_loaded = True
    state: dict = context.session.state()
    context.check('server pack selected on the client', any(uiclient.is_server_pack(pack) for pack in state['resourcePacks']),
                  state['resourcePacks'])
    context.session.command('spike scene layout_single')
    found: dict = context.session.wait_screen(cls='ContainerScreen', timeout=15)['screen']
    time.sleep(1.0)
    found = screen(context) or found
    context.check('title carries the spike page font', 'spike:single' in fonts(found), {'fonts': fonts(found)})
    context.shot('page')
    context.session.command('spike scene anim')
    found = context.session.wait(lambda state: bool(models(state['screen'])), 'animated scene', 15)['screen']
    context.check('slots carry spike item models', any(model.startswith('spike:') for model in models(found)), models(found))
    capture_motion(context, 'anim', found)
    entry: dict = menu_slots(found)[0]
    hovered: dict = hover(context, entry)
    context.check('pointer is over the slot', hovered.get('hoveredSlot') == entry['index'], hovered.get('hoveredSlot'))
    context.shot('hover', pointer=True)
    context.session.command('spike scene anim')
    time.sleep(1.5)
    reopened: dict = screen(context) or {}
    context.check('pointer stays on the slot when the menu is reopened', reopened.get('hoveredSlot') == entry['index'],
                  {'hovered': reopened.get('hoveredSlot'), 'cursor': [context.session.state()['window'].get('cursorX'),
                                                                      context.session.state()['window'].get('cursorY')]})
    context.session.command('spike stop')
    close(context)


@dataclass(frozen=True)
class Scenario:
    name: str
    run: Callable[[Context], None]
    per_scale: bool = True


SCENARIOS: dict[str, Scenario] = {scenario.name: scenario for scenario in (
    Scenario('server-health', server_health, False),
    Scenario('pack-viewer-mode', pack_viewer_mode, False),
    Scenario('join-accepted', join_pack_accepted, False),
    Scenario('join-declined', join_pack_declined, False),
    Scenario('mod-server-no-pack', mod_server_no_pack, False),
    Scenario('modded-not-offered', modded_not_offered, False),
    Scenario('native-hud', native_hud, False),
    Scenario('skills', skills),
    Scenario('skill-pages', skill_pages),
    Scenario('adaptation-recipes', adaptation_recipes),
    Scenario('adaptation-preferences', adaptation_preferences),
    Scenario('ledger-config', ledger_config),
    Scenario('page-turn', page_turn),
    Scenario('tooltip', tooltip),
    Scenario('animation', animation),
    Scenario('learn-unlearn', learn_unlearn, False),
    Scenario('toast', toast, False),
    Scenario('hud', hud_strip, False),
    Scenario('mutate', mutate_arm_confirm, False),
    Scenario('mutated-page', mutated_page, False),
    Scenario('safety-inventory', safety_inventory, False),
    Scenario('safety-add-item', safety_add_item, False),
    Scenario('safety-death', safety_death, False),
    Scenario('safety-disconnect', safety_disconnect, False),
    Scenario('spike', spike_pack),
)}

SCREENS: tuple[str, ...] = ('skills', 'skill-pages', 'adaptation-recipes', 'adaptation-preferences', 'ledger-config',
                            'page-turn', 'tooltip', 'learn-unlearn', 'toast')
SAFETY: tuple[str, ...] = ('safety-inventory', 'safety-add-item', 'safety-death', 'safety-disconnect')
PROFILES: dict[str, tuple[str, ...]] = {
    'pack': ('server-health', 'join-accepted', 'pack-viewer-mode', *SCREENS, 'animation', 'hud', 'mutate', 'mutated-page', *SAFETY),
    'declined': ('server-health', 'join-declined', *SCREENS, *SAFETY),
    'vanilla': ('server-health', 'mod-server-no-pack', *SCREENS, *SAFETY),
    'modded': ('modded-not-offered', 'native-hud'),
    'selftest': ('spike',),
}
BUKKIT_PROFILES: tuple[str, ...] = ('pack', 'declined', 'selftest')


def run(context: Context, names: tuple[str, ...], scales: list[str], apply_scale: Callable[[str], None]) -> dict[str, dict]:
    """Runs the named scenarios, the per-scale ones once for every scale. One failure never stops the others."""
    results: dict[str, dict] = {}
    for name in names:
        scenario: Scenario = SCENARIOS[name]
        for scale in scales if scenario.per_scale else scales[:1]:
            key: str = name if not scenario.per_scale else f'{name}@gui{scale}'
            apply_scale(scale)
            context.scale = scale
            context.current = name
            started: float = time.monotonic()
            try:
                scenario.run(context)
                results[key] = {'status': 'pass'}
            except Skip as reason:
                context.record('skipped', 'skip', str(reason))
                results[key] = {'status': 'skip', 'reason': str(reason)}
            except Exception as failure:  # noqa: BLE001 - one broken scenario must not hide the rest
                detail: str = f'{type(failure).__name__}: {failure}'[:1200]
                context.record('failed', 'fail', detail)
                with_shot: Path = context.output / 'screenshots' / f'{name}-failure-gui{scale}.png'
                try:
                    context.session.op('screenshot', timeout=60, path=str(with_shot))
                except Exception:  # noqa: BLE001
                    pass
                results[key] = {'status': 'fail', 'error': detail}
            results[key]['seconds'] = round(time.monotonic() - started, 1)
            uiclient.log(f'{context.profile} {key}: {results[key]["status"]}')
            with_screen: dict | None = None
            try:
                with_screen = screen(context)
            except Exception:  # noqa: BLE001
                pass
            if with_screen is not None:
                try:
                    close(context)
                except Exception:  # noqa: BLE001
                    pass
    return results


def skipped_all(names: tuple[str, ...], reason: str) -> dict[str, dict]:
    return {name: {'status': 'skip', 'reason': reason} for name in names}


def pattern_list(text: str) -> list[str]:
    return [part for part in re.split(r'[,\s]+', text) if part]
