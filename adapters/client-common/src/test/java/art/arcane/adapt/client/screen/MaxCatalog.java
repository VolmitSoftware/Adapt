package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.view.AdaptationView;
import art.arcane.adapt.client.view.CatalogView;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.client.view.PlayerAccess;
import art.arcane.adapt.client.view.PlayerHeader;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.PreferenceChoiceView;
import art.arcane.adapt.client.view.PreferenceGroupView;
import art.arcane.adapt.client.view.PreferenceKey;
import art.arcane.adapt.client.view.PreferenceOwner;
import art.arcane.adapt.client.view.PreferenceValue;
import art.arcane.adapt.client.view.PreferenceView;
import art.arcane.adapt.client.view.RequirementView;
import art.arcane.adapt.client.view.SkillLineView;
import art.arcane.adapt.client.view.SkillView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A catalog at the real catalog's maxima, all at once: the most skills, the largest skill (its adaptations in the most
 * and widest row groups) and three adaptations with the most levels and requirements, the longest names, labels and
 * stat lines, each filling the most page lines ({@link #ROWS}) with a different heaviest mix: a big toggle group with a
 * choice, the most plain switches, the most choice preferences. The numbers are core's CatalogFitBudgetTest budget,
 * which the real catalog is held to on every version. The player has every skill unlocked at a three digit level and
 * every adaptation's preferences open.
 */
final class MaxCatalog {
    static final int SKILLS = 24;
    static final int ADAPTATIONS = 19;
    static final int[] ROW_GROUPS = {8, 8, 2, 1};
    static final int LEVELS = 8;
    static final int STAT_LINES = 9;
    static final int STAT_CHARS = 114;
    static final int REQUIREMENTS = 2;
    static final int NAME_CHARS = 25;
    static final int UNGROUPED_TOGGLES = 19;
    static final int CHOICE_PREFERENCES = 4;
    static final int ROWS = 13;
    static final int SWITCH_ROW = 3;
    static final int GROUP_ROW = 15;
    static final int CHOICES = 13;
    static final int GROUP_ENTRIES = 20;
    static final int LABEL_CHARS = 37;
    static final int GROUP_LABEL_CHARS = 9;
    static final String LARGEST_SKILL = "skill00";
    static final String GROUPED_ADAPTATION = "skill00-a00";
    static final String SWITCHES_ADAPTATION = "skill00-a01";
    static final String CHOICES_ADAPTATION = "skill00-a02";
    private static final String[] ITEMS = {"minecraft:feather", "minecraft:iron_axe", "minecraft:shield", "minecraft:brewing_stand",
            "minecraft:clock", "minecraft:crafting_table", "minecraft:spyglass", "minecraft:enchanting_table", "minecraft:iron_shovel",
            "minecraft:wheat", "minecraft:bow", "minecraft:netherrack", "minecraft:iron_pickaxe", "minecraft:ender_pearl"};

    private MaxCatalog() {
    }

    static CatalogView catalog() {
        List<SkillView> skills = new ArrayList<>();
        for (int index = 0; index < SKILLS; index++) {
            skills.add(skill(index));
        }
        return new CatalogView(1L, "en_us", skills);
    }

    static PlayerView player() {
        List<SkillLineView> lines = new ArrayList<>();
        Map<String, Integer> levels = new HashMap<>();
        List<PreferenceValue> preferences = new ArrayList<>();
        Map<String, List<List<String>>> rows = new LinkedHashMap<>();
        List<String> skillIds = new ArrayList<>();
        for (SkillView skill : catalog().skills()) {
            skillIds.add(skill.id());
            lines.add(new SkillLineView(skill.id(), 1_234_567.0D, 140, 0.6D, 98_765.0D, 123_456L));
            rows.put(skill.id(), skill.rowGroups());
            for (AdaptationView adaptation : skill.adaptations()) {
                levels.put(adaptation.id(), Math.min(adaptation.maxLevel(), 3));
                for (PreferenceView preference : adaptation.preferences()) {
                    preferences.add(new PreferenceValue(new PreferenceKey(PreferenceOwner.ADAPTATION, adaptation.id(), preference.id()),
                            preference.defaultValue()));
                }
            }
        }
        return new PlayerView(new PlayerHeader(999, 0.5D, 120, 999, 0L, true), lines, levels, preferences,
                new PlayerAccess(false, true, true, skillIds, rows));
    }

    private static SkillView skill(int index) {
        String id = String.format("skill%02d", index);
        int count = index == 0 ? ADAPTATIONS : 15;
        List<AdaptationView> adaptations = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (int number = 0; number < count; number++) {
            String adaptationId = id + "-a" + String.format("%02d", number);
            ids.add(adaptationId);
            AdaptationView adaptation = plain(id, adaptationId, number);
            if (index == 0 && number < SHAPES.size()) {
                adaptation = largest(id, adaptationId, SHAPES.get(number), number + 1);
            }
            adaptations.add(adaptation);
        }
        List<List<String>> rowGroups = new ArrayList<>();
        int[] sizes = index == 0 ? ROW_GROUPS : new int[] {8, 7};
        int at = 0;
        for (int size : sizes) {
            rowGroups.add(List.copyOf(ids.subList(at, at + size)));
            at += size;
        }
        return new SkillView(id, "Skill " + "ABCDEFGHIJKLMNOPQRSTUVWX".charAt(index) + "xxxxx",
                "A skill whose description is long enough to need cutting in any compact layout at all.",
                IconRef.item(ITEMS[index % ITEMS.length]), 0x40A0FF + index * 0x070301, true, rowGroups,
                List.of(toggle("enabled", "Skill adaptations enabled")), adaptations);
    }

    private static AdaptationView plain(String skillId, String id, int number) {
        int levels = 1 + number % 5;
        List<Integer> costs = new ArrayList<>();
        List<Integer> powers = new ArrayList<>();
        List<List<String>> stats = new ArrayList<>();
        for (int level = 1; level <= levels; level++) {
            costs.add(level * 7);
            powers.add(level);
            stats.add(List.of("§a+ " + level * 10 + "% §7strength"));
        }
        return new AdaptationView(id, skillId, "Adaptation " + number, "Does something useful.", IconRef.item(ITEMS[number % ITEMS.length]),
                true, levels, false, costs, costs, powers, List.of(), stats, List.of(), List.of());
    }

    /**
     * The three heaviest mixes, each {@link #ROWS} lines: stat lines plus switches three to a line, one line per choice
     * preference and one per fifteen group entries.
     */
    private static final List<Shape> SHAPES = List.of(
            new Shape(GROUP_ENTRIES, 3, 1),
            new Shape(0, UNGROUPED_TOGGLES, 0),
            new Shape(0, 4, CHOICE_PREFERENCES));

    private record Shape(int grouped, int switches, int choicePreferences) {
        int statLines() {
            int rows = (switches + SWITCH_ROW - 1) / SWITCH_ROW + choicePreferences + (grouped + GROUP_ROW - 1) / GROUP_ROW;
            return Math.min(STAT_LINES, ROWS - rows);
        }
    }

    private static AdaptationView largest(String skillId, String id, Shape shape, int firstRequirement) {
        List<Integer> costs = new ArrayList<>();
        List<Integer> powers = new ArrayList<>();
        List<List<String>> stats = new ArrayList<>();
        for (int level = 1; level <= LEVELS; level++) {
            costs.add(level * 1234);
            powers.add(level * 3);
            List<String> lines = new ArrayList<>();
            for (int line = 0; line < shape.statLines(); line++) {
                lines.add("§a+" + "x".repeat(STAT_CHARS - 1));
            }
            stats.add(lines);
        }
        List<RequirementView> requirements = new ArrayList<>();
        for (int index = 0; index < REQUIREMENTS; index++) {
            requirements.add(new RequirementView(skillId + "-a" + String.format("%02d", firstRequirement + index), 2));
        }
        List<PreferenceView> preferences = new ArrayList<>();
        List<PreferenceGroupView.Entry> entries = new ArrayList<>();
        for (int index = 0; index < shape.grouped(); index++) {
            String preferenceId = "grouped-" + index;
            preferences.add(toggle(preferenceId, label(index)));
            entries.add(new PreferenceGroupView.Entry(preferenceId, IconRef.item(ITEMS[index % ITEMS.length])));
        }
        for (int index = 0; index < shape.switches(); index++) {
            preferences.add(toggle("toggle-" + index, label(index)));
        }
        List<PreferenceChoiceView> choices = new ArrayList<>();
        for (int index = 0; index < CHOICES; index++) {
            choices.add(new PreferenceChoiceView("C" + index, "Choice " + index, IconRef.item(ITEMS[index % ITEMS.length]), index % 4));
        }
        for (int index = 0; index < shape.choicePreferences(); index++) {
            preferences.add(new PreferenceView(index == 0 ? "style" : "style-" + index, label(90 + index), "C0", choices, true));
        }
        PreferenceGroupView group = new PreferenceGroupView("crops", "G".repeat(GROUP_LABEL_CHARS), IconRef.item("minecraft:wheat"), entries);
        return new AdaptationView(id, skillId, "N".repeat(NAME_CHARS),
                "A very long description that goes on and on about what this adaptation does, well past any single line. ".repeat(5),
                IconRef.item("minecraft:wheat_seeds"), true, LEVELS, true, costs, costs, powers, requirements, stats, preferences,
                entries.isEmpty() ? List.of() : List.of(group));
    }

    private static String label(int index) {
        String prefix = "Label " + index + " ";
        return prefix + "l".repeat(LABEL_CHARS - prefix.length());
    }

    private static PreferenceView toggle(String id, String label) {
        return new PreferenceView(id, label, "ON", List.of(
                new PreferenceChoiceView("ON", "On", IconRef.item("minecraft:lime_dye"), 0),
                new PreferenceChoiceView("OFF", "Off", IconRef.item("minecraft:gray_dye"), 0)), true);
    }
}
