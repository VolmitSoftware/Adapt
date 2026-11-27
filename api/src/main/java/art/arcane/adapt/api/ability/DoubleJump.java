package art.arcane.adapt.api.ability;

import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.platform.entity.PlayerRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;
import java.util.UUID;

/**
 * Watches players for a double jump: a jump, then one more jump press in the air. The press that leaves the ground
 * counts as the first; after walking off an edge both presses happen in the air. Never completes while the player
 * flies, glides, rides, swims or climbs, or may fly. Jump presses come from the client's input where the platform
 * reads it, from the Adapt client mod, or from the flight request a vanilla client sends after two quick presses.
 *
 * <p>Each instance keeps its own per-player state; an adaptation holds one and feeds it every movement.
 */
@ApiStatus.Experimental
public interface DoubleJump {
  /** A new watcher. Needs a running core. */
  static DoubleJump create() {
    return Engine.bridge().abilities().doubleJump();
  }

  /** Reads the player's input now; true once, at the moment the double jump completes. */
  boolean update(PlayerRef player);

  /** Forgets the presses seen so far and keeps listening, for a player who moved without walking (a teleport). */
  void restart(UUID player);

  /** Stops watching the player until the next {@link #update}. */
  default void reset(PlayerRef player) {
    reset(Objects.requireNonNull(player, "player").uuid());
  }

  /** Stops watching the player until the next {@link #update}. */
  void reset(UUID player);
}
