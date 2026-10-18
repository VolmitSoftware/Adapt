package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.node.NodeFilters;
import art.arcane.adapt.api.golem.node.NodeRecord;
import org.bukkit.Material;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

final class SorterRules {
  static final String SORT = "sort";
  static final String RESTOCK = "restock";
  static final String FETCH = "fetch";
  static final String DROP = "drop";
  static final String STOCK = "stock";
  static final String HAND = "hand";
  static final String RETURN = "return";
  static final String MATCH = "match";
  static final String RADIUS = "radius";
  static final String OVERFLOW = "overflow";
  static final String RESTOCK_TARGET = "restock";
  static final int MIN_RADIUS = 2;
  static final int RADIUS_STEP = 2;
  static final List<Integer> RESTOCK_STEPS = List.of(0, 1, 4, 8, 16, 32, 64, 128, 256, 512);
  static final List<String> DEFAULT_GROUPS = List.of(
      "logs=#minecraft:logs",
      "planks=#minecraft:planks",
      "ores=#minecraft:coal_ores,#minecraft:iron_ores,#minecraft:copper_ores,#minecraft:gold_ores,#minecraft:redstone_ores,"
          + "#minecraft:lapis_ores,#minecraft:diamond_ores,#minecraft:emerald_ores,NETHER_QUARTZ_ORE,NETHER_GOLD_ORE,ANCIENT_DEBRIS",
      "stone=STONE,COBBLESTONE,DEEPSLATE,COBBLED_DEEPSLATE,GRANITE,DIORITE,ANDESITE,TUFF,CALCITE,BLACKSTONE,BASALT,SMOOTH_BASALT,"
          + "NETHERRACK,END_STONE",
      "wool=#minecraft:wool",
      "food=APPLE,BREAD,CARROT,POTATO,BAKED_POTATO,BEETROOT,MELON_SLICE,SWEET_BERRIES,GLOW_BERRIES,COOKIE,PUMPKIN_PIE,GOLDEN_CARROT,"
          + "BEEF,COOKED_BEEF,PORKCHOP,COOKED_PORKCHOP,CHICKEN,COOKED_CHICKEN,MUTTON,COOKED_MUTTON,RABBIT,COOKED_RABBIT,COD,COOKED_COD,"
          + "SALMON,COOKED_SALMON,DRIED_KELP",
      "redstone=REDSTONE,REDSTONE_TORCH,REPEATER,COMPARATOR,LEVER,PISTON,STICKY_PISTON,OBSERVER,HOPPER,DROPPER,DISPENSER,"
          + "REDSTONE_LAMP,TARGET,DAYLIGHT_DETECTOR,TRIPWIRE_HOOK,STONE_PRESSURE_PLATE,STONE_BUTTON",
      "saplings=#minecraft:saplings",
      "seeds=WHEAT_SEEDS,BEETROOT_SEEDS,MELON_SEEDS,PUMPKIN_SEEDS,TORCHFLOWER_SEEDS,PITCHER_POD");
  private static final char GROUP_SEPARATOR = '=';
  private static final String MEMBER_SEPARATOR = ",";
  private static final String TAG_PREFIX = "#";
  private static final String SHULKER_BOX = "SHULKER_BOX";

  private SorterRules() {
  }

  static int destinationRadius(List<Integer> byLevel, int level) {
    return Math.max(1, at(byLevel, level));
  }

  static int stacksPerTrip(List<Integer> byLevel, int level) {
    return Math.max(1, at(byLevel, level));
  }

  static boolean unlocked(int level, int unlockLevel) {
    return level >= Math.max(1, unlockLevel);
  }

  static Match highestMatch(GolemSorterCore.Config config, int level) {
    if (unlocked(level, config.preciseLevel)) {
      return Match.PRECISE;
    }
    return unlocked(level, config.groupsLevel) ? Match.GROUPS : Match.EXACT;
  }

  static Match match(NodeRecord input, GolemSorterCore.Config config, int level) {
    Match highest = highestMatch(config, level);
    Match stored = Match.fromId(input.data().get(MATCH)).orElse(highest);
    return stored.ordinal() > highest.ordinal() ? highest : stored;
  }

  static NodeRecord cycleMatch(NodeRecord input, GolemSorterCore.Config config, int level, boolean forward) {
    int unlocked = highestMatch(config, level).ordinal() + 1;
    int current = match(input, config, level).ordinal();
    int next = Math.floorMod(current + (forward ? 1 : -1), unlocked);
    return withData(input, MATCH, Match.values()[next].id());
  }

  static int radius(NodeRecord input, int levelRadius) {
    int cap = Math.max(MIN_RADIUS, levelRadius);
    return Math.max(MIN_RADIUS, Math.min(cap, parse(input.data().get(RADIUS), cap)));
  }

