package art.arcane.adapt.content.golem.reveal;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.text.Markup;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.viewer.ViewerGlowPort;
import art.arcane.adapt.content.adaptation.GlowLayers;
import art.arcane.adapt.content.golem.GolemCoreType;
import art.arcane.adapt.content.golem.GolemHandle;
import art.arcane.adapt.content.golem.GolemMastery;
import art.arcane.adapt.content.golem.GolemMode;
import art.arcane.adapt.content.golem.GolemNameMode;
import art.arcane.adapt.content.golem.GolemNames;
import art.arcane.adapt.content.golem.GolemRegistry;
import art.arcane.adapt.content.golem.GolemRunes;
import art.arcane.adapt.content.golem.GolemService;
import art.arcane.adapt.content.golem.GolemState;
import art.arcane.adapt.content.golem.GolemVitals;
import art.arcane.adapt.content.golem.GolemancyRuntime;
import art.arcane.adapt.content.golem.HiddenDisplays;
import art.arcane.adapt.content.golem.ServantFrame;
import art.arcane.adapt.content.golem.item.GolemItems;
import art.arcane.adapt.content.golem.node.NodeBundle;
import art.arcane.adapt.content.golem.node.NodeIndex;
import art.arcane.adapt.content.golem.node.NodeKey;
import art.arcane.adapt.content.golem.node.NodeOutline;
import art.arcane.adapt.content.golem.node.NodeService;
import art.arcane.adapt.content.golem.node.NodeVisibility;
import art.arcane.adapt.content.golem.node.NodeWeavingPolicy;
import art.arcane.adapt.content.adaptation.discovery.EntityInsight;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.display.TextDisplayRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.entity.EntityPickupItemEvent;
import art.arcane.adapt.platform.event.entity.EntityRegainHealthEvent;
import art.arcane.adapt.platform.event.inventory.InventoryClickEvent;
import art.arcane.adapt.platform.event.inventory.InventoryDragEvent;
import art.arcane.adapt.platform.event.player.PlayerChangedWorldEvent;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerDropItemEvent;
import art.arcane.adapt.platform.event.player.PlayerItemHeldEvent;
import art.arcane.adapt.platform.event.player.PlayerJoinEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerRespawnEvent;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.fx.FxHost;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.viewer.GlowColor;
import art.arcane.adapt.platform.world.WorldRef;

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


public final class RevealService extends GolemService implements EntityInsight.Provider {
  public static final long LOCATE_MILLIS = 10_000L;
  private static final int LOCATE_PULSE_TICKS = 10;
  private static final GlowColor LOCATE_COLOR = GlowColor.AQUA;
  private static final double LINE_SPACING = 0.75D;
  private static final double LINE_START_OFFSET = 1.5D;
  private static final int LINE_MAX_POINTS = 64;
  private static final float LABEL_LIFT = 0.2F;
  private static final int LABEL_BACKGROUND_ARGB = 64 << 24;
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
  private final Set<UUID> statusViewers = ConcurrentHashMap.newKeySet();
  private final GlossStatus glossStatus = new GlossStatus(statusViewers::contains);
  private final Set<UUID> pendingUpdates = ConcurrentHashMap.newKeySet();
  private final Set<UUID> spawning = ConcurrentHashMap.newKeySet();
  private final Set<UUID> pendingRefresh = ConcurrentHashMap.newKeySet();
  private final Map<UUID, Label> labels = new ConcurrentHashMap<>();
  private final Map<UUID, Long> labelRetries = new ConcurrentHashMap<>();
  private final Map<LocateKey, Long> locates = new ConcurrentHashMap<>();

  public RevealService(Supplier<RevealSettings> settings) {
    super(Objects.requireNonNull(settings).get().pollMillis());
    this.settings = settings;
  }

  public static boolean isRevealItem(ItemRef item) {
    return GolemItems.kindOf(item).map(RevealRules::reveals).orElse(false);
  }

  public static boolean holdsRevealItem(PlayerRef player) {
    PlayerInventoryRef inventory = player.inventory();
    return RevealRules.eligible(
        GolemItems.kindOf(inventory.mainHand()).orElse(null),
        GolemItems.kindOf(inventory.offHand()).orElse(null));
  }

  public boolean isArmed(UUID viewer) {
    return armed.contains(viewer);
  }

