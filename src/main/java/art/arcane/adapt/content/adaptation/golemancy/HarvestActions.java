package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.golem.GolemContext;
import art.arcane.adapt.api.golem.GolemTarget;
import art.arcane.adapt.api.golem.GolemTask;
import art.arcane.adapt.api.golem.node.NodeItemTags;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.xp.XpProvenance;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.CaveVinesPlant;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;

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

  private final GolemHarvesterCore core;
  private final Supplier<RandomGenerator> random;

  HarvestActions(GolemHarvesterCore core, Supplier<RandomGenerator> random) {
    this.core = Objects.requireNonNull(core);
    this.random = Objects.requireNonNull(random);
  }

  HarvesterRules.Plan plan(int level, NodeRecord node) {
    HarvesterRules.Plan plan = HarvesterRules.plan(core.config(), core.crops(), level, node, NodeItemTags.shared(),
        AdaptConfig.get().getBlacklistedGrowableMaterials());
    UUID owner = node.owner();
    return plan.withPractices(plan.tending() && core.tends(owner), plan.tilling() && core.tills(owner));
  }

  int intervalTicks(GolemContext golem) {
    return HarvesterRules.actionIntervalTicks(core.config().actionIntervalTicksByLevel, GolemancySupport.ownerLevel(core, golem));
  }

  Block target(GolemContext golem, GolemTask task) {
    GolemTarget target = task.target();
    return golem.world().getBlockAt(target.blockX(), target.blockY(), target.blockZ());
  }

  Predicate<Block> owned(GolemContext golem) {
    return block -> golem.owns(block.getLocation());
  }

  private static boolean natural(HarvestCrops.Kind kind, List<Block> parts) {
    if (kind != HarvestCrops.Kind.COLUMN && kind != HarvestCrops.Kind.GOURD && kind != HarvestCrops.Kind.TORCHFLOWER) {
      return true;
    }
    boolean natural = true;
    for (Block part : parts) {
      if (XpProvenance.forgetPlacement(part)) {
        natural = false;
      }
    }
    return natural;
  }

  void reap(GolemContext golem, Block block, HarvesterRules.Plan plan) {
    Material type = block.getType();
    BlockData data = block.getBlockData();
    HarvestCrops.Kind kind = plan.crops().kind(type);
    List<Block> parts = kind == HarvestCrops.Kind.COLUMN ? HarvesterRules.column(block) : List.of(block);
    List<ItemStack> drops = roll(parts);
    if (natural(kind, parts)) {
      if (plan.bountiful() && random.get().nextDouble() < core.config().bountifulChance) {
        drops.addAll(roll(parts));
      }
      drops = new ArrayList<>(golem.withBonusYield(drops));
    }
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
    Inventory chest = null;
    int slot = -1;
    if (seed == null) {
      chest = GolemancySupport.homeChest(golem).orElse(null);
      List<ItemStack> stock = seedStock(chest);
      seed = HarvesterRules.seedToPlant(plan, stock, spot, owned);
      slot = seedSlot(chest, seed);
    }
    if (seed == null) {
      return false;
    }
    Material chosen = seed;
    ItemStack spent;
    if (chest == null) {
      spent = golem.carriedItems().stream().filter(item -> item.getType() == chosen).findFirst().orElseThrow().clone();
      spent.setAmount(1);
      if (golem.take(item -> item.getType() == chosen, 1) < 1) {
        return false;
      }
    } else {
      if (slot < 0) {
        return false;
      }
      ItemStack held = chest.getItem(slot);
      if (held == null || held.getType() != seed || held.getAmount() <= 0) {
        return false;
      }
      spent = held.clone();
      spent.setAmount(1);
      ItemStack rest = held.clone();
      rest.setAmount(held.getAmount() - 1);
      chest.setItem(slot, rest.getAmount() == 0 ? null : rest);
    }
    try {
      spot.setType(plan.crops().farmlandCrop(seed));
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

  List<ItemStack> seedStock(GolemContext golem) {
    return seedStock(GolemancySupport.homeChest(golem).orElse(null));
  }

  private List<ItemStack> seedStock(Inventory chest) {
    if (chest == null) {
      return List.of();
    }
    List<ItemStack> stock = new ArrayList<>();
    for (ItemStack item : chest.getStorageContents()) {
      if (item != null && item.getAmount() > 0 && core.crops().farmlandCrop(item.getType()) != null) {
        stock.add(item);
      }
    }
    return stock;
  }

  private static int seedSlot(Inventory chest, Material seed) {
    if (chest == null || seed == null) {
      return -1;
    }
    ItemStack[] contents = chest.getStorageContents();
    for (int index = 0; index < contents.length; index++) {
      ItemStack item = contents[index];
      if (item != null && item.getType() == seed && item.getAmount() > 0) {
        return index;
      }
    }
    return -1;
  }

  private static void refundSeed(GolemContext golem, Inventory chest, int slot, ItemStack spent, Block spot) {
    if (chest == null) {
      golem.carry(spent).ifPresent(left -> spot.getWorld().dropItemNaturally(spot.getLocation(), left));
      return;
    }
    ItemStack held = chest.getItem(slot);
    if (held == null) {
      chest.setItem(slot, spent);
    } else if (held.isSimilar(spent) && held.getAmount() < held.getMaxStackSize()) {
      ItemStack restored = held.clone();
      restored.setAmount(held.getAmount() + 1);
      chest.setItem(slot, restored);
    } else {
      for (ItemStack left : chest.addItem(spent).values()) {
        spot.getWorld().dropItemNaturally(spot.getLocation(), left);
      }
    }
  }

  boolean canHarvest(GolemContext golem, Block block, HarvesterRules.Plan plan) {
    for (Block part : removed(block, plan.crops().kind(block.getType()))) {
      if (!golem.canBreak(part.getLocation())) {
        return false;
      }
    }
    return true;
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
