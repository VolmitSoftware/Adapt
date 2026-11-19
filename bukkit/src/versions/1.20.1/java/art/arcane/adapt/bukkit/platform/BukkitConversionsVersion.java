package art.arcane.adapt.bukkit.platform;

import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.TeleportCause;
import org.bukkit.event.player.PlayerTeleportEvent;

final class BukkitConversionsVersion {
  private BukkitConversionsVersion() {
  }

  static org.bukkit.inventory.EquipmentSlot slot(EquipmentSlot slot) {
    return switch (slot) {
      case MAIN_HAND -> org.bukkit.inventory.EquipmentSlot.HAND;
      case OFF_HAND -> org.bukkit.inventory.EquipmentSlot.OFF_HAND;
      case HEAD -> org.bukkit.inventory.EquipmentSlot.HEAD;
      case CHEST -> org.bukkit.inventory.EquipmentSlot.CHEST;
      case LEGS -> org.bukkit.inventory.EquipmentSlot.LEGS;
      case FEET -> org.bukkit.inventory.EquipmentSlot.FEET;
      case BODY, SADDLE -> throw new IllegalArgumentException("No " + slot + " slot on this server");
    };
  }

  static EquipmentSlot slot(org.bukkit.inventory.EquipmentSlot slot) {
    return switch (slot) {
      case HAND -> EquipmentSlot.MAIN_HAND;
      case OFF_HAND -> EquipmentSlot.OFF_HAND;
      case HEAD -> EquipmentSlot.HEAD;
      case CHEST -> EquipmentSlot.CHEST;
      case LEGS -> EquipmentSlot.LEGS;
      case FEET -> EquipmentSlot.FEET;
    };
  }

  static TeleportCause cause(PlayerTeleportEvent.TeleportCause cause) {
    if (cause == null) {
      return TeleportCause.UNKNOWN;
    }
    return switch (cause) {
      case PLUGIN -> TeleportCause.PLUGIN;
      case COMMAND -> TeleportCause.COMMAND;
      case ENDER_PEARL -> TeleportCause.ENDER_PEARL;
      case CHORUS_FRUIT -> TeleportCause.CHORUS_FRUIT;
      case NETHER_PORTAL -> TeleportCause.NETHER_PORTAL;
      case END_PORTAL -> TeleportCause.END_PORTAL;
      case END_GATEWAY -> TeleportCause.END_GATEWAY;
      case SPECTATE -> TeleportCause.SPECTATE;
      case DISMOUNT -> TeleportCause.DISMOUNT;
      case EXIT_BED -> TeleportCause.EXIT_BED;
      default -> TeleportCause.UNKNOWN;
    };
  }
}
