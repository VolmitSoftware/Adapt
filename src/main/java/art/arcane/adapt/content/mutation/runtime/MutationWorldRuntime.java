package art.arcane.adapt.content.mutation.runtime;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.mutation.MutationClaim;
import art.arcane.adapt.api.mutation.MutationConfig;
import art.arcane.adapt.api.mutation.MutationEventClaims;
import art.arcane.adapt.api.mutation.MutationLimits;
import art.arcane.adapt.api.mutation.MutationType;
import art.arcane.adapt.api.mutation.PlayerMutationData;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import com.jeff_media.customblockdata.CustomBlockData;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

final class MutationWorldRuntime {
  private static final long HARVEST_REPLANT_WINDOW_MILLIS = 30_000L;
  private static final int CHUNK_RESTORE_BATCH = 16;
  private static final int RECOVERY_CHUNK_BATCH = 8;
  private static final int NEARBY_ENTITY_CAP = 16;
  private static final int RESOURCE_SAVE_DELAY_TICKS = 20;
  private static final long REGION_CAPABILITY_MILLIS = 1_000L;
  private static final String MARKER_TAG = "adapt-mutation-marker";
  private static final int[][] CROP_OFFSETS = {
      {0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {2, 0}, {-2, 0}, {0, 2},
      {0, -2}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}, {3, 0}, {0, 3}, {-3, 0}
  };

  private final MutationRuntimeAccess access;
  private final MutationRuntimeStore store;
  private final MutationBlockProvenance provenance;
  private final MutationEquipmentRuntime equipment;
  private final Set<MutationRuntimeStore.BlockPosition> restoreWarnings = ConcurrentHashMap.newKeySet();

  MutationWorldRuntime(
      MutationRuntimeAccess access,
      MutationRuntimeStore store,
      MutationBlockProvenance provenance,
      MutationEquipmentRuntime equipment
  ) {
    this.access = access;
    this.store = store;
    this.provenance = provenance;
    this.equipment = equipment;
  }

  void onBlockBreak(BlockBreakEvent event) {
    Block block = event.getBlock();
    Player player = event.getPlayer();
    if (provenance.isTemporary(block)) {
      event.setCancelled(true);
      event.setDropItems(false);
      forceCollapse(block);
      return;
    }
    if (equipment.blocksHeldItem(player)) {
      event.setCancelled(true);
    }
  }

  void onSuccessfulBlockBreak(BlockBreakEvent event) {
    Block block = event.getBlock();
    Player player = event.getPlayer();
    if (!eligible(player)) {
      return;
    }
    boolean playerModified = provenance.isPlayerPlaced(block);
    if (access.expressed(player, MutationType.LIVING_LATTICE) && !playerModified) {
      recordHarvest(player, block);
    }
    if (access.expressed(player, MutationType.DEEPBLOOD) && !playerModified && isDeepResource(block)) {
      equipment.addIchor(player, access.config().getDeepblood().getIchorPerBlock());
      scheduleResourceSave(player);
    }
  }

  void onBlockPlace(BlockPlaceEvent event) {
    Player player = event.getPlayer();
    if (!eligible(player) || !access.expressed(player, MutationType.LIVING_LATTICE)) {
      return;
    }
    MutationRuntimeStore.PlayerRuntimeState runtime = store.player(player.getUniqueId());
    MutationRuntimeStore.BlockPosition position = MutationRuntimeStore.BlockPosition.of(event.getBlockPlaced().getLocation());
    synchronized (runtime) {
      MutationRuntimeStore.HarvestRecord record = runtime.lattice.harvested.get(position);
      if (record == null || record.expiresAt() < System.currentTimeMillis()
          || !matchesReplant(record.material(), event.getBlockPlaced().getType())) {
        return;
      }
      runtime.lattice.harvested.remove(position);
    }
    PlayerMutationData durable = access.durable(player);
    if (durable == null) {
      return;
    }
    durable.setLivingLatticeRootCharge(Math.min(
        access.config().getLivingLattice().getMaximumRootCharge(),
        durable.getLivingLatticeRootCharge() + 1D
    ));
    scheduleResourceSave(player);
    access.tell(player, MutationType.LIVING_LATTICE, Particle.COMPOSTER, 5);
  }

  void onInteract(PlayerInteractEvent event, MutationEventClaims claims) {
    if (event.getHand() != EquipmentSlot.HAND || !event.getPlayer().isSneaking()) {
      return;
    }
    ItemStack item = event.getItem();
    if (!isSapling(item) || !access.expressed(event.getPlayer(), MutationType.LIVING_LATTICE)) {
      return;
    }
    growPath(event.getPlayer(), claims);
  }

  void onBurn(BlockBurnEvent event) {
    if (provenance.isTemporary(event.getBlock())) {
      event.setCancelled(true);
      collapseForEnvironment(event.getBlock());
    }
  }

  void onIgnite(BlockIgniteEvent event) {
    if (provenance.isTemporary(event.getBlock())) {
      event.setCancelled(true);
      collapseForEnvironment(event.getBlock());
    }
  }

  void onBlockExplosion(BlockExplodeEvent event) {
    containExplosion(event.blockList());
  }

  void onEntityExplosion(EntityExplodeEvent event) {
    containExplosion(event.blockList());
  }

  void onPistonExtend(BlockPistonExtendEvent event) {
    containPiston(event.getBlocks(), event.getDirection(), event::setCancelled);
  }

  void onPistonRetract(BlockPistonRetractEvent event) {
    containPiston(event.getBlocks(), event.getDirection().getOppositeFace(), event::setCancelled);
  }

  void onFluid(BlockFromToEvent event) {
    Block destination = event.getToBlock();
    if (!provenance.isTemporary(destination)) {
      return;
    }
    event.setCancelled(true);
    collapseForEnvironment(destination);
  }

  void onFade(BlockFadeEvent event) {
    if (!provenance.isTemporary(event.getBlock())) {
      return;
    }
    event.setCancelled(true);
    forceCollapse(event.getBlock());
  }

  void onEntityChangeBlock(EntityChangeBlockEvent event) {
    if (!provenance.isTemporary(event.getBlock())) {
      return;
    }
    event.setCancelled(true);
    forceCollapse(event.getBlock());
  }

  void onDrop(BlockDropItemEvent event) {
    if (provenance.isTemporary(event.getBlock())) {
      event.setCancelled(true);
    }
  }

  void onChunkLoad(ChunkLoadEvent event) {
    recoverChunk(event.getChunk());
  }

  void recoverLoadedState() {
    ArrayList<Chunk> loadedChunks = new ArrayList<>();
    for (World world : Bukkit.getWorlds()) {
      for (Chunk chunk : world.getLoadedChunks()) {
        loadedChunks.add(chunk);
      }
    }
    recoverLoadedChunkBatch(loadedChunks, 0);
  }

