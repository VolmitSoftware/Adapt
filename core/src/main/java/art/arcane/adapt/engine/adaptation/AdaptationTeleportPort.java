package art.arcane.adapt.engine.adaptation;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;

/**
 * Announces an adaptation teleport before it happens, so a host can cancel or redirect it. NONE lets every teleport
 * through unchanged.
 */
public interface AdaptationTeleportPort {
  AdaptationTeleportPort NONE = new AdaptationTeleportPort() {
  };

  /** The destination listeners settled on, or null when one of them cancelled the teleport. */
  default Loc announce(PlayerRef player, Adaptation<?> adaptation, Loc from, Loc to) {
    return to;
  }
}
