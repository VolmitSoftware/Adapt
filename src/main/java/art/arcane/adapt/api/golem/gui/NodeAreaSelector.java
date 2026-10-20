package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.item.GolemItemKind;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.node.NodeArea;
import art.arcane.adapt.api.golem.node.NodeCuboid;
import art.arcane.adapt.api.golem.node.NodeEdits;
import art.arcane.adapt.api.golem.node.NodeIndex;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeService;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.localization.catalog.NodeEditorMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.plugin.ProtectionEventProbe;
import art.arcane.adapt.util.common.scheduling.J;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public final class NodeAreaSelector implements Listener {
  public static final String CHANNEL = "golemancy-area-select";
  private static final int WATCH_TICKS = 10;
  private static final long DOUBLE_SNEAK_MILLIS = 500L;
  private static final long IDLE_MILLIS = 120_000L;
  private final Map<UUID, ShiftSession> sessions = new ConcurrentHashMap<>();
  private final Map<UUID, Long> sneaks = new ConcurrentHashMap<>();
  private final AtomicLong generations = new AtomicLong();

  public boolean selecting(Player player, NodeKey key) {
    ShiftSession session = sessions.get(player.getUniqueId());
    return session != null && session.key().equals(key);
  }

  public boolean swallows(Player player) {
    return sessions.containsKey(player.getUniqueId()) && isCrook(player.getInventory().getItemInMainHand());
  }

  public void toggle(Player player, NodeKey key, NodeRecord record) {
    if (selecting(player, key)) {
      end(player);
      return;
    }
    if (!record.owner().equals(player.getUniqueId())) {
      GolemGuiSupport.warn(player, GolemancyMessages.NODE_NOT_OWNER);
      return;
    }
    if (!isCrook(player.getInventory().getItemInMainHand()) || !player.getWorld().getUID().equals(key.world()) || NodeGui.level(player) < 1) {
      GolemGuiSupport.warn(player, NodeEditorMessages.HOLD_CROOK);
      return;
    }
    ShiftSession session = new ShiftSession(generations.incrementAndGet(), key, System.currentTimeMillis());
    sessions.put(player.getUniqueId(), session);
    GolemancyRuntime.reveal().ifPresent(reveal -> reveal.selectArea(player, key));
    player.closeInventory();
    GolemGuiSupport.notify(player, NodeEditorMessages.SHIFT_STARTED);
    watchLater(player, session.generation());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerToggleSneakEvent event) {
    Player player = event.getPlayer();
    if (!event.isSneaking() || !isCrook(player.getInventory().getItemInMainHand())) {
      return;
    }
    long now = System.currentTimeMillis();
    Long previous = sneaks.put(player.getUniqueId(), now);
    if (previous == null || now - previous > DOUBLE_SNEAK_MILLIS) {
      return;
    }
    sneaks.remove(player.getUniqueId());
    if (swallows(player)) {
      end(player);
      return;
    }
    Location at = player.getLocation();
    GolemancyRuntime.nodes().ifPresent(service -> service.index().near(player.getWorld().getUID(), at.getX(), at.getY(), at.getZ(), 32D).stream()
        .filter(entry -> entry.record().owner().equals(player.getUniqueId()) && NodeArea.covers(entry.key(), entry.record(), at.getX(), at.getY(), at.getZ()))
        .min(Comparator.comparingInt((NodeIndex.Entry entry) -> entry.record().area().volume())
            .thenComparingDouble(entry -> entry.key().distanceSquared(at.getX(), at.getY(), at.getZ()))
            .thenComparingInt(entry -> entry.key().x()).thenComparingInt(entry -> entry.key().y()).thenComparingInt(entry -> entry.key().z())
            .thenComparingInt(entry -> entry.key().face().ordinal()))
        .ifPresent(entry -> toggle(player, entry.key(), entry.record())));
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void on(PlayerItemHeldEvent event) {
    Player player = event.getPlayer();
    ShiftSession session = sessions.get(player.getUniqueId());
    if (session == null) {
      return;
    }
    if (!player.isSneaking() || !isCrook(player.getInventory().getItemInMainHand())) {
      end(player);
      return;
    }
    event.setCancelled(true);
    int delta = Math.floorMod(event.getNewSlot() - event.getPreviousSlot() + 4, 9) - 4;
    if (delta != 0) {
      move(player, session, delta > 0 ? 1 : -1);
    }
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void on(PlayerInteractEvent event) {
    if (!ProtectionEventProbe.isActive(event) && swallows(event.getPlayer())) {
      event.setUseInteractedBlock(Event.Result.DENY);
      event.setUseItemInHand(Event.Result.DENY);
    }
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void on(PlayerInteractEntityEvent event) {
    if (!ProtectionEventProbe.isActive(event) && swallows(event.getPlayer())) {
      event.setCancelled(true);
    }
  }

  public void attacked(Player player, Cancellable event) {
    if (swallows(player)) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerSwapHandItemsEvent event) {
    end(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerChangedWorldEvent event) {
    end(event.getPlayer());
    sneaks.remove(event.getPlayer().getUniqueId());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent event) {
    NodeSettingsClipboard.forget(event.getPlayer().getUniqueId());
    sessions.remove(event.getPlayer().getUniqueId());
    sneaks.remove(event.getPlayer().getUniqueId());
  }

  private void move(Player player, ShiftSession session, int step) {
    Vector direction = player.getEyeLocation().getDirection();
    NodeEdits.Axis axis = direction(direction);
    double facing = switch (axis) {
      case X -> direction.getX();
      case Y -> direction.getY();
      case Z -> direction.getZ();
    };
    int delta = (facing < 0D ? -1 : 1) * step;
    int maxRadius = GolemancyRuntime.nodeWeaving().map(policy -> policy.maxAreaRadius(policy.level(player))).orElse(0);
    NodeService service = GolemancyRuntime.nodes().orElse(null);
    World world = Bukkit.getWorld(session.key().world());
    if (service == null || world == null || !J.runAt(session.key().center(world), () -> shiftOwned(player, session, service, world, axis, delta, maxRadius))) {
      end(player);
    }
  }

  private void shiftOwned(Player player, ShiftSession session, NodeService service, World world, NodeEdits.Axis axis, int delta, int maxRadius) {
    ShiftSession currentSession = sessions.get(player.getUniqueId());
    if (currentSession == null || currentSession.generation() != session.generation()) {
      return;
    }
    if (!GolemancyRuntime.protectionPolicy().map(policy -> policy.canInteract(player, session.key().center(world))).orElse(false)) {
      GolemGuiSupport.warn(player, GolemancyMessages.NODE_PROTECTED);
      return;
    }
    service.update(player.getUniqueId(), session.key(), record -> {
      NodeCuboid area = record.area().shift(axis, delta);
      if (!area.check(maxRadius).fits()) {
        GolemGuiSupport.warn(player, NodeEditorMessages.SHIFT_BLOCKED);
        return record;
      }
      if (!NodeArea.inWorld(session.key(), area, world.getMinHeight(), world.getMaxHeight())) {
        GolemGuiSupport.warn(player, NodeEditorMessages.WORLD_LIMIT);
        return record;
      }
      sessions.computeIfPresent(player.getUniqueId(), (ignored, active) -> active.generation() == session.generation()
          ? new ShiftSession(active.generation(), active.key(), System.currentTimeMillis()) : active);
      return record.toBuilder().area(area).build();
    });
  }

  static NodeEdits.Axis direction(Vector direction) {
    double x = Math.abs(direction.getX());
    double y = Math.abs(direction.getY());
    double z = Math.abs(direction.getZ());
    return y >= x && y >= z ? NodeEdits.Axis.Y : x >= z ? NodeEdits.Axis.X : NodeEdits.Axis.Z;
  }

  private void watchLater(Player player, long generation) {
    if (!J.runEntity(player, () -> watch(player, generation), WATCH_TICKS)) {
      sessions.remove(player.getUniqueId());
    }
  }

  private void watch(Player player, long generation) {
    ShiftSession session = sessions.get(player.getUniqueId());
    if (session == null || session.generation() != generation) {
      return;
    }
    boolean present = GolemancyRuntime.nodes().flatMap(service -> service.node(session.key())).filter(record -> record.owner().equals(player.getUniqueId())).isPresent();
    if (!player.isOnline() || !isCrook(player.getInventory().getItemInMainHand()) || !player.getWorld().getUID().equals(session.key().world())
        || NodeGui.level(player) < 1 || !present || System.currentTimeMillis() - session.touchedAt() >= IDLE_MILLIS) {
      end(player);
      return;
    }
    AdaptHud.actionBar(player, C.AQUA + AdaptLanguage.text(NodeEditorMessages.SHIFT_STATUS, trusted("axis", direction(player.getEyeLocation().getDirection()).name())));
    watchLater(player, generation);
  }

  private void end(Player player) {
    if (sessions.remove(player.getUniqueId()) != null) {
      GolemancyRuntime.reveal().ifPresent(reveal -> reveal.selectArea(player, null));
      GolemGuiSupport.notify(player, NodeEditorMessages.SHIFT_STOPPED);
    }
  }

  private static boolean isCrook(ItemStack item) {
    return GolemItems.kindOf(item).filter(kind -> kind == GolemItemKind.CROOK).isPresent();
  }

  private record ShiftSession(long generation, NodeKey key, long touchedAt) {
  }

  public static final class PaperAttacks implements Listener {
    private final NodeAreaSelector areas;

    public PaperAttacks(NodeAreaSelector areas) {
      this.areas = Objects.requireNonNull(areas);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void on(PrePlayerAttackEntityEvent event) {
      areas.attacked(event.getPlayer(), event);
    }
  }

  public static final class SpigotAttacks implements Listener {
    private final NodeAreaSelector areas;

    public SpigotAttacks(NodeAreaSelector areas) {
      this.areas = Objects.requireNonNull(areas);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void on(EntityDamageByEntityEvent event) {
      if (event.getDamager() instanceof Player player) {
        areas.attacked(player, event);
      }
    }
  }
}
