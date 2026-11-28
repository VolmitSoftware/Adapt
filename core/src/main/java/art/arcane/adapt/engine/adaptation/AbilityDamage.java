package art.arcane.adapt.engine.adaptation;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;

/**
 * Damage an ability deals in a player's name. The server reports it as that player's attack, so while it lands
 * {@link AdaptationRuntimeGuards#resolveAttackContext} refuses it and no upgrade reads it as a melee hit. The same goes
 * for a melee hit an ability fixed, for the rest of its dispatch.
 */
public final class AbilityDamage {
  private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);
  /** Dispatch id of the last hit an ability fixed on this thread; 0 is never a dispatch id. */
  private static final ThreadLocal<long[]> FIXED = ThreadLocal.withInitial(() -> new long[1]);

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

  /** Marks the hit with {@code dispatchId} as fixed; every priority of one hit runs on the same thread. */
  public static void fix(long dispatchId) {
    FIXED.get()[0] = dispatchId;
  }

  public static boolean fixed(long dispatchId) {
    return FIXED.get()[0] == dispatchId;
  }

  public static boolean dealing() {
    return DEPTH.get()[0] > 0;
  }
}
