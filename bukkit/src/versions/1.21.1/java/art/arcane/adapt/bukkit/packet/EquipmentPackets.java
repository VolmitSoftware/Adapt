package art.arcane.adapt.bukkit.packet;

import art.arcane.adapt.bukkit.natives.ServerNatives;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Equipment packets for entity ids the server does not know (virtual players). Bukkit only sends equipment for
 * real living entities, so this sends {@code ClientboundSetEquipmentPacket} through this jar's server natives.
 */
public final class EquipmentPackets {
  private EquipmentPackets() {
  }

  public static boolean send(Player viewer, int entityId, Map<EquipmentSlot, ItemStack> items) {
    return !items.isEmpty() && ServerNatives.sendEquipment(viewer, entityId, items);
  }
}
