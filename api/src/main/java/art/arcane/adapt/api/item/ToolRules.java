package art.arcane.adapt.api.item;

import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.RegistryLookup;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Consumer;

/**
 * An operator's tool and block lists for one {@link art.arcane.adapt.platform.item.ToolKind}, and which automatic
 * rules of {@link Tools} stay on. Entries are item or block ids such as {@code mekanismtools:osmium_paxel}, or tags
 * written with a leading {@code #} such as {@code #forge:tools/hammers}; a bare path means {@code minecraft}.
 */
@ApiStatus.Experimental
public final class ToolRules {
  /** No operator entries and every automatic rule on. */
  public static final ToolRules AUTOMATIC = new ToolRules(Entries.NONE, Entries.NONE, Map.of(), Map.of(), Entries.NONE,
      true, true, true, null);

  private final Entries tools;
  private final Entries excludedTools;
  private final Map<Id, Double> blockXp;
  private final Map<Id, Double> blockTagXp;
  private final Entries excludedBlocks;
  private final boolean tagRule;
  private final boolean capabilityRule;
  private final boolean behaviourRule;
  private final @Nullable RegistryLookup registries;

  private ToolRules(Entries tools, Entries excludedTools, Map<Id, Double> blockXp, Map<Id, Double> blockTagXp,
      Entries excludedBlocks, boolean tagRule, boolean capabilityRule, boolean behaviourRule,
      @Nullable RegistryLookup registries) {
    this.tools = tools;
    this.excludedTools = excludedTools;
    this.blockXp = blockXp;
    this.blockTagXp = blockTagXp;
    this.excludedBlocks = excludedBlocks;
    this.tagRule = tagRule;
    this.capabilityRule = capabilityRule;
    this.behaviourRule = behaviourRule;
    this.registries = registries;
  }

  /**
   * Reads an operator's lists. {@code source} names the config file, such as {@code skills/adapt/pickaxe.toml}. An
   * entry that is not an id or tag is skipped, and one that names no registered item or block, or a tag with no
   * members, is kept; each is reported once through {@code warnings} with the file and key. Nothing is thrown.
   */
  public static ToolRules parse(String source, List<String> extraTools, List<String> excludedTools,
      Map<String, Double> extraBlocks, List<String> excludedBlocks, boolean tagRule, boolean capabilityRule,
      boolean behaviourRule, RegistryLookup registries, Consumer<String> warnings) {
    Reader reader = new Reader(source, registries, warnings);
    Map<Id, Double> blockXp = new LinkedHashMap<>();
    Map<Id, Double> blockTagXp = new LinkedHashMap<>();
    if (extraBlocks != null) {
      for (Map.Entry<String, Double> entry : extraBlocks.entrySet()) {
        Double xp = entry.getValue();
        if (xp == null || !Double.isFinite(xp) || xp < 0D) {
          warnings.accept(source + " extraBlocks: '" + entry.getKey() + "' needs an XP value of 0 or more; skipped");
          continue;
        }
        Entry parsed = reader.entry("extraBlocks", entry.getKey(), RegistryKind.BLOCK);
        if (parsed != null) {
          (parsed.tag() ? blockTagXp : blockXp).put(parsed.id(), xp);
        }
      }
    }
    return new ToolRules(reader.entries("extraTools", extraTools, RegistryKind.ITEM),
        reader.entries("excludedTools", excludedTools, RegistryKind.ITEM), Map.copyOf(blockXp),
        Map.copyOf(blockTagXp), reader.entries("excludedBlocks", excludedBlocks, RegistryKind.BLOCK), tagRule,
        capabilityRule, behaviourRule, registries);
  }

  /** Whether the tag rule is on. */
  public boolean tagRule() {
    return tagRule;
  }

  /** Whether the loader capability rule is on. */
  public boolean capabilityRule() {
    return capabilityRule;
  }

  /** Whether the correct-tool behaviour rule is on, for kinds and for single blocks. */
  public boolean behaviourRule() {
    return behaviourRule;
  }

  /** {@link ToolMatch#EXCLUDED}, {@link ToolMatch#OPERATOR} or {@link ToolMatch#NONE} for the item type. */
  public ToolMatch operatorMatch(Id item) {
    if (excludedTools.contains(RegistryKind.ITEM, item, registries)) {
      return ToolMatch.EXCLUDED;
    }
    return tools.contains(RegistryKind.ITEM, item, registries) ? ToolMatch.OPERATOR : ToolMatch.NONE;
  }

  /** True when the excluded-blocks list names the block or one of its tags. */
  public boolean excludesBlock(Id block) {
    return excludedBlocks.contains(RegistryKind.BLOCK, block, registries);
  }

  /** The XP the extra-blocks list gives the block, by id first and then by tag; empty when it is not listed. */
  public OptionalDouble blockXp(Id block) {
    Double byId = blockXp.get(block);
    if (byId != null) {
      return OptionalDouble.of(byId);
    }
    if (registries != null) {
      for (Map.Entry<Id, Double> entry : blockTagXp.entrySet()) {
        if (registries.isTagged(RegistryKind.BLOCK, entry.getKey(), block)) {
          return OptionalDouble.of(entry.getValue());
        }
      }
    }
    return OptionalDouble.empty();
  }

  private record Entry(Id id, boolean tag) {
  }

  private record Entries(Set<Id> ids, Set<Id> tags) {
    static final Entries NONE = new Entries(Set.of(), Set.of());

    boolean contains(RegistryKind kind, Id value, @Nullable RegistryLookup registries) {
      if (ids.contains(value)) {
        return true;
      }
      if (registries != null) {
        for (Id tag : tags) {
          if (registries.isTagged(kind, tag, value)) {
            return true;
          }
        }
      }
      return false;
    }
  }

  private record Reader(String source, RegistryLookup registries, Consumer<String> warnings) {
    Entries entries(String key, @Nullable List<String> values, RegistryKind kind) {
      if (values == null || values.isEmpty()) {
        return Entries.NONE;
      }
      Set<Id> ids = new LinkedHashSet<>();
      Set<Id> tags = new LinkedHashSet<>();
      for (String value : values) {
        Entry parsed = entry(key, value, kind);
        if (parsed != null) {
          (parsed.tag() ? tags : ids).add(parsed.id());
        }
      }
      return new Entries(Set.copyOf(ids), Set.copyOf(tags));
    }

    @Nullable Entry entry(String key, @Nullable String raw, RegistryKind kind) {
      String text = raw == null ? "" : raw.trim();
      boolean tag = text.startsWith("#");
      Id id = Id.tryParse(tag ? text.substring(1) : text);
      String noun = kind == RegistryKind.ITEM ? "item" : "block";
      if (id == null) {
        warnings.accept(source + " " + key + ": '" + raw + "' is not " + (kind == RegistryKind.ITEM ? "an " : "a ") + noun
            + " id or #tag; skipped");
        return null;
      }
      if (tag && registries.tagValues(kind, id).isEmpty()) {
        warnings.accept(source + " " + key + ": tag #" + id + " has no " + noun + "s on this server");
      } else if (!tag && !registries.exists(kind, id)) {
        warnings.accept(source + " " + key + ": " + id + " is not a registered " + noun + " on this server");
      }
      return new Entry(id, tag);
    }
  }
}
