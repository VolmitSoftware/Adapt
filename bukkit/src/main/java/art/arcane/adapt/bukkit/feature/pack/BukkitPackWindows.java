package art.arcane.adapt.bukkit.feature.pack;

import art.arcane.adapt.bukkit.canvas.Canvases;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.pack.surface.PackSession;
import art.arcane.adapt.pack.surface.PackSupport;
import art.arcane.adapt.pack.surface.PackSurface;
import art.arcane.adapt.pack.surface.PackWindows;
import art.arcane.adapt.pack.surface.TitleRun;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Pack windows on Paper, Folia and Spigot 26.x. Where the server canvas loads, pages take the 36 player slots and
 * change title in place; elsewhere they are plain chests over the live inventory that reopen for a new title.
 */
public final class BukkitPackWindows implements PackWindows {
  private static final long NANOS_PER_TICK = 50_000_000L;

  private final JavaPlugin plugin;
  private final NativeCanvases natives;
  private final Map<UUID, BukkitPackSession> sessions = new ConcurrentHashMap<>();

  private BukkitPackWindows(JavaPlugin plugin) {
    this.plugin = plugin;
    this.natives = new NativeCanvases(plugin.getLogger());
  }

  /** Creates the windows and registers their listeners. */
  public static BukkitPackWindows install(JavaPlugin plugin) {
    BukkitPackWindows windows = new BukkitPackWindows(Objects.requireNonNull(plugin, "plugin"));
    plugin.getServer().getPluginManager().registerEvents(new PackWindowListener(windows), plugin);
    return windows;
  }

  @Override
  public PackSession open(PlayerRef viewer, ChestMenu menu, PackSurface surface) {
    PackFrame.check(menu, surface);
    Player player = BukkitRefs.player(viewer);
    BukkitPackSession session = new BukkitPackSession(this, viewer, player, menu, surface);
    BukkitPackSession previous = sessions.get(player.getUniqueId());
    if (previous != null && previous.isOpen()) {
      session.adopt(previous);
    }
    sessions.put(player.getUniqueId(), session);
    session.render(menu, surface);
    return session;
  }

  @Override
  public PackSupport support(PlayerRef viewer) {
    Player player = BukkitRefs.player(viewer);
    return player != null && natives.get(player) != null ? PackSupport.FULL : PackSupport.NONE;
  }

  /** Closes every open pack window, running close callbacks. */
  public void closeAll() {
    for (BukkitPackSession session : new ArrayList<>(sessions.values())) {
      session.close();
    }
    sessions.clear();
  }

  /** Puts the viewer's open pack window away without its close callback. */
  void putAway(Player player) {
    BukkitPackSession session = sessions.get(player.getUniqueId());
    if (session != null) {
      session.putAway();
    }
  }

  boolean canvasFor(Player player, ChestMenu menu, PackSurface surface) {
    return PackFrame.wantsCanvas(menu, surface) && natives.get(player) != null;
  }

  PackWindow openCanvas(Player player, int rows, List<TitleRun> title, Map<Integer, PackFrame.Shown> frame,
                        BukkitPackSession owner) {
    Canvases canvases = natives.get(player);
    if (canvases != null) {
      try {
        return CanvasPackWindow.open(canvases, player, rows, title, frame, owner);
      } catch (LinkageError | RuntimeException failure) {
        natives.disable(failure);
      }
    }
    return null;
  }

  boolean canvasView(UUID viewer, InventoryView view) {
    BukkitPackSession session = sessions.get(viewer);
    return session != null && session.window() instanceof CanvasPackWindow canvas && canvas.isOpen() && canvas.view() == view;
  }

  void forget(BukkitPackSession session) {
    sessions.remove(session.player().getUniqueId(), session);
  }

  void forget(UUID viewer) {
    sessions.remove(viewer);
  }

  void later(Player player, Runnable task, int delayTicks) {
    J.runEntity(player, task, delayTicks);
  }

  long tick() {
    return System.nanoTime() / NANOS_PER_TICK;
  }

  Logger logger() {
    return plugin.getLogger();
  }
}
