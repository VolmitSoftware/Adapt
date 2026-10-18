package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.node.NodeFilters;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeTagLookup;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
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

  private BreakerRules() {
  }

  static Plan plan(GolemBreakerCore.Config config, Set<Material> blacklist, int level, NodeRecord node, NodeTagLookup tags, ItemStack tool) {
    return new Plan(level, maxHardness(config.maxHardnessByLevel, level), blacklist, node, tags, tool);
  }

  static boolean unlocked(int level, int required) {
    return level >= 1 && level >= required;
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
    return unlocked(level, config.silkTouchLevel) && on(node, SILK_KEY);
  }

  static boolean fortune(GolemBreakerCore.Config config, int level, NodeRecord node) {
    return unlocked(level, config.fortuneLevel) && on(node, FORTUNE_KEY) && !silk(config, level, node);
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

  static double nonNegative(double value) {
    return Double.isFinite(value) ? Math.max(0D, value) : 0D;
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
