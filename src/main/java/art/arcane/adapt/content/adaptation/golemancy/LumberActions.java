package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemContext;
import art.arcane.adapt.api.golem.GolemState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

final class LumberActions {
  private static final int PAIRED_SAPLINGS = 4;

  private final GolemLumberCore core;
  private final Predicate<Block> placed;

  LumberActions(GolemLumberCore core, Predicate<Block> placed) {
    this.core = Objects.requireNonNull(core);
    this.placed = Objects.requireNonNull(placed);
  }

  int level(GolemContext golem) {
    Player owner = golem.owner();
    return owner == null ? 0 : core.ownerLevel(owner.getUniqueId());
  }

  LumberRules.Plan plan(int level) {
    return LumberRules.plan(core.config(), level);
  }

  TreeView view(World world, Predicate<Block> owns) {
    return new WorldTreeView(world, owns, placed);
  }

  TreeView view(GolemContext golem) {
    return view(golem.world(), block -> golem.owns(block.getLocation()));
  }

  Location location(GolemContext golem, LumberTrees.Part part) {
    return new Location(golem.world(), part.x(), part.y(), part.z());
  }

  void fell(GolemContext golem, LumberTrees.Tree tree, List<LumberTrees.Part> parts) {
    List<ItemStack> drops = remove(golem.world(), parts);
    stow(golem, tree, golem.withBonusYield(drops));
    LumberFeedback.play(core, center(golem, parts.getFirst()), tree.nether() ? LumberFeedback.Kind.FELL_STEM : LumberFeedback.Kind.FELL);
  }

  void sweep(GolemContext golem, TreeView view, LumberTrees.Tree tree, List<LumberTrees.Part> leaves) {
    List<LumberTrees.Part> loose = new ArrayList<>(leaves.size());
    for (LumberTrees.Part leaf : leaves) {
      if (LumberTrees.sweepable(view, leaf) && golem.canBreak(location(golem, leaf))) {
        loose.add(leaf);
      }
    }
    if (loose.isEmpty()) {
      return;
    }
    stow(golem, tree, golem.withBonusYield(remove(golem.world(), loose)));
    LumberFeedback.play(core, center(golem, loose.getFirst()), LumberFeedback.Kind.SWEEP);
  }

  void replant(GolemContext golem, TreeView view, LumberTrees.Tree tree) {
    Material sapling = TreeBlocks.sapling(tree.species());
    if (sapling == null) {
      return;
    }
    List<LumberTrees.Part> spots = paired(golem, view, tree, sapling) && take(golem, sapling, PAIRED_SAPLINGS) ? tree.bases() : List.of();
    if (spots.isEmpty() && TreeBlocks.growsAlone(tree.species()) && plantable(golem, view, tree.base(), sapling) && take(golem, sapling, 1)) {
      spots = List.of(tree.base());
    }
    if (spots.isEmpty()) {
      return;
    }
    for (LumberTrees.Part spot : spots) {
      golem.world().getBlockAt(spot.x(), spot.y(), spot.z()).setType(sapling);
    }
    LumberFeedback.play(core, center(golem, tree.base()), tree.nether() ? LumberFeedback.Kind.PLANT_FUNGUS : LumberFeedback.Kind.PLANT);
  }

  private boolean paired(GolemContext golem, TreeView view, LumberTrees.Tree tree, Material sapling) {
    if (tree.nether() || tree.bases().size() != PAIRED_SAPLINGS) {
      return false;
    }
    for (LumberTrees.Part base : tree.bases()) {
      if (!plantable(golem, view, base, sapling)) {
        return false;
      }
    }
    return true;
  }

  private boolean plantable(GolemContext golem, TreeView view, LumberTrees.Part spot, Material sapling) {
    if (!view.owned(spot.x(), spot.y(), spot.z()) || !view.owned(spot.x(), spot.y() - 1, spot.z())
        || !TreeBlocks.air(view.type(spot.x(), spot.y(), spot.z()))) {
      return false;
    }
    Material below = view.type(spot.x(), spot.y() - 1, spot.z());
    boolean footing = sapling == Material.CRIMSON_FUNGUS || sapling == Material.WARPED_FUNGUS ? TreeBlocks.nylium(below) : TreeBlocks.soil(below);
    return footing && golem.canPlace(location(golem, spot));
  }

  private boolean take(GolemContext golem, Material sapling, int amount) {
    int carried = count(golem.carriedItems().toArray(ItemStack[]::new), sapling);
    Inventory chest = carried >= amount ? null : homeChest(golem).orElse(null);
    int stocked = chest == null ? 0 : count(chest.getStorageContents(), sapling);
    if (carried + stocked < amount) {
      return false;
    }
    int taken = carried > 0 ? golem.take(item -> item.getType() == sapling, Math.min(amount, carried)) : 0;
    if (taken < amount && chest != null) {
      withdraw(chest, sapling, amount - taken);
    }
    return true;
  }

  private static List<ItemStack> remove(World world, List<LumberTrees.Part> parts) {
    List<ItemStack> drops = new ArrayList<>(parts.size() * 2);
    for (LumberTrees.Part part : parts) {
      Block block = world.getBlockAt(part.x(), part.y(), part.z());
      drops.addAll(block.getDrops());
      block.setType(Material.AIR);
    }
    return drops;
  }

  private static void stow(GolemContext golem, LumberTrees.Tree tree, List<ItemStack> drops) {
    Location at = null;
    for (ItemStack item : drops) {
      if (item == null || item.getAmount() <= 0) {
        continue;
      }
      Optional<ItemStack> left = golem.carry(item);
      if (left.isEmpty()) {
        continue;
      }
      if (at == null) {
        at = center(golem, tree.base());
      }
      golem.world().dropItemNaturally(at, left.get());
    }
  }

  private static Optional<Inventory> homeChest(GolemContext golem) {
    GolemState.Anchor chest = golem.handle().state().homeChest();
    World world = golem.world();
    if (chest == null || !chest.world().equals(world.getUID()) || !world.isChunkLoaded(chest.x() >> 4, chest.z() >> 4)) {
      return Optional.empty();
    }
    return golem.container(world.getBlockAt(chest.x(), chest.y(), chest.z()));
  }

  private static int count(ItemStack[] items, Material type) {
    int total = 0;
    for (ItemStack item : items) {
      if (item != null && item.getType() == type) {
        total += Math.max(0, item.getAmount());
      }
    }
    return total;
  }

  private static void withdraw(Inventory chest, Material type, int amount) {
    ItemStack[] contents = chest.getStorageContents();
    int left = amount;
    for (int slot = 0; slot < contents.length && left > 0; slot++) {
      ItemStack item = contents[slot];
      if (item == null || item.getType() != type || item.getAmount() <= 0) {
        continue;
      }
      int used = Math.min(left, item.getAmount());
      left -= used;
      if (used == item.getAmount()) {
        chest.setItem(slot, null);
        continue;
      }
      ItemStack rest = item.clone();
      rest.setAmount(item.getAmount() - used);
      chest.setItem(slot, rest);
    }
  }

  private static Location center(GolemContext golem, LumberTrees.Part part) {
    return new Location(golem.world(), part.x() + 0.5D, part.y() + 0.5D, part.z() + 0.5D);
  }
}
