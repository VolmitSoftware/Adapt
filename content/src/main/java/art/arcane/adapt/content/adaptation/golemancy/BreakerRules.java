package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.content.golem.GolemToolEnchants;
import art.arcane.adapt.content.golem.GolemToolNeed;
import art.arcane.adapt.content.golem.GolemToolNote;
import art.arcane.adapt.content.golem.GolemToolPlan;
import art.arcane.adapt.content.golem.node.NodeFilters;
import art.arcane.adapt.content.golem.node.NodeRecord;
import art.arcane.adapt.content.golem.node.NodeTagLookup;
import art.arcane.adapt.api.item.MaterialNames;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

final class BreakerRules {
  static final String BREAK = "break";
  static final String SILK_KEY = "silk";
  static final String FORTUNE_KEY = "fortune";
  static final List<String> DEFAULT_TOOLS = List.of("STONE_PICKAXE", "IRON_PICKAXE", "IRON_PICKAXE", "DIAMOND_PICKAXE", "NETHERITE_PICKAXE");
  static final List<String> DEFAULT_BLACKLIST = List.of("BEDROCK", "BARRIER", "COMMAND_BLOCK", "CHAIN_COMMAND_BLOCK", "REPEATING_COMMAND_BLOCK",
      "STRUCTURE_BLOCK", "STRUCTURE_VOID", "JIGSAW", "SPAWNER", "TRIAL_SPAWNER", "VAULT", "END_PORTAL_FRAME", "REINFORCED_DEEPSLATE",
      "BUDDING_AMETHYST", "#beds", "#doors");

  private static final String ON = "true";
  private static final char TAG_PREFIX = '#';
  private static final String PICKAXE_SUFFIX = "_pickaxe";
  private static final Id WOODEN_PICKAXE = Id.minecraft("wooden_pickaxe");
  private static final Id STONE_PICKAXE = Id.minecraft("stone_pickaxe");
  private static final Id COPPER_PICKAXE = Id.minecraft("copper_pickaxe");
  private static final Id GOLDEN_PICKAXE = Id.minecraft("golden_pickaxe");
  private static final Id IRON_PICKAXE = Id.minecraft("iron_pickaxe");
  private static final Id DIAMOND_PICKAXE = Id.minecraft("diamond_pickaxe");
  private static final Id NETHERITE_PICKAXE = Id.minecraft("netherite_pickaxe");
  private static final Id FREE_TIER = IRON_PICKAXE;
  private static final List<Id> PICKAXE_COSTS = List.of(WOODEN_PICKAXE, STONE_PICKAXE, COPPER_PICKAXE, GOLDEN_PICKAXE, IRON_PICKAXE,
      DIAMOND_PICKAXE, NETHERITE_PICKAXE);
  private static final List<Id> TIER_LADDER = List.of(WOODEN_PICKAXE, STONE_PICKAXE, IRON_PICKAXE, DIAMOND_PICKAXE, NETHERITE_PICKAXE);
  private static final GolemToolNote SILK_NOTE = new GolemToolNote(Text.key("adapt.golemancy.tools.needs_silk"), null);
  private static final GolemToolNote FORTUNE_NOTE = new GolemToolNote(Text.key("adapt.golemancy.tools.needs_fortune"), null);

  private BreakerRules() {
  }

  static Plan plan(GolemBreakerCore.Config config, Set<Id> blacklist, int level, NodeRecord node, NodeTagLookup tags, ItemRef tool) {
    return new Plan(level, maxHardness(config.maxHardnessByLevel, level), blacklist, node, tags, tool);
  }

  static int intervalTicks(List<Integer> byLevel, int level) {
    return Math.max(1, AnimusBindingRules.atLevel(byLevel, Math.max(1, level), 1));
  }

  static double maxHardness(List<Double> byLevel, int level) {
    double value = AnimusBindingRules.atLevel(byLevel, Math.max(1, level), 0D);
    return Double.isFinite(value) ? value : 0D;
  }

  static List<Id> tools(List<String> byLevel, Predicate<Id> pickaxe, Consumer<String> rejected) {
    if (byLevel == null) {
      return List.of();
    }
    List<Id> tools = new ArrayList<>(byLevel.size());
    for (int index = 0; index < byLevel.size(); index++) {
      String name = byLevel.get(index);
      Id type = name == null ? null : MaterialNames.match(name.trim());
      if (type == null || !pickaxe.test(type)) {
        rejected.accept(String.valueOf(name));
        type = defaultTool(index + 1);
      }
      tools.add(type);
    }
    return List.copyOf(tools);
  }

  static Id toolAt(List<Id> tools, int level) {
    int at = Math.max(1, level);
    return AnimusBindingRules.atLevel(tools, at, defaultTool(at));
  }

  static Id freeTool(Id levelTool) {
    return aboveFree(levelTool) ? FREE_TIER : levelTool;
  }

  static boolean aboveFree(Id levelTool) {
    return tier(levelTool) > tier(FREE_TIER);
  }

  static int cost(ItemRef pickaxe) {
    int at = PICKAXE_COSTS.indexOf(pickaxe.type());
    return at < 0 ? PICKAXE_COSTS.size() : at;
  }

