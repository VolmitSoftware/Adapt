package art.arcane.adapt.api.golem;

import art.arcane.adapt.api.version.IAttribute;
import art.arcane.adapt.api.version.Version;
import art.arcane.adapt.util.reflect.registries.Attributes;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason;

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

  public static boolean fullHealth(LivingEntity entity) {
    return entity.getHealth() >= maxHealth(entity);
  }

  public static double heal(LivingEntity entity, double amount, RegainReason reason) {
    if (!Double.isFinite(amount) || amount <= 0D || entity.isDead()) {
      return 0D;
    }
    double health = entity.getHealth();
    double maximum = maxHealth(entity);
    if (health <= 0D || health >= maximum) {
      return 0D;
    }
    EntityRegainHealthEvent regain = new EntityRegainHealthEvent(entity, amount, reason);
    Bukkit.getPluginManager().callEvent(regain);
    double granted = regain.getAmount();
    if (regain.isCancelled() || !Double.isFinite(granted) || granted <= 0D) {
      return 0D;
    }
    double next = Math.min(maximum, health + granted);
    entity.setHealth(next);
    return next - health;
  }
}
