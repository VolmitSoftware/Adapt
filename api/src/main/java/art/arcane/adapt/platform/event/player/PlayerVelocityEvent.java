package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Vec3;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/** A player's velocity is about to be sent to the client. Cancelling sends nothing. Mutable: velocity, cancelled. */
@ApiStatus.AvailableSince("1.0.0")
public class PlayerVelocityEvent extends PlayerEvent {
  private Vec3 velocity;

  public PlayerVelocityEvent(PlayerRef player, Vec3 velocity) {
    super(player);
    this.velocity = Objects.requireNonNull(velocity, "velocity");
  }

  /** The velocity about to be sent. */
  public Vec3 velocity() {
    return velocity;
  }

  /** Sends this velocity instead. */
  public void setVelocity(Vec3 velocity) {
    this.velocity = Objects.requireNonNull(velocity, "velocity");
  }
}
