package art.arcane.adapt.api.attribute;

import org.bukkit.entity.LivingEntity;

public interface AdaptAttributeScheduler {
  boolean runOnEntity(LivingEntity entity, Runnable action);

  void runOnEntityLater(LivingEntity entity, Runnable action, long delayTicks);
}
