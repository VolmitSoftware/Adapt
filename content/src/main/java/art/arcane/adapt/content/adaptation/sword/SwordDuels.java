package art.arcane.adapt.content.adaptation.sword;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;

/** Duel checks shared by the sword adaptations that reward one-on-one fights and finishing blows. */
public final class SwordDuels {
  private SwordDuels() {
  }

  /** Monsters and other players within {@code radius} of the player, counted up to 2. */
  public static int countEngaged(PlayerRef p, double radius) {
    int count = 0;
    for (EntityRef entity : p.nearbyEntities(radius, radius, radius)) {
      if (entity.uuid().equals(p.uuid())) {
        continue;
      }

      if (entity.has(EntityTrait.MONSTER) || entity instanceof PlayerRef) {
        count++;
        if (count > 1) {
          return count;
        }
      }
    }
    return count;
  }

  /** The target's maximum health, or its current health when it has no maximum health attribute. */
  public static double maxHealth(LivingRef entity) {
    return entity.hasAttribute(Attributes.MAX_HEALTH) ? entity.attributeValue(Attributes.MAX_HEALTH) : entity.health();
  }
}
