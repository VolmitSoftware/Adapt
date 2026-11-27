package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.content.golem.GolemContext;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

final class LumberActions {
  private static final int PAIRED_SAPLINGS = 4;
  private static final Id AIR = Id.minecraft("air");
  private static final Id CRIMSON_FUNGUS = Id.minecraft("crimson_fungus");
  private static final Id WARPED_FUNGUS = Id.minecraft("warped_fungus");
  static final int SWEEP_SLICE = 64;

  private final GolemLumberCore core;
  private final Predicate<BlockRef> placed;

  LumberActions(GolemLumberCore core, Predicate<BlockRef> placed) {
    this.core = Objects.requireNonNull(core);
    this.placed = Objects.requireNonNull(placed);
  }

  LumberRules.Plan plan(int level) {
    return LumberRules.plan(core.config(), level);
  }

  TreeView view(WorldRef world, Predicate<BlockRef> owns) {
    return new WorldTreeView(world, owns, placed);
  }

  TreeView view(GolemContext golem) {
    return view(golem.world(), block -> golem.owns(block.location()));
  }

  Loc location(GolemContext golem, LumberTrees.Part part) {
    return new Loc(golem.world(), part.x(), part.y(), part.z());
  }

  void fell(GolemContext golem, LumberTrees.Tree tree, List<LumberTrees.Part> parts) {
    List<ItemRef> drops = remove(golem.world(), parts);
    stow(golem, tree, golem.withBonusYield(drops));
    LumberFeedback.play(core, center(golem, parts.getFirst()), tree.nether() ? LumberFeedback.Kind.FELL_STEM : LumberFeedback.Kind.FELL);
  }

  int sweep(GolemContext golem, TreeView view, LumberTrees.Tree tree, List<LumberTrees.Part> leaves, int from) {
    int room = golem.room();
    int end = Math.min(leaves.size(), from + SWEEP_SLICE);
    int index = from;
    WorldRef world = golem.world();
    List<ItemRef> drops = new ArrayList<>(8);
    LumberTrees.Part first = null;
    while (index < end && room > 0) {
      LumberTrees.Part leaf = leaves.get(index);
      index++;
      if (!LumberTrees.sweepable(view, leaf) || !golem.canBreak(location(golem, leaf))) {
        continue;
      }
      BlockRef block = world.blockAt(leaf.x(), leaf.y(), leaf.z());
      for (ItemRef item : block.drops(null, null)) {
        drops.add(item);
        room -= item == null ? 0 : Math.max(0, item.amount());
      }
      block.setType(AIR, true);
      if (first == null) {
        first = leaf;
      }
    }
    if (first != null) {
      stow(golem, tree, golem.withBonusYield(drops));
      LumberFeedback.play(core, center(golem, first), LumberFeedback.Kind.SWEEP);
    }
    return index;
  }

  boolean replant(GolemContext golem, TreeView view, LumberTrees.Tree tree) {
    Id sapling = tree.sapling();
    if (sapling == null) {
      return true;
    }
    boolean pair = paired(golem, view, tree, sapling);
    boolean single = TreeBlocks.growsAlone(sapling) && plantable(golem, view, tree.base(), sapling);
    if (!pair && !single) {
      return true;
    }
    int needed = pair ? PAIRED_SAPLINGS : 1;
    int carried = count(golem.carriedItems(), sapling);
    InventoryRef chest = carried >= needed ? null : GolemancySupport.homeChest(golem).orElse(null);
    int stocked = chest == null ? 0 : count(chest.contents(), sapling);
    if (carried + stocked < needed) {
      LumberSaplings.collect(golem, tree, needed - carried - stocked);
    }
    List<LumberTrees.Part> spots = paired(golem, view, tree, sapling) && take(golem, sapling, PAIRED_SAPLINGS) ? tree.bases() : List.of();
    if (spots.isEmpty() && TreeBlocks.growsAlone(sapling) && plantable(golem, view, tree.base(), sapling) && take(golem, sapling, 1)) {
      spots = List.of(tree.base());
    }
    if (spots.isEmpty()) {
      return false;
    }
    for (LumberTrees.Part spot : spots) {
      golem.world().blockAt(spot.x(), spot.y(), spot.z()).setType(sapling, true);
    }
    LumberFeedback.play(core, center(golem, tree.base()), tree.nether() ? LumberFeedback.Kind.PLANT_FUNGUS : LumberFeedback.Kind.PLANT);
    return true;
  }

