package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.view.AdaptationView;
import art.arcane.adapt.client.view.CatalogView;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.client.view.PlayerAccess;
import art.arcane.adapt.client.view.PlayerHeader;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.PreferenceChoiceView;
import art.arcane.adapt.client.view.PreferenceKey;
import art.arcane.adapt.client.view.PreferenceOwner;
import art.arcane.adapt.client.view.PreferenceValue;
import art.arcane.adapt.client.view.PreferenceView;
import art.arcane.adapt.client.view.SkillLineView;
import art.arcane.adapt.client.view.SkillView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A catalog shaped like the one players see: up to 24 skills under their real names with two digit levels, each with
 * 13 adaptations of five levels in one group, two stat lines a level, a switch plus a two way choice as the
 * preferences of {@link #ADAPTATION}, the switch alone for {@link #SWITCH_ADAPTATION} and the switch above a checklist
 * of eighteen for {@link #CHECKLIST_ADAPTATION}. What {@link MaxCatalog} is to
 * the extremes, this is to the everyday page.
 */
final class TypicalCatalog {
    static final int SKILLS = 24;
    static final int ADAPTATIONS = 13;
    static final String SKILL = "agility";
    static final String ADAPTATION = "agility-wall-jump";
    static final String PLAIN_ADAPTATION = "agility-vault";
    static final String SWITCH_ADAPTATION = "agility-marathoner";
    static final String CHECKLIST_ADAPTATION = "agility-armor-up";
    private static final String[] EFFECTS = {"Shorten harmful effects", "Speed", "Jump Boost", "Regeneration", "Resistance",
            "Fire Resistance", "Water Breathing", "Invisibility", "Night Vision", "Health Boost", "Absorption", "Saturation", "Luck",
            "Slow Falling", "Dolphins Grace", "Hero Of The Village", "Haste", "Strength"};
    private static final String[] NAMES = {"Agility", "Architect", "Axes", "Blocking", "Brewing", "Chronos", "Crafting", "Discovery",
            "Enchanting", "Golemancy", "Herbalism", "Hunter", "Nether", "Pickaxe", "Ranged", "Rift", "Seaborne", "Stealth", "Swords",
            "Taming", "TragOul", "Unarmed", "Excavation", "Kinetics"};
    private static final String[] ADAPTATION_NAMES = {"Air Dash", "Armor-Up", "Cat Reflexes", "Featherfoot", "Kip-Up", "Ladder Slide",
            "Marathoner", "Roll Landing", "Slipstream Slide", "Super Jump", "Vault", "Wall Jump", "Wind Up"};

    private TypicalCatalog() {
    }

    static CatalogView catalog() {
        return catalog(SKILLS);
    }

    static CatalogView catalog(int skills) {
        List<SkillView> views = new ArrayList<>();
        for (int index = 0; index < skills; index++) {
            views.add(skill(index));
        }
        return new CatalogView(1L, "en_us", views);
    }

    static String skillId(int index) {
        return NAMES[index].toLowerCase();
    }

    static String adaptationId(int skill, int number) {
        return skillId(skill) + "-" + ADAPTATION_NAMES[number].toLowerCase().replace(' ', '-');
    }

    static PlayerView player() {
        return player(SKILLS);
    }

    static PlayerView player(int skills) {
        List<SkillLineView> lines = new ArrayList<>();
        Map<String, Integer> levels = new HashMap<>();
        List<PreferenceValue> preferences = new ArrayList<>();
        Map<String, List<List<String>>> rows = new LinkedHashMap<>();
        List<String> skillIds = new ArrayList<>();
        List<SkillView> views = catalog(skills).skills();
        for (int index = 0; index < views.size(); index++) {
            SkillView skill = views.get(index);
            skillIds.add(skill.id());
            lines.add(new SkillLineView(skill.id(), 5400.0D, 12 + index * 7 % 80, 0.2D + index % 5 * 0.15D, 860.0D, 12L + index * 15L));
            rows.put(skill.id(), skill.rowGroups());
            preferences.add(new PreferenceValue(new PreferenceKey(PreferenceOwner.SKILL, skill.id(), "enabled"), "ON"));
            for (int number = 0; number < skill.adaptations().size(); number++) {
                levels.put(skill.adaptations().get(number).id(), number % 3);
            }
        }
        levels.put(ADAPTATION, 2);
        levels.put(SWITCH_ADAPTATION, 5);
        levels.put(CHECKLIST_ADAPTATION, 1);
        preferences.add(new PreferenceValue(new PreferenceKey(PreferenceOwner.ADAPTATION, SWITCH_ADAPTATION, "enabled"), "ON"));
        preferences.add(new PreferenceValue(new PreferenceKey(PreferenceOwner.ADAPTATION, ADAPTATION, "enabled"), "ON"));
        preferences.add(new PreferenceValue(new PreferenceKey(PreferenceOwner.ADAPTATION, ADAPTATION, "control"), "SNEAK"));
        return new PlayerView(new PlayerHeader(34, 0.5D, 30, 90, 0L, true), lines, levels, preferences,
                new PlayerAccess(false, true, true, skillIds, rows));
    }

    private static SkillView skill(int index) {
        String id = skillId(index);
        List<AdaptationView> adaptations = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (int number = 0; number < ADAPTATIONS; number++) {
            String adaptationId = adaptationId(index, number);
            ids.add(adaptationId);
            adaptations.add(adaptation(id, adaptationId, ADAPTATION_NAMES[number]));
        }
        return new SkillView(id, NAMES[index], "Move faster, jump further and land softer.", IconRef.item("minecraft:feather"),
                0x40A040 + index * 0x070301, true, List.of(ids), List.of(toggle("enabled", "Skill adaptations enabled")), adaptations);
    }

    private static AdaptationView adaptation(String skillId, String id, String name) {
        List<Integer> costs = new ArrayList<>();
        List<Integer> powers = new ArrayList<>();
        List<List<String>> stats = new ArrayList<>();
        for (int level = 1; level <= 5; level++) {
            costs.add(3 + level * 2);
            powers.add(1);
            stats.add(List.of("§a+ " + (level + 1) + " §7Max Jumps", "§a+ " + (30 + level * 23) + "% §7Jump Height"));
        }
        List<PreferenceView> preferences = id.equals(SWITCH_ADAPTATION) ? List.of(toggle("enabled", "Adaptation enabled"))
                : id.equals(CHECKLIST_ADAPTATION) ? checklist() : !id.equals(ADAPTATION) ? List.of() : List.of(
                toggle("enabled", "Adaptation enabled"),
                new PreferenceView("control", "Wall jump control", "SNEAK", List.of(
                        new PreferenceChoiceView("SNEAK", "Sneak", IconRef.item("minecraft:leather_boots"), 0),
                        new PreferenceChoiceView("JUMP", "Jump", IconRef.item("minecraft:feather"), 0)), true));
        return new AdaptationView(id, skillId, name, "Hold shift while mid-air against a wall to latch, then release shift to jump.",
                IconRef.item("minecraft:rabbit_foot"), true, 5, false, costs, costs, powers, List.of(), stats, preferences, List.of());
    }

    private static List<PreferenceView> checklist() {
        List<PreferenceView> preferences = new ArrayList<>();
        preferences.add(toggle("enabled", "Adaptation enabled"));
        for (int index = 0; index < EFFECTS.length; index++) {
            preferences.add(toggle("effect-" + index, EFFECTS[index]));
        }
        return preferences;
    }

    private static PreferenceView toggle(String id, String label) {
        return new PreferenceView(id, label, "ON", List.of(
                new PreferenceChoiceView("ON", "On", IconRef.item("minecraft:lime_dye"), 0),
                new PreferenceChoiceView("OFF", "Off", IconRef.item("minecraft:gray_dye"), 0)), true);
    }
}
