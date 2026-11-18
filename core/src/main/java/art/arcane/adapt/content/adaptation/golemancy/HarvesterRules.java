package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.node.NodeFilters;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeTagLookup;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

final class HarvesterRules {
  static final String HARVEST = "harvest";
  static final String TILL = "till";
  static final String PLANT = "plant";
  static final String TEND = "tend";
  static final int MAX_COLUMN = 32;
  static final long MIN_TENDING_INTERVAL_MILLIS = 1_000L;

  private static final List<BlockFace> SIDES = List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST);
  private static final Id DIRT = Id.minecraft("dirt");
  private static final Id FARMLAND = Id.minecraft("farmland");
  private static final Id MELON_STEM = Id.minecraft("melon_stem");
  private static final Id PUMPKIN_STEM = Id.minecraft("pumpkin_stem");
  private static final Id ATTACHED_MELON_STEM = Id.minecraft("attached_melon_stem");
  private static final Id ATTACHED_PUMPKIN_STEM = Id.minecraft("attached_pumpkin_stem");

  private HarvesterRules() {
  }

  static Plan plan(GolemHarvesterCore.Config config, HarvestCrops crops, int level, NodeRecord node, NodeTagLookup tags, Set<Id> growthBlacklist) {
    return new Plan(crops, GolemancyRules.unlocked(level, config.extendedCropsLevel), GolemancyRules.unlocked(level, config.tendingLevel), GolemancyRules.unlocked(level, config.tillingLevel),
        GolemancyRules.unlocked(level, config.bountifulLevel), node, tags, growthBlacklist);
  }

  static int actionIntervalTicks(List<Integer> byLevel, int level) {
    return Math.max(1, AnimusBindingRules.atLevel(byLevel, Math.max(1, level), 1));
  }

  static int cropsPerAction(List<Integer> byLevel, int level) {
    return Math.max(1, AnimusBindingRules.atLevel(byLevel, Math.max(1, level), 1));
  }

  static long tendingInterval(long millis) {
    return Math.max(MIN_TENDING_INTERVAL_MILLIS, millis);
  }

  static Spot survey(BlockRef block, Plan plan, Predicate<BlockRef> owned) {
    Id type = block.type();
    if (DIRT.equals(type)) {
      BlockRef above = block.relative(BlockFace.UP);
      return plan.tilling() && air(above.type()) ? openFarmland(above, owned, Spot.TILL) : Spot.NONE;
    }
    if (FARMLAND.equals(type)) {
      BlockRef above = block.relative(BlockFace.UP);
      return plan.tilling() && air(above.type()) ? openFarmland(above, owned, Spot.PLANT) : Spot.NONE;
    }
    HarvestCrops.Kind kind = plan.crops().kind(type);
    if (kind == HarvestCrops.Kind.NONE || !wanted(plan, type)) {
      return Spot.NONE;
    }
    return switch (kind) {
      case CROP, BERRY_BUSH -> growth(block.state(), plan.tends(type) && plan.crops().tendable(type));
      case TORCHFLOWER -> torchflower(block, type, plan);
      case CAVE_VINES -> "true".equals(block.state().property("berries")) ? Spot.RIPE : Spot.NONE;
      case GOURD -> plan.extended() ? attached(block, type, owned) : Spot.NONE;
      case COLUMN -> plan.extended() && aboveBase(block, type) ? Spot.RIPE : Spot.NONE;
      case NONE -> Spot.NONE;
    };
  }

  static boolean wanted(Plan plan, Id crop) {
    NodeRecord node = plan.node();
    if (node.filterCount() == 0) {
      return true;
    }
    boolean block = NodeFilters.accepts(node, crop, plan.tags());
    Id seed = HarvestCrops.seed(crop);
    if (seed == null || seed.equals(crop)) {
      return block;
    }
    boolean planted = NodeFilters.accepts(node, seed, plan.tags());
    return node.whitelist() ? block || planted : block && planted;
  }

  static List<BlockRef> column(BlockRef target) {
    Id type = target.type();
    List<BlockRef> column = new ArrayList<>(4);
    BlockRef cursor = target;
    while (cursor.type().equals(type) && column.size() < MAX_COLUMN) {
      column.add(cursor);
      cursor = cursor.relative(BlockFace.UP);
    }
    return column;
  }

  static Id seedToPlant(Plan plan, List<ItemRef> carried, BlockRef spot, Predicate<BlockRef> owned) {
    Id fallback = null;
    for (ItemRef item : carried) {
      Id seed = item.type();
      Id crop = plan.crops().farmlandCrop(seed);
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

  static boolean air(Id type) {
    return type.isAir();
  }

  static boolean bisected(BlockStateRef data) {
    return data.property("half") != null;
  }

  private static Spot growth(BlockStateRef data, boolean tend) {
    String half = data.property("half");
    if (data.age() < 0 || "upper".equals(half) || "top".equals(half)) {
      return Spot.NONE;
    }
    if (data.age() >= data.maxAge()) {
      return Spot.RIPE;
    }
    return tend ? Spot.TEND : Spot.NONE;
  }

  private static Spot torchflower(BlockRef block, Id type, Plan plan) {
    if (HarvestCrops.TORCHFLOWER.equals(type)) {
      return FARMLAND.equals(block.relative(BlockFace.DOWN).type()) ? Spot.RIPE : Spot.NONE;
    }
    return plan.tends(type) ? Spot.TEND : Spot.NONE;
  }

  private static Spot attached(BlockRef fruit, Id type, Predicate<BlockRef> owned) {
    Id stem = HarvestCrops.MELON.equals(type) ? ATTACHED_MELON_STEM : ATTACHED_PUMPKIN_STEM;
    boolean unavailable = false;
    for (BlockFace side : SIDES) {
      BlockRef neighbor = fruit.relative(side);
      if (!readable(neighbor, owned)) {
        unavailable = true;
        continue;
      }
      if (neighbor.type().equals(stem) && side.opposite().name().toLowerCase(Locale.ROOT).equals(neighbor.state().property("facing"))) {
        return Spot.RIPE;
      }
    }
    return unavailable ? Spot.UNAVAILABLE : Spot.NONE;
  }

  private static Spot openFarmland(BlockRef spot, Predicate<BlockRef> owned, Spot work) {
    boolean unavailable = false;
    for (BlockFace side : SIDES) {
      BlockRef neighbor = spot.relative(side);
      if (!readable(neighbor, owned)) {
        unavailable = true;
        continue;
      }
      if (stem(neighbor.type())) {
        return Spot.NONE;
      }
    }
    return unavailable ? Spot.UNAVAILABLE : work;
  }

  private static boolean aboveBase(BlockRef block, Id type) {
    BlockRef base = block.relative(BlockFace.DOWN);
    return base.type().equals(type) && !base.relative(BlockFace.DOWN).type().equals(type);
  }

  static boolean nearStem(BlockRef spot, Predicate<BlockRef> owned) {
    for (BlockFace side : SIDES) {
      BlockRef neighbor = spot.relative(side);
      if (!readable(neighbor, owned) || stem(neighbor.type())) {
        return true;
      }
    }
    return false;
  }

  private static boolean besideCrop(BlockRef spot, Id crop, Predicate<BlockRef> owned) {
    for (BlockFace side : SIDES) {
      BlockRef neighbor = spot.relative(side);
      if (readable(neighbor, owned) && neighbor.type().equals(crop)) {
        return true;
      }
    }
    return false;
  }

  private static boolean readable(BlockRef block, Predicate<BlockRef> owned) {
    return block.world().isChunkLoaded(block.x() >> 4, block.z() >> 4) && owned.test(block);
  }

  private static boolean stem(Id type) {
    return MELON_STEM.equals(type) || PUMPKIN_STEM.equals(type) || ATTACHED_MELON_STEM.equals(type) || ATTACHED_PUMPKIN_STEM.equals(type);
  }

  enum Spot {
    NONE,
    UNAVAILABLE,
    RIPE,
    TEND,
    PLANT,
    TILL
  }

  record Plan(HarvestCrops crops, boolean extended, boolean tending, boolean tilling, boolean bountiful, NodeRecord node, NodeTagLookup tags,
              Set<Id> growthBlacklist) {
    long criteria(int level) {
      return (long) growthBlacklist.hashCode() << 32
          | (long) level << 4 | (extended ? 1L : 0L) | (tending ? 2L : 0L) | (tilling ? 4L : 0L) | (bountiful ? 8L : 0L);
    }

    boolean tends(Id type) {
      return tending && !growthBlacklist.contains(type);
    }

    Plan withPractices(boolean tending, boolean tilling) {
      return new Plan(crops, extended, tending, tilling, bountiful, node, tags, growthBlacklist);
    }
  }
}