  void onRegainHealth(EntityRegainHealthEvent event) {
    if (event.isCancelled() || event.getRegainReason() != EntityRegainHealthEvent.RegainReason.SATIATED
        || !(event.getEntity() instanceof Player player)) {
      return;
    }
    for (MutationType type : access.ordered(player)) {
      if (event.isCancelled()) {
        return;
      }
      if (type == MutationType.DEEPBLOOD) {
        applyDeepbloodRegeneration(player, event);
      } else if (type == MutationType.GRAVEBLOOM) {
        applyGravebloomRegeneration(player, event);
      }
    }
  }

  void createGravebloom(UUID ownerId, long loadoutGeneration, Location location) {
    if (ownerId == null || location == null || location.getWorld() == null) {
      return;
    }
    Location requestedLocation = location.clone();
    Player owner = access.onlinePlayer(ownerId);
    if (owner == null || !J.runEntity(owner, () -> authorizeGravebloom(
        owner,
        ownerId,
        loadoutGeneration,
        requestedLocation
    ))) {
      return;
    }
  }

  private void authorizeGravebloom(
      Player owner,
      UUID ownerId,
      long loadoutGeneration,
      Location location
  ) {
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(ownerId);
    if (!eligible(owner)
        || !access.expressed(owner, MutationType.GRAVEBLOOM)
        || !activeLoadout(runtime, loadoutGeneration)) {
      return;
    }
    GravebloomCapability capability = new GravebloomCapability(
        ownerId,
        loadoutGeneration,
        MutationRuntimeStore.BlockPosition.of(location),
        access.perfect(owner),
        System.currentTimeMillis() + REGION_CAPABILITY_MILLIS
    );
    J.runAt(location, () -> createGravebloomRegion(capability));
  }

  private void createGravebloomRegion(GravebloomCapability capability) {
    long now = System.currentTimeMillis();
    Location location = location(capability.position());
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(capability.ownerId());
    Player regionOwnedOwner = regionOwnedPlayer(capability.ownerId());
    if (location == null
        || capability.expiresAt() < now
        || !activeLoadout(runtime, capability.loadoutGeneration())
        || !isLoaded(location)
        || regionOwnedOwner == null
        || !access.protection().canInteract(regionOwnedOwner, location)
        || !isNaturalTerrain(location.getBlock())) {
      return;
    }
    MutationConfig.Gravebloom config = access.config().getGravebloom();
    MutationRuntimeStore.Bloom bloom;
    synchronized (runtime) {
      runtime.grave.blooms.removeIf(existing -> existing.expiresAt() <= now);
      while (runtime.grave.blooms.size() >= config.getMaximumBlooms()) {
        runtime.grave.blooms.remove(0);
      }
      bloom = new MutationRuntimeStore.Bloom(
          capability.position(),
          now + config.getLifetimeMillis(),
          runtime.grave.generation,
          capability.loadoutGeneration(),
          capability.perfect()
      );
      runtime.grave.blooms.add(bloom);
    }
    showGravebloom(bloom, false);
    scheduleOwnerTell(capability.ownerId(), capability.loadoutGeneration(), MutationType.GRAVEBLOOM,
        Particle.SOUL_FIRE_FLAME, 12);
    scheduleGravebloomPulse(capability.ownerId(), bloom, config.getPulseTicks());
  }

  boolean hasActiveGravebloom(Player player) {
    MutationRuntimeStore.PlayerRuntimeState runtime = store.player(player.getUniqueId());
    synchronized (runtime) {
      long now = System.currentTimeMillis();
      runtime.grave.blooms.removeIf(bloom -> bloom.expiresAt() <= now);
      return !runtime.grave.blooms.isEmpty();
    }
  }

  void clearEnvironmentalCharge(Player player) {
    if (player == null || !access.expressed(player, MutationType.LIVING_LATTICE) || access.perfect(player)) {
      return;
    }
    PlayerMutationData durable = access.durable(player);
    if (durable != null && durable.getLivingLatticeRootCharge() > 0D) {
      durable.setLivingLatticeRootCharge(0D);
      scheduleResourceSave(player);
    }
  }

  void cleanup(Player player) {
    if (player == null) {
      return;
    }
    UUID ownerId = player.getUniqueId();
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(ownerId);
    if (runtime == null) {
      return;
    }
    ArrayList<MutationRuntimeStore.BlockPosition> activeBlocks;
    int rootRefunds;
    synchronized (runtime) {
      activeBlocks = new ArrayList<>(runtime.lattice.activeBlocks);
      rootRefunds = runtime.lattice.pendingRootRefunds;
      runtime.lattice.pendingRootRefunds = 0;
      for (MutationRuntimeStore.LatticeStructure structure : runtime.lattice.structures.values()) {
        if (structure.placedBlocks == 0) {
          rootRefunds++;
        }
      }
      runtime.grave.clear();
    }
    applyRootRefunds(player, rootRefunds);
    for (MutationRuntimeStore.BlockPosition key : activeBlocks) {
      MutationRuntimeStore.TemporaryBlock temporary = store.temporaryBlocks.get(key);
      if (temporary != null) {
        restoreKnownTemporary(key, temporary);
      }
    }
    synchronized (runtime) {
      runtime.lattice.clearTransient();
    }
    flushResourceSave(player);
  }

  void shutdown() {
    ArrayList<Map.Entry<MutationRuntimeStore.BlockPosition, MutationRuntimeStore.TemporaryBlock>> blocks =
        new ArrayList<>(store.temporaryBlocks.entrySet());
    for (Map.Entry<MutationRuntimeStore.BlockPosition, MutationRuntimeStore.TemporaryBlock> entry : blocks) {
      if (J.isFoliaThreading()) {
        restoreKnownTemporary(entry.getKey(), entry.getValue());
      } else {
        restoreKnownTemporaryDirect(entry.getKey(), entry.getValue());
      }
    }
  }

  private void recordHarvest(Player player, Block block) {
    Material expected = expectedReplant(block);
    if (expected == null) {
      return;
    }
    MutationRuntimeStore.PlayerRuntimeState runtime = store.player(player.getUniqueId());
    settlePendingRootRefunds(player, runtime);
    MutationRuntimeStore.BlockPosition position = MutationRuntimeStore.BlockPosition.of(block.getLocation());
    synchronized (runtime) {
      long now = System.currentTimeMillis();
      runtime.lattice.harvested.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
      if (runtime.lattice.harvested.size() >= 16) {
        Iterator<MutationRuntimeStore.BlockPosition> iterator = runtime.lattice.harvested.keySet().iterator();
        if (iterator.hasNext()) {
          runtime.lattice.harvested.remove(iterator.next());
        }
      }
      runtime.lattice.harvested.put(position, new MutationRuntimeStore.HarvestRecord(
          expected,
          now + HARVEST_REPLANT_WINDOW_MILLIS
      ));
    }
  }

