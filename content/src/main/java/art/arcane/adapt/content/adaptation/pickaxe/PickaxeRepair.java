package art.arcane.adapt.content.adaptation.pickaxe;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.PlayerItemDamageEvent;
import art.arcane.adapt.platform.item.ItemRef;

import java.util.Map;
import java.util.UUID;
import java.util.function.ObjIntConsumer;

/**
 * Restores pickaxe durability for a mined block. The repair waits for the wear of that block: it cancels that wear when
 * it arrives, or repairs the held slot a tick later when it never does.
 */
public final class PickaxeRepair {
  private final Map<UUID, PendingRepair> pendingRepairs;
  private final ObjIntConsumer<PlayerRef> onRepaired;

  public PickaxeRepair(Map<UUID, PendingRepair> pendingRepairs, ObjIntConsumer<PlayerRef> onRepaired) {
    this.pendingRepairs = pendingRepairs;
    this.onRepaired = onRepaired;
  }

  public void queue(PlayerRef p, ItemRef hand, int restore) {
    UUID playerId = p.uuid();
    PendingRepair pending = new PendingRepair(p.inventory().heldSlot(), hand.copy(), restore);
    pendingRepairs.put(playerId, pending);
    AdaptApi.scheduler().runEntity(p, () -> applyDeferredRepair(p, playerId, pending), 1);
  }

  public void onItemDamage(PlayerItemDamageEvent e) {
    PlayerRef p = e.player();
    UUID playerId = p.uuid();
    PendingRepair pending = pendingRepairs.get(playerId);
    if (pending == null || !sameRepairTarget(pending.item(), e.item())) {
      return;
    }

    pendingRepairs.remove(playerId, pending);
    int restored = repair(e.item(), pending.restore());
    if (restored <= 0) {
      return;
    }

    e.setCancelled(true);
    onRepaired.accept(p, restored);
  }

  public static int repairedDamage(int damage, int restore) {
    return Math.max(0, damage - Math.max(0, restore));
  }

  public static boolean sameRepairTarget(ItemRef expected, ItemRef actual) {
    if (expected == null || actual == null || !expected.type().equals(actual.type())) {
      return false;
    }

    ItemRef expectedCopy = expected.copy();
    ItemRef actualCopy = actual.copy();
    expectedCopy.setDamage(0);
    actualCopy.setDamage(0);
    return expectedCopy.isSimilar(actualCopy);
  }

  private void applyDeferredRepair(PlayerRef p, UUID playerId, PendingRepair pending) {
    if (!pendingRepairs.remove(playerId, pending)) {
      return;
    }

    ItemRef item = p.inventory().item(pending.slot());
    if (!sameRepairTarget(pending.item(), item)) {
      return;
    }

    int restored = repair(item, pending.restore());
    if (restored > 0) {
      p.inventory().setItem(pending.slot(), item);
      onRepaired.accept(p, restored);
    }
  }

  private int repair(ItemRef item, int requested) {
    if (item == null || item.isEmpty()) {
      return 0;
    }

    int damage = item.damage();
    int remainingDamage = repairedDamage(damage, requested);
    int restored = damage - remainingDamage;
    if (restored <= 0) {
      return 0;
    }

    item.setDamage(remainingDamage);
    return restored;
  }

  public record PendingRepair(int slot, ItemRef item, int restore) {
  }
}
