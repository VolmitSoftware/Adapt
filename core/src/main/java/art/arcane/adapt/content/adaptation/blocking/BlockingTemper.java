package art.arcane.adapt.content.adaptation.blocking;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.item.ItemRef;

import java.util.List;

/** Durability repair for a held shield and worn armor, shared by the adaptations that temper gear on blocked hits. */
public final class BlockingTemper {
  private static final List<EquipmentSlot> ARMOR_SLOTS = List.of(EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD);

  private BlockingTemper() {
  }

  /** Restores up to {@code amount} durability on the held shield, off hand first; returns what was restored. */
  public static int repairShield(Adaptation<?> owner, PlayerInventoryRef inventory, int amount) {
    ItemRef off = inventory.offHand();
    if (owner.isItem(off) && BlockingShields.SHIELD.equals(off.type())) {
      int restored = restoreDurability(off, amount);
      if (restored > 0) {
        inventory.setOffHand(off);
        return restored;
      }
    }

    ItemRef main = inventory.mainHand();
    if (owner.isItem(main) && BlockingShields.SHIELD.equals(main.type())) {
      int restored = restoreDurability(main, amount);
      if (restored > 0) {
        inventory.setMainHand(main);
        return restored;
      }
    }

    return 0;
  }

  /** Restores up to {@code amount} durability on the first damaged armor piece, boots first; returns what was restored. */
  public static int repairArmor(Adaptation<?> owner, PlayerInventoryRef inventory, int amount) {
    for (EquipmentSlot slot : ARMOR_SLOTS) {
      ItemRef piece = inventory.armor(slot);
      if (!owner.isItem(piece)) {
        continue;
      }

      int restored = restoreDurability(piece, amount);
      if (restored > 0) {
        inventory.setArmor(slot, piece);
        return restored;
      }
    }

    return 0;
  }

  private static int restoreDurability(ItemRef item, int amount) {
    int damage = item.damage();
    if (damage <= 0) {
      return 0;
    }

    int restored = Math.min(damage, amount);
    item.setDamage(damage - restored);
    return restored;
  }
}