  public void requestUpdate(PlayerRef viewer) {
    UUID viewerId = viewer.uuid();
    if (pendingUpdates.add(viewerId) && !AdaptApi.scheduler().runEntity(viewer, () -> update(viewer), 1)) {
      pendingUpdates.remove(viewerId);
    }
  }

  public void update(PlayerRef viewer) {
    UUID viewerId = viewer.uuid();
    pendingUpdates.remove(viewerId);
    if (!viewer.isOnline()) {
      forgetViewer(viewerId);
      return;
    }
    if (!holdsRevealItem(viewer)) {
      armed.remove(viewerId);
      statusViewers.remove(viewerId);
      apply(viewer, golems.update(viewerId, Set.of()), Map.of());
      hideServants(viewer);
      glossStatus.release(viewerId);
      applyNodes(viewer, nodes.update(viewerId, Set.of()));
      outline.forget(viewerId);
      return;
    }
    armed.add(viewerId);
    GolemStatusRoute route = GolemStatusRoute.of(nameMode(), !hidesLabels(viewer));
    if (route == GolemStatusRoute.HIDDEN) {
      statusViewers.remove(viewerId);
    } else {
      statusViewers.add(viewerId);
    }
    Map<UUID, GolemHandle> inRange = golemsInRange(viewer);
    RevealTracker.Delta<UUID> delta = golems.update(viewerId, inRange.keySet());
    if (route == GolemStatusRoute.LABEL) {
      apply(viewer, delta, inRange);
      syncStayingLabels(viewer, inRange, delta);
    } else {
      apply(viewer, delta, Map.of());
      hideLabels(viewer, inRange.keySet());
    }
    glowGolems(viewer, inRange);
    List<GlossStatus.Target> statusTargets = route == GolemStatusRoute.GLOSS ? golemTargets(inRange) : List.of();
    revealServants(viewer, route, statusTargets);
    if (route == GolemStatusRoute.GLOSS) {
      glossStatus.push(viewer, statusTargets, settings.get().pollMillis());
    } else {
      glossStatus.release(viewerId);
    }
    Optional<NodeWeavingPolicy> nodeWeaving = GolemancyRuntime.nodeWeaving();
    boolean revealsNodes = nodeWeaving.isEmpty() || !nodeWeaving.get().crookOnlyReveal(viewer) || holdsCrook(viewer);
    applyNodes(viewer, nodes.update(viewerId, revealsNodes ? nodesInRange(viewer) : Set.of()));
    if (holdsCrook(viewer) && revealsNodes && (nodeWeaving.isEmpty() || !nodeWeaving.get().hidesOutline(viewer))) {
      outlineLookedAtNode(viewer);
    } else {
      outline.forget(viewerId);
    }
  }

  public void reset(PlayerRef viewer) {
    UUID viewerId = viewer.uuid();
    armed.remove(viewerId);
    statusViewers.remove(viewerId);
    glossStatus.release(viewerId);
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
    if (!AdaptApi.scheduler().runEntity(handle.entity(), task, 1)) {
      pendingRefresh.remove(golem);
    }
  }

  public void forget(GolemHandle handle) {
    UUID golem = handle.entityId();
    unglowFor(golems.drop(golem), golem);
    retireLabel(golem);
  }

  public void refreshViewers() {
    for (UUID viewerId : List.copyOf(armed)) {
      PlayerRef viewer = onlinePlayer(viewerId);
      if (viewer != null) {
        requestUpdate(viewer);
      }
    }
  }

  @Override
  public List<String> details(PlayerRef viewer, LivingRef target) {
    UUID viewerId = viewer.uuid();
    boolean status = statusViewers.contains(viewerId);
    Optional<GolemHandle> golem = GolemancyRuntime.handle(target);
    if (golem.isPresent()) {
      return golemDetails(golem.get(), viewerId, status);
    }
    if (status && target instanceof MobRef servant) {
      return servantDetails(servant, viewerId);
    }
    return List.of();
  }

  public void nodeSpawned(NodeKey key) {
    nodes.drop(key);
    for (UUID viewerId : armed) {
      PlayerRef viewer = onlinePlayer(viewerId);
      if (viewer != null && viewer.world().uid().equals(key.world())) {
        requestUpdate(viewer);
      }
    }
  }

