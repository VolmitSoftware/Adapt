package art.arcane.adapt.engine.adaptation;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;

/**
 * Damage an ability deals in a player's name. The server reports it as that player's attack, so while it lands
 * {@link AdaptationRuntimeGuards#resolveAttackContext} refuses it and no upgrade reads it as a melee hit.
 */
public final class AbilityDamage {
  private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

  private AbilityDamage() {
  }

  public static void deal(LivingRef target, double amount, EntityRef source) {
    int[] depth = DEPTH.get();
    depth[0]++;
    try {
      target.damage(amount, source);
    } finally {
      depth[0]--;
    }
  }

  public static boolean dealing() {
    return DEPTH.get()[0] > 0;
  }
}