  private void growPath(Player player, MutationEventClaims claims) {
    if (!eligible(player)) {
      return;
    }
    MutationConfig.LivingLattice config = access.config().getLivingLattice();
    PlayerMutationData durable = access.durable(player);
    Location origin = player.getLocation();
    Vector direction = origin.getDirection().setY(0D);
    if (direction.lengthSquared() <= 0.0001D || durable == null) {
      return;
    }
    direction.normalize();
    ArrayList<Location> candidates = new ArrayList<>(config.getPathLength());
    HashSet<MutationRuntimeStore.BlockPosition> candidatePositions = new HashSet<>(config.getPathLength());
    for (int step = 1; step <= config.getPathLength(); step++) {
      Location location = origin.clone().add(direction.clone().multiply(step));
      location.setY(origin.getBlockY() - 1D);
      MutationRuntimeStore.BlockPosition position = MutationRuntimeStore.BlockPosition.of(location);
      if (sameRegionOwnership(player, location)
          && isLoaded(location)
          && candidatePositions.add(position)
          && access.protection().canPlace(player, location)) {
        candidates.add(location);
      }
    }
    if (candidates.isEmpty()) {
      return;
    }
    MutationRuntimeStore.PlayerRuntimeState runtime = store.player(player.getUniqueId());
    long now = System.currentTimeMillis();
    long structureId;
    long loadoutGeneration;
    boolean perfect = access.perfect(player);
    int reserved;
    synchronized (runtime) {
      pruneStructures(runtime.lattice, now);
    }
    settlePendingRootRefunds(player, runtime);
    synchronized (runtime) {
      int availableBlocks = config.getMaximumBlocks()
          - runtime.lattice.activeBlocks.size()
          - runtime.lattice.reservedBlocks;
      reserved = Math.min(candidates.size(), Math.max(0, availableBlocks));
      if (durable.getLivingLatticeRootCharge() < 1D
          || runtime.lattice.collapseLockUntil > now
          || runtime.lattice.structures.size() >= config.getMaximumStructures()
          || reserved <= 0
          || !claims.tryClaim(MutationClaim.WORLD_STATE)) {
        return;
      }
      structureId = runtime.lattice.nextStructureId++;
      loadoutGeneration = runtime.loadoutGeneration;
      long lifetime = latticeLifetime(config, candidates.getFirst(), perfect);
      runtime.lattice.structures.put(structureId, new MutationRuntimeStore.LatticeStructure(
          loadoutGeneration,
          now + lifetime,
          reserved
      ));
      runtime.lattice.reservedBlocks += reserved;
      durable.setLivingLatticeRootCharge(durable.getLivingLatticeRootCharge() - 1D);
    }
    scheduleResourceSave(player);
    for (int index = 0; index < reserved; index++) {
      Location location = candidates.get(index);
      LatticePlacementCapability capability = new LatticePlacementCapability(
          player.getUniqueId(),
          MutationRuntimeStore.BlockPosition.of(location),
          structureId,
          loadoutGeneration,
          index,
          perfect,
          now + REGION_CAPABILITY_MILLIS
      );
      if (!J.runAt(location, () -> placeTemporary(capability))) {
        completePlacement(
            capability.ownerId(),
            capability.structureId(),
            capability.reservationId(),
            capability.loadoutGeneration(),
            null,
            false
        );
      }
    }
  }

  private void placeTemporary(LatticePlacementCapability capability) {
    Location location = location(capability.position());
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(capability.ownerId());
    if (location == null
        || capability.expiresAt() < System.currentTimeMillis()
        || !placementValid(runtime, capability)
        || !isLoaded(location)) {
      completePlacement(
          capability.ownerId(),
          capability.structureId(),
          capability.reservationId(),
          capability.loadoutGeneration(),
          null,
          false
      );
      return;
    }
    Block block = location.getBlock();
    if (!isReplaceable(block) || provenance.isTemporary(block)) {
      completePlacement(
          capability.ownerId(),
          capability.structureId(),
          capability.reservationId(),
          capability.loadoutGeneration(),
          null,
          false
      );
      return;
    }
    MutationConfig.LivingLattice config = access.config().getLivingLattice();
    MutationRuntimeStore.BlockPosition key = capability.position();
    BlockData original = block.getBlockData().clone();
    long lifetime = latticeLifetime(config, location, capability.perfect());
    long expiresAt = System.currentTimeMillis() + lifetime;
    MutationRuntimeStore.TemporaryBlock temporary = new MutationRuntimeStore.TemporaryBlock(
        capability.ownerId(),
        capability.structureId(),
        original,
        expiresAt
    );
    if (store.temporaryBlocks.putIfAbsent(key, temporary) != null) {
      completePlacement(
          capability.ownerId(),
          capability.structureId(),
          capability.reservationId(),
          capability.loadoutGeneration(),
          null,
          false
      );
      return;
    }
    try {
      provenance.markTemporary(block, capability.ownerId().toString(), original.getAsString(), expiresAt);
      block.setType(Material.MANGROVE_ROOTS, false);
    } catch (RuntimeException error) {
      store.temporaryBlocks.remove(key, temporary);
      provenance.clearTemporary(block);
      reportRestorationError("Failed to place Living Lattice block at " + format(key), error);
      completePlacement(
          capability.ownerId(),
          capability.structureId(),
          capability.reservationId(),
          capability.loadoutGeneration(),
          null,
          false
      );
      return;
    }
    if (!completePlacement(
        capability.ownerId(),
        capability.structureId(),
        capability.reservationId(),
        capability.loadoutGeneration(),
        key,
        true
    )) {
      restoreTemporaryBlock(block);
      return;
    }
    scheduleTemporaryExpiry(key, temporary);
  }

  private void forceCollapse(Block block) {
    MutationBlockProvenance.TemporaryMarker marker = provenance.temporary(block);
    restoreTemporaryBlock(block);
    if (marker == null) {
      return;
    }
    UUID ownerId = parseUuid(marker.ownerId());
    Player owner = access.onlinePlayer(ownerId);
    if (owner == null) {
      return;
    }
    J.runEntity(owner, () -> applyForcedCollapseBurden(owner));
  }

  private void collapseForEnvironment(Block block) {
    MutationBlockProvenance.TemporaryMarker marker = provenance.temporary(block);
    restoreTemporaryBlock(block);
    if (marker == null) {
      return;
    }
    Player owner = access.onlinePlayer(parseUuid(marker.ownerId()));
    if (owner != null) {
      J.runEntity(owner, () -> clearEnvironmentalCharge(owner));
    }
  }

