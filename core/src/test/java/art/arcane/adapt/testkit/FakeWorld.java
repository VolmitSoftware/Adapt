package art.arcane.adapt.testkit;

import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.entity.ArmorStandStyle;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.display.BlockDisplayRef;
import art.arcane.adapt.platform.entity.display.ItemDisplayRef;
import art.arcane.adapt.platform.entity.display.TextDisplayRef;
import art.arcane.adapt.platform.event.block.BlockChange;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.ChunkPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockHit;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.RayHit;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.ChunkStructures;
import art.arcane.adapt.platform.world.Environment;
import art.arcane.adapt.platform.world.GeneratedStructure;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Sparse block world: unset positions are air, every chunk is loaded. Records experience, drops and lightning.
 * Display entities are not modelled ({@code spawn*Display} returns null).
 */
public final class FakeWorld implements WorldRef {
  private static final BlockStateRef AIR = FakeBlockState.of("air");
  private static final Set<Id> THROWN = Set.of(Id.minecraft("ender_pearl"), Id.minecraft("snowball"), Id.minecraft("egg"));

  private final UUID uid = UUID.randomUUID();
  private final String name;
  private final Map<BlockPos, BlockStateRef> blocks = new HashMap<>();
  private final Map<BlockPos, MemoryData> blockData = new HashMap<>();
  private final Map<BlockPos, MemoryData> blockEntityData = new HashMap<>();
  private final Map<BlockPos, InventoryRef> blockInventories = new HashMap<>();
  private final Map<BlockPos, String> blockEntityNames = new HashMap<>();
  private final Map<BlockPos, FakeBrewingStand> brewingStands = new HashMap<>();
  private final Map<BlockPos, FakeFurnace> furnaces = new HashMap<>();
  private final Map<BlockPos, FakeCampfire> campfires = new HashMap<>();
  private final Map<ChunkPos, MemoryData> chunkData = new HashMap<>();
  private final List<EntityRef> entities = new ArrayList<>();
  private final List<Integer> experience = new ArrayList<>();
  private final Map<ChunkPos, List<GeneratedStructure>> structures = new HashMap<>();
  private final Map<ChunkPos, CompletableFuture<ChunkStructures>> generated = new HashMap<>();
  private final List<ChunkPos> generatedRequests = new ArrayList<>();
  private final Map<Id, Loc> nearestStructures = new HashMap<>();
  private Environment environment = Environment.NORMAL;
  private long seed;
  private long time;
  private Loc spawnLocation;
  private final Set<ChunkPos> chunkTickets = new HashSet<>();

  public FakeWorld(String name) {
    this.name = name;
  }

  public FakeWorld environment(Environment environment, long seed) {
    this.environment = environment;
    this.seed = seed;
    return this;
  }

  /** Structures {@link #structuresAt} reports for the chunk. */
  public FakeWorld structuresAt(ChunkPos chunk, List<GeneratedStructure> found) {
    structures.put(chunk, List.copyOf(found));
    return this;
  }

  /** Answer for {@link #generatedStructures}; chunks without one complete with null (never generated). */
  public FakeWorld generated(ChunkPos chunk, CompletableFuture<ChunkStructures> answer) {
    generated.put(chunk, answer);
    return this;
  }

  /** Chunks {@link #generatedStructures} was asked for, in request order. */
  public List<ChunkPos> generatedRequests() {
    return generatedRequests;
  }

  public FakeWorld nearestStructure(Id structureType, Loc found) {
    nearestStructures.put(structureType, found);
    return this;
  }

  public FakeWorld set(int x, int y, int z, String vanillaPath) {
    set(new BlockPos(x, y, z), FakeBlockState.of(vanillaPath));
    return this;
  }

  public void set(BlockPos pos, BlockStateRef state) {
    if (state == null || state.isAir()) {
      blocks.remove(pos);
    } else {
      blocks.put(pos, state);
    }
  }

  BlockStateRef stateAt(BlockPos pos) {
    return blocks.getOrDefault(pos, AIR);
  }

  /** Gives the block at {@code pos} a block entity, so {@code BlockRef#blockEntityData} answers for it. */
  public FakeWorld blockEntity(BlockPos pos) {
    blockEntityData.computeIfAbsent(pos, (BlockPos ignored) -> new MemoryData());
    return this;
  }

