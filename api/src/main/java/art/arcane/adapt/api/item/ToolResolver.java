package art.arcane.adapt.api.item;

import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.RegistryLookup;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.ToolKind;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockStateRef;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** The rules behind {@link Tools}, over a port so they run without a server. */
final class ToolResolver {
  static final List<ToolKind> MINING = List.of(ToolKind.PICKAXE, ToolKind.AXE, ToolKind.SHOVEL);
  private static final Map<ToolKind, Set<Id>> TOOL_TAGS = Map.of(
      ToolKind.PICKAXE, tags("minecraft:pickaxes", "forge:tools/pickaxes", "c:pickaxes", "fabric:pickaxes"),
      ToolKind.AXE, tags("minecraft:axes", "forge:tools/axes", "c:axes", "fabric:axes"),
      ToolKind.SHOVEL, tags("minecraft:shovels", "forge:tools/shovels", "c:shovels", "fabric:shovels"),
      ToolKind.HOE, tags("minecraft:hoes", "forge:tools/hoes", "c:hoes", "fabric:hoes"),
      ToolKind.SWORD, tags("minecraft:swords", "forge:tools/swords", "c:swords", "fabric:swords"));
  private static final Map<ToolKind, Set<Id>> BLOCK_TAGS = Map.of(
      ToolKind.PICKAXE, tags("minecraft:mineable/pickaxe", "forge:ores", "c:ores", "forge:stone", "c:stones"),
      ToolKind.AXE, tags("minecraft:mineable/axe"),
      ToolKind.SHOVEL, tags("minecraft:mineable/shovel"),
      ToolKind.HOE, tags("minecraft:mineable/hoe"),
      ToolKind.SWORD, Set.of());
  private static final Map<ToolKind, Id> REFERENCE_BLOCKS = Map.of(ToolKind.PICKAXE, Id.minecraft("stone"),
      ToolKind.AXE, Id.minecraft("oak_log"), ToolKind.SHOVEL, Id.minecraft("dirt"));
  private static final Map<ToolKind, Set<String>> VANILLA = Map.of(
      ToolKind.PICKAXE, materials("pickaxe"),
      ToolKind.AXE, materials("axe"),
      ToolKind.SHOVEL, materials("shovel"),
      ToolKind.HOE, materials("hoe"),
      ToolKind.SWORD, materials("sword"));

  /** What the resolver reads from the platform. */
  interface Port {
    RegistryLookup registries();

    Set<ToolKind> toolActions(ItemRef item);

    @Nullable BlockStateRef state(Id block);
  }

  private final Port port;
  private final Map<ToolKind, ToolRules> rules = new ConcurrentHashMap<>();
  private final Map<Id, Set<ToolKind>> taggedItems = new ConcurrentHashMap<>();
  private final Map<Id, Set<ToolKind>> claimedBlocks = new ConcurrentHashMap<>();
  private final Map<ToolKind, BlockStateRef> references = new ConcurrentHashMap<>();

  ToolResolver(Port port) {
    this.port = port;
  }

  static Set<Id> toolTags(ToolKind kind) {
    return TOOL_TAGS.get(kind);
  }

  static Set<Id> blockTags(ToolKind kind) {
    return BLOCK_TAGS.get(kind);
  }

  static boolean vanilla(Id item, ToolKind kind) {
    return item.isMinecraft() && VANILLA.get(kind).contains(item.path());
  }

  void rules(ToolKind kind, ToolRules value) {
    rules.put(kind, value);
  }

  ToolRules rules(ToolKind kind) {
    return rules.getOrDefault(kind, ToolRules.AUTOMATIC);
  }

  void invalidate() {
    taggedItems.clear();
    claimedBlocks.clear();
    references.clear();
  }

  ToolMatch match(ItemRef item, ToolKind kind) {
    if (item == null || item.isEmpty()) {
      return ToolMatch.NONE;
    }
    Id type = item.type();
    ToolRules active = rules(kind);
    ToolMatch operator = active.operatorMatch(type);
    if (operator == ToolMatch.EXCLUDED) {
      return operator;
    }
    if (usedUp(item)) {
      return ToolMatch.BROKEN;
    }
    if (operator == ToolMatch.OPERATOR) {
      return operator;
    }
    if (active.tagRule() && taggedKinds(type).contains(kind)) {
      return stopsMining(item, kind) ? ToolMatch.BROKEN : ToolMatch.TAG;
    }
    if (active.capabilityRule() && port.toolActions(item).contains(kind)) {
      return ToolMatch.CAPABILITY;
    }
    if (active.behaviourRule() && REFERENCE_BLOCKS.containsKey(kind)) {
      BlockStateRef reference = reference(kind);
      if (reference != null && reference.isCorrectTool(item)) {
        return ToolMatch.BEHAVIOUR;
      }
    }
    return vanilla(type, kind) ? ToolMatch.VANILLA : ToolMatch.NONE;
  }

