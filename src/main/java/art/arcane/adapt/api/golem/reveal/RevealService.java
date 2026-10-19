package art.arcane.adapt.api.golem.reveal;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.fx.ViewerGlowCoordinator;
import art.arcane.adapt.api.fx.display.HiddenDisplays;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemMastery;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemRunes;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemVitals;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.ServantFrame;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.node.NodeBundle;
import art.arcane.adapt.api.golem.node.NodeIndex;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeOutline;
import art.arcane.adapt.api.golem.node.NodeService;
import art.arcane.adapt.api.golem.node.NodeVisibility;
import art.arcane.adapt.api.golem.node.NodeWeavingPolicy;
import art.arcane.adapt.api.tick.TickedObject;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.localization.catalog.TragoulMessages;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.math.M;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public final class RevealService extends TickedObject {
  public static final long LOCATE_MILLIS = 10_000L;
  private static final int LOCATE_PULSE_TICKS = 10;
  private static final ChatColor LOCATE_COLOR = ChatColor.AQUA;
  private static final double LINE_SPACING = 0.75D;
  private static final double LINE_START_OFFSET = 1.5D;
  private static final int LINE_MAX_POINTS = 64;
  private static final float LABEL_LIFT = 0.2F;
  private static final int LABEL_BACKGROUND_ALPHA = 64;
  private static final long LABEL_RETRY_MILLIS = 5_000L;
  private static final String CORE_SEPARATOR = " + ";

  private final Supplier<RevealSettings> settings;
  private final RevealTracker<UUID> golems = new RevealTracker<>();
  private final RevealTracker<NodeKey> nodes = new RevealTracker<>();
  private final RevealTracker<UUID> servantGlows = new RevealTracker<>();
  private final RevealTracker<UUID> servantLabels = new RevealTracker<>();
  private final Map<UUID, Integer> glowIds = new ConcurrentHashMap<>();
  private final NodeOutline outline = new NodeOutline();
  private final Set<UUID> armed = ConcurrentHashMap.newKeySet();
  private final Set<UUID> pendingUpdates = ConcurrentHashMap.newKeySet();
  private final Set<UUID> spawning = ConcurrentHashMap.newKeySet();
  private final Set<UUID> pendingRefresh = ConcurrentHashMap.newKeySet();
  private final Map<UUID, Label> labels = new ConcurrentHashMap<>();
  private final Map<UUID, Long> labelRetries = new ConcurrentHashMap<>();
  private final Map<LocateKey, Long> locates = new ConcurrentHashMap<>();

  public RevealService(Supplier<RevealSettings> settings) {
    super("golemancy", "golem-reveal", Objects.requireNonNull(settings).get().pollMillis());
    this.settings = settings;
  }

  public static boolean isRevealItem(ItemStack item) {
    return GolemItems.kindOf(item).map(RevealRules::reveals).orElse(false);
  }

  public static boolean holdsRevealItem(Player player) {
    PlayerInventory inventory = player.getInventory();
    return RevealRules.eligible(
        GolemItems.kindOf(inventory.getItemInMainHand()).orElse(null),
        GolemItems.kindOf(inventory.getItemInOffHand()).orElse(null));
  }

  public boolean isArmed(UUID viewer) {
    return armed.contains(viewer);
  }

  public void requestUpdate(Player viewer) {
    UUID viewerId = viewer.getUniqueId();
    if (pendingUpdates.add(viewerId) && !J.runEntity(viewer, () -> update(viewer), 1)) {
      pendingUpdates.remove(viewerId);
    }
  }

  public void update(Player viewer) {
    UUID viewerId = viewer.getUniqueId();
    pendingUpdates.remove(viewerId);
    if (!viewer.isOnline()) {
      forgetViewer(viewerId);
      return;
    }
    if (!holdsRevealItem(viewer)) {
      armed.remove(viewerId);
      apply(viewer, golems.update(viewerId, Set.of()), Map.of());
      hideServants(viewer);
      applyNodes(viewer, nodes.update(viewerId, Set.of()));
      outline.forget(viewerId);
      return;
    }
    armed.add(viewerId);
    Map<UUID, GolemHandle> inRange = golemsInRange(viewer);
    RevealTracker.Delta<UUID> delta = golems.update(viewerId, inRange.keySet());
    if (hidesLabels(viewer)) {
      apply(viewer, delta, Map.of());
      hideLabels(viewer, inRange.keySet());
    } else {
      apply(viewer, delta, inRange);
      syncStayingLabels(viewer, inRange, delta);
    }
    glowGolems(viewer, inRange);
    revealServants(viewer);
    Optional<NodeWeavingPolicy> nodeWeaving = GolemancyRuntime.nodeWeaving();
    boolean revealsNodes = nodeWeaving.isEmpty() || !nodeWeaving.get().crookOnlyReveal(viewer) || holdsCrook(viewer);
    applyNodes(viewer, nodes.update(viewerId, revealsNodes ? nodesInRange(viewer) : Set.of()));
    if (revealsNodes && (nodeWeaving.isEmpty() || !nodeWeaving.get().hidesOutline(viewer))) {
      outlineLookedAtNode(viewer);
    } else {
      outline.forget(viewerId);
    }
  }

  public void reset(Player viewer) {
    UUID viewerId = viewer.getUniqueId();
    armed.remove(viewerId);
    for (UUID golem : golems.reset(viewerId)) {
      hideLabel(viewer, golem);
      unglow(viewer, golem);
    }
    for (UUID servant : servantLabels.reset(viewerId)) {
      hideLabel(viewer, servant);
    }
    for (UUID servant : servantGlows.reset(viewerId)) {
      unglow(viewer, servant);
    }
    for (NodeKey node : nodes.reset(viewerId)) {
      hideNode(viewer, node);
    }
    outline.forget(viewerId);
  }

  public void refresh(GolemHandle handle) {
    UUID golem = handle.entityId();
    if (!labels.containsKey(golem) || !pendingRefresh.add(golem)) {
      return;
    }
    Runnable task = () -> {
      pendingRefresh.remove(golem);
      Label current = labels.get(golem);
      if (current != null && current.display().isValid() && !handle.isRetired()) {
        applyText(handle, current);
      }
    };
    if (!J.runEntity(handle.entity(), task, 1)) {
      pendingRefresh.remove(golem);
    }
  }

  public void forget(GolemHandle handle) {
    UUID golem = handle.entityId();
    unglowFor(golems.drop(golem), golem);
    retireLabel(golem);
  }

  public void nodeSpawned(NodeKey key) {
    nodes.drop(key);
    for (UUID viewerId : armed) {
      Player viewer = Bukkit.getPlayer(viewerId);
      if (viewer != null && viewer.getWorld().getUID().equals(key.world())) {
        requestUpdate(viewer);
      }
    }
  }

  public void nodeGone(NodeKey key) {
    nodes.drop(key);
  }

  public void locate(Player owner, GolemHandle handle) {
    Mob golem = handle.entity();
    LocateKey key = new LocateKey(owner.getUniqueId(), handle.entityId(), golem.getEntityId());
    boolean fresh = locates.put(key, M.ms() + LOCATE_MILLIS) == null;
    ViewerGlowCoordinator glow = Adapt.instance.getViewerGlowCoordinator();
    if (glow != null && glow.isAvailable()) {
      glow.set(ViewerGlowCoordinator.Layer.GOLEMANCY_LOCATE, golem, owner, LOCATE_COLOR);
    }
    drawLineTo(owner, golem);
    if (fresh && !J.runEntity(owner, () -> pulseLocate(owner, handle, key), LOCATE_PULSE_TICKS)) {
      endLocate(owner, key);
    }
  }

  public void point(Player owner, Location target) {
    Location eye = owner.getEyeLocation();
    World world = target.getWorld();
    if (world == null || !world.equals(eye.getWorld())) {
      return;
    }
    Vector delta = target.toVector().subtract(eye.toVector());
    double distance = delta.length();
    if (distance <= LINE_START_OFFSET) {
      return;
    }
    Vector step = delta.multiply(LINE_SPACING / distance);
    Location cursor = eye.clone().add(step.clone().multiply(LINE_START_OFFSET / LINE_SPACING));
    int points = Math.min(LINE_MAX_POINTS, (int) ((distance - LINE_START_OFFSET) / LINE_SPACING));
    for (int index = 0; index < points; index++) {
      owner.spawnParticle(Particles.END_ROD, cursor, 1, 0D, 0D, 0D, 0D);
      cursor.add(step);
    }
  }

  public void shutdown() {
    unregister();
    outline.shutdown();
    for (Label label : labels.values()) {
      removeDisplay(label.display());
    }
    labels.clear();
    labelRetries.clear();
    glowIds.clear();
    armed.clear();
    pendingUpdates.clear();
    spawning.clear();
    pendingRefresh.clear();
    locates.clear();
  }

  @Override
  public boolean hasTickDemand() {
    return !armed.isEmpty();
  }

  @Override
  public void onTick() {
    for (UUID viewerId : List.copyOf(armed)) {
      Player viewer = Bukkit.getPlayer(viewerId);
      if (viewer == null) {
        forgetViewer(viewerId);
        continue;
      }
      if (pendingUpdates.add(viewerId) && !J.runEntity(viewer, () -> update(viewer))) {
        pendingUpdates.remove(viewerId);
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerItemHeldEvent e) {
    Player player = e.getPlayer();
    if (isArmed(player.getUniqueId()) || isRevealItem(player.getInventory().getItem(e.getNewSlot()))) {
      requestUpdate(player);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerSwapHandItemsEvent e) {
    Player player = e.getPlayer();
    if (isArmed(player.getUniqueId()) || isRevealItem(e.getMainHandItem()) || isRevealItem(e.getOffHandItem())) {
      requestUpdate(player);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(InventoryClickEvent e) {
    if (e.getWhoClicked() instanceof Player player
        && (isArmed(player.getUniqueId()) || movesRevealItem(player, e))) {
      requestUpdate(player);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(InventoryDragEvent e) {
    if (e.getWhoClicked() instanceof Player player && (isArmed(player.getUniqueId()) || isRevealItem(e.getOldCursor()))) {
      requestUpdate(player);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerDropItemEvent e) {
    if (isArmed(e.getPlayer().getUniqueId())) {
      requestUpdate(e.getPlayer());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntityPickupItemEvent e) {
    if (e.getEntity() instanceof Player player && isRevealItem(e.getItem().getItemStack())) {
      requestUpdate(player);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerJoinEvent e) {
    requestUpdate(e.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerRespawnEvent e) {
    requestUpdate(e.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerChangedWorldEvent e) {
    reset(e.getPlayer());
    requestUpdate(e.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerDeathEvent e) {
    reset(e.getEntity());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    forgetViewer(e.getPlayer().getUniqueId());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntityDamageEvent e) {
    refreshLabeled(e.getEntity());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntityRegainHealthEvent e) {
    refreshLabeled(e.getEntity());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent e) {
    UUID entity = e.getEntity().getUniqueId();
    if (!labels.containsKey(entity) && !glowIds.containsKey(entity)) {
      return;
    }
    List<UUID> viewers = new ArrayList<>(golems.drop(entity));
    viewers.addAll(servantGlows.drop(entity));
    servantLabels.drop(entity);
    unglowFor(viewers, entity);
    retireLabel(entity);
  }

  private Map<UUID, GolemHandle> golemsInRange(Player viewer) {
    GolemRegistry registry = GolemancyRuntime.registry().orElse(null);
    if (registry == null) {
      return Map.of();
    }
    Collection<GolemHandle> owned = registry.loadedOf(viewer.getUniqueId());
    if (owned.isEmpty()) {
      return Map.of();
    }
    UUID viewerId = viewer.getUniqueId();
    Location center = viewer.getLocation();
    World world = center.getWorld();
    double range = settings.get().revealRange();
    Map<UUID, GolemHandle> inRange = new HashMap<>(owned.size() * 2);
    for (GolemHandle handle : owned) {
      Mob golem = handle.entity();
      if (!golem.isValid() || !Objects.equals(world, golem.getWorld())) {
        continue;
      }
      if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(golem)) {
        continue;
      }
      if (RevealRules.inRevealRange(golem.getLocation().distanceSquared(center), range, golems.isShown(viewerId, handle.entityId()))) {
        inRange.put(handle.entityId(), handle);
      }
    }
    return inRange;
  }

  private Set<NodeKey> nodesInRange(Player viewer) {
    NodeService service = GolemancyRuntime.nodes().orElse(null);
    Location center = viewer.getLocation();
    World world = center.getWorld();
    if (service == null || world == null) {
      return Set.of();
    }
    double range = settings.get().revealRange();
    List<NodeIndex.Entry> candidates = service.index().near(world.getUID(), center.getX(), center.getY(), center.getZ(), range + RevealRules.LEAVE_MARGIN);
    if (candidates.isEmpty()) {
      return Set.of();
    }
    UUID viewerId = viewer.getUniqueId();
    NodeVisibility visibility = GolemancyRuntime.nodeSettings().visibility();
    int nodeWeaving = visibility == NodeVisibility.ANY_GOLEMANCER ? nodeWeavingLevel(viewer) : 0;
    Set<NodeKey> visible = new HashSet<>(candidates.size() * 2);
    for (NodeIndex.Entry entry : candidates) {
      NodeKey key = entry.key();
      if (!RevealRules.nodeVisible(true, entry.record().owner().equals(viewerId), visibility, nodeWeaving)
          || !RevealRules.inRevealRange(key.distanceSquared(center.getX(), center.getY(), center.getZ()), range, nodes.isShown(viewerId, key))) {
        continue;
      }
      Optional<NodeBundle> bundle = service.renderer().bundle(key);
      if (bundle.isEmpty() || (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(bundle.get().icon()))) {
        continue;
      }
      visible.add(key);
    }
    return visible;
  }

  private void outlineLookedAtNode(Player viewer) {
    Optional<NodeService> service = GolemancyRuntime.nodes();
    if (service.isEmpty()) {
      outline.forget(viewer.getUniqueId());
      return;
    }
    outline.update(viewer, service.get().index(), settings.get().pollTicks());
  }

  private static int nodeWeavingLevel(Player viewer) {
    Optional<NodeWeavingPolicy> policy = GolemancyRuntime.nodeWeaving();
    return policy.map(nodeWeaving -> nodeWeaving.level(viewer)).orElse(0);
  }

  private static boolean holdsCrook(Player player) {
    PlayerInventory inventory = player.getInventory();
    return RevealRules.holdsCrook(
        GolemItems.kindOf(inventory.getItemInMainHand()).orElse(null),
        GolemItems.kindOf(inventory.getItemInOffHand()).orElse(null));
  }

  private void applyNodes(Player viewer, RevealTracker.Delta<NodeKey> delta) {
    for (NodeKey node : delta.leaving()) {
      hideNode(viewer, node);
    }
    for (NodeKey node : delta.entering()) {
      showNode(viewer, node);
    }
  }

  private static void showNode(Player viewer, NodeKey node) {
    Optional<NodeBundle> bundle = GolemancyRuntime.nodes().flatMap(service -> service.renderer().bundle(node));
    if (bundle.isEmpty()) {
      return;
    }
    for (Entity entity : bundle.get().entities()) {
      HiddenDisplays.show(viewer, entity);
    }
  }

  private static void hideNode(Player viewer, NodeKey node) {
    Optional<NodeBundle> bundle = GolemancyRuntime.nodes().flatMap(service -> service.renderer().bundle(node));
    if (bundle.isEmpty()) {
      return;
    }
    for (Entity entity : bundle.get().entities()) {
      if (entity.isValid()) {
        HiddenDisplays.hide(viewer, entity);
      }
    }
  }

  private void apply(Player viewer, RevealTracker.Delta<UUID> delta, Map<UUID, GolemHandle> inRange) {
    for (UUID golem : delta.leaving()) {
      hideLabel(viewer, golem);
      unglow(viewer, golem);
    }
    for (UUID golem : delta.entering()) {
      GolemHandle handle = inRange.get(golem);
      if (handle != null) {
        showLabel(viewer, handle);
      }
    }
  }

  private static boolean hidesLabels(Player owner) {
    Optional<GolemLabelPolicy> policy = GolemancyRuntime.labelPolicy();
    return policy.isPresent() && policy.get().hidesLabels(owner);
  }

  private void hideLabels(Player owner, Set<UUID> golemIds) {
    for (UUID golem : golemIds) {
      if (labels.containsKey(golem)) {
        hideLabel(owner, golem);
      }
    }
  }

  private void syncStayingLabels(Player viewer, Map<UUID, GolemHandle> inRange, RevealTracker.Delta<UUID> delta) {
    for (Map.Entry<UUID, GolemHandle> entry : inRange.entrySet()) {
      if (delta.entering().contains(entry.getKey())) {
        continue;
      }
      Label label = labels.get(entry.getKey());
      if (label == null || !label.display().isValid()) {
        showLabel(viewer, entry.getValue());
      } else if (J.isOwnedByCurrentRegion(entry.getValue().entity())) {
        applyText(entry.getValue(), label);
      }
    }
  }

  private void showLabel(Player viewer, GolemHandle handle) {
    UUID golem = handle.entityId();
    Label label = labels.get(golem);
    if (label != null && label.display().isValid()) {
      HiddenDisplays.show(viewer, label.display());
      return;
    }
    Long retryAt = labelRetries.get(golem);
    if (retryAt != null && retryAt > M.ms()) {
      return;
    }
    if (spawning.add(golem) && !J.runEntity(handle.entity(), () -> spawnLabel(viewer, handle))) {
      spawning.remove(golem);
    }
  }

  private void spawnLabel(Player viewer, GolemHandle handle) {
    UUID golem = handle.entityId();
    try {
      Mob entity = handle.entity();
      if (handle.isRetired() || !entity.isValid() || !golems.isShown(viewer.getUniqueId(), golem)) {
        return;
      }
      attachLabel(viewer, entity, handle.owner(), content(handle), settings.get().labelViewRange(), golems);
    } finally {
      spawning.remove(golem);
    }
  }

  private void attachLabel(Player viewer, Entity carrier, UUID owner, GolemLabel.Content content, float viewRange, RevealTracker<UUID> tracker) {
    TextDisplay display = HiddenDisplays.spawnLabel(carrier.getLocation(), GolemLabel.compose(content),
        Color.fromARGB(LABEL_BACKGROUND_ALPHA, 0, 0, 0), viewRange);
    Transformation base = display.getTransformation();
    display.setTransformation(new Transformation(new Vector3f(0F, LABEL_LIFT, 0F), base.getLeftRotation(), base.getScale(), base.getRightRotation()));
    UUID carrierId = carrier.getUniqueId();
    if (!carrier.addPassenger(display)) {
      display.remove();
      labelRetries.put(carrierId, M.ms() + LABEL_RETRY_MILLIS);
      return;
    }
    labelRetries.remove(carrierId);
    Label previous = labels.put(carrierId, new Label(display, owner, content));
    if (previous != null) {
      removeDisplay(previous.display());
    }
    J.runEntity(viewer, () -> showSpawned(viewer, carrierId, display, tracker));
  }

  private void showSpawned(Player viewer, UUID carrier, TextDisplay display, RevealTracker<UUID> tracker) {
    Label current = labels.get(carrier);
    if (viewer.isOnline() && current != null && current.display() == display && tracker.isShown(viewer.getUniqueId(), carrier)) {
      HiddenDisplays.show(viewer, display);
    }
  }

  private void hideLabel(Player viewer, UUID golem) {
    labelRetries.remove(golem);
    Label label = labels.remove(golem);
    if (label == null) {
      return;
    }
    HiddenDisplays.hide(viewer, label.display());
    removeDisplay(label.display());
  }

  private void retireLabel(UUID golem) {
    labelRetries.remove(golem);
    Label label = labels.remove(golem);
    if (label == null) {
      return;
    }
    Player owner = Bukkit.getPlayer(label.owner());
    if (owner != null) {
      J.runEntity(owner, () -> HiddenDisplays.hide(owner, label.display()));
    }
    removeDisplay(label.display());
  }

  private void removeDisplay(TextDisplay display) {
    if (!Adapt.instance.isEnabled()) {
      if (!J.isFoliaThreading() || J.isOwnedByCurrentRegion(display)) {
        removeDisplayOwned(display);
      }
      return;
    }
    J.runEntity(display, () -> removeDisplayOwned(display));
  }

  private void removeDisplayOwned(TextDisplay display) {
    if (display.isValid()) {
      display.remove();
    }
  }

  private void applyText(GolemHandle handle, Label label) {
    applyContent(handle.entityId(), label, content(handle));
  }

  private void applyContent(UUID carrier, Label label, GolemLabel.Content content) {
    if (content.equals(label.content())) {
      return;
    }
    Label updated = new Label(label.display(), label.owner(), content);
    if (labels.replace(carrier, label, updated)) {
      PaperCompat.text(label.display(), GolemLabel.compose(content));
    }
  }

  private GolemLabel.Content content(GolemHandle handle) {
    try (LanguageAudience.Scope audience = LanguageAudience.open(handle.owner())) {
      Mob golem = handle.entity();
      GolemState state = handle.state();
      Component name = PaperCompat.customName(golem);
      if (name == null) {
        name = Component.text(AdaptLanguage.text(GolemancyMessages.GUI_TITLE, trusted("body", AdaptLanguage.text(state.body().displayName()))));
      }
      return new GolemLabel.Content(name, golem.getHealth(), GolemVitals.maxHealth(golem), coreNames(GolemMastery.cores(handle)),
          AdaptLanguage.text(state.mode().displayName()), GolemRunes.channels(handle), state.masterwork());
    }
  }

  private static String coreNames(List<GolemCoreType> cores) {
    List<String> names = new ArrayList<>(cores.size());
    for (GolemCoreType core : cores) {
      names.add(AdaptLanguage.text(core.displayName()));
    }
    return String.join(CORE_SEPARATOR, names);
  }

  private void glowGolems(Player viewer, Map<UUID, GolemHandle> inRange) {
    for (GolemHandle handle : inRange.values()) {
      glow(viewer, handle.entity(), RevealRules.glowColor(GolemRunes.channels(handle)));
    }
  }

  private void revealServants(Player viewer) {
    UUID viewerId = viewer.getUniqueId();
    ServantFrame.Frame frame = ServantFrame.frame().filter(found -> found.level(viewerId) >= 1).orElse(null);
    if (frame == null) {
      hideServants(viewer);
      return;
    }
    Map<UUID, Skeleton> glowing = servantsInRange(viewer, frame.servants(viewerId), settings.get().revealRange(), servantGlows);
    Map<UUID, Skeleton> labeled = servantsInRange(viewer, glowing.values(), frame.labelRange(), servantLabels);
    int channels = frame.channels(viewerId);
    for (UUID servant : servantGlows.update(viewerId, glowing.keySet()).leaving()) {
      unglow(viewer, servant);
    }
    ChatColor color = RevealRules.glowColor(channels);
    for (Skeleton servant : glowing.values()) {
      glow(viewer, servant, color);
    }
    RevealTracker.Delta<UUID> delta = servantLabels.update(viewerId, labeled.keySet());
    for (UUID servant : delta.leaving()) {
      hideLabel(viewer, servant);
    }
    ServantView view = new ServantView(frame, channels);
    for (Skeleton servant : labeled.values()) {
      showServantLabel(viewer, servant, view, delta.entering().contains(servant.getUniqueId()));
    }
  }

  private void hideServants(Player viewer) {
    UUID viewerId = viewer.getUniqueId();
    for (UUID servant : servantLabels.update(viewerId, Set.of()).leaving()) {
      hideLabel(viewer, servant);
    }
    for (UUID servant : servantGlows.update(viewerId, Set.of()).leaving()) {
      unglow(viewer, servant);
    }
  }

  private Map<UUID, Skeleton> servantsInRange(Player viewer, Collection<Skeleton> candidates, double range, RevealTracker<UUID> tracker) {
    if (candidates.isEmpty()) {
      return Map.of();
    }
    UUID viewerId = viewer.getUniqueId();
    Location center = viewer.getLocation();
    World world = center.getWorld();
    Map<UUID, Skeleton> inRange = new HashMap<>(candidates.size() * 2);
    for (Skeleton servant : candidates) {
      if (!servant.isValid() || !Objects.equals(world, servant.getWorld()) || (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(servant))) {
        continue;
      }
      UUID servantId = servant.getUniqueId();
      if (RevealRules.inRevealRange(servant.getLocation().distanceSquared(center), range, tracker.isShown(viewerId, servantId))) {
        inRange.put(servantId, servant);
      }
    }
    return inRange;
  }

  private void showServantLabel(Player viewer, Skeleton servant, ServantView view, boolean entering) {
    UUID servantId = servant.getUniqueId();
    Label label = labels.get(servantId);
    if (label != null && label.display().isValid()) {
      if (entering) {
        HiddenDisplays.show(viewer, label.display());
      }
      if (J.isOwnedByCurrentRegion(servant)) {
        applyContent(servantId, label, servantContent(servant, view, viewer.getUniqueId()));
      }
      return;
    }
    if (spawning.add(servantId) && !J.runEntity(servant, () -> spawnServantLabel(viewer, servant, view))) {
      spawning.remove(servantId);
    }
  }

  private void spawnServantLabel(Player viewer, Skeleton servant, ServantView view) {
    UUID servantId = servant.getUniqueId();
    try {
      UUID viewerId = viewer.getUniqueId();
      if (!servant.isValid() || !servantLabels.isShown(viewerId, servantId)) {
        return;
      }
      attachLabel(viewer, servant, viewerId, servantContent(servant, view, viewerId), RevealSettings.viewRange(view.frame().labelRange()), servantLabels);
    } finally {
      spawning.remove(servantId);
    }
  }

  private GolemLabel.Content servantContent(Skeleton servant, ServantView view, UUID owner) {
    try (LanguageAudience.Scope audience = LanguageAudience.open(owner)) {
      long seconds = RevealRules.secondsLeft(view.frame().expiresAt(servant), M.ms());
      return new GolemLabel.Content(Component.text(AdaptLanguage.text(TragoulMessages.SKELETAL_SERVANT_NAME)), servant.getHealth(),
          GolemVitals.maxHealth(servant), AdaptLanguage.text(GolemancyMessages.BONE_FRAME_TIME_LEFT, trusted("seconds", seconds)), "", view.channels(), false);
    }
  }

  private void glow(Player viewer, Entity entity, ChatColor color) {
    ViewerGlowCoordinator coordinator = glowCoordinator();
    if (coordinator != null && coordinator.isAvailable() && coordinator.set(ViewerGlowCoordinator.Layer.GOLEMANCY, entity, viewer, color)) {
      glowIds.put(entity.getUniqueId(), entity.getEntityId());
    }
  }

  private void unglow(Player viewer, UUID entity) {
    Integer runtimeId = glowIds.remove(entity);
    ViewerGlowCoordinator coordinator = glowCoordinator();
    if (runtimeId != null && coordinator != null) {
      coordinator.unset(ViewerGlowCoordinator.Layer.GOLEMANCY, entity, runtimeId, viewer);
    }
  }

  private void unglowFor(List<UUID> viewers, UUID entity) {
    for (UUID viewerId : viewers) {
      Player viewer = Bukkit.getPlayer(viewerId);
      if (viewer != null) {
        J.runEntity(viewer, () -> unglow(viewer, entity));
      }
    }
    if (viewers.isEmpty()) {
      glowIds.remove(entity);
    }
  }

  private static ViewerGlowCoordinator glowCoordinator() {
    Adapt plugin = Adapt.instance;
    return plugin == null ? null : plugin.getViewerGlowCoordinator();
  }

  private void refreshLabeled(Entity entity) {
    if (!labels.containsKey(entity.getUniqueId())) {
      return;
    }
    GolemancyRuntime.handle(entity).ifPresent(this::refresh);
  }

  private void forgetViewer(UUID viewerId) {
    armed.remove(viewerId);
    pendingUpdates.remove(viewerId);
    for (UUID golem : golems.reset(viewerId)) {
      glowIds.remove(golem);
      Label label = labels.remove(golem);
      if (label != null) {
        removeDisplay(label.display());
      }
    }
    for (UUID servant : servantLabels.reset(viewerId)) {
      Label label = labels.remove(servant);
      if (label != null) {
        removeDisplay(label.display());
      }
    }
    for (UUID servant : servantGlows.reset(viewerId)) {
      glowIds.remove(servant);
    }
    nodes.reset(viewerId);
    outline.forget(viewerId);
    locates.keySet().removeIf(key -> key.viewer().equals(viewerId));
  }

  private boolean movesRevealItem(Player player, InventoryClickEvent e) {
    if (isRevealItem(e.getCurrentItem()) || isRevealItem(e.getCursor())) {
      return true;
    }
    if (e.getClick() == ClickType.NUMBER_KEY) {
      return isRevealItem(player.getInventory().getItem(e.getHotbarButton()));
    }
    return e.getClick() == ClickType.SWAP_OFFHAND && isRevealItem(player.getInventory().getItemInOffHand());
  }

  private void pulseLocate(Player owner, GolemHandle handle, LocateKey key) {
    Long until = locates.get(key);
    if (until == null) {
      return;
    }
    Mob golem = handle.entity();
    if (!owner.isOnline() || M.ms() >= until || handle.isRetired() || !golem.isValid()) {
      endLocate(owner, key);
      return;
    }
    drawLineTo(owner, golem);
    if (!J.runEntity(owner, () -> pulseLocate(owner, handle, key), LOCATE_PULSE_TICKS)) {
      endLocate(owner, key);
    }
  }

  private void endLocate(Player owner, LocateKey key) {
    locates.remove(key);
    ViewerGlowCoordinator glow = Adapt.instance.getViewerGlowCoordinator();
    if (glow != null) {
      glow.unset(ViewerGlowCoordinator.Layer.GOLEMANCY_LOCATE, key.golem(), key.runtimeEntityId(), owner);
    }
  }

  private void drawLineTo(Player owner, Mob golem) {
    if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(golem)) {
      return;
    }
    point(owner, golem.getLocation().add(0D, golem.getHeight() * 0.5D, 0D));
  }

  private record Label(TextDisplay display, UUID owner, GolemLabel.Content content) {
  }

  private record LocateKey(UUID viewer, UUID golem, int runtimeEntityId) {
  }

  private record ServantView(ServantFrame.Frame frame, int channels) {
  }
}
