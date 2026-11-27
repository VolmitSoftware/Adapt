package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * A player's position or rotation changed. Hot path: posted for every movement packet that moves more than 1/16
 * block or turns more than 10 degrees, so handlers must return fast. Teleports post a {@link PlayerTeleportEvent}
 * instead. Mutable: {@code to}, cancelled (cancel sends the player back to {@code from}).
 */
@ApiStatus.AvailableSince("1.0.0")
public class PlayerMoveEvent extends PlayerEvent {
  private final Loc from;
  private Loc to;

  public PlayerMoveEvent(PlayerRef player, Loc from, Loc to) {
    super(player);
    this.from = Objects.requireNonNull(from, "from");
    this.to = Objects.requireNonNull(to, "to");
  }

  /** Position before the movement. */
  public Loc from() {
    return from;
  }

  /** Position after the movement. */
  public Loc to() {
    return to;
  }

  /** Moves the player to this location instead. */
  public void setTo(Loc to) {
    this.to = Objects.requireNonNull(to, "to");
  }

  /** True when the position changed at all, not just rotation. */
  public boolean changedPosition() {
    return from.x() != to.x() || from.y() != to.y() || from.z() != to.z() || !from.sameWorld(to);
  }

  /** True when the block position changed, not just rotation or sub-block movement. */
  public boolean changedBlock() {
    return from.blockX() != to.blockX() || from.blockY() != to.blockY() || from.blockZ() != to.blockZ()
        || !from.sameWorld(to);
  }
}
