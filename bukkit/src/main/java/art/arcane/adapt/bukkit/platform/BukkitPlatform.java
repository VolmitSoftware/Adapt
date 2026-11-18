package art.arcane.adapt.bukkit.platform;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.bukkit.event.BukkitEventBridge;
import art.arcane.adapt.bukkit.event.RegionRespawns;
import art.arcane.adapt.bukkit.platform.schedule.BukkitScheduler;
import art.arcane.adapt.bukkit.platform.schedule.JSchedulerBackend;
import art.arcane.adapt.platform.AdaptPlatform;
import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.platform.PlatformHost;
import art.arcane.adapt.platform.PlatformLog;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.event.AdaptEventBus;
import art.arcane.adapt.platform.permission.PermissionHost;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
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
      LOG.error("Event handler " + owner + " failed on " + event.getClass().getSimpleName(), error));

  private static volatile Plugin plugin;
  private static volatile BukkitScheduler scheduler;
  private static volatile BukkitPlatformHost host;
  private static volatile PermissionHost permissions;
  private static volatile AdaptPlatform platform;
  private static volatile Listener lifecycle;
  private static volatile Listener respawns;

  private BukkitPlatform() {
  }

  /** The assembled platform, or null before start, after stop, or while a registered host is missing. */
  public static AdaptPlatform platform() {
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

  public static PermissionHost permissions() {
    return permissions;
  }

  /**
   * Main thread, during plugin enable. Builds the base hosts, installs the event bridge and assembles the platform. Never throws:
   * a failure is logged and leaves the platform unset, so plugin startup continues without it.
   */
  public static AdaptPlatform start(Plugin owner) {
    try {
      return startUnchecked(owner);
    } catch (RuntimeException | LinkageError failure) {
      LOG.error("Platform failed to start", failure);
      platform = null;
      return null;
    }
  }

  private static AdaptPlatform startUnchecked(Plugin owner) {
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
    BukkitEventBridge.install(owner, BUS);
    if (createdScheduler.isRegionized()) {
      Listener regionRespawns = RegionRespawns.create(BUS);
      owner.getServer().getPluginManager().registerEvents(regionRespawns, owner);
      respawns = regionRespawns;
    }
    AdaptPlatform assembled = assemble();
    platform = assembled;
    if (assembled != null) {
      for (Consumer<AdaptPlatform> started : BukkitHosts.startedListeners()) {
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

  private static AdaptPlatform assemble() {
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
    return new AdaptPlatform(host, scheduler, BukkitHosts.fx(), BukkitHosts.viewers(), BukkitHosts.menus(), permissions,
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityRemoveFromWorldEvent event) {
      if (!(event.getEntity() instanceof Player)) {
        BukkitRefs.forget(event.getEntity());
      }
    }
  }
}
