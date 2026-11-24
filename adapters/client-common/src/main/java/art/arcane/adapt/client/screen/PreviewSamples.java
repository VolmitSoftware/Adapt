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
import java.util.List;
import java.util.Map;

/**
 * A built-in catalog and player for the screen preview and tests: skills with and without progress, a disabled skill,
 * row groups, and adaptations in every state (learned, mastered, affordable, short of knowledge, short of power,
 * locked by a requirement, permanent, disabled), with toggle, choice and grouped preferences. The player's access is
 * what a server with default settings would send: skills with progress in name order, disabled ones left out.
 */
public final class PreviewSamples {
    public static final long REVISION = 7L;

    private PreviewSamples() {
    }

    public static CatalogView catalog() {
        List<SkillView> skills = new ArrayList<>();
        skills.add(skill("agility", "Agility", "Move faster, jump higher and climb anything.", "minecraft:feather", 0x55FF55, true,
                List.of(List.of("agility-wall-jump", "agility-super-jump"), List.of("agility-ladder-slide")), true, List.of(
                        adaptation("agility-wall-jump", "agility", "Wall Jump", "Jump off walls while airborne.", "minecraft:rabbit_foot",
                                5, false, List.of(4, 6, 8, 10, 12), 1, List.of(),
                                List.of(toggle("jump-sound", "Jump sound", "ON", true)), List.of()),
                        adaptation("agility-super-jump", "agility", "Super Jump", "Sneak and jump to leap much higher.", "minecraft:slime_ball",
                                3, false, List.of(8, 10, 12), 1, List.of(), List.of(), List.of()),
                        adaptation("agility-ladder-slide", "agility", "Ladder Slide", "Slide down ladders without slowing.",
                                "minecraft:ladder", 1, false, List.of(5), 1, List.of(), List.of(), List.of()),
                        adaptation("agility-armor-up", "agility", "Armor Up", "Sprinting slowly builds up armor.", "minecraft:iron_chestplate",
                                5, false, List.of(40, 60, 80, 100, 120), 1, List.of(), List.of(), List.of()))));
        skills.add(skill("rift", "Rift", "Bend space: blink, open gates and resist the void.", "minecraft:ender_pearl", 0xAA00AA, true,
                List.of(), true, List.of(
                        adaptation("rift-gate", "rift", "Rift Gate", "Channel to return to a marked spot.", "minecraft:end_portal_frame",
                                5, false, List.of(3, 5, 7, 9, 11), 1, List.of(),
                                List.of(choice("gate-style", "Gate style", "DEFAULT", List.of(
                                                new PreferenceChoiceView("DEFAULT", "Default", IconRef.item("minecraft:purple_stained_glass_pane"), 1),
                                                new PreferenceChoiceView("MINIMAL", "Minimal", IconRef.item("minecraft:white_stained_glass_pane"), 1),
                                                new PreferenceChoiceView("FANCY", "Fancy", IconRef.item("minecraft:amethyst_shard"), 3))),
                                        toggle("gate-sounds", "Gate sounds", "ON", true),
                                        toggle("gate-particles", "Gate particles", "ON", true)),
                                List.of(new PreferenceGroupView("effects", "Effects", IconRef.item("minecraft:blaze_powder"), List.of(
                                        new PreferenceGroupView.Entry("gate-sounds", IconRef.item("minecraft:note_block")),
                                        new PreferenceGroupView.Entry("gate-particles", IconRef.item("minecraft:firework_star")))))),
                        adaptation("rift-blink", "rift", "Rift Blink", "Double tap sprint to blink forward.", "minecraft:ender_eye",
                                4, false, List.of(6, 8, 10, 12), 1, List.of(new RequirementView("rift-gate", 2)), List.of(), List.of()),
                        adaptation("rift-resistance", "rift", "Rift Resistance", "Shrug off void damage. Cannot be undone.",
                                "minecraft:obsidian", 3, true, List.of(5, 7, 9), 1, List.of(), List.of(), List.of()),
                        disabled(adaptation("rift-access", "rift", "Rift Access", "Reach your ender chest anywhere.",
                                "minecraft:ender_chest", 1, false, List.of(10), 1, List.of(), List.of(), List.of())))));
        skills.add(skill("stealth", "Stealth", "Stay unseen and strike from the shadows.", "minecraft:leather_boots", 0x555555, true,
                List.of(), false, List.of(
                        adaptation("stealth-ghost-armor", "stealth", "Ghost Armor", "Gain armor while unseen.", "minecraft:chainmail_chestplate",
                                5, false, List.of(10, 12, 14, 16, 18), 1, List.of(), List.of(), List.of()),
                        adaptation("stealth-vision", "stealth", "Night Vision", "See in the dark while sneaking.", "minecraft:golden_carrot",
                                3, false, List.of(6, 8, 10), 6, List.of(), List.of(), List.of()))));
        skills.add(skill("hunter", "Hunter", "Track, trap and bring down prey.", "minecraft:bone", 0xFF5555, true, List.of(), true,
                List.of(adaptation("hunter-adrenaline", "hunter", "Adrenaline", "Deal more damage at low health.", "minecraft:redstone",
                        5, false, List.of(9, 11, 13, 15, 17), 1, List.of(), List.of(), List.of()))));
        skills.add(skill("herbalism", "Herbalism", "Grow, harvest and brew.", "minecraft:wheat", 0x00AA00, true, List.of(), true,
                List.of()));
        skills.add(skill("taming", "Taming", "Befriend and strengthen animals.", "minecraft:lead", 0xFFFF55, false, List.of(), true,
                List.of()));
        return new CatalogView(REVISION, "en_us", skills);
    }

