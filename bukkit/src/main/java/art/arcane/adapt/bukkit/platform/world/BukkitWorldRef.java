package art.arcane.adapt.bukkit.platform.world;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.bukkit.platform.BukkitConversions;
import art.arcane.adapt.bukkit.platform.BukkitDisplayFactory;
import art.arcane.adapt.bukkit.platform.BukkitHosts;
import art.arcane.adapt.bukkit.platform.BukkitNatives;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.bukkit.platform.BukkitResolve;
import art.arcane.adapt.bukkit.platform.data.PdcDataHolder;
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
import art.arcane.adapt.platform.world.BlockHit;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.ChunkStructures;
import art.arcane.adapt.platform.world.Environment;
import art.arcane.adapt.platform.world.GeneratedStructure;
import art.arcane.adapt.platform.world.RayHit;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.compat.PaperApi;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.volmlib.nativelib.chunk.ChunkPosition;
import art.arcane.volmlib.nativelib.chunk.GeneratedChunkStructures;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.generator.structure.StructureType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.StructureSearchResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** World handle. Equality is by Bukkit world UID; the handle goes stale when Bukkit unloads the world. */
public final class BukkitWorldRef implements WorldRef {
  private static final double RAIN_TEMPERATURE = 0.15D;
  private static volatile Optional<GeneratedChunkStructures> structureMetadata;
  private final World world;
  private final UUID uid;
  private final String name;
  private final Id key;

  public BukkitWorldRef(World world) {
    this.world = Objects.requireNonNull(world, "world");
    this.uid = world.getUID();
    this.name = world.getName();
    this.key = BukkitConversions.id(world.getKey());
  }

  public World world() {
    return world;
  }

