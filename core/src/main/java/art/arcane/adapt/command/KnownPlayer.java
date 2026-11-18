package art.arcane.adapt.command;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.platform.entity.PlayerRef;

import java.util.Objects;
import java.util.UUID;

/** A command target that may be offline: its id and last known name, which is null when the server never saw one. */
public record KnownPlayer(UUID uuid, String name) {
  public KnownPlayer {
    Objects.requireNonNull(uuid, "uuid");
  }

  public static KnownPlayer of(PlayerRef player) {
    return new KnownPlayer(player.uuid(), player.name());
  }

  /** The online player, or null when offline. */
  public PlayerRef online() {
    return AdaptCore.platform().host().player(uuid);
  }

  /** The name, or the id when no name is known. */
  public String displayName() {
    return name == null ? uuid.toString() : name;
  }
}
