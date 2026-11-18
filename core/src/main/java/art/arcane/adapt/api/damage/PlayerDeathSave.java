package art.arcane.adapt.api.damage;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;

public interface PlayerDeathSave {
  default boolean protectsFromDamage(EntityDamageEvent event) {
    return false;
  }

  void tryPreventDeath(EntityDamageEvent event);

  int deathSavePriority(PlayerRef player);

  int defaultOrder();
}
