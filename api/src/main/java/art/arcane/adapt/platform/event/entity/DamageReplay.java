package art.arcane.adapt.platform.event.entity;

import art.arcane.adapt.platform.entity.LivingRef;
import org.jetbrains.annotations.ApiStatus;

/** Deals damage again from the same source as the hit it came from (type, causing and direct entity, position). */
@FunctionalInterface
@ApiStatus.AvailableSince("1.0.0")
public interface DamageReplay {
  /**
   * Deals {@code amount} base damage to {@code target} through the normal damage pipeline, so reductions apply and the
   * hit posts its own damage event.
   */
  void dealTo(LivingRef target, double amount);
}