  static GolemToolPlan toolPlan(Predicate<ItemRef> harvests, Function<Id, ItemRef> virtual, Id levelTool, boolean silk, boolean fortune) {
    boolean gated = !harvests.test(virtual.apply(freeTool(levelTool)));
    Id required = gated ? requiredTool(harvests, virtual) : FREE_TIER;
    String tier = required.legacyName();
    List<GolemToolNeed> needs = new ArrayList<>(2);
    if (silk) {
      needs.add(new GolemToolNeed("pickaxe:silk:" + tier, item -> pickaxe(item) && GolemToolEnchants.silkTouch(item) > 0 && harvests.test(item),
          BreakerRules::cost, SILK_NOTE));
    } else if (fortune) {
      needs.add(new GolemToolNeed("pickaxe:fortune:" + tier, item -> pickaxe(item) && GolemToolEnchants.fortune(item) > 0 && harvests.test(item),
          BreakerRules::cost, FORTUNE_NOTE));
    }
    if (gated) {
      needs.add(new GolemToolNeed("pickaxe:" + tier, item -> pickaxe(item) && harvests.test(item), BreakerRules::cost,
          new GolemToolNote(Text.key("adapt.golemancy.tools.needs_tier"), toolName(required))));
    }
    return needs.isEmpty() ? GolemToolPlan.NONE : new GolemToolPlan(needs, !gated);
  }

  static ItemRef dropsTool(ItemRef held, boolean silk, boolean fortune, ItemRef free, Function<Id, ItemRef> virtual) {
    if (held == null) {
      return free;
    }
    boolean enchanted = silk ? GolemToolEnchants.silkTouch(held) > 0 : fortune && GolemToolEnchants.fortune(held) > 0;
    return enchanted ? held : virtual.apply(held.type());
  }

  static String toolName(Id type) {
    return Format.capitalizeWords(type.legacyName().toLowerCase(Locale.ROOT).replace('_', ' '));
  }

  static Set<Id> blacklist(List<String> names, Function<String, Collection<Id>> tags, Predicate<Id> known) {
    Set<Id> blocked = new HashSet<>();
    if (names == null) {
      return blocked;
    }
    for (String name : names) {
      String entry = name == null ? "" : name.trim();
      if (entry.isEmpty()) {
        continue;
      }
      if (entry.charAt(0) == TAG_PREFIX) {
        Collection<Id> tagged = tags.apply(entry);
        if (tagged != null) {
          blocked.addAll(tagged);
        }
        continue;
      }
      Id type = MaterialNames.match(entry);
      if (type != null && known.test(type)) {
        blocked.add(type);
      }
    }
    return Collections.unmodifiableSet(blocked);
  }

  static boolean filtered(NodeRecord node) {
    return node.whitelist() && node.filterCount() > 0;
  }

  static boolean wanted(NodeRecord node, BlockRef block, NodeTagLookup tags) {
    Id type = block.type();
    if (!filtered(node) || air(type)) {
      return false;
    }
    if (NodeFilters.accepts(node, type, tags)) {
      return true;
    }
    Id placement = block.state().placementItem();
    return !placement.equals(type) && NodeFilters.accepts(node, placement, tags);
  }

  static boolean minable(Id type, float hardness, Plan plan) {
    return !air(type) && !plan.blacklist().contains(type) && hardness >= 0F && hardness <= plan.maxHardness();
  }

  static boolean silk(GolemBreakerCore.Config config, int level, NodeRecord node) {
    return GolemancyRules.unlocked(level, config.silkTouchLevel) && on(node, SILK_KEY);
  }

  static boolean fortune(GolemBreakerCore.Config config, int level, NodeRecord node) {
    return GolemancyRules.unlocked(level, config.fortuneLevel) && on(node, FORTUNE_KEY) && !silk(config, level, node);
  }

  static boolean on(NodeRecord node, String key) {
    return ON.equals(node.data().get(key));
  }

  static NodeRecord toggleSilk(NodeRecord node) {
    return toggle(node, SILK_KEY, FORTUNE_KEY);
  }

  static NodeRecord toggleFortune(NodeRecord node) {
    return toggle(node, FORTUNE_KEY, SILK_KEY);
  }

  private static boolean pickaxe(ItemRef item) {
    return item.type().path().endsWith(PICKAXE_SUFFIX);
  }

  private static Id requiredTool(Predicate<ItemRef> harvests, Function<Id, ItemRef> virtual) {
    for (Id type : TIER_LADDER) {
      if (harvests.test(virtual.apply(type))) {
        return type;
      }
    }
    return TIER_LADDER.getLast();
  }

  private static int tier(Id pickaxe) {
    if (STONE_PICKAXE.equals(pickaxe) || COPPER_PICKAXE.equals(pickaxe)) {
      return 1;
    }
    if (IRON_PICKAXE.equals(pickaxe)) {
      return 2;
    }
    if (DIAMOND_PICKAXE.equals(pickaxe)) {
      return 3;
    }
    return NETHERITE_PICKAXE.equals(pickaxe) ? 4 : 0;
  }

  private static Id defaultTool(int level) {
    return Id.fromLegacyName(AnimusBindingRules.atLevel(DEFAULT_TOOLS, level, DEFAULT_TOOLS.getFirst()));
  }

  private static NodeRecord toggle(NodeRecord node, String key, String other) {
    Map<String, String> data = new LinkedHashMap<>(node.data());
    if (on(node, key)) {
      data.remove(key);
    } else {
      data.put(key, ON);
      data.remove(other);
    }
    return node.toBuilder().data(data).build();
  }

  private static boolean air(Id type) {
    return type.isAir();
  }

  record Plan(int level, double maxHardness, Set<Id> blacklist, NodeRecord node, NodeTagLookup tags, ItemRef tool) {
  }
}
