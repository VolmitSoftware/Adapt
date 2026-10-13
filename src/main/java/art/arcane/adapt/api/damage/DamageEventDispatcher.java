package art.arcane.adapt.api.damage;

import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;

public final class DamageEventDispatcher implements Listener {
    private static final Snapshot EMPTY = new Snapshot(new Handler[0], new Handler[0], new Handler[0]);
    private final Plugin plugin;
    private final List<Handler> handlers = new ArrayList<>();
    private volatile Snapshot snapshot = EMPTY;

    public DamageEventDispatcher(Plugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void register(Listener listener, Class<? extends EntityDamageEvent> eventType,
                                      EventExecutor executor, boolean ignoreCancelled) {
        handlers.add(new Handler(listener, eventType, executor, ignoreCancelled));
        rebuildSnapshot();
    }

    public synchronized void unregister(Listener listener) {
        handlers.removeIf(handler -> {
            if (handler.listener != listener) {
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

    public void onDamage(EntityDamageEvent event) {
        Snapshot current = snapshot;
        protectFromDamage(current.saves, event);
        invokeAll(current.modifiers, event);
        invokeAll(current.deferrals, event);
        protectFromDamage(current.saves, event);
        if (!(event.getEntity() instanceof Player player) || !isLethal(event, player)) {
            return;
        }

        for (int rank = 0; rank <= 2; rank++) {
            for (Handler handler : current.saves) {
                if (!handler.accepts(event)) {
                    continue;
                }
                try {
                    if (Math.clamp(handler.deathSave.deathSavePriority(player), 0, 2) != rank) {
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
            } else if (handler.listener instanceof DamageDeferral) {
                deferrals.add(handler);
            } else {
                modifiers.add(handler);
            }
        }
        saves.sort(Comparator.comparingInt((Handler handler) -> handler.deathSave.defaultOrder())
                .thenComparing(handler -> handler.listener.getClass().getName()));
        snapshot = new Snapshot(modifiers.toArray(Handler[]::new), deferrals.toArray(Handler[]::new),
                saves.toArray(Handler[]::new));
    }

    private void protectFromDamage(Handler[] saves, EntityDamageEvent event) {
        if (event.isCancelled() || !(event.getEntity() instanceof Player)) {
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
            handler.executor.execute(handler.listener, event);
        } catch (Throwable error) {
            logFailure(handler, "damage handler", error);
        }
    }

    private void logFailure(Handler handler, String operation, Throwable error) {
        plugin.getLogger().log(Level.SEVERE,
                "Failed " + operation + " for " + handler.listener.getClass().getName(), error);
    }

    private static boolean isLethal(EntityDamageEvent event, Player player) {
        return !event.isCancelled() && !player.isDead() && player.getHealth() - event.getFinalDamage() <= 0;
    }

    private record Snapshot(Handler[] modifiers, Handler[] deferrals, Handler[] saves) {
    }

    private static final class Handler {
        private final Listener listener;
        private final Class<? extends EntityDamageEvent> eventType;
        private final EventExecutor executor;
        private final boolean ignoreCancelled;
        private final PlayerDeathSave deathSave;
        private volatile boolean active = true;

        private Handler(Listener listener, Class<? extends EntityDamageEvent> eventType,
                        EventExecutor executor, boolean ignoreCancelled) {
            this.listener = listener;
            this.eventType = eventType;
            this.executor = executor;
            this.ignoreCancelled = ignoreCancelled;
            this.deathSave = listener instanceof PlayerDeathSave save ? save : null;
        }

        private boolean accepts(EntityDamageEvent event) {
            return active && eventType.isInstance(event) && (!ignoreCancelled || !event.isCancelled());
        }
    }
}
