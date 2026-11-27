package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockPistonEvent;
import art.arcane.adapt.platform.event.world.ChunkEvent;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.ChunkPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Tinted glass that stands for a while and then goes away. While it stands it cannot be broken, pushed or blown up, so
 * it never drops. Every block is journaled in its chunk's data and cleared when the chunk next loads, so a restart or a
 * crash leaves none behind. The owner forwards its events here, keeps its own journal and shows its own effects when a
 * block goes away on its timer.
 */
public final class ArchitectTemporaryBlocks {
  private static final Id TINTED_GLASS = Id.minecraft("tinted_glass");
  private static final Id AIR = Id.minecraft("air");
  private static final int MAX_JOURNALED_BLOCKS_PER_CHUNK = 4096;
  private static final int MAX_RECOVERY_CHUNKS_PER_TICK = 32;
  private final SimpleAdaptation<?> owner;
  private final DataKey<int[]> journalKey;
  private final Consumer<BlockRef> expired;
  private final ArchitectBlockMarks activeBlocks = new ArchitectBlockMarks();
  private final Queue<ChunkRecovery> recoveryQueue = new ConcurrentLinkedQueue<>();
  private final Set<ChunkRecovery> queuedRecoveries = ConcurrentHashMap.newKeySet();
  private final AtomicBoolean recoveryScheduled = new AtomicBoolean();
  private final Map<Spot, Long> raisedGenerations = new ConcurrentHashMap<>();
  private final AtomicLong nextGeneration = new AtomicLong();

  public ArchitectTemporaryBlocks(SimpleAdaptation<?> owner, String journal, Consumer<BlockRef> expired) {
    this.owner = owner;
    this.journalKey = DataKey.adapt(journal, DataCodecs.INT_ARRAY);
    this.expired = expired;
  }

  /** Raises one block where the player may build, for {@code durationTicks}; false when nothing was raised. */
  public boolean raise(PlayerRef player, BlockRef block, int durationTicks) {
    Loc location = block.location();
    if (!AdaptApi.scheduler().owns(location)
        || (AdaptApi.scheduler().isRegionized() && !AdaptApi.scheduler().owns(player))) {
      return false;
    }
    if (!block.type().isAir()) {
      return false;
    }

    Loc center = location.add(0.5D, 0.5D, 0.5D);
    if ((AdaptApi.scheduler().isRegionized() && !AdaptApi.scheduler().ownsArea(center, 0.5D, 0.5D))
        || hasHangingDecoration(block.world().nearbyEntities(center.position(), .5, .5, .5))) {
      return false;
    }

    if (!owner.canBlockPlace(player, location)
        || !ProtectionProbes.attemptBlockPlaceProbe(player, block)) {
      return false;
    }
    if (!AdaptApi.scheduler().owns(location)
        || (AdaptApi.scheduler().isRegionized() && !AdaptApi.scheduler().owns(player))
        || !block.type().isAir()
        || !journal(block)) {
      return false;
    }
    boolean cleanupScheduled = false;
    Spot spot = Spot.of(block);
    long generation = nextGeneration.incrementAndGet();
    try {
      block.setType(TINTED_GLASS, false);
      activeBlocks.add(block);
      raisedGenerations.put(spot, generation);
      cleanupScheduled = AdaptApi.scheduler().runAt(location, () -> expire(block, spot, generation), Math.max(1, durationTicks));
      return cleanupScheduled;
    } finally {
      if (!cleanupScheduled) {
        raisedGenerations.remove(spot, generation);
        activeBlocks.remove(block);
        if (TINTED_GLASS.equals(block.type())) {
          block.setType(AIR, false);
        }
        unjournal(block);
      }
    }
  }

  private static boolean hasHangingDecoration(List<EntityRef> entities) {
    for (EntityRef entity : entities) {
      if (entity.has(EntityTrait.ITEM_FRAME) || entity.has(EntityTrait.PAINTING)) {
        return true;
      }
    }
    return false;
  }

  /** Runs when a raise's timer ends; a block taken down early and raised again since belongs to the newer raise. */
  private void expire(BlockRef block, Spot spot, long generation) {
    if (raisedGenerations.remove(spot, generation)) {
      remove(block, true);
    }
  }

  public void remove(BlockRef block, boolean renderEffects) {
    raisedGenerations.remove(Spot.of(block));
    activeBlocks.remove(block);
    WorldRef world = block.world();
    if (!world.isChunkLoaded(block.x() >> 4, block.z() >> 4)) {
      return;
    }
    boolean removed = TINTED_GLASS.equals(block.type());
    if (removed) {
      block.setType(AIR, false);
    }
    unjournal(block);
    if (removed && renderEffects) {
      expired.accept(block);
    }
  }

  public void on(BlockPistonEvent e) {
    for (BlockRef block : e.blocks()) {
      if (activeBlocks.contains(block)) {
        AdaptApi.log().verbose(e.extending()
            ? "Cancelled Piston Extend on a temporary Architect block"
            : "Cancelled Piston Retract on a temporary Architect block");
        e.setCancelled(true);
        return;
      }
    }
  }

  public void on(BlockBreakEvent e) {
    if (activeBlocks.contains(e.block())) {
      e.setCancelled(true);
    }
  }

  /** Spares the temporary blocks from an explosion's block list. */
  public void spare(List<BlockRef> exploded) {
    activeBlocks.spare(exploded);
  }

  public void on(ChunkEvent event) {
    if (event.phase() == ChunkEvent.Phase.LOAD) {
      queueChunkRecovery(event.world(), event.chunk().x(), event.chunk().z());
    }
  }

