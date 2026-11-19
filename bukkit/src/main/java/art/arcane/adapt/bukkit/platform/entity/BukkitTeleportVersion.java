package art.arcane.adapt.bukkit.platform.entity;

import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.concurrent.CompletableFuture;

/**
 * Keeps every TeleportFlag reference out of BukkitEntityRef's constant pool so that class still verifies on Spigot,
 * where the type does not exist. Only the probe-guarded call site loads this class.
 */
final class BukkitTeleportVersion {
  private BukkitTeleportVersion() {
  }

  static CompletableFuture<Boolean> teleportRetainingPassengers(Entity entity, Location destination,
                                                                PlayerTeleportEvent.TeleportCause cause) {
    return entity.teleportAsync(destination, cause, TeleportFlag.EntityState.RETAIN_PASSENGERS);
  }
}
