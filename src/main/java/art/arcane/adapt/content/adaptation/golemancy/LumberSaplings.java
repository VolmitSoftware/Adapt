package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemContext;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.plugin.ProtectionEventProbe;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

import java.util.List;
import java.util.UUID;

final class LumberSaplings {
  private static final List<String> FOREIGN_MARKERS = List.of("NPC", "shopitem", "hologram");

  private LumberSaplings() {
  }

  static void collect(GolemContext golem, LumberTrees.Tree tree, int needed) {
    int left = Math.min(needed, golem.room());
    if (left <= 0) {
      return;
    }
    BoundingBox bounds = bounds(tree);
    Location center = new Location(golem.world(), bounds.getCenterX(), bounds.getCenterY(), bounds.getCenterZ());
    for (Item item : PaperCompat.nearbyEntitiesByType(Item.class, center, bounds.getWidthX() / 2D, bounds.getHeight() / 2D,
        bounds.getWidthZ() / 2D)) {
      if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(item)) {
        continue;
      }
      ItemStack stack = item.getItemStack();
      if (stack == null || stack.getType() != tree.sapling() || stack.getAmount() <= 0 || !eligible(item, golem.handle().owner())) {
        continue;
      }
      Location at = item.getLocation();
      if (!bounds.contains(at.getX(), at.getY(), at.getZ()) || !golem.canInteract(at) || !golem.canBreak(at)) {
        continue;
      }
      left -= collect(golem, item, stack, left);
      if (left <= 0) {
        return;
      }
    }
  }

  private static int collect(GolemContext golem, Item item, ItemStack stack, int needed) {
    ItemStack expected = stack.clone();
    int amount = stack.getAmount();
    int taking = Math.min(needed, amount);
    if (!ProtectionEventProbe.attemptEntityItemPickup(golem.golem(), item, amount - taking)) {
      return 0;
    }
    ItemStack current = item.getItemStack();
    if (!item.isValid() || item.isDead() || current == null || !current.isSimilar(expected) || current.getAmount() != amount) {
      return 0;
    }
    ItemStack taken = current.clone();
    taken.setAmount(taking);
    int carried = taking - golem.carry(taken).map(ItemStack::getAmount).orElse(0);
    if (carried >= amount) {
      item.remove();
    } else if (carried > 0) {
      ItemStack rest = current.clone();
      rest.setAmount(amount - carried);
      item.setItemStack(rest);
    }
    return carried;
  }

  private static boolean eligible(Item item, UUID owner) {
    if (!item.isValid() || item.isDead() || item.getPickupDelay() > 0 || !PaperCompat.canPlayerPickup(item)
        || !PaperCompat.canMobPickup(item) || foreign(item.getOwner(), owner) || foreign(item.getThrower(), owner)) {
      return false;
    }
    for (String marker : FOREIGN_MARKERS) {
      if (item.hasMetadata(marker)) {
        return false;
      }
    }
    return true;
  }

  private static boolean foreign(UUID itemOwner, UUID owner) {
    return itemOwner != null && !itemOwner.equals(owner);
  }

  private static BoundingBox bounds(LumberTrees.Tree tree) {
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
    return new BoundingBox(minX - LumberTrees.SWEEP_DEPTH, base.y() - 1, minZ - LumberTrees.SWEEP_DEPTH,
        maxX + LumberTrees.SWEEP_DEPTH + 1, maxY + LumberTrees.SWEEP_DEPTH + 1, maxZ + LumberTrees.SWEEP_DEPTH + 1);
  }
}
