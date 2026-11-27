package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockExplodeEvent;
import art.arcane.adapt.platform.event.block.BlockPistonEvent;
import art.arcane.adapt.platform.event.entity.EntityExplodeEvent;
import art.arcane.adapt.platform.event.world.ChunkEvent;
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
import java.util.function.Predicate;

/**
 * Temporary cobwebs placed for an adaptation. Each web is journaled in its chunk so it is removed on time even across
 * restarts, and pistons, explosions and players cannot take it while it lasts.
 */
public final class RangedWebs {
  private static final DataKey<long[]> JOURNAL_KEY = DataKey.adapt("ranged-web-bomb", DataCodecs.LONG_ARRAY);
  private static final int MAX_JOURNALED_WEBS_PER_CHUNK = 4096;
  private static final int MAX_RECOVERY_CHUNKS_PER_TICK = 32;
  private static final Id COBWEB = Id.minecraft("cobweb");
  private static final Id AIR = Id.minecraft("air");

  private final SimpleAdaptation<?> owner;
  private final Map<WebKey, ActiveWeb> activeBlocks = new ConcurrentHashMap<>();
  private final Queue<ChunkRecovery> recoveryQueue = new ConcurrentLinkedQueue<>();
  private final Set<ChunkRecovery> queuedRecoveries = ConcurrentHashMap.newKeySet();
  private final AtomicBoolean recoveryScheduled = new AtomicBoolean();

  public RangedWebs(SimpleAdaptation<?> owner) {
    this.owner = owner;
  }

  /** Places a web at {@code placement} for {@code player} if the spot is free and allowed; runs {@code onPlaced} after. */
  public void place(PlayerRef player, WebPlacement placement, long expiresAt, Runnable onPlaced) {
    Loc location = placement.location();
    AdaptApi.scheduler().runAt(location, () -> {
      if (!owner.isRuntimeRegistered()
          || (AdaptApi.scheduler().isRegionized() && !AdaptApi.scheduler().owns(player))
          || !player.isOnline()
          || !owner.canBlockPlace(player, location)) {
        return;
      }
      BlockRef block = location.world().blockAt(location);
      if (!block.isAir()
          || !ProtectionProbes.attemptBlockPlaceProbe(player, block)
          || !block.isAir()) {
        return;
      }
      if (!journalWeb(block, expiresAt)) {
        return;
      }
      block.setType(COBWEB, false);
      WebKey key = webKey(block);
      activeBlocks.put(key, new ActiveWeb(block, expiresAt));
      scheduleWebRemoval(block, expiresAt);
      onPlaced.run();
    });
  }

  public void onPiston(BlockPistonEvent e) {
    e.blocks().forEach(b -> {
      if (isActiveWeb(b)) {
        e.setCancelled(true);
      }
    });
  }

  public void onExplode(BlockExplodeEvent e) {
    protectActiveWebs(e.blocks(), this::isActiveWeb);
  }

  public void onExplode(EntityExplodeEvent e) {
    protectActiveWebs(e.blocks(), this::isActiveWeb);
  }

  public void onBreak(BlockBreakEvent e) {
    if (isActiveWeb(e.block())) {
      e.setCancelled(true);
    }
  }

  public void clear() {
    recoveryQueue.clear();
    queuedRecoveries.clear();
    recoveryScheduled.set(false);
    List<ActiveWeb> activeWebs = List.copyOf(activeBlocks.values());
    activeBlocks.clear();
    for (ActiveWeb activeWeb : activeWebs) {
      AdaptApi.scheduler().runAt(activeWeb.block().location(),
          () -> removeFoundation(activeWeb.block(), activeWeb.expiresAt()));
    }
  }

  private void scheduleWebRemoval(BlockRef block, long expiresAt) {
    AdaptApi.scheduler().runAt(block.location(), () -> removeFoundation(block, expiresAt), delayTicksUntil(expiresAt, System.currentTimeMillis()));
  }

