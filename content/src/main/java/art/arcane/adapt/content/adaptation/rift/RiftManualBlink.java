package art.arcane.adapt.content.adaptation.rift;

import art.arcane.adapt.platform.entity.PlayerRef;

/** A rift adaptation that blinks on the player's double jump, which other double-jump abilities then leave alone. */
public interface RiftManualBlink {
  boolean usesManualTrigger(PlayerRef player);
}
