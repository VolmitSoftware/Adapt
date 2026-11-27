package art.arcane.adapt.api.xp;

import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import org.jetbrains.annotations.ApiStatus;

/** How a mob came to be, so kill rewards are not paid for farmed mobs. */
@ApiStatus.Experimental
public final class MobOrigin {
  private MobOrigin() {
  }

  /** True when the mob spawned from a farmable source such as a spawner, an egg, breeding or a command. */
  public static boolean isFarmed(EntityRef entity) {
    return Engine.bridge().world().isFarmed(entity);
  }

  /** True when killing the victim may pay a kill reward: not farmed, not a minion or grave mob, not the killer's pet. */
  public static boolean paysKillReward(PlayerRef killer, EntityRef victim) {
    return Engine.bridge().world().paysKillReward(killer, victim);
  }
}