    public static PlayerView player() {
        List<SkillLineView> lines = List.of(
                new SkillLineView("agility", 5400.0D, 14, 0.42D, 860.0D, 30L),
                new SkillLineView("rift", 2100.0D, 9, 0.75D, 300.0D, 22L),
                new SkillLineView("stealth", 900.0D, 5, 0.1D, 640.0D, 50L),
                new SkillLineView("hunter", 12.0D, 0, 0.05D, 188.0D, 0L),
                new SkillLineView("herbalism", 0.0D, 0, 0.0D, 200.0D, 0L),
                new SkillLineView("taming", 300.0D, 2, 0.5D, 100.0D, 4L));
        Map<String, Integer> levels = Map.of("agility-wall-jump", 2, "agility-ladder-slide", 1, "rift-gate", 1);
        List<PreferenceValue> preferences = new ArrayList<>();
        for (SkillLineView line : lines) {
            preferences.add(new PreferenceValue(new PreferenceKey(PreferenceOwner.SKILL, line.skillId(), "enabled"), "ON"));
        }
        preferences.add(value("agility-wall-jump", "jump-sound", "ON"));
        preferences.add(value("rift-gate", "gate-style", "DEFAULT"));
        preferences.add(value("rift-gate", "gate-sounds", "ON"));
        preferences.add(value("rift-gate", "gate-particles", "OFF"));
        return new PlayerView(new PlayerHeader(12, 0.63D, 3, 8, 0L, true), lines, levels, preferences, access());
    }

    public static PlayerAccess access() {
        return new PlayerAccess(false, true, true, List.of("agility", "hunter", "rift", "stealth"), Map.of(
                "agility", List.of(List.of("agility-wall-jump", "agility-super-jump"), List.of("agility-ladder-slide"),
                        List.of("agility-armor-up")),
                "hunter", List.of(List.of("hunter-adrenaline")),
                "rift", List.of(List.of("rift-blink", "rift-gate", "rift-resistance")),
                "stealth", List.of(List.of("stealth-ghost-armor", "stealth-vision")),
                "herbalism", List.of()));
    }

    private static PreferenceValue value(String adaptationId, String preferenceId, String value) {
        return new PreferenceValue(new PreferenceKey(PreferenceOwner.ADAPTATION, adaptationId, preferenceId), value);
    }

    private static SkillView skill(String id, String name, String description, String icon, int color, boolean enabled,
                                   List<List<String>> rowGroups, boolean editable, List<AdaptationView> adaptations) {
        return new SkillView(id, name, description, IconRef.item(icon), color, enabled, rowGroups,
                List.of(toggle("enabled", "Skill adaptations enabled", "ON", editable)), adaptations);
    }

    private static AdaptationView adaptation(String id, String skillId, String name, String description, String icon, int maxLevel,
                                             boolean permanent, List<Integer> costs, int power, List<RequirementView> requirements,
                                             List<PreferenceView> preferences, List<PreferenceGroupView> groups) {
        List<Integer> powers = new ArrayList<>();
        List<List<String>> stats = new ArrayList<>();
        for (int level = 1; level <= maxLevel; level++) {
            powers.add(level == 1 ? power : 0);
            stats.add(List.of("§a+ " + (level * 10) + "% §7strength", "§c- " + (level * 2) + " §7hunger per use"));
        }
        return new AdaptationView(id, skillId, name, description, IconRef.item(icon), true, maxLevel, permanent, costs, costs,
                powers, requirements, stats, preferences, groups);
    }

    private static AdaptationView disabled(AdaptationView view) {
        return new AdaptationView(view.id(), view.skillId(), view.name(), view.description(), view.icon(), false, view.maxLevel(),
                view.permanent(), view.knowledgeCosts(), view.refunds(), view.powerCosts(), view.requirements(), view.statLines(),
                view.preferences(), view.preferenceGroups());
    }

    private static PreferenceView toggle(String id, String label, String value, boolean editable) {
        return new PreferenceView(id, label, value, List.of(
                new PreferenceChoiceView("ON", "On", IconRef.item("minecraft:lime_dye"), 0),
                new PreferenceChoiceView("OFF", "Off", IconRef.item("minecraft:gray_dye"), 0)), editable);
    }

    private static PreferenceView choice(String id, String label, String value, List<PreferenceChoiceView> choices) {
        return new PreferenceView(id, label, value, choices, true);
    }
}
