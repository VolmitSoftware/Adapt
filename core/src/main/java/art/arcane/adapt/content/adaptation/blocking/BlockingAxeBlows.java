package art.arcane.adapt.content.adaptation.blocking;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;

/** Recognizes axe blows on a raised shield, the hits that can disable it. */
public final class BlockingAxeBlows {
  private BlockingAxeBlows() {
  }

  /** The blocking player an axe-wielding attacker struck, or null for any other hit. */
  public static PlayerRef defender(Adaptation<?> owner, EntityDamageByEntityEvent e) {
    if (!(e.entity() instanceof PlayerRef p) || !p.isBlocking() || !(e.damager() instanceof LivingRef attacker)) {
      return null;
    }
    return owner.isAxe(attacker.equipment(EquipmentSlot.MAIN_HAND)) ? p : null;
  }

  /** Ticks left before the player's shield can be raised again. */
  public static int disabledTicks(PlayerRef p) {
    return p.cooldownTicks(BlockingShields.SHIELD);
  }
}
