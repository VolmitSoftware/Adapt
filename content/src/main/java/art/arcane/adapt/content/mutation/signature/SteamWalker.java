package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneWet;
import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockDropItemEvent;
import art.arcane.adapt.platform.event.block.BlockExplodeEvent;
import art.arcane.adapt.platform.event.block.BlockFormEvent;
import art.arcane.adapt.platform.event.block.BlockFromToEvent;
import art.arcane.adapt.platform.event.block.BlockPistonEvent;
import art.arcane.adapt.platform.event.entity.EntityExplodeEvent;
import art.arcane.adapt.platform.event.player.PlayerBucketEmptyEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.world.ChunkEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.ChunkPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.Environment;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Seaborne and Nether, either leading, replacing Convergence: stepping onto lava while wet starts a steam walk that
 * cools the lava under your feet for a moment, source lava to obsidian and flowing lava to cobblestone, which then melts
 * back to the lava it was and never drops. Water buckets also empty in the Nether for you; that water stays a still
 * pool that never spreads or hardens lava, and it evaporates after a while, after a restart at the latest.
 */
public final class SteamWalker extends Signature<SteamWalker.Config> {
  private static final Id LAVA = Id.minecraft("lava");
  private static final Id OBSIDIAN = Id.minecraft("obsidian");
  private static final Id COBBLESTONE = Id.minecraft("cobblestone");
  private static final Id WATER = Id.minecraft("water");
  private static final Id AIR = Id.minecraft("air");
  private static final Id WATER_BUCKET = Id.minecraft("water_bucket");
  private static final Id BUCKET = Id.minecraft("bucket");
  private static final int MAX_JOURNALED_PER_CHUNK = 1024;
  private static final int[][] SIDES = {{0, 0, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
  private final DataKey<int[]> cooledJournal = DataKey.adapt("steam-walker-obsidian", DataCodecs.INT_ARRAY);
  private final DataKey<int[]> poolJournal = DataKey.adapt("steam-walker-water", DataCodecs.INT_ARRAY);
  private final Map<Spot, BlockStateRef> cooled = new ConcurrentHashMap<>();
  private final Set<Spot> pools = ConcurrentHashMap.newKeySet();
  private final Map<UUID, Long> walking = playerState();
  private final Map<UUID, Long> lastWet = playerState();
  private final Map<UUID, Deque<BlockRef>> waters = playerState();

  public SteamWalker(MutationSlot slot) {
    super(slot);
    setLocalizationKey(PairMessages.signatureKey(slot));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("obsidian"));
  }

  @Override
  public void addSignatureStats(int level, StatLines v) {
    statLore(v, Format.duration(walkTicks(level) * 50D, 0), 1);
    statLore(v, (radius(level) * 2) + 1, 2);
    statLore(v, maxWaters(level), 3);
  }

  @Override
  protected void onRuntimeActivated() {
    for (WorldRef world : AdaptApi.platform().host().worlds()) {
      for (ChunkPos chunk : world.loadedChunks()) {
        AdaptApi.scheduler().runAt(new Loc(world, (chunk.x() << 4) + 8, world.minHeight(), (chunk.z() << 4) + 8),
            () -> recover(world, chunk.x(), chunk.z()));
      }
    }
  }

  @Override
  public void unregister() {
    for (Map.Entry<Spot, BlockStateRef> entry : cooled.entrySet()) {
      BlockRef block = entry.getKey().block();
      AdaptApi.scheduler().runAt(block.location(), () -> melt(List.of(block)));
    }
    super.unregister();
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerMoveEvent e) {
    if (!e.changedBlock()) {
      return;
    }
    PlayerRef p = e.player();
    if (!hasActiveAdaptation(p) || !usable()) {
      return;
    }
    long now = System.currentTimeMillis();
    if (SeaborneWet.isWet(p)) {
      lastWet.put(p.uuid(), now);
    }
    BlockRef below = feet(p).relative(0, -1, 0);
    Long until = walking.get(p.uuid());
    if (until != null && until > now) {
      cool(p, below, getActiveLevel(p));
      return;
    }
    Long wet = lastWet.get(p.uuid());
    if (LAVA.equals(below.type()) && wet != null && now - wet <= getConfig().wetGraceMillis) {
      fire(p, null, below.location(), false);
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerBucketEmptyEvent e) {
    PlayerRef p = e.player();
    BlockRef target = e.block();
    if (e.isCancelled() || !WATER_BUCKET.equals(e.bucket()) || target.world().environment() != Environment.NETHER) {
      return;
    }
    int level = getActiveLevel(p);
    if (level <= 0 || !usable() || !(target.isAir() || target.isReplaceable()) || !canBlockPlace(p, target.location())
        || !ProtectionProbes.attemptBlockPlaceProbe(p, target) || !journal(target, poolJournal, 0)) {
      return;
    }
    e.setCancelled(true);
    pools.add(Spot.of(target));
    target.setType(WATER, true);
    if (p.gameMode() != GameMode.CREATIVE) {
      emptyBucket(p);
    }
    Deque<BlockRef> poured = waters.computeIfAbsent(p.uuid(), (UUID ignored) -> new ArrayDeque<>());
    synchronized (poured) {
      poured.addLast(target);
      while (poured.size() > maxWaters(level)) {
        BlockRef oldest = poured.pollFirst();
        AdaptApi.scheduler().runAt(oldest.location(), () -> evaporate(oldest));
      }
    }
    AdaptApi.scheduler().runAt(target.location(), () -> {
      synchronized (poured) {
        poured.remove(target);
      }
      evaporate(target);
    }, Math.max(1, getConfig().evaporateTicks));
  }

  /** A pool stays one still source: no flow, so no new sources between pools and no water reaching lava. */
  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockFromToEvent e) {
    if (!pools.isEmpty() && pools.contains(Spot.of(e.block()))) {
      e.setCancelled(true);
    }
  }

  /** Lava touching a pool forms no obsidian, cobblestone or stone. */
  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockFormEvent e) {
    if (pools.isEmpty()) {
      return;
    }
    BlockRef block = e.block();
    for (int[] side : SIDES) {
      if (pools.contains(Spot.of(block.relative(side[0], side[1], side[2])))) {
        e.setCancelled(true);
        return;
      }
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockBreakEvent e) {
    if (!cooled.isEmpty() && cooled.containsKey(Spot.of(e.block()))) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockDropItemEvent e) {
    if (!cooled.isEmpty() && cooled.containsKey(Spot.of(e.block()))) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityExplodeEvent e) {
    if (!cooled.isEmpty()) {
      e.blocks().removeIf((BlockRef block) -> cooled.containsKey(Spot.of(block)));
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockExplodeEvent e) {
    if (!cooled.isEmpty()) {
      e.blocks().removeIf((BlockRef block) -> cooled.containsKey(Spot.of(block)));
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockPistonEvent e) {
    if (cooled.isEmpty()) {
      return;
    }
    for (BlockRef block : e.blocks()) {
      if (cooled.containsKey(Spot.of(block))) {
        e.setCancelled(true);
        return;
      }
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(ChunkEvent e) {
    if (e.phase() == ChunkEvent.Phase.LOAD) {
      WorldRef world = e.world();
      int x = e.chunk().x();
      int z = e.chunk().z();
      AdaptApi.scheduler().runAt(new Loc(world, (x << 4) + 8, world.minHeight(), (z << 4) + 8), () -> recover(world, x, z));
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    walking.remove(e.player().uuid());
    lastWet.remove(e.player().uuid());
  }

  @Override
  public Outcome run(BlockUse use) {
    PlayerRef p = use.player();
    int ticks = walkTicks(use.level());
    walking.put(p.uuid(), System.currentTimeMillis() + (ticks * 50L));
    cool(p, feet(p).relative(0, -1, 0), use.level());
    fx(p.location(), FxPriority.TRANSITION)
        .burst(Particles.CLOUD, 12, 0.6D)
        .sound(Sounds.BLOCK_FIRE_EXTINGUISH, 0.8F, 0.8F);
    return Outcome.lasting(p, ticks);
  }

  private void cool(PlayerRef p, BlockRef center, int level) {
    int radius = radius(level);
    Map<Long, List<BlockRef>> byChunk = new LinkedHashMap<>();
    for (int dx = -radius; dx <= radius; dx++) {
      for (int dz = -radius; dz <= radius; dz++) {
        BlockRef block = center.relative(dx, 0, dz);
        if (LAVA.equals(block.type()) && !cooled.containsKey(Spot.of(block)) && canBlockPlace(p, block.location())
            && ProtectionProbes.attemptBlockPlaceProbe(p, block)) {
          byChunk.computeIfAbsent(ChunkPos.key(block.x() >> 4, block.z() >> 4), (Long ignored) -> new ArrayList<>())
              .add(block);
        }
      }
    }
    int cooledNow = 0;
    for (List<BlockRef> blocks : byChunk.values()) {
      List<BlockRef> journaled = cool(blocks);
      if (!journaled.isEmpty()) {
        cooledNow += journaled.size();
        AdaptApi.scheduler().runAt(journaled.getFirst().location(), () -> melt(journaled), Math.max(1, getConfig().cooledTicks));
      }
    }
    if (cooledNow > 0) {
      fx(center.center().add(0D, 1D, 0D), FxPriority.TRAIL).burst(Particles.CLOUD, Math.min(8, cooledNow * 2), 0.6D);
    }
  }

  /** Cools lava blocks of one chunk with a single journal write; returns those cooled. */
  private List<BlockRef> cool(List<BlockRef> blocks) {
    BlockRef first = blocks.getFirst();
    DataHolder data = first.world().chunkData(first.x() >> 4, first.z() >> 4);
    int[] journal = data.get(cooledJournal);
    int had = journal == null ? 0 : journal.length;
    int room = Math.min(blocks.size(), MAX_JOURNALED_PER_CHUNK - had);
    if (room <= 0) {
      return List.of();
    }
    int[] grown = journal == null ? new int[room] : Arrays.copyOf(journal, had + room);
    List<BlockRef> done = new ArrayList<>(room);
    for (int i = 0; i < room; i++) {
      BlockRef block = blocks.get(i);
      BlockStateRef lava = block.state();
      int level = lavaLevel(lava);
      grown[had + i] = encode(block, level);
      cooled.put(Spot.of(block), lava);
      block.setType(cooledType(level), false);
      done.add(block);
    }
    data.set(cooledJournal, grown);
    return done;
  }

  /** Melts cooled blocks of one chunk back to their lava with a single journal write. */
  private void melt(List<BlockRef> blocks) {
    List<BlockRef> melted = new ArrayList<>(blocks.size());
    for (BlockRef block : blocks) {
      BlockStateRef lava = cooled.remove(Spot.of(block));
      if (lava == null) {
        continue;
      }
      if (cooledType(lavaLevel(lava)).equals(block.type())) {
        block.setState(lava, true);
      }
      melted.add(block);
    }
    if (!melted.isEmpty()) {
      unjournal(melted, cooledJournal);
    }
  }

  private void evaporate(BlockRef block) {
    if (!pools.remove(Spot.of(block))) {
      return;
    }
    if (WATER.equals(block.type())) {
      block.setType(AIR, true);
    }
    unjournal(List.of(block), poolJournal);
  }

  private static Id cooledType(int lavaLevel) {
    return lavaLevel == 0 ? OBSIDIAN : COBBLESTONE;
  }

  private static void emptyBucket(PlayerRef p) {
    ItemRef bucket = AdaptApi.platform().host().items().create(BUCKET, 1);
    if (WATER_BUCKET.equals(p.inventory().mainHand().type())) {
      p.inventory().setMainHand(bucket);
    } else if (WATER_BUCKET.equals(p.inventory().offHand().type())) {
      p.inventory().setOffHand(bucket);
    }
  }

  private static BlockRef feet(PlayerRef p) {
    Loc at = p.location();
    return at.world().blockAt(at.blockX(), (int) Math.floor(at.y() + 0.01D), at.blockZ());
  }

  private static int lavaLevel(BlockStateRef state) {
    String level = state.property("level");
    try {
      return level == null ? 0 : Math.max(0, Math.min(15, Integer.parseInt(level)));
    } catch (NumberFormatException ignored) {
      return 0;
    }
  }

  private static boolean journal(BlockRef block, DataKey<int[]> key, int level) {
    DataHolder data = block.world().chunkData(block.x() >> 4, block.z() >> 4);
    int[] journal = data.get(key);
    if (journal == null) {
      data.set(key, new int[]{encode(block, level)});
      return true;
    }
    if (journal.length >= MAX_JOURNALED_PER_CHUNK) {
      return false;
    }
    int[] grown = Arrays.copyOf(journal, journal.length + 1);
    grown[journal.length] = encode(block, level);
    data.set(key, grown);
    return true;
  }

  /** Drops the entries of {@code blocks}, all in one chunk, from a journal. */
  private static void unjournal(List<BlockRef> blocks, DataKey<int[]> key) {
    BlockRef first = blocks.getFirst();
    DataHolder data = first.world().chunkData(first.x() >> 4, first.z() >> 4);
    int[] journal = data.get(key);
    if (journal == null) {
      return;
    }
    int[] kept = Arrays.stream(journal).filter((int entry) -> blocks.stream().noneMatch((BlockRef block) -> sameSpot(entry, block)))
        .toArray();
    if (kept.length == 0) {
      data.remove(key);
    } else if (kept.length != journal.length) {
      data.set(key, kept);
    }
  }

  /** Undoes what a restart or crash left in a chunk: cooled lava melts back and poured water evaporates. */
  private void recover(WorldRef world, int chunkX, int chunkZ) {
    if (!world.isChunkLoaded(chunkX, chunkZ)) {
      return;
    }
    DataHolder data = world.chunkData(chunkX, chunkZ);
    recover(world, chunkX, chunkZ, data, cooledJournal, (BlockRef block, int level) -> {
      if (cooled.containsKey(Spot.of(block))) {
        return true;
      }
      if (cooledType(level).equals(block.type())) {
        block.setType(LAVA, false);
        block.setState(block.state().with("level", Integer.toString(level)), true);
      }
      return false;
    });
    recover(world, chunkX, chunkZ, data, poolJournal, (BlockRef block, int level) -> {
      if (pools.contains(Spot.of(block))) {
        return true;
      }
      if (WATER.equals(block.type())) {
        block.setType(AIR, true);
      }
      return false;
    });
  }

  private static void recover(WorldRef world, int chunkX, int chunkZ, DataHolder data, DataKey<int[]> key, Recovery recovery) {
    int[] journal = data.get(key);
    if (journal == null) {
      return;
    }
    int[] kept = Arrays.stream(journal).filter((int entry) -> recovery.keep(world.blockAt((chunkX << 4) + (entry & 15),
        world.minHeight() + (entry >>> 12), (chunkZ << 4) + ((entry >>> 4) & 15)), (entry >>> 8) & 15)).toArray();
    if (kept.length == 0) {
      data.remove(key);
    } else {
      data.set(key, kept);
    }
  }

  private static int encode(BlockRef block, int level) {
    return ((block.y() - block.world().minHeight()) << 12) | ((level & 15) << 8) | ((block.z() & 15) << 4) | (block.x() & 15);
  }

  private static boolean sameSpot(int entry, BlockRef block) {
    return (entry & 15) == (block.x() & 15) && ((entry >>> 4) & 15) == (block.z() & 15)
        && (entry >>> 12) == block.y() - block.world().minHeight();
  }

  int walkTicks(int level) {
    return (int) Math.round(SignatureKit.scaled(level, getMaxLevel(), getConfig().walkTicksAtFirstLevel, getConfig().walkTicksAtTopLevel));
  }

  int radius(int level) {
    return (int) Math.round(SignatureKit.scaled(level, getMaxLevel(), getConfig().radiusAtFirstLevel, getConfig().radiusAtTopLevel));
  }

  int maxWaters(int level) {
    return Math.max(1, (int) Math.round(SignatureKit.scaled(level, getMaxLevel(), getConfig().watersAtFirstLevel, getConfig().watersAtTopLevel)));
  }

  /** Whether a journal entry stays: its block is still cooled or still a pool. */
  private interface Recovery {
    boolean keep(BlockRef block, int level);
  }

  private record Spot(WorldRef world, BlockPos pos) {
    private static Spot of(BlockRef block) {
      return new Spot(block.world(), block.pos());
    }

    private BlockRef block() {
      return world.blockAt(pos.x(), pos.y(), pos.z());
    }
  }

  @ConfigDescription("Lava you touch while wet cools under your feet for a moment, and water buckets work in the Nether.")
  public static class Config extends Signature.Config {
    @ConfigDoc(value = "Length of a steam walk at level 1, in ticks.", impact = "Higher values cross wider lava.")
    double walkTicksAtFirstLevel = 120;
    @ConfigDoc(value = "Length of a steam walk at the top level, in ticks.", impact = "Higher values cross wider lava.")
    double walkTicksAtTopLevel = 240;
    @ConfigDoc(value = "Blocks cooled around the block under you at level 1, in each direction.", impact = "Higher values make a wider path.")
    double radiusAtFirstLevel = 1;
    @ConfigDoc(value = "Blocks cooled around the block under you at the top level, in each direction.", impact = "Higher values make a wider path.")
    double radiusAtTopLevel = 2;
    @ConfigDoc(value = "Ticks a cooled block stays solid before it melts back.", impact = "Higher values leave the path longer.")
    int cooledTicks = 60;
    @ConfigDoc(value = "Time after leaving water or rain that you still count as wet, in milliseconds.", impact = "Higher values give more time to reach the lava.")
    long wetGraceMillis = 4000;
    @ConfigDoc(value = "Ticks before water poured in the Nether evaporates.", impact = "Higher values keep it longer.")
    int evaporateTicks = 600;
    @ConfigDoc(value = "Water sources you may keep in the Nether at once at level 1.", impact = "Higher values allow more pools.")
    double watersAtFirstLevel = 1;
    @ConfigDoc(value = "Water sources you may keep in the Nether at once at the top level.", impact = "Higher values allow more pools.")
    double watersAtTopLevel = 3;
  }
}
