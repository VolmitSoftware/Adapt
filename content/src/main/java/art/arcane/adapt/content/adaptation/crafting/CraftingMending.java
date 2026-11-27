package art.arcane.adapt.content.adaptation.crafting;

import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;

import java.util.List;

/** Repairs held items and worn armor by a number of durability points. */
public final class CraftingMending {
  public static final List<EquipmentSlot> ARMOR = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST,
      EquipmentSlot.LEGS, EquipmentSlot.FEET);

  private CraftingMending() {
  }

  /** Repairs the item in {@code slot}; returns the durability restored, 0 when it was not damaged or not repairable. */
  public static int repair(PlayerRef player, EquipmentSlot slot, int amount) {
    ItemRef item = player.equipment(slot);
    if (amount <= 0 || !repairable(item) || item.damage() <= 0) {
      return 0;
    }
    int restored = Math.min(amount, item.damage());
    item.setDamage(item.damage() - restored);
    player.setEquipment(slot, item);
    return restored;
  }

  /** Repairs the item in the main hand, or the most damaged armor piece when the main hand is empty. */
  public static int repairHeldOrArmor(PlayerRef player, int amount) {
    ItemRef held = player.inventory().mainHand();
    if (held != null && !held.isEmpty()) {
      return repair(player, EquipmentSlot.MAIN_HAND, amount);
    }
    EquipmentSlot worst = mostDamagedArmor(player);
    return worst == null ? 0 : repair(player, worst, amount);
  }

  /** Repairs every armor piece worn; returns the total durability restored. */
  public static int repairAllArmor(PlayerRef player, int amount) {
    int restored = 0;
    for (EquipmentSlot slot : ARMOR) {
      restored += repair(player, slot, amount);
    }
    return restored;
  }

  /** The worn armor slot missing the most durability, or null when no piece is damaged. */
  public static EquipmentSlot mostDamagedArmor(PlayerRef player) {
    EquipmentSlot worst = null;
    int worstDamage = 0;
    for (EquipmentSlot slot : ARMOR) {
      ItemRef item = player.equipment(slot);
      if (repairable(item) && item.damage() > worstDamage) {
        worst = slot;
        worstDamage = item.damage();
      }
    }
    return worst;
  }

  private static boolean repairable(ItemRef item) {
    return item != null && !item.isEmpty() && !item.isUnbreakable() && item.maxDamage() > 0;
  }
}