  private void removeFoundation(BlockRef block, long expiresAt) {
    WorldRef world = block.world();
    int chunkX = block.x() >> 4;
    int chunkZ = block.z() >> 4;
    if (!world.isChunkLoaded(chunkX, chunkZ)) {
      WebKey unloadedKey = webKey(block);
      activeBlocks.computeIfPresent(unloadedKey,
          (key, activeWeb) -> activeWeb.expiresAt() == expiresAt ? null : activeWeb);
      return;
    }

    WebKey key = webKey(block);
    ActiveWeb current = activeBlocks.get(key);
    if (current != null && current.expiresAt() != expiresAt) {
      return;
    }
    if (current == null && !journalMatches(block, expiresAt)) {
      return;
    }

    if (current != null) {
      activeBlocks.remove(key, current);
    }
    if (block.type().equals(COBWEB)) {
      block.setType(AIR, false);
    }
    unjournalWeb(block, expiresAt);
  }

  private static DataHolder chunkData(BlockRef block) {
    return block.world().chunkData(block.x() >> 4, block.z() >> 4);
  }

  private boolean journalWeb(BlockRef block, long expiresAt) {
    DataHolder data = chunkData(block);
    long encoded = encodeBlock(block.x() & 15, block.y(), block.z() & 15,
        block.world().minHeight());
    long[] journal = data.get(JOURNAL_KEY);
    long[] updated = upsertJournalEntry(journal, encoded, expiresAt, MAX_JOURNALED_WEBS_PER_CHUNK);
    if (updated == null) {
      return false;
    }
    data.set(JOURNAL_KEY, updated);
    return true;
  }

  private void unjournalWeb(BlockRef block, long expiresAt) {
    DataHolder data = chunkData(block);
    long[] journal = data.get(JOURNAL_KEY);
    long encoded = encodeBlock(block.x() & 15, block.y(), block.z() & 15,
        block.world().minHeight());
    long[] updated = removeJournalEntry(journal, encoded, expiresAt);
    if (updated.length == 0) {
      data.remove(JOURNAL_KEY);
    } else if (!Arrays.equals(journal, updated)) {
      data.set(JOURNAL_KEY, updated);
    }
  }

  private boolean journalMatches(BlockRef block, long expiresAt) {
    Long journalExpiry = journalExpiry(block, chunkData(block));
    return journalExpiry != null && journalExpiry == expiresAt;
  }

  public boolean isActiveWeb(BlockRef block) {
    WebKey key = webKey(block);
    if (activeBlocks.containsKey(key)) {
      return true;
    }
    return block.type().equals(COBWEB)
        && journalExpiry(block, chunkData(block)) != null;
  }

  private Long journalExpiry(BlockRef block, DataHolder data) {
    long[] journal = data.get(JOURNAL_KEY);
    long encoded = encodeBlock(block.x() & 15, block.y(), block.z() & 15,
        block.world().minHeight());
    return journalExpiry(journal, encoded);
  }

  private WebKey webKey(BlockRef block) {
    return new WebKey(block.world().uid(), block.x(), block.y(), block.z());
  }

  public void queueLoadedChunkRecovery() {
    for (WorldRef world : AdaptApi.platform().host().worlds()) {
      for (ChunkPos chunk : world.loadedChunks()) {
        queueChunkRecovery(world, chunk.x(), chunk.z());
      }
    }
  }

  public void onChunk(ChunkEvent event) {
    if (event.phase() != ChunkEvent.Phase.LOAD) {
      return;
    }
    queueChunkRecovery(event.world(), event.chunk().x(), event.chunk().z());
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
      queuedRecoveries.clear();
      recoveryScheduled.set(false);
      return;
    }

    int dispatched = 0;
    ChunkRecovery recovery;
    while (dispatched < MAX_RECOVERY_CHUNKS_PER_TICK && (recovery = recoveryQueue.poll()) != null) {
      ChunkRecovery current = recovery;
      queuedRecoveries.remove(current);
      Loc anchor = new Loc(current.world(), (current.chunkX() << 4) + 8,
          current.world().minHeight(), (current.chunkZ() << 4) + 8);
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
    long[] journal = data.get(JOURNAL_KEY);
    if (journal == null || journal.length < 2) {
      data.remove(JOURNAL_KEY);
      return;
    }

    int pairCount = journal.length / 2;
    long[] retained = new long[journal.length];
    int retainedLength = 0;
    long now = System.currentTimeMillis();
    for (int index = 0; index < pairCount; index++) {
      long encoded = journal[index * 2];
      long expiresAt = journal[(index * 2) + 1];
      int y = decodeY(encoded, world.minHeight());
      if (y < world.minHeight() || y >= world.maxHeight()) {
        continue;
      }
      BlockRef block = world.blockAt((recovery.chunkX() << 4) + decodeX(encoded), y,
          (recovery.chunkZ() << 4) + decodeZ(encoded));
      if (!block.type().equals(COBWEB)) {
        activeBlocks.remove(webKey(block));
        continue;
      }
      if (expiresAt <= now) {
        activeBlocks.remove(webKey(block));
        block.setType(AIR, false);
        continue;
      }
      retained[retainedLength++] = encoded;
      retained[retainedLength++] = expiresAt;
      activeBlocks.put(webKey(block), new ActiveWeb(block, expiresAt));
      scheduleWebRemoval(block, expiresAt);
    }
    if (retainedLength == 0) {
      data.remove(JOURNAL_KEY);
    } else {
      data.set(JOURNAL_KEY, Arrays.copyOf(retained, retainedLength));
    }
  }

