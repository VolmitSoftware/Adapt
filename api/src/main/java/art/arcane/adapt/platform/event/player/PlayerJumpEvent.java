package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * A player jumps. Cancelling sends the player back to {@link #from()}, except on Bukkit without Paper, where the
 * event comes from the jump statistic and cannot be cancelled. Mutable: cancelled.
 */
@ApiStatus.AvailableSince("1.0.0")
public class PlayerJumpEvent extends PlayerEvent {
  private final Loc from;
  private final Loc to;

  public PlayerJumpEvent(PlayerRef player, Loc from) {
    this(player, from, from);
  }

  public PlayerJumpEvent(PlayerRef player, Loc from, Loc to) {
    super(player);
    this.from = Objects.requireNonNull(from, "from");
    this.to = to == null ? from : to;
  }

  /** Where the jump started. */
  public Loc from() {
    return from;
  }

  /** Position after the jump movement, or {@link #from()} when the platform did not report one. */
  public Loc to() {
    return to;
  }
}
