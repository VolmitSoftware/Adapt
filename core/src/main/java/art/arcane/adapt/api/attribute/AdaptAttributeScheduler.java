package art.arcane.adapt.api.attribute;

import art.arcane.adapt.platform.entity.LivingRef;

public interface AdaptAttributeScheduler {
  boolean runOnEntity(LivingRef entity, Runnable action);

  void runOnEntityLater(LivingRef entity, Runnable action, long delayTicks);
}
