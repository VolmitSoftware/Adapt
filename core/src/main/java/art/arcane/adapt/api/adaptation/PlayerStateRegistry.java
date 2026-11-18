package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.ComponentRegistrar;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.util.common.scheduling.Tasks;

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
  private static volatile ComponentRegistrar listenerRegistrar;
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

  public static void start() {
    ensureListener();
  }

  public static synchronized void reset() {
    pruneCollectedMaps();
    for (WeakReference<Map<UUID, ?>> reference : MAPS) {
      Map<UUID, ?> map = reference.get();
      if (map != null) {
        map.clear();
      }
    }
    ComponentRegistrar registrar = listenerRegistrar;
    QuitListener listener = quitListener;
    if (registrar != null && listener != null) {
      registrar.unregister(listener);
    }
    listenerRegistrar = null;
    quitListener = null;
    LISTENER_REGISTERED.set(false);
  }

  private static synchronized void ensureListener() {
    if (!LISTENER_REGISTERED.compareAndSet(false, true)) {
      return;
    }

    AdaptCore core = AdaptCore.current();
    if (core == null) {
      LISTENER_REGISTERED.set(false);
      return;
    }

    QuitListener listener = new QuitListener();
    ComponentRegistrar registrar = core.getRegistrar();
    listenerRegistrar = registrar;
    quitListener = listener;
    registrar.register(listener);
  }

  public static void clearPlayer(UUID id) {
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

  public static final class QuitListener {
    @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void on(PlayerQuitEvent e) {
      UUID playerId = e.player().uuid();
      Tasks.s(() -> {
        AdaptCore core = AdaptCore.current();
        AdaptServer server = core == null ? null : core.getAdaptServer();
        AdaptPlayer current = server == null ? null : server.getOnlineAdaptPlayer(playerId);
        if (current == null || !current.isRuntimeReady()) {
          clearPlayer(playerId);
        }
      }, 1);
    }
  }
}
