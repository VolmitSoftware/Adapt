package art.arcane.adapt.engine.adaptation;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;

/**
 * Entity facts the SPI cannot express that adaptation guards depend on: whether a player handle is a real
 * connected server player (not a fake or NPC player class), and whether an entity is an NPC, a marker armour stand
 * or one of Adapt's minions, which adaptations must never hit.
 */
public interface EntityGuardPort {
  EntityGuardPort NONE = new EntityGuardPort() {
  };

  /** False for fake players (mod FakePlayers, NPC plugin player classes, packet-spoofed handles). */
  default boolean isServerPlayer(PlayerRef player) {
    return player != null;
  }

  /** True for entities adaptations must treat as friendly: NPCs, marker armour stands and minions. */
  default boolean isShielded(EntityRef entity) {
    return false;
  }
}
