package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.AdaptCore;
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
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerChangedWorldEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerItemHeldEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.event.player.PrePlayerAttackEntityEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public final class NodeAreaSelector {
  public static final String CHANNEL = "golemancy-area-select";
  private static final int WATCH_TICKS = 10;
  private static final long DOUBLE_SNEAK_MILLIS = 500L;
  private static final long IDLE_MILLIS = 120_000L;
  private final Map<UUID, ShiftSession> sessions = new ConcurrentHashMap<>();
  private final Map<UUID, Long> sneaks = new ConcurrentHashMap<>();
  private final AtomicLong generations = new AtomicLong();

  public boolean selecting(PlayerRef player, NodeKey key) {
    ShiftSession session = sessions.get(player.uuid());
    return session != null && session.key().equals(key);
  }

  public boolean swallows(PlayerRef player) {
    return sessions.containsKey(player.uuid()) && isCrook(player.inventory().mainHand());
  }

  public void toggle(PlayerRef player, NodeKey key, NodeRecord record) {
    if (selecting(player, key)) {
      end(player);
      return;
    }
    if (!record.owner().equals(player.uuid())) {
      GolemGuiSupport.warn(player, GolemancyMessages.NODE_NOT_OWNER);
      return;
    }
    if (!isCrook(player.inventory().mainHand()) || !player.world().uid().equals(key.world()) || NodeGui.level(player) < 1) {
      GolemGuiSupport.warn(player, NodeEditorMessages.HOLD_CROOK);
      return;
    }
    ShiftSession session = new ShiftSession(generations.incrementAndGet(), key, System.currentTimeMillis());
    sessions.put(player.uuid(), session);
    GolemancyRuntime.reveal().ifPresent(reveal -> reveal.selectArea(player, key));
    player.closeInventory();
    GolemGuiSupport.notify(player, NodeEditorMessages.SHIFT_STARTED);
    watchLater(player, session.generation());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerToggleSneakEvent event) {
    PlayerRef player = event.player();
    if (!event.isSneaking() || !isCrook(player.inventory().mainHand())) {
      return;
    }
    long now = System.currentTimeMillis();
    Long previous = sneaks.put(player.uuid(), now);
    if (previous == null || now - previous > DOUBLE_SNEAK_MILLIS) {
      return;
    }
    sneaks.remove(player.uuid());
    if (swallows(player)) {
      end(player);
      return;
    }
    Loc at = player.location();
    GolemancyRuntime.nodes().ifPresent(service -> service.index().near(player.world().uid(), at.x(), at.y(), at.z(), 32D).stream()
        .filter(entry -> entry.record().owner().equals(player.uuid()) && NodeArea.covers(entry.key(), entry.record(), at.x(), at.y(), at.z()))
        .min(Comparator.comparingInt((NodeIndex.Entry entry) -> entry.record().area().volume())
            .thenComparingDouble(entry -> entry.key().distanceSquared(at.x(), at.y(), at.z()))
            .thenComparingInt(entry -> entry.key().x()).thenComparingInt(entry -> entry.key().y()).thenComparingInt(entry -> entry.key().z())
            .thenComparingInt(entry -> entry.key().face().ordinal()))
        .ifPresent(entry -> toggle(player, entry.key(), entry.record())));
  }

  @Handle(priority = EventPriority.LOWEST)
  public void on(PlayerItemHeldEvent event) {
    PlayerRef player = event.player();
    ShiftSession session = sessions.get(player.uuid());
    if (session == null) {
      return;
    }
    if (!player.isSneaking() || !isCrook(player.inventory().mainHand())) {
      end(player);
      return;
    }
    event.setCancelled(true);
    int delta = Math.floorMod(event.newSlot() - event.previousSlot() + 4, 9) - 4;
    if (delta != 0) {
      move(player, session, delta > 0 ? 1 : -1);
    }
  }

  @Handle(priority = EventPriority.LOWEST, ignoreCancelled = false)
  public void on(PlayerInteractEvent event) {
    if (!event.protectionProbe() && swallows(event.player())) {
      event.setUseBlock(UseResult.DENY);
      event.setUseItem(UseResult.DENY);
    }
  }

  @Handle(priority = EventPriority.LOWEST, ignoreCancelled = false)
  public void on(PlayerInteractEntityEvent event) {
    if (!event.protectionProbe() && swallows(event.player())) {
      event.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.LOWEST, ignoreCancelled = false)
  public void on(PrePlayerAttackEntityEvent event) {
    if (swallows(event.player())) {
      event.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.LOWEST, ignoreCancelled = false)
  public void on(EntityDamageByEntityEvent event) {
    if (event.damager() instanceof PlayerRef player && swallows(player)) {
      event.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerSwapHandItemsEvent event) {
    end(event.player());
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerChangedWorldEvent event) {
    end(event.player());
    sneaks.remove(event.player().uuid());
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerQuitEvent event) {
    NodeSettingsClipboard.forget(event.player().uuid());
    sessions.remove(event.player().uuid());
    sneaks.remove(event.player().uuid());
  }

  private void move(PlayerRef player, ShiftSession session, int step) {
    Vec3 direction = player.eyeLocation().direction();
    NodeEdits.Axis axis = direction(direction);
    double facing = switch (axis) {
      case X -> direction.x();
      case Y -> direction.y();
      case Z -> direction.z();
    };
    int delta = (facing < 0D ? -1 : 1) * step;
    int maxRadius = GolemancyRuntime.nodeWeaving().map(policy -> policy.maxAreaRadius(policy.level(player))).orElse(0);
    NodeService service = GolemancyRuntime.nodes().orElse(null);
    WorldRef world = AdaptCore.platform().host().world(session.key().world());
    if (service == null || world == null || !Tasks.runAt(session.key().center(world), () -> shiftOwned(player, session, service, world, axis, delta, maxRadius))) {
      end(player);
    }
  }

  private void shiftOwned(PlayerRef player, ShiftSession session, NodeService service, WorldRef world, NodeEdits.Axis axis, int delta, int maxRadius) {
    ShiftSession currentSession = sessions.get(player.uuid());
    if (currentSession == null || currentSession.generation() != session.generation()) {
      return;
    }
    if (!GolemancyRuntime.protectionPolicy().map(policy -> policy.canInteract(player, session.key().center(world))).orElse(false)) {
      GolemGuiSupport.warn(player, GolemancyMessages.NODE_PROTECTED);
      return;
    }
    service.update(player.uuid(), session.key(), record -> {
      NodeCuboid area = record.area().shift(axis, delta);
      if (!area.check(maxRadius).fits()) {
        GolemGuiSupport.warn(player, NodeEditorMessages.SHIFT_BLOCKED);
        return record;
      }
      if (!NodeArea.inWorld(session.key(), area, world.minHeight(), world.maxHeight())) {
        GolemGuiSupport.warn(player, NodeEditorMessages.WORLD_LIMIT);
        return record;
      }
      sessions.computeIfPresent(player.uuid(), (ignored, active) -> active.generation() == session.generation()
          ? new ShiftSession(active.generation(), active.key(), System.currentTimeMillis()) : active);
      return record.toBuilder().area(area).build();
    });
  }

  static NodeEdits.Axis direction(Vec3 direction) {
    double x = Math.abs(direction.x());
    double y = Math.abs(direction.y());
    double z = Math.abs(direction.z());
    return y >= x && y >= z ? NodeEdits.Axis.Y : x >= z ? NodeEdits.Axis.X : NodeEdits.Axis.Z;
  }

  private void watchLater(PlayerRef player, long generation) {
    if (!Tasks.runEntity(player, () -> watch(player, generation), WATCH_TICKS)) {
      sessions.remove(player.uuid());
    }
  }

  private void watch(PlayerRef player, long generation) {
    ShiftSession session = sessions.get(player.uuid());
    if (session == null || session.generation() != generation) {
      return;
    }
    boolean present = GolemancyRuntime.nodes().flatMap(service -> service.node(session.key())).filter(record -> record.owner().equals(player.uuid())).isPresent();
    if (!player.isOnline() || !isCrook(player.inventory().mainHand()) || !player.world().uid().equals(session.key().world())
        || NodeGui.level(player) < 1 || !present || System.currentTimeMillis() - session.touchedAt() >= IDLE_MILLIS) {
      end(player);
      return;
    }
    AdaptHud.actionBar(player, C.AQUA + AdaptLanguage.text(NodeEditorMessages.SHIFT_STATUS, trusted("axis", direction(player.eyeLocation().direction()).name())));
    watchLater(player, generation);
  }

  private void end(PlayerRef player) {
    if (sessions.remove(player.uuid()) != null) {
      GolemancyRuntime.reveal().ifPresent(reveal -> reveal.selectArea(player, null));
      GolemGuiSupport.notify(player, NodeEditorMessages.SHIFT_STOPPED);
    }
  }

  private static boolean isCrook(ItemRef item) {
    return GolemItems.kindOf(item).filter(kind -> kind == GolemItemKind.CROOK).isPresent();
  }

  private record ShiftSession(long generation, NodeKey key, long touchedAt) {
  }
}
