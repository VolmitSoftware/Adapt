package art.arcane.adapt.bukkit.platform;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.bukkit.event.BukkitEventBridge;
import art.arcane.adapt.bukkit.event.RegionRespawns;
import art.arcane.adapt.bukkit.platform.schedule.BukkitScheduler;
import art.arcane.adapt.bukkit.platform.schedule.JSchedulerBackend;
import art.arcane.adapt.api.internal.HostPlatform;
import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.platform.PlatformHost;
import art.arcane.adapt.platform.PlatformLog;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.event.AdaptEventBus;
import art.arcane.adapt.api.internal.HostPermissions;
import art.arcane.adapt.util.common.compat.PaperApi;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Process-wide access to the Bukkit platform. {@link #bus()} and {@link #log()} live for the whole
 * class lifetime; everything else is null outside {@link #start}/{@link #stop}. {@link #platform()}
 * stays null while a host from {@link BukkitHosts} is missing, as on modded.
 */
public final class BukkitPlatform {
  private static final PlatformLog LOG = new BukkitLog();
  private static final AdaptEventBus BUS = new AdaptEventBus((Object owner, AdaptEvent event, Throwable error) ->
      LOG.error("Event handler " + owner + " failed on " + (event == null ? "registration" : event.getClass().getSimpleName()), error));

  private static volatile Plugin plugin;
  private static volatile BukkitScheduler scheduler;
  private static volatile BukkitPlatformHost host;
  private static volatile HostPermissions permissions;
  private static volatile HostPlatform platform;
  private static volatile Listener lifecycle;
  private static volatile Listener removals;
  private static volatile Listener respawns;

  private BukkitPlatform() {
  }

  /** The assembled platform, or null before start, after stop, or while a registered host is missing. */
  public static HostPlatform platform() {
    return platform;
  }

  public static AdaptEventBus bus() {
    return BUS;
  }

  public static PlatformLog log() {
    return LOG;
  }

  public static Plugin plugin() {
    Plugin current = plugin;
    return current != null ? current : Adapt.instance;
  }

  public static AdaptScheduler scheduler() {
    return scheduler;
  }

  public static PlatformHost host() {
    return host;
  }

  public static HostPermissions permissions() {
    return permissions;
  }

  /**
   * Main thread, during plugin enable. Builds the base hosts, installs the event bridge and assembles the platform. Never throws:
   * a failure is logged and leaves the platform unset, so plugin startup continues without it.
   */
  public static HostPlatform start(Plugin owner) {
    try {
      return startUnchecked(owner);
    } catch (RuntimeException | LinkageError failure) {
      LOG.error("Platform failed to start", failure);
      platform = null;
      return null;
    }
  }

  private static HostPlatform startUnchecked(Plugin owner) {
    if (plugin != null) {
      stop();
    }
    plugin = owner;
    BukkitScheduler createdScheduler = new BukkitScheduler(new JSchedulerBackend(), LOG);
    scheduler = createdScheduler;
    permissions = new BukkitPermissionHost(() -> owner.getServer().getPluginManager());
    host = new BukkitPlatformHost(owner.getServer(), owner, LOG, createdScheduler);
    Listener listener = new Lifecycle();
    owner.getServer().getPluginManager().registerEvents(listener, owner);
    lifecycle = listener;
    Listener entityRemovals = PaperApi.isPaper() ? new PaperRefRelease() : new Removals();
    owner.getServer().getPluginManager().registerEvents(entityRemovals, owner);
    removals = entityRemovals;
    BukkitEventBridge.install(owner, BUS);
    if (createdScheduler.isRegionized()) {
      Listener regionRespawns = RegionRespawns.create(BUS);
      owner.getServer().getPluginManager().registerEvents(regionRespawns, owner);
      respawns = regionRespawns;
    }
    HostPlatform assembled = assemble();
    platform = assembled;
    if (assembled != null) {
      for (Consumer<HostPlatform> started : BukkitHosts.startedListeners()) {
        try {
          started.accept(assembled);
        } catch (RuntimeException failure) {
          LOG.error("Platform start listener failed", failure);
        }
      }
    }
    return assembled;
  }

  /** Main thread, during plugin disable. */
  public static void stop() {
    if (plugin == null) {
      return;
    }
    for (Runnable stopping : BukkitHosts.stoppingListeners()) {
      try {
        stopping.run();
      } catch (RuntimeException failure) {
        LOG.error("Platform stop listener failed", failure);
      }
    }
    BukkitEventBridge.uninstall();
    Listener listener = lifecycle;
    if (listener != null) {
      HandlerList.unregisterAll(listener);
    }
    Listener entityRemovals = removals;
    if (entityRemovals != null) {
      HandlerList.unregisterAll(entityRemovals);
    }
    removals = null;
    Listener regionRespawns = respawns;
    if (regionRespawns != null) {
      HandlerList.unregisterAll(regionRespawns);
    }
    respawns = null;
    BukkitScheduler current = scheduler;
    if (current != null) {
      current.cancelAll();
    }
    lifecycle = null;
    platform = null;
    host = null;
    permissions = null;
    scheduler = null;
    plugin = null;
    BukkitRefs.clear();
  }

  private static HostPlatform assemble() {
    List<String> missing = new ArrayList<>();
    if (BukkitHosts.fx() == null) {
      missing.add("fx");
    }
    if (BukkitHosts.viewers() == null) {
      missing.add("viewers");
    }
    if (BukkitHosts.menus() == null) {
      missing.add("menus");
    }
    if (BukkitHosts.protection() == null) {
      missing.add("protection");
    }
    if (BukkitHosts.recipes() == null) {
      missing.add("recipes");
    }
    if (BukkitHosts.advancements() == null) {
      missing.add("advancements");
    }
    if (!missing.isEmpty()) {
      LOG.verbose("Platform incomplete, missing hosts: " + String.join(", ", missing));
      return null;
    }
    return new HostPlatform(host, scheduler, BukkitHosts.fx(), BukkitHosts.viewers(), BukkitHosts.menus(), permissions,
        BukkitHosts.protection(), BukkitHosts.recipes(), BukkitHosts.advancements());
  }

  public static final class Lifecycle implements Listener {
    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerQuitEvent event) {
      Player player = event.getPlayer();
      UUID id = player.getUniqueId();
      BukkitScheduler current = scheduler;
      if (current == null || !current.runGlobal(() -> BukkitRefs.forget(id), 1L)) {
        BukkitRefs.forget(id);
      }
    }
  }

  /** Spigot has no world-removal event, so cached refs are released when the entity leaves for good or unloads. */
  public static final class Removals implements Listener {
    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityRemoveEvent event) {
      if (!(event.getEntity() instanceof Player)) {
        BukkitRefs.forget(event.getEntity());
      }
    }
  }
}