  private void restoreKnownTemporary(
      MutationRuntimeStore.BlockPosition key,
      MutationRuntimeStore.TemporaryBlock temporary
  ) {
    World world = Bukkit.getWorld(key.worldId());
    if (world == null || !world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) {
      store.temporaryBlocks.remove(key, temporary);
      removeActiveTemporary(temporary.ownerId(), key, temporary.generation());
      return;
    }
    Location location = new Location(world, key.x(), key.y(), key.z());
    if (!J.runAt(location, () -> restoreKnownTemporaryDirect(key, temporary))) {
      reportRestoreScheduleFailure(key);
    }
  }

  private void restoreKnownTemporaryDirect(
      MutationRuntimeStore.BlockPosition key,
      MutationRuntimeStore.TemporaryBlock temporary
  ) {
    World world = Bukkit.getWorld(key.worldId());
    if (world == null || !world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) {
      store.temporaryBlocks.remove(key, temporary);
      removeActiveTemporary(temporary.ownerId(), key, temporary.generation());
      return;
    }
    Block block = world.getBlockAt(key.x(), key.y(), key.z());
    MutationRuntimeStore.TemporaryBlock current = store.temporaryBlocks.get(key);
    if (current != null && current.generation() == temporary.generation() && provenance.isTemporary(block)) {
      restoreBlockData(block, current.originalData(), key);
      provenance.clearTemporary(block);
    }
    store.temporaryBlocks.remove(key, temporary);
    restoreWarnings.remove(key);
    removeActiveTemporary(temporary.ownerId(), key, temporary.generation());
  }

  private void scheduleTemporaryExpiry(
      MutationRuntimeStore.BlockPosition key,
      MutationRuntimeStore.TemporaryBlock temporary
  ) {
    World world = Bukkit.getWorld(key.worldId());
    if (world == null || !world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) {
      store.temporaryBlocks.remove(key, temporary);
      removeActiveTemporary(temporary.ownerId(), key, temporary.generation());
      return;
    }
    Location location = new Location(world, key.x(), key.y(), key.z());
    int delayTicks = boundedDelayTicks(temporary.expiresAt(), System.currentTimeMillis());
    if (!J.runAt(location, () -> continueTemporaryExpiry(key, temporary), delayTicks)) {
      reportRestoreScheduleFailure(key);
    }
  }

  private void continueTemporaryExpiry(
      MutationRuntimeStore.BlockPosition key,
      MutationRuntimeStore.TemporaryBlock temporary
  ) {
    if (store.temporaryBlocks.get(key) != temporary) {
      return;
    }
    if (temporary.expiresAt() > System.currentTimeMillis()) {
      scheduleTemporaryExpiry(key, temporary);
      return;
    }
    restoreKnownTemporaryDirect(key, temporary);
  }

  static int boundedDelayTicks(long deadline, long now) {
    long remaining = Math.max(1L, deadline - now);
    long ticks = Math.max(1L, (remaining + 49L) / 50L);
    return (int) Math.min(MutationLimits.MAX_DELAY_TICKS, ticks);
  }

  private void restoreTemporaryBlock(Block block) {
    MutationBlockProvenance.TemporaryMarker marker = provenance.temporary(block);
    MutationRuntimeStore.BlockPosition key = MutationRuntimeStore.BlockPosition.of(block.getLocation());
    MutationRuntimeStore.TemporaryBlock known = store.temporaryBlocks.remove(key);
    BlockData original = null;
    if (known != null) {
      original = known.originalData();
    } else if (marker != null) {
      try {
        original = Bukkit.createBlockData(marker.originalData());
      } catch (RuntimeException error) {
        reportRestorationError("Invalid Living Lattice restoration data at " + format(key), error);
      }
    } else if (provenance.isTemporary(block)) {
      reportRestorationError(
          "Incomplete Living Lattice restoration marker at " + format(key),
          new IllegalStateException("Temporary block marker is missing owner, original data, or expiry")
      );
    }
    restoreBlockData(block, original == null ? Bukkit.createBlockData(Material.AIR) : original, key);
    provenance.clearTemporary(block);
    UUID ownerId = known == null ? parseUuid(marker == null ? null : marker.ownerId()) : known.ownerId();
    long structureId = known == null ? -1L : known.generation();
    removeActiveTemporary(ownerId, key, structureId);
    restoreWarnings.remove(key);
  }

  private void restoreChunkBatch(List<Block> blocks, int start) {
    if (start >= blocks.size()) {
      return;
    }
    int end = Math.min(blocks.size(), start + CHUNK_RESTORE_BATCH);
    long now = System.currentTimeMillis();
    for (int index = start; index < end; index++) {
      Block block = blocks.get(index);
      MutationRuntimeStore.BlockPosition key = MutationRuntimeStore.BlockPosition.of(block.getLocation());
      MutationRuntimeStore.TemporaryBlock known = store.temporaryBlocks.get(key);
      MutationBlockProvenance.TemporaryMarker marker = provenance.temporary(block);
      if (known == null || marker == null || marker.expiresAt() <= now) {
        restoreTemporaryBlock(block);
      } else {
        scheduleTemporaryExpiry(key, known);
      }
    }
    if (end < blocks.size()) {
      Location continuation = blocks.get(end).getLocation();
      if (!J.runAt(continuation, () -> restoreChunkBatch(blocks, end), 1)) {
        Adapt.warn("Living Lattice recovery could not schedule the remaining " + (blocks.size() - end)
            + " block(s) in chunk " + (continuation.getBlockX() >> 4) + "," + (continuation.getBlockZ() >> 4)
            + "; their persistent markers will be retried when the chunk loads again.");
      }
    }
  }

  private void applyDeepbloodRegeneration(Player player, EntityRegainHealthEvent event) {
    MutationConfig.Deepblood config = access.config().getDeepblood();
    if (player.getLocation().getBlockY() > config.getMaximumDepthY()) {
      equipment.currentIchor(player);
      scheduleResourceSave(player);
      return;
    }
    boolean consumed = equipment.consumeIchor(player, config.getRegenerationCost());
    if (consumed) {
      scheduleResourceSave(player);
    } else if (!access.perfect(player)) {
      event.setCancelled(true);
    }
  }

  private void applyGravebloomRegeneration(Player player, EntityRegainHealthEvent event) {
    if (!access.perfect(player) && hasActiveGravebloom(player)) {
      event.setAmount(event.getAmount() * access.config().getGravebloom().getRegenerationFactor());
    }
  }

  private void scheduleGravebloomPulse(UUID ownerId, MutationRuntimeStore.Bloom bloom, int delayTicks) {
    Player owner = access.onlinePlayer(ownerId);
    if (owner == null
        || !J.runEntity(owner, () -> pulseGravebloomOwner(owner, bloom), Math.max(1, delayTicks))) {
      removeBloom(ownerId, bloom);
    }
  }

