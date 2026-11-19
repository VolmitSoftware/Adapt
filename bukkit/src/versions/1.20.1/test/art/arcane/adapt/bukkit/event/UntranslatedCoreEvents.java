package art.arcane.adapt.bukkit.event;

import java.util.Set;

/**
 * Core events Paper 1.20.1 never fires: those missing on 1.21.1, plus the crafter, which 1.20.1 does not have. The
 * shared mace smash translation is present but never posts there (no mace before 1.21).
 */
final class UntranslatedCoreEvents {
  static final Set<String> NAMES = Set.of(
      "art.arcane.adapt.platform.event.entity.EntityLungeEvent",
      "art.arcane.adapt.platform.event.entity.EntityEquipmentChangedEvent",
      "art.arcane.adapt.platform.event.entity.ItemTransportingEntityValidateTargetEvent",
      "art.arcane.adapt.platform.event.player.PlayerInputEvent",
      "art.arcane.adapt.platform.event.block.CrafterCraftEvent");

  private UntranslatedCoreEvents() {
  }
}