  @Override
  public UUID uid() {
    return uid;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public Id key() {
    return key;
  }

  @Override
  public boolean isLoaded() {
    return Bukkit.getWorld(uid) == world;
  }

  @Override
  public long seed() {
    return world.getSeed();
  }

  @Override
  public Environment environment() {
    return switch (world.getEnvironment()) {
      case NORMAL -> Environment.NORMAL;
      case NETHER -> Environment.NETHER;
      case THE_END -> Environment.THE_END;
      case CUSTOM -> Environment.CUSTOM;
    };
  }

  @Override
  public int minHeight() {
    return world.getMinHeight();
  }

  @Override
  public int maxHeight() {
    return world.getMaxHeight();
  }

  @Override
  public int seaLevel() {
    return world.getSeaLevel();
  }

  @Override
  public long time() {
    return world.getTime();
  }

  @Override
  public long fullTime() {
    return world.getFullTime();
  }

  @Override
  public boolean isDay() {
    return PaperApi.isDayTime(world);
  }

  @Override
  public boolean hasStorm() {
    return world.hasStorm();
  }

  @Override
  public boolean isThundering() {
    return world.isThundering();
  }

  /**
   * Vanilla's rain test from what Bukkit exposes on every version: an open column above the block, a biome with
   * precipitation (every dry biome has no downfall) and a temperature warm enough for rain instead of snow.
   */
  @Override
  public boolean isRainingAt(int x, int y, int z) {
    if (!world.hasStorm() || world.getEnvironment() != World.Environment.NORMAL || y <= world.getHighestBlockYAt(x, z)) {
      return false;
    }
    Block block = world.getBlockAt(x, y, z);
    return block.getHumidity() > 0D && block.getTemperature() >= RAIN_TEMPERATURE;
  }

  @Override
  public BlockRef blockAt(int x, int y, int z) {
    return new BukkitBlockRef(world.getBlockAt(x, y, z));
  }

  @Override
  public int highestBlockY(int x, int z) {
    return world.getHighestBlockYAt(x, z);
  }

  @Override
  public boolean isChunkLoaded(int chunkX, int chunkZ) {
    return world.isChunkLoaded(chunkX, chunkZ);
  }

  @Override
  public CompletableFuture<Void> loadChunkAsync(int chunkX, int chunkZ) {
    return Adapt.platform.getChunkAtAsync(new Location(world, chunkX << 4, 0, chunkZ << 4))
        .thenApply(chunk -> null);
  }

  @Override
  public boolean isInsideBorder(Vec3 position) {
    return world.getWorldBorder().isInside(new Location(world, position.x(), position.y(), position.z()));
  }

  @Override
  public DataHolder chunkData(int chunkX, int chunkZ) {
    if (!world.isChunkLoaded(chunkX, chunkZ)) {
      return null;
    }
    return PdcDataHolder.live(world.getChunkAt(chunkX, chunkZ).getPersistentDataContainer());
  }

  @Override
  public List<PlayerRef> players() {
    List<Player> players = world.getPlayers();
    List<PlayerRef> wrapped = new ArrayList<>(players.size());
    for (Player player : players) {
      wrapped.add(BukkitRefs.player(player));
    }
    return wrapped;
  }

  @Override
  public List<EntityRef> entities() {
    List<Entity> found = world.getEntities();
    List<EntityRef> wrapped = new ArrayList<>(found.size());
    for (Entity entity : found) {
      wrapped.add(BukkitRefs.entity(entity));
    }
    return wrapped;
  }

  @Override
  public List<EntityRef> entitiesIn(Box box, Predicate<EntityRef> filter) {
    Collection<Entity> found = world.getNearbyEntities(BukkitConversions.boundingBox(box));
    List<EntityRef> wrapped = new ArrayList<>(found.size());
    for (Entity entity : found) {
      EntityRef ref = BukkitRefs.entity(entity);
      if (filter == null || filter.test(ref)) {
        wrapped.add(ref);
      }
    }
    return wrapped;
  }

  @Override
  public EntityRef rayTraceEntity(Vec3 start, Vec3 direction, double maxDistance, double raySize, Predicate<EntityRef> filter) {
    Vec3 unit = direction.normalize();
    if (unit.lengthSquared() == 0.0D || maxDistance <= 0.0D) {
      return null;
    }
    Location origin = new Location(world, start.x(), start.y(), start.z());
    RayTraceResult result = world.rayTraceEntities(origin, BukkitConversions.vector(unit), maxDistance, raySize,
        (Entity entity) -> filter == null || filter.test(BukkitRefs.entity(entity)));
    return result == null || result.getHitEntity() == null ? null : BukkitRefs.entity(result.getHitEntity());
  }

  @Override
  public BlockRef rayTraceBlock(Vec3 start, Vec3 direction, double maxDistance, boolean includeFluids) {
    Vec3 unit = direction.normalize();
    if (unit.lengthSquared() == 0.0D || maxDistance <= 0.0D) {
      return null;
    }
    Location origin = new Location(world, start.x(), start.y(), start.z());
    RayTraceResult result = world.rayTraceBlocks(origin, BukkitConversions.vector(unit), maxDistance,
        includeFluids ? FluidCollisionMode.ALWAYS : FluidCollisionMode.NEVER, true);
    Block hit = result == null ? null : result.getHitBlock();
    return hit == null ? null : new BukkitBlockRef(hit);
  }

  @Override
  public BlockHit rayTraceBlockHit(Vec3 start, Vec3 direction, double maxDistance, boolean includeFluids) {
    Vec3 unit = direction.normalize();
    if (unit.lengthSquared() == 0.0D || maxDistance <= 0.0D) {
      return null;
    }
    Location origin = new Location(world, start.x(), start.y(), start.z());
    RayTraceResult result = world.rayTraceBlocks(origin, BukkitConversions.vector(unit), maxDistance,
        includeFluids ? FluidCollisionMode.ALWAYS : FluidCollisionMode.NEVER, true);
    Block hit = result == null ? null : result.getHitBlock();
    if (hit == null) {
      return null;
    }
    org.bukkit.block.BlockFace face = result.getHitBlockFace();
    return new BlockHit(BukkitConversions.vec(result.getHitPosition()), new BukkitBlockRef(hit),
        face == null ? null : BukkitConversions.face(face));
  }

  @Override
  public RayHit rayTraceEntityHit(Vec3 start, Vec3 direction, double maxDistance, double raySize,
                                  Predicate<EntityRef> filter) {
    Vec3 unit = direction.normalize();
    if (unit.lengthSquared() == 0.0D || maxDistance <= 0.0D) {
      return null;
    }
    Location origin = new Location(world, start.x(), start.y(), start.z());
    RayTraceResult result = world.rayTraceEntities(origin, BukkitConversions.vector(unit), maxDistance, raySize,
        (Entity entity) -> filter == null || filter.test(BukkitRefs.entity(entity)));
    Entity entity = result == null ? null : result.getHitEntity();
    return entity == null ? null : new RayHit(BukkitConversions.vec(result.getHitPosition()), null, BukkitRefs.entity(entity));
  }

  @Override
  public RayHit rayTrace(Vec3 start, Vec3 direction, double maxDistance, boolean includeFluids, double raySize,
                         Predicate<EntityRef> filter) {
    Vec3 unit = direction.normalize();
    if (unit.lengthSquared() == 0.0D || maxDistance <= 0.0D) {
      return null;
    }
    Location origin = new Location(world, start.x(), start.y(), start.z());
    RayTraceResult result = world.rayTrace(origin, BukkitConversions.vector(unit), maxDistance,
        includeFluids ? FluidCollisionMode.ALWAYS : FluidCollisionMode.NEVER, true, raySize,
        (Entity entity) -> filter == null || filter.test(BukkitRefs.entity(entity)));
    if (result == null) {
      return null;
    }
    Entity entity = result.getHitEntity();
    Block block = entity == null ? result.getHitBlock() : null;
    if (entity == null && block == null) {
      return null;
    }
    return new RayHit(BukkitConversions.vec(result.getHitPosition()), block == null ? null : new BukkitBlockRef(block),
        entity == null ? null : BukkitRefs.entity(entity));
  }

  @Override
  public ItemEntityRef dropItem(Vec3 position, ItemRef item) {
    ItemStack stack = BukkitRefs.item(item);
    if (BukkitResolve.isAir(stack.getType())) {
      return null;
    }
    Item dropped = world.dropItem(new Location(world, position.x(), position.y(), position.z()), stack);
    return BukkitRefs.itemEntity(dropped);
  }

  @Override
  public ItemEntityRef dropItem(Vec3 position, ItemRef item, Consumer<ItemEntityRef> prepare) {
    ItemStack stack = BukkitRefs.item(item);
    if (BukkitResolve.isAir(stack.getType())) {
      return null;
    }
    Item dropped = world.dropItem(new Location(world, position.x(), position.y(), position.z()), stack, (Item spawned) -> {
      if (prepare != null) {
        prepare.accept(BukkitRefs.itemEntity(spawned));
      }
    });
    return BukkitRefs.itemEntity(dropped);
  }

  @Override
  public ItemEntityRef createItemEntity(Vec3 position, ItemRef item) {
    Item created = BukkitWorldVersion.createItem(new Location(world, position.x(), position.y(), position.z()), BukkitRefs.item(item));
    return BukkitRefs.itemEntity(created);
  }

  @Override
  public ItemEntityRef dropItemNaturally(Vec3 position, ItemRef item) {
    ItemStack stack = BukkitRefs.item(item);
    if (BukkitResolve.isAir(stack.getType())) {
      return null;
    }
    Item dropped = world.dropItemNaturally(new Location(world, position.x(), position.y(), position.z()), stack);
    return BukkitRefs.itemEntity(dropped);
  }

  @Override
  public void spawnExperience(Vec3 position, int amount) {
    if (amount <= 0) {
      return;
    }
    world.spawn(new Location(world, position.x(), position.y(), position.z()), ExperienceOrb.class,
        (ExperienceOrb orb) -> orb.setExperience(amount));
  }

  @Override
  public EntityRef spawn(Id entityType, Loc location, Consumer<EntityRef> prepare) {
    EntityType type = BukkitResolve.entityType(entityType);
    Location target = BukkitRefs.location(location);
    if (type == null || target == null || !type.isSpawnable()) {
      return null;
    }
    Entity spawned = PaperApi.spawnEntity(world, target, type, (Entity entity) -> {
      if (prepare != null) {
        prepare.accept(BukkitRefs.entity(entity));
      }
    });
    return spawned == null ? null : BukkitRefs.entity(spawned);
  }

  @Override
  public ItemDisplayRef spawnItemDisplay(Loc location, ItemRef item, Consumer<ItemDisplayRef> prepare) {
    return displays().spawnItem(location, item, prepare);
  }

  @Override
  public BlockDisplayRef spawnBlockDisplay(Loc location, BlockStateRef block, Consumer<BlockDisplayRef> prepare) {
    return displays().spawnBlock(location, block, prepare);
  }

  @Override
  public TextDisplayRef spawnTextDisplay(Loc location, String markup, Consumer<TextDisplayRef> prepare) {
    return displays().spawnText(location, markup, prepare);
  }

  @Override
  public EntityRef spawnInteraction(Loc location, float width, float height, boolean visibleByDefault, Consumer<EntityRef> prepare) {
    Location target = BukkitRefs.location(location);
    if (target == null) {
      return null;
    }
    Interaction spawned = PaperCompat.spawnHydrated(world, target, Interaction.class, (Interaction interaction) -> {
      interaction.setVisibleByDefault(visibleByDefault);
      interaction.setInteractionWidth(width);
      interaction.setInteractionHeight(height);
      if (prepare != null) {
        prepare.accept(BukkitRefs.entity(interaction));
      }
    });
    return spawned == null ? null : BukkitRefs.entity(spawned);
  }

  @Override
  public EntityRef spawnArmorStand(Loc location, ArmorStandStyle style, Consumer<EntityRef> prepare) {
    Location target = BukkitRefs.location(location);
    if (target == null) {
      return null;
    }
    ArmorStand spawned = world.spawn(target, ArmorStand.class, (ArmorStand stand) -> {
      stand.setSmall(style.small());
      stand.setBasePlate(style.basePlate());
      stand.setMarker(style.marker());
      stand.setCollidable(style.collidable());
      stand.setArms(style.arms());
      if (prepare != null) {
        prepare.accept(BukkitRefs.entity(stand));
      }
    });
    return spawned == null ? null : BukkitRefs.entity(spawned);
  }

  @Override
  public List<ChunkPos> loadedChunks() {
    Chunk[] chunks = world.getLoadedChunks();
    List<ChunkPos> loaded = new ArrayList<>(chunks.length);
    for (Chunk chunk : chunks) {
      loaded.add(new ChunkPos(chunk.getX(), chunk.getZ()));
    }
    return loaded;
  }

  @Override
  public List<BlockRef> blockEntities(int chunkX, int chunkZ) {
    if (!world.isChunkLoaded(chunkX, chunkZ)) {
      return List.of();
    }
    Collection<BlockState> states = tileEntities(world.getChunkAt(chunkX, chunkZ));
    List<BlockRef> blocks = new ArrayList<>(states.size());
    for (BlockState state : states) {
      blocks.add(new BukkitBlockRef(state.getBlock()));
    }
    return blocks;
  }

  @Override
  public List<EntityRef> chunkEntities(int chunkX, int chunkZ) {
    if (!world.isChunkLoaded(chunkX, chunkZ)) {
      return List.of();
    }
    Entity[] found = world.getChunkAt(chunkX, chunkZ).getEntities();
    List<EntityRef> wrapped = new ArrayList<>(found.length);
    for (Entity entity : found) {
      wrapped.add(BukkitRefs.entity(entity));
    }
    return wrapped;
  }

  @Override
  public List<GeneratedStructure> structuresAt(int chunkX, int chunkZ) {
    return BukkitWorldVersion.structuresAt(world.getChunkAt(chunkX, chunkZ));
  }

  @Override
  public CompletableFuture<ChunkStructures> generatedStructures(int chunkX, int chunkZ) {
    Optional<GeneratedChunkStructures> available;
    try {
      available = structureMetadata();
    } catch (RuntimeException | LinkageError failure) {
      return CompletableFuture.failedFuture(new UnsupportedOperationException(
          "generated structure metadata is unsupported on this server", failure));
    }
    if (available.isEmpty()) {
      return CompletableFuture.failedFuture(new UnsupportedOperationException(
          "generated structure metadata is unsupported on this server"));
    }
    GeneratedChunkStructures metadata = available.get();
    return Adapt.platform.getChunkAtAsync(world, chunkX, chunkZ, false, false)
        .thenApply(chunk -> chunk == null ? null : structures(metadata.read(chunk)));
  }

  @Override
  public Loc locateNearestStructure(Loc origin, Id structureType, int radiusChunks, boolean findUnexplored) {
    StructureType type = BukkitWorldVersion.structureType(structureType);
    if (type == null) {
      return null;
    }
    StructureSearchResult result = world.locateNearestStructure(BukkitRefs.location(origin), type, radiusChunks, findUnexplored);
    if (result == null || result.getLocation() == null || result.getLocation().getWorld() != world) {
      return null;
    }
    return BukkitRefs.loc(result.getLocation());
  }

  private static Optional<GeneratedChunkStructures> structureMetadata() {
    Optional<GeneratedChunkStructures> metadata = structureMetadata;
    if (metadata == null) {
      metadata = BukkitNatives.find(GeneratedChunkStructures.class);
      structureMetadata = metadata;
    }
    return metadata;
  }

  private static ChunkStructures structures(GeneratedChunkStructures.Snapshot snapshot) {
    List<GeneratedStructure> starts = new ArrayList<>(snapshot.starts().size());
    for (GeneratedChunkStructures.Entry entry : snapshot.starts()) {
      GeneratedChunkStructures.Bounds box = entry.bounds();
      starts.add(new GeneratedStructure(BukkitConversions.id(entry.structureKey()), BukkitConversions.id(entry.typeKey()),
          new Box(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())));
    }
    List<ChunkPos> references = new ArrayList<>(snapshot.references().size());
    for (ChunkPosition position : snapshot.references()) {
      references.add(new ChunkPos(position.x(), position.z()));
    }
    return new ChunkStructures(starts, references);
  }

  private static Collection<BlockState> tileEntities(Chunk chunk) {
    return PaperApi.tileEntities(chunk);
  }

  @Override
  public void strikeLightningEffect(Vec3 position) {
    world.strikeLightningEffect(new Location(world, position.x(), position.y(), position.z()));
  }

  @Override
  public boolean createExplosion(Vec3 position, float power, boolean setFire, boolean breakBlocks, EntityRef source) {
    Entity entity = source == null ? null : BukkitRefs.entity(source);
    return world.createExplosion(new Location(world, position.x(), position.y(), position.z()), power, setFire, breakBlocks, entity);
  }

  @Override
  public List<BlockChange> planTree(Id feature, BlockPos origin, long seed) {
    TreeType type = BukkitTrees.treeType(feature);
    if (type == null) {
      return List.of();
    }
    List<BlockChange> planned = new ArrayList<>();
    world.generateTree(new Location(world, origin.x(), origin.y(), origin.z()), new Random(seed), type, (BlockState state) -> {
      planned.add(new BlockChange(BukkitRefs.block(state.getBlock()), BukkitRefs.blockState(state.getBlockData())));
      return false;
    });
    return planned;
  }

  @Override
  public boolean growTree(Id feature, BlockPos origin, long seed) {
    TreeType type = BukkitTrees.treeType(feature);
    return type != null && world.generateTree(new Location(world, origin.x(), origin.y(), origin.z()), new Random(seed), type);
  }

  @Override
  public Loc spawnLocation() {
    return BukkitRefs.loc(world.getSpawnLocation());
  }

  @Override
  public boolean addChunkTicket(int chunkX, int chunkZ) {
    world.addPluginChunkTicket(chunkX, chunkZ, Adapt.instance);
    return true;
  }

  @Override
  public void removeChunkTicket(int chunkX, int chunkZ) {
    world.removePluginChunkTicket(chunkX, chunkZ, Adapt.instance);
  }

  @Override
  public void requestChunkUnload(int chunkX, int chunkZ) {
    world.unloadChunkRequest(chunkX, chunkZ);
  }

  @Override
  public Object unwrap() {
    return world;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof WorldRef that && uid.equals(that.uid());
  }

  @Override
  public int hashCode() {
    return uid.hashCode();
  }

  @Override
  public String toString() {
    return "BukkitWorldRef[" + name + "]";
  }

  private static BukkitDisplayFactory displays() {
    BukkitDisplayFactory factory = BukkitHosts.displays();
    if (factory == null) {
      throw new IllegalStateException("No display factory registered in BukkitHosts");
    }
    return factory;
  }
}
