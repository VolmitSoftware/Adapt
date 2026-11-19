package art.arcane.adapt.bukkit.natives;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftEquipmentSlot;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;

/** CraftBukkit handles on Paper 1.21.1, where CraftBukkit is not relocated. */
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

  static Vec3 knownMovement(ServerPlayer player) {
    return player.getKnownMovement();
  }
}
