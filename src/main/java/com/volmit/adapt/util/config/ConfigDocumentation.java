package com.volmit.adapt.util.config;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ConfigDocumentation {
    private static final Map<String, String> SUMMARY_BY_KEY = Map.ofEntries(
            Map.entry("enabled", "Enables or disables this feature."),
            Map.entry("permanent", "Keeps this adaptation permanently active once learned."),
            Map.entry("baseCost", "Base knowledge cost used when learning this adaptation."),
            Map.entry("initialCost", "Knowledge cost required to purchase level 1."),
            Map.entry("costFactor", "Scaling factor applied to higher adaptation levels."),
            Map.entry("maxLevel", "Maximum level a player can reach for this adaptation."),
            Map.entry("setInterval", "Tick interval used by this logic."),
            Map.entry("minXp", "Minimum xp threshold required for this skill logic."),
            Map.entry("language", "Primary language file used for localizations."),
            Map.entry("fallbackLanguageDontChangeUnlessYouKnowWhatYouAreDoing", "Fallback language used when a localization key is missing."),
            Map.entry("autoUpdateLanguage", "When enabled, language files are refreshed from plugin resources."),
            Map.entry("autoUpdateCheck", "Checks for plugin updates during startup."),
            Map.entry("metrics", "Sends anonymous bStats usage metrics."),
            Map.entry("xpInCreative", "Allows skill xp gain while players are in creative or spectator."),
            Map.entry("allowAdaptationsInCreative", "Allows using adaptations in creative mode."),
            Map.entry("blacklistedWorlds", "World folder names where Adapt logic is disabled."),
            Map.entry("experienceMaxLevel", "Global maximum level cap for skill progression."),
            Map.entry("adaptActivatorBlock", "Block type players right-click to open the skills UI."),
            Map.entry("adaptActivatorBlockName", "Display name used in UI text for the activator block."),
            Map.entry("customModels", "Enables custom model lookups from the models config."),
            Map.entry("advancements", "Enables Adapt advancement registration and grant flow."),
            Map.entry("loginBonus", "Grants the configured login bonus message/rewards."),
            Map.entry("welcomeMessage", "Shows the Adapt welcome message when players join."),
            Map.entry("useSql", "Uses SQL as the player data backend."),
            Map.entry("useRedis", "Enables Redis synchronization when SQL is active.")
    );

    private static final Map<String, String> IMPACT_BY_KEY = Map.ofEntries(
            Map.entry("enabled", "Set to false to disable behavior without uninstalling files."),
            Map.entry("permanent", "True removes the normal learn/unlearn flow and treats it as always learned."),
            Map.entry("baseCost", "Higher values make each level cost more knowledge."),
            Map.entry("initialCost", "Higher values make unlocking the first level more expensive."),
            Map.entry("costFactor", "Higher values increase level-to-level cost growth."),
            Map.entry("maxLevel", "Higher values allow more levels; lower values cap progression sooner."),
            Map.entry("setInterval", "Lower values run logic more often; higher values run it less often."),
            Map.entry("minXp", "Higher values delay when this skill starts applying."),
            Map.entry("metrics", "Set to false to opt out of bStats telemetry."),
            Map.entry("xpInCreative", "Set to true if you want creative/spectator players to gain xp."),
            Map.entry("allowAdaptationsInCreative", "Set to true to let creative players trigger adaptations."),
            Map.entry("experienceMaxLevel", "Higher values raise the hard cap for skill leveling."),
            Map.entry("customModels", "Set to false to disable all custom model assignments."),
            Map.entry("advancements", "Set to false to disable advancement creation and toast notifications."),
            Map.entry("useSql", "Switching this changes where player data is loaded/saved."),
            Map.entry("useRedis", "Requires SQL support and Redis credentials to synchronize across servers.")
    );
    private static final Set<String> ALWAYS_VISIBLE_KEYS = Set.of(
            "enabled",
            "permanent",
            "baseCost",
            "initialCost",
            "costFactor",
            "maxLevel",
            "minXp",
            "showParticles",
            "showSounds"
    );

    private ConfigDocumentation() {
    }

    public static List<String> buildFieldComments(String sourceTag, String path, Field field, Object value) {
        List<String> lines = new ArrayList<>();
        ConfigDoc annotation = field.getAnnotation(ConfigDoc.class);
        String key = field.getName();
        String summary;
        String impact;

        if (annotation != null) {
            summary = annotation.value().strip();
            impact = annotation.impact().strip();
            if (isGenericSummary(summary)) {
                summary = defaultSummary(sourceTag, path, field);
            }
            if (impact.isBlank() || isGenericImpact(impact)) {
                impact = defaultImpact(field, value);
            }
        } else {
            summary = SUMMARY_BY_KEY.getOrDefault(key, defaultSummary(sourceTag, path, field));
            impact = IMPACT_BY_KEY.getOrDefault(key, defaultImpact(field, value));
        }

        if (summary != null && !summary.isBlank()) {
            lines.add(summary);
        }
        if (!impact.isBlank()) {
            lines.add("Effect: " + impact);
        }
        return lines;
    }

    public static boolean shouldExposeField(String sourceTag, String path, Field field, Object value) {
        if (field == null) {
            return false;
        }
        if (field.getAnnotation(ConfigAdvanced.class) != null) {
            return false;
        }

        String key = field.getName();
        if (ALWAYS_VISIBLE_KEYS.contains(key)) {
            return true;
        }

        String lowered = key.toLowerCase(Locale.ROOT);
        Class<?> type = field.getType();
        boolean isBoolean = type == boolean.class || type == Boolean.class;

        // Hide challenge reward tuning; these are rarely gameplay-critical knobs.
        if (lowered.startsWith("challenge") && lowered.contains("reward")) {
            return false;
        }

        // Internal update cadence knobs are advanced and should stay out of default configs.
        if (lowered.equals("setinterval") || lowered.equals("statintervalms")) {
            return false;
        }

        // Hide over-granular audiovisual tuning by default.
        if (lowered.contains("pitch") || lowered.contains("volume")) {
            return false;
        }
        if (lowered.contains("sound") && !isBoolean) {
            return false;
        }
        if (lowered.contains("particlesize") || lowered.contains("particlecount") || lowered.contains("particleevery")) {
            return false;
        }
        if (lowered.contains("xoffset") || lowered.contains("yoffset") || lowered.contains("zoffset")) {
            return false;
        }

        // Hide fallback/anti-edge tuning that is mostly diagnostic.
        if (lowered.contains("fallback") || lowered.contains("variance") || lowered.contains("curveexponent")) {
            return false;
        }

        return true;
    }

    public static List<String> buildSectionComments(String sourceTag, String path) {
        if (path == null || path.isBlank()) {
            return List.of();
        }

        String leaf = path;
        int idx = leaf.lastIndexOf('.');
        if (idx >= 0 && idx + 1 < leaf.length()) {
            leaf = leaf.substring(idx + 1);
        }

        String humanLeaf = humanize(leaf);
        if (sourceTag != null && sourceTag.startsWith("skill:")) {
            return List.of("Settings for the " + sourceTag.substring("skill:".length()) + " skill " + humanLeaf + " section.");
        }
        if (sourceTag != null && sourceTag.startsWith("adaptation:")) {
            return List.of("Settings for the " + sourceTag.substring("adaptation:".length()) + " adaptation " + humanLeaf + " section.");
        }

        return List.of("Settings for " + humanLeaf + ".");
    }

    private static String defaultSummary(String sourceTag, String path, Field field) {
        String key = field.getName();
        String lower = key.toLowerCase(Locale.ROOT);
        String subject = subject(sourceTag, path);
        if (lower.contains("cooldown")) {
            return "Cooldown between " + subject + " activations.";
        }
        if (lower.contains("chance")) {
            return "Chance for " + subject + " to trigger.";
        }
        if (lower.contains("xp")) {
            return "XP gain tuning for " + subject + ".";
        }
        if (lower.contains("multiplier") || lower.contains("factor") || lower.contains("scalar")) {
            return "Scaling applied to " + subject + ".";
        }
        if (lower.contains("duration") || lower.contains("ticks") || lower.contains("millis") || lower.endsWith("ms")) {
            return "Duration or timing used by " + subject + ".";
        }
        if (lower.contains("radius") || lower.contains("range") || lower.contains("distance")) {
            return "Distance/area limit used by " + subject + ".";
        }
        if (lower.startsWith("min") || lower.contains("threshold")) {
            return "Minimum threshold required for " + subject + ".";
        }
        if (lower.startsWith("max") || lower.contains("cap")) {
            return "Maximum cap applied to " + subject + ".";
        }

        String label = humanize(field.getName());
        if (sourceTag != null && sourceTag.startsWith("skill:")) {
            return "Controls " + label + " for the " + sourceTag.substring("skill:".length()) + " skill.";
        }
        if (sourceTag != null && sourceTag.startsWith("adaptation:")) {
            return "Controls " + label + " for the " + sourceTag.substring("adaptation:".length()) + " adaptation.";
        }
        if (path != null && !path.isBlank()) {
            return "Controls " + label + " in the " + path + " section.";
        }
        return "Controls " + label + ".";
    }

    private static String defaultImpact(Field field, Object value) {
        Class<?> type = field.getType();
        String lower = field.getName().toLowerCase(Locale.ROOT);
        if (type == boolean.class || type == Boolean.class) {
            return "True enables this behavior and false disables it.";
        }
        if (lower.contains("chance")) {
            return "Use values near 0.0-1.0; higher values trigger more often.";
        }
        if (lower.contains("cooldown")) {
            return "Higher values increase time between activations; lower values allow more frequent triggers.";
        }
        if (lower.contains("xp")) {
            return "Higher values grant more progression; lower values slow progression.";
        }
        if (lower.contains("multiplier") || lower.contains("factor") || lower.contains("scalar")) {
            return "Higher values scale the effect more strongly; lower values scale it down.";
        }
        if (lower.contains("duration") || lower.contains("ticks") || lower.contains("millis") || lower.endsWith("ms")) {
            return "Higher values make the effect last longer; lower values shorten it.";
        }
        if (lower.contains("radius") || lower.contains("range") || lower.contains("distance")) {
            return "Higher values affect a wider area; lower values keep the effect tighter.";
        }
        if (lower.startsWith("min") || lower.contains("threshold")) {
            return "Higher values make activation stricter; lower values make it easier to trigger.";
        }
        if (lower.startsWith("max") || lower.contains("cap")) {
            return "Higher values raise the upper limit; lower values clamp the effect sooner.";
        }
        if (Number.class.isAssignableFrom(type) || type.isPrimitive() && type != boolean.class && type != char.class) {
            return "Higher values increase intensity or limits; lower values reduce them.";
        }
        if (type.isEnum()) {
            return "Changing this selects a different operating mode.";
        }
        if (type == String.class || type == char.class || type == Character.class) {
            return "Changing this alters the identifier or text used by the feature.";
        }
        if (value instanceof List<?>) {
            return "Add or remove entries to control which values are included.";
        }
        if (value instanceof Map<?, ?>) {
            return "Edit entries to control per-key overrides for this feature.";
        }
        return "";
    }

    private static boolean isGenericSummary(String summary) {
        if (summary == null || summary.isBlank()) {
            return true;
        }

        String lower = summary.toLowerCase(Locale.ROOT).trim();
        return lower.startsWith("controls ") || lower.equals("no description provided");
    }

    private static boolean isGenericImpact(String impact) {
        if (impact == null || impact.isBlank()) {
            return true;
        }

        String lower = impact.toLowerCase(Locale.ROOT);
        return lower.contains("higher values usually increase intensity, limits, or frequency; lower values reduce it.")
                || lower.contains("true enables this behavior and false disables it.");
    }

    private static String subject(String sourceTag, String path) {
        if (sourceTag != null && sourceTag.startsWith("skill:")) {
            return "the " + sourceTag.substring("skill:".length()) + " skill";
        }
        if (sourceTag != null && sourceTag.startsWith("adaptation:")) {
            return "the " + sourceTag.substring("adaptation:".length()) + " adaptation";
        }
        if (path != null && !path.isBlank()) {
            return "the " + path + " section";
        }
        return "this feature";
    }

    private static String humanize(String key) {
        if (key == null || key.isBlank()) {
            return "this setting";
        }

        String spaced = key
                .replace('_', ' ')
                .replace('-', ' ')
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .trim();
        if (spaced.isBlank()) {
            return key;
        }

        String lower = spaced.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
