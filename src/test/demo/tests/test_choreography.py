import json
import re
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / 'src' / 'test' / 'demo'))
import choreography


def matrix_ids(skill: str) -> set[str]:
    data = json.loads((ROOT / 'src' / 'test' / 'gameplay' / 'adaptation-matrix.json').read_text())
    entries = data['adaptations'] if isinstance(data, dict) and 'adaptations' in data else data
    return {e['name'] for e in entries if e['skill'] == skill}


def beat_problems(tmp: str, beat: dict) -> list[str]:
    root = Path(tmp)
    write_skill(root, {'x-one': {'set': 'arena', 'expect': {'sound': 'a'}, 'beats': [beat]}})
    return choreography.validate(choreography.load_skill(root, 'x').entries, {'x-one'})


def write_matrix(root: Path, skills: dict) -> None:
    folder = root / 'src' / 'test' / 'gameplay'
    folder.mkdir(parents=True)
    (folder / 'adaptation-matrix.json').write_text(json.dumps({'adaptations': [{'name': name, 'skill': skill} for name, skill in skills.items()]}))


def write_skill(root: Path, body: dict) -> None:
    folder = root / 'src' / 'test' / 'demo' / 'sheets'
    folder.mkdir(parents=True)
    (folder / 'x.json').write_text(json.dumps(body))


