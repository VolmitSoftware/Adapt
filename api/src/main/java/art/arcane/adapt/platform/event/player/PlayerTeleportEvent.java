package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.value.Loc;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * A player teleports. Not a {@link PlayerMoveEvent}: move handlers never see teleports, as in Bukkit, where the
 * teleport event has its own handler list. Cancelling keeps the player in place. Mutable: {@code to}, cancelled.
 */
@ApiStatus.AvailableSince("1.0.0")
public class PlayerTeleportEvent extends PlayerEvent {
  private final Loc from;
  private final TeleportCause cause;
  private Loc to;

  public PlayerTeleportEvent(PlayerRef player, Loc from, Loc to, TeleportCause cause) {
    super(player);
    this.from = Objects.requireNonNull(from, "from");
    this.to = Objects.requireNonNull(to, "to");
    this.cause = cause == null ? TeleportCause.UNKNOWN : cause;
  }

  /** Where the player is before the teleport. */
  public Loc from() {
    return from;
  }

  /** Where the player will arrive. */
  public Loc to() {
    return to;
  }

  /** Sends the player to this location instead. */
  public void setTo(Loc to) {
    this.to = Objects.requireNonNull(to, "to");
  }

  /** What started the teleport; {@link TeleportCause#UNKNOWN} when the platform gave none. */
  public TeleportCause cause() {
    return cause;
  }
}
