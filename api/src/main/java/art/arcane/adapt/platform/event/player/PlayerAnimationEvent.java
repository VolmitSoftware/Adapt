package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;
import org.jetbrains.annotations.ApiStatus;

/** A player swings an arm. Mutable: cancelled. */
@ApiStatus.AvailableSince("1.0.0")
public class PlayerAnimationEvent extends PlayerEvent {
  private final Animation animation;

  public PlayerAnimationEvent(PlayerRef player, Animation animation) {
    super(player);
    this.animation = animation == null ? Animation.ARM_SWING : animation;
  }

  /** Which arm swung. */
  public Animation animation() {
    return animation;
  }

  /**
   * Arm swing kinds.
   * A {@code switch} over this enum needs a default branch: minor releases add constants.
   */
  public enum Animation {
    /** A main-hand swing. */
    ARM_SWING,
    /** An off-hand swing. */
    OFF_ARM_SWING
  }
}
