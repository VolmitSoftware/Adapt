package art.arcane.adapt.content.adaptation.unarmed;

import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.platform.entity.AttributeMod;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;

/**
 * The damage pool Unarmed Power, Glass Cannon and Sucker Punch share. Each bonus is a share of the bare fist and the
 * shares add, so a punch deals {@code fist * (1 + power + glass + sucker)} before the flat adds, whichever of them the
 * player has and in whatever order their handlers run. Power's share reaches the hit through its attack damage
 * modifier; the others add theirs to the event as {@code fist * share}.
 */
public final class UnarmedStrike {
  private UnarmedStrike() {
  }

  /**
   * The bare fist behind a hit: the platform's original damage with Unarmed Power's modifier divided out. Crits, attack
   * cooldown and other attribute effects stay in it. Read from the original damage so earlier pool handlers do not
   * change it.
   */
  public static double fist(EntityDamageByEntityEvent e, PlayerRef attacker) {
    double scale = 1D + powerShare(attacker);
    return scale > 0D ? e.originalDamage() / scale : e.originalDamage();
  }

  /** Unarmed Power's share currently on the attacker's attack damage, 0 without its modifier. */
  private static double powerShare(PlayerRef attacker) {
    AttributeMod power = attacker.modifier(Attributes.ATTACK_DAMAGE,
        AdaptAttributeService.get().modifierKey(UnarmedPower.ID.toString(), UnarmedPower.SLOT_POWER));
    return power != null && power.operation() == AttributeOperation.ADD_MULTIPLIED_TOTAL ? power.amount() : 0D;
  }
}