  private boolean paired(GolemContext golem, TreeView view, LumberTrees.Tree tree, Id sapling) {
    if (tree.nether() || !tree.square()) {
      return false;
    }
    for (LumberTrees.Part base : tree.bases()) {
      if (!plantable(golem, view, base, sapling)) {
        return false;
      }
    }
    return true;
  }

  private boolean plantable(GolemContext golem, TreeView view, LumberTrees.Part spot, Id sapling) {
    if (!view.owned(spot.x(), spot.y(), spot.z()) || !view.owned(spot.x(), spot.y() - 1, spot.z())
        || !TreeBlocks.air(view.type(spot.x(), spot.y(), spot.z()))) {
      return false;
    }
    Id below = view.type(spot.x(), spot.y() - 1, spot.z());
    boolean footing = CRIMSON_FUNGUS.equals(sapling) || WARPED_FUNGUS.equals(sapling) ? TreeBlocks.nylium(below) : TreeBlocks.soil(below);
    return footing && golem.canPlace(location(golem, spot));
  }

  private boolean take(GolemContext golem, Id sapling, int amount) {
    int carried = count(golem.carriedItems(), sapling);
    InventoryRef chest = carried >= amount ? null : GolemancySupport.homeChest(golem).orElse(null);
    int stocked = chest == null ? 0 : count(chest.contents(), sapling);
    if (carried + stocked < amount) {
      return false;
    }
    int taken = carried > 0 ? golem.take(item -> item.type().equals(sapling), Math.min(amount, carried)) : 0;
    if (taken < amount && chest != null) {
      withdraw(chest, sapling, amount - taken);
      GolemancySupport.visitHomeChest(golem);
    }
    return true;
  }

  private static List<ItemRef> remove(WorldRef world, List<LumberTrees.Part> parts) {
    List<ItemRef> drops = new ArrayList<>(parts.size() * 2);
    for (LumberTrees.Part part : parts) {
      BlockRef block = world.blockAt(part.x(), part.y(), part.z());
      drops.addAll(block.drops(null, null));
      block.setType(AIR, true);
    }
    return drops;
  }

  private static void stow(GolemContext golem, LumberTrees.Tree tree, List<ItemRef> drops) {
    Loc at = null;
    for (ItemRef item : drops) {
      if (item == null || item.isEmpty() || item.amount() <= 0) {
        continue;
      }
      Optional<ItemRef> left = golem.carry(item);
      if (left.isEmpty()) {
        continue;
      }
      if (at == null) {
        at = center(golem, tree.base());
      }
      golem.world().dropItemNaturally(at.position(), left.get());
    }
  }

  private static int count(List<ItemRef> items, Id type) {
    int total = 0;
    for (ItemRef item : items) {
      if (item != null && !item.isEmpty() && item.type().equals(type)) {
        total += Math.max(0, item.amount());
      }
    }
    return total;
  }

  private static void withdraw(InventoryRef chest, Id type, int amount) {
    List<ItemRef> contents = chest.contents();
    int left = amount;
    for (int slot = 0; slot < contents.size() && left > 0; slot++) {
      ItemRef item = contents.get(slot);
      if (item == null || item.isEmpty() || !item.type().equals(type) || item.amount() <= 0) {
        continue;
      }
      int used = Math.min(left, item.amount());
      left -= used;
      if (used == item.amount()) {
        chest.clear(slot);
        continue;
      }
      chest.setItem(slot, item.copyWithAmount(item.amount() - used));
    }
  }

  private static Loc center(GolemContext golem, LumberTrees.Part part) {
    return new Loc(golem.world(), part.x() + 0.5D, part.y() + 0.5D, part.z() + 0.5D);
  }
}