  /**
   * A damageable modded stack its tag calls a digging kind, which is no longer the correct tool for the kind's
   * reference block: a broken Tinkers' Construct tool keeps its tags and durability bar but stops mining. Vanilla items
   * are left alone, so a platform that cannot answer the correct-tool question for every block never hides them.
   */
  private boolean stopsMining(ItemRef item, ToolKind kind) {
    if (item.type().isMinecraft() || item.maxDamage() <= 0 || !REFERENCE_BLOCKS.containsKey(kind)) {
      return false;
    }
    BlockStateRef reference = reference(kind);
    return reference != null && !reference.isCorrectTool(item);
  }

  Set<ToolKind> kinds(ItemRef item) {
    Set<ToolKind> out = EnumSet.noneOf(ToolKind.class);
    for (ToolKind kind : ToolKind.values()) {
      if (match(item, kind).matches()) {
        out.add(kind);
      }
    }
    return Collections.unmodifiableSet(out);
  }

  boolean claims(ToolKind kind, Id block) {
    ToolRules active = rules(kind);
    if (active.excludesBlock(block)) {
      return false;
    }
    return active.blockXp(block).isPresent() || claimedKinds(block).contains(kind);
  }

  boolean serves(ItemRef item, BlockStateRef block, ToolKind kind) {
    if (item == null || block == null || item.isEmpty()) {
      return false;
    }
    Id type = block.type();
    ToolRules active = rules(kind);
    if (active.excludesBlock(type)) {
      return false;
    }
    ToolMatch match = match(item, kind);
    if (match == ToolMatch.EXCLUDED || match == ToolMatch.BROKEN) {
      return false;
    }
    boolean claimed = claims(kind, type);
    if (!match.matches()) {
      return active.behaviourRule() && claimed && block.isCorrectTool(item);
    }
    if (claimed) {
      return true;
    }
    for (ToolKind other : MINING) {
      if (other != kind && match(item, other).matches()) {
        return false;
      }
    }
    return true;
  }

  boolean isEffective(ItemRef item, BlockStateRef block) {
    if (item == null || block == null || item.isEmpty() || usedUp(item)) {
      return false;
    }
    if (block.isCorrectTool(item)) {
      return true;
    }
    for (ToolKind kind : ToolKind.values()) {
      if (claims(kind, block.type()) && match(item, kind).matches()) {
        return true;
      }
    }
    return false;
  }

  /** A damageable stack whose damage reached its maximum without breaking, which modded tools such as Tinkers' allow. */
  static boolean usedUp(ItemRef item) {
    int max = item.maxDamage();
    return max > 0 && !item.isUnbreakable() && item.damage() >= max;
  }

  boolean isTagged(ItemRef item, Id tag) {
    return item != null && !item.isEmpty() && port.registries().isTagged(RegistryKind.ITEM, tag, item.type());
  }

  boolean isBlockTagged(Id block, Id tag) {
    return port.registries().isTagged(RegistryKind.BLOCK, tag, block);
  }

  private Set<ToolKind> taggedKinds(Id item) {
    return taggedItems.computeIfAbsent(item, (Id type) -> tagged(RegistryKind.ITEM, TOOL_TAGS, type));
  }

  private Set<ToolKind> claimedKinds(Id block) {
    return claimedBlocks.computeIfAbsent(block, (Id type) -> tagged(RegistryKind.BLOCK, BLOCK_TAGS, type));
  }

  private Set<ToolKind> tagged(RegistryKind registry, Map<ToolKind, Set<Id>> table, Id value) {
    RegistryLookup registries = port.registries();
    Set<ToolKind> out = EnumSet.noneOf(ToolKind.class);
    for (Map.Entry<ToolKind, Set<Id>> entry : table.entrySet()) {
      for (Id tag : entry.getValue()) {
        if (registries.isTagged(registry, tag, value)) {
          out.add(entry.getKey());
          break;
        }
      }
    }
    return out.isEmpty() ? Set.of() : Collections.unmodifiableSet(out);
  }

  private @Nullable BlockStateRef reference(ToolKind kind) {
    BlockStateRef cached = references.get(kind);
    if (cached != null) {
      return cached;
    }
    BlockStateRef state = port.state(REFERENCE_BLOCKS.get(kind));
    if (state != null) {
      references.put(kind, state);
    }
    return state;
  }

  private static Set<Id> tags(String... values) {
    Set<Id> out = new LinkedHashSet<>();
    for (String value : values) {
      out.add(Id.parse(value));
    }
    return Collections.unmodifiableSet(out);
  }

  private static Set<String> materials(String tool) {
    return Set.of("wooden_" + tool, "stone_" + tool, "copper_" + tool, "iron_" + tool, "golden_" + tool,
        "diamond_" + tool, "netherite_" + tool);
  }
}
