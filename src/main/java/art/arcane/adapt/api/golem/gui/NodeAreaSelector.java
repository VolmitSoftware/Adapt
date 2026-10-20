package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.fx.ViewerDisplayDirector;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.item.GolemItemKind;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.node.NodeCuboid;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeOutline;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeService;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.plugin.ProtectionEventProbe;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.TextKey;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public final class NodeAreaSelector implements Listener {
  public static final String CHANNEL = "golemancy-area-select";
  private static final int WATCH_TICKS = 10;
  private static final int TARGET_REACH = 6;
  private static final long TICK_MILLIS = 50L;
  private static final long PREVIEW_MILLIS = NodeOutline.DURATION_TICKS * TICK_MILLIS;
  private static final long REFRESH_MARGIN_MILLIS = (WATCH_TICKS + 10) * TICK_MILLIS;

  private final AreaSelections selections = new AreaSelections();
  private final Map<UUID, Preview> previews = new ConcurrentHashMap<>();

  public boolean selecting(Player player, NodeKey key) {
    return selections.selection(player.getUniqueId()).filter(selection -> selection.node().equals(key)).isPresent();
  }

  public boolean swallows(Player player) {
    return selections.swallows(player.getUniqueId(), System.currentTimeMillis()) && isCrook(player.getInventory().getItemInMainHand());
  }

  public void toggle(Player player, NodeKey key, NodeRecord record) {
    UUID playerId = player.getUniqueId();
    long now = System.currentTimeMillis();
    if (selecting(player, key)) {
      selections.end(playerId, now);
      clearPreview(playerId);
      GolemGuiSupport.notify(player, GolemancyMessages.NODE_AREA_CANCELLED);
      return;
    }
    if (!selections.begin(playerId, key, record, now)) {
      GolemGuiSupport.warn(player, GolemancyMessages.NODE_NOT_OWNER);
      return;
    }
    clearPreview(playerId);
    player.closeInventory();
    LanguageAudience.run(playerId, () -> {
      Adapt.messagePlayer(player, C.AQUA + AdaptLanguage.text(GolemancyMessages.NODE_AREA_STARTED));
      Adapt.messagePlayer(player, C.GRAY + AdaptLanguage.text(GolemancyMessages.NODE_AREA_STARTED_HINT));
    });
    selections.selection(playerId).ifPresent(selection -> watchLater(player, selection.session()));
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void on(PlayerInteractEvent e) {
    if (ProtectionEventProbe.isActive(e) || e.getAction() == Action.PHYSICAL || !swallows(e.getPlayer())) {
      return;
    }
    e.setUseInteractedBlock(Event.Result.DENY);
    e.setUseItemInHand(Event.Result.DENY);
    if (e.getHand() != EquipmentSlot.HAND) {
      return;
    }
    Player player = e.getPlayer();
    Block block = e.getClickedBlock();
    switch (e.getAction()) {
      case LEFT_CLICK_BLOCK, LEFT_CLICK_AIR -> leftClicked(player, block);
      case RIGHT_CLICK_BLOCK, RIGHT_CLICK_AIR -> rightClicked(player, block);
      default -> {
      }
    }
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void on(PlayerInteractEntityEvent e) {
    Player player = e.getPlayer();
    if (ProtectionEventProbe.isActive(e) || !swallows(player)) {
      return;
    }
    e.setCancelled(true);
    if (e.getHand() == EquipmentSlot.HAND) {
      rightClicked(player, player.getTargetBlockExact(TARGET_REACH));
    }
  }

  public void attacked(Player player, Cancellable event) {
    if (!swallows(player)) {
      return;
    }
    event.setCancelled(true);
    leftClicked(player, player.getTargetBlockExact(TARGET_REACH));
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void on(BlockBreakEvent e) {
    if (swallows(e.getPlayer())) {
      e.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerChangedWorldEvent e) {
    Player player = e.getPlayer();
    if (selections.end(player.getUniqueId(), System.currentTimeMillis()).isPresent()) {
      clearPreview(player.getUniqueId());
      GolemGuiSupport.warn(player, GolemancyMessages.NODE_AREA_WORLD_CHANGED);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    UUID playerId = e.getPlayer().getUniqueId();
    selections.forget(playerId);
    clearPreview(playerId);
  }

  private void leftClicked(Player player, Block block) {
    if (player.isSneaking()) {
      cancel(player);
    } else if (block != null) {
      corner(player, AreaSelections.Corner.FIRST, block);
    }
  }

  private void rightClicked(Player player, Block block) {
    if (player.isSneaking()) {
      apply(player);
    } else if (block != null) {
      corner(player, AreaSelections.Corner.SECOND, block);
    }
  }

  private void corner(Player player, AreaSelections.Corner corner, Block block) {
    long now = System.currentTimeMillis();
    selections.corner(player.getUniqueId(), corner, block.getX(), block.getY(), block.getZ(), now).ifPresent(selection -> {
      GolemGuiSupport.notify(player, GolemancyMessages.NODE_AREA_CORNER, trusted("corner", corner == AreaSelections.Corner.FIRST ? 1 : 2),
          trusted("x", block.getX()), trusted("y", block.getY()), trusted("z", block.getZ()));
      if (selection.complete()) {
        preview(player, selection.node(), selection.area(), now);
      }
    });
  }

  private void apply(Player player) {
    int maxRadius = maxRadius(player);
    AreaSelections.Applied applied = selections.apply(player.getUniqueId(), maxRadius, System.currentTimeMillis());
    switch (applied.outcome()) {
      case NOT_SELECTING -> {
      }
      case INCOMPLETE -> GolemGuiSupport.warn(player, GolemancyMessages.NODE_AREA_INCOMPLETE);
      case REJECTED -> rejected(player, applied.limit(), maxRadius);
      case READY -> save(player, applied.node(), applied.area());
    }
  }

  private void cancel(Player player) {
    UUID playerId = player.getUniqueId();
    if (selections.end(playerId, System.currentTimeMillis()).isPresent()) {
      clearPreview(playerId);
      GolemGuiSupport.notify(player, GolemancyMessages.NODE_AREA_CANCELLED);
    }
  }

  private void rejected(Player player, NodeCuboid.Limit limit, int maxRadius) {
    if (limit.verdict() == NodeCuboid.Verdict.MISSING_CENTER) {
      GolemGuiSupport.warn(player, GolemancyMessages.NODE_AREA_MISSING_CENTER);
      return;
    }
    GolemGuiSupport.warn(player, GolemancyMessages.NODE_AREA_TOO_FAR, trusted("face", limit.face()), trusted("distance", limit.distance()),
        trusted("max", maxRadius));
  }

  private void save(Player player, NodeKey key, NodeCuboid area) {
    UUID owner = player.getUniqueId();
    clearPreview(owner);
    World world = Bukkit.getWorld(key.world());
    Optional<NodeService> service = GolemancyRuntime.nodes();
    if (world == null || service.isEmpty() || !J.runAt(key.center(world), () -> service.get()
        .update(owner, key, current -> current.toBuilder().area(area).build())
        .ifPresentOrElse(updated -> GolemGuiSupport.notify(player, GolemancyMessages.NODE_AREA_APPLIED, trusted("x", area.spanX()),
            trusted("y", area.spanY()), trusted("z", area.spanZ())), () -> GolemGuiSupport.warn(player, GolemancyMessages.NODE_GONE)))) {
      GolemGuiSupport.warn(player, GolemancyMessages.NODE_GONE);
    }
  }

  private void watchLater(Player player, long session) {
    if (!J.runEntity(player, () -> watch(player, session), WATCH_TICKS)) {
      selections.forget(player.getUniqueId());
      clearPreview(player.getUniqueId());
    }
  }

  private void watch(Player player, long session) {
    UUID playerId = player.getUniqueId();
    AreaSelections.Selection current = selections.selection(playerId).orElse(null);
    if (current == null || current.session() != session) {
      return;
    }
    if (!player.isOnline()) {
      selections.forget(playerId);
      clearPreview(playerId);
      return;
    }
    long now = System.currentTimeMillis();
    NodeKey key = current.node();
    boolean nodePresent = GolemancyRuntime.nodes().flatMap(service -> service.node(key)).filter(found -> found.owner().equals(playerId)).isPresent();
    AreaSelections.Watch facts = new AreaSelections.Watch(player.getWorld().getUID(), isCrook(player.getInventory().getItemInMainHand()), nodePresent);
    AreaSelections.Exit exit = selections.watch(playerId, facts, now);
    if (exit != AreaSelections.Exit.NONE) {
      clearPreview(playerId);
      GolemGuiSupport.warn(player, exitMessage(exit));
      return;
    }
    Preview shown = previews.get(playerId);
    if (shown != null && now >= shown.expiresAt() - REFRESH_MARGIN_MILLIS) {
      preview(player, key, shown.area(), now);
    }
    watchLater(player, session);
  }

  private void preview(Player player, NodeKey key, NodeCuboid area, long now) {
    UUID playerId = player.getUniqueId();
    boolean valid = area.check(maxRadius(player)).fits();
    Preview shown = previews.get(playerId);
    boolean same = shown != null && shown.area().equals(area) && shown.valid() == valid;
    if (same && now < shown.expiresAt() - REFRESH_MARGIN_MILLIS) {
      return;
    }
    if (shown != null && !same) {
      ViewerDisplayDirector.clearViewer(CHANNEL, playerId);
    }
    previews.put(playerId, new Preview(area, valid, now + PREVIEW_MILLIS));
    DyeColor color = valid ? DyeColor.LIME : DyeColor.RED;
    NodeOutline.show(CHANNEL, player, NodeOutline.edges(key, area), NodeOutline.glass(color), color.getColor());
  }

  private void clearPreview(UUID playerId) {
    if (previews.remove(playerId) != null) {
      ViewerDisplayDirector.clearViewer(CHANNEL, playerId);
    }
  }

  private static int maxRadius(Player player) {
    return GolemancyRuntime.nodeWeaving().map(policy -> policy.maxAreaRadius(policy.level(player))).orElse(0);
  }

  private static TextKey exitMessage(AreaSelections.Exit exit) {
    return switch (exit) {
      case TIMED_OUT -> GolemancyMessages.NODE_AREA_TIMED_OUT;
      case PUT_AWAY -> GolemancyMessages.NODE_AREA_PUT_AWAY;
      case WORLD_CHANGED -> GolemancyMessages.NODE_AREA_WORLD_CHANGED;
      case NODE_GONE, NONE -> GolemancyMessages.NODE_AREA_NODE_GONE;
    };
  }

  private static boolean isCrook(ItemStack item) {
    return GolemItems.kindOf(item).filter(kind -> kind == GolemItemKind.CROOK).isPresent();
  }

  private record Preview(NodeCuboid area, boolean valid, long expiresAt) {
  }

  public static final class PaperAttacks implements Listener {
    private final NodeAreaSelector areas;

    public PaperAttacks(NodeAreaSelector areas) {
      this.areas = Objects.requireNonNull(areas);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void on(PrePlayerAttackEntityEvent e) {
      areas.attacked(e.getPlayer(), e);
    }
  }

  public static final class SpigotAttacks implements Listener {
    private final NodeAreaSelector areas;

    public SpigotAttacks(NodeAreaSelector areas) {
      this.areas = Objects.requireNonNull(areas);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void on(EntityDamageByEntityEvent e) {
      if (e.getDamager() instanceof Player player) {
        areas.attacked(player, e);
      }
    }
  }
}
