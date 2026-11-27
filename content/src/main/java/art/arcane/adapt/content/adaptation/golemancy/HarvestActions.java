package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.content.golem.GolemContext;
import art.arcane.adapt.content.golem.GolemTarget;
import art.arcane.adapt.content.golem.GolemTask;
import art.arcane.adapt.content.golem.node.NodeItemTags;
import art.arcane.adapt.content.golem.node.NodeRecord;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

final class HarvestActions {
  private static final int PICKED_BUSH_AGE = 1;
  private static final Id AIR = Id.minecraft("air");

  private final GolemHarvesterCore core;
  private final Supplier<RandomGenerator> random;

  HarvestActions(GolemHarvesterCore core, Supplier<RandomGenerator> random) {
    this.core = Objects.requireNonNull(core);
    this.random = Objects.requireNonNull(random);
  }

  HarvesterRules.Plan plan(int level, NodeRecord node) {
    HarvesterRules.Plan plan = HarvesterRules.plan(core.config(), core.crops(), level, node, NodeItemTags.shared(), AdaptApi.settings().growthBlacklist());
    UUID owner = node.owner();
    return plan.withPractices(plan.tending() && core.tends(owner), plan.tilling() && core.tills(owner));
  }

  int intervalTicks(GolemContext golem) {
    return HarvesterRules.actionIntervalTicks(core.config().actionIntervalTicksByLevel, GolemancySupport.ownerLevel(core, golem));
  }

  BlockRef target(GolemContext golem, GolemTask task) {
    GolemTarget target = task.target();
    return golem.world().blockAt(target.blockX(), target.blockY(), target.blockZ());
  }

  Predicate<BlockRef> owned(GolemContext golem) {
    return block -> golem.owns(block.location());
  }


  private static boolean natural(HarvestCrops.Kind kind, List<BlockRef> parts) {
    if (kind != HarvestCrops.Kind.COLUMN && kind != HarvestCrops.Kind.GOURD && kind != HarvestCrops.Kind.TORCHFLOWER) {
      return true;
    }
    boolean natural = true;
    for (BlockRef part : parts) {
      if (XpProvenance.forgetPlacement(part)) {
        natural = false;
      }
    }
    return natural;
  }

  void reap(GolemContext golem, BlockRef block, HarvesterRules.Plan plan) {
    Id type = block.type();
    BlockStateRef data = block.state();
    HarvestCrops.Kind kind = plan.crops().kind(type);
    List<BlockRef> parts = kind == HarvestCrops.Kind.COLUMN ? HarvesterRules.column(block) : List.of(block);
    List<ItemRef> drops = roll(parts);
    if (natural(kind, parts)) {
      if (plan.bountiful() && random.get().nextDouble() < core.config().bountifulChance) {
        drops.addAll(roll(parts));
      }
      drops = new ArrayList<>(golem.withBonusYield(drops));
    }
    switch (kind) {
      case CROP -> replant(block, type, data, drops);
      case TORCHFLOWER -> block.setType(takeSeed(drops, HarvestCrops.TORCHFLOWER_SEEDS) ? HarvestCrops.TORCHFLOWER_CROP : AIR, true);
      case BERRY_BUSH -> block.setState(data.with("age", Integer.toString(Math.min(PICKED_BUSH_AGE, data.maxAge()))), true);
      case CAVE_VINES -> block.setState(data.with("berries", "false"), true);
      case GOURD -> block.setType(AIR, true);
      case COLUMN -> {
        for (int index = parts.size() - 1; index >= 0; index--) {
          parts.get(index).setType(AIR, false);
        }
      }
      case NONE -> {
        return;
      }
    }
    stow(golem, block, drops);
    boolean picked = kind == HarvestCrops.Kind.BERRY_BUSH || kind == HarvestCrops.Kind.CAVE_VINES;
    HarvestFeedback.play(core, center(block), picked ? HarvestFeedback.Kind.PICK : HarvestFeedback.Kind.HARVEST);
  }

  boolean plant(GolemContext golem, BlockRef farmland, HarvesterRules.Plan plan) {
    BlockRef spot = farmland.relative(BlockFace.UP);
    Predicate<BlockRef> owned = owned(golem);
    if (!HarvesterRules.air(spot.type()) || HarvesterRules.nearStem(spot, owned) || !golem.canPlace(spot.location())) {
      return false;
    }
    Id seed = HarvesterRules.seedToPlant(plan, golem.carriedItems(), spot, owned);
    InventoryRef chest = null;
    int slot = -1;
    if (seed == null) {
      chest = GolemancySupport.homeChest(golem).orElse(null);
      List<ItemRef> stock = seedStock(chest);
      seed = HarvesterRules.seedToPlant(plan, stock, spot, owned);
      slot = seedSlot(chest, seed);
    }
    if (seed == null) {
      return false;
    }
    Id chosen = seed;
    ItemRef spent;
    if (chest == null) {
      spent = golem.carriedItems().stream().filter(item -> item.type().equals(chosen)).findFirst().orElseThrow().copyWithAmount(1);
      if (golem.take(item -> item.type().equals(chosen), 1) < 1) {
        return false;
      }
    } else {
      if (slot < 0) {
        return false;
      }
      ItemRef held = chest.item(slot);
      if (held == null || held.isEmpty() || !held.type().equals(seed) || held.amount() <= 0) {
        return false;
      }
      spent = held.copyWithAmount(1);
      if (held.amount() == 1) {
        chest.clear(slot);
      } else {
        chest.setItem(slot, held.copyWithAmount(held.amount() - 1));
      }
    }
    try {
      spot.setType(plan.crops().farmlandCrop(seed), true);
    } catch (RuntimeException failure) {
      refundSeed(golem, chest, slot, spent, spot);
      throw failure;
    }
    if (chest != null) {
      GolemancySupport.visitHomeChest(golem);
    }
    HarvestFeedback.play(core, center(spot), HarvestFeedback.Kind.PLANT);
    return true;
  }

