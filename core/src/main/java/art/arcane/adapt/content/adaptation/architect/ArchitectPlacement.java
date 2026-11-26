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

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.api.adaptation.RunsWithoutLearnedAdaptation;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.batch.BuildBatch;
import art.arcane.adapt.api.batch.BuildBatches;
import art.arcane.adapt.api.batch.BuildResult;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.mutation.upgrade.UpgradeLoans;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.adaptation.architect.upgraded.WreckingHand;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ArchitectMessages;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.display.BlockDisplayRef;
import art.arcane.adapt.platform.entity.display.Brightness;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

public class ArchitectPlacement extends SimpleAdaptation<ArchitectPlacement.Config> {
  public static final String ID = "architect-placement";
  public static final PlayerPreference<Control> CONTROL = new PlayerPreference<>(Control.class,
      new PlayerPreference.Definition<>("control", ArchitectMessages.ARCHITECTPLACEMENT_PREFERENCE_CONTROL, Control.SNEAK, List.of(
          new PlayerPreference.Choice<>(Control.SNEAK, ArchitectMessages.ARCHITECTPLACEMENT_PREFERENCE_CONTROL_SNEAK, Id.minecraft("leather_boots"), 1),
          new PlayerPreference.Choice<>(Control.ARMED, ArchitectMessages.ARCHITECTPLACEMENT_PREFERENCE_CONTROL_ARMED, Id.minecraft("bricks"), 1))));
  public static final PlayerPreference<CommonPreferences.Toggle> PREVIEW = CommonPreferences.toggle("preview", ArchitectMessages.ARCHITECTPLACEMENT_PREFERENCE_PREVIEW, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Scale> LIMIT = CommonPreferences.scale("limit", ArchitectMessages.ARCHITECTPLACEMENT_PREFERENCE_LIMIT);
  public static final PlayerPreference<Materials> MATERIALS = new PlayerPreference<>(Materials.class,
      new PlayerPreference.Definition<>("materials", ArchitectMessages.ARCHITECTPLACEMENT_PREFERENCE_MATERIALS, Materials.ALL, List.of(
          new PlayerPreference.Choice<>(Materials.ALL, ArchitectMessages.ARCHITECTPLACEMENT_PREFERENCE_MATERIALS_ALL, Id.minecraft("stone"), 1),
          new PlayerPreference.Choice<>(Materials.WOOD, ArchitectMessages.ARCHITECTPLACEMENT_PREFERENCE_MATERIALS_WOOD, Id.minecraft("oak_planks"), 1),
          new PlayerPreference.Choice<>(Materials.STONE, ArchitectMessages.ARCHITECTPLACEMENT_PREFERENCE_MATERIALS_STONE, Id.minecraft("stone_bricks"), 1),
          new PlayerPreference.Choice<>(Materials.GLASS, ArchitectMessages.ARCHITECTPLACEMENT_PREFERENCE_MATERIALS_GLASS, Id.minecraft("glass"), 1))));

  private static final int MAX_PREVIEW_OWNERS_PER_TICK = 64;
  private static final int MAX_DISPLAY_WORK_PER_TICK = 256;
  private static final int MAX_DISPLAY_REMOVALS_PER_TICK = 256;
  private static final int MAX_ACTIVE_PREVIEW_DISPLAYS = 2048;
  private static final long PREVIEW_CYCLE_MILLIS = 500L;
  private static final long VIEWPORT_DEBOUNCE_MILLIS = 150L;
  private static final int MAX_FACE_REACH = 3;
  private static final int FACE_BATCH_PACE = 64;
  private final Map<UUID, Map<BlockRef, BlockFace>> totalMap = new ConcurrentHashMap<>();
  private final Map<UUID, Map<PreviewKey, BlockDisplayRef>> previewDisplays = new ConcurrentHashMap<>();
  private final Map<UUID, ViewportSignature> viewportSignatures = new ConcurrentHashMap<>();
  private final Map<UUID, Long> lastViewportCheck = new ConcurrentHashMap<>();
  private final Set<PreviewOwnerKey> pendingPreviewSpawns = ConcurrentHashMap.newKeySet();
  private final Map<Long, Boolean> pendingSneakPreviews = new ConcurrentHashMap<>();
  private final Queue<EntityRef> displayRemovalQueue = new ConcurrentLinkedQueue<>();
  private final PreviewWorkBudget previewWorkBudget = new PreviewWorkBudget(MAX_DISPLAY_WORK_PER_TICK);
  private final PreviewCapacity previewCapacity = new PreviewCapacity(MAX_ACTIVE_PREVIEW_DISPLAYS);
  private List<UUID> previewOwners = List.of();
  private int previewOwnerIndex;
  private long nextPreviewCycleAt;

  public ArchitectPlacement() {
    super("architect-placement");
    registerConfiguration(ArchitectPlacement.Config.class);
    setIcon(Id.minecraft("scaffolding"));
    setInterval(PREVIEW_CYCLE_MILLIS);
    nextPreviewCycleAt = System.currentTimeMillis() + PREVIEW_CYCLE_MILLIS;
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("bricks"))
        .key("challenge_architect_placement_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("bricks"))
            .key("challenge_architect_placement_25k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_architect_placement_1k", "architect.placement.blocks-placed", 1000, 300);
    registerMilestone("challenge_architect_placement_25k", "architect.placement.blocks-placed", 25000, 1500);
  }

  private boolean acceptsMaterial(PlayerRef player, Id material) {
    String name = material.path().toUpperCase(Locale.ROOT);
    return switch (preference(player, MATERIALS)) {
      case ALL -> true;
      case WOOD -> name.endsWith("_PLANKS") || name.endsWith("_LOG") || name.endsWith("_WOOD") || name.endsWith("_STEM") || name.endsWith("_HYPHAE");
      case STONE -> name.contains("STONE") || name.contains("BRICK") || name.contains("DEEPSLATE");
      case GLASS -> name.contains("GLASS");
    };
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, CONTROL, PREVIEW, LIMIT, MATERIALS);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    clearPlayerPreview(player.getPlayer().uuid());
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + AdaptLanguage.text(ArchitectMessages.PLACEMENT_LORE3));
  }

  private boolean buildingArmed(PlayerRef player) {
    return player.isSneaking() || preference(player, CONTROL) == Control.ARMED;
  }

  private static ItemOps items() {
    return AdaptCore.platform().host().items();
  }

  private BlockFace getBlockFace(PlayerRef player) {
    List<BlockRef> lastTwoTargetBlocks = player.lastTwoTargetBlocks(5);
    if (lastTwoTargetBlocks.size() != 2 || !items().isOccluding(lastTwoTargetBlocks.get(1).type())) {
      return null;
    }
    BlockRef targetBlock = lastTwoTargetBlocks.get(1);
    BlockRef adjacentBlock = lastTwoTargetBlocks.get(0);
    return faceBetween(targetBlock, adjacentBlock);
  }

  static BlockFace faceBetween(BlockRef from, BlockRef to) {
    int dx = to.x() - from.x();
    int dy = to.y() - from.y();
    int dz = to.z() - from.z();
    for (BlockFace face : BlockFace.values()) {
      if (face.dx() == dx && face.dy() == dy && face.dz() == dz) {
        return face;
      }
    }
    return null;
  }

  private static BlockRef targetBlock(PlayerRef player) {
    List<BlockRef> blocks = player.lastTwoTargetBlocks(5);
    return blocks.isEmpty() ? null : blocks.getLast();
  }

  @Override
  public int getActiveLevel(PlayerRef p) {
    return UpgradeLoans.level(this, super.getActiveLevel(p), (Adaptation<?> upgrade) -> upgrade.getActiveLevel(p));
  }

  @Override
  public int getActiveLevel(PlayerRef p, Predicate<PlayerRef> requirement) {
    return UpgradeLoans.level(this, super.getActiveLevel(p, requirement),
        (Adaptation<?> upgrade) -> upgrade.getActiveLevel(p, requirement));
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    clearPlayerPreview(e.player().uuid());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockPlaceEvent e) {
    PlayerRef p = e.player();
    withPlayerThread(p, e, () -> {
      if (Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(p)) {
        return;
      }

      UUID id = p.uuid();
      if (getActiveLevel(p, this::buildingArmed) <= 0) {
        return;
      }

      Map<BlockRef, BlockFace> blocks = totalMap.get(id);
      if (blocks == null || blocks.isEmpty()) {
        return;
      }
      if (!ownsPlacementFootprint(p, e.block(), blocks)) {
        return;
      }

      ItemRef hand = e.itemInHand();
      BlockRef first = null;
      for (BlockRef candidate : blocks.keySet()) {
        first = candidate;
        break;
      }
      if (!items().isBlock(hand.type()) || first == null || !first.type().equals(hand.type())) {
        return;
      }

      if (hand.amount() < blocks.size()) {
        p.sendMessage(C.GRAY + "[" + C.DARK_RED + "Adapt" + C.GRAY + "]: " + C.RED + AdaptLanguage.text(
            ArchitectMessages.PLACEMENT_REQUIREMENT,
            trusted("count", C.GREEN + String.valueOf(blocks.size()) + C.RED)
        ));
        fx(p.location(), FxPriority.TRANSITION).sound(Sounds.BLOCK_DISPENSER_FAIL, 0.4f, 1.0f);
        return;
      }

      placeFaceBatch(p, e.block(), blocks, hand, getValue(e.block()));
      clearPlayerPreview(id);
    });
  }

  /**
   * One build batch of real, paid placements of the single-item state beside the block the player placed, which
   * Wrecking Hand can undo. It pays no skill XP, so placing and undoing farms nothing.
   */
  private void placeFaceBatch(PlayerRef p, BlockRef placed, Map<BlockRef, BlockFace> blocks, ItemRef hand, double value) {
    BuildBatch.Builder batch = BuildBatch.of(this, p)
        .pace(FACE_BATCH_PACE)
        .undoable(WreckingHand.kept(p));
    int planned = 0;
    for (Map.Entry<BlockRef, BlockFace> entry : blocks.entrySet()) {
      BlockRef source = entry.getKey();
      BlockFace face = entry.getValue();
      if (source == null || face == null) {
        continue;
      }
      BlockRef relative = source.relative(face);
      if (relative.equals(placed) || !relative.type().isAir()) {
        continue;
      }
      batch.place(relative, ArchitectPattern.single(source.state()), hand.copyWithAmount(1));
      planned++;
    }
    if (planned == 0) {
      return;
    }
    batch.onDone((BuildResult result) -> {
      if (result.placed() <= 0) {
        return;
      }
      addStat(p, "blocks.placed", result.placed());
      addStat(p, "blocks.placed.value", value * result.placed());
      addStat(p, "architect.placement.blocks-placed", result.placed());
      UpgradeLoans.landed(this, p);
      WreckingHand.site(p, result.blocks());
      fx(placed.location().add(0.5, 0.5, 0.5), FxPriority.GAMEPLAY)
          .dustRing(0.8D, 16, 1.0F)
          .sound(Sounds.BLOCK_AMETHYST_BLOCK_PLACE, 0.5f, 1.2f + Math.min(0.6f, result.placed() * 0.05f));
    });
    BuildBatches.get().start(batch.build());
  }

  private boolean ownsPlacementFootprint(PlayerRef player, BlockRef placed,
                                         Map<BlockRef, BlockFace> blocks) {
    if (!ownsBlock(player, placed)) {
      return false;
    }
    for (Map.Entry<BlockRef, BlockFace> entry : blocks.entrySet()) {
      BlockRef source = entry.getKey();
      BlockFace face = entry.getValue();
      if (source == null || face == null) {
        continue;
      }
      if (!ownsBlock(player, source) || !ownsBlock(player, source.relative(face))) {
        return false;
      }
    }
    return true;
  }

  private boolean ownsBlock(PlayerRef player, BlockRef block) {
    return !Tasks.isFoliaThreading()
        || (Tasks.isOwnedByCurrentRegion(player) && Tasks.isOwnedByCurrentRegion(block.location()));
  }


  @Handle
  public void on(PlayerToggleSneakEvent e) {
    PlayerRef p = e.player();
    UUID id = p.uuid();
    if (!e.isSneaking() && preference(p, CONTROL) == Control.SNEAK) {
      clearPlayerPreview(id);
      return;
    }
    if (!items().isBlock(p.inventory().mainHand().type())) {
      return;
    }

    long dispatch = e.dispatchId();
    pendingSneakPreviews.put(dispatch, Boolean.FALSE);
    if (!Tasks.runEntity(p, () -> startSneakingPreview(p, e), 1)) {
      pendingSneakPreviews.remove(dispatch);
      clearPlayerPreview(id);
    }
  }

  @ReceiveCancelledEvents
  @Handle(priority = EventPriority.MONITOR)
  public void onSettled(PlayerToggleSneakEvent e) {
    pendingSneakPreviews.computeIfPresent(e.dispatchId(), (Long dispatch, Boolean cancelled) -> e.isCancelled());
  }

  private void startSneakingPreview(PlayerRef player, PlayerToggleSneakEvent event) {
    boolean cancelled = event.isCancelled() || Boolean.TRUE.equals(pendingSneakPreviews.remove(event.dispatchId()));
    if (!isRuntimeRegistered() || cancelled || !player.isOnline() || !buildingArmed(player)
        || getActiveLevel(player) <= 0) {
      clearPlayerPreview(player.uuid());
      return;
    }
    Id handMaterial = player.inventory().mainHand().type();
    if (!items().isBlock(handMaterial) || handMaterial.isAir()) {
      clearPlayerPreview(player.uuid());
      return;
    }
    BlockRef block = targetBlock(player);
    runPlayerViewport(getBlockFace(player), block, handMaterial, player);
  }


  @Handle(priority = EventPriority.MONITOR)
  @RunsWithoutLearnedAdaptation
  public void on(PlayerMoveEvent e) {
    PlayerRef p = e.player();
    if (!buildingArmed(p) || !items().isBlock(p.inventory().mainHand().type())) {
      clearResidualPreview(p);
      return;
    }

    UUID id = p.uuid();
    long now = System.currentTimeMillis();
    Long previousCheck = lastViewportCheck.put(id, now);
    if (previousCheck != null && now - previousCheck < VIEWPORT_DEBOUNCE_MILLIS) {
      return;
    }

    withPlayerThread(p, e, () -> {
      int level = getActiveLevel(p);
      if (level <= 0) {
        clearPlayerPreview(id);
        return;
      }

      if (buildingArmed(p) && items().isBlock(p.inventory().mainHand().type())) {
        BlockRef block = targetBlock(p);
        Id currentHandMaterial = p.inventory().mainHand().type();
        if (currentHandMaterial.isAir()) {
          return;
        }
        BlockFace viewPortBlock = getBlockFace(p);
        runPlayerViewport(viewPortBlock, block, currentHandMaterial, p);
      }
    });
  }

  public void runPlayerViewport(BlockFace viewPortBlock, BlockRef block, Id handMaterial, PlayerRef p) {
    UUID id = p.uuid();
    if (viewPortBlock == null || block == null || handMaterial == null || handMaterial.isAir() || !acceptsMaterial(p, handMaterial)) {
      clearPlayerPreview(id);
      return;
    }

    ViewportSignature signature = ViewportSignature.of(block, viewPortBlock, handMaterial);
    if (signature.equals(viewportSignatures.get(id)) && totalMap.containsKey(id)) {
      return;
    }

    Map<BlockRef, BlockFace> map = new HashMap<>();
    WorldRef world = block.world();
    int reach = faceReach(p);
    int maxBlocks = clampPreviewBlocks(getConfig().maxBlocks, reach);

    if (viewPortBlock == BlockFace.NORTH || viewPortBlock == BlockFace.SOUTH) {
      for (int x = block.x() - reach; x <= block.x() + reach; x++) {
        for (int y = block.y() - reach; y <= block.y() + reach; y++) {
          addViewportEntry(map, maxBlocks, world.blockAt(x, y, block.z()), viewPortBlock, handMaterial);
        }
      }
    } else if (viewPortBlock == BlockFace.EAST || viewPortBlock == BlockFace.WEST) {
      for (int z = block.z() - reach; z <= block.z() + reach; z++) {
        for (int y = block.y() - reach; y <= block.y() + reach; y++) {
          addViewportEntry(map, maxBlocks, world.blockAt(block.x(), y, z), viewPortBlock, handMaterial);
        }
      }
    } else if (viewPortBlock == BlockFace.UP || viewPortBlock == BlockFace.DOWN) {
      for (int z = block.z() - reach; z <= block.z() + reach; z++) {
        for (int x = block.x() - reach; x <= block.x() + reach; x++) {
          addViewportEntry(map, maxBlocks, world.blockAt(x, block.y(), z), viewPortBlock, handMaterial);
        }
      }
    }

    int personalLimit = Math.max(1, (int) (maxBlocks * preference(p, LIMIT).multiplier()));
    while (map.size() > personalLimit) {
      map.remove(map.keySet().iterator().next());
    }
    if (map.isEmpty()) {
      clearPlayerPreview(id);
      return;
    }

    totalMap.put(id, map);
    viewportSignatures.put(id, signature);
    renderPreview(p);
  }

  private int faceReach(PlayerRef p) {
    return Math.min(MAX_FACE_REACH, Math.max(1, (int) UpgradeLoans.strength(this, p)));
  }

  private void addViewportEntry(Map<BlockRef, BlockFace> map, int maxBlocks, BlockRef target, BlockFace viewPortBlock,
                                Id handMaterial) {
    if (target == null || viewPortBlock == null || handMaterial == null) {
      return;
    }

    if (map.size() >= maxBlocks) {
      return;
    }

    if (target.type().equals(handMaterial)) {
      map.put(target, viewPortBlock);
    }
  }


  @Override
  public boolean hasTickDemand() {
    return !totalMap.isEmpty()
        || !previewDisplays.isEmpty()
        || !pendingPreviewSpawns.isEmpty()
        || !displayRemovalQueue.isEmpty();
  }

  @Override
  public void onTick() {
    drainDisplayRemovals();
    if (previewDisplays.isEmpty() && totalMap.isEmpty() && displayRemovalQueue.isEmpty()) {
      previewOwners = List.of();
      previewOwnerIndex = 0;
      setInterval(PREVIEW_CYCLE_MILLIS);
      return;
    }

    if (totalMap.isEmpty()) {
      setInterval(displayRemovalQueue.isEmpty() ? PREVIEW_CYCLE_MILLIS : MIN_INTERVAL_MILLIS);
      return;
    }

    long now = System.currentTimeMillis();
    if (previewOwnerIndex >= previewOwners.size()) {
      if (now < nextPreviewCycleAt) {
        setInterval(nextPreviewCycleAt - now);
        return;
      }
      previewOwners = new ArrayList<>(totalMap.keySet());
      previewOwnerIndex = 0;
      nextPreviewCycleAt = now + PREVIEW_CYCLE_MILLIS;
    }

    int endIndex = previewBatchEnd(previewOwnerIndex, previewOwners.size());
    while (previewOwnerIndex < endIndex) {
      UUID playerId = previewOwners.get(previewOwnerIndex++);
      PlayerRef player = AdaptCore.platform().host().player(playerId);
      if (player == null) {
        clearPlayerPreview(playerId);
        continue;
      }
      withPlayerThread(player, () -> renderPreview(player));
    }
    setInterval(previewOwnerIndex < previewOwners.size()
        ? MIN_INTERVAL_MILLIS
        : Math.max(MIN_INTERVAL_MILLIS, nextPreviewCycleAt - now));
  }

  private void renderPreview(PlayerRef p) {
    UUID id = p.uuid();
    Map<BlockRef, BlockFace> blockRender = totalMap.get(id);
    if (getActiveLevel(p, this::buildingArmed) <= 0 || blockRender == null || blockRender.isEmpty()) {
      clearPlayerPreview(id);
      return;
    }

    if (!preferenceEnabled(p, PREVIEW)) {
      clearPreviewDisplays(id);
      return;
    }

    Set<PreviewKey> activePreviews = new HashSet<>(blockRender.size());
    boolean displayPreview = getConfig().useDisplayEntities;

    for (Map.Entry<BlockRef, BlockFace> entry : blockRender.entrySet()) {
      BlockRef b = entry.getKey();
      BlockFace bf = entry.getValue();
      if (b == null || bf == null) {
        continue;
      }

      BlockRef transposedBlock = b.relative(bf);
      if (displayPreview) {
        if (!transposedBlock.type().isAir()) {
          continue;
        }

        PreviewKey key = PreviewKey.of(transposedBlock);
        activePreviews.add(key);
        if (!ensurePreviewDisplay(id, key, b.state())) {
          fx(transposedBlock.location().add(0.5, 0.5, 0.5), FxPriority.TRAIL)
              .dustRing(0.55D, 8, 0.6F);
        }
      } else {
        fx(transposedBlock.location().add(0.5, 0.5, 0.5), FxPriority.TRAIL)
            .dustRing(0.55D, 8, 0.6F);
      }
    }

    if (displayPreview) {
      clearStalePreviewDisplays(id, activePreviews);
    } else {
      clearPreviewDisplays(id);
    }
  }

  private boolean ensurePreviewDisplay(UUID playerId, PreviewKey key, BlockStateRef sourceData) {
    if (key == null || sourceData == null) {
      return false;
    }

    Map<PreviewKey, BlockDisplayRef> displays = previewDisplays.computeIfAbsent(playerId, unused -> new ConcurrentHashMap<>());
    BlockDisplayRef existing = displays.get(key);
    if (existing != null) {
      if (previewWorkBudget.tryReserve(System.currentTimeMillis())) {
        refreshPreviewDisplay(displays, key, existing, sourceData);
      }
      return true;
    }

    PreviewOwnerKey ownerKey = new PreviewOwnerKey(playerId, key);
    if (pendingPreviewSpawns.contains(ownerKey)) {
      return true;
    }
    if (!previewWorkBudget.tryReserve(System.currentTimeMillis()) || !previewCapacity.tryAcquire()) {
      return false;
    }
    if (!pendingPreviewSpawns.add(ownerKey)) {
      previewCapacity.release();
      return true;
    }

    WorldRef world = AdaptCore.platform().host().world(key.worldId());
    if (world == null) {
      pendingPreviewSpawns.remove(ownerKey);
      previewCapacity.release();
      return false;
    }

    Runnable spawnTask = () -> {
      boolean retainedCapacity = false;
      try {
        if (!pendingPreviewSpawns.contains(ownerKey) || !totalMap.containsKey(playerId)) {
          return;
        }

        BlockRef targetBlock = world.blockAt(key.x(), key.y(), key.z());
        BlockDisplayRef live = displays.get(key);
        if (live != null) {
          if (live.isValid()) {
            if (!live.block().equals(sourceData)) {
              live.setBlock(sourceData);
            }
            return;
          }
          if (displays.remove(key, live)) {
            previewCapacity.release();
          }
        }

        BlockDisplayRef spawned = world.spawnBlockDisplay(targetBlock.location(), sourceData, (BlockDisplayRef display) -> {
          display.setPersistent(false);
          display.setInvulnerable(true);
          display.setGravity(false);
          display.setSilent(true);
          display.setVisibleByDefault(false);
          display.setInterpolationDuration(2);
          display.setTeleportDuration(1);
          display.setViewRange((float) clampDisplayViewRange(getConfig().displayEntityViewRange));
          display.setShadowRadius(0f);
          display.setBrightness(new Brightness(15, 15));
        });
        if (spawned == null) {
          return;
        }
        if (!totalMap.containsKey(playerId)) {
          spawned.remove();
          return;
        }
        BlockDisplayRef raced = displays.putIfAbsent(key, spawned);
        if (raced != null) {
          spawned.remove();
          refreshPreviewDisplay(displays, key, raced, sourceData);
          return;
        }
        retainedCapacity = true;
        showPreviewToOwner(playerId, spawned);
      } finally {
        boolean heldReservation = pendingPreviewSpawns.remove(ownerKey);
        if (!retainedCapacity && heldReservation) {
          previewCapacity.release();
        }
      }
    };

    Loc location = new Loc(world, key.x() + 0.5, key.y(), key.z() + 0.5);
    if (!Tasks.runAt(location, spawnTask)) {
      pendingPreviewSpawns.remove(ownerKey);
      previewCapacity.release();
      return false;
    }
    Tasks.s(() -> {
      if (pendingPreviewSpawns.remove(ownerKey) && !displays.containsKey(key)) {
        previewCapacity.release();
      }
    }, 20);
    return true;
  }

  private void refreshPreviewDisplay(Map<PreviewKey, BlockDisplayRef> displays, PreviewKey key,
                                     BlockDisplayRef display, BlockStateRef sourceData) {
    if (!Tasks.runEntity(display, () -> {
      if (!display.isValid()) {
        if (displays.remove(key, display)) {
          previewCapacity.release();
        }
        return;
      }
      if (!display.block().equals(sourceData)) {
        display.setBlock(sourceData);
      }
    })) {
      if (displays.remove(key, display)) {
        previewCapacity.release();
      }
    }
  }

  private void clearStalePreviewDisplays(UUID playerId, Set<PreviewKey> activePreviews) {
    Map<PreviewKey, BlockDisplayRef> displays = previewDisplays.get(playerId);
    if (displays == null || displays.isEmpty()) {
      return;
    }

    for (Map.Entry<PreviewKey, BlockDisplayRef> entry : displays.entrySet()) {
      PreviewKey key = entry.getKey();
      if (key == null || activePreviews.contains(key)) {
        continue;
      }

      BlockDisplayRef removed = entry.getValue();
      if (removed != null && displays.remove(key, removed)) {
        previewCapacity.release();
        removeDisplayEntity(removed);
      }
    }

    if (displays.isEmpty()) {
      previewDisplays.remove(playerId, displays);
    }
  }

  private void clearPreviewDisplays(UUID playerId) {
    Map<PreviewKey, BlockDisplayRef> displays = previewDisplays.remove(playerId);
    clearPreviewDisplays(displays);
  }

  private void clearResidualPreview(PlayerRef p) {
    if (totalMap.isEmpty() && previewDisplays.isEmpty()) {
      return;
    }

    UUID playerId = p.uuid();
    if (totalMap.containsKey(playerId) || previewDisplays.containsKey(playerId)) {
      clearPlayerPreview(playerId);
    }
  }

  private void clearPlayerPreview(UUID playerId) {
    totalMap.remove(playerId);
    viewportSignatures.remove(playerId);
    lastViewportCheck.remove(playerId);
    clearPreviewDisplays(playerId);
  }

  private void clearPreviewDisplays(Map<PreviewKey, BlockDisplayRef> displays) {
    if (displays == null || displays.isEmpty()) {
      return;
    }

    for (Map.Entry<PreviewKey, BlockDisplayRef> entry : displays.entrySet()) {
      BlockDisplayRef display = entry.getValue();
      if (display != null && displays.remove(entry.getKey(), display)) {
        previewCapacity.release();
        removeDisplayEntity(display);
      }
    }
  }

  private void removeDisplayEntity(EntityRef entity) {
    if (entity == null) {
      return;
    }
    displayRemovalQueue.add(entity);
    setInterval(MIN_INTERVAL_MILLIS);
    if (!isBursting()) {
      retick();
    }
  }

  private void drainDisplayRemovals() {
    EntityRef entity;
    int dispatched = 0;
    while (dispatched < MAX_DISPLAY_REMOVALS_PER_TICK && (entity = displayRemovalQueue.poll()) != null) {
      removeDisplayEntityNow(entity);
      dispatched++;
    }
  }

  private void removeDisplayEntityNow(EntityRef entity) {
    Tasks.runEntity(entity, () -> {
      if (entity.isValid()) {
        entity.remove();
      }
    });
  }

  private void showPreviewToOwner(UUID playerId, EntityRef entity) {
    if (entity == null) {
      return;
    }

    PlayerRef owner = AdaptCore.platform().host().player(playerId);
    if (owner == null) {
      return;
    }

    Tasks.runEntity(owner, () -> {
      if (owner.isOnline()) {
        AdaptCore.platform().viewers().showEntity(owner, entity);
      }
    });
  }

  static int previewBatchEnd(int startIndex, int ownerCount) {
    return Math.min(Math.max(0, ownerCount), Math.max(0, startIndex) + MAX_PREVIEW_OWNERS_PER_TICK);
  }

  static int clampPreviewBlocks(int configured, int reach) {
    int side = (reach * 2) + 1;
    return Math.max(1, Math.min(side * side, configured * reach * reach));
  }

  static double clampDisplayViewRange(double configured) {
    return Math.max(0.25D, Math.min(2D, configured));
  }

  @Override
  public void unregister() {
    super.unregister();
    totalMap.clear();
    viewportSignatures.clear();
    lastViewportCheck.clear();
    pendingPreviewSpawns.clear();
    pendingSneakPreviews.clear();
    for (Map<PreviewKey, BlockDisplayRef> displays : previewDisplays.values()) {
      for (BlockDisplayRef display : displays.values()) {
        removeDisplayEntityNow(display);
      }
    }
    previewDisplays.clear();
    previewCapacity.reset();
    EntityRef queuedRemoval;
    while ((queuedRemoval = displayRemovalQueue.poll()) != null) {
      removeDisplayEntityNow(queuedRemoval);
    }
    previewOwners = List.of();
    previewOwnerIndex = 0;
  }

  private record PreviewKey(UUID worldId, int x, int y, int z) {
    private static PreviewKey of(BlockRef block) {
      return new PreviewKey(block.world().uid(), block.x(), block.y(), block.z());
    }
  }

  private record PreviewOwnerKey(UUID playerId, PreviewKey previewKey) {
  }

  private record ViewportSignature(UUID worldId, int x, int y, int z, BlockFace face, Id material) {
    private static ViewportSignature of(BlockRef block, BlockFace face, Id material) {
      return new ViewportSignature(block.world().uid(), block.x(), block.y(), block.z(), face, material);
    }
  }

  static final class PreviewWorkBudget {
    private final int limit;
    private final AtomicLong state = new AtomicLong();

    PreviewWorkBudget(int limit) {
      this.limit = Math.max(0, limit);
    }

    boolean tryReserve(long nowMillis) {
      int tick = (int) (Math.max(0L, nowMillis) / MIN_INTERVAL_MILLIS);
      while (true) {
        long current = state.get();
        int currentTick = (int) (current >>> 32);
        int used = (int) current;
        if (currentTick == tick && used >= limit) {
          return false;
        }
        int nextUsed = currentTick == tick ? used + 1 : 1;
        long next = (Integer.toUnsignedLong(tick) << 32) | Integer.toUnsignedLong(nextUsed);
        if (state.compareAndSet(current, next)) {
          return nextUsed <= limit;
        }
      }
    }
  }

  static final class PreviewCapacity {
    private final int limit;
    private final AtomicInteger active = new AtomicInteger();

    PreviewCapacity(int limit) {
      this.limit = Math.max(0, limit);
    }

    boolean tryAcquire() {
      while (true) {
        int current = active.get();
        if (current >= limit) {
          return false;
        }
        if (active.compareAndSet(current, current + 1)) {
          return true;
        }
      }
    }

    void release() {
      int current = active.get();
      while (current > 0 && !active.compareAndSet(current, current - 1)) {
        current = active.get();
      }
    }

    int active() {
      return active.get();
    }

    void reset() {
      active.set(0);
    }
  }

  @ConfigDescription("Place multiple blocks at once while sneaking with a matching block.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Blocks for the Architect Placement adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    public int maxBlocks = 20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Use owner-only block display previews instead of particles for the wand guide.", impact = "True shows ghost blocks only to the wand user; false keeps particle outlines.")
    boolean useDisplayEntities = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "View range used for wand preview display entities.", impact = "Lower values hide previews sooner; higher values keep them visible from farther away.")
    double displayEntityViewRange = 0.75;

    public Config() {
      baseCost = 5;
      costFactor = 1;
      maxLevel = 1;
      initialCost = 6;
    }
  }

  public enum Control { SNEAK, ARMED }

  public enum Materials { ALL, WOOD, STONE, GLASS }
}
