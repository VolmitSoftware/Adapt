package art.arcane.adapt.api.golem;

import art.arcane.adapt.api.version.IAttribute;
import art.arcane.adapt.api.version.Version;
import art.arcane.adapt.util.reflect.registries.Attributes;
import org.bukkit.entity.LivingEntity;

public final class GolemVitals {
  private static final double FALLBACK_MAX_HEALTH = 20D;

  private GolemVitals() {
  }

  public static double maxHealth(LivingEntity entity) {
    if (Attributes.MAX_HEALTH == null) {
      return FALLBACK_MAX_HEALTH;
    }
    IAttribute attribute = Version.get().getAttribute(entity, Attributes.MAX_HEALTH);
    return attribute == null ? FALLBACK_MAX_HEALTH : attribute.getValue();
  }
}
