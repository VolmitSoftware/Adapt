package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.content.golem.GolemContext;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Loc;

import java.util.UUID;

final class LumberSaplings {
  private LumberSaplings() {
  }

  static void collect(GolemContext golem, LumberTrees.Tree tree, int needed) {
    int left = Math.min(needed, golem.room());
    if (left <= 0) {
      return;
    }
    Box bounds = bounds(tree);
    Loc center = Loc.of(golem.world(), bounds.center());
    for (ItemEntityRef item : GathererWork.nearby(ItemEntityRef.class, center, bounds.widthX() / 2D, bounds.height() / 2D,
        bounds.widthZ() / 2D)) {
      if (AdaptApi.scheduler().isRegionized() && !AdaptApi.scheduler().owns(item)) {
        continue;
      }
      ItemRef stack = item.item();
      if (stack == null || stack.isEmpty() || !stack.type().equals(tree.sapling()) || stack.amount() <= 0
          || !eligible(item, golem.handle().owner())) {
        continue;
      }
      Loc at = item.location();
      if (!inside(bounds, at) || !golem.canInteract(at) || !golem.canBreak(at)) {
        continue;
      }
      left -= collect(golem, item, stack, left);
      if (left <= 0) {
        return;
      }
    }
  }

  private static int collect(GolemContext golem, ItemEntityRef item, ItemRef stack, int needed) {
    ItemRef expected = stack.copy();
    int amount = stack.amount();
    int taking = Math.min(needed, amount);
    if (!ProtectionProbes.attemptEntityItemPickup(golem.golem(), item, amount - taking)) {
      return 0;
    }
    ItemRef current = item.item();
    if (!item.isValid() || item.isDead() || current == null || current.isEmpty() || !current.isSimilar(expected)
        || current.amount() != amount) {
      return 0;
    }
    int carried = taking - golem.carry(current.copyWithAmount(taking)).map(ItemRef::amount).orElse(0);
    if (carried >= amount) {
      item.remove();
    } else if (carried > 0) {
      item.setItem(current.copyWithAmount(amount - carried));
    }
    return carried;
  }

  private static boolean eligible(ItemEntityRef item, UUID owner) {
    return item.isValid() && !item.isDead() && item.pickupDelay() <= 0 && item.canPlayerPickup() && item.canMobPickup()
        && !foreign(item.owner(), owner) && !foreign(item.thrower(), owner) && !GathererWork.marked(item);
  }

  private static boolean foreign(UUID itemOwner, UUID owner) {
    return itemOwner != null && !itemOwner.equals(owner);
  }

  private static boolean inside(Box box, Loc at) {
    return at.x() >= box.minX() && at.x() < box.maxX() && at.y() >= box.minY() && at.y() < box.maxY()
        && at.z() >= box.minZ() && at.z() < box.maxZ();
  }

  private static Box bounds(LumberTrees.Tree tree) {
    LumberTrees.Part base = tree.base();
    int minX = base.x();
    int minZ = base.z();
    int maxX = minX;
    int maxY = base.y();
    int maxZ = minZ;
    for (LumberTrees.Part part : tree.parts()) {
      minX = Math.min(minX, part.x());
      minZ = Math.min(minZ, part.z());
      maxX = Math.max(maxX, part.x());
      maxY = Math.max(maxY, part.y());
      maxZ = Math.max(maxZ, part.z());
    }
    return new Box(minX - LumberTrees.SWEEP_DEPTH, base.y() - 1, minZ - LumberTrees.SWEEP_DEPTH,
        maxX + LumberTrees.SWEEP_DEPTH + 1, maxY + LumberTrees.SWEEP_DEPTH + 1, maxZ + LumberTrees.SWEEP_DEPTH + 1);
  }
}
