package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.Adapt;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PlayerStateRegistry {
  private static final List<Map<UUID, ?>> MAPS = new CopyOnWriteArrayList<>();
  private static final AtomicBoolean LISTENER_REGISTERED = new AtomicBoolean(false);

  private PlayerStateRegistry() {
  }

  public static <V> Map<UUID, V> newPlayerMap() {
    ensureListener();
    ConcurrentHashMap<UUID, V> map = new ConcurrentHashMap<>();
    MAPS.add(map);
    return map;
  }

  public static Cooldowns newCooldowns() {
    return new Cooldowns(newPlayerMap());
  }

  public static void reset() {
    MAPS.clear();
    LISTENER_REGISTERED.set(false);
  }

  private static void ensureListener() {
    if (!LISTENER_REGISTERED.compareAndSet(false, true)) {
      return;
    }

    Adapt plugin = Adapt.instance;
    if (plugin == null) {
      LISTENER_REGISTERED.set(false);
      return;
    }

    plugin.registerListener(new QuitListener());
  }

  private static void clearPlayer(UUID id) {
    for (Map<UUID, ?> map : MAPS) {
      map.remove(id);
    }
  }

  public static final class QuitListener implements Listener {
    @EventHandler
    public void on(PlayerQuitEvent e) {
      clearPlayer(e.getPlayer().getUniqueId());
    }
  }
}
