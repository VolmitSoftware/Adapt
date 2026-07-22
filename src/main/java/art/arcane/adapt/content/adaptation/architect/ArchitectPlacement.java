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
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.inventorygui.Element;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ArchitectPlacement extends SimpleAdaptation<ArchitectPlacement.Config> {
  private static final int MAX_PREVIEW_OWNERS_PER_TICK = 64;
  private static final int MAX_DISPLAY_WORK_PER_TICK = 256;
  private static final int MAX_DISPLAY_REMOVALS_PER_TICK = 256;
  private static final int MAX_ACTIVE_PREVIEW_DISPLAYS = 2048;
  private static final long PREVIEW_CYCLE_MILLIS = 500L;
  private static final long VIEWPORT_DEBOUNCE_MILLIS = 150L;
  private final Map<UUID, Map<Block, BlockFace>> totalMap = new ConcurrentHashMap<>();
  private final Map<UUID, Map<PreviewKey, BlockDisplay>> previewDisplays = new ConcurrentHashMap<>();
  private final Map<UUID, ViewportSignature> viewportSignatures = new ConcurrentHashMap<>();
  private final Map<UUID, Long> lastViewportCheck = new ConcurrentHashMap<>();
  private final Set<PreviewOwnerKey> pendingPreviewSpawns = ConcurrentHashMap.newKeySet();
  private final Queue<Entity> displayRemovalQueue = new ConcurrentLinkedQueue<>();
  private final PreviewWorkBudget previewWorkBudget = new PreviewWorkBudget(MAX_DISPLAY_WORK_PER_TICK);
  private final PreviewCapacity previewCapacity = new PreviewCapacity(MAX_ACTIVE_PREVIEW_DISPLAYS);
  private List<UUID> previewOwners = List.of();
  private int previewOwnerIndex;
  private long nextPreviewCycleAt;

  public ArchitectPlacement() {
    super("architect-placement");
    registerConfiguration(ArchitectPlacement.Config.class);
    setIcon(Material.SCAFFOLDING);
    setInterval(PREVIEW_CYCLE_MILLIS);
    nextPreviewCycleAt = System.currentTimeMillis() + PREVIEW_CYCLE_MILLIS;
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.BRICKS)
        .key("challenge_architect_placement_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.BRICKS)
            .key("challenge_architect_placement_25k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_architect_placement_1k", "architect.placement.blocks-placed", 1000, 300);
    registerMilestone("challenge_architect_placement_25k", "architect.placement.blocks-placed", 25000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + AdaptLanguage.text(ArchitectMessages.PLACEMENT_LORE3));
  }

  private BlockFace getBlockFace(Player player) {
    List<Block> lastTwoTargetBlocks = player.getLastTwoTargetBlocks(null, 5);
    if (lastTwoTargetBlocks.size() != 2 || !lastTwoTargetBlocks.get(1).getType().isOccluding())
      return null;
    Block targetBlock = lastTwoTargetBlocks.get(1);
    Block adjacentBlock = lastTwoTargetBlocks.get(0);
    return targetBlock.getFace(adjacentBlock);
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    clearPlayerPreview(e.getPlayer().getUniqueId());
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(BlockPlaceEvent e) {
    Player p = e.getPlayer();
    withPlayerThread(p, e, () -> {
      UUID id = p.getUniqueId();
      if (getActiveLevel(p, Player::isSneaking) <= 0) {
        return;
      }

      Map<Block, BlockFace> blocks = totalMap.get(id);
      if (blocks == null || blocks.isEmpty()) {
        return;
      }

      ItemStack hand = e.getItemInHand();
      Block first = null;
      for (Block candidate : blocks.keySet()) {
        first = candidate;
        break;
      }
      if (!hand.getType().isBlock() || first == null || first.getType() != hand.getType()) {
        return;
      }

      double v = getValue(e.getBlock());
      Block ignored = null;
      for (Map.Entry<Block, BlockFace> entry : blocks.entrySet()) {
        Block source = entry.getKey();
        BlockFace face = entry.getValue();
        if (source == null || face == null) {
          continue;
        }
        if (source.getRelative(face).equals(e.getBlock())) {
          ignored = source;
          break;
        }
      }

      if (hand.getAmount() < blocks.size()) {
        Adapt.messagePlayer(p, C.RED + AdaptLanguage.text(
            ArchitectMessages.PLACEMENT_REQUIREMENT,
            trusted("count", C.GREEN + String.valueOf(blocks.size()) + C.RED)
        ));
        fx(p.getLocation(), FxPriority.TRANSITION).sound(Sound.BLOCK_DISPENSER_FAIL, 0.4f, 1.0f);
        return;
      }

      if (ignored != null) {
        blocks.remove(ignored);
      }
      int placedCount = 0;
      for (Map.Entry<Block, BlockFace> entry : blocks.entrySet()) {
        Block b = entry.getKey();
        BlockFace face = entry.getValue();
        if (b == null || face == null) {
          continue;
        }

        Block relative = b.getRelative(face);
        if (!relative.getType().isAir()) {
          continue;
        }

        if (!canBlockPlace(p, relative.getLocation())) {
          continue;
        }

        relative.setBlockData(b.getBlockData());
        addStat(p, "blocks.placed", 1);
        addStat(p, "blocks.placed.value", v);
        addStat(p, "architect.placement.blocks-placed", 1);
        xp(p, 2);
        placedCount++;

        hand.setAmount(hand.getAmount() - 1);
      }

      if (ignored != null) {
        e.getBlock().setBlockData(ignored.getBlockData());
        addStat(p, "blocks.placed", 1);
        addStat(p, "blocks.placed.value", v);
        addStat(p, "architect.placement.blocks-placed", 1);
        xp(p, 2);
        placedCount++;
      } else {
        e.setCancelled(true);
      }

      if (placedCount > 0) {
        float pitch = 1.2f + Math.min(0.6f, placedCount * 0.05f);
        fx(e.getBlock().getLocation().add(0.5, 0.5, 0.5), FxPriority.GAMEPLAY)
            .dustRing(0.8D, 16, 1.0F)
            .sound(Sound.BLOCK_AMETHYST_BLOCK_PLACE, 0.5f, pitch);
      }

      clearPlayerPreview(id);
      if (hand.getAmount() > 0) {
        runPlayerViewport(getBlockFace(p), p.getTargetBlock(null, 5), p.getInventory().getItemInMainHand().getType(), p);
      }
    });
  }


  @EventHandler(ignoreCancelled = true)
  public void on(PlayerToggleSneakEvent e) {
    Player p = e.getPlayer();
    UUID id = p.getUniqueId();
    if (!e.isSneaking()) {
      clearPlayerPreview(id);
      return;
    }
    if (!p.getInventory().getItemInMainHand().getType().isBlock()) {
      return;
    }

    withPlayerThread(p, e, () -> {
      int level = getActiveLevel(p);
      if (level <= 0) {
        clearPlayerPreview(id);
        return;
      }

      if (p.getInventory().getItemInMainHand().getType().isBlock()) {
        Block block = p.getTargetBlock(null, 5); // 5 is the range of player
        if (block instanceof Container) { // return if block is a container
          return;
        }
        Material handMaterial = p.getInventory().getItemInMainHand().getType();
        if (handMaterial.isAir()) {
          return;
        }
        BlockFace viewPortBlock = getBlockFace(p);
        runPlayerViewport(viewPortBlock, block, handMaterial, p);
      }
    });
  }


  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerMoveEvent e) {
    Player p = e.getPlayer();
    UUID id = p.getUniqueId();
    Material handMaterial = p.getInventory().getItemInMainHand().getType();
    if (!p.isSneaking() || !handMaterial.isBlock()) {
      if (totalMap.containsKey(id) || previewDisplays.containsKey(id)) {
        clearPlayerPreview(id);
      }
      return;
    }

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

      if (p.isSneaking() && p.getInventory().getItemInMainHand().getType().isBlock()) {
        Block block = p.getTargetBlock(null, 5); // 5 is the range of player
        if (block instanceof Container) { // return if block is a container
          return;
        }
        Material currentHandMaterial = p.getInventory().getItemInMainHand().getType();
        if (currentHandMaterial.isAir()) {
          return;
        }
        BlockFace viewPortBlock = getBlockFace(p);
        runPlayerViewport(viewPortBlock, block, currentHandMaterial, p);
      }
    });
  }

  public void runPlayerViewport(BlockFace viewPortBlock, Block block, Material handMaterial, Player p) {
    UUID id = p.getUniqueId();
    if (viewPortBlock == null || block == null || handMaterial == null || handMaterial.isAir()) {
      clearPlayerPreview(id);
      return;
    }

    ViewportSignature signature = ViewportSignature.of(block, viewPortBlock, handMaterial);
    if (signature.equals(viewportSignatures.get(id)) && totalMap.containsKey(id)) {
      return;
    }

    Map<Block, BlockFace> map = new HashMap<>();

    if (viewPortBlock.getDirection().equals(BlockFace.NORTH.getDirection()) || viewPortBlock.getDirection().equals(BlockFace.SOUTH.getDirection())) { // North & South = X
      for (int x = block.getX() - 1; x <= block.getX() + 1; x++) { // 1 is the radius of the blocks
        for (int y = block.getY() - 1; y <= block.getY() + 1; y++) {
          addViewportEntry(map, block.getWorld().getBlockAt(x, y, block.getZ()), viewPortBlock, handMaterial);
        }
      }
    } else if (viewPortBlock.getDirection().equals(BlockFace.EAST.getDirection()) || viewPortBlock.getDirection().equals(BlockFace.WEST.getDirection())) { // East & West = Z
      for (int z = block.getZ() - 1; z <= block.getZ() + 1; z++) { // 1 is the radius of the blocks
        for (int y = block.getY() - 1; y <= block.getY() + 1; y++) {
          addViewportEntry(map, block.getWorld().getBlockAt(block.getX(), y, z), viewPortBlock, handMaterial);
        }
      }
    } else if (viewPortBlock.getDirection().equals(BlockFace.UP.getDirection()) || viewPortBlock.getDirection().equals(BlockFace.DOWN.getDirection())) { // Up & Down = Y
      for (int z = block.getZ() - 1; z <= block.getZ() + 1; z++) { // 1 is the radius of the blocks
        for (int x = block.getX() - 1; x <= block.getX() + 1; x++) {
          addViewportEntry(map, block.getWorld().getBlockAt(x, block.getY(), z), viewPortBlock, handMaterial);
        }
      }
    }

    if (map.isEmpty()) {
      clearPlayerPreview(id);
      return;
    }

    totalMap.put(id, map);
    viewportSignatures.put(id, signature);
    renderPreview(p);
  }

  private void addViewportEntry(Map<Block, BlockFace> map, Block target, BlockFace viewPortBlock, Material handMaterial) {
    if (target == null || viewPortBlock == null || handMaterial == null) {
      return;
    }

    int maxBlocks = clampPreviewBlocks(getConfig().maxBlocks);
    if (map.size() >= maxBlocks) {
      return;
    }

    if (target.getType() == handMaterial) {
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
      Player player = Bukkit.getPlayer(playerId);
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

  private void renderPreview(Player p) {
    UUID id = p.getUniqueId();
    Map<Block, BlockFace> blockRender = totalMap.get(id);
    if (getActiveLevel(p, Player::isSneaking) <= 0 || blockRender == null || blockRender.isEmpty()) {
      clearPlayerPreview(id);
      return;
    }

    Set<PreviewKey> activePreviews = new HashSet<>(blockRender.size());
    boolean displayPreview = getConfig().useDisplayEntities;

    for (Map.Entry<Block, BlockFace> entry : blockRender.entrySet()) {
      Block b = entry.getKey();
      BlockFace bf = entry.getValue();
      if (b == null || bf == null || b instanceof Container) {
        continue;
      }

      Block transposedBlock = b.getRelative(bf);
      if (displayPreview) {
        if (!transposedBlock.getType().isAir()) {
          continue;
        }

        PreviewKey key = PreviewKey.of(transposedBlock);
        activePreviews.add(key);
        if (!ensurePreviewDisplay(id, key, b.getBlockData())) {
          fx(transposedBlock.getLocation().add(0.5, 0.5, 0.5), FxPriority.TRAIL)
              .dustRing(0.55D, 8, 0.6F);
        }
      } else {
        fx(transposedBlock.getLocation().add(0.5, 0.5, 0.5), FxPriority.TRAIL)
            .dustRing(0.55D, 8, 0.6F);
      }
    }

    if (displayPreview) {
      clearStalePreviewDisplays(id, activePreviews);
    } else {
      clearPreviewDisplays(id);
    }
  }

  private boolean ensurePreviewDisplay(UUID playerId, PreviewKey key, BlockData sourceData) {
    if (key == null || sourceData == null) {
      return false;
    }

    Map<PreviewKey, BlockDisplay> displays = previewDisplays.computeIfAbsent(playerId, unused -> new ConcurrentHashMap<>());
    BlockDisplay existing = displays.get(key);
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

    World world = Bukkit.getWorld(key.worldId());
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

        Block targetBlock = world.getBlockAt(key.x(), key.y(), key.z());
        BlockDisplay live = displays.get(key);
        if (live != null) {
          if (live.isValid()) {
            if (!live.getBlock().equals(sourceData)) {
              live.setBlock(sourceData);
            }
            return;
          }
          if (displays.remove(key, live)) {
            previewCapacity.release();
          }
        }

        BlockDisplay spawned = world.spawn(targetBlock.getLocation(), BlockDisplay.class, display -> {
          display.setPersistent(false);
          display.setInvulnerable(true);
          display.setGravity(false);
          display.setSilent(true);
          display.setVisibleByDefault(false);
          display.setInterpolationDuration(2);
          display.setTeleportDuration(1);
          display.setViewRange((float) clampDisplayViewRange(getConfig().displayEntityViewRange));
          display.setShadowRadius(0f);
          display.setShadowStrength(0f);
          display.setBrightness(new Display.Brightness(15, 15));
          display.setBlock(sourceData);
        });
        if (!totalMap.containsKey(playerId)) {
          spawned.remove();
          return;
        }
        BlockDisplay raced = displays.putIfAbsent(key, spawned);
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

    Location location = new Location(world, key.x() + 0.5, key.y(), key.z() + 0.5);
    if (!J.runAt(location, spawnTask)) {
      pendingPreviewSpawns.remove(ownerKey);
      previewCapacity.release();
      return false;
    }
    J.s(() -> {
      if (pendingPreviewSpawns.remove(ownerKey) && !displays.containsKey(key)) {
        previewCapacity.release();
      }
    }, 20);
    return true;
  }

  private void refreshPreviewDisplay(Map<PreviewKey, BlockDisplay> displays, PreviewKey key,
                                     BlockDisplay display, BlockData sourceData) {
    if (!J.runEntity(display, () -> {
      if (!display.isValid()) {
        if (displays.remove(key, display)) {
          previewCapacity.release();
        }
        return;
      }
      if (!display.getBlock().equals(sourceData)) {
        display.setBlock(sourceData);
      }
    })) {
      if (displays.remove(key, display)) {
        previewCapacity.release();
      }
    }
  }

  private void clearStalePreviewDisplays(UUID playerId, Set<PreviewKey> activePreviews) {
    Map<PreviewKey, BlockDisplay> displays = previewDisplays.get(playerId);
    if (displays == null || displays.isEmpty()) {
      return;
    }

    for (Map.Entry<PreviewKey, BlockDisplay> entry : displays.entrySet()) {
      PreviewKey key = entry.getKey();
      if (key == null || activePreviews.contains(key)) {
        continue;
      }

      BlockDisplay removed = entry.getValue();
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
    Map<PreviewKey, BlockDisplay> displays = previewDisplays.remove(playerId);
    clearPreviewDisplays(displays);
  }

  private void clearPlayerPreview(UUID playerId) {
    totalMap.remove(playerId);
    viewportSignatures.remove(playerId);
    lastViewportCheck.remove(playerId);
    clearPreviewDisplays(playerId);
  }

  private void clearPreviewDisplays(Map<PreviewKey, BlockDisplay> displays) {
    if (displays == null || displays.isEmpty()) {
      return;
    }

    for (Map.Entry<PreviewKey, BlockDisplay> entry : displays.entrySet()) {
      BlockDisplay display = entry.getValue();
      if (display != null && displays.remove(entry.getKey(), display)) {
        previewCapacity.release();
        removeDisplayEntity(display);
      }
    }
  }

  private void removeDisplayEntity(Entity entity) {
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
    Entity entity;
    int dispatched = 0;
    while (dispatched < MAX_DISPLAY_REMOVALS_PER_TICK && (entity = displayRemovalQueue.poll()) != null) {
      removeDisplayEntityNow(entity);
      dispatched++;
    }
  }

  private void removeDisplayEntityNow(Entity entity) {
    J.runEntity(entity, () -> {
      if (entity.isValid()) {
        entity.remove();
      }
    });
  }

  private void showPreviewToOwner(UUID playerId, Entity entity) {
    if (entity == null) {
      return;
    }

    Player owner = Bukkit.getPlayer(playerId);
    if (owner == null) {
      return;
    }

    J.runEntity(owner, () -> {
      if (owner.isOnline()) {
        owner.showEntity(Adapt.instance, entity);
      }
    });
  }

  static int previewBatchEnd(int startIndex, int ownerCount) {
    return Math.min(Math.max(0, ownerCount), Math.max(0, startIndex) + MAX_PREVIEW_OWNERS_PER_TICK);
  }

  static int clampPreviewBlocks(int configured) {
    return Math.max(1, Math.min(9, configured));
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
    for (Map<PreviewKey, BlockDisplay> displays : previewDisplays.values()) {
      for (BlockDisplay display : displays.values()) {
        removeDisplayEntityNow(display);
      }
    }
    previewDisplays.clear();
    previewCapacity.reset();
    Entity queuedRemoval;
    while ((queuedRemoval = displayRemovalQueue.poll()) != null) {
      removeDisplayEntityNow(queuedRemoval);
    }
    previewOwners = List.of();
    previewOwnerIndex = 0;
  }

  private record PreviewKey(UUID worldId, int x, int y, int z) {
    private static PreviewKey of(Block block) {
      return new PreviewKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }
  }

  private record PreviewOwnerKey(UUID playerId, PreviewKey previewKey) {
  }

  private record ViewportSignature(UUID worldId, int x, int y, int z, BlockFace face, Material material) {
    private static ViewportSignature of(Block block, BlockFace face, Material material) {
      return new ViewportSignature(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ(), face, material);
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
      baseCost = 6;
      costFactor = 2;
      maxLevel = 1;
      initialCost = 4;
    }
  }
}
