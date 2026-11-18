package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;

/**
 * Announces an adaptation teleport to third-party plugins before it happens (Bukkit's AdaptAdaptationTeleportEvent).
 * NONE lets every teleport through unchanged.
 */
public interface AdaptationTeleportPort {
  AdaptationTeleportPort NONE = new AdaptationTeleportPort() {
  };

  /** The destination listeners settled on, or null when one of them cancelled the teleport. */
  default Loc announce(PlayerRef player, Adaptation<?> adaptation, Loc from, Loc to) {
    return to;
  }
}
