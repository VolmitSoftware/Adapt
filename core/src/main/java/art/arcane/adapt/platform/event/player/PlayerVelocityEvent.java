package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Vec3;

import java.util.Objects;

/** A player's velocity is about to be sent. Mutable: velocity, cancelled. */
public class PlayerVelocityEvent extends PlayerEvent {
  private Vec3 velocity;

  public PlayerVelocityEvent(PlayerRef player, Vec3 velocity) {
    super(player);
    this.velocity = Objects.requireNonNull(velocity, "velocity");
  }

  public Vec3 velocity() {
    return velocity;
  }

  public void setVelocity(Vec3 velocity) {
    this.velocity = Objects.requireNonNull(velocity, "velocity");
  }
}
