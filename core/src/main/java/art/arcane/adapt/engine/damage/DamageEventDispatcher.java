package art.arcane.adapt.engine.damage;

import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.content.DamageDeferral;
import art.arcane.adapt.api.content.DeathSave;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventListener;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Runs every HIGHEST damage handler of Adapt components in one fixed order: ongoing death-save protection, plain
 * modifiers, {@link DamageDeferral}s, protection again, then {@link DeathSave}s by rank while the hit is still
 * lethal. The {@link art.arcane.adapt.engine.ComponentRegistrar} subscribes it to the bus once at HIGHEST.
 */
public final class DamageEventDispatcher {
  private static final Snapshot EMPTY = new Snapshot(new Handler[0], new Handler[0], new Handler[0]);
  private final List<Handler> handlers = new ArrayList<>();
  private volatile Snapshot snapshot = EMPTY;

  public DamageEventDispatcher() {
  }

  /** Registers a handler whose role comes from the owner: a {@link DeathSave}, a {@link DamageDeferral} or a modifier. */
  public void register(Object owner, Class<? extends EntityDamageEvent> eventType,
                       EventListener<? super EntityDamageEvent> listener, boolean ignoreCancelled) {
    register(owner, owner instanceof DeathSave save ? save : null, owner instanceof DamageDeferral,
        eventType, listener, ignoreCancelled);
  }

  /**
   * Registers a handler with an explicit role, for owners that act as a death save or deferral through an adapter
   * rather than by implementing the interfaces themselves.
   */
  public synchronized void register(Object owner, DeathSave deathSave, boolean deferral,
                                    Class<? extends EntityDamageEvent> eventType,
                                    EventListener<? super EntityDamageEvent> listener, boolean ignoreCancelled) {
    handlers.add(new Handler(owner, deathSave, deferral, eventType, listener, ignoreCancelled));
    rebuildSnapshot();
  }

  public synchronized void unregister(Object owner) {
    handlers.removeIf(handler -> {
      if (handler.owner != owner) {
        return false;
      }
      handler.active = false;
      return true;
    });
    rebuildSnapshot();
  }

  public synchronized void clear() {
    for (Handler handler : handlers) {
      handler.active = false;
    }
    handlers.clear();
    snapshot = EMPTY;
  }

  public boolean isEmpty() {
    Snapshot current = snapshot;
    return current.modifiers.length == 0 && current.deferrals.length == 0 && current.saves.length == 0;
  }

  public void onDamage(EntityDamageEvent event) {
    Snapshot current = snapshot;
    protectFromDamage(current.saves, event);
    invokeAll(current.modifiers, event);
    invokeAll(current.deferrals, event);
    protectFromDamage(current.saves, event);
    if (!(event.entity() instanceof PlayerRef player) || !isLethal(event, player)) {
      return;
    }

    for (int rank = 0; rank <= 2; rank++) {
      for (Handler handler : current.saves) {
        if (!handler.accepts(event)) {
          continue;
        }
        try {
          if (handler.deathSave.deathSavePriority(player).ordinal() != rank) {
            continue;
          }
        } catch (Throwable error) {
          logFailure(handler, "death-save priority", error);
          continue;
        }
        invoke(handler, event);
        if (!isLethal(event, player)) {
          return;
        }
      }
    }
  }

  private void rebuildSnapshot() {
    List<Handler> modifiers = new ArrayList<>();
    List<Handler> deferrals = new ArrayList<>();
    List<Handler> saves = new ArrayList<>();
    for (Handler handler : handlers) {
      if (handler.deathSave != null) {
        saves.add(handler);
      } else if (handler.deferral) {
        deferrals.add(handler);
      } else {
        modifiers.add(handler);
      }
    }
    saves.sort(Comparator.comparingInt((Handler handler) -> handler.deathSave.defaultOrder())
        .thenComparing(handler -> handler.owner.getClass().getName()));
    snapshot = new Snapshot(modifiers.toArray(Handler[]::new), deferrals.toArray(Handler[]::new),
        saves.toArray(Handler[]::new));
  }

  private void protectFromDamage(Handler[] saves, EntityDamageEvent event) {
    if (event.isCancelled() || !(event.entity() instanceof PlayerRef)) {
      return;
    }
    for (Handler handler : saves) {
      if (!handler.accepts(event)) {
        continue;
      }
      try {
        if (handler.deathSave.protectsFromDamage(event)) {
          event.setCancelled(true);
          return;
        }
      } catch (Throwable error) {
        logFailure(handler, "ongoing death-save protection", error);
      }
    }
  }

  private void invokeAll(Handler[] entries, EntityDamageEvent event) {
    for (Handler handler : entries) {
      invoke(handler, event);
    }
  }

  private void invoke(Handler handler, EntityDamageEvent event) {
    if (!handler.accepts(event)) {
      return;
    }
    try {
      handler.listener.handle(event);
    } catch (Throwable error) {
      logFailure(handler, "damage handler", error);
    }
  }

  private void logFailure(Handler handler, String operation, Throwable error) {
    AdaptLog.error("Failed " + operation + " for " + handler.owner.getClass().getName(), error);
  }

  private static boolean isLethal(EntityDamageEvent event, PlayerRef player) {
    return !event.isCancelled() && !player.isDead() && player.health() - event.finalDamage() <= 0;
  }

  private record Snapshot(Handler[] modifiers, Handler[] deferrals, Handler[] saves) {
  }

  private static final class Handler {
    private final Object owner;
    private final DeathSave deathSave;
    private final boolean deferral;
    private final Class<? extends EntityDamageEvent> eventType;
    private final EventListener<? super EntityDamageEvent> listener;
    private final boolean ignoreCancelled;
    private volatile boolean active = true;

    private Handler(Object owner, DeathSave deathSave, boolean deferral,
                    Class<? extends EntityDamageEvent> eventType, EventListener<? super EntityDamageEvent> listener,
                    boolean ignoreCancelled) {
      this.owner = owner;
      this.deathSave = deathSave;
      this.deferral = deferral;
      this.eventType = eventType;
      this.listener = listener;
      this.ignoreCancelled = ignoreCancelled;
    }

    private boolean accepts(EntityDamageEvent event) {
      return active && eventType.isInstance(event) && (!ignoreCancelled || !event.isCancelled());
    }
  }
}
