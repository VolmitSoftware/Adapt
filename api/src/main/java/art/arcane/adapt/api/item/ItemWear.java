package art.arcane.adapt.api.item;

import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.PlayerRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * Wear on the items a player holds or wears, taken the way vanilla tool use takes it: Unbreaking applies, other
 * plugins see the item damage, and an item that runs out of durability breaks. Unlike
 * {@link art.arcane.adapt.api.content.SimpleAdaptation#damageHand}, nothing here is an ability cost a cost provider can
 * replace; wrap it in {@code payDurabilityCost} when it should be. Needs a running core.
 */
@ApiStatus.Experimental
public final class ItemWear {
  private ItemWear() {
  }

  /** Damages the item in {@code slot} by {@code amount}; nothing happens for an empty, unbreakable or zero amount. */
  public static void damage(PlayerRef player, EquipmentSlot slot, int amount) {
    if (amount <= 0) {
      return;
    }
    Engine.bridge().abilities().damageItem(Objects.requireNonNull(player, "player"), Objects.requireNonNull(slot, "slot"),
        amount);
  }
}
