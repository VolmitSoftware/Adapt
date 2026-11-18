package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;

import java.util.Objects;

/**
 * A player sends a chat message. May be posted off the server thread when async() is true. Mutable:
 * cancelled.
 */
public class PlayerChatEvent extends PlayerEvent {
  private final String message;
  private final boolean async;

  public PlayerChatEvent(PlayerRef player, String message, boolean async) {
    super(player);
    this.message = Objects.requireNonNull(message, "message");
    this.async = async;
  }

  public String message() {
    return message;
  }

  public boolean async() {
    return async;
  }
}
