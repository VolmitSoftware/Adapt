/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ArchitectMessages;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.ViewerDisplayDirector;
import art.arcane.adapt.api.recipe.AdaptRecipe;
import art.arcane.adapt.api.recipe.MaterialChar;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.content.item.ChalkWandItem;
import art.arcane.adapt.content.item.ChalkWandItem.Plan;
import art.arcane.adapt.content.item.ChalkWandItem.Plane;
import art.arcane.adapt.content.item.ChalkWandItem.Point;
import art.arcane.adapt.content.item.ChalkWandItem.Tool;
import art.arcane.adapt.content.item.ChalkWandItem.WandData;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.localization.TextKey;
import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ArchitectChalkLine extends SimpleAdaptation<ArchitectChalkLine.Config> {
  private static final int CHALK_LEVELS = 4;
  private static final int MAX_PLAYERS_PER_TICK = 32;
  private static final int MARKERS_PER_RECONCILIATION = 8;
  private static final int HARD_MAX_GUIDE_BLOCKS = 128;
  private static final long HELD_WAND_SCAN_MILLIS = 250L;
  private static final long IDLE_SCAN_MILLIS = Long.MAX_VALUE;
  private final Map<UUID, VisiblePreview> visiblePreviews = new ConcurrentHashMap<>();
  private final Map<UUID, Integer> markerCursors = playerState();
  private final Set<UUID> heldPlannedWands = ConcurrentHashMap.newKeySet();
  private final Set<UUID> pendingRefreshes = ConcurrentHashMap.newKeySet();
  private List<UUID> scanPlayers = List.of();
  private int scanIndex;
  private long nextScanAt;

  public ArchitectChalkLine() {
    super("architect-chalk-line");
    registerConfiguration(ArchitectChalkLine.Config.class);
    setMaxLevel(CHALK_LEVELS);
    setIcon(Material.STRING);
    setInterval(IDLE_SCAN_MILLIS);
    nextScanAt = System.currentTimeMillis() + HELD_WAND_SCAN_MILLIS;
    registerToolRecipe(Tool.STRAIGHTEDGE, List.of("S", "T"));
    registerToolRecipe(Tool.POLYLINE, List.of("S ", " T"));
    registerToolRecipe(Tool.COMPASS, List.of("T", "S"));
    registerToolRecipe(Tool.ARC_BOW, List.of("TS"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.STRING)
        .key("challenge_architect_chalk_line_50")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.STRING)
            .key("challenge_architect_chalk_line_500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_architect_chalk_line_50", "architect.chalk-line.guides-drafted", 50, 300);
    registerMilestone("challenge_architect_chalk_line_500", "architect.chalk-line.guides-drafted", 500, 1000);
  }

  @Override
  protected void normalizeLoadedConfig(Config loadedConfig) {
    loadedConfig.normalizeForPersistence();
  }

  @Override
  protected boolean shouldCanonicalizeConfigOnLoad() {
    return true;
  }

  @Override
  protected void onRuntimeActivated() {
    for (Player player : Bukkit.getOnlinePlayers()) {
      withPlayerThread(player, () -> refreshHeldState(player));
    }
  }

  @Override
  public void addStats(int level, Element v) {
    for (Tool tool : Tool.values()) {
      if (level >= tool.requiredLevel()) {
        v.addLore(C.GREEN + "+ " + AdaptLanguage.text(tool.nameKey()));
      }
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(CraftItemEvent event) {
    if (!(event.getWhoClicked() instanceof Player player) || !(event.getRecipe() instanceof Keyed keyed)) {
      return;
    }
    if (!"adapt".equals(keyed.getKey().getNamespace())) {
      return;
    }

    Tool tool = Tool.fromRecipeKey(keyed.getKey().getKey());
    normalizeStoredLevel(player);
    if (tool == null || getLevel(player) >= tool.requiredLevel()) {
      return;
    }

    event.setCancelled(true);
    Adapt.actionbar(player, C.RED + AdaptLanguage.text(ArchitectMessages.CHALK_LINE_REQUIRES_LEVEL)
        + " " + tool.requiredLevel());
    fx(player.getLocation(), FxPriority.TRANSITION)
        .sound(Sound.BLOCK_NOTE_BLOCK_BASS, 0.45F, 0.7F);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(CrafterCraftEvent event) {
    if ("adapt".equals(event.getRecipe().getKey().getNamespace())
        && Tool.fromRecipeKey(event.getRecipe().getKey().getKey()) != null) {
      event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent event) {
    EquipmentSlot handSlot = event.getHand();
    if (handSlot != null && handSlot != EquipmentSlot.HAND) {
      return;
    }

    Player player = event.getPlayer();
    ItemStack hand = player.getInventory().getItemInMainHand();
    WandData wand = ChalkWandItem.read(hand);
    if (wand == null) {
      return;
    }
    normalizeStoredLevel(player);

    Action action = event.getAction();
    if (player.isSneaking() && (action == Action.LEFT_CLICK_AIR || action == Action.RIGHT_CLICK_AIR)) {
      event.setUseItemInHand(Event.Result.DENY);
      ChalkWandItem.writePlan(hand, Plan.empty());
      replacePreview(player, wand.tool(), Plan.empty(), settings());
      Adapt.actionbar(player, C.GRAY + AdaptLanguage.text(ArchitectMessages.CHALK_LINE_CLEARED));
      fx(player.getLocation(), FxPriority.TRANSITION)
          .sound(Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.4F, 0.9F);
      return;
    }

    if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) {
      return;
    }

    event.setUseItemInHand(Event.Result.DENY);
    event.setUseInteractedBlock(Event.Result.DENY);
    event.setCancelled(true);
    Block clicked = event.getClickedBlock();
    BlockFace face = event.getBlockFace();
    if (clicked == null || face == null) {
      return;
    }

    Block target = clicked.getRelative(face);
    Location targetLocation = target.getLocation();
    int activeLevel = getActiveInteractLevel(player, targetLocation);
    if (activeLevel < wand.tool().requiredLevel()) {
      Adapt.actionbar(player, C.RED + AdaptLanguage.text(ArchitectMessages.CHALK_LINE_REQUIRES_LEVEL)
          + " " + wand.tool().requiredLevel());
      fx(player.getLocation(), FxPriority.TRANSITION)
          .sound(Sound.BLOCK_NOTE_BLOCK_BASS, 0.45F, 0.7F);
      return;
    }

    World world = target.getWorld();
    if (target.getY() < world.getMinHeight() || target.getY() >= world.getMaxHeight()) {
      return;
    }

    Point point = new Point(target.getX(), target.getY(), target.getZ());
    PreviewSettings previewSettings = settings();
    SelectionResult result = action == Action.LEFT_CLICK_BLOCK
        ? selectStart(wand.tool(), world.getUID(), point, Plane.fromFace(face))
        : selectNext(wand.tool(), wand.plan(), world, point, previewSettings);
    if (!result.accepted()) {
      Adapt.actionbar(player, C.RED + AdaptLanguage.text(result.messageKey()));
      fx(targetLocation.add(0.5D, 0.5D, 0.5D), FxPriority.TRANSITION)
          .sound(Sound.BLOCK_NOTE_BLOCK_BASS, 0.4F, 0.8F);
      return;
    }

    if (!ChalkWandItem.writePlan(hand, result.plan())) {
      Adapt.actionbar(player, C.RED + AdaptLanguage.text(ArchitectMessages.CHALK_LINE_INVALID_PLAN));
      return;
    }

    replacePreview(player, wand.tool(), result.plan(), previewSettings);
    Adapt.actionbar(player, C.AQUA + AdaptLanguage.text(result.messageKey()));
    fx(target.getLocation().add(0.5D, 0.5D, 0.5D), FxPriority.GAMEPLAY)
        .sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.45F, result.completed() ? 1.7F : 1.2F);
    if (result.completed()) {
      addStat(player, "architect.chalk-line.guides-drafted", 1);
      xp(player, getConfig().xpPerGuide);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(BlockPlaceEvent event) {
    if (visiblePreviews.isEmpty()) {
      return;
    }
    GuidePoint placed = GuidePoint.of(event.getBlock());
    for (Map.Entry<UUID, VisiblePreview> entry : visiblePreviews.entrySet()) {
      VisiblePreview preview = entry.getValue();
      if (preview.points().contains(placed)) {
        preview.renderedPoints().remove(placed);
        clearMarker(entry.getKey(), preview.tool(), placed);
      }
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent event) {
    clearPlayerState(event.getPlayer().getUniqueId());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    withPlayerThread(player, () -> refreshHeldState(player));
  }

  @Override
  protected List<Listener> createCompanionListeners() {
    if (!PaperCompat.hasClass("io.papermc.paper.event.player.PlayerInventorySlotChangeEvent")) {
      return List.of();
    }

    return List.of(new SlotChangeListener());
  }

  private final class SlotChangeListener implements Listener {
    @EventHandler(priority = EventPriority.MONITOR)
    public void on(PlayerInventorySlotChangeEvent event) {
      Player player = event.getPlayer();
      if (event.getSlot() != player.getInventory().getHeldItemSlot()) {
        return;
      }
      refreshSoon(player);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerGameModeChangeEvent event) {
    refreshSoon(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerRespawnEvent event) {
    refreshSoon(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerItemHeldEvent event) {
    refreshSoon(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerSwapHandItemsEvent event) {
    refreshSoon(event.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerChangedWorldEvent event) {
    hidePreview(event.getPlayer().getUniqueId());
    refreshSoon(event.getPlayer());
  }

  @Override
  public void onTick() {
    if (!hasPreviewRuntimeWork()) {
      parkPreviewRuntime();
      return;
    }

    long now = System.currentTimeMillis();
    if (scanIndex >= scanPlayers.size()) {
      if (now < nextScanAt) {
        setInterval(previewRuntimeInterval(true, nextScanAt - now));
        return;
      }
      LinkedHashSet<UUID> trackedPlayers = new LinkedHashSet<>(heldPlannedWands);
      trackedPlayers.addAll(visiblePreviews.keySet());
      scanPlayers = List.copyOf(trackedPlayers);
      scanIndex = 0;
      nextScanAt = now + HELD_WAND_SCAN_MILLIS;
    }

    int endIndex = scanBatchEnd(scanIndex, scanPlayers.size());
    while (scanIndex < endIndex) {
      UUID playerId = scanPlayers.get(scanIndex++);
      Player player = Bukkit.getPlayer(playerId);
      if (player == null) {
        clearPlayerState(playerId);
        continue;
      }
      withPlayerThread(player, () -> refreshHeldState(player));
    }
    if (!hasPreviewRuntimeWork()) {
      parkPreviewRuntime();
      return;
    }
    setInterval(scanIndex < scanPlayers.size()
        ? MIN_INTERVAL_MILLIS
        : previewRuntimeInterval(true, nextScanAt - now));
  }

  static int scanBatchEnd(int startIndex, int playerCount) {
    return Math.min(Math.max(0, playerCount), Math.max(0, startIndex) + MAX_PLAYERS_PER_TICK);
  }

  static long previewRuntimeInterval(boolean hasVisiblePreviews, long requestedDelay) {
    return hasVisiblePreviews
        ? Math.max(MIN_INTERVAL_MILLIS, requestedDelay)
        : IDLE_SCAN_MILLIS;
  }

  boolean normalizeStoredLevel(PlayerSkillLine line) {
    if (line == null || line.getAdaptationLevel(getName()) <= CHALK_LEVELS) {
      return false;
    }
    line.setAdaptation(this, CHALK_LEVELS);
    return true;
  }

  static int clampGuideBlocks(int configured) {
    return Math.max(16, Math.min(HARD_MAX_GUIDE_BLOCKS, configured));
  }

  static double clampSelectionDistance(double configured) {
    double value = Double.isFinite(configured) ? configured : 96D;
    return Math.max(8D, Math.min(128D, value));
  }

  static int clampPolylineVertices(int configured) {
    return Math.max(2, Math.min(32, configured));
  }

  static double clampCircleRadius(double configured) {
    double value = Double.isFinite(configured) ? configured : 15D;
    return Math.max(1D, Math.min(64D, value));
  }

  static double clampArcRadius(double configured) {
    double value = Double.isFinite(configured) ? configured : 64D;
    return Math.max(2D, Math.min(128D, value));
  }

  static double renderRangeSquared(double configured) {
    double value = Double.isFinite(configured) ? configured : 64D;
    double range = Math.max(8D, Math.min(96D, value));
    return range * range;
  }

  /** A guide marker draws wherever a normal placement would land: empty space or a replaceable occupant. */
  static boolean isGuideSpaceDrawable(Block block) {
    if (block == null) {
      return true;
    }

    Material type = block.getType();
    return type == Material.AIR
        || type == Material.CAVE_AIR
        || type == Material.VOID_AIR
        || PaperCompat.isReplaceable(block);
  }

  static boolean isWithinBuildHeight(List<Point> points, int minHeight, int maxHeight) {
    for (Point point : points) {
      if (point.y() < minHeight || point.y() >= maxHeight) {
        return false;
      }
    }
    return true;
  }

  private void registerToolRecipe(Tool tool, List<String> shapes) {
    registerRecipe(AdaptRecipe.shaped()
        .key(tool.recipeKey())
        .requiredLevel(tool.requiredLevel())
        .ingredient(new MaterialChar('S', Material.STRING))
        .ingredient(new MaterialChar('T', Material.STICK))
        .shapes(shapes)
        .result(ChalkWandItem.create(tool))
        .build());
  }

  private SelectionResult selectStart(Tool tool, UUID worldId, Point point, Plane plane) {
    Plan plan = new Plan(worldId, List.of(point), tool == Tool.COMPASS ? plane : Plane.XZ);
    return SelectionResult.accepted(plan, false, ArchitectMessages.CHALK_LINE_START_SET);
  }

  private SelectionResult selectNext(Tool tool, Plan current, World world, Point point,
                                     PreviewSettings settings) {
    UUID worldId = world.getUID();
    if (current == null || current.isEmpty() || !worldId.equals(current.worldId())) {
      return SelectionResult.rejected(ArchitectMessages.CHALK_LINE_START_REQUIRED);
    }

    List<Point> controlPoints = current.points();
    Point distanceOrigin = tool == Tool.POLYLINE
        ? controlPoints.getLast()
        : controlPoints.getFirst();
    if (ArchitectChalkGeometry.squaredDistance(distanceOrigin, point) > settings.selectionDistanceSquared()) {
      return SelectionResult.rejected(ArchitectMessages.CHALK_LINE_TOO_FAR);
    }
    if (point.equals(controlPoints.getLast())) {
      return SelectionResult.rejected(ArchitectMessages.CHALK_LINE_DUPLICATE_POINT);
    }

    Plan candidate;
    boolean completed;
    TextKey messageKey;
    switch (tool) {
      case STRAIGHTEDGE -> {
        candidate = new Plan(worldId, List.of(controlPoints.getFirst(), point), current.plane());
        completed = true;
        messageKey = ArchitectMessages.CHALK_LINE_GUIDE_READY;
      }
      case POLYLINE -> {
        if (controlPoints.size() >= settings.maxPolylineVertices()) {
          return SelectionResult.rejected(ArchitectMessages.CHALK_LINE_TOO_MANY_VERTICES);
        }
        List<Point> vertices = new ArrayList<>(controlPoints);
        vertices.add(point);
        candidate = new Plan(worldId, vertices, current.plane());
        completed = true;
        messageKey = ArchitectMessages.CHALK_LINE_VERTEX_ADDED;
      }
      case COMPASS -> {
        double radiusSquared = circleRadiusSquared(controlPoints.getFirst(), point, current.plane());
        if (radiusSquared < 1D || radiusSquared > settings.circleRadiusSquared()) {
          return SelectionResult.rejected(ArchitectMessages.CHALK_LINE_INVALID_RADIUS);
        }
        candidate = new Plan(worldId, List.of(controlPoints.getFirst(), point), current.plane());
        completed = true;
        messageKey = ArchitectMessages.CHALK_LINE_GUIDE_READY;
      }
      case ARC_BOW -> {
        List<Point> arcPoints = new ArrayList<>(controlPoints.subList(0, Math.min(2, controlPoints.size())));
        arcPoints.add(point);
        candidate = new Plan(worldId, arcPoints, current.plane());
        completed = arcPoints.size() == 3;
        messageKey = completed
            ? ArchitectMessages.CHALK_LINE_GUIDE_READY
            : ArchitectMessages.CHALK_LINE_THROUGH_POINT_SET;
      }
      default -> throw new IllegalStateException("Unsupported chalk tool " + tool);
    }

    List<Point> geometry = buildGeometry(tool, candidate, settings);
    if (geometry.isEmpty()) {
      return SelectionResult.rejected(ArchitectMessages.CHALK_LINE_INVALID_SHAPE);
    }
    if (geometry.size() > settings.maxGuideBlocks()) {
      return SelectionResult.rejected(ArchitectMessages.CHALK_LINE_GUIDE_TOO_LARGE);
    }
    if (!isWithinBuildHeight(geometry, world.getMinHeight(), world.getMaxHeight())) {
      return SelectionResult.rejected(ArchitectMessages.CHALK_LINE_GUIDE_OUT_OF_BOUNDS);
    }
    return SelectionResult.accepted(candidate, completed, messageKey);
  }

  private void refreshSoon(Player player) {
    UUID playerId = player.getUniqueId();
    if (!pendingRefreshes.add(playerId)) {
      return;
    }

    if (!J.runEntity(player, () -> {
      pendingRefreshes.remove(playerId);
      if (!isRuntimeRegistered()) {
        return;
      }
      if (player.isOnline()) {
        refreshHeldState(player);
      } else {
        clearPlayerState(playerId);
      }
    }, 1)) {
      pendingRefreshes.remove(playerId);
      clearPlayerState(playerId);
    }
  }

  private void refreshHeldState(Player player) {
    normalizeStoredLevel(player);
    WandData wand = ChalkWandItem.read(player.getInventory().getItemInMainHand());
    UUID playerId = player.getUniqueId();
    if (wand == null || wand.plan() == null || wand.plan().isEmpty()) {
      heldPlannedWands.remove(playerId);
      hidePreview(playerId);
      return;
    }

    heldPlannedWands.add(playerId);
    wakePreviewRuntime();
    if (getActiveLevel(player) < wand.tool().requiredLevel()) {
      hidePreview(player.getUniqueId());
      return;
    }

    PreviewSettings previewSettings = settings();
    VisiblePreview current = visiblePreviews.get(player.getUniqueId());
    if (current == null || !current.matches(wand, previewSettings)) {
      replacePreview(player, wand.tool(), wand.plan(), previewSettings);
      return;
    }
    reconcilePreviewBatch(player, current);
  }

  private void normalizeStoredLevel(Player player) {
    AdaptPlayer adaptPlayer = getPlayer(player);
    PlayerSkillLine line = adaptPlayer.getData().getSkillLineNullable(getSkill().getName());
    normalizeStoredLevel(line);
  }

  private void replacePreview(Player player, Tool tool, Plan plan, PreviewSettings settings) {
    UUID playerId = player.getUniqueId();
    List<Point> geometry = buildGeometry(tool, plan, settings);
    if (geometry.size() > settings.maxGuideBlocks()) {
      geometry = List.of();
    }
    World planWorld = plan == null || plan.worldId() == null ? null : Bukkit.getWorld(plan.worldId());
    if (!geometry.isEmpty() && (planWorld == null
        || !isWithinBuildHeight(geometry, planWorld.getMinHeight(), planWorld.getMaxHeight()))) {
      geometry = List.of();
    }
    List<GuidePoint> guidePoints = toGuidePoints(plan, geometry);
    if (guidePoints.isEmpty()) {
      hidePreview(playerId);
      return;
    }
    VisiblePreview preview = VisiblePreview.create(tool, plan, guidePoints, settings);
    visiblePreviews.put(playerId, preview);
    markerCursors.put(playerId, 0);
    wakePreviewRuntime();
    ViewerDisplayDirector.clearViewer(getName(), playerId);
    Location viewerLocation = player.getLocation();
    for (GuidePoint point : guidePoints) {
      reconcileMarker(player, viewerLocation, preview, point);
    }
  }

  private void reconcilePreviewBatch(Player player, VisiblePreview preview) {
    List<GuidePoint> orderedPoints = preview.orderedPoints();
    if (orderedPoints.isEmpty()) {
      return;
    }

    UUID playerId = player.getUniqueId();
    int start = Math.floorMod(markerCursors.getOrDefault(playerId, 0), orderedPoints.size());
    int count = Math.min(MARKERS_PER_RECONCILIATION, orderedPoints.size());
    Location viewerLocation = player.getLocation();
    for (int offset = 0; offset < count; offset++) {
      GuidePoint point = orderedPoints.get((start + offset) % orderedPoints.size());
      reconcileMarker(player, viewerLocation, preview, point);
    }
    markerCursors.put(playerId, (start + count) % orderedPoints.size());
  }

  private void reconcileMarker(Player player, Location viewerLocation, VisiblePreview preview,
                               GuidePoint point) {
    UUID playerId = player.getUniqueId();
    World world = Bukkit.getWorld(point.worldId());
    if (world == null
        || viewerLocation.getWorld() != world
        || distanceSquared(viewerLocation, point) > preview.settings().renderRangeSquared()) {
      if (preview.renderedPoints().remove(point)) {
        clearMarker(playerId, preview.tool(), point);
      }
      return;
    }

    Location markerLocation = new Location(world, point.x(), point.y(), point.z());
    J.runAt(markerLocation, () -> {
      VisiblePreview current = visiblePreviews.get(playerId);
      if (current != preview || !current.points().contains(point)) {
        return;
      }
      if (!world.isChunkLoaded(point.x() >> 4, point.z() >> 4)) {
        if (preview.renderedPoints().remove(point)) {
          clearMarker(playerId, preview.tool(), point);
        }
        return;
      }
      if (!isGuideSpaceDrawable(markerLocation.getBlock())) {
        if (preview.renderedPoints().remove(point)) {
          clearMarker(playerId, preview.tool(), point);
        }
        return;
      }
      if (preview.renderedPoints().contains(point)
          && ViewerDisplayDirector.isShowing(
              getName(), point.key(preview.tool()), playerId, markerLocation)) {
        return;
      }
      preview.renderedPoints().remove(point);
      if (!preview.renderedPoints().add(point)) {
        return;
      }

      BlockData displayData = displayMaterial(preview.tool()).createBlockData();
      boolean scheduled = ViewerDisplayDirector.showPersistentBlock(
          getName(),
          point.key(preview.tool()),
          player,
          markerLocation,
          displayData,
          displayColor(preview.tool())
      );
      if (!scheduled) {
        preview.renderedPoints().remove(point);
      }
    });
  }

  private void clearMarker(UUID playerId, Tool tool, GuidePoint point) {
    ViewerDisplayDirector.clearViewerKey(getName(), point.key(tool), playerId);
  }

  private void hidePreview(UUID playerId) {
    VisiblePreview removed = visiblePreviews.remove(playerId);
    markerCursors.remove(playerId);
    if (removed != null) {
      ViewerDisplayDirector.clearViewer(getName(), playerId);
    }
    parkPreviewRuntime();
  }

  private void clearPlayerState(UUID playerId) {
    pendingRefreshes.remove(playerId);
    heldPlannedWands.remove(playerId);
    visiblePreviews.remove(playerId);
    markerCursors.remove(playerId);
    ViewerDisplayDirector.clearViewer(getName(), playerId);
    parkPreviewRuntime();
  }

  private void wakePreviewRuntime() {
    setInterval(HELD_WAND_SCAN_MILLIS);
    if (!isBursting()) {
      retick();
    }
  }

  private void parkPreviewRuntime() {
    if (hasPreviewRuntimeWork()) {
      return;
    }
    setInterval(IDLE_SCAN_MILLIS);
    if (hasPreviewRuntimeWork()) {
      wakePreviewRuntime();
    }
  }

  private boolean hasPreviewRuntimeWork() {
    return !heldPlannedWands.isEmpty() || !visiblePreviews.isEmpty();
  }

  private List<Point> buildGeometry(Tool tool, Plan plan, PreviewSettings settings) {
    if (plan == null || plan.isEmpty() || !isPlanWithinLimits(tool, plan, settings)) {
      return List.of();
    }

    List<Point> points = plan.points();
    List<Point> geometry = switch (tool) {
      case STRAIGHTEDGE -> points.size() == 1
          ? List.of(points.getFirst())
          : ArchitectChalkGeometry.line(points.getFirst(), points.get(1));
      case POLYLINE -> ArchitectChalkGeometry.polyline(points);
      case COMPASS -> points.size() == 1
          ? List.of(points.getFirst())
          : circleWithCenter(points.getFirst(), points.get(1), plan.plane());
      case ARC_BOW -> {
        if (points.size() == 1) {
          yield List.of(points.getFirst());
        }
        if (points.size() == 2) {
          yield ArchitectChalkGeometry.line(points.getFirst(), points.get(1));
        }
        yield ArchitectChalkGeometry.arc(
            points.getFirst(),
            points.get(1),
            points.get(2),
            settings.maxArcRadius()
        );
      }
    };
    return geometry;
  }

  private boolean isPlanWithinLimits(Tool tool, Plan plan, PreviewSettings settings) {
    List<Point> points = plan.points();
    int maximumPoints = switch (tool) {
      case STRAIGHTEDGE, COMPASS -> 2;
      case ARC_BOW -> 3;
      case POLYLINE -> settings.maxPolylineVertices();
    };
    if (points.isEmpty() || points.size() > maximumPoints) {
      return false;
    }

    for (int index = 1; index < points.size(); index++) {
      Point origin = tool == Tool.POLYLINE ? points.get(index - 1) : points.getFirst();
      if (ArchitectChalkGeometry.squaredDistance(origin, points.get(index))
          > settings.selectionDistanceSquared()) {
        return false;
      }
    }
    if (tool == Tool.COMPASS && points.size() == 2) {
      double radiusSquared = circleRadiusSquared(points.getFirst(), points.get(1), plan.plane());
      return radiusSquared >= 1D && radiusSquared <= settings.circleRadiusSquared();
    }
    return true;
  }

  private List<Point> circleWithCenter(Point center, Point radiusPoint, Plane plane) {
    Set<Point> points = new LinkedHashSet<>();
    points.add(center);
    points.addAll(ArchitectChalkGeometry.circle(center, radiusPoint, plane));
    return List.copyOf(points);
  }

  private List<GuidePoint> toGuidePoints(Plan plan, List<Point> geometry) {
    if (plan == null || plan.worldId() == null || geometry.isEmpty()) {
      return List.of();
    }
    List<GuidePoint> guidePoints = new ArrayList<>(geometry.size());
    for (Point point : geometry) {
      guidePoints.add(new GuidePoint(plan.worldId(), point.x(), point.y(), point.z()));
    }
    return List.copyOf(guidePoints);
  }

  private double circleRadiusSquared(Point center, Point radiusPoint, Plane plane) {
    long first;
    long second;
    switch (plane) {
      case XY -> {
        first = (long) center.x() - radiusPoint.x();
        second = (long) center.y() - radiusPoint.y();
      }
      case XZ -> {
        first = (long) center.x() - radiusPoint.x();
        second = (long) center.z() - radiusPoint.z();
      }
      case YZ -> {
        first = (long) center.y() - radiusPoint.y();
        second = (long) center.z() - radiusPoint.z();
      }
      default -> throw new IllegalStateException("Unsupported chalk plane " + plane);
    }
    return (first * first) + (second * second);
  }

  private double distanceSquared(Location location, GuidePoint point) {
    double deltaX = location.getX() - (point.x() + 0.5D);
    double deltaY = location.getY() - (point.y() + 0.5D);
    double deltaZ = location.getZ() - (point.z() + 0.5D);
    return (deltaX * deltaX) + (deltaY * deltaY) + (deltaZ * deltaZ);
  }

  private Material displayMaterial(Tool tool) {
    return switch (tool) {
      case STRAIGHTEDGE -> Material.WHITE_STAINED_GLASS;
      case POLYLINE -> Material.LIME_STAINED_GLASS;
      case COMPASS -> Material.LIGHT_BLUE_STAINED_GLASS;
      case ARC_BOW -> Material.MAGENTA_STAINED_GLASS;
    };
  }

  private Color displayColor(Tool tool) {
    return switch (tool) {
      case STRAIGHTEDGE -> Color.fromRGB(245, 245, 245);
      case POLYLINE -> Color.fromRGB(100, 255, 100);
      case COMPASS -> Color.fromRGB(100, 210, 255);
      case ARC_BOW -> Color.fromRGB(255, 100, 220);
    };
  }

  private PreviewSettings settings() {
    Config config = getConfig();
    double selectionDistance = clampSelectionDistance(config.maxSelectionDistance);
    double circleRadius = clampCircleRadius(config.maxCircleRadius);
    return new PreviewSettings(
        selectionDistance * selectionDistance,
        clampPolylineVertices(config.maxPolylineVertices),
        circleRadius * circleRadius,
        clampArcRadius(config.maxArcRadius),
        clampGuideBlocks(config.maxGuideBlocks),
        renderRangeSquared(config.renderRangeBlocks)
    );
  }

  @Override
  public void unregister() {
    super.unregister();
    ViewerDisplayDirector.clearChannel(getName());
    visiblePreviews.clear();
    markerCursors.clear();
    heldPlannedWands.clear();
    pendingRefreshes.clear();
    scanPlayers = List.of();
    scanIndex = 0;
  }

  private record GuidePoint(UUID worldId, int x, int y, int z) {
    private static GuidePoint of(Block block) {
      return new GuidePoint(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    private String key(Tool tool) {
      return tool.id() + ":" + worldId + ":" + x + ":" + y + ":" + z;
    }
  }

  private record PreviewSettings(double selectionDistanceSquared, int maxPolylineVertices,
                                 double circleRadiusSquared, double maxArcRadius,
                                 int maxGuideBlocks, double renderRangeSquared) {
  }

  private record VisiblePreview(Tool tool, Plan plan, List<GuidePoint> orderedPoints,
                                Set<GuidePoint> points, Set<GuidePoint> renderedPoints,
                                PreviewSettings settings) {
    private static VisiblePreview create(Tool tool, Plan plan, List<GuidePoint> points,
                                         PreviewSettings settings) {
      return new VisiblePreview(
          tool,
          plan,
          points,
          Set.copyOf(points),
          ConcurrentHashMap.newKeySet(),
          settings
      );
    }

    private boolean matches(WandData wand, PreviewSettings previewSettings) {
      return tool == wand.tool() && plan.equals(wand.plan()) && settings.equals(previewSettings);
    }
  }

  private record SelectionResult(Plan plan, boolean accepted, boolean completed, TextKey messageKey) {
    private static SelectionResult accepted(Plan plan, boolean completed, TextKey messageKey) {
      return new SelectionResult(plan, true, completed, messageKey);
    }

    private static SelectionResult rejected(TextKey messageKey) {
      return new SelectionResult(Plan.empty(), false, false, messageKey);
    }
  }

  @ConfigDescription("Craft persistent chalk guide wands for straight lines, polylines, circles, and arcs.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum distance in blocks between selected control points.", impact = "Higher values permit larger guide segments but increase potential display work.")
    double maxSelectionDistance = 96D;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum number of control vertices stored by a polyline wand.", impact = "Higher values permit more complex polylines before a new guide is required.")
    int maxPolylineVertices = 12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum circle radius in blocks.", impact = "Higher values permit larger circle guides and may create more markers.")
    double maxCircleRadius = 15D;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum computed radius in blocks for a three-point arc.", impact = "Higher values accept broader arcs whose centers may lie farther from their selected points.")
    double maxArcRadius = 64D;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum block-display markers in one chalk guide.", impact = "Higher values allow larger guides at greater entity and reconciliation cost.")
    int maxGuideBlocks = 96;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum distance in blocks at which the held wand renders its private guide.", impact = "Higher values keep more distant markers active for the owner.")
    double renderRangeBlocks = 64D;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Adaptation xp granted whenever a complete chalk guide is drafted or extended.", impact = "Higher values speed up progression while using chalk wands.")
    double xpPerGuide = 3D;

    public Config() {
      baseCost = 3;
      costFactor = 0.4D;
      initialCost = 1;
      normalizeForPersistence();
    }

    void normalizeForPersistence() {
      maxLevel = CHALK_LEVELS;
    }
  }
}