class ChoreographyTest(unittest.TestCase):
    def test_agility_covers_every_matrix_id_exactly_once(self) -> None:
        entries = choreography.load_skill(ROOT, 'agility').entries
        problems = choreography.validate(entries, matrix_ids('agility'))
        self.assertEqual(problems, [])
        self.assertEqual(set(entries), matrix_ids('agility'))

    def test_stealth_covers_every_matrix_id_exactly_once(self) -> None:
        entries: dict[str, choreography.Entry] = choreography.load_skill(ROOT, 'stealth').entries
        problems: list[str] = choreography.validate(entries, matrix_ids('stealth'))
        self.assertEqual(problems, [])
        self.assertEqual(set(entries), matrix_ids('stealth'))

    def test_seaborne_covers_every_matrix_id_exactly_once(self) -> None:
        entries: dict[str, choreography.Entry] = choreography.load_skill(ROOT, 'seaborne').entries
        problems: list[str] = choreography.validate(entries, matrix_ids('seaborne'))
        self.assertEqual(problems, [])
        self.assertEqual(set(entries), matrix_ids('seaborne'))

    def test_kinetics_covers_every_matrix_id_exactly_once(self) -> None:
        entries: dict[str, choreography.Entry] = choreography.load_skill(ROOT, 'kinetics').entries
        problems: list[str] = choreography.validate(entries, matrix_ids('kinetics'))
        self.assertEqual(problems, [])
        self.assertEqual(set(entries), matrix_ids('kinetics'))

    def test_herbalism_covers_every_matrix_id_exactly_once(self) -> None:
        entries: dict[str, choreography.Entry] = choreography.load_skill(ROOT, 'herbalism').entries
        problems: list[str] = choreography.validate(entries, matrix_ids('herbalism'))
        self.assertEqual(problems, [])
        self.assertEqual(set(entries), matrix_ids('herbalism'))

    def test_pickaxe_covers_every_matrix_id_exactly_once(self) -> None:
        entries: dict[str, choreography.Entry] = choreography.load_skill(ROOT, 'pickaxe').entries
        problems: list[str] = choreography.validate(entries, matrix_ids('pickaxe'))
        self.assertEqual(problems, [])
        self.assertEqual(set(entries), matrix_ids('pickaxe'))

    def test_ranged_covers_every_matrix_id_exactly_once(self) -> None:
        entries: dict[str, choreography.Entry] = choreography.load_skill(ROOT, 'ranged').entries
        problems: list[str] = choreography.validate(entries, matrix_ids('ranged'))
        self.assertEqual(problems, [])
        self.assertEqual(set(entries), matrix_ids('ranged'))

    def test_axes_covers_every_matrix_id_exactly_once(self) -> None:
        entries: dict[str, choreography.Entry] = choreography.load_skill(ROOT, 'axes').entries
        problems: list[str] = choreography.validate(entries, matrix_ids('axes'))
        self.assertEqual(problems, [])
        self.assertEqual(set(entries), matrix_ids('axes'))

    def test_excavation_covers_every_matrix_id_exactly_once(self) -> None:
        entries: dict[str, choreography.Entry] = choreography.load_skill(ROOT, 'excavation').entries
        problems: list[str] = choreography.validate(entries, matrix_ids('excavation'))
        self.assertEqual(problems, [])
        self.assertEqual(set(entries), matrix_ids('excavation'))

    def test_hunter_covers_every_matrix_id_exactly_once(self) -> None:
        entries: dict[str, choreography.Entry] = choreography.load_skill(ROOT, 'hunter').entries
        problems: list[str] = choreography.validate(entries, matrix_ids('hunter'))
        self.assertEqual(problems, [])
        self.assertEqual(set(entries), matrix_ids('hunter'))

    def test_unarmed_covers_every_matrix_id_exactly_once(self) -> None:
        entries: dict[str, choreography.Entry] = choreography.load_skill(ROOT, 'unarmed').entries
        problems: list[str] = choreography.validate(entries, matrix_ids('unarmed'))
        self.assertEqual(problems, [])
        self.assertEqual(set(entries), matrix_ids('unarmed'))

    def test_swords_covers_every_matrix_id_exactly_once(self) -> None:
        entries: dict[str, choreography.Entry] = choreography.load_skill(ROOT, 'swords').entries
        problems: list[str] = choreography.validate(entries, matrix_ids('swords'))
        self.assertEqual(problems, [])
        self.assertEqual(set(entries), matrix_ids('swords'))

    def test_tragoul_covers_every_matrix_id_exactly_once(self) -> None:
        entries: dict[str, choreography.Entry] = choreography.load_skill(ROOT, 'tragoul').entries
        problems: list[str] = choreography.validate(entries, matrix_ids('tragoul'))
        self.assertEqual(problems, [])
        self.assertEqual(set(entries), matrix_ids('tragoul'))

    def test_chronos_covers_every_matrix_id_exactly_once(self) -> None:
        entries: dict[str, choreography.Entry] = choreography.load_skill(ROOT, 'chronos').entries
        problems: list[str] = choreography.validate(entries, matrix_ids('chronos'))
        self.assertEqual(problems, [])
        self.assertEqual(set(entries), matrix_ids('chronos'))

    def test_discovery_covers_every_matrix_id_exactly_once(self) -> None:
        entries: dict[str, choreography.Entry] = choreography.load_skill(ROOT, 'discovery').entries
        problems: list[str] = choreography.validate(entries, matrix_ids('discovery'))
        self.assertEqual(problems, [])
        self.assertEqual(set(entries), matrix_ids('discovery'))

    def test_rift_covers_every_matrix_id_exactly_once(self) -> None:
        entries: dict[str, choreography.Entry] = choreography.load_skill(ROOT, 'rift').entries
        problems: list[str] = choreography.validate(entries, matrix_ids('rift'))
        self.assertEqual(problems, [])
        self.assertEqual(set(entries), matrix_ids('rift'))

    def test_blocking_covers_every_matrix_id_exactly_once(self) -> None:
        entries: dict[str, choreography.Entry] = choreography.load_skill(ROOT, 'blocking').entries
        problems: list[str] = choreography.validate(entries, matrix_ids('blocking'))
        self.assertEqual(problems, [])
        self.assertEqual(set(entries), matrix_ids('blocking'))

    def test_taming_covers_every_matrix_id_exactly_once(self) -> None:
        entries: dict[str, choreography.Entry] = choreography.load_skill(ROOT, 'taming').entries
        problems: list[str] = choreography.validate(entries, matrix_ids('taming'))
        self.assertEqual(problems, [])
        self.assertEqual(set(entries), matrix_ids('taming'))

    def test_brewing_covers_every_matrix_id_exactly_once(self) -> None:
        entries: dict[str, choreography.Entry] = choreography.load_skill(ROOT, 'brewing').entries
        problems: list[str] = choreography.validate(entries, matrix_ids('brewing'))
        self.assertEqual(problems, [])
        self.assertEqual(set(entries), matrix_ids('brewing'))

    def test_crafting_covers_every_matrix_id_exactly_once(self) -> None:
        entries: dict[str, choreography.Entry] = choreography.load_skill(ROOT, 'crafting').entries
        problems: list[str] = choreography.validate(entries, matrix_ids('crafting'))
        self.assertEqual(problems, [])
        self.assertEqual(set(entries), matrix_ids('crafting'))

    def test_enchanting_covers_every_matrix_id_exactly_once(self) -> None:
        entries: dict[str, choreography.Entry] = choreography.load_skill(ROOT, 'enchanting').entries
        problems: list[str] = choreography.validate(entries, matrix_ids('enchanting'))
        self.assertEqual(problems, [])
        self.assertEqual(set(entries), matrix_ids('enchanting'))

    def test_architect_covers_every_matrix_id_exactly_once(self) -> None:
        entries: dict[str, choreography.Entry] = choreography.load_skill(ROOT, 'architect').entries
        problems: list[str] = choreography.validate(entries, matrix_ids('architect'))
        self.assertEqual(problems, [])
        self.assertEqual(set(entries), matrix_ids('architect'))

    def test_golemancy_covers_every_matrix_id_exactly_once(self) -> None:
        entries: dict[str, choreography.Entry] = choreography.load_skill(ROOT, 'golemancy').entries
        problems: list[str] = choreography.validate(entries, matrix_ids('golemancy'), choreography.catalog_ids(ROOT))
        self.assertEqual(problems, [])
        self.assertEqual({identifier for identifier, entry in entries.items() if entry.showcase is None}, matrix_ids('golemancy'))

    def test_golemancy_showcase_covers_the_requested_topics_under_exact_docs_headings(self) -> None:
        entries: dict[str, choreography.Entry] = choreography.load_skill(ROOT, 'golemancy').entries
        headings: dict[str, str] = {identifier: entry.showcase.heading for identifier, entry in entries.items() if entry.showcase is not None}
        self.assertEqual(headings, {
            'golemancy-crafting': 'Items and crafting', 'golemancy-bodies': 'Bound golems', 'golemancy-modes': 'Bound golems',
            'golemancy-asleep': 'Awake and asleep', 'golemancy-golem-gui': 'Golem GUI', 'golemancy-roster': 'Golem roster',
            'golemancy-plinth-ritual': 'Animus Plinth', 'golemancy-node-config': 'Node Weaving (`golem-node-weaving`)',
            'golemancy-preferences': 'Player preferences'})
        docs: Path = ROOT.parent / 'docs' / 'adapt' / '34-skill-golemancy.md'
        if not docs.is_file():
            self.skipTest('The docs checkout with the Golemancy page is not beside this worktree')
        page: str = docs.read_text()
        for heading in headings.values():
            self.assertRegex(page, r'(?m)^#{2,3} ' + re.escape(heading) + r'$')

    def test_golemancy_preferences_clip_toggles_absorb_intercept_and_node_reveal(self) -> None:
        entry: choreography.Entry = choreography.load_skill(ROOT, 'golemancy').entries['golemancy-preferences']
        commands: str = ' '.join(str(beat.get('text', '')) for beat in entry.beats)
        for adaptation in ('golem-gatherer-core', 'golem-guardian-core', 'golem-node-weaving'):
            self.assertIn('adaptation:' + adaptation, commands)
        self.assertTrue(entry.pov_capture)

    def test_nether_covers_every_matrix_id_exactly_once(self) -> None:
        sheet: choreography.Sheet = choreography.load_skill(ROOT, 'nether')
        problems: list[str] = choreography.validate(sheet.entries, matrix_ids('nether'))
        self.assertEqual(problems, [])
        self.assertEqual(set(sheet.entries), matrix_ids('nether'))
        self.assertEqual(sheet.world, choreography.NETHER)

    def test_prerequisites_are_loaded_with_their_skills(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_matrix(root, {'x-one': 'x', 'y-base': 'y'})
            write_skill(root, {'x-one': {'set': 'arena', 'expect': {'sound': 'a'}, 'beats': [{'verb': 'wait', 'ticks': 1}],
                                         'prerequisites': [{'name': 'y-base', 'level': 2}]}})
            entries = choreography.load_skill(root, 'x').entries
            self.assertEqual(entries['x-one'].prerequisites, (choreography.Prerequisite(skill='y', name='y-base', level=2),))
            self.assertEqual(choreography.validate(entries, {'x-one'}), [])

    def test_prerequisites_default_to_empty(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_skill(root, {'x-one': {'set': 'arena', 'expect': {'sound': 'a'}, 'beats': [{'verb': 'wait', 'ticks': 1}]}})
            self.assertEqual(choreography.load_skill(root, 'x').entries['x-one'].prerequisites, ())

    def test_unknown_self_or_unleveled_prerequisites_are_reported(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_matrix(root, {'x-one': 'x'})
            write_skill(root, {'x-one': {'set': 'arena', 'expect': {'sound': 'a'}, 'beats': [{'verb': 'wait', 'ticks': 1}],
                                         'prerequisites': [{'name': 'z-gone', 'level': 1}, {'name': 'x-one', 'level': 1}, {'name': 'x-one', 'level': 0}]}})
            problems = choreography.validate(choreography.load_skill(root, 'x').entries, {'x-one'})
            self.assertIn('x-one: prerequisite z-gone is not a catalog adaptation', problems)
            self.assertIn('x-one: prerequisite x-one is the entry itself', problems)
            self.assertIn('x-one: prerequisite x-one level must be at least 1', problems)

    def test_malformed_prerequisites_are_rejected(self) -> None:
        for prerequisites in ({'name': 'y-base', 'level': 1}, [{'name': 'y-base'}], [{'name': 'y-base', 'level': '1'}], [{'name': 'y-base', 'level': True}], ['y-base']):
            with self.subTest(prerequisites=prerequisites), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                write_matrix(root, {'x-one': 'x', 'y-base': 'y'})
                write_skill(root, {'x-one': {'set': 'arena', 'expect': {'sound': 'a'}, 'beats': [{'verb': 'wait', 'ticks': 1}],
                                             'prerequisites': prerequisites}})
                with self.assertRaises(ValueError):
                    choreography.load_skill(root, 'x')

    def test_every_sheet_claims_the_adaptations_its_source_requires(self) -> None:
        matrix = json.loads((ROOT / 'src' / 'test' / 'gameplay' / 'adaptation-matrix.json').read_text())['adaptations']
        for skill in sorted({entry['skill'] for entry in matrix}):
            entries = choreography.load_skill(ROOT, skill).entries
            for adaptation in (entry for entry in matrix if entry['skill'] == skill):
                source = (ROOT / adaptation['source']).read_text()
                declared = re.search(r'\brequires\s*=\s*new ArrayList<>\(List\.of\(([^)]*)\)\)', source)
                entry = entries.get(adaptation['name'])
                if declared is None or entry is None or entry.skip:
                    continue
                for name, level in re.findall(r'"([^":]+):(\d+)"', declared.group(1)):
                    with self.subTest(adaptation=adaptation['name'], requires=name):
                        self.assertTrue(any(prerequisite.name == name and prerequisite.level >= int(level) for prerequisite in entry.prerequisites))

    def test_golemancy_entries_claim_animus_binding_first(self) -> None:
        entries = choreography.load_skill(ROOT, 'golemancy').entries
        for identifier in ('golem-frost-frame', 'golem-node-weaving'):
            self.assertEqual([prerequisite.name for prerequisite in entries[identifier].prerequisites], ['golem-animus-binding'])
            self.assertFalse(any('claim-adaptation' in command for command in entries[identifier].pre))

    def test_golemancy_node_clips_capture_the_pov_live(self) -> None:
        entries: dict[str, choreography.Entry] = choreography.load_skill(ROOT, 'golemancy').entries
        node_clips: set[str] = {identifier for identifier, entry in entries.items() if any(re.search(r'demo give \{actor\} \d node/', text) for text in entry.pre)}
        self.assertEqual(node_clips, {'golem-node-weaving', 'golem-harvester-core', 'golem-breaker-core', 'golem-angler-core',
                                      'golem-lumber-core', 'golem-rancher-core', 'golem-sorter-core', 'golemancy-node-config',
                                      'golem-gatherer-core'})
        for identifier in sorted(node_clips):
            with self.subTest(entry=identifier):
                self.assertTrue(entries[identifier].pov_capture)

    def test_node_weaving_selects_a_channel_before_demonstrating_three_axis_shifting(self) -> None:
        entry: choreography.Entry = choreography.load_skill(ROOT, 'golemancy').entries['golem-node-weaving']
        clicks: list[int] = [beat['slot'] for beat in entry.beats if beat.get('verb') == 'window' and beat.get('action') == 'click']
        self.assertEqual(clicks[:2], [2, 22])
        self.assertIn(11, clicks)
        self.assertIn(12, clicks)
        turns: list[dict] = [beat for beat in entry.beats if beat.get('verb') == 'look']
        self.assertTrue(any(beat['yaw'] == -90 and beat['pitch'] == 0 for beat in turns))
        self.assertTrue(any(beat['pitch'] < -60 for beat in turns))
        self.assertTrue(any(beat['yaw'] == 180 and beat['pitch'] == 0 for beat in turns))
        self.assertGreaterEqual(sum(beat.get('verb') == 'tap' and beat.get('key') == 'sneak' for beat in entry.beats), 4)
        self.assertEqual(sum(beat.get('verb') == 'slot' and beat.get('index') == 2 for beat in entry.beats), 3)

    def test_harvester_demo_links_a_seed_chest_and_includes_bare_farmland(self) -> None:
        entry: choreography.Entry = choreography.load_skill(ROOT, 'golemancy').entries['golem-harvester-core']
        self.assertTrue(any('wheat_seeds' in text and 'data merge block' in text for text in entry.pre))
        self.assertTrue(any('fill ~-2 ~-1 ~-4 ~0 ~-1 ~-4 minecraft:farmland' in text for text in entry.pre))
        self.assertTrue(any(beat.get('verb') == 'window' and beat.get('action') == 'click' and beat.get('slot') == 8 for beat in entry.beats))

    def test_golemancy_clips_that_show_nodes_or_labels_arm_the_reveal_with_a_hotbar_switch(self) -> None:
        entries: dict[str, choreography.Entry] = choreography.load_skill(ROOT, 'golemancy').entries
        crook: str = 'demo give {actor} 1 crook'
        for identifier in ('golem-harvester-core', 'golem-breaker-core', 'golem-angler-core', 'golem-lumber-core', 'golem-rancher-core',
                           'golem-sorter-core', 'golem-node-weaving', 'golem-animus-binding', 'golem-copper-frame', 'golem-overseer'):
            with self.subTest(entry=identifier):
                entry: choreography.Entry = entries[identifier]
                self.assertTrue(any(crook in text for text in entry.pre))
                self.assertTrue(any(beat.get('verb') == 'slot' and beat.get('index') == 1 for beat in entry.beats))

    def test_golemancy_clips_never_wait_for_the_sound_of_an_immediate_click(self) -> None:
        entries: dict[str, choreography.Entry] = choreography.load_skill(ROOT, 'golemancy').entries
        for identifier, entry in entries.items():
            for index, beat in enumerate(entry.beats):
                with self.subTest(entry=identifier, beat=index):
                    self.assertNotEqual(beat.get('sound'), 'minecraft:block.amethyst_block.place')

    def test_the_bone_frame_clip_captures_its_owner_only_servant_reveal_live(self) -> None:
        entry: choreography.Entry = choreography.load_skill(ROOT, 'golemancy').entries['golem-bone-frame']
        self.assertIn('demo give {actor} 1 crook', ' '.join(entry.pre))
        self.assertTrue(entry.pov_capture)

    def test_every_sheet_names_a_known_world(self) -> None:
        for sheet in sorted((ROOT / 'src' / 'test' / 'demo' / 'sheets').glob('*.json')):
            self.assertEqual(choreography.world_problems(choreography.load_skill(ROOT, sheet.stem).world), [], sheet.name)

    def test_sheet_without_a_world_plays_on_the_iris_demo_world(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_skill(root, {'x-one': {'skip': 'Nothing visible.'}})
            sheet: choreography.Sheet = choreography.load_skill(root, 'x')
        self.assertEqual(sheet.world, 'iris:adapt_demo')
        self.assertEqual(choreography.OVERWORLD, 'iris:adapt_demo')

    def test_declared_world_is_returned_and_is_not_an_adaptation(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_skill(root, {'world': 'minecraft:the_nether', 'x-one': {'skip': 'Nothing visible.'}})
            sheet: choreography.Sheet = choreography.load_skill(root, 'x')
        self.assertEqual(sheet.world, 'minecraft:the_nether')
        self.assertEqual(list(sheet.entries), ['x-one'])
        self.assertEqual(choreography.world_problems(sheet.world), [])
        self.assertEqual(choreography.validate(sheet.entries, {'x-one'}), [])

    def test_unknown_world_is_reported(self) -> None:
        self.assertEqual(choreography.world_problems('minecraft:the_end'),
                         ['world must be iris:adapt_demo or minecraft:the_nether, not minecraft:the_end'])
        self.assertEqual(choreography.world_problems('world_nether'), ['world must be iris:adapt_demo or minecraft:the_nether, not world_nether'])

    def test_world_that_is_not_text_is_reported_as_json(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_skill(root, {'world': None, 'x-one': {'skip': 'Nothing visible.'}})
            sheet: choreography.Sheet = choreography.load_skill(root, 'x')
        self.assertEqual(choreography.world_problems(sheet.world), ['world must be iris:adapt_demo or minecraft:the_nether, not null'])

    def test_unknown_verb_is_reported(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_skill(root, {'x-one': {'set': 'arena', 'beats': [{'verb': 'teleport'}], 'expect': {'sound': 'a'}}})
            entries = choreography.load_skill(root, 'x').entries
            problems = choreography.validate(entries, {'x-one'})
            self.assertTrue(any('teleport' in p for p in problems))

    def test_missing_expectation_is_reported(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_skill(root, {'x-one': {'set': 'arena', 'beats': [{'verb': 'wait', 'ticks': 5}]}})
            entries = choreography.load_skill(root, 'x').entries
            problems = choreography.validate(entries, {'x-one'})
            self.assertTrue(any('expect' in p for p in problems))

    def test_skip_entry_needs_no_set_beats_or_expect(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_skill(root, {'x-one': {'skip': 'Nothing visible.'}})
            entries = choreography.load_skill(root, 'x').entries
            self.assertEqual(entries['x-one'].skip, 'Nothing visible.')
            self.assertEqual(choreography.validate(entries, {'x-one'}), [])

    def test_skip_with_beats_is_reported(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_skill(root, {'x-one': {'skip': 'Nothing visible.', 'beats': [{'verb': 'wait', 'ticks': 5}]}})
            problems = choreography.validate(choreography.load_skill(root, 'x').entries, {'x-one'})
            self.assertTrue(any('skip' in p and 'beats' in p for p in problems))

    def test_agility_marathoner_compares_matched_live_sprints(self) -> None:
        entry: choreography.Entry = choreography.load_skill(ROOT, 'agility').entries['agility-marathoner']
        self.assertEqual(entry.skip, '')
        self.assertTrue(entry.pov_capture)
        self.assertIsNotNone(entry.expect_visual)
        runs: list[dict] = [beat for beat in entry.beats if beat['verb'] == 'keys']
        self.assertEqual(runs, [{'verb': 'keys', 'hold': ['forward', 'sprint'], 'ticks': 80}] * 2)
        actions: list[str] = [beat['text'].split()[-1] if beat['verb'] == 'command' else 'sprint'
                             for beat in entry.beats if beat['verb'] in ('command', 'keys')]
        self.assertEqual(actions, ['baseline', 'sprint', 'finish', 'learned', 'sprint', 'finish', 'clear'])

    def test_discovery_polymath_compares_actual_sword_awards(self) -> None:
        entry: choreography.Entry = choreography.load_skill(ROOT, 'discovery').entries['discovery-polymath']
        self.assertEqual(entry.skip, '')
        self.assertTrue(entry.pov_capture)
        self.assertIsNotNone(entry.expect_visual)
        actions: list[str] = [beat['text'].split()[-1] if beat['verb'] == 'command' else beat['key']
                             for beat in entry.beats if beat['verb'] in ('command', 'click')]
        self.assertEqual(actions, ['baseline', 'attack', 'finish', 'learned', 'attack', 'finish', 'clear'])

    def test_lapis_return_keeps_table_open_until_actual_refund(self) -> None:
        entry: choreography.Entry = choreography.load_skill(ROOT, 'enchanting').entries['enchanting-lapis-return']
        button: int = next(index for index, beat in enumerate(entry.beats)
                           if beat['verb'] == 'window' and beat['action'] == 'button')
        self.assertEqual(entry.beats[button + 1], {'verb': 'wait', 'ticks': 5})
        self.assertEqual(entry.beats[button + 2], {'verb': 'window', 'action': 'close'})
        self.assertEqual(entry.expect_sound, 'minecraft:block.amethyst_block.chime')
        self.assertEqual(entry.expect_particle, 'glow')

    def test_keys_and_press_need_a_hold(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            self.assertTrue(any('keys needs a non-empty hold' in p for p in beat_problems(tmp, {'verb': 'keys', 'ticks': 5})))
        with tempfile.TemporaryDirectory() as tmp:
            self.assertTrue(any('press needs a non-empty hold' in p for p in beat_problems(tmp, {'verb': 'press', 'hold': [], 'ticks': 5})))

    def test_lease_ticks_must_be_integers_in_range(self) -> None:
        rejected: list[dict] = [{'verb': 'keys', 'hold': ['forward'], 'ticks': 0}, {'verb': 'keys', 'hold': ['forward'], 'ticks': 201},
                                {'verb': 'keys', 'hold': ['forward'], 'ticks': 2.5}, {'verb': 'keys', 'hold': ['forward'], 'ticks': '10'},
                                {'verb': 'keys', 'hold': ['forward']}, {'verb': 'press', 'hold': ['forward'], 'ticks': True},
                                {'verb': 'tap', 'key': 'jump', 'ticks': 0}, {'verb': 'tap', 'key': 'jump', 'ticks': 201}]
        for beat in rejected:
            with tempfile.TemporaryDirectory() as tmp:
                self.assertTrue(any('ticks must be an integer between 1 and 200' in p for p in beat_problems(tmp, beat)), beat)

    def test_tap_without_ticks_uses_default(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            self.assertEqual(beat_problems(tmp, {'verb': 'tap', 'key': 'jump'}), [])

    def test_tap_key_must_be_known(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            self.assertTrue(any('taps unknown key crouch' in p for p in beat_problems(tmp, {'verb': 'tap', 'key': 'crouch'})))

    def test_click_key_must_be_attack_use_swap_hands_or_drop(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            self.assertTrue(any('click key must be attack, use, swapHands, drop or inventory, not jump' in p
                                for p in beat_problems(tmp, {'verb': 'click', 'key': 'jump'})))
        with tempfile.TemporaryDirectory() as tmp:
            self.assertTrue(any('click key must be attack, use, swapHands, drop or inventory' in p for p in beat_problems(tmp, {'verb': 'click'})))
        for key in ('attack', 'use', 'swapHands', 'drop'):
            with tempfile.TemporaryDirectory() as tmp:
                self.assertEqual(beat_problems(tmp, {'verb': 'click', 'key': key}), [], key)

    def test_swap_hands_and_drop_are_input_keys(self) -> None:
        self.assertTrue({'swapHands', 'drop'} <= choreography.KEYS)
        accepted: list[dict] = [{'verb': 'keys', 'hold': ['swapHands'], 'ticks': 2}, {'verb': 'tap', 'key': 'drop'},
                                {'verb': 'press', 'hold': ['drop'], 'off': ['swapHands'], 'ticks': 4}]
        for beat in accepted:
            with tempfile.TemporaryDirectory() as tmp:
                self.assertEqual(beat_problems(tmp, beat), [], beat)

    def test_key_names_are_case_sensitive(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            self.assertTrue(any('taps unknown key swaphands' in p for p in beat_problems(tmp, {'verb': 'tap', 'key': 'swaphands'})))

    def test_off_is_only_allowed_on_press(self) -> None:
        for beat in ({'verb': 'keys', 'hold': ['forward'], 'off': ['jump'], 'ticks': 5}, {'verb': 'tap', 'key': 'jump', 'off': ['sneak']}):
            with tempfile.TemporaryDirectory() as tmp:
                self.assertTrue(any('off is only allowed on press' in p for p in beat_problems(tmp, beat)), beat)

    def test_wait_for_timeout_must_be_positive_integer(self) -> None:
        for timeout in (0, -5, 2.5, '20'):
            with tempfile.TemporaryDirectory() as tmp:
                problems = beat_problems(tmp, {'verb': 'waitFor', 'sound': 'a', 'timeout': timeout})
                self.assertTrue(any('waitFor timeout must be an integer of at least 1' in p for p in problems), timeout)
        with tempfile.TemporaryDirectory() as tmp:
            self.assertEqual(beat_problems(tmp, {'verb': 'waitFor', 'sound': 'a', 'timeout': 1}), [])

    def test_press_lease_out_of_range_is_reported(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_skill(root, {'x-one': {'set': 'runway', 'expect': {'sound': 'a'}, 'beats': [
                {'verb': 'press', 'hold': ['forward'], 'ticks': 201},
                {'verb': 'press', 'hold': ['forward'], 'off': ['crouch'], 'ticks': 0},
            ]}})
            problems = choreography.validate(choreography.load_skill(root, 'x').entries, {'x-one'})
            self.assertTrue(any('beat 0' in p and 'ticks' in p for p in problems))
            self.assertTrue(any('beat 1' in p and 'ticks' in p for p in problems))
            self.assertTrue(any('beat 1' in p and 'crouch' in p for p in problems))

    def test_press_within_lease_range_is_accepted(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_skill(root, {'x-one': {'set': 'runway', 'expect': {'sound': 'a'}, 'beats': [
                {'verb': 'press', 'hold': ['forward', 'sprint'], 'off': ['jump'], 'ticks': 200},
            ]}})
            self.assertEqual(choreography.validate(choreography.load_skill(root, 'x').entries, {'x-one'}), [])

    def test_wait_for_without_target_is_reported(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_skill(root, {'x-one': {'set': 'arena', 'expect': {'sound': 'a'}, 'beats': [{'verb': 'waitFor', 'timeout': 20}]}})
            problems = choreography.validate(choreography.load_skill(root, 'x').entries, {'x-one'})
            self.assertTrue(any('beat 0' in p and 'waitFor' in p for p in problems))

    def test_prelude_is_loaded_and_validated_like_beats(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_skill(root, {'x-one': {'set': 'arena', 'expect': {'sound': 'a'}, 'prelude': [{'verb': 'look', 'yaw': 180, 'pitch': 0},
                                                                                           {'verb': 'teleport'}],
                                         'beats': [{'verb': 'wait', 'ticks': 5}]}})
            entries = choreography.load_skill(root, 'x').entries
            self.assertEqual(entries['x-one'].prelude[0]['verb'], 'look')
            problems = choreography.validate(entries, {'x-one'})
            self.assertTrue(any('prelude 1' in p and 'teleport' in p for p in problems))

    def test_prelude_defaults_to_empty(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_skill(root, {'x-one': {'set': 'arena', 'expect': {'sound': 'a'}, 'beats': [{'verb': 'wait', 'ticks': 5}]}})
            self.assertEqual(choreography.load_skill(root, 'x').entries['x-one'].prelude, [])

    def test_visual_only_entry_is_loaded_and_accepted(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root: Path = Path(tmp)
            write_skill(root, {'x-one': {'set': 'arena', 'expect': {'visual': 'The actor lunges forward.'}, 'beats': [{'verb': 'wait', 'ticks': 5}]}})
            entries: dict[str, choreography.Entry] = choreography.load_skill(root, 'x').entries
            self.assertEqual(entries['x-one'].expect_visual, 'The actor lunges forward.')
            self.assertIsNone(entries['x-one'].expect_sound)
            self.assertIsNone(entries['x-one'].expect_particle)
            self.assertEqual(choreography.validate(entries, {'x-one'}), [])

    def test_visual_defaults_to_none(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root: Path = Path(tmp)
            write_skill(root, {'x-one': {'set': 'arena', 'expect': {'sound': 'a'}, 'beats': [{'verb': 'wait', 'ticks': 5}]}})
            self.assertIsNone(choreography.load_skill(root, 'x').entries['x-one'].expect_visual)

    def test_visual_must_be_a_non_empty_string(self) -> None:
        for visual in ('', '   ', 5, ['lunge'], True):
            with tempfile.TemporaryDirectory() as tmp:
                root: Path = Path(tmp)
                write_skill(root, {'x-one': {'set': 'arena', 'expect': {'visual': visual}, 'beats': [{'verb': 'wait', 'ticks': 5}]}})
                problems: list[str] = choreography.validate(choreography.load_skill(root, 'x').entries, {'x-one'})
                self.assertEqual(problems, ['x-one: expect.visual must be a non-empty string'], visual)

    def test_visual_cannot_be_combined_with_sound_or_particle(self) -> None:
        for expect in ({'visual': 'The actor lunges forward.', 'sound': 'a'}, {'visual': 'The actor lunges forward.', 'particle': 'cloud'}):
            with tempfile.TemporaryDirectory() as tmp:
                root: Path = Path(tmp)
                write_skill(root, {'x-one': {'set': 'arena', 'expect': expect, 'beats': [{'verb': 'wait', 'ticks': 5}]}})
                problems: list[str] = choreography.validate(choreography.load_skill(root, 'x').entries, {'x-one'})
                self.assertEqual(problems, ['x-one: expect.visual cannot be combined with expect.sound or expect.particle'], expect)

    def test_missing_expectation_names_every_option(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root: Path = Path(tmp)
            write_skill(root, {'x-one': {'set': 'arena', 'expect': {}, 'beats': [{'verb': 'wait', 'ticks': 5}]}})
            problems: list[str] = choreography.validate(choreography.load_skill(root, 'x').entries, {'x-one'})
            self.assertEqual(problems, ['x-one: expect.sound, expect.particle or expect.visual is required'])

    def test_agility_entries_aim_before_recording(self) -> None:
        for identifier, entry in choreography.load_skill(ROOT, 'agility').entries.items():
            if entry.skip:
                continue
            self.assertTrue(entry.prelude and entry.prelude[0]['verb'] in ('look', 'lookAt'), identifier)
            self.assertNotIn(entry.beats[0]['verb'], ('look', 'lookAt'), identifier)


    def test_slot_index_must_be_a_hotbar_index(self) -> None:
        for index in (-1, 9, 2.5, '3', True, None):
            beat: dict = {'verb': 'slot'} if index is None else {'verb': 'slot', 'index': index}
            with tempfile.TemporaryDirectory() as tmp:
                self.assertEqual(beat_problems(tmp, beat), ['x-one: beat 0 slot index must be an integer from 0 to 8'], index)
        for index in (0, 8):
            with tempfile.TemporaryDirectory() as tmp:
                self.assertEqual(beat_problems(tmp, {'verb': 'slot', 'index': index}), [], index)

    def test_window_action_must_be_known(self) -> None:
        for beat in ({'verb': 'window', 'action': 'open'}, {'verb': 'window'}):
            with tempfile.TemporaryDirectory() as tmp:
                problems: list[str] = beat_problems(tmp, beat)
                self.assertEqual(problems, ['x-one: beat 0 window action must be list, click, shift, drop, hover, button or close, not ' + str(beat.get('action'))])

    def test_window_click_shift_and_drop_need_a_slot_index(self) -> None:
        for action in ('click', 'shift', 'drop'):
            for slot in (None, -1, 2.5, '3', True):
                beat: dict = {'verb': 'window', 'action': action} if slot is None else {'verb': 'window', 'action': action, 'slot': slot}
                with tempfile.TemporaryDirectory() as tmp:
                    self.assertEqual(beat_problems(tmp, beat), ['x-one: beat 0 window ' + action + ' needs a slot index of at least 0'], beat)

    def test_window_button_must_be_0_or_1(self) -> None:
        for button in (2, -1, 0.5, '1', True):
            with tempfile.TemporaryDirectory() as tmp:
                problems: list[str] = beat_problems(tmp, {'verb': 'window', 'action': 'click', 'slot': 1, 'button': button})
                self.assertEqual(problems, ['x-one: beat 0 window button must be 0 or 1'], button)

    def test_window_list_and_close_take_no_slot_or_button(self) -> None:
        for beat in ({'verb': 'window', 'action': 'list', 'slot': 1}, {'verb': 'window', 'action': 'close', 'button': 0}):
            with tempfile.TemporaryDirectory() as tmp:
                self.assertEqual(beat_problems(tmp, beat), ['x-one: beat 0 window ' + beat['action'] + ' takes no slot or button'], beat)

    def test_valid_window_beats_are_accepted(self) -> None:
        for beat in ({'verb': 'window', 'action': 'list'}, {'verb': 'window', 'action': 'close'},
                     {'verb': 'window', 'action': 'click', 'slot': 0}, {'verb': 'window', 'action': 'click', 'slot': 38, 'button': 1},
                     {'verb': 'window', 'action': 'shift', 'slot': 0, 'button': 0}, {'verb': 'window', 'action': 'drop', 'slot': 10, 'button': 1}):
            with tempfile.TemporaryDirectory() as tmp:
                self.assertEqual(beat_problems(tmp, beat), [], beat)

    def test_anvil_name_text_must_have_1_to_50_characters(self) -> None:
        for text in ('', 5, None, 'x' * 51):
            beat: dict = {'verb': 'anvilName'} if text is None else {'verb': 'anvilName', 'text': text}
            with tempfile.TemporaryDirectory() as tmp:
                self.assertEqual(beat_problems(tmp, beat), ['x-one: beat 0 anvilName text must be a string of 1 to 50 characters'], text)
        for text in ('Blade of Dawn', 'x' * 50):
            with tempfile.TemporaryDirectory() as tmp:
                self.assertEqual(beat_problems(tmp, {'verb': 'anvilName', 'text': text}), [], text)


def opponent_problems(tmp: str, body: dict) -> list[str]:
    root: Path = Path(tmp)
    entry: dict = {'set': 'arena', 'expect': {'sound': 'a'}, 'beats': [{'verb': 'wait', 'ticks': 5}]}
    entry.update(body)
    write_skill(root, {'x-one': entry})
    return choreography.validate(choreography.load_skill(root, 'x').entries, {'x-one'})


SHOWCASE_BEATS: list[dict] = [{'verb': 'wait', 'ticks': 1}]


def showcase_clip(identifier: str = 'x-ritual', **values: object) -> dict:
    clip: dict = {'id': identifier, 'title': 'Ritual', 'heading': 'Animus Plinth', 'set': 'arena', 'expect': {'sound': 'a'}, 'beats': SHOWCASE_BEATS}
    clip.update(values)
    return clip


class ShowcaseSheetTest(unittest.TestCase):
    def sheet(self, tmp: str, body: dict) -> choreography.Sheet:
        root: Path = Path(tmp)
        write_matrix(root, {'x-one': 'x', 'y-other': 'y'})
        write_skill(root, body)
        return choreography.load_skill(root, 'x')

    def adaptation(self) -> dict:
        return {'set': 'arena', 'expect': {'sound': 'a'}, 'beats': SHOWCASE_BEATS}

    def test_showcase_clips_are_loaded_after_the_adaptations_in_sheet_order(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            sheet: choreography.Sheet = self.sheet(tmp, {'x-one': self.adaptation(), 'showcase': [showcase_clip('x-b'), showcase_clip('x-a', title='Second')]})
        self.assertEqual(list(sheet.entries), ['x-one', 'x-b', 'x-a'])
        self.assertIsNone(sheet.entries['x-one'].showcase)
        self.assertEqual(sheet.entries['x-a'].showcase, choreography.Showcase(title='Second', heading='Animus Plinth'))
        self.assertEqual([entry.id for entry in choreography.showcase_entries(sheet.entries)], ['x-b', 'x-a'])
        self.assertEqual(sheet.entries['x-a'].set, 'arena')
        self.assertEqual(sheet.entries['x-a'].beats, SHOWCASE_BEATS)

    def test_showcase_ids_are_accepted(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            sheet: choreography.Sheet = self.sheet(tmp, {'x-one': self.adaptation(), 'showcase': [showcase_clip()]})
        self.assertEqual(choreography.validate(sheet.entries, {'x-one'}, frozenset({'x-one', 'y-other'})), [])

    def test_matrix_coverage_still_applies_to_adaptations(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            sheet: choreography.Sheet = self.sheet(tmp, {'showcase': [showcase_clip()]})
        self.assertEqual(choreography.validate(sheet.entries, {'x-one'}, frozenset({'x-one', 'y-other'})), ['missing choreography for x-one'])

    def test_a_showcase_id_that_is_a_catalog_id_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            sheet: choreography.Sheet = self.sheet(tmp, {'x-one': self.adaptation(), 'showcase': [showcase_clip('y-other')]})
        self.assertEqual(choreography.validate(sheet.entries, {'x-one'}, frozenset({'x-one', 'y-other'})), ['y-other: showcase id is also an adaptation id'])

    def test_a_showcase_id_that_repeats_a_sheet_id_cannot_load(self) -> None:
        for body in ({'x-one': {'skip': 'Nothing visible.'}, 'showcase': [showcase_clip('x-one')]}, {'showcase': [showcase_clip('x-a'), showcase_clip('x-a')]}):
            with self.subTest(body=sorted(body)), tempfile.TemporaryDirectory() as tmp:
                with self.assertRaisesRegex(ValueError, 'showcase id repeats another id'):
                    self.sheet(tmp, body)

    def test_malformed_showcase_lists_are_rejected(self) -> None:
        for showcase in ({'id': 'x-a'}, ['x-a'], [{'title': 'No id'}], [{'id': 7}]):
            with self.subTest(showcase=showcase), tempfile.TemporaryDirectory() as tmp:
                with self.assertRaisesRegex(ValueError, 'showcase must be a list'):
                    self.sheet(tmp, {'x-one': self.adaptation(), 'showcase': showcase})

    def test_title_and_heading_are_required_and_the_heading_is_exact_text(self) -> None:
        cases: dict[str, tuple[dict, str]] = {
            'missing title': ({'title': ' '}, 'x-ritual: showcase title is required'),
            'missing heading': ({'heading': ''}, 'x-ritual: showcase heading is required'),
            'hashes': ({'heading': '## Animus Plinth'}, 'x-ritual: showcase heading must be the exact heading text without the leading hashes'),
            'padding': ({'heading': 'Animus Plinth '}, 'x-ritual: showcase heading must be the exact heading text without the leading hashes'),
            'adaptation heading': ({'heading': 'Frost Frame (`golem-frost-frame`)'}, 'x-ritual: showcase heading must not be an adaptation heading'),
        }
        for label, (values, problem) in cases.items():
            with self.subTest(label), tempfile.TemporaryDirectory() as tmp:
                sheet: choreography.Sheet = self.sheet(tmp, {'x-one': self.adaptation(), 'showcase': [showcase_clip(**values)]})
                self.assertEqual(choreography.validate(sheet.entries, {'x-one'}, frozenset({'x-one'})), [problem])

    def test_a_showcase_heading_may_name_an_adaptation_of_the_same_sheet(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            sheet: choreography.Sheet = self.sheet(tmp, {'x-one': self.adaptation(), 'showcase': [showcase_clip(heading='One (`x-one`)')]})
        self.assertEqual(choreography.validate(sheet.entries, {'x-one'}, frozenset({'x-one', 'y-other'})), [])

    def test_a_showcase_heading_naming_an_adaptation_of_another_sheet_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            sheet: choreography.Sheet = self.sheet(tmp, {'x-one': self.adaptation(), 'showcase': [showcase_clip(heading='Other (`y-other`)')]})
        self.assertEqual(choreography.validate(sheet.entries, {'x-one'}, frozenset({'x-one', 'y-other'})),
                         ['x-ritual: showcase heading must not be an adaptation heading'])

    def test_showcase_clips_get_the_usual_entry_validation(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            sheet: choreography.Sheet = self.sheet(tmp, {'x-one': self.adaptation(), 'showcase': [showcase_clip(beats=[{'verb': 'dance'}])]})
        self.assertEqual(choreography.validate(sheet.entries, {'x-one'}, frozenset({'x-one'})), ['x-ritual: beat 0 has unknown verb dance'])

    def test_showcase_prerequisites_resolve_their_skills(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            sheet: choreography.Sheet = self.sheet(tmp, {'x-one': self.adaptation(),
                                                          'showcase': [showcase_clip(prerequisites=[{'name': 'y-other', 'level': 3}])]})
        self.assertEqual(sheet.entries['x-ritual'].prerequisites, (choreography.Prerequisite(skill='y', name='y-other', level=3),))

    def test_showcase_ids_are_file_stems(self) -> None:
        for identifier in ('', 'X-ritual', 'x/ritual', '../x', 'x--ritual', '-x', 'x ritual'):
            with self.subTest(identifier), tempfile.TemporaryDirectory() as tmp:
                sheet: choreography.Sheet = self.sheet(tmp, {'x-one': self.adaptation(), 'showcase': [showcase_clip(identifier)]})
                self.assertIn(identifier + ': showcase id must be lowercase letters, digits and single hyphens',
                              choreography.validate(sheet.entries, {'x-one'}, frozenset({'x-one'})))

    def test_every_sheet_validates_against_the_whole_catalog(self) -> None:
        for sheet in sorted((ROOT / 'src' / 'test' / 'demo' / 'sheets').glob('*.json')):
            with self.subTest(sheet.stem):
                entries: dict[str, choreography.Entry] = choreography.load_skill(ROOT, sheet.stem).entries
                self.assertEqual(choreography.validate(entries, matrix_ids(sheet.stem), choreography.catalog_ids(ROOT)), [])


class StillSheetTest(unittest.TestCase):
    def entries(self, tmp: str, stills: object) -> dict[str, choreography.Entry]:
        write_skill(Path(tmp), {'x-one': {'set': 'arena', 'expect': {'sound': 'a'}, 'beats': SHOWCASE_BEATS, 'stills': stills}})
        return choreography.load_skill(Path(tmp), 'x').entries

    def test_stills_are_loaded_in_order(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            entries: dict[str, choreography.Entry] = self.entries(tmp, [{'angle': 'observer', 'at': 3.5, 'name': 'plinth-ritual'},
                                                                         {'angle': 'pov', 'at': 2, 'name': 'crook'}])
        self.assertEqual(entries['x-one'].stills, (choreography.Still(angle='observer', at=3.5, name='plinth-ritual'),
                                                   choreography.Still(angle='pov', at=2.0, name='crook')))
        self.assertEqual(choreography.validate(entries, {'x-one'}), [])

    def test_stills_default_to_none(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            write_skill(Path(tmp), {'x-one': {'skip': 'Nothing visible.'}})
            self.assertEqual(choreography.load_skill(Path(tmp), 'x').entries['x-one'].stills, ())

    def test_malformed_stills_are_rejected(self) -> None:
        bad: list[object] = [{'angle': 'pov', 'at': 1, 'name': 'a'}, ['a'], [{'angle': 'pov', 'name': 'a'}], [{'angle': 'pov', 'at': '1', 'name': 'a'}],
                             [{'angle': 'pov', 'at': True, 'name': 'a'}], [{'angle': 1, 'at': 1, 'name': 'a'}], [{'angle': 'pov', 'at': 1}]]
        for stills in bad:
            with self.subTest(stills=stills), tempfile.TemporaryDirectory() as tmp:
                with self.assertRaisesRegex(ValueError, 'x-one: stills must be a list'):
                    self.entries(tmp, stills)

    def test_angle_time_and_name_are_validated(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            entries: dict[str, choreography.Entry] = self.entries(tmp, [{'angle': 'overhead', 'at': 1, 'name': 'a'}, {'angle': 'pov', 'at': -0.5, 'name': 'b'},
                                                                         {'angle': 'pov', 'at': 1, 'name': 'Bad Name'}, {'angle': 'pov', 'at': 1, 'name': 'a'}])
        self.assertEqual(choreography.validate(entries, {'x-one'}), [
            'x-one: still a angle must be pov or observer, not overhead',
            'x-one: still b at must be 0 or more seconds',
            'x-one: still name Bad Name must be lowercase letters, digits and single hyphens',
            'x-one: still name a is used twice'])


class AllowCloseSheetTest(unittest.TestCase):
    def entries(self, tmp: str, windows: object) -> dict[str, choreography.Entry]:
        write_skill(Path(tmp), {'x-one': {'set': 'arena', 'expect': {'sound': 'a'}, 'beats': SHOWCASE_BEATS, 'allowClose': windows}})
        return choreography.load_skill(Path(tmp), 'x').entries

    def test_windows_are_loaded_in_order(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            entries: dict[str, choreography.Entry] = self.entries(tmp, [{'from': 1, 'to': 4.5, 'reason': 'bind-ritual'},
                                                                         {'from': 8, 'to': 12, 'reason': 'frozen-gui'}])
        self.assertEqual(entries['x-one'].allow_close, (choreography.AllowClose(start=1.0, end=4.5, reason='bind-ritual'),
                                                        choreography.AllowClose(start=8.0, end=12.0, reason='frozen-gui')))
        self.assertEqual(choreography.validate(entries, {'x-one'}), [])

    def test_windows_default_to_none(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            write_skill(Path(tmp), {'x-one': {'skip': 'Nothing visible.'}})
            self.assertEqual(choreography.load_skill(Path(tmp), 'x').entries['x-one'].allow_close, ())

    def test_malformed_windows_are_rejected(self) -> None:
        bad: list[object] = [['a'], [{'from': 1, 'to': 2}], [{'from': '1', 'to': 2, 'reason': 'bind-ritual'}], [{'from': True, 'to': 2, 'reason': 'bind-ritual'}],
                             [{'from': 1, 'to': 2, 'reason': 3}], 'always']
        for windows in bad:
            with self.subTest(windows=windows), tempfile.TemporaryDirectory() as tmp:
                with self.assertRaisesRegex(ValueError, 'x-one: allowClose must be a list'):
                    self.entries(tmp, windows)

    def test_a_window_needs_one_of_the_allowed_reasons_and_a_forward_range(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            entries: dict[str, choreography.Entry] = self.entries(tmp, [{'from': 1, 'to': 2, 'reason': 'the golem looks nicer close'},
                                                                         {'from': 5, 'to': 5, 'reason': 'bind-ritual'}, {'from': -1, 'to': 2, 'reason': 'frozen-gui'}])
        self.assertEqual(choreography.validate(entries, {'x-one'}), [
            'x-one: allowClose reason the golem looks nicer close must be bind-ritual or frozen-gui',
            'x-one: allowClose window 5 to 5 must run forward from 0 or more seconds',
            'x-one: allowClose window -1 to 2 must run forward from 0 or more seconds'])


class OpponentSheetTest(unittest.TestCase):
    def test_opponent_flag_defaults_to_false(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root: Path = Path(tmp)
            write_skill(root, {'x-one': {'set': 'arena', 'expect': {'sound': 'a'}, 'beats': [{'verb': 'wait', 'ticks': 5}]},
                               'x-two': {'set': 'arena', 'opponent': True, 'expect': {'sound': 'a'}, 'beats': [{'verb': 'wait', 'ticks': 5}]}})
            entries: dict[str, choreography.Entry] = choreography.load_skill(root, 'x').entries
        self.assertFalse(entries['x-one'].opponent)
        self.assertTrue(entries['x-two'].opponent)

    def test_opponent_beats_are_accepted_in_an_opponent_entry(self) -> None:
        steps: list[dict] = [{'verb': 'look', 'yaw': 0, 'pitch': 0, 'actor': 'opponent'}, {'verb': 'lookAt', 'offset': [0.5, 1.2, 0.5], 'actor': 'opponent'},
                             {'verb': 'keys', 'hold': ['forward'], 'ticks': 5, 'actor': 'opponent'},
                             {'verb': 'press', 'hold': ['forward'], 'ticks': 5, 'actor': 'opponent'}, {'verb': 'tap', 'key': 'jump', 'actor': 'opponent'},
                             {'verb': 'click', 'key': 'attack', 'actor': 'opponent'}, {'verb': 'slot', 'index': 1, 'actor': 'opponent'},
                             {'verb': 'window', 'action': 'close', 'actor': 'opponent'}, {'verb': 'anvilName', 'text': 'Dawn', 'actor': 'opponent'}]
        with tempfile.TemporaryDirectory() as tmp:
            problems: list[str] = opponent_problems(tmp, {'opponent': True, 'prelude': steps[:2], 'beats': steps})
        self.assertEqual(problems, [])

    def test_opponent_beat_needs_an_opponent_entry(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            problems: list[str] = opponent_problems(tmp, {'prelude': [{'verb': 'lookAt', 'offset': [0.5, 1.2, 0.5], 'actor': 'opponent'}],
                                                          'beats': [{'verb': 'click', 'key': 'attack', 'actor': 'opponent'}]})
        self.assertEqual(problems, ['x-one: prelude 0 targets the opponent, but the entry does not set "opponent": true',
                                    'x-one: beat 0 targets the opponent, but the entry does not set "opponent": true'])

    def test_timing_and_server_verbs_cannot_target_the_opponent(self) -> None:
        for beat in ({'verb': 'wait', 'ticks': 5}, {'verb': 'waitFor', 'sound': 'a'}, {'verb': 'command', 'text': 'say hi'}, {'verb': 'hit', 'amount': 2}):
            with tempfile.TemporaryDirectory() as tmp:
                problems: list[str] = opponent_problems(tmp, {'opponent': True, 'beats': [dict(beat, actor='opponent')]})
            self.assertEqual(problems, ['x-one: beat 0 ' + beat['verb'] + ' cannot target the opponent'], beat)

    def test_actor_field_only_names_the_opponent(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            problems: list[str] = opponent_problems(tmp, {'opponent': True, 'beats': [{'verb': 'click', 'key': 'attack', 'actor': 'actor'}]})
        self.assertEqual(problems, ['x-one: beat 0 actor must be opponent, not actor'])

    def test_opponent_placeholder_needs_an_opponent_entry(self) -> None:
        body: dict = {'pre': ['give {opponent} minecraft:iron_sword 1'], 'beats': [{'verb': 'command', 'text': 'effect give {opponent} speed 5'}]}
        with tempfile.TemporaryDirectory() as tmp:
            problems: list[str] = opponent_problems(tmp, body)
        with tempfile.TemporaryDirectory() as tmp:
            accepted: list[str] = opponent_problems(tmp, dict(body, opponent=True))
        self.assertEqual(problems, ['x-one: pre 0 names {opponent}, but the entry does not set "opponent": true',
                                    'x-one: beat 0 names {opponent}, but the entry does not set "opponent": true'])
        self.assertEqual(accepted, [])


if __name__ == '__main__':
    unittest.main()
