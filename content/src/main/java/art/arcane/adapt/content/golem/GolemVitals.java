package art.arcane.adapt.content.golem;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.event.entity.EntityRegainHealthEvent;

public final class GolemVitals {
  private static final double FALLBACK_MAX_HEALTH = 20D;

  private GolemVitals() {
  }

  public static double maxHealth(LivingRef entity) {
    double value = entity.attributeValue(Attributes.MAX_HEALTH);
    return Double.isNaN(value) ? FALLBACK_MAX_HEALTH : value;
  }

  public static boolean fullHealth(LivingRef entity) {
    return entity.health() >= maxHealth(entity);
  }

  public static double heal(LivingRef entity, double amount, EntityRegainHealthEvent.Reason reason) {
    return entity.regainHealth(amount, reason);
  }
}