  /** Clears journaled blocks left in every loaded chunk; call when the owner starts. */
  public void recoverLoadedChunks() {
    for (WorldRef world : AdaptApi.platform().host().worlds()) {
      for (ChunkPos chunk : world.loadedChunks()) {
        queueChunkRecovery(world, chunk.x(), chunk.z());
      }
    }
  }

  /** Takes every standing block down; call when the owner stops. */
  public void clear() {
    recoveryQueue.clear();
    queuedRecoveries.clear();
    recoveryScheduled.set(false);
    raisedGenerations.clear();
    for (BlockRef block : activeBlocks.clear()) {
      AdaptApi.scheduler().runAt(block.location(), () -> remove(block, false));
    }
  }

  private boolean journal(BlockRef block) {
    DataHolder data = block.world().chunkData(block.x() >> 4, block.z() >> 4);
    int encoded = encodeBlock(block.x() & 15, block.y(), block.z() & 15, block.world().minHeight());
    int[] journal = data.get(journalKey);
    if (journal == null) {
      data.set(journalKey, new int[]{encoded});
      return true;
    }
    for (int existing : journal) {
      if (existing == encoded) {
        return true;
      }
    }
    if (journal.length >= MAX_JOURNALED_BLOCKS_PER_CHUNK) {
      return false;
    }
    int[] expanded = Arrays.copyOf(journal, journal.length + 1);
    expanded[journal.length] = encoded;
    data.set(journalKey, expanded);
    return true;
  }

  private void unjournal(BlockRef block) {
    DataHolder data = block.world().chunkData(block.x() >> 4, block.z() >> 4);
    int[] journal = data.get(journalKey);
    if (journal == null || journal.length == 0) {
      return;
    }

    int encoded = encodeBlock(block.x() & 15, block.y(), block.z() & 15, block.world().minHeight());
    int found = -1;
    for (int index = 0; index < journal.length; index++) {
      if (journal[index] == encoded) {
        found = index;
        break;
      }
    }
    if (found < 0) {
      return;
    }
    if (journal.length == 1) {
      data.remove(journalKey);
      return;
    }

    int[] compacted = new int[journal.length - 1];
    System.arraycopy(journal, 0, compacted, 0, found);
    System.arraycopy(journal, found + 1, compacted, found, journal.length - found - 1);
    data.set(journalKey, compacted);
  }

  private void queueChunkRecovery(WorldRef world, int chunkX, int chunkZ) {
    if (!owner.isRuntimeRegistered()) {
      return;
    }
    ChunkRecovery recovery = new ChunkRecovery(world, chunkX, chunkZ);
    if (!queuedRecoveries.add(recovery)) {
      return;
    }
    recoveryQueue.add(recovery);
    if (recoveryScheduled.compareAndSet(false, true)) {
      AdaptApi.scheduler().runGlobal(this::drainRecoveryQueue, 1);
    }
  }

  private void drainRecoveryQueue() {
    if (!owner.isRuntimeRegistered()) {
      recoveryQueue.clear();
      recoveryScheduled.set(false);
      return;
    }

    int dispatched = 0;
    ChunkRecovery recovery;
    while (dispatched < MAX_RECOVERY_CHUNKS_PER_TICK && (recovery = recoveryQueue.poll()) != null) {
      ChunkRecovery current = recovery;
      queuedRecoveries.remove(current);
      Loc anchor = new Loc(current.world(), (current.chunkX() << 4) + 8, current.world().minHeight(),
          (current.chunkZ() << 4) + 8);
      AdaptApi.scheduler().runAt(anchor, () -> recoverChunk(current));
      dispatched++;
    }

    recoveryScheduled.set(false);
    if (!recoveryQueue.isEmpty() && recoveryScheduled.compareAndSet(false, true)) {
      AdaptApi.scheduler().runGlobal(this::drainRecoveryQueue, 1);
    }
  }

  private void recoverChunk(ChunkRecovery recovery) {
    WorldRef world = recovery.world();
    if (!world.isChunkLoaded(recovery.chunkX(), recovery.chunkZ())) {
      return;
    }

    DataHolder data = world.chunkData(recovery.chunkX(), recovery.chunkZ());
    int[] journal = data.get(journalKey);
    if (journal == null || journal.length == 0) {
      return;
    }

    int limit = Math.min(journal.length, MAX_JOURNALED_BLOCKS_PER_CHUNK);
    for (int index = 0; index < limit; index++) {
      int encoded = journal[index];
      int y = decodeY(encoded, world.minHeight());
      if (y < world.minHeight() || y >= world.maxHeight()) {
        continue;
      }
      BlockRef block = world.blockAt((recovery.chunkX() << 4) + decodeX(encoded), y,
          (recovery.chunkZ() << 4) + decodeZ(encoded));
      raisedGenerations.remove(Spot.of(block));
      activeBlocks.remove(block);
      if (TINTED_GLASS.equals(block.type())) {
        block.setType(AIR, false);
      }
    }
    if (journal.length > limit) {
      data.set(journalKey, Arrays.copyOfRange(journal, limit, journal.length));
      queueChunkRecovery(world, recovery.chunkX(), recovery.chunkZ());
    } else {
      data.remove(journalKey);
    }
  }

  static int encodeBlock(int localX, int y, int localZ, int minHeight) {
    return ((y - minHeight) << 8) | ((localZ & 15) << 4) | (localX & 15);
  }

  static int decodeX(int encoded) {
    return encoded & 15;
  }

  static int decodeZ(int encoded) {
    return (encoded >>> 4) & 15;
  }

  static int decodeY(int encoded, int minHeight) {
    return minHeight + (encoded >>> 8);
  }

  private record ChunkRecovery(WorldRef world, int chunkX, int chunkZ) {
  }

  private record Spot(UUID worldId, BlockPos pos) {
    private static Spot of(BlockRef block) {
      return new Spot(block.world().uid(), block.pos());
    }
  }
}
