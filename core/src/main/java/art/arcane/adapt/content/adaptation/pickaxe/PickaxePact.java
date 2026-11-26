package art.arcane.adapt.content.adaptation.pickaxe;

import art.arcane.adapt.platform.event.player.PlayerItemDamageEvent;
import art.arcane.adapt.platform.item.ItemRef;

/** Keeps a pickaxe from breaking: wear that would break it leaves it at its last durability point. */
public final class PickaxePact {
  private PickaxePact() {
  }

  public static boolean spare(PlayerItemDamageEvent e) {
    ItemRef item = e.item();
    int maxDurability = item.maxDamage();
    if (item.damage() + e.damage() < maxDurability) {
      return false;
    }

    e.setCancelled(true);
    item.setDamage(maxDurability - 1);
    return true;
  }
}