  static NodeRecord resize(NodeRecord input, int levelRadius, int steps) {
    int cap = Math.max(MIN_RADIUS, levelRadius);
    int next = Math.max(MIN_RADIUS, Math.min(cap, radius(input, levelRadius) + steps * RADIUS_STEP));
    return withData(input, RADIUS, Integer.toString(next));
  }

  static boolean overflow(NodeRecord filter) {
    return Boolean.parseBoolean(filter.data().get(OVERFLOW));
  }

  static NodeRecord toggleOverflow(NodeRecord filter) {
    return overflow(filter) ? withoutData(filter, OVERFLOW) : withData(filter, OVERFLOW, Boolean.TRUE.toString());
  }

  static int restockTarget(NodeRecord filter) {
    return Math.max(0, parse(filter.data().get(RESTOCK_TARGET), 0));
  }

  static NodeRecord stepRestock(NodeRecord filter, boolean up) {
    int current = restockTarget(filter);
    int next = current;
    if (up) {
      for (int step : RESTOCK_STEPS) {
        if (step > current) {
          next = step;
          break;
        }
      }
    } else {
      next = 0;
      for (int step : RESTOCK_STEPS) {
        if (step < current) {
          next = step;
        }
      }
    }
    return next <= 0 ? withoutData(filter, RESTOCK_TARGET) : withData(filter, RESTOCK_TARGET, Integer.toString(next));
  }

  static boolean shulker(Material type) {
    return type.name().endsWith(SHULKER_BOX);
  }

  static boolean precise(Material type) {
    return switch (type) {
      case ENCHANTED_BOOK, POTION, SPLASH_POTION, LINGERING_POTION, TIPPED_ARROW -> true;
      default -> false;
    };
  }

  static Map<Material, String> groups(List<String> entries, Function<String, Collection<Material>> tags, Consumer<String> rejected) {
    Map<Material, String> groups = new HashMap<>();
    for (String entry : entries) {
      int separator = entry.indexOf(GROUP_SEPARATOR);
      String name = separator <= 0 ? "" : entry.substring(0, separator).trim();
      String members = separator < 0 ? "" : entry.substring(separator + 1).trim();
      if (name.isEmpty() || members.isEmpty()) {
        rejected.accept(entry);
        continue;
      }
      for (String raw : members.split(MEMBER_SEPARATOR)) {
        String member = raw.trim();
        if (!member.isEmpty()) {
          group(groups, name, member, tags, rejected);
        }
      }
    }
    return groups;
  }

  static double nonNegative(double value) {
    return Double.isFinite(value) ? Math.max(0D, value) : 0D;
  }

  static int clamp(int value, int minimum, int maximum) {
    return Math.max(minimum, Math.min(maximum, value));
  }

  private static void group(Map<Material, String> groups, String name, String member, Function<String, Collection<Material>> tags,
                            Consumer<String> rejected) {
    if (member.startsWith(TAG_PREFIX)) {
      Optional<String> tag = NodeFilters.normalizeTag(member);
      Collection<Material> tagged = tag.map(tags).orElse(List.of());
      if (tagged.isEmpty()) {
        rejected.accept(tag.orElse(member));
        return;
      }
      for (Material type : tagged) {
        groups.putIfAbsent(type, name);
      }
      return;
    }
    Material type = Material.matchMaterial(member);
    if (type == null) {
      rejected.accept(member);
      return;
    }
    groups.putIfAbsent(type, name);
  }

  private static NodeRecord withData(NodeRecord record, String key, String value) {
    Map<String, String> data = new LinkedHashMap<>(record.data());
    data.put(key, value);
    return record.toBuilder().data(data).build();
  }

  private static NodeRecord withoutData(NodeRecord record, String key) {
    if (!record.data().containsKey(key)) {
      return record;
    }
    Map<String, String> data = new LinkedHashMap<>(record.data());
    data.remove(key);
    return record.toBuilder().data(data).build();
  }

  private static int at(List<Integer> byLevel, int level) {
    if (byLevel == null || byLevel.isEmpty()) {
      return 1;
    }
    Integer value = byLevel.get(Math.max(0, Math.min(byLevel.size() - 1, level - 1)));
    return value == null ? 1 : value;
  }

  private static int parse(String value, int fallback) {
    if (value == null) {
      return fallback;
    }
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException malformed) {
      return fallback;
    }
  }

  enum Match {
    EXACT("exact"),
    GROUPS("groups"),
    PRECISE("precise");

    private final String id;

    Match(String id) {
      this.id = id;
    }

    static Optional<Match> fromId(String id) {
      if (id == null) {
        return Optional.empty();
      }
      String normalized = id.trim().toLowerCase(Locale.ROOT);
      for (Match match : values()) {
        if (match.id.equals(normalized)) {
          return Optional.of(match);
        }
      }
      return Optional.empty();
    }

    String id() {
      return id;
    }
  }
}
