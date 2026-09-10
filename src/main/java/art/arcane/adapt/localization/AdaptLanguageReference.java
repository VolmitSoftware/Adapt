package art.arcane.adapt.localization;

import art.arcane.adapt.Adapt;
import art.arcane.volmlib.util.localization.LanguageFileHeader;
import art.arcane.volmlib.util.localization.LanguageReferenceRenderer;
import art.arcane.volmlib.util.localization.MessageCatalog;

import java.io.File;
import java.util.List;
import java.util.Map;

public final class AdaptLanguageReference {
  private AdaptLanguageReference() {
  }

  public static File folder() {
    return new File(Adapt.instance.getDataFolder(), "languages");
  }

  public static File referenceFile() {
    return new File(folder(), AdaptMessages.catalog().englishLocale() + ".toml");
  }

  public static boolean write() {
    if (referenceFile().exists()) {
      return true;
    }
    return LanguageFileWriter.write(
        referenceFile().toPath(),
        "language reference",
        render(AdaptMessages.catalog())
    );
  }

  static String render(MessageCatalog catalog) {
    return LanguageReferenceRenderer.render(catalog, LanguageFileHeader.render(new LanguageFileHeader.Options(
        "Adapt", catalog.englishLocale(),
        List.of("Command prefixes are applied by the command renderer. {prefix} is the prefix of a statistic value."),
        List.of("Colors: &0-&f, &k-&r, &#RRGGBB and MiniMessage. Bracket gradients follow automatic-gradients.",
            "Lists retain their line order; plural tables use the forms supplied in the English catalog."),
        Map.ofEntries(
            Map.entry("adaptation", "Adaptation name"),
            Map.entry("adaptations", "Matching Adaptation names"),
            Map.entry("after", "Value after an edit"),
            Map.entry("amount", "Currency, knowledge or experience amount"),
            Map.entry("argument", "Unexpected command argument"),
            Map.entry("available", "Available power or knowledge"),
            Map.entry("before", "Value before an edit"),
            Map.entry("benefit", "Mutation benefit description"),
            Map.entry("block", "Block named in advancement instructions"),
            Map.entry("bonus", "Masterwork bonus amount"),
            Map.entry("burden", "Mutation drawback description"),
            Map.entry("category", "Configuration category name"),
            Map.entry("chance", "Activation probability"),
            Map.entry("combo", "Instant Recall trigger combination"),
            Map.entry("command", "Command path"),
            Map.entry("control", "Mutation usage instructions"),
            Map.entry("cost", "Knowledge cost"),
            Map.entry("count", "Number of items, files or messages"),
            Map.entry("damage", "Added damage amount"),
            Map.entry("danger", "Gate travel danger warning"),
            Map.entry("deepCharge", "Stored Deep Charge amount"),
            Map.entry("deepblood", "Linked Deepblood tool status"),
            Map.entry("description", "Advancement description"),
            Map.entry("direction", "Direction toward a structure"),
            Map.entry("distance", "Distance to a structure in blocks"),
            Map.entry("domain", "Mutation skill group name"),
            Map.entry("duration", "Formatted duration or cooldown"),
            Map.entry("effect", "Potion effect name"),
            Map.entry("enchantment", "Enchantment name"),
            Map.entry("environment", "Environment affecting an ability"),
            Map.entry("error", "Configuration error details"),
            Map.entry("experience", "Experience stored in an orb"),
            Map.entry("file", "Configuration file name"),
            Map.entry("first", "First skill group or trigger component"),
            Map.entry("from", "First entry index on a page"),
            Map.entry("group", "Language editor category"),
            Map.entry("hits", "Successful cache lookups"),
            Map.entry("instruction", "Advancement unlock instruction"),
            Map.entry("interaction", "Interaction with the other mutation slot"),
            Map.entry("key", "Message or configuration key"),
            Map.entry("knowledge", "Knowledge amount"),
            Map.entry("label", "Setting or statistic label"),
            Map.entry("level", "Skill, Adaptation or effect level"),
            Map.entry("levels", "Experience levels saved"),
            Map.entry("line", "Language message line number"),
            Map.entry("locale", "Language code"),
            Map.entry("masterwork", "Linked Masterwork equipment status"),
            Map.entry("maximum", "Maximum allowed value or power limit"),
            Map.entry("message", "Status message before cooldown details"),
            Map.entry("microsPerCheck", "Microseconds spent per ability check"),
            Map.entry("millisPerSecond", "Milliseconds spent checking abilities per second"),
            Map.entry("minimum", "Minimum allowed value"),
            Map.entry("misses", "Cache lookups without a stored result"),
            Map.entry("multiplier", "Experience boost multiplier"),
            Map.entry("mutation", "Mutation name"),
            Map.entry("needed", "Required power or knowledge"),
            Map.entry("newValue", "New configuration value"),
            Map.entry("nextLevel", "Next skill level display"),
            Map.entry("oldValue", "Previous configuration value"),
            Map.entry("other", "Conflicting mutation name"),
            Map.entry("page", "Current page number"),
            Map.entry("pages", "Total number of pages"),
            Map.entry("parameter", "Command parameter name"),
            Map.entry("parameters", "Ignored command parameters"),
            Map.entry("particle", "Particle type name"),
            Map.entry("path", "Configuration path"),
            Map.entry("perMinute", "Ability checks per minute"),
            Map.entry("perSecond", "Ability checks per second"),
            Map.entry("percent", "Percentage of timing budget or experience bonus"),
            Map.entry("permission", "Required permission node"),
            Map.entry("personal", "Personal language code"),
            Map.entry("player", "Player receiving the action"),
            Map.entry("plugin", "Plugin name"),
            Map.entry("power", "Power amount or consumption"),
            Map.entry("prefix", "Statistic value prefix"),
            Map.entry("progress", "Skill progress display"),
            Map.entry("range", "Search or ability range in blocks"),
            Map.entry("ratio", "Cache hit ratio"),
            Map.entry("reason", "Mutation status or editor error explanation"),
            Map.entry("refund", "Knowledge refunded when unlearning"),
            Map.entry("remaining", "Number of omitted configuration changes"),
            Map.entry("result", "Mutation effect at level 200"),
            Map.entry("rootCharge", "Stored Root Charge amount"),
            Map.entry("second", "Second skill group or trigger component"),
            Map.entry("seconds", "Duration in seconds"),
            Map.entry("separator", "Separator between statistic value and label"),
            Map.entry("skill", "Skill name"),
            Map.entry("slot", "Mutation slot number"),
            Map.entry("slots", "Number of backpack slots"),
            Map.entry("stacks", "Effect stacks or backpack stack capacity"),
            Map.entry("state", "Enabled state or mutation slot status"),
            Map.entry("steps", "Completed crafting, brewing or enchanting steps"),
            Map.entry("strength", "Added armor strength"),
            Map.entry("structure", "Located structure name"),
            Map.entry("surface", "Surface affecting the trigger"),
            Map.entry("symbol", "Structure indicator symbol"),
            Map.entry("target", "Target player or language plugin"),
            Map.entry("tell", "Visible mutation effect description"),
            Map.entry("temperbound", "Linked Temperbound equipment status"),
            Map.entry("timestamp", "Configuration archive timestamp"),
            Map.entry("to", "Last entry index on a page"),
            Map.entry("total", "Total number of entries or slots"),
            Map.entry("trigger", "Ability activation condition"),
            Map.entry("trophy", "Linked trophy status"),
            Map.entry("type", "Expected parameter or value type"),
            Map.entry("usage", "Command syntax"),
            Map.entry("used", "Used power or occupied slots"),
            Map.entry("value", "Current setting or statistic value"),
            Map.entry("values", "Allowed setting values"),
            Map.entry("variables", "Allowed message placeholders"),
            Map.entry("window", "Performance measurement window in milliseconds"),
            Map.entry("world", "World name"),
            Map.entry("x", "Target X coordinate"),
            Map.entry("xp", "Experience amount"),
            Map.entry("z", "Target Z coordinate")
        )
    )));
  }
}