  public static int delayTicksUntil(long expiresAt, long now) {
    long remainingMillis = Math.max(1L, expiresAt - now);
    return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, (remainingMillis + 49L) / 50L));
  }

  static long encodeBlock(int localX, int y, int localZ, int minHeight) {
    return ((long) (y - minHeight) << 8) | ((long) (localZ & 15) << 4) | (localX & 15);
  }

  static int decodeX(long encoded) {
    return (int) encoded & 15;
  }

  static int decodeZ(long encoded) {
    return ((int) encoded >>> 4) & 15;
  }

  static int decodeY(long encoded, int minHeight) {
    return minHeight + (int) (encoded >>> 8);
  }

  static long[] upsertJournalEntry(long[] journal, long encoded, long expiresAt, int maxEntries) {
    long[] normalized = journal == null ? new long[0] : Arrays.copyOf(journal, journal.length & ~1);
    for (int index = 0; index < normalized.length; index += 2) {
      if (normalized[index] == encoded) {
        normalized[index + 1] = expiresAt;
        return normalized;
      }
    }
    if (normalized.length / 2 >= maxEntries) {
      return null;
    }
    long[] expanded = Arrays.copyOf(normalized, normalized.length + 2);
    expanded[normalized.length] = encoded;
    expanded[normalized.length + 1] = expiresAt;
    return expanded;
  }

  static long[] removeJournalEntry(long[] journal, long encoded, long expiresAt) {
    if (journal == null || journal.length < 2) {
      return new long[0];
    }
    int normalizedLength = journal.length & ~1;
    for (int index = 0; index < normalizedLength; index += 2) {
      if (journal[index] != encoded || journal[index + 1] != expiresAt) {
        continue;
      }
      long[] compacted = new long[normalizedLength - 2];
      System.arraycopy(journal, 0, compacted, 0, index);
      System.arraycopy(journal, index + 2, compacted, index, normalizedLength - index - 2);
      return compacted;
    }
    return Arrays.copyOf(journal, normalizedLength);
  }

  static Long journalExpiry(long[] journal, long encoded) {
    if (journal == null) {
      return null;
    }
    for (int index = 0; index + 1 < journal.length; index += 2) {
      if (journal[index] == encoded) {
        return journal[index + 1];
      }
    }
    return null;
  }

  static void protectActiveWebs(List<BlockRef> blocks, Predicate<BlockRef> activeWeb) {
    blocks.removeIf(activeWeb);
  }

  public static List<WebPlacement> placementCandidates(WorldRef world, int x, int y, int z) {
    return List.of(
        new WebPlacement(world, x, y, z),
        new WebPlacement(world, x, y + 1, z),
        new WebPlacement(world, x, y - 1, z),
        new WebPlacement(world, x, y, z + 1),
        new WebPlacement(world, x, y, z - 1),
        new WebPlacement(world, x + 1, y, z),
        new WebPlacement(world, x - 1, y, z)
    );
  }

  private record WebKey(UUID worldId, int x, int y, int z) {
  }

  private record ActiveWeb(BlockRef block, long expiresAt) {
  }

  private record ChunkRecovery(WorldRef world, int chunkX, int chunkZ) {
  }

  public record WebPlacement(WorldRef world, int x, int y, int z) {
    public Loc location() {
      return new Loc(world, x, y, z);
    }
  }

}