  private void pulseGravebloomOwner(Player owner, MutationRuntimeStore.Bloom bloom) {
    UUID ownerId = owner.getUniqueId();
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(ownerId);
    long now = System.currentTimeMillis();
    if (!eligible(owner)
        || !access.expressed(owner, MutationType.GRAVEBLOOM)
        || !activeBloom(runtime, bloom, now)) {
      removeBloom(ownerId, bloom);
      return;
    }
    Location center = location(bloom.position());
    if (center == null) {
      removeBloom(ownerId, bloom);
      return;
    }
    GravebloomPulseCapability capability = new GravebloomPulseCapability(
        ownerId,
        bloom.loadoutGeneration(),
        bloom.graveGeneration(),
        bloom.position(),
        access.perfect(owner),
        authorizeCropSupport(owner, center),
        now + REGION_CAPABILITY_MILLIS
    );
    if (!J.runAt(center, () -> pulseGravebloomRegion(bloom, capability))) {
      removeBloom(ownerId, bloom);
    }
  }

  private void pulseGravebloomRegion(
      MutationRuntimeStore.Bloom bloom,
      GravebloomPulseCapability capability
  ) {
    long now = System.currentTimeMillis();
    Location center = location(capability.position());
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(capability.ownerId());
    if (center == null
        || capability.expiresAt() < now
        || !isLoaded(center)
        || !activeBloom(runtime, bloom, now)
        || bloom.loadoutGeneration() != capability.loadoutGeneration()
        || bloom.graveGeneration() != capability.graveGeneration()) {
      removeBloom(capability.ownerId(), bloom);
      return;
    }
    supportCrops(bloom, capability);
    supportNearby(bloom, capability, now);
    boolean mature = bloom.expiresAt() - now <= access.config().getGravebloom().getLifetimeMillis() / 2L;
    showGravebloom(bloom, mature);
    scheduleGravebloomPulse(
        capability.ownerId(),
        bloom,
        access.config().getGravebloom().getPulseTicks()
    );
  }

  private List<MutationRuntimeStore.BlockPosition> authorizeCropSupport(Player owner, Location center) {
    ArrayList<MutationRuntimeStore.BlockPosition> authorized = new ArrayList<>();
    int cap = Math.min(access.config().getGravebloom().getMaximumCrops(), CROP_OFFSETS.length);
    for (int index = 0; index < cap; index++) {
      Location cropLocation = center.clone().add(CROP_OFFSETS[index][0], 1D, CROP_OFFSETS[index][1]);
      if (access.protection().canInteract(owner, cropLocation)) {
        authorized.add(MutationRuntimeStore.BlockPosition.of(cropLocation));
      }
    }
    return List.copyOf(authorized);
  }

  private void supportCrops(
      MutationRuntimeStore.Bloom bloom,
      GravebloomPulseCapability capability
  ) {
    for (MutationRuntimeStore.BlockPosition position : capability.cropPositions()) {
      Location cropLocation = location(position);
      if (cropLocation != null && isLoaded(cropLocation)) {
        J.runAt(cropLocation, () -> applyCropSupport(bloom, capability, position));
      }
    }
  }

  private void applyCropSupport(
      MutationRuntimeStore.Bloom bloom,
      GravebloomPulseCapability capability,
      MutationRuntimeStore.BlockPosition position
  ) {
    long now = System.currentTimeMillis();
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(capability.ownerId());
    Location cropLocation = location(position);
    if (cropLocation == null
        || capability.expiresAt() < now
        || !isLoaded(cropLocation)
        || !capability.cropPositions().contains(position)
        || !activeBloom(runtime, bloom, now)) {
      return;
    }
    Block block = cropLocation.getBlock();
    if (block.getBlockData() instanceof Ageable ageable && ageable.getAge() < ageable.getMaximumAge()) {
      ageable.setAge(ageable.getAge() + 1);
      block.setBlockData(ageable, false);
    }
  }

  private void supportNearby(
      MutationRuntimeStore.Bloom bloom,
      GravebloomPulseCapability capability,
      long now
  ) {
    Location center = location(bloom.position());
    if (center == null) {
      return;
    }
    double radius = access.config().getGravebloom().getRadius();
    int[] accepted = {0};
    Collection<Entity> nearby = center.getWorld().getNearbyEntities(
        center,
        radius,
        radius,
        radius,
        entity -> {
          boolean relevantAnimal = entity instanceof Animals && entity instanceof Tameable;
          if ((J.isFoliaThreading() && !J.isOwnedByCurrentRegion(entity))
              || accepted[0] >= NEARBY_ENTITY_CAP
              || (!relevantAnimal && !(entity instanceof Monster))) {
            return false;
          }
          accepted[0]++;
          return true;
        }
    );
    boolean mature = bloom.expiresAt() - now <= access.config().getGravebloom().getLifetimeMillis() / 2L;
    GravebloomTargetBudget budget = new GravebloomTargetBudget();
    for (Entity entity : nearby) {
      J.runEntity(entity, () -> inspectTargetSupport(entity, bloom, capability, budget, mature));
    }
  }

  private void inspectTargetSupport(
      Entity target,
      MutationRuntimeStore.Bloom bloom,
      GravebloomPulseCapability capability,
      GravebloomTargetBudget budget,
      boolean mature
  ) {
    long now = System.currentTimeMillis();
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(capability.ownerId());
    Location center = location(bloom.position());
    double radius = access.config().getGravebloom().getRadius();
    if (center == null
        || capability.expiresAt() < now
        || !target.isValid()
        || !activeBloom(runtime, bloom, now)
        || target.getWorld() != center.getWorld()
        || target.getLocation().distanceSquared(center) > radius * radius) {
      return;
    }
    if (target instanceof Animals animal
        && animal instanceof Tameable tameable
        && capability.ownerId().equals(PaperCompat.tamedOwnerId(tameable))
        && budget.reserveAnimal(access.config().getGravebloom().getMaximumAnimals())) {
      authorizeTargetSupport(animal, bloom, capability, GravebloomTargetKind.ANIMAL);
      return;
    }
    if (!capability.perfect() && mature && target instanceof Monster monster) {
      authorizeTargetSupport(monster, bloom, capability, GravebloomTargetKind.MONSTER);
    }
  }

  private void authorizeTargetSupport(
      Entity target,
      MutationRuntimeStore.Bloom bloom,
      GravebloomPulseCapability pulse,
      GravebloomTargetKind kind
  ) {
    Location targetLocation = target.getLocation().clone();
    GravebloomTargetRequest request = new GravebloomTargetRequest(
        pulse.ownerId(),
        pulse.loadoutGeneration(),
        pulse.graveGeneration(),
        target.getUniqueId(),
        MutationRuntimeStore.BlockPosition.of(targetLocation),
        kind,
        pulse.expiresAt()
    );
    Player owner = access.onlinePlayer(pulse.ownerId());
    if (owner == null) {
      return;
    }
    J.runEntity(owner, () -> authorizeTargetSupportOwner(owner, target, bloom, request));
  }

