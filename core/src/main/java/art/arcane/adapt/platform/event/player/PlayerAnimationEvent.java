package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;

/** A player swings an arm. Mutable: cancelled. */
public class PlayerAnimationEvent extends PlayerEvent {
  private final Animation animation;

  public PlayerAnimationEvent(PlayerRef player, Animation animation) {
    super(player);
    this.animation = animation == null ? Animation.ARM_SWING : animation;
  }

  public Animation animation() {
    return animation;
  }

  public enum Animation {
    ARM_SWING,
    OFF_ARM_SWING
  }
}
