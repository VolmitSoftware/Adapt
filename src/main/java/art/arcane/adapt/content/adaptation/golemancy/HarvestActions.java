package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemContext;
import art.arcane.adapt.api.golem.GolemTarget;
import art.arcane.adapt.api.golem.GolemTask;
import art.arcane.adapt.api.golem.node.NodeArea;
import art.arcane.adapt.api.golem.node.NodeItemTags;
import art.arcane.adapt.api.golem.node.NodeRecord;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.CaveVinesPlant;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

final class HarvestActions {
  private static final int PICKED_BUSH_AGE = 1;

  private final GolemHarvesterCore core;
  private final Supplier<RandomGenerator> random;

  HarvestActions(GolemHarvesterCore core, Supplier<RandomGenerator> random) {
    this.core = Objects.requireNonNull(core);
    this.random = Objects.requireNonNull(random);
  }

  int level(GolemContext golem) {
    Player owner = golem.owner();
    return owner == null ? 0 : core.ownerLevel(owner.getUniqueId());
  }

  HarvesterRules.Plan plan(int level, NodeRecord node) {
    return HarvesterRules.plan(core.config(), core.crops(), level, node, NodeItemTags.shared());
  }

  int intervalTicks(GolemContext golem) {
    return HarvesterRules.actionIntervalTicks(core.config().actionIntervalTicksByLevel, level(golem));
  }

  Block target(GolemContext golem, GolemTask task) {
    GolemTarget target = task.target();
    return golem.world().getBlockAt(target.blockX(), target.blockY(), target.blockZ());
  }

  Predicate<Block> owned(GolemContext golem) {
    return block -> golem.owns(block.getLocation());
  }

  void reap(GolemContext golem, Block block, HarvesterRules.Plan plan) {
    Material type = block.getType();
    BlockData data = block.getBlockData();
    HarvestCrops.Kind kind = plan.crops().kind(type);
    List<Block> parts = kind == HarvestCrops.Kind.COLUMN ? HarvesterRules.column(block) : List.of(block);
    List<ItemStack> drops = roll(parts);
    if (plan.bountiful() && random.get().nextDouble() < core.config().bountifulChance) {
      drops.addAll(roll(parts));
    }
    drops = new ArrayList<>(golem.withBonusYield(drops));
    switch (kind) {
      case CROP -> replant(block, type, data, drops);
      case TORCHFLOWER -> block.setType(takeSeed(drops, Material.TORCHFLOWER_SEEDS) ? Material.TORCHFLOWER_CROP : Material.AIR);
      case BERRY_BUSH -> {
        Ageable picked = (Ageable) data.clone();
        picked.setAge(Math.min(PICKED_BUSH_AGE, picked.getMaximumAge()));
        block.setBlockData(picked);
      }
      case CAVE_VINES -> {
        CaveVinesPlant picked = (CaveVinesPlant) data.clone();
        picked.setBerries(false);
        block.setBlockData(picked);
      }
      case GOURD -> block.setType(Material.AIR);
      case COLUMN -> {
        for (int index = parts.size() - 1; index >= 0; index--) {
          parts.get(index).setType(Material.AIR, false);
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

  boolean plant(GolemContext golem, Block farmland, HarvesterRules.Plan plan) {
    Block spot = farmland.getRelative(BlockFace.UP);
    Predicate<Block> owned = owned(golem);
    if (!HarvesterRules.air(spot.getType()) || HarvesterRules.nearStem(spot, owned) || !golem.canPlace(spot.getLocation())) {
      return false;
    }
    Material seed = HarvesterRules.seedToPlant(plan, golem.carriedItems(), spot, owned);
    if (seed == null || golem.take(item -> item.getType() == seed, 1) < 1) {
      return false;
    }
    spot.setType(plan.crops().farmlandCrop(seed));
    HarvestFeedback.play(core, center(spot), HarvestFeedback.Kind.PLANT);
    return true;
  }

  boolean canHarvest(GolemContext golem, Block block, HarvesterRules.Plan plan) {
    for (Block part : removed(block, plan.crops().kind(block.getType()))) {
      if (!golem.canBreak(part.getLocation())) {
        return false;
      }
    }
    return true;
  }

  boolean covered(NodeRecord record, GolemTask task) {
    GolemTarget target = task.target();
    return task.node().filter(key -> NodeArea.covers(key, record, target.x(), target.y(), target.z())).isPresent();
  }

  Location center(Block block) {
    return block.getLocation().add(0.5D, 0.5D, 0.5D);
  }

  private static List<Block> removed(Block block, HarvestCrops.Kind kind) {
    if (kind == HarvestCrops.Kind.COLUMN) {
      return HarvesterRules.column(block);
    }
    if (kind == HarvestCrops.Kind.CROP && block.getBlockData() instanceof Bisected) {
      Block top = block.getRelative(BlockFace.UP);
      return top.getType() == block.getType() ? List.of(block, top) : List.of(block);
    }
    return List.of(block);
  }

  private static List<ItemStack> roll(List<Block> parts) {
    List<ItemStack> drops = new ArrayList<>(parts.size() * 2);
    for (Block part : parts) {
      drops.addAll(part.getDrops());
    }
    return drops;
  }

  private static void replant(Block block, Material type, BlockData data, List<ItemStack> drops) {
    if (data instanceof Bisected) {
      Block top = block.getRelative(BlockFace.UP);
      if (top.getType() == type) {
        top.setType(Material.AIR, false);
      }
    }
    Material seed = HarvestCrops.seed(type);
    if (seed == null) {
      seed = data.getPlacementMaterial();
    }
    if (!(data instanceof Ageable crop) || seed == null || !takeSeed(drops, seed)) {
      block.setType(Material.AIR);
      return;
    }
    Ageable young = (Ageable) crop.clone();
    young.setAge(0);
    block.setBlockData(young);
  }

  private static boolean takeSeed(List<ItemStack> drops, Material seed) {
    Iterator<ItemStack> items = drops.iterator();
    while (items.hasNext()) {
      ItemStack item = items.next();
      if (item == null || item.getType() != seed || item.getAmount() <= 0) {
        continue;
      }
      if (item.getAmount() == 1) {
        items.remove();
      } else {
        item.setAmount(item.getAmount() - 1);
      }
      return true;
    }
    return false;
  }

  private void stow(GolemContext golem, Block block, List<ItemStack> drops) {
    Location at = center(block);
    for (ItemStack item : drops) {
      if (item == null || item.getAmount() <= 0) {
        continue;
      }
      golem.carry(item).ifPresent(left -> block.getWorld().dropItemNaturally(at, left));
    }
  }
}
