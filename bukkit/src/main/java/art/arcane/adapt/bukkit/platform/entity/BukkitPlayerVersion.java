package art.arcane.adapt.bukkit.platform.entity;

import art.arcane.adapt.bukkit.platform.BukkitConversions;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.util.common.compat.PaperApi;
import org.bukkit.Location;
import org.bukkit.entity.Player;

final class BukkitPlayerVersion {
  private BukkitPlayerVersion() {
  }

  static int cooldownTicks(Player player, Id group) {
    return PaperApi.cooldown(player, BukkitConversions.key(group));
  }

  static void setCooldown(Player player, Id group, int ticks) {
    PaperApi.setCooldown(player, BukkitConversions.key(group), ticks);
  }

  static Location respawnLocation(Player player) {
    return player.getRespawnLocation();
  }

  static Vec3 movement(Player player) {
    return BukkitConversions.vec(player.getVelocity());
  }
}
