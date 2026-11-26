package art.arcane.adapt.content.adaptation.hunter;

import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;

import java.util.Map;
import java.util.UUID;

/** Per-player damage ramp on one target: stacks climb while the same target is struck within the window. */
public final class HunterRamp {
  private final Map<UUID, State> ramps;

  public HunterRamp(Map<UUID, State> ramps) {
    this.ramps = ramps;
  }

  /** Sweep hits splash onto bystanders; they neither build nor break the ramp on the main target. */
  public static boolean ramps(EntityDamageByEntityEvent e) {
    return e.cause() != DamageCause.ENTITY_SWEEP_ATTACK;
  }

  public static int nextStacks(int previousStacks, boolean sameTarget, boolean withinWindow, int cap, int step) {
    int safeCap = Math.max(1, cap);
    if (!sameTarget || !withinWindow || previousStacks < 1) {
      return 1;
    }
    return Math.min(previousStacks + Math.max(1, step), safeCap);
  }

  public static double damageMultiplier(int stacks, double perStackBonus) {
    return 1.0D + (Math.max(0, stacks - 1) * Math.max(0.0D, perStackBonus));
  }

  /** Records a hit by {@code playerId} on {@code targetId} and returns the stacks it now holds. */
  public int hit(UUID playerId, UUID targetId, int cap, int step, long decayMillis, long now) {
    State state = ramps.computeIfAbsent(playerId, (UUID key) -> new State());
    boolean sameTarget = targetId.equals(state.target);
    boolean withinWindow = now - state.lastHitAt <= decayMillis;
    int stacks = nextStacks(state.stacks, sameTarget, withinWindow, cap, step);
    state.target = targetId;
    state.stacks = stacks;
    state.lastHitAt = now;
    return stacks;
  }

  public static final class State {
    private UUID target;
    private int stacks;
    private long lastHitAt;
  }
}
