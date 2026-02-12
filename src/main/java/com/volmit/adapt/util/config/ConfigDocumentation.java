package com.volmit.adapt.util.config;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    private ConfigDocumentation() {
    }

    public static List<String> buildFieldComments(String sourceTag, String path, Field field, Object value) {
        List<String> lines = new ArrayList<>();
        ConfigDoc annotation = field.getAnnotation(ConfigDoc.class);
        if (annotation != null) {
            lines.add(annotation.value().strip());
            if (!annotation.impact().isBlank()) {
                lines.add("Effect: " + annotation.impact().strip());
            }
            return lines;
        }

        String key = field.getName();
        String summary = SUMMARY_BY_KEY.getOrDefault(key, defaultSummary(sourceTag, path, field));
        String impact = IMPACT_BY_KEY.getOrDefault(key, defaultImpact(field, value));
        lines.add(summary);
        if (!impact.isBlank()) {
            lines.add("Effect: " + impact);
        }
        return lines;
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
        if (type == boolean.class || type == Boolean.class) {
            return "True enables this behavior and false disables it.";
        }
        if (Number.class.isAssignableFrom(type) || type.isPrimitive() && type != boolean.class && type != char.class) {
            return "Higher values usually increase intensity, limits, or frequency; lower values reduce it.";
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
