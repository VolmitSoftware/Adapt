package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.api.golem.node.NodeFilters;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeTagLookup;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.CaveVinesPlant;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

final class HarvesterRules {
  static final String HARVEST = "harvest";
  static final String TILL = "till";
  static final String PLANT = "plant";
  static final String TEND = "tend";
  static final int MAX_COLUMN = 32;
  static final long MIN_TENDING_INTERVAL_MILLIS = 1_000L;

  private static final List<BlockFace> SIDES = List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST);

  private HarvesterRules() {
  }

  static Plan plan(GolemHarvesterCore.Config config, HarvestCrops crops, int level, NodeRecord node, NodeTagLookup tags) {
    return new Plan(crops, unlocked(level, config.extendedCropsLevel), unlocked(level, config.tendingLevel), unlocked(level, config.tillingLevel),
        unlocked(level, config.bountifulLevel), node, tags);
  }

  static boolean unlocked(int level, int required) {
    return level >= 1 && level >= required;
  }

  static int actionIntervalTicks(List<Integer> byLevel, int level) {
    return Math.max(1, AnimusBindingRules.atLevel(byLevel, Math.max(1, level), 1));
  }

  static int cropsPerAction(List<Integer> byLevel, int level) {
    return Math.max(1, AnimusBindingRules.atLevel(byLevel, Math.max(1, level), 1));
  }

  static double fraction(double value) {
    return Double.isFinite(value) ? Math.clamp(value, 0D, 1D) : 0D;
  }

  static double nonNegative(double value) {
    return Double.isFinite(value) ? Math.max(0D, value) : 0D;
  }

  static long tendingInterval(long millis) {
    return Math.max(MIN_TENDING_INTERVAL_MILLIS, millis);
  }

  static Spot survey(Block block, Plan plan, Predicate<Block> owned) {
    Material type = block.getType();
    if (type == Material.DIRT) {
      Block above = block.getRelative(BlockFace.UP);
      return plan.tilling() && air(above.getType()) && !nearStem(above, owned) ? Spot.TILL : Spot.NONE;
    }
    if (type == Material.FARMLAND) {
      Block above = block.getRelative(BlockFace.UP);
      return plan.tilling() && air(above.getType()) && !nearStem(above, owned) ? Spot.PLANT : Spot.NONE;
    }
    HarvestCrops.Kind kind = plan.crops().kind(type);
    if (kind == HarvestCrops.Kind.NONE || !wanted(plan, type)) {
      return Spot.NONE;
    }
    return switch (kind) {
      case CROP, BERRY_BUSH -> growth(block.getBlockData(), plan.tending() && plan.crops().tendable(type));
      case TORCHFLOWER -> torchflower(block, type, plan);
      case CAVE_VINES -> block.getBlockData() instanceof CaveVinesPlant vines && PaperCompat.hasBerries(vines) ? Spot.RIPE : Spot.NONE;
      case GOURD -> plan.extended() && attached(block, type, owned) ? Spot.RIPE : Spot.NONE;
      case COLUMN -> plan.extended() && aboveBase(block, type) ? Spot.RIPE : Spot.NONE;
      case NONE -> Spot.NONE;
    };
  }

  static boolean wanted(Plan plan, Material crop) {
    NodeRecord node = plan.node();
    if (node.filterCount() == 0) {
      return true;
    }
    boolean block = NodeFilters.accepts(node, crop, plan.tags());
    Material seed = HarvestCrops.seed(crop);
    if (seed == null || seed == crop) {
      return block;
    }
    boolean planted = NodeFilters.accepts(node, seed, plan.tags());
    return node.whitelist() ? block || planted : block && planted;
  }

  static List<Block> column(Block target) {
    Material type = target.getType();
    List<Block> column = new ArrayList<>(4);
    Block cursor = target;
    while (cursor.getType() == type && column.size() < MAX_COLUMN) {
      column.add(cursor);
      cursor = cursor.getRelative(BlockFace.UP);
    }
    return column;
  }

  static Material seedToPlant(Plan plan, List<ItemStack> carried, Block spot, Predicate<Block> owned) {
    Material fallback = null;
    for (ItemStack item : carried) {
      Material seed = item.getType();
      Material crop = plan.crops().farmlandCrop(seed);
      if (crop == null || !wanted(plan, crop)) {
        continue;
      }
      if (besideCrop(spot, crop, owned)) {
        return seed;
      }
      if (fallback == null) {
        fallback = seed;
      }
    }
    return fallback;
  }

  static boolean air(Material type) {
    return type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR;
  }

  private static Spot growth(BlockData data, boolean tend) {
    if (!(data instanceof Ageable crop) || (data instanceof Bisected half && half.getHalf() == Bisected.Half.TOP)) {
      return Spot.NONE;
    }
    if (crop.getAge() >= crop.getMaximumAge()) {
      return Spot.RIPE;
    }
    return tend ? Spot.TEND : Spot.NONE;
  }

  private static Spot torchflower(Block block, Material type, Plan plan) {
    if (type == Material.TORCHFLOWER) {
      return block.getRelative(BlockFace.DOWN).getType() == Material.FARMLAND ? Spot.RIPE : Spot.NONE;
    }
    return plan.tending() ? Spot.TEND : Spot.NONE;
  }

  private static boolean attached(Block fruit, Material type, Predicate<Block> owned) {
    Material stem = type == Material.MELON ? Material.ATTACHED_MELON_STEM : Material.ATTACHED_PUMPKIN_STEM;
    for (BlockFace side : SIDES) {
      Block neighbor = fruit.getRelative(side);
      if (owned.test(neighbor) && neighbor.getType() == stem && neighbor.getBlockData() instanceof Directional facing
          && facing.getFacing() == side.getOppositeFace()) {
        return true;
      }
    }
    return false;
  }

  private static boolean aboveBase(Block block, Material type) {
    Block base = block.getRelative(BlockFace.DOWN);
    return base.getType() == type && base.getRelative(BlockFace.DOWN).getType() != type;
  }

  static boolean nearStem(Block spot, Predicate<Block> owned) {
    for (BlockFace side : SIDES) {
      Block neighbor = spot.getRelative(side);
      if (!owned.test(neighbor) || stem(neighbor.getType())) {
        return true;
      }
    }
    return false;
  }

  private static boolean besideCrop(Block spot, Material crop, Predicate<Block> owned) {
    for (BlockFace side : SIDES) {
      Block neighbor = spot.getRelative(side);
      if (owned.test(neighbor) && neighbor.getType() == crop) {
        return true;
      }
    }
    return false;
  }

  private static boolean stem(Material type) {
    return type == Material.MELON_STEM || type == Material.PUMPKIN_STEM || type == Material.ATTACHED_MELON_STEM
        || type == Material.ATTACHED_PUMPKIN_STEM;
  }

  enum Spot {
    NONE,
    RIPE,
    TEND,
    PLANT,
    TILL
  }

  record Plan(HarvestCrops crops, boolean extended, boolean tending, boolean tilling, boolean bountiful, NodeRecord node, NodeTagLookup tags) {
  }
}