  /** Gives the block at {@code pos} its own inventory, as {@link FakeBlock#blockInventory()} returns it. */
  public FakeWorld blockInventory(BlockPos pos, InventoryRef inventory) {
    blockInventories.put(pos, inventory);
    return this;
  }

  InventoryRef blockInventory(BlockPos pos) {
    return blockInventories.get(pos);
  }

  /** Names the block entity at {@code pos}, as a renamed shulker box or chest keeps its custom name. */
  public FakeWorld blockEntityName(BlockPos pos, String name) {
    blockEntity(pos);
    blockEntityNames.put(pos, name);
    return this;
  }

  String blockEntityName(BlockPos pos) {
    return blockEntityNames.get(pos);
  }

  DataHolder blockEntityData(BlockPos pos) {
    return blockEntityData.get(pos);
  }

  DataHolder blockData(BlockPos pos) {
    return blockData.computeIfAbsent(pos, (BlockPos ignored) -> new MemoryData());
  }

  /** Brewing stand contents at {@code pos}; kept until the world is dropped, whatever block is placed there. */
  public FakeFurnace furnace(BlockPos pos) {
    return furnaces.computeIfAbsent(pos, (BlockPos at) -> new FakeFurnace(blockAt(at)));
  }

  public FakeCampfire campfire(BlockPos pos) {
    return campfires.computeIfAbsent(pos, (BlockPos at) -> new FakeCampfire(blockAt(at)));
  }

  public FakeBrewingStand brewingStand(BlockPos pos) {
    return brewingStands.computeIfAbsent(pos, (BlockPos at) -> new FakeBrewingStand(blockAt(at)));
  }

  public <E extends EntityRef> E add(E entity) {
    entities.add(entity);
    return entity;
  }

  void forget(EntityRef entity) {
    entities.remove(entity);
  }

  /** Experience orb amounts spawned so far. */
  public List<Integer> experience() {
    return List.copyOf(experience);
  }

  /** Item entities currently in the world. */
  public List<FakeItemEntity> items() {
    List<FakeItemEntity> items = new ArrayList<>();
    for (EntityRef entity : entities) {
      if (entity instanceof FakeItemEntity item) {
        items.add(item);
      }
    }
    return items;
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
    return Id.minecraft(name);
  }

  @Override
  public boolean isLoaded() {
    return true;
  }

  @Override
  public long seed() {
    return seed;
  }

  @Override
  public Environment environment() {
    return environment;
  }

  @Override
  public int minHeight() {
    return -64;
  }

  @Override
  public int maxHeight() {
    return 320;
  }

  @Override
  public int seaLevel() {
    return 63;
  }

  public FakeWorld fullTime(long value) {
    time = value;
    return this;
  }

  @Override
  public long time() {
    return time % 24000L;
  }

  @Override
  public long fullTime() {
    return time;
  }

  @Override
  public boolean isDay() {
    return time() < 12300L;
  }

  @Override
  public boolean hasStorm() {
    return false;
  }

  @Override
  public BlockRef blockAt(int x, int y, int z) {
    return new FakeBlock(this, new BlockPos(x, y, z));
  }

  @Override
  public int highestBlockY(int x, int z) {
    int highest = minHeight();
    for (BlockPos pos : blocks.keySet()) {
      if (pos.x() == x && pos.z() == z) {
        highest = Math.max(highest, pos.y());
      }
    }
    return highest;
  }

