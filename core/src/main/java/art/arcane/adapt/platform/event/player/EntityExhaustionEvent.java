package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;

/** A player gains exhaustion. Mutable: exhaustion, cancelled. */
public class EntityExhaustionEvent extends PlayerEvent {
  private final Reason reason;
  private float exhaustion;

  public EntityExhaustionEvent(PlayerRef player, Reason reason, float exhaustion) {
    super(player);
    this.reason = reason == null ? Reason.UNKNOWN : reason;
    this.exhaustion = exhaustion;
  }

  public Reason reason() {
    return reason;
  }

  public float exhaustion() {
    return exhaustion;
  }

  public void setExhaustion(float exhaustion) {
    this.exhaustion = exhaustion;
  }

  public enum Reason {
    BLOCK_MINED,
    HUNGER_EFFECT,
    DAMAGED,
    ATTACK,
    JUMP_SPRINT,
    JUMP,
    SWIM,
    WALK_UNDERWATER,
    WALK_ON_WATER,
    SPRINT,
    CROUCH,
    WALK,
    REGEN,
    UNKNOWN
  }
}
