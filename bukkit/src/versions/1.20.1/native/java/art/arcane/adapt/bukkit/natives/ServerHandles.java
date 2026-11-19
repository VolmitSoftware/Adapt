package art.arcane.adapt.bukkit.natives;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_20_R1.CraftEquipmentSlot;
import org.bukkit.craftbukkit.v1_20_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_20_R1.inventory.CraftItemStack;
import org.bukkit.entity.Player;

/**
 * CraftBukkit handles on Paper 1.20.1 (CraftBukkit under its versioned package). The server does not track the
 * client's movement on this version; the horizontal part is the distance the player moved since the last tick.
 */
final class ServerHandles {
  private ServerHandles() {
  }

  static boolean isServerPlayerClass(Class<?> playerClass) {
    return playerClass == CraftPlayer.class;
  }

  static ServerPlayer player(Player player) {
    return player instanceof CraftPlayer craftPlayer ? craftPlayer.getHandle() : null;
  }

  static Entity entity(org.bukkit.entity.Entity entity) {
    return entity instanceof CraftEntity craftEntity ? craftEntity.getHandle() : null;
  }

  static ServerLevel level(World world) {
    return world instanceof CraftWorld craftWorld ? craftWorld.getHandle() : null;
  }

  static ItemStack item(org.bukkit.inventory.ItemStack stack) {
    return CraftItemStack.asNMSCopy(stack);
  }

  static EquipmentSlot slot(org.bukkit.inventory.EquipmentSlot slot) {
    return CraftEquipmentSlot.getNMS(slot);
  }

  /**
   * Movement since the player's last level tick. xOld is set only there and by teleports, while xo is also reset by
   * every accepted move packet (absMoveTo), so position - xo is zero between ticks.
   */
  static Vec3 knownMovement(ServerPlayer player) {
    return new Vec3(player.getX() - player.xOld, player.getY() - player.yOld, player.getZ() - player.zOld);
  }
}
