package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * A player sends a chat message. Bukkit posts it from the async chat event, so it may arrive off the server
 * thread, which {@link #async()} reports; mod platforms post it on the server thread. Cancelling stops the message
 * from being broadcast. Mutable: cancelled.
 */
@ApiStatus.AvailableSince("1.0.0")
public class PlayerChatEvent extends PlayerEvent {
  private final String message;
  private final boolean async;

  public PlayerChatEvent(PlayerRef player, String message, boolean async) {
    super(player);
    this.message = Objects.requireNonNull(message, "message");
    this.async = async;
  }

  /** The message text. */
  public String message() {
    return message;
  }

  /** True when the event is posted off the server thread. */
  public boolean async() {
    return async;
  }
}
