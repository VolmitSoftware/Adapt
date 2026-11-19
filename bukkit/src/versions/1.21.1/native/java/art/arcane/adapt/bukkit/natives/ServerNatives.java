package art.arcane.adapt.bukkit.natives;

import art.arcane.volmlib.nativelib.chunk.GeneratedChunkStructures;
import art.arcane.volmlib.nativelib.entity.EntityGlowAccess;
import art.arcane.volmlib.nativelib.entity.VirtualPlayerAccess;
import art.arcane.volmlib.nativelib.player.PlayerClientAccess;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Map;

/**
 * The VolmLib native capabilities this jar implements itself against its one Paper version (VolmLib's own native
 * adapters cover 26.x servers only).
 */
public final class ServerNatives {
  private ServerNatives() {
  }

  /** A new implementation of a capability, or null when this jar has none. */
  public static <T> T find(Class<T> capability) {
    if (capability == EntityGlowAccess.class) {
      return capability.cast(new ServerEntityGlow());
    }
    if (capability == VirtualPlayerAccess.class) {
      return capability.cast(new ServerVirtualPlayers());
    }
    if (capability == PlayerClientAccess.class) {
      return capability.cast(new ServerPlayerClient());
    }
    if (capability == GeneratedChunkStructures.class) {
      return capability.cast(new ServerChunkStructures());
    }
    return null;
  }

  public static boolean isServerPlayer(Player player) {
    return player != null && ServerHandles.isServerPlayerClass(player.getClass());
  }

  /** Movement of the player over the last tick as the server accepted it from the client, or null for a non-server player. */
  public static Vector movement(Player player) {
    ServerPlayer handle = ServerHandles.player(player);
    if (handle == null) {
      return null;
    }
    net.minecraft.world.phys.Vec3 known = ServerHandles.knownMovement(handle);
    return new Vector(known.x, known.y, known.z);
  }

  /**
   * Sets an entity's position without a teleport: the velocity and the tick state stay as they are (a Bukkit teleport of
   * a projectile in the middle of its tick is not applied and clears its velocity). False for a non-server entity.
   */
  public static boolean place(org.bukkit.entity.Entity entity, double x, double y, double z) {
    net.minecraft.world.entity.Entity handle = ServerHandles.entity(entity);
    if (handle == null) {
      return false;
    }
    handle.setPos(x, y, z);
    return true;
  }

  /** Equipment shown to one viewer for an entity id, including ids the server has no entity for. */
  public static boolean sendEquipment(Player viewer, int entityId, Map<EquipmentSlot, ItemStack> items) {
    ServerPlayer player = ServerHandles.player(viewer);
    ClientboundSetEquipmentPacket packet = ServerVirtualPlayers.equipmentPacket(entityId, items);
    if (player == null || player.connection == null || packet == null) {
      return false;
    }
    player.connection.send(packet);
    return true;
  }
}
