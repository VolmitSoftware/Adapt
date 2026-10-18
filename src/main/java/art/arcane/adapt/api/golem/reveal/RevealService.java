package art.arcane.adapt.api.golem.reveal;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.fx.ViewerGlowCoordinator;
import art.arcane.adapt.api.fx.display.HiddenDisplays;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemVitals;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.tick.TickedObject;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.localization.LanguageAudience;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
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

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

  private final Supplier<RevealSettings> settings;
  private final RevealTracker golems = new RevealTracker();
  private final Set<UUID> armed = ConcurrentHashMap.newKeySet();
  private final Set<UUID> pendingUpdates = ConcurrentHashMap.newKeySet();
  private final Set<UUID> spawning = ConcurrentHashMap.newKeySet();
  private final Set<UUID> pendingRefresh = ConcurrentHashMap.newKeySet();
  private final Map<UUID, Label> labels = new ConcurrentHashMap<>();
  private final Map<LocateKey, Long> locates = new ConcurrentHashMap<>();

  public RevealService(Supplier<RevealSettings> settings) {
    super("golemancy", "golem-reveal", settings.get().pollMillis());
    this.settings = Objects.requireNonNull(settings);
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
      return;
    }
    armed.add(viewerId);
    Map<UUID, GolemHandle> inRange = golemsInRange(viewer);
    RevealTracker.Delta delta = golems.update(viewerId, inRange.keySet());
    apply(viewer, delta, inRange);
    syncStayingLabels(inRange, delta);
  }

  public void reset(Player viewer) {
    UUID viewerId = viewer.getUniqueId();
    armed.remove(viewerId);
    for (UUID golem : golems.reset(viewerId)) {
      hideLabel(viewer, golem);
    }
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
    golems.drop(golem);
    retireLabel(golem);
  }

  public void locate(Player owner, GolemHandle handle) {
    Mob golem = handle.entity();
    LocateKey key = new LocateKey(owner.getUniqueId(), handle.entityId(), golem.getEntityId());
    boolean fresh = locates.put(key, System.currentTimeMillis() + LOCATE_MILLIS) == null;
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
    for (Label label : labels.values()) {
      removeDisplay(label.display());
    }
    labels.clear();
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
    UUID golem = e.getEntity().getUniqueId();
    if (labels.containsKey(golem)) {
      golems.drop(golem);
      retireLabel(golem);
    }
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
      if (RevealRules.withinRange(golem.getLocation().distanceSquared(center), range)) {
        inRange.put(handle.entityId(), handle);
      }
    }
    return inRange;
  }

  private void apply(Player viewer, RevealTracker.Delta delta, Map<UUID, GolemHandle> inRange) {
    for (UUID golem : delta.leaving()) {
      hideLabel(viewer, golem);
    }
    for (UUID golem : delta.entering()) {
      GolemHandle handle = inRange.get(golem);
      if (handle != null) {
        showLabel(viewer, handle);
      }
    }
  }

  private void syncStayingLabels(Map<UUID, GolemHandle> inRange, RevealTracker.Delta delta) {
    for (Map.Entry<UUID, GolemHandle> entry : inRange.entrySet()) {
      Label label = labels.get(entry.getKey());
      if (label == null || delta.entering().contains(entry.getKey()) || !J.isOwnedByCurrentRegion(entry.getValue().entity())) {
        continue;
      }
      applyText(entry.getValue(), label);
    }
  }

  private void showLabel(Player viewer, GolemHandle handle) {
    UUID golem = handle.entityId();
    Label label = labels.get(golem);
    if (label != null && label.display().isValid()) {
      HiddenDisplays.show(viewer, label.display());
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
      GolemLabel.Content content = content(handle);
      TextDisplay display = HiddenDisplays.spawnLabel(entity.getLocation(), GolemLabel.compose(content),
          Color.fromARGB(LABEL_BACKGROUND_ALPHA, 0, 0, 0), settings.get().labelViewRange());
      Transformation base = display.getTransformation();
      display.setTransformation(new Transformation(new Vector3f(0F, LABEL_LIFT, 0F), base.getLeftRotation(), base.getScale(), base.getRightRotation()));
      if (!entity.addPassenger(display)) {
        display.remove();
        return;
      }
      Label previous = labels.put(golem, new Label(display, handle.owner(), content));
      if (previous != null) {
        removeDisplay(previous.display());
      }
      J.runEntity(viewer, () -> showSpawned(viewer, golem, display));
    } finally {
      spawning.remove(golem);
    }
  }

  private void showSpawned(Player viewer, UUID golem, TextDisplay display) {
    Label current = labels.get(golem);
    if (viewer.isOnline() && current != null && current.display() == display && golems.isShown(viewer.getUniqueId(), golem)) {
      HiddenDisplays.show(viewer, display);
    }
  }

  private void hideLabel(Player viewer, UUID golem) {
    Label label = labels.remove(golem);
    if (label == null) {
      return;
    }
    HiddenDisplays.hide(viewer, label.display());
    removeDisplay(label.display());
  }

  private void retireLabel(UUID golem) {
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
    if (display.isValid()) {
      J.runEntity(display, display::remove);
    }
  }

  private void applyText(GolemHandle handle, Label label) {
    GolemLabel.Content content = content(handle);
    if (content.equals(label.content())) {
      return;
    }
    Label updated = new Label(label.display(), label.owner(), content);
    if (labels.replace(handle.entityId(), label, updated)) {
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
      return new GolemLabel.Content(name, golem.getHealth(), GolemVitals.maxHealth(golem),
          AdaptLanguage.text(state.primaryCore().displayName()), AdaptLanguage.text(state.mode().displayName()), state.channels());
    }
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
      Label label = labels.remove(golem);
      if (label != null) {
        removeDisplay(label.display());
      }
    }
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
    if (!owner.isOnline() || System.currentTimeMillis() >= until || handle.isRetired() || !golem.isValid()) {
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
}
