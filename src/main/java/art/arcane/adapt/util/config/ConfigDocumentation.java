package art.arcane.adapt.util.config;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ConfigDocumentation {
  private static final Map<String, String> SUMMARY_BY_KEY = Map.ofEntries(
      Map.entry("enabled", "Enables or disables this feature."),
      Map.entry("permanent", "Prevents normal player unlearning after this adaptation is purchased."),
      Map.entry("baseCost", "Base knowledge cost used when learning this adaptation."),
      Map.entry("initialCost", "Knowledge cost required to purchase level 1."),
      Map.entry("costFactor", "Scaling factor applied to higher adaptation levels."),
      Map.entry("maxLevel", "Maximum level a player can reach for this adaptation."),
      Map.entry("setInterval", "Tick interval used by this logic."),
      Map.entry("minXp", "Minimum xp threshold required for this skill logic."),
      Map.entry(
          "language",
          "Locale used for in-game text; supported non-English translations download automatically."
              + " languages/en_US.toml is a regenerated reference, and server edits belong in"
              + " languages/overrides/<locale>.toml."
      ),
      Map.entry("autoUpdateCheck", "Checks for plugin updates during startup."),
      Map.entry("metrics", "Sends anonymous bStats usage metrics."),
      Map.entry("xpInCreative", "Allows skill xp gain while players are in creative or spectator."),
      Map.entry("allowAdaptationsInCreative", "Allows using adaptations in creative mode."),
      Map.entry("blacklistedWorlds", "Namespaced Bukkit world keys where Adapt gameplay is disabled."),
      Map.entry("adaptActivatorBlock", "Block type players right-click to open the skills UI."),
      Map.entry("adaptActivatorBlockName", "Display name used in UI text for the activator block."),
      Map.entry("customModels", "Enables custom model lookups from the models config."),
      Map.entry("advancements", "Enables Adapt advancement registration and grant flow."),
      Map.entry("loginBonus", "Grants the configured login bonus message/rewards."),
      Map.entry("welcomeMessage", "Shows the Adapt welcome message when players join.")
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
      "showSounds",
      "immunitySoundVolume",
      "levelMilestoneSoundVolume"
  );

  private ConfigDocumentation() {
  }

  public static List<String> buildFieldComments(String sourceTag, String path, Field field, Object value) {
    List<String> lines = new ArrayList<>();
    ConfigDoc annotation = field.getAnnotation(ConfigDoc.class);
    String key = field.getName();
    String summary;
    String impact = "";

    if (annotation != null) {
      summary = annotation.value().strip();
      if (isGenericSummary(summary)) {
        summary = defaultSummary(sourceTag, path, field);
      }

      String annotatedImpact = annotation.impact().strip();
      if (!isGenericImpact(annotatedImpact)) {
        impact = annotatedImpact;
      }
    } else {
      summary = SUMMARY_BY_KEY.getOrDefault(key, defaultSummary(sourceTag, path, field));
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