  @Override
  public CompletableFuture<Void> loadChunkAsync(int chunkX, int chunkZ) {
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public boolean isInsideBorder(Vec3 position) {
    return true;
  }

  @Override
  public boolean isChunkLoaded(int chunkX, int chunkZ) {
    return true;
  }

  @Override
  public DataHolder chunkData(int chunkX, int chunkZ) {
    return chunkData.computeIfAbsent(new ChunkPos(chunkX, chunkZ), (ChunkPos ignored) -> new MemoryData());
  }

  @Override
  public List<PlayerRef> players() {
    List<PlayerRef> players = new ArrayList<>();
    for (EntityRef entity : entities) {
      if (entity instanceof PlayerRef player) {
        players.add(player);
      }
    }
    return players;
  }

  @Override
  public List<EntityRef> entities() {
    return List.copyOf(entities);
  }

  @Override
  public List<EntityRef> entitiesIn(Box box, Predicate<EntityRef> filter) {
    List<EntityRef> found = new ArrayList<>();
    for (EntityRef entity : entities) {
      if (box.contains(entity.location().position()) && filter.test(entity)) {
        found.add(entity);
      }
    }
    return found;
  }

  @Override
  public EntityRef rayTraceEntity(Vec3 start, Vec3 direction, double maxDistance, double raySize,
                                  Predicate<EntityRef> filter) {
    return null;
  }

  @Override
  public RayHit rayTrace(Vec3 start, Vec3 direction, double maxDistance, boolean includeFluids, double raySize,
                         Predicate<EntityRef> filter) {
    Vec3 step = direction.normalize().multiply(0.05D);
    Vec3 at = start;
    for (double travelled = 0D; travelled <= maxDistance; travelled += 0.05D) {
      BlockRef block = blockAt(at.toBlockPos());
      if (block.isSolid()) {
        return new RayHit(at, block, null);
      }
      for (EntityRef entity : entities()) {
        if ((filter == null || filter.test(entity)) && entity.boundingBox().contains(at)) {
          return new RayHit(at, null, entity);
        }
      }
      at = at.add(step);
    }
    return null;
  }

  @Override
  public BlockHit rayTraceBlockHit(Vec3 start, Vec3 direction, double maxDistance, boolean includeFluids) {
    Vec3 step = direction.normalize().multiply(0.05D);
    Vec3 at = start;
    for (double travelled = 0D; travelled <= maxDistance; travelled += 0.05D) {
      BlockRef block = blockAt(at.toBlockPos());
      if (block.isSolid()) {
        return new BlockHit(at, block, null);
      }
      at = at.add(step);
    }
    return null;
  }

  @Override
  public RayHit rayTraceEntityHit(Vec3 start, Vec3 direction, double maxDistance, double raySize,
                                  Predicate<EntityRef> filter) {
    Vec3 step = direction.normalize().multiply(0.05D);
    Vec3 at = start;
    for (double travelled = 0D; travelled <= maxDistance; travelled += 0.05D) {
      for (EntityRef entity : entities()) {
        if ((filter == null || filter.test(entity)) && entity.boundingBox().expand(raySize).contains(at)) {
          return new RayHit(at, null, entity);
        }
      }
      at = at.add(step);
    }
    return null;
  }

  @Override
  public BlockRef rayTraceBlock(Vec3 start, Vec3 direction, double maxDistance, boolean includeFluids) {
    Vec3 step = direction.normalize().multiply(0.05D);
    Vec3 at = start;
    for (double travelled = 0D; travelled <= maxDistance; travelled += 0.05D) {
      BlockRef block = blockAt(at.toBlockPos());
      if (block.isSolid()) {
        return block;
      }
      at = at.add(step);
    }
    return null;
  }

  @Override
  public ItemEntityRef dropItem(Vec3 position, ItemRef item) {
    return add(new FakeItemEntity(Loc.of(this, position), item.copy()));
  }

  @Override
  public ItemEntityRef dropItem(Vec3 position, ItemRef item, Consumer<ItemEntityRef> prepare) {
    FakeItemEntity entity = new FakeItemEntity(Loc.of(this, position), item.copy());
    if (prepare != null) {
      prepare.accept(entity);
    }
    return add(entity);
  }

  @Override
  public ItemEntityRef createItemEntity(Vec3 position, ItemRef item) {
    return new FakeItemEntity(Loc.of(this, position), item.copy());
  }

  @Override
  public ItemEntityRef dropItemNaturally(Vec3 position, ItemRef item) {
    return dropItem(position, item);
  }

  @Override
  public void spawnExperience(Vec3 position, int amount) {
    if (amount > 0) {
      experience.add(amount);
    }
  }

  @Override
  public EntityRef spawn(Id entityType, Loc location, Consumer<EntityRef> prepare) {
    FakeEntity entity = THROWN.contains(entityType) ? new FakeProjectile(entityType, location) : new FakeEntity(entityType, location);
    prepare.accept(entity);
    return add(entity);
  }

  @Override
  public ItemDisplayRef spawnItemDisplay(Loc location, ItemRef item, Consumer<ItemDisplayRef> prepare) {
    return null;
  }

  @Override
  public BlockDisplayRef spawnBlockDisplay(Loc location, BlockStateRef block, Consumer<BlockDisplayRef> prepare) {
    return null;
  }

  @Override
  public TextDisplayRef spawnTextDisplay(Loc location, String markup, Consumer<TextDisplayRef> prepare) {
    return null;
  }

  @Override
  public EntityRef spawnInteraction(Loc location, float width, float height, boolean visibleByDefault,
                                    Consumer<EntityRef> prepare) {
    FakeEntity entity = new FakeEntity(Id.minecraft("interaction"), location);
    if (prepare != null) {
      prepare.accept(entity);
    }
    return add(entity);
  }

  @Override
  public EntityRef spawnArmorStand(Loc location, ArmorStandStyle style, Consumer<EntityRef> prepare) {
    FakeLiving entity = new FakeLiving(Id.minecraft("armor_stand"), location);
    if (prepare != null) {
      prepare.accept(entity);
    }
    return add(entity);
  }

  @Override
  public List<ChunkPos> loadedChunks() {
    return List.of();
  }

  @Override
  public List<BlockRef> blockEntities(int chunkX, int chunkZ) {
    return List.of();
  }

  @Override
  public List<EntityRef> chunkEntities(int chunkX, int chunkZ) {
    List<EntityRef> found = new ArrayList<>();
    for (EntityRef entity : entities()) {
      Loc at = entity.location();
      if (at.chunkX() == chunkX && at.chunkZ() == chunkZ) {
        found.add(entity);
      }
    }
    return found;
  }

  @Override
  public List<GeneratedStructure> structuresAt(int chunkX, int chunkZ) {
    return structures.getOrDefault(new ChunkPos(chunkX, chunkZ), List.of());
  }

  @Override
  public CompletableFuture<ChunkStructures> generatedStructures(int chunkX, int chunkZ) {
    ChunkPos chunk = new ChunkPos(chunkX, chunkZ);
    generatedRequests.add(chunk);
    CompletableFuture<ChunkStructures> answer = generated.get(chunk);
    return answer == null ? CompletableFuture.completedFuture(null) : answer;
  }

  @Override
  public Loc locateNearestStructure(Loc origin, Id structureType, int radiusChunks, boolean findUnexplored) {
    return nearestStructures.get(structureType);
  }

  @Override
  public void strikeLightningEffect(Vec3 position) {
  }

  @Override
  public boolean createExplosion(Vec3 position, float power, boolean setFire, boolean breakBlocks, EntityRef source) {
    return false;
  }

  @Override
  public List<BlockChange> planTree(Id feature, BlockPos origin, long seed) {
    return List.of();
  }

  @Override
  public boolean growTree(Id feature, BlockPos origin, long seed) {
    return false;
  }

  @Override
  public Loc spawnLocation() {
    return spawnLocation == null ? new Loc(this, 0.0D, 64.0D, 0.0D) : spawnLocation;
  }

  public FakeWorld spawnLocation(Loc location) {
    spawnLocation = location;
    return this;
  }

  @Override
  public boolean addChunkTicket(int chunkX, int chunkZ) {
    return chunkTickets.add(new ChunkPos(chunkX, chunkZ));
  }

  @Override
  public void removeChunkTicket(int chunkX, int chunkZ) {
    chunkTickets.remove(new ChunkPos(chunkX, chunkZ));
  }

  @Override
  public void requestChunkUnload(int chunkX, int chunkZ) {
  }

  /** Chunks Adapt currently holds a ticket for. */
  public Set<ChunkPos> chunkTickets() {
    return Set.copyOf(chunkTickets);
  }

  @Override
  public Object unwrap() {
    return this;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof WorldRef that && uid.equals(that.uid());
  }

  @Override
  public int hashCode() {
    return uid.hashCode();
  }
}
