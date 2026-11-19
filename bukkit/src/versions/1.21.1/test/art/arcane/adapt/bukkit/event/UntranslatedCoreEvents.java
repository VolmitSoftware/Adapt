package art.arcane.adapt.bukkit.event;

import java.util.Set;

/**
 * Core events whose native events Paper 1.21.1 does not have: spear lunge content, the equipment change and copper
 * golem transport events, and client movement input. Mace smashes are read from the hit's damage event.
 */
final class UntranslatedCoreEvents {
  static final Set<String> NAMES = Set.of(
      "art.arcane.adapt.platform.event.entity.EntityLungeEvent",
      "art.arcane.adapt.platform.event.entity.EntityEquipmentChangedEvent",
      "art.arcane.adapt.platform.event.entity.ItemTransportingEntityValidateTargetEvent",
      "art.arcane.adapt.platform.event.player.PlayerInputEvent");

  private UntranslatedCoreEvents() {
  }
}
