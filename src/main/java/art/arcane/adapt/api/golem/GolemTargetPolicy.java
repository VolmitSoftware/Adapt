package art.arcane.adapt.api.golem;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public interface GolemTargetPolicy {
  LivingEntity select(GolemHandle golem, Player owner);

  boolean holds(GolemHandle golem, Player owner, LivingEntity target);

  default void tick(GolemHandle golem, Player owner) {
  }
}
