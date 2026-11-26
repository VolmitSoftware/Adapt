package art.arcane.adapt.bukkit.feature.recipe;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.inventory.Recipe;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.util.Iterator;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Stands in for the recipe map identity that {@code MaterialRecipeIndex} keys on, since Bukkit exposes none. The
 * token changes on Paper's resource reload, a plugin enabling or disabling, a server reload, or Adapt registering
 * its own recipes. Servers without Paper's reload event recount the recipes at most every five minutes instead and
 * change the token when the count moved.
 */
public final class BukkitRecipeRegistry {
  static final long RECOUNT_MILLIS = 300_000L;
  private static final String RELOAD_EVENT = "io.papermc.paper.event.server.ServerResourcesReloadedEvent";
  private static final BukkitRecipeRegistry SHARED = new BukkitRecipeRegistry(Bukkit::recipeIterator, reloadEvent(), System::currentTimeMillis);

  private final Supplier<Iterator<Recipe>> source;
  private final Class<? extends Event> reloadEvent;
  private final LongSupplier clock;
  private volatile Object generation = new Object();
  private long countedAt;
  private int count = -1;

  /** @param reloadEvent the server's resource reload event, or null to fall back to recounting */
  public BukkitRecipeRegistry(Supplier<Iterator<Recipe>> source, Class<? extends Event> reloadEvent, LongSupplier clock) {
    this.source = source;
    this.reloadEvent = reloadEvent;
    this.clock = clock;
  }

  public static BukkitRecipeRegistry shared() {
    return SHARED;
  }

  public Object generation() {
    if (reloadEvent == null) {
      recountIfDue();
    }
    return generation;
  }

  public Iterable<Recipe> recipes() {
    return source::get;
  }

  public synchronized void invalidate() {
    generation = new Object();
    count = -1;
  }

  public void listen(Plugin plugin) {
    PluginManager manager = plugin.getServer().getPluginManager();
    Listener owner = new Listener() {
    };
    EventExecutor reset = (Listener listener, Event event) -> invalidate();
    manager.registerEvent(PluginEnableEvent.class, owner, EventPriority.MONITOR, reset, plugin);
    manager.registerEvent(PluginDisableEvent.class, owner, EventPriority.MONITOR, reset, plugin);
    manager.registerEvent(ServerLoadEvent.class, owner, EventPriority.MONITOR, reset, plugin);
    if (reloadEvent != null) {
      manager.registerEvent(reloadEvent, owner, EventPriority.MONITOR, reset, plugin);
    }
  }

  private synchronized void recountIfDue() {
    long now = clock.getAsLong();
    if (count >= 0 && now - countedAt < RECOUNT_MILLIS) {
      return;
    }
    int counted = 0;
    for (Iterator<Recipe> recipes = source.get(); recipes.hasNext(); recipes.next()) {
      counted++;
    }
    if (count >= 0 && counted != count) {
      generation = new Object();
    }
    count = counted;
    countedAt = now;
  }

  private static Class<? extends Event> reloadEvent() {
    try {
      return Class.forName(RELOAD_EVENT).asSubclass(Event.class);
    } catch (ClassNotFoundException | LinkageError missing) {
      return null;
    }
  }
}