  private void authorizeTargetSupportOwner(
      Player owner,
      Entity target,
      MutationRuntimeStore.Bloom bloom,
      GravebloomTargetRequest request
  ) {
    long now = System.currentTimeMillis();
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(request.ownerId());
    Location targetLocation = location(request.position());
    if (targetLocation == null
        || request.expiresAt() < now
        || !eligible(owner)
        || !access.expressed(owner, MutationType.GRAVEBLOOM)
        || !activeBloom(runtime, bloom, now)
        || bloom.loadoutGeneration() != request.loadoutGeneration()
        || bloom.graveGeneration() != request.graveGeneration()
        || !access.protection().canAffectAt(owner, targetLocation, false)) {
      return;
    }
    J.runEntity(target, () -> applyTargetSupport(target, bloom, request));
  }

  private void applyTargetSupport(
      Entity target,
      MutationRuntimeStore.Bloom bloom,
      GravebloomTargetRequest request
  ) {
    long now = System.currentTimeMillis();
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(request.ownerId());
    if (request.expiresAt() < now
        || !request.targetId().equals(target.getUniqueId())
        || !target.isValid()
        || !activeBloom(runtime, bloom, now)
        || bloom.loadoutGeneration() != request.loadoutGeneration()
        || bloom.graveGeneration() != request.graveGeneration()
        || !request.position().equals(MutationRuntimeStore.BlockPosition.of(target.getLocation()))) {
      return;
    }
    if (request.kind() == GravebloomTargetKind.ANIMAL
        && target instanceof Animals animal
        && animal instanceof Tameable tameable
        && request.ownerId().equals(PaperCompat.tamedOwnerId(tameable))) {
      healAnimal(animal);
      return;
    }
    if (request.kind() != GravebloomTargetKind.MONSTER || !(target instanceof Monster monster)) {
      return;
    }
    Player regionOwnedOwner = regionOwnedPlayer(request.ownerId());
    if (regionOwnedOwner != null) {
      attractMonster(monster, regionOwnedOwner);
    }
  }

  private void healAnimal(Animals animal) {
    if (animal.isValid() && !animal.isDead()) {
      animal.setHealth(Math.min(animal.getMaxHealth(), animal.getHealth() + 1D));
    }
  }

  private void attractMonster(Monster monster, Player owner) {
    if (monster.isValid() && !monster.isDead()) {
      monster.setTarget(owner);
    }
  }

  private void showGravebloom(MutationRuntimeStore.Bloom bloom, boolean mature) {
    Location bloomLocation = location(bloom.position());
    if (bloomLocation == null) {
      return;
    }
    Location center = bloomLocation.add(0.5D, 1D, 0.5D);
    Fx.now(MutationType.GRAVEBLOOM, center, FxPriority.TRANSITION)
        .burst(Particle.SOUL, mature ? 10 : 5, mature ? 0.8D : 0.45D)
        .ring(Particle.SOUL_FIRE_FLAME, mature ? 1.8D : 0.8D, mature ? 12 : 8, 0.15D);
  }

  private Material expectedReplant(Block block) {
    BlockData data = block.getBlockData();
    if (data instanceof Ageable ageable && ageable.getAge() >= ageable.getMaximumAge()) {
      return block.getType();
    }
    if (!Tag.LOGS.isTagged(block.getType())) {
      return null;
    }
    String name = block.getType().name();
    String base = name.replace("_LOG", "").replace("_STEM", "");
    Material sapling = Material.matchMaterial(base + "_SAPLING");
    if (sapling != null) {
      return sapling;
    }
    return base.equals("MANGROVE") ? Material.MANGROVE_PROPAGULE : null;
  }

  private boolean matchesReplant(Material expected, Material placed) {
    return expected == placed;
  }

  private boolean isDeepResource(Block block) {
    MutationConfig.Deepblood config = access.config().getDeepblood();
    if (block.getY() > config.getMaximumDepthY()) {
      return false;
    }
    Material material = block.getType();
    String name = material.name();
    return material == Material.DEEPSLATE
        || material == Material.OBSIDIAN
        || material == Material.CRYING_OBSIDIAN
        || name.endsWith("_ORE");
  }

  private boolean isNaturalTerrain(Block block) {
    return block != null
        && !block.getType().isAir()
        && !block.isLiquid()
        && !provenance.isPlayerPlaced(block)
        && !provenance.isTemporary(block);
  }

  private boolean isSapling(ItemStack item) {
    if (item == null || item.getType().isAir()) {
      return false;
    }
    String name = item.getType().name();
    return name.endsWith("_SAPLING") || item.getType() == Material.MANGROVE_PROPAGULE;
  }

  private boolean isReplaceable(Block block) {
    Material material = block.getType();
    return material.isAir()
        || material == Material.SHORT_GRASS
        || material == Material.TALL_GRASS
        || material == Material.SNOW
        || material == Material.VINE;
  }

  private boolean hostileDryness(Location location) {
    World.Environment environment = location.getWorld().getEnvironment();
    return environment == World.Environment.NETHER || environment == World.Environment.THE_END;
  }

  private void containExplosion(List<Block> affectedBlocks) {
    Iterator<Block> iterator = affectedBlocks.iterator();
    ArrayList<Block> temporaryBlocks = new ArrayList<>();
    while (iterator.hasNext()) {
      Block block = iterator.next();
      if (!provenance.isTemporary(block)) {
        continue;
      }
      iterator.remove();
      temporaryBlocks.add(block);
    }
    for (Block block : temporaryBlocks) {
      forceCollapse(block);
    }
  }

  private void containPiston(List<Block> movedBlocks, BlockFace direction, Consumer<Boolean> cancel) {
    HashSet<Block> temporaryBlocks = new HashSet<>();
    for (Block block : movedBlocks) {
      if (provenance.isTemporary(block)) {
        temporaryBlocks.add(block);
      }
      Block destination = block.getRelative(direction);
      if (provenance.isTemporary(destination)) {
        temporaryBlocks.add(destination);
      }
    }
    if (temporaryBlocks.isEmpty()) {
      return;
    }
    cancel.accept(true);
    for (Block block : temporaryBlocks) {
      forceCollapse(block);
    }
  }

