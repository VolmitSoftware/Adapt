package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PlayerStateRegistry {
  private static final Queue<WeakReference<Map<UUID, ?>>> MAPS = new ConcurrentLinkedQueue<>();
  private static final ReferenceQueue<Map<UUID, ?>> COLLECTED_MAPS = new ReferenceQueue<>();
  private static final AtomicBoolean LISTENER_REGISTERED = new AtomicBoolean(false);
  private static volatile Adapt listenerPlugin;
  private static volatile QuitListener quitListener;

  private PlayerStateRegistry() {
  }

  public static <V> Map<UUID, V> newPlayerMap() {
    ensureListener();
    ConcurrentHashMap<UUID, V> map = new ConcurrentHashMap<>();
    pruneCollectedMaps();
    MAPS.add(new WeakReference<>(map, COLLECTED_MAPS));
    return map;
  }

  public static Cooldowns newCooldowns() {
    return new Cooldowns(newPlayerMap());
  }

  public static synchronized void reset() {
    MAPS.clear();
    while (COLLECTED_MAPS.poll() != null) {
    }
    Adapt plugin = listenerPlugin;
    QuitListener listener = quitListener;
    if (plugin != null && listener != null) {
      plugin.unregisterListener(listener);
    }
    listenerPlugin = null;
    quitListener = null;
    LISTENER_REGISTERED.set(false);
  }

  private static synchronized void ensureListener() {
    if (!LISTENER_REGISTERED.compareAndSet(false, true)) {
      return;
    }

    Adapt plugin = Adapt.instance;
    if (plugin == null) {
      LISTENER_REGISTERED.set(false);
      return;
    }

    QuitListener listener = new QuitListener();
    listenerPlugin = plugin;
    quitListener = listener;
    plugin.registerListener(listener);
  }

  static void clearPlayer(UUID id) {
    pruneCollectedMaps();
    for (WeakReference<Map<UUID, ?>> reference : MAPS) {
      Map<UUID, ?> map = reference.get();
      if (map != null) {
        map.remove(id);
      }
    }
  }

  private static void pruneCollectedMaps() {
    Reference<? extends Map<UUID, ?>> reference;
    while ((reference = COLLECTED_MAPS.poll()) != null) {
      MAPS.remove(reference);
    }
  }

  public static final class QuitListener implements Listener {
    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerQuitEvent e) {
      UUID playerId = e.getPlayer().getUniqueId();
      J.s(() -> {
        Player current = Bukkit.getPlayer(playerId);
        if (current == null || !current.isOnline()) {
          clearPlayer(playerId);
        }
      }, 1);
    }
  }
}
