package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemToolEnchants;
import art.arcane.adapt.api.golem.GolemToolNeed;
import art.arcane.adapt.api.golem.GolemToolNote;
import art.arcane.adapt.api.golem.GolemToolPlan;
import art.arcane.adapt.api.golem.node.NodeFilters;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeTagLookup;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.volmlib.util.format.Form;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
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
  private static final String PICKAXE_SUFFIX = "_PICKAXE";
  private static final Material FREE_TIER = Material.IRON_PICKAXE;
  private static final List<Material> PICKAXE_COSTS = List.of(Material.WOODEN_PICKAXE, Material.STONE_PICKAXE, Material.COPPER_PICKAXE,
      Material.GOLDEN_PICKAXE, Material.IRON_PICKAXE, Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE);
  private static final List<Material> TIER_LADDER = List.of(Material.WOODEN_PICKAXE, Material.STONE_PICKAXE, Material.IRON_PICKAXE,
      Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE);
  private static final GolemToolNote SILK_NOTE = new GolemToolNote(GolemancyMessages.TOOLS_NEEDS_SILK, null);
  private static final GolemToolNote FORTUNE_NOTE = new GolemToolNote(GolemancyMessages.TOOLS_NEEDS_FORTUNE, null);

  private BreakerRules() {
  }

  static Plan plan(GolemBreakerCore.Config config, Set<Material> blacklist, int level, NodeRecord node, NodeTagLookup tags, ItemStack tool) {
    return new Plan(level, maxHardness(config.maxHardnessByLevel, level), blacklist, node, tags, tool);
  }

  static int intervalTicks(List<Integer> byLevel, int level) {
    return Math.max(1, AnimusBindingRules.atLevel(byLevel, Math.max(1, level), 1));
  }

  static double maxHardness(List<Double> byLevel, int level) {
    double value = AnimusBindingRules.atLevel(byLevel, Math.max(1, level), 0D);
    return Double.isFinite(value) ? value : 0D;
  }

  static List<Material> tools(List<String> byLevel, Predicate<Material> pickaxe, Consumer<String> rejected) {
    if (byLevel == null) {
      return List.of();
    }
    List<Material> tools = new ArrayList<>(byLevel.size());
    for (int index = 0; index < byLevel.size(); index++) {
      String name = byLevel.get(index);
      Material type = name == null ? null : Material.matchMaterial(name.trim());
      if (type == null || !pickaxe.test(type)) {
        rejected.accept(String.valueOf(name));
        type = defaultTool(index + 1);
      }
      tools.add(type);
    }
    return List.copyOf(tools);
  }

  static Material toolAt(List<Material> tools, int level) {
    int at = Math.max(1, level);
    return AnimusBindingRules.atLevel(tools, at, defaultTool(at));
  }

  static Material freeTool(Material levelTool) {
    return aboveFree(levelTool) ? FREE_TIER : levelTool;
  }

  static boolean aboveFree(Material levelTool) {
    return tier(levelTool) > tier(FREE_TIER);
  }

  static int cost(ItemStack pickaxe) {
    int at = PICKAXE_COSTS.indexOf(pickaxe.getType());
    return at < 0 ? PICKAXE_COSTS.size() : at;
  }

  static GolemToolPlan toolPlan(Predicate<ItemStack> harvests, Function<Material, ItemStack> virtual, Material levelTool, boolean silk, boolean fortune) {
    boolean gated = !harvests.test(virtual.apply(freeTool(levelTool)));
    Material required = gated ? requiredTool(harvests, virtual) : FREE_TIER;
    String tier = required.name();
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
          new GolemToolNote(GolemancyMessages.TOOLS_NEEDS_TIER, toolName(required))));
    }
    return needs.isEmpty() ? GolemToolPlan.NONE : new GolemToolPlan(needs, !gated);
  }

  static ItemStack dropsTool(ItemStack held, boolean silk, boolean fortune, ItemStack free, Function<Material, ItemStack> virtual) {
    if (held == null) {
      return free;
    }
    boolean enchanted = silk ? GolemToolEnchants.silkTouch(held) > 0 : fortune && GolemToolEnchants.fortune(held) > 0;
    return enchanted ? held : virtual.apply(held.getType());
  }

  static String toolName(Material type) {
    return Form.capitalizeWords(type.name().toLowerCase(Locale.ROOT).replace('_', ' '));
  }

  static Set<Material> blacklist(List<String> names, Function<String, Collection<Material>> tags) {
    Set<Material> blocked = EnumSet.noneOf(Material.class);
    if (names == null) {
      return blocked;
    }
    for (String name : names) {
      String entry = name == null ? "" : name.trim();
      if (entry.isEmpty()) {
        continue;
      }
      if (entry.charAt(0) == TAG_PREFIX) {
        Collection<Material> tagged = tags.apply(entry);
        if (tagged != null) {
          blocked.addAll(tagged);
        }
        continue;
      }
      Material type = Material.matchMaterial(entry);
      if (type != null) {
        blocked.add(type);
      }
    }
    return Collections.unmodifiableSet(blocked);
  }

  static boolean filtered(NodeRecord node) {
    return node.whitelist() && node.filterCount() > 0;
  }

  static boolean wanted(NodeRecord node, Block block, NodeTagLookup tags) {
    Material type = block.getType();
    if (!filtered(node) || air(type)) {
      return false;
    }
    if (NodeFilters.accepts(node, type, tags)) {
      return true;
    }
    Material placement = block.getBlockData().getPlacementMaterial();
    return placement != type && NodeFilters.accepts(node, placement, tags);
  }

  static boolean minable(Material type, float hardness, Plan plan) {
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

  private static boolean pickaxe(ItemStack item) {
    return item.getType().name().endsWith(PICKAXE_SUFFIX);
  }

  private static Material requiredTool(Predicate<ItemStack> harvests, Function<Material, ItemStack> virtual) {
    for (Material type : TIER_LADDER) {
      if (harvests.test(virtual.apply(type))) {
        return type;
      }
    }
    return TIER_LADDER.getLast();
  }

  private static int tier(Material pickaxe) {
    return switch (pickaxe) {
      case STONE_PICKAXE, COPPER_PICKAXE -> 1;
      case IRON_PICKAXE -> 2;
      case DIAMOND_PICKAXE -> 3;
      case NETHERITE_PICKAXE -> 4;
      default -> 0;
    };
  }

  private static Material defaultTool(int level) {
    return Material.valueOf(AnimusBindingRules.atLevel(DEFAULT_TOOLS, level, DEFAULT_TOOLS.getFirst()));
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

  private static boolean air(Material type) {
    return type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR;
  }

  record Plan(int level, double maxHardness, Set<Material> blacklist, NodeRecord node, NodeTagLookup tags, ItemStack tool) {
  }
}
