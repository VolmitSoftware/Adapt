package art.arcane.adapt.engine.adaptation;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;

import java.util.Set;

public final class AdaptationDamageTargets {
  private static final Set<Id> NEUTRAL_ENEMIES = Set.of(
      Id.minecraft("enderman"),
      Id.minecraft("piglin"),
      Id.minecraft("zombified_piglin"),
      Id.minecraft("spider"),
      Id.minecraft("cave_spider")
  );

  private AdaptationDamageTargets() {
  }

  public static boolean allows(EntityRef target, boolean ignorePassiveMobs) {
    if (!ignorePassiveMobs || target instanceof PlayerRef) {
      return true;
    }
    if (target == null || !target.has(EntityTrait.ENEMY)) {
      return false;
    }
    return !NEUTRAL_ENEMIES.contains(target.type());
  }
}
