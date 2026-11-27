package art.arcane.adapt.bukkit.platform.entity;

import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.concurrent.CompletableFuture;

/** Teleports that keep passengers through Paper's teleport flags. Loaded only on Paper. */
final class PaperTeleports {
  private PaperTeleports() {
  }

  static CompletableFuture<Boolean> teleportRetainingPassengers(Entity entity, Location destination,
                                                                PlayerTeleportEvent.TeleportCause cause) {
    return entity.teleportAsync(destination, cause, TeleportFlag.EntityState.RETAIN_PASSENGERS);
  }
}