  public void nodeGone(NodeKey key) {
    nodes.drop(key);
  }

  public void selectArea(PlayerRef viewer, NodeKey key) {
    outline.select(viewer.uuid(), key);
    requestUpdate(viewer);
  }

  public void locate(PlayerRef owner, GolemHandle handle) {
    MobRef golem = handle.entity();
    LocateKey key = new LocateKey(owner.uuid(), handle.entityId(), golem.entityId());
    boolean fresh = locates.put(key, AdaptApi.clock().millis() + LOCATE_MILLIS) == null;
    ViewerGlowPort glow = ViewerGlowPort.current();
    if (glow.isAvailable()) {
      glow.set(GlowLayers.GOLEMANCY_LOCATE, golem, owner, LOCATE_COLOR);
    }
    drawLineTo(owner, golem);
    if (fresh && !AdaptApi.scheduler().runEntity(owner, () -> pulseLocate(owner, handle, key), LOCATE_PULSE_TICKS)) {
      endLocate(owner, key);
    }
  }

  public void point(PlayerRef owner, Loc target) {
    Loc eye = owner.eyeLocation();
    WorldRef world = target.world();
    if (world == null || !world.equals(eye.world())) {
      return;
    }
    Vec3 delta = target.position().subtract(eye.position());
    double distance = delta.length();
    if (distance <= LINE_START_OFFSET) {
      return;
    }
    Vec3 step = delta.multiply(LINE_SPACING / distance);
    Vec3 cursor = eye.position().add(step.multiply(LINE_START_OFFSET / LINE_SPACING));
    int points = Math.min(LINE_MAX_POINTS, (int) ((distance - LINE_START_OFFSET) / LINE_SPACING));
    FxHost fx = AdaptApi.platform().fx();
    for (int index = 0; index < points; index++) {
      fx.particle(owner, Particles.END_ROD, cursor.x(), cursor.y(), cursor.z(), 1, 0D, 0D, 0D, 0D, false);
      cursor = cursor.add(step);
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
    statusViewers.clear();
    glossStatus.clear();
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
      PlayerRef viewer = onlinePlayer(viewerId);
      if (viewer == null) {
        forgetViewer(viewerId);
        continue;
      }
      if (pendingUpdates.add(viewerId) && !AdaptApi.scheduler().runEntity(viewer, () -> update(viewer))) {
        pendingUpdates.remove(viewerId);
      }
    }
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerItemHeldEvent e) {
    PlayerRef player = e.player();
    if (isArmed(player.uuid()) || isRevealItem(player.inventory().item(e.newSlot()))) {
      requestUpdate(player);
    }
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerSwapHandItemsEvent e) {
    PlayerRef player = e.player();
    if (isArmed(player.uuid()) || isRevealItem(e.mainHand()) || isRevealItem(e.offHand())) {
      requestUpdate(player);
    }
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(InventoryClickEvent e) {
    PlayerRef player = e.player();
    if (isArmed(player.uuid()) || movesRevealItem(player, e)) {
      requestUpdate(player);
    }
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(InventoryDragEvent e) {
    PlayerRef player = e.player();
    if (isArmed(player.uuid()) || isRevealItem(e.oldCursor())) {
      requestUpdate(player);
    }
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerDropItemEvent e) {
    if (isArmed(e.player().uuid())) {
      requestUpdate(e.player());
    }
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(EntityPickupItemEvent e) {
    if (e.entity() instanceof PlayerRef player && isRevealItem(e.item().item())) {
      requestUpdate(player);
    }
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerJoinEvent e) {
    requestUpdate(e.player());
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerRespawnEvent e) {
    requestUpdate(e.player());
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerChangedWorldEvent e) {
    reset(e.player());
    requestUpdate(e.player());
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerDeathEvent e) {
    reset(e.player());
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerQuitEvent e) {
    forgetViewer(e.player().uuid());
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(EntityDamageEvent e) {
    refreshLabeled(e.entity());
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(EntityRegainHealthEvent e) {
    refreshLabeled(e.entity());
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(EntityDeathEvent e) {
    UUID entity = e.entity().uuid();
    if (!labels.containsKey(entity) && !glowIds.containsKey(entity)) {
      return;
    }
    List<UUID> viewers = new ArrayList<>(golems.drop(entity));
    viewers.addAll(servantGlows.drop(entity));
    servantLabels.drop(entity);
    unglowFor(viewers, entity);
    retireLabel(entity);
  }

  private Map<UUID, GolemHandle> golemsInRange(PlayerRef viewer) {
    GolemRegistry registry = GolemancyRuntime.registry().orElse(null);
    if (registry == null) {
      return Map.of();
    }
    Collection<GolemHandle> owned = registry.loadedOf(viewer.uuid());
    if (owned.isEmpty()) {
      return Map.of();
    }
    UUID viewerId = viewer.uuid();
    Loc center = viewer.location();
    WorldRef world = center.world();
    double range = settings.get().revealRange();
    Map<UUID, GolemHandle> inRange = new HashMap<>(owned.size() * 2);
    for (GolemHandle handle : owned) {
      MobRef golem = handle.entity();
      if (!golem.isValid() || !Objects.equals(world, golem.world())) {
        continue;
      }
      if (AdaptApi.scheduler().isRegionized() && !AdaptApi.scheduler().owns(golem)) {
        continue;
      }
      if (RevealRules.inRevealRange(golem.location().distanceSquared(center), range, golems.isShown(viewerId, handle.entityId()))) {
        inRange.put(handle.entityId(), handle);
      }
    }
    return inRange;
  }

  private Set<NodeKey> nodesInRange(PlayerRef viewer) {
    NodeService service = GolemancyRuntime.nodes().orElse(null);
    Loc center = viewer.location();
    WorldRef world = center.world();
    if (service == null || world == null) {
      return Set.of();
    }
    double range = settings.get().revealRange();
    List<NodeIndex.Entry> candidates = service.index().near(world.uid(), center.x(), center.y(), center.z(), range + RevealRules.LEAVE_MARGIN);
    if (candidates.isEmpty()) {
      return Set.of();
    }
    UUID viewerId = viewer.uuid();
    NodeVisibility visibility = GolemancyRuntime.nodeSettings().visibility();
    int nodeWeaving = visibility == NodeVisibility.ANY_GOLEMANCER ? nodeWeavingLevel(viewer) : 0;
    Set<NodeKey> visible = new HashSet<>(candidates.size() * 2);
    for (NodeIndex.Entry entry : candidates) {
      NodeKey key = entry.key();
      if (!RevealRules.nodeVisible(true, entry.record().owner().equals(viewerId), visibility, nodeWeaving)
          || !RevealRules.inRevealRange(key.distanceSquared(center.x(), center.y(), center.z()), range, nodes.isShown(viewerId, key))) {
        continue;
      }
      Optional<NodeBundle> bundle = service.renderer().bundle(key);
      if (bundle.isEmpty() || (AdaptApi.scheduler().isRegionized() && !AdaptApi.scheduler().owns(bundle.get().icon()))) {
        continue;
      }
      visible.add(key);
    }
    return visible;
  }

  private void outlineLookedAtNode(PlayerRef viewer) {
    Optional<NodeService> service = GolemancyRuntime.nodes();
    if (service.isEmpty()) {
      outline.forget(viewer.uuid());
      return;
    }
    outline.update(viewer, service.get().index(), settings.get().pollTicks());
  }

  private static int nodeWeavingLevel(PlayerRef viewer) {
    Optional<NodeWeavingPolicy> policy = GolemancyRuntime.nodeWeaving();
    return policy.map(nodeWeaving -> nodeWeaving.level(viewer)).orElse(0);
  }

  private static boolean holdsCrook(PlayerRef player) {
    PlayerInventoryRef inventory = player.inventory();
    return RevealRules.holdsCrook(
        GolemItems.kindOf(inventory.mainHand()).orElse(null),
        GolemItems.kindOf(inventory.offHand()).orElse(null));
  }

  private void applyNodes(PlayerRef viewer, RevealTracker.Delta<NodeKey> delta) {
    for (NodeKey node : delta.leaving()) {
      hideNode(viewer, node);
    }
    for (NodeKey node : delta.entering()) {
      showNode(viewer, node);
    }
  }

  private static void showNode(PlayerRef viewer, NodeKey node) {
    Optional<NodeBundle> bundle = GolemancyRuntime.nodes().flatMap(service -> service.renderer().bundle(node));
    if (bundle.isEmpty()) {
      return;
    }
    for (EntityRef entity : bundle.get().entities()) {
      HiddenDisplays.show(viewer, entity);
    }
  }

  private static void hideNode(PlayerRef viewer, NodeKey node) {
    Optional<NodeBundle> bundle = GolemancyRuntime.nodes().flatMap(service -> service.renderer().bundle(node));
    if (bundle.isEmpty()) {
      return;
    }
    for (EntityRef entity : bundle.get().entities()) {
      if (entity.isValid()) {
        HiddenDisplays.hide(viewer, entity);
      }
    }
  }

  private void apply(PlayerRef viewer, RevealTracker.Delta<UUID> delta, Map<UUID, GolemHandle> inRange) {
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

  private static boolean hidesLabels(PlayerRef owner) {
    Optional<GolemLabelPolicy> policy = GolemancyRuntime.labelPolicy();
    return policy.isPresent() && policy.get().hidesLabels(owner);
  }

  private void hideLabels(PlayerRef owner, Set<UUID> golemIds) {
    for (UUID golem : golemIds) {
      if (labels.containsKey(golem)) {
        hideLabel(owner, golem);
      }
    }
  }

  private void syncStayingLabels(PlayerRef viewer, Map<UUID, GolemHandle> inRange, RevealTracker.Delta<UUID> delta) {
    for (Map.Entry<UUID, GolemHandle> entry : inRange.entrySet()) {
      if (delta.entering().contains(entry.getKey())) {
        continue;
      }
      Label label = labels.get(entry.getKey());
      if (label == null || !label.display().isValid()) {
        showLabel(viewer, entry.getValue());
      } else if (AdaptApi.scheduler().owns(entry.getValue().entity())) {
        applyText(entry.getValue(), label);
      }
    }
  }

  private void showLabel(PlayerRef viewer, GolemHandle handle) {
    UUID golem = handle.entityId();
    Label label = labels.get(golem);
    if (label != null && label.display().isValid()) {
      HiddenDisplays.show(viewer, label.display());
      return;
    }
    Long retryAt = labelRetries.get(golem);
    if (retryAt != null && retryAt > AdaptApi.clock().millis()) {
      return;
    }
    if (spawning.add(golem) && !AdaptApi.scheduler().runEntity(handle.entity(), () -> spawnLabel(viewer, handle))) {
      spawning.remove(golem);
    }
  }

  private void spawnLabel(PlayerRef viewer, GolemHandle handle) {
    UUID golem = handle.entityId();
    try {
      MobRef entity = handle.entity();
      if (handle.isRetired() || !entity.isValid() || !golems.isShown(viewer.uuid(), golem)) {
        return;
      }
      attachLabel(viewer, entity, handle.owner(), content(handle, false), settings.get().labelViewRange(), golems);
    } finally {
      spawning.remove(golem);
    }
  }

  private void attachLabel(PlayerRef viewer, EntityRef carrier, UUID owner, GolemLabel.Content content, float viewRange, RevealTracker<UUID> tracker) {
    TextDisplayRef display = HiddenDisplays.spawnLabel(carrier.location(), GolemLabel.compose(content), LABEL_BACKGROUND_ARGB, viewRange);
    display.setTransform(display.transform().withTranslation(new Vec3(0D, LABEL_LIFT, 0D)));
    UUID carrierId = carrier.uuid();
    if (!carrier.addPassenger(display)) {
      display.remove();
      labelRetries.put(carrierId, AdaptApi.clock().millis() + LABEL_RETRY_MILLIS);
      return;
    }
    labelRetries.remove(carrierId);
    Label previous = labels.put(carrierId, new Label(display, owner, content));
    if (previous != null) {
      removeDisplay(previous.display());
    }
    AdaptApi.scheduler().runEntity(viewer, () -> showSpawned(viewer, carrierId, display, tracker));
  }

  private void showSpawned(PlayerRef viewer, UUID carrier, TextDisplayRef display, RevealTracker<UUID> tracker) {
    Label current = labels.get(carrier);
    if (viewer.isOnline() && current != null && current.display() == display && tracker.isShown(viewer.uuid(), carrier)) {
      HiddenDisplays.show(viewer, display);
    }
  }

  private void hideLabel(PlayerRef viewer, UUID golem) {
    labelRetries.remove(golem);
    Label label = labels.remove(golem);
    if (label == null) {
      return;
    }
    hideOwned(viewer, label.display());
    removeDisplay(label.display());
  }

  private static void hideOwned(PlayerRef viewer, TextDisplayRef display) {
    if (!AdaptApi.scheduler().isRegionized() || AdaptApi.scheduler().owns(display)) {
      HiddenDisplays.hide(viewer, display);
    }
  }

  private void retireLabel(UUID golem) {
    labelRetries.remove(golem);
    Label label = labels.remove(golem);
    if (label == null) {
      return;
    }
    PlayerRef owner = onlinePlayer(label.owner());
    if (owner != null) {
      AdaptApi.scheduler().runEntity(owner, () -> hideOwned(owner, label.display()));
    }
    removeDisplay(label.display());
  }

  private void removeDisplay(TextDisplayRef display) {
    if (!AdaptApi.isRunning()) {
      if (!AdaptApi.scheduler().isRegionized() || AdaptApi.scheduler().owns(display)) {
        removeDisplayOwned(display);
      }
      return;
    }
    AdaptApi.scheduler().runEntity(display, () -> removeDisplayOwned(display));
  }

  private void removeDisplayOwned(TextDisplayRef display) {
    if (display.isValid()) {
      display.remove();
    }
  }

  private void applyText(GolemHandle handle, Label label) {
    applyContent(handle.entityId(), label, content(handle, false));
  }

  private void applyContent(UUID carrier, Label label, GolemLabel.Content content) {
    if (content.equals(label.content())) {
      return;
    }
    Label updated = new Label(label.display(), label.owner(), content);
    if (labels.replace(carrier, label, updated)) {
      label.display().setText(GolemLabel.compose(content));
    }
  }

  private GolemLabel.Content content(GolemHandle handle, boolean named) {
    return Text.renderFor(handle.owner(), () -> {
      MobRef golem = handle.entity();
      GolemState state = handle.state();
      String mode = state.mode().displayName().resolve();
      if (state.mode() == GolemMode.WORK) {
        mode += ": " + handle.workStatus().describe(handle.toolNote());
      }
      return new GolemLabel.Content(golemName(handle), golem.health(), GolemVitals.maxHealth(golem), coreNames(GolemMastery.cores(handle)),
          mode, GolemRunes.channels(handle), state.masterwork(), named);
    });
  }

  private static String golemName(GolemHandle handle) {
    GolemState state = handle.state();
    return state.name().map(Markup::fromComponentJson)
        .orElseGet(() -> Markup.escape(Text.key("adapt.golemancy.gui.title").with("body", state.body().displayName()).resolve()));
  }

  private static GolemNameMode nameMode() {
    return GolemancyRuntime.names().map(GolemNames::mode).orElse(GolemNameMode.VANILLA);
  }

  private List<String> golemDetails(GolemHandle handle, UUID viewerId, boolean status) {
    boolean named = !nameMode().showsCustomName();
    if (status && handle.owner().equals(viewerId)) {
      return GolemLabel.legacyLines(content(handle, named));
    }
    if (!named) {
      return List.of();
    }
    return Text.renderFor(viewerId, () -> {
      return List.of(GolemLabel.legacyName(golemName(handle)));
    });
  }

  private List<String> servantDetails(MobRef servant, UUID viewerId) {
    ServantFrame.Frame frame = ServantFrame.frame().filter(found -> found.level(viewerId) >= 1).orElse(null);
    if (frame == null || frame.servants(viewerId).stream().noneMatch((MobRef known) -> known.uuid().equals(servant.uuid()))) {
      return List.of();
    }
    return GolemLabel.legacyLines(servantContent(servant, new ServantView(frame, frame.channels(viewerId)), viewerId));
  }

  private List<GlossStatus.Target> golemTargets(Map<UUID, GolemHandle> inRange) {
    double range = settings.get().labelRange();
    List<GlossStatus.Target> targets = new ArrayList<>(inRange.size() + 2);
    for (GolemHandle handle : inRange.values()) {
      targets.add(new GlossStatus.Target(handle.entity(), range, () -> content(handle, true)));
    }
    return targets;
  }

  private static String coreNames(List<GolemCoreType> cores) {
    List<String> names = new ArrayList<>(cores.size());
    for (GolemCoreType core : cores) {
      names.add(core.displayName().resolve());
    }
    return String.join(CORE_SEPARATOR, names);
  }

  private void glowGolems(PlayerRef viewer, Map<UUID, GolemHandle> inRange) {
    for (GolemHandle handle : inRange.values()) {
      glow(viewer, handle.entity(), RevealRules.glowColor(GolemRunes.channels(handle)));
    }
  }

  private void revealServants(PlayerRef viewer, GolemStatusRoute route, List<GlossStatus.Target> statusTargets) {
    UUID viewerId = viewer.uuid();
    ServantFrame.Frame frame = ServantFrame.frame().filter(found -> found.level(viewerId) >= 1).orElse(null);
    if (frame == null) {
      hideServants(viewer);
      return;
    }
    Map<UUID, MobRef> glowing = servantsInRange(viewer, frame.servants(viewerId), settings.get().revealRange(), servantGlows);
    Map<UUID, MobRef> labeled = route == GolemStatusRoute.LABEL ? servantsInRange(viewer, glowing.values(), frame.labelRange(), servantLabels) : Map.of();
    int channels = frame.channels(viewerId);
    for (UUID servant : servantGlows.update(viewerId, glowing.keySet()).leaving()) {
      unglow(viewer, servant);
    }
    GlowColor color = RevealRules.glowColor(channels);
    for (MobRef servant : glowing.values()) {
      glow(viewer, servant, color);
    }
    RevealTracker.Delta<UUID> delta = servantLabels.update(viewerId, labeled.keySet());
    for (UUID servant : delta.leaving()) {
      hideLabel(viewer, servant);
    }
    ServantView view = new ServantView(frame, channels);
    for (MobRef servant : labeled.values()) {
      showServantLabel(viewer, servant, view, delta.entering().contains(servant.uuid()));
    }
    if (route == GolemStatusRoute.GLOSS) {
      for (MobRef servant : glowing.values()) {
        statusTargets.add(new GlossStatus.Target(servant, frame.labelRange(), () -> servantContent(servant, view, viewerId)));
      }
    }
  }

  private void hideServants(PlayerRef viewer) {
    UUID viewerId = viewer.uuid();
    for (UUID servant : servantLabels.update(viewerId, Set.of()).leaving()) {
      hideLabel(viewer, servant);
    }
    for (UUID servant : servantGlows.update(viewerId, Set.of()).leaving()) {
      unglow(viewer, servant);
    }
  }

  private Map<UUID, MobRef> servantsInRange(PlayerRef viewer, Collection<MobRef> candidates, double range, RevealTracker<UUID> tracker) {
    if (candidates.isEmpty()) {
      return Map.of();
    }
    UUID viewerId = viewer.uuid();
    Loc center = viewer.location();
    WorldRef world = center.world();
    Map<UUID, MobRef> inRange = new HashMap<>(candidates.size() * 2);
    for (MobRef servant : candidates) {
      if (!servant.isValid() || !Objects.equals(world, servant.world()) || (AdaptApi.scheduler().isRegionized() && !AdaptApi.scheduler().owns(servant))) {
        continue;
      }
      UUID servantId = servant.uuid();
      if (RevealRules.inRevealRange(servant.location().distanceSquared(center), range, tracker.isShown(viewerId, servantId))) {
        inRange.put(servantId, servant);
      }
    }
    return inRange;
  }

  private void showServantLabel(PlayerRef viewer, MobRef servant, ServantView view, boolean entering) {
    UUID servantId = servant.uuid();
    Label label = labels.get(servantId);
    if (label != null && label.display().isValid()) {
      if (entering) {
        HiddenDisplays.show(viewer, label.display());
      }
      if (AdaptApi.scheduler().owns(servant)) {
        applyContent(servantId, label, servantContent(servant, view, viewer.uuid()));
      }
      return;
    }
    if (spawning.add(servantId) && !AdaptApi.scheduler().runEntity(servant, () -> spawnServantLabel(viewer, servant, view))) {
      spawning.remove(servantId);
    }
  }

  private void spawnServantLabel(PlayerRef viewer, MobRef servant, ServantView view) {
    UUID servantId = servant.uuid();
    try {
      UUID viewerId = viewer.uuid();
      if (!servant.isValid() || !servantLabels.isShown(viewerId, servantId)) {
        return;
      }
      attachLabel(viewer, servant, viewerId, servantContent(servant, view, viewerId), RevealSettings.viewRange(view.frame().labelRange()), servantLabels);
    } finally {
      spawning.remove(servantId);
    }
  }

  private GolemLabel.Content servantContent(MobRef servant, ServantView view, UUID owner) {
    return Text.renderFor(owner, () -> {
      long seconds = RevealRules.secondsLeft(view.frame().expiresAt(servant), AdaptApi.clock().millis());
      return new GolemLabel.Content(Markup.escape(Text.key("adapt.tragoul.skeletal_servant.name").resolve()), servant.health(),
          GolemVitals.maxHealth(servant), Text.key("adapt.golemancy.bone_frame.time_left").withMarkup("seconds", String.valueOf(seconds)).resolve(), "", view.channels(), false, true);
    });
  }

  private void glow(PlayerRef viewer, EntityRef entity, GlowColor color) {
    ViewerGlowPort coordinator = ViewerGlowPort.current();
    if (coordinator.isAvailable() && coordinator.set(GlowLayers.GOLEMANCY, entity, viewer, color)) {
      glowIds.put(entity.uuid(), entity.entityId());
    }
  }

  private void unglow(PlayerRef viewer, UUID entity) {
    Integer runtimeId = glowIds.remove(entity);
    if (runtimeId != null) {
      ViewerGlowPort.current().unset(GlowLayers.GOLEMANCY, entity, runtimeId, viewer);
    }
  }

  private void unglowFor(List<UUID> viewers, UUID entity) {
    for (UUID viewerId : viewers) {
      PlayerRef viewer = onlinePlayer(viewerId);
      if (viewer != null) {
        AdaptApi.scheduler().runEntity(viewer, () -> unglow(viewer, entity));
      }
    }
    if (viewers.isEmpty()) {
      glowIds.remove(entity);
    }
  }

  private static PlayerRef onlinePlayer(UUID id) {
    return !AdaptApi.isRunning() ? null : AdaptApi.platform().host().player(id);
  }

  private void refreshLabeled(EntityRef entity) {
    if (!labels.containsKey(entity.uuid())) {
      return;
    }
    GolemancyRuntime.handle(entity).ifPresent(this::refresh);
  }

  private void forgetViewer(UUID viewerId) {
    armed.remove(viewerId);
    statusViewers.remove(viewerId);
    glossStatus.forget(viewerId);
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

  private boolean movesRevealItem(PlayerRef player, InventoryClickEvent e) {
    if (isRevealItem(e.currentItem()) || isRevealItem(e.cursor())) {
      return true;
    }
    if (e.click() == ClickKind.NUMBER_KEY) {
      return isRevealItem(player.inventory().item(e.hotbarButton()));
    }
    return e.click() == ClickKind.SWAP_OFFHAND && isRevealItem(player.inventory().offHand());
  }

  private void pulseLocate(PlayerRef owner, GolemHandle handle, LocateKey key) {
    Long until = locates.get(key);
    if (until == null) {
      return;
    }
    MobRef golem = handle.entity();
    if (!owner.isOnline() || AdaptApi.clock().millis() >= until || handle.isRetired() || !golem.isValid()) {
      endLocate(owner, key);
      return;
    }
    drawLineTo(owner, golem);
    if (!AdaptApi.scheduler().runEntity(owner, () -> pulseLocate(owner, handle, key), LOCATE_PULSE_TICKS)) {
      endLocate(owner, key);
    }
  }

  private void endLocate(PlayerRef owner, LocateKey key) {
    locates.remove(key);
    ViewerGlowPort.current().unset(GlowLayers.GOLEMANCY_LOCATE, key.golem(), key.runtimeEntityId(), owner);
  }

  private void drawLineTo(PlayerRef owner, MobRef golem) {
    if (AdaptApi.scheduler().isRegionized() && !AdaptApi.scheduler().owns(golem)) {
      return;
    }
    point(owner, golem.location().add(0D, golem.height() * 0.5D, 0D));
  }

  private record Label(TextDisplayRef display, UUID owner, GolemLabel.Content content) {
  }

  private record LocateKey(UUID viewer, UUID golem, int runtimeEntityId) {
  }

  private record ServantView(ServantFrame.Frame frame, int channels) {
  }
}