  private void recoverLoadedChunkBatch(List<Chunk> chunks, int start) {
    if (start >= chunks.size()) {
      return;
    }
    int end = Math.min(chunks.size(), start + RECOVERY_CHUNK_BATCH);
    for (int index = start; index < end; index++) {
      Chunk chunk = chunks.get(index);
      if (!chunk.isLoaded()) {
        continue;
      }
      if (!J.isFoliaThreading()) {
        recoverChunk(chunk);
        continue;
      }
      Location anchor = new Location(
          chunk.getWorld(),
          (chunk.getX() << 4) + 8,
          chunk.getWorld().getMinHeight(),
          (chunk.getZ() << 4) + 8
      );
      if (!J.runAt(anchor, () -> recoverChunk(chunk))) {
        Adapt.warn("Mutation startup recovery could not schedule loaded chunk " + chunk.getX() + ","
            + chunk.getZ() + " in " + chunk.getWorld().getName() + ". Persistent state will retry on chunk load.");
      }
    }
    if (end < chunks.size()) {
      J.s(() -> recoverLoadedChunkBatch(chunks, end), 1);
    }
  }

  private void recoverChunk(Chunk chunk) {
    if (chunk == null || !chunk.isLoaded()) {
      return;
    }
    try {
      Set<Block> blocks = CustomBlockData.getBlocksWithCustomData(Adapt.instance, chunk);
      if (!blocks.isEmpty()) {
        ArrayList<Block> temporary = new ArrayList<>();
        for (Block block : blocks) {
          if (provenance.isTemporary(block)) {
            temporary.add(block);
          }
        }
        restoreChunkBatch(temporary, 0);
      }
      for (Entity entity : chunk.getEntities()) {
        if (entity.getScoreboardTags().contains(MARKER_TAG)) {
          J.runEntity(entity, entity::remove);
        }
      }
    } catch (RuntimeException error) {
      reportRestorationError(
          "Mutation recovery failed for chunk " + chunk.getX() + "," + chunk.getZ()
              + " in " + chunk.getWorld().getName(),
          error
      );
    }
  }

  private boolean completePlacement(
      UUID ownerId,
      long structureId,
      int reservationId,
      long loadoutGeneration,
      MutationRuntimeStore.BlockPosition key,
      boolean placed
  ) {
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(ownerId);
    if (runtime == null) {
      return false;
    }
    boolean tell = false;
    boolean refund = false;
    synchronized (runtime) {
      MutationRuntimeStore.LatticeStructure structure = runtime.lattice.structures.get(structureId);
      boolean committed = placed && key != null;
      if (structure == null
          || runtime.loadoutGeneration != loadoutGeneration
          || structure.loadoutGeneration != loadoutGeneration
          || !structure.completePlacement(reservationId, committed)) {
        return false;
      }
      runtime.lattice.reservedBlocks = Math.max(0, runtime.lattice.reservedBlocks - 1);
      if (committed) {
        runtime.lattice.activeBlocks.add(key);
        if (!structure.told) {
          structure.told = true;
          tell = true;
        }
      }
      if (structure.isPlacementComplete() && structure.placedBlocks == 0) {
        runtime.lattice.structures.remove(structureId);
        refund = !structure.told;
        if (refund) {
          runtime.lattice.pendingRootRefunds++;
        }
      }
    }
    if (tell) {
      scheduleOwnerTell(ownerId, loadoutGeneration, MutationType.LIVING_LATTICE, Particle.COMPOSTER, 10);
    }
    if (refund) {
      scheduleRootChargeRefund(ownerId, loadoutGeneration);
    }
    return true;
  }

  private boolean placementValid(
      MutationRuntimeStore.PlayerRuntimeState runtime,
      LatticePlacementCapability capability
  ) {
    if (runtime == null) {
      return false;
    }
    synchronized (runtime) {
      MutationRuntimeStore.LatticeStructure structure = runtime.lattice.structures.get(capability.structureId());
      return runtime.loadoutGeneration == capability.loadoutGeneration()
          && structure != null
          && structure.loadoutGeneration == capability.loadoutGeneration()
          && structure.hasReservation(capability.reservationId());
    }
  }

  private void refundRootCharge(Player owner, long loadoutGeneration) {
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(owner.getUniqueId());
    if (!eligible(owner)
        || !activeLoadout(runtime, loadoutGeneration)
        || !access.expressed(owner, MutationType.LIVING_LATTICE)) {
      return;
    }
    synchronized (runtime) {
      if (runtime.lattice.pendingRootRefunds <= 0) {
        return;
      }
      runtime.lattice.pendingRootRefunds--;
    }
    applyRootRefunds(owner, 1);
  }

  private void settlePendingRootRefunds(
      Player owner,
      MutationRuntimeStore.PlayerRuntimeState runtime
  ) {
    int pending;
    synchronized (runtime) {
      pending = runtime.lattice.pendingRootRefunds;
      runtime.lattice.pendingRootRefunds = 0;
    }
    applyRootRefunds(owner, pending);
  }

  private void applyRootRefunds(Player owner, int refunds) {
    if (refunds <= 0) {
      return;
    }
    PlayerMutationData durable = access.durable(owner);
    if (durable == null) {
      return;
    }
    MutationConfig.LivingLattice config = access.config().getLivingLattice();
    durable.setLivingLatticeRootCharge(Math.min(
        config.getMaximumRootCharge(),
        durable.getLivingLatticeRootCharge() + refunds
    ));
    scheduleResourceSave(owner);
  }

  private void pruneStructures(MutationRuntimeStore.LatticeState lattice, long now) {
    Iterator<Map.Entry<Long, MutationRuntimeStore.LatticeStructure>> iterator = lattice.structures.entrySet().iterator();
    while (iterator.hasNext()) {
      MutationRuntimeStore.LatticeStructure structure = iterator.next().getValue();
      if (structure.expiresAt > now) {
        continue;
      }
      lattice.reservedBlocks = Math.max(0, lattice.reservedBlocks - structure.expireReservations());
      if (structure.placedBlocks == 0) {
        lattice.pendingRootRefunds++;
      }
      iterator.remove();
    }
  }

  private long latticeLifetime(MutationConfig.LivingLattice config, Location location, boolean perfect) {
    if (!perfect && hostileDryness(location)) {
      return Math.max(1_000L, config.getBlockLifetimeMillis() / 3L);
    }
    return config.getBlockLifetimeMillis();
  }

  private boolean activeBloom(
      MutationRuntimeStore.PlayerRuntimeState runtime,
      MutationRuntimeStore.Bloom bloom,
      long now
  ) {
    if (runtime == null) {
      return false;
    }
    synchronized (runtime) {
      if (runtime.loadoutGeneration != bloom.loadoutGeneration()
          || runtime.grave.generation != bloom.graveGeneration()
          || !runtime.grave.blooms.contains(bloom)) {
        return false;
      }
      if (bloom.expiresAt() <= now) {
        runtime.grave.blooms.remove(bloom);
        return false;
      }
      return true;
    }
  }

  private void removeBloom(UUID ownerId, MutationRuntimeStore.Bloom bloom) {
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(ownerId);
    if (runtime == null) {
      return;
    }
    synchronized (runtime) {
      runtime.grave.blooms.remove(bloom);
    }
  }

