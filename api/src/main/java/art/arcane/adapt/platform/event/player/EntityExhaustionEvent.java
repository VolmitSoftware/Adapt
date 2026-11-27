package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;
import org.jetbrains.annotations.ApiStatus;

/**
 * A player is about to gain food exhaustion. Posted for players only. Cancelling adds no exhaustion. Mutable:
 * exhaustion, cancelled.
 */
@ApiStatus.AvailableSince("1.0.0")
public class EntityExhaustionEvent extends PlayerEvent {
  private final Reason reason;
  private float exhaustion;

  public EntityExhaustionEvent(PlayerRef player, Reason reason, float exhaustion) {
    super(player);
    this.reason = reason == null ? Reason.UNKNOWN : reason;
    this.exhaustion = exhaustion;
  }

  /** What caused the exhaustion; {@link Reason#UNKNOWN} when the platform could not tell. */
  public Reason reason() {
    return reason;
  }

  /** Exhaustion about to be added. */
  public float exhaustion() {
    return exhaustion;
  }

  /** Changes the exhaustion added. */
  public void setExhaustion(float exhaustion) {
    this.exhaustion = exhaustion;
  }

  /**
   * Sources of exhaustion, matching Bukkit's exhaustion reasons.
   * A {@code switch} over this enum needs a default branch: minor releases add constants.
   */
  public enum Reason {
    /** Breaking a block. */
    BLOCK_MINED,
    /** The Hunger status effect. */
    HUNGER_EFFECT,
    /** Taking damage. */
    DAMAGED,
    /** Attacking an entity. */
    ATTACK,
    /** Jumping while sprinting. */
    JUMP_SPRINT,
    JUMP,
    SWIM,
    /** Moving with the head under water. */
    WALK_UNDERWATER,
    /** Moving in water without being submerged or swimming. */
    WALK_ON_WATER,
    SPRINT,
    CROUCH,
    WALK,
    /** Natural health regeneration. */
    REGEN,
    /** A source the platform could not identify. */
    UNKNOWN
  }
}
