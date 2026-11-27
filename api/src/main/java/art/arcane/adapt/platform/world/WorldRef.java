package art.arcane.adapt.platform.world;

import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.entity.ArmorStandStyle;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.block.BlockChange;
import art.arcane.adapt.platform.entity.display.BlockDisplayRef;
import art.arcane.adapt.platform.entity.display.ItemDisplayRef;
import art.arcane.adapt.platform.entity.display.TextDisplayRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.ChunkPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A loaded world or dimension. {@link #uid()}, {@link #name()} and {@link #key()} are safe on any
 * thread; everything else follows region ownership (server thread on modded). The handle goes
 * stale when the world unloads; {@link #isLoaded()} reports it. Equality is by {@link #uid()}.
 */
@ApiStatus.AvailableSince("1.0.0")
public interface WorldRef {
  /** Unique id of the world; safe on any thread. */
  UUID uid();

  /** World name; safe on any thread. */
  String name();

  /** Dimension key, for example {@code minecraft:overworld}. */
  Id key();

  /** False once the world unloaded and the handle went stale. */
  boolean isLoaded();

  /** World generation seed. */
  long seed();

  /** Dimension kind. */
  Environment environment();

  /** Lowest buildable Y, inclusive. */
  int minHeight();

  /** Top build limit, exclusive: the highest buildable Y plus one. */
  int maxHeight();

  /** Sea level Y. */
  int seaLevel();

  /** Time of day in ticks, 0 to 23999; 0 is sunrise. */
  long time();

  /** Total world time in ticks, which keeps counting across days. */
  long fullTime();

  /** True while it is daytime in this world. */
  boolean isDay();

  /** True while it rains or snows. */
  boolean hasStorm();

  /** True during a thunderstorm. */
  boolean isThundering();

  /**
   * True when rain falls on the block: a storm, nothing above it that stops rain, and a biome where it rains there
   * rather than snowing or staying dry.
   */
  boolean isRainingAt(int x, int y, int z);

  /** Every loaded entity of the world. Owning thread only. */
  List<EntityRef> entities();

  /** Block at the coordinates. */
  BlockRef blockAt(int x, int y, int z);

  /** Block at the position. */
  default BlockRef blockAt(BlockPos pos) {
    return blockAt(pos.x(), pos.y(), pos.z());
  }

  /** Block that contains the location. */
  default BlockRef blockAt(Loc location) {
    return blockAt(location.blockX(), location.blockY(), location.blockZ());
  }

  /** Y of the topmost block in the column that blocks motion or holds a fluid. */
  int highestBlockY(int x, int z);

  /** True when the chunk at chunk coordinates is loaded. */
  boolean isChunkLoaded(int chunkX, int chunkZ);

  /** Loads (or generates) the chunk without blocking the caller; completes once the chunk is available. */
  CompletableFuture<Void> loadChunkAsync(int chunkX, int chunkZ);

  /** True when the position lies inside the world border. */
  boolean isInsideBorder(Vec3 position);

  /** Data attached to a loaded chunk; null when the chunk is not loaded. */
  DataHolder chunkData(int chunkX, int chunkZ);

  /** Players in this world. */
  List<PlayerRef> players();

  /** Entities whose boxes touch {@code box} and pass {@code filter}; a null filter accepts every entity. */
  List<EntityRef> entitiesIn(Box box, Predicate<EntityRef> filter);

  /** Entities whose boxes touch the box of the given half-extents around {@code center}. */
  default List<EntityRef> nearbyEntities(Vec3 center, double radiusX, double radiusY, double radiusZ) {
    return entitiesIn(Box.around(center, radiusX, radiusY, radiusZ), entity -> true);
  }

  /** First entity hit along the ray, or null. */
  EntityRef rayTraceEntity(Vec3 start, Vec3 direction, double maxDistance, double raySize, Predicate<EntityRef> filter);

  /** First non-passable block hit along the ray, or null. */
  BlockRef rayTraceBlock(Vec3 start, Vec3 direction, double maxDistance, boolean includeFluids);

  /** Like {@link #rayTraceBlock} with the hit position and struck face, or null when nothing is hit. */
  BlockHit rayTraceBlockHit(Vec3 start, Vec3 direction, double maxDistance, boolean includeFluids);

  /** Like {@link #rayTraceEntity} with the hit position on the entity's box ({@code block} is null), or null. */
  RayHit rayTraceEntityHit(Vec3 start, Vec3 direction, double maxDistance, double raySize, Predicate<EntityRef> filter);

  /**
   * Closest hit along the ray of either a collidable block (passable blocks ignored) or an entity matching the filter,
   * like Bukkit World#rayTrace with ignorePassableBlocks; entities behind the first block are not hit. Null when
   * nothing is hit.
   */
  RayHit rayTrace(Vec3 start, Vec3 direction, double maxDistance, boolean includeFluids, double raySize,
                  Predicate<EntityRef> filter);

  /** Drops a copy of the item at the position with the default pickup delay. Null when the item is empty. */
  ItemEntityRef dropItem(Vec3 position, ItemRef item);

  /**
   * Drops the item at the exact position; {@code prepare} runs before the entity enters the world. Null when the item
   * is empty.
   */
  ItemEntityRef dropItem(Vec3 position, ItemRef item, Consumer<ItemEntityRef> prepare);

  /**
   * Item entity that is not in the world yet. It spawns when added to a {@code BlockDropItemEvent} item list
   * that is not cancelled.
   */
  ItemEntityRef createItemEntity(Vec3 position, ItemRef item);

  /** Drops with vanilla random scatter, like a broken block. Null when the item is empty. */
  ItemEntityRef dropItemNaturally(Vec3 position, ItemRef item);

  /** Spawns experience orbs worth {@code amount} points in total; nothing for 0 or less. */
  void spawnExperience(Vec3 position, int amount);

  /**
   * Spawns an entity of the given type. {@code prepare} runs before the entity is added to the
   * world, so it can set data and visibility without a visible first frame. Null when the type is unknown or
   * cannot be spawned.
   */
  EntityRef spawn(Id entityType, Loc location, Consumer<EntityRef> prepare);

  /** Spawns an item display showing {@code item}; {@code prepare} runs before it enters the world. */
  ItemDisplayRef spawnItemDisplay(Loc location, ItemRef item, Consumer<ItemDisplayRef> prepare);

  /** Spawns a block display showing {@code block}; {@code prepare} runs before it enters the world. */
  BlockDisplayRef spawnBlockDisplay(Loc location, BlockStateRef block, Consumer<BlockDisplayRef> prepare);

  /** Spawns a text display showing the markup; {@code prepare} runs before it enters the world. */
  TextDisplayRef spawnTextDisplay(Loc location, String markup, Consumer<TextDisplayRef> prepare);

  /**
   * Spawns an interaction entity (an invisible click and hit box) {@code width} wide and {@code height} tall,
   * standing on {@code location}. {@code prepare} runs before it enters the world.
   */
  EntityRef spawnInteraction(Loc location, float width, float height, boolean visibleByDefault, Consumer<EntityRef> prepare);

  /** Spawns an armor stand with {@code style}; {@code prepare} runs before it enters the world. Null when it cannot spawn. */
  EntityRef spawnArmorStand(Loc location, ArmorStandStyle style, Consumer<EntityRef> prepare);

  /** Chunks of this world that are loaded right now. */
  List<ChunkPos> loadedChunks();

  /** Blocks holding a block entity (chests, furnaces, signs...) in a loaded chunk; empty when it is not loaded. */
  List<BlockRef> blockEntities(int chunkX, int chunkZ);

  /** Entities stored in a loaded chunk; empty when it is not loaded. */
  List<EntityRef> chunkEntities(int chunkX, int chunkZ);

  /**
   * Structures whose pieces reach into the chunk (starts referenced by it), loading the chunk when needed.
   * Owning thread of the chunk.
   */
  List<GeneratedStructure> structuresAt(int chunkX, int chunkZ);

  /**
   * Structure starts and references stored in a chunk that was already generated, without generating anything.
   * Completes with null when the chunk was never generated; completes on the chunk's owning thread. Completes
   * exceptionally with {@link UnsupportedOperationException} when the platform cannot read structure data.
   */
  CompletableFuture<ChunkStructures> generatedStructures(int chunkX, int chunkZ);

  /**
   * Nearest structure of the structure type {@code structureType} (for example {@code minecraft:jigsaw}) within
   * {@code radiusChunks} of {@code origin}, like a cartography map search. Null when none is found.
   */
  Loc locateNearestStructure(Loc origin, Id structureType, int radiusChunks, boolean findUnexplored);

  /** Shows a lightning strike that deals no damage and starts no fire. */
  void strikeLightningEffect(Vec3 position);

  /** Explosion that damages entities; {@code breakBlocks} false keeps terrain intact. */
  boolean createExplosion(Vec3 position, float power, boolean setFire, boolean breakBlocks, EntityRef source);

  /**
   * Blocks the vanilla tree feature (for example {@code minecraft:fancy_oak}) would place at {@code origin} with
   * {@code seed}, in placement order, without placing anything. Empty when the feature is unknown or cannot grow.
   */
  List<BlockChange> planTree(Id feature, BlockPos origin, long seed);

  /** Places the vanilla tree feature at {@code origin} with {@code seed}; true when a tree was placed. */
  boolean growTree(Id feature, BlockPos origin, long seed);

  /** The world spawn point. */
  Loc spawnLocation();

  /** Keeps the chunk loaded for Adapt until {@link #removeChunkTicket}; true when the ticket was added or already held. */
  boolean addChunkTicket(int chunkX, int chunkZ);

  /** Releases Adapt's chunk ticket. */
  void removeChunkTicket(int chunkX, int chunkZ);

  /** Asks the platform to unload the chunk when nothing else keeps it loaded. */
  void requestChunkUnload(int chunkX, int chunkZ);

  /** Adapter-internal access to the native world. Content code must not call this. */
  @ApiStatus.Internal
  Object unwrap();
}
