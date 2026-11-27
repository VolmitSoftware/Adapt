package art.arcane.adapt.content.adaptation.blocking;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

/** Shield checks and wear shared by the blocking adaptations. */
public final class BlockingShields {
  public static final Id SHIELD = Id.minecraft("shield");

  private BlockingShields() {
  }

  /** True when either hand holds a vanilla shield. */
  public static boolean held(Adaptation<?> owner, PlayerRef p) {
    ItemRef main = p.inventory().mainHand();
    ItemRef off = p.inventory().offHand();
    return (ItemKinds.isItem(main) && SHIELD.equals(main.type())) || (ItemKinds.isItem(off) && SHIELD.equals(off.type()));
  }

  /** The player whose raised shield took part of this hit, or null. */
  public static PlayerRef blocker(Adaptation<?> owner, EntityDamageByEntityEvent e) {
    if (!(e.entity() instanceof PlayerRef p) || !p.isBlocking() || !held(owner, p) || e.blockedDamage() <= 0D) {
      return null;
    }
    return p;
  }

  /** The held shield, off hand first, or the main-hand item when the off hand holds none. */
  public static ItemRef preferred(PlayerRef p) {
    ItemRef shield = p.inventory().offHand();
    return SHIELD.equals(shield.type()) ? shield : p.inventory().mainHand();
  }

  /** Wears {@code cost} durability off the held shield without ever breaking it. */
  public static void wear(Adaptation<?> owner, PlayerRef p, int cost) {
    PlayerInventoryRef inventory = p.inventory();
    ItemRef shield = inventory.offHand();
    boolean offHand = ItemKinds.isItem(shield) && SHIELD.equals(shield.type());
    if (!offHand) {
      shield = inventory.mainHand();
      if (!ItemKinds.isItem(shield) || !SHIELD.equals(shield.type())) {
        return;
      }
    }

    int maxDurability = shield.maxDamage();
    if (maxDurability <= 1) {
      return;
    }

    int newDamage = Math.min(maxDurability - 1, shield.damage() + cost);
    if (newDamage <= shield.damage()) {
      return;
    }
    shield.setDamage(newDamage);
    if (offHand) {
      inventory.setOffHand(shield);
    } else {
      inventory.setMainHand(shield);
    }
  }
}
