package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.event.AdaptEvent;

import java.util.Objects;
import java.util.UUID;

/**
 * A player passed authentication and is about to log in. Posted off the server thread. Read-only.
 */
public class PlayerPreLoginEvent extends AdaptEvent {
  private final UUID uuid;
  private final String name;
  private final boolean allowed;

  public PlayerPreLoginEvent(UUID uuid, String name, boolean allowed) {
    this.uuid = Objects.requireNonNull(uuid, "uuid");
    this.name = Objects.requireNonNull(name, "name");
    this.allowed = allowed;
  }

  public UUID uuid() {
    return uuid;
  }

  public String name() {
    return name;
  }

  public boolean allowed() {
    return allowed;
  }
}