  private void scheduleResourceSave(Player player) {
    if (player == null) {
      return;
    }
    MutationRuntimeStore.PlayerRuntimeState runtime = store.player(player.getUniqueId());
    long generation = runtime.requestResourceSave();
    if (generation == 0L) {
      return;
    }
    if (!J.runEntity(player, () -> flushScheduledResourceSave(player, generation), RESOURCE_SAVE_DELAY_TICKS)
        && runtime.claimResourceSave(generation)) {
      access.save(player);
    }
  }

  private void flushScheduledResourceSave(Player player, long generation) {
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(player.getUniqueId());
    if (runtime != null && runtime.claimResourceSave(generation)) {
      access.save(player);
    }
  }

  private void flushResourceSave(Player player) {
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(player.getUniqueId());
    if (runtime != null && runtime.claimImmediateResourceSave()) {
      access.save(player);
    }
  }

  private void scheduleRootChargeRefund(UUID ownerId, long loadoutGeneration) {
    Player owner = access.onlinePlayer(ownerId);
    if (owner != null) {
      J.runEntity(owner, () -> refundRootCharge(owner, loadoutGeneration));
    }
  }

  private void scheduleOwnerTell(
      UUID ownerId,
      long loadoutGeneration,
      MutationType type,
      Particle particle,
      int count
  ) {
    Player owner = access.onlinePlayer(ownerId);
    if (owner == null) {
      return;
    }
    J.runEntity(owner, () -> {
      MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(ownerId);
      if (eligible(owner) && activeLoadout(runtime, loadoutGeneration) && access.expressed(owner, type)) {
        access.tell(owner, type, particle, count);
      }
    });
  }

  private boolean activeLoadout(MutationRuntimeStore.PlayerRuntimeState runtime, long loadoutGeneration) {
    if (runtime == null) {
      return false;
    }
    synchronized (runtime) {
      return runtime.loadoutGeneration == loadoutGeneration;
    }
  }

  private Player regionOwnedPlayer(UUID playerId) {
    Player player = access.onlinePlayer(playerId);
    if (player == null || (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(player))) {
      return null;
    }
    return eligible(player) ? player : null;
  }

  private boolean sameRegionOwnership(Player player, Location location) {
    return !J.isFoliaThreading()
        || (J.isOwnedByCurrentRegion(player) && FoliaScheduler.isOwnedByCurrentRegion(location));
  }

  private Location location(MutationRuntimeStore.BlockPosition position) {
    if (position == null) {
      return null;
    }
    World world = Bukkit.getWorld(position.worldId());
    return world == null ? null : new Location(world, position.x(), position.y(), position.z());
  }

  private void applyForcedCollapseBurden(Player owner) {
    if (access.perfect(owner)) {
      return;
    }
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(owner.getUniqueId());
    if (runtime == null) {
      return;
    }
    synchronized (runtime) {
      long now = System.currentTimeMillis();
      if (runtime.lattice.collapseLockUntil > now) {
        return;
      }
      runtime.lattice.collapseLockUntil = now + access.config().getLivingLattice().getCollapseLockMillis();
    }
    owner.setFoodLevel(Math.max(0, owner.getFoodLevel() - 2));
  }

  private void restoreBlockData(
      Block block,
      BlockData original,
      MutationRuntimeStore.BlockPosition key
  ) {
    try {
      block.setBlockData(original, false);
    } catch (RuntimeException error) {
      reportRestorationError("Failed to restore Living Lattice block at " + format(key), error);
      try {
        block.setType(Material.AIR, false);
      } catch (RuntimeException fallbackError) {
        reportRestorationError("Failed to clear unrestorable Living Lattice block at " + format(key), fallbackError);
      }
    }
  }

  private void reportRestoreScheduleFailure(MutationRuntimeStore.BlockPosition key) {
    if (restoreWarnings.add(key)) {
      Adapt.warn("Living Lattice restoration could not be scheduled at " + format(key)
          + "; its persistent marker will be retried when the chunk loads again.");
    }
  }

  private void reportRestorationError(String message, RuntimeException error) {
    Adapt.error(message);
    error.printStackTrace();
  }

  private String format(MutationRuntimeStore.BlockPosition key) {
    return key.worldId() + " " + key.x() + "," + key.y() + "," + key.z();
  }

  private boolean eligible(Player player) {
    return player != null && player.isOnline() && eligibleGameMode(player.getGameMode());
  }

  static boolean eligibleGameMode(GameMode mode) {
    return mode != null && mode != GameMode.CREATIVE && mode != GameMode.SPECTATOR;
  }

  private boolean isLoaded(Location location) {
    return location != null && location.getWorld() != null
        && location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
  }

  private UUID parseUuid(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException error) {
      return null;
    }
  }

  private void removeActiveTemporary(UUID ownerId, MutationRuntimeStore.BlockPosition key, long structureId) {
    if (ownerId == null || key == null) {
      return;
    }
    MutationRuntimeStore.PlayerRuntimeState runtime = store.existing(ownerId);
    if (runtime == null) {
      return;
    }
    synchronized (runtime) {
      if (!runtime.lattice.activeBlocks.remove(key) || structureId < 0L) {
        return;
      }
      MutationRuntimeStore.LatticeStructure structure = runtime.lattice.structures.get(structureId);
      if (structure == null) {
        return;
      }
      structure.placedBlocks = Math.max(0, structure.placedBlocks - 1);
      if (structure.isPlacementComplete() && structure.placedBlocks == 0) {
        runtime.lattice.structures.remove(structureId);
      }
    }
  }

  private record GravebloomCapability(
      UUID ownerId,
      long loadoutGeneration,
      MutationRuntimeStore.BlockPosition position,
      boolean perfect,
      long expiresAt
  ) {
  }

  private record GravebloomPulseCapability(
      UUID ownerId,
      long loadoutGeneration,
      long graveGeneration,
      MutationRuntimeStore.BlockPosition position,
      boolean perfect,
      List<MutationRuntimeStore.BlockPosition> cropPositions,
      long expiresAt
  ) {
  }

  private record GravebloomTargetRequest(
      UUID ownerId,
      long loadoutGeneration,
      long graveGeneration,
      UUID targetId,
      MutationRuntimeStore.BlockPosition position,
      GravebloomTargetKind kind,
      long expiresAt
  ) {
  }

  private record LatticePlacementCapability(
      UUID ownerId,
      MutationRuntimeStore.BlockPosition position,
      long structureId,
      long loadoutGeneration,
      int reservationId,
      boolean perfect,
      long expiresAt
  ) {
  }

  private enum GravebloomTargetKind {
    ANIMAL,
    MONSTER
  }

  private static final class GravebloomTargetBudget {
    private int animalReservations;

    synchronized boolean reserveAnimal(int maximumAnimals) {
      if (animalReservations >= maximumAnimals) {
        return false;
      }
      animalReservations++;
      return true;
    }
  }

}
