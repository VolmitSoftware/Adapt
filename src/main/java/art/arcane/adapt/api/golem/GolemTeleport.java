package art.arcane.adapt.api.golem;

import art.arcane.adapt.util.common.compat.PaperCompat;
import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class GolemTeleport {
  private static final String TELEPORT_FLAG_CLASS = "io.papermc.paper.entity.TeleportFlag";
  private static volatile boolean teleportFlagsSupported = true;

  private GolemTeleport() {
  }

  public static CompletableFuture<Boolean> teleport(GolemHandle handle, Location destination) {
    Mob golem = handle.entity();
    if (crossesWorlds(golem.getWorld(), destination.getWorld())) {
      GolemancyRuntime.reveal().ifPresent(reveal -> reveal.forget(handle));
    }
    if (teleportFlagsSupported && PaperCompat.hasClass(TELEPORT_FLAG_CLASS)) {
      try {
        return TeleportFlagBridge.teleportRetainingPassengers(golem, destination);
      } catch (NoSuchMethodError | NoClassDefFoundError absent) {
        teleportFlagsSupported = false;
      }
    }
    return teleportRemountingPassengers(golem, destination);
  }

  static CompletableFuture<Boolean> teleportRemountingPassengers(Entity entity, Location destination) {
    List<Entity> passengers = List.copyOf(entity.getPassengers());
    for (Entity passenger : passengers) {
      entity.removePassenger(passenger);
    }
    boolean moved = entity.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
    for (Entity passenger : passengers) {
      if (moved) {
        passenger.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
      }
      entity.addPassenger(passenger);
    }
    return CompletableFuture.completedFuture(moved);
  }

  private static boolean crossesWorlds(World from, World to) {
    return from != null && to != null && !from.getUID().equals(to.getUID());
  }

  private static final class TeleportFlagBridge {
    private TeleportFlagBridge() {
    }

    private static CompletableFuture<Boolean> teleportRetainingPassengers(Entity entity, Location destination) {
      return entity.teleportAsync(destination, PlayerTeleportEvent.TeleportCause.PLUGIN, TeleportFlag.EntityState.RETAIN_PASSENGERS);
    }
  }
}
