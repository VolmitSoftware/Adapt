package art.arcane.adapt.bukkit.platform.entity;

import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.concurrent.CompletableFuture;

/** Paper 1.20.1 keeps passengers only on the synchronous teleport, which completes before this returns. */
final class BukkitTeleportVersion {
  private BukkitTeleportVersion() {
  }

  static CompletableFuture<Boolean> teleportRetainingPassengers(Entity entity, Location destination,
                                                                PlayerTeleportEvent.TeleportCause cause) {
    return CompletableFuture.completedFuture(entity.teleport(destination, cause, TeleportFlag.EntityState.RETAIN_PASSENGERS));
  }
}
