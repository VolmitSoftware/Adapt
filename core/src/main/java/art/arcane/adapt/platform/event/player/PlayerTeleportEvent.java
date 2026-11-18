package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.value.Loc;

import java.util.Objects;

/**
 * A player teleports. Not a {@link PlayerMoveEvent}: move handlers never see teleports, as in Bukkit, where
 * the teleport event has its own handler list. Mutable: {@code to}, cancelled.
 */
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

  public Loc from() {
    return from;
  }

  public Loc to() {
    return to;
  }

  public void setTo(Loc to) {
    this.to = Objects.requireNonNull(to, "to");
  }

  public TeleportCause cause() {
    return cause;
  }
}
