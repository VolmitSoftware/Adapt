package art.arcane.adapt.api.golem;

import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;

public interface GolemTargetPolicy {
  LivingRef select(GolemHandle golem, PlayerRef owner);

  boolean holds(GolemHandle golem, PlayerRef owner, LivingRef target);

  default void tick(GolemHandle golem, PlayerRef owner) {
  }

  default boolean idleBackoff() {
    return false;
  }
}
