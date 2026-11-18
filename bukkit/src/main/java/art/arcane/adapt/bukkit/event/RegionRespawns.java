package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.AdaptEventBus;
import art.arcane.adapt.platform.event.player.PlayerRespawnEvent;
import art.arcane.adapt.platform.value.Loc;
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Folia respawns players asynchronously and never fires the player respawn event. On a regionized server a player
 * who died is announced as respawned when it is placed back into a world alive, on its own region, with the
 * relocation semantics the Bukkit bridge gives on Paper. Bed and anchor spawns cannot be told apart there.
 */
public final class RegionRespawns implements Listener {
  private final AdaptEventBus bus;
  private final Set<UUID> awaiting = ConcurrentHashMap.newKeySet();

  RegionRespawns(AdaptEventBus bus) {
    this.bus = Objects.requireNonNull(bus, "bus");
  }

  public static RegionRespawns create(AdaptEventBus bus) {
    return new RegionRespawns(bus);
  }

  void died(UUID playerId) {
    awaiting.add(playerId);
  }

  void forget(UUID playerId) {
    awaiting.remove(playerId);
  }

  void placed(PlayerRef player, boolean alive, Loc location, Consumer<Loc> relocate) {
    if (!alive || !awaiting.remove(player.uuid())) {
      return;
    }
    PlayerRespawnEvent event = new PlayerRespawnEvent(player, location, false, false);
    bus.post(event);
    if (!event.respawnLocation().equals(location)) {
      relocate.accept(event.respawnLocation());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerDeathEvent event) {
    died(event.getPlayer().getUniqueId());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent event) {
    forget(event.getPlayer().getUniqueId());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntityAddToWorldEvent event) {
    if (!(event.getEntity() instanceof Player player)) {
      return;
    }
    placed(BukkitRefs.player(player), !player.isDead(), BukkitRefs.loc(player.getLocation()),
        (Loc target) -> player.teleportAsync(BukkitRefs.location(target)));
  }
}
