package art.arcane.adapt.bukkit.platform.entity;

import art.arcane.adapt.bukkit.natives.ServerNatives;
import art.arcane.adapt.bukkit.platform.BukkitConversions;
import art.arcane.adapt.bukkit.platform.BukkitResolve;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Vec3;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

final class BukkitPlayerVersion {
  private BukkitPlayerVersion() {
  }

  static int cooldownTicks(Player player, Id group) {
    Material material = BukkitResolve.material(group);
    return material == null ? 0 : player.getCooldown(material);
  }

  static void setCooldown(Player player, Id group, int ticks) {
    Material material = BukkitResolve.material(group);
    if (material != null) {
      player.setCooldown(material, ticks);
    }
  }

  @SuppressWarnings("deprecation")
  static Location respawnLocation(Player player) {
    return player.getBedSpawnLocation();
  }

  /** Client movement over the last tick: these servers get no movement keys, and a player's velocity omits walking. */
  static Vec3 movement(Player player) {
    Vector movement = ServerNatives.movement(player);
    return movement == null ? Vec3.ZERO : BukkitConversions.vec(movement);
  }
}
