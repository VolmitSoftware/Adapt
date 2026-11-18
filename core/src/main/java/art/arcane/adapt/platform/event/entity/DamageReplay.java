package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.LivingRef;

/** Deals damage again from the same source as the hit it came from (type, causing and direct entity, position). */
@FunctionalInterface
public interface DamageReplay {
  void dealTo(LivingRef target, double amount);
}
