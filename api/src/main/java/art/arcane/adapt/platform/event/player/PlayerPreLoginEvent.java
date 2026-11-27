package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.event.AdaptEvent;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;
import java.util.UUID;

/**
 * A player passed authentication and is about to log in. Bukkit posts it off the server thread; mod platforms post
 * it on the server thread. Read-only.
 */
@ApiStatus.AvailableSince("1.0.0")
public class PlayerPreLoginEvent extends AdaptEvent {
  private final UUID uuid;
  private final String name;
  private final boolean allowed;

  public PlayerPreLoginEvent(UUID uuid, String name, boolean allowed) {
    this.uuid = Objects.requireNonNull(uuid, "uuid");
    this.name = Objects.requireNonNull(name, "name");
    this.allowed = allowed;
  }

  /** The player's profile id. */
  public UUID uuid() {
    return uuid;
  }

  /** The player's profile name. */
  public String name() {
    return name;
  }

  /**
   * True when nothing has refused the login so far: bans, the whitelist, a full server, or on Bukkit another
   * plugin.
   */
  public boolean allowed() {
    return allowed;
  }
}