  List<ItemRef> seedStock(GolemContext golem) {
    return seedStock(GolemancySupport.homeChest(golem).orElse(null));
  }

  private List<ItemRef> seedStock(InventoryRef chest) {
    if (chest == null) {
      return List.of();
    }
    List<ItemRef> stock = new ArrayList<>();
    for (ItemRef item : chest.contents()) {
      if (item != null && !item.isEmpty() && item.amount() > 0 && core.crops().farmlandCrop(item.type()) != null) {
        stock.add(item);
      }
    }
    return stock;
  }

  private static int seedSlot(InventoryRef chest, Id seed) {
    if (chest == null || seed == null) {
      return -1;
    }
    List<ItemRef> contents = chest.contents();
    for (int index = 0; index < contents.size(); index++) {
      ItemRef item = contents.get(index);
      if (item != null && !item.isEmpty() && item.type().equals(seed) && item.amount() > 0) {
        return index;
      }
    }
    return -1;
  }

  private static void refundSeed(GolemContext golem, InventoryRef chest, int slot, ItemRef spent, BlockRef spot) {
    if (chest == null) {
      golem.carry(spent).ifPresent(left -> spot.world().dropItemNaturally(spot.location().position(), left));
      return;
    }
    ItemRef held = chest.item(slot);
    if (held == null || held.isEmpty()) {
      chest.setItem(slot, spent);
    } else if (held.isSimilar(spent) && held.amount() < held.maxStackSize()) {
      chest.setItem(slot, held.copyWithAmount(held.amount() + 1));
    } else {
      for (ItemRef left : chest.addItem(spent)) {
        spot.world().dropItemNaturally(spot.location().position(), left);
      }
    }
  }

  boolean canHarvest(GolemContext golem, BlockRef block, HarvesterRules.Plan plan) {
    for (BlockRef part : removed(block, plan.crops().kind(block.type()))) {
      if (!golem.canBreak(part.location())) {
        return false;
      }
    }
    return true;
  }

  Loc center(BlockRef block) {
    return block.center();
  }

  private static List<BlockRef> removed(BlockRef block, HarvestCrops.Kind kind) {
    if (kind == HarvestCrops.Kind.COLUMN) {
      return HarvesterRules.column(block);
    }
    if (kind == HarvestCrops.Kind.CROP && HarvesterRules.bisected(block.state())) {
      BlockRef top = block.relative(BlockFace.UP);
      return top.type().equals(block.type()) ? List.of(block, top) : List.of(block);
    }
    return List.of(block);
  }

  private static List<ItemRef> roll(List<BlockRef> parts) {
    List<ItemRef> drops = new ArrayList<>(parts.size() * 2);
    for (BlockRef part : parts) {
      drops.addAll(part.drops(null, null));
    }
    return drops;
  }

  private static void replant(BlockRef block, Id type, BlockStateRef data, List<ItemRef> drops) {
    if (HarvesterRules.bisected(data)) {
      BlockRef top = block.relative(BlockFace.UP);
      if (top.type().equals(type)) {
        top.setType(AIR, false);
      }
    }
    Id seed = HarvestCrops.seed(type);
    if (seed == null) {
      seed = data.placementItem();
    }
    if (data.age() < 0 || seed == null || !takeSeed(drops, seed)) {
      block.setType(AIR, true);
      return;
    }
    block.setState(data.with("age", "0"), true);
  }

  private static boolean takeSeed(List<ItemRef> drops, Id seed) {
    Iterator<ItemRef> items = drops.iterator();
    while (items.hasNext()) {
      ItemRef item = items.next();
      if (item == null || item.isEmpty() || !item.type().equals(seed) || item.amount() <= 0) {
        continue;
      }
      if (item.amount() == 1) {
        items.remove();
      } else {
        item.setAmount(item.amount() - 1);
      }
      return true;
    }
    return false;
  }

  private void stow(GolemContext golem, BlockRef block, List<ItemRef> drops) {
    Loc at = center(block);
    for (ItemRef item : drops) {
      if (item == null || item.isEmpty() || item.amount() <= 0) {
        continue;
      }
      golem.carry(item).ifPresent(left -> block.world().dropItemNaturally(at.position(), left));
    }
  }
}
