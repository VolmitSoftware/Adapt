package art.arcane.adapt.mod.platform.world;

import art.arcane.adapt.mod.mixin.gapblock.ChunkMapAccess;
import art.arcane.adapt.mod.platform.Compat;
import art.arcane.adapt.mod.platform.ModContentAliases;
import art.arcane.adapt.mod.platform.ModConversions;
import art.arcane.adapt.mod.platform.ModDisplayFactory;
import art.arcane.adapt.mod.platform.ModHosts;
import art.arcane.adapt.mod.platform.ModPlatform;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.mod.platform.data.ChunkDataStore;
import art.arcane.adapt.mod.platform.data.NbtDataHolder;
import art.arcane.adapt.mod.platform.mixin.ArmorStandAccess;
import art.arcane.adapt.platform.RegistryKind;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** A loaded dimension. One instance per level; stale once the server drops the level. */
public final class ModWorldRef implements WorldRef {
    private static final long DAY = 24_000L;
    private static final TicketType<net.minecraft.world.level.ChunkPos> ADAPT_TICKET = TicketType.create("adapt",
        Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong));
    private static final int ADAPT_TICKET_RADIUS = 2;
    private final ServerLevel level;
    private final ResourceKey<Level> dimension;
    private final UUID uid;
    private final String name;
    private final Id key;

    public ModWorldRef(ServerLevel level) {
        this.level = Objects.requireNonNull(level, "level");
        this.dimension = level.dimension();
        this.key = ModConversions.id(dimension.location());
        this.uid = uid(key);
        this.name = name(level.getServer(), dimension, key);
    }

    public static UUID uid(Id dimension) {
        return UUID.nameUUIDFromBytes(("adapt:world:" + dimension).getBytes(StandardCharsets.UTF_8));
    }

    /** Bukkit-style world name: level name for the overworld, with the vanilla suffixes for the nether and the end. */
    public static String name(MinecraftServer server, ResourceKey<Level> dimension, Id key) {
        String levelName = server.getWorldData().getLevelName();
        if (dimension == Level.OVERWORLD) {
            return levelName;
        }
        if (dimension == Level.NETHER) {
            return levelName + "_nether";
        }
        if (dimension == Level.END) {
            return levelName + "_the_end";
        }
        return key.toString();
    }

    public ServerLevel level() {
        return level;
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
        MinecraftServer server = ModPlatform.server();
        return server != null && server.getLevel(dimension) == level;
    }

    @Override
    public long seed() {
        return level.getSeed();
    }

    @Override
    public Environment environment() {
        if (dimension == Level.OVERWORLD) {
            return Environment.NORMAL;
        }
        if (dimension == Level.NETHER) {
            return Environment.NETHER;
        }
        return dimension == Level.END ? Environment.THE_END : Environment.CUSTOM;
    }

    @Override
    public int minHeight() {
        return level.getMinBuildHeight();
    }

    @Override
    public int maxHeight() {
        return level.getMaxBuildHeight();
    }

    @Override
    public int seaLevel() {
        return level.getSeaLevel();
    }

    @Override
    public long time() {
        return Math.floorMod(level.getDayTime(), DAY);
    }

    @Override
    public long fullTime() {
        return level.getDayTime();
    }

    @Override
    public boolean isDay() {
        return level.isDay();
    }

    @Override
    public boolean hasStorm() {
        return level.isRaining();
    }

    @Override
    public boolean isThundering() {
        return level.isThundering();
    }

    @Override
    public boolean isRainingAt(int x, int y, int z) {
        return level.isRainingAt(new net.minecraft.core.BlockPos(x, y, z));
    }

    @Override
    public List<EntityRef> entities() {
        List<EntityRef> out = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity != null) {
                out.add(ModRefs.entity(entity));
            }
        }
        return out;
    }

    @Override
    public BlockRef blockAt(int x, int y, int z) {
        return new ModBlockRef(this, new net.minecraft.core.BlockPos(x, y, z));
    }

    @Override
    public int highestBlockY(int x, int z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
    }

    @Override
    public boolean isChunkLoaded(int chunkX, int chunkZ) {
        return level.getChunkSource().hasChunk(chunkX, chunkZ);
    }

    @Override
    public CompletableFuture<Void> loadChunkAsync(int chunkX, int chunkZ) {
        if (level.getServer().isSameThread()) {
            level.getChunk(chunkX, chunkZ);
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> level.getChunk(chunkX, chunkZ), level.getServer());
    }

    @Override
    public boolean isInsideBorder(Vec3 position) {
        return level.getWorldBorder().isWithinBounds(position.x(), position.z());
    }

    @Override
    public DataHolder chunkData(int chunkX, int chunkZ) {
        if (!isChunkLoaded(chunkX, chunkZ)) {
            return null;
        }
        long chunk = ChunkPos.key(chunkX, chunkZ);
        return new NbtDataHolder(new NbtDataHolder.Store() {
            @Override
            public CompoundTag read() {
                CompoundTag tag = ChunkDataStore.of(level).chunk(chunk);
                return tag == null || !tag.contains(ChunkDataStore.DATA, Tag.TAG_COMPOUND) ? null : tag.getCompound(ChunkDataStore.DATA);
            }

            @Override
            public void write(NbtDataHolder.Edit edit) {
                ChunkDataStore store = ChunkDataStore.of(level);
                CompoundTag chunkTag = store.chunkOrCreate(chunk);
                CompoundTag data = chunkTag.getCompound(ChunkDataStore.DATA);
                edit.apply(data);
                if (data.isEmpty()) {
                    chunkTag.remove(ChunkDataStore.DATA);
                } else {
                    chunkTag.put(ChunkDataStore.DATA, data);
                }
                store.changed(chunk);
            }
        });
    }

    @Override
    public List<PlayerRef> players() {
        List<ServerPlayer> players = level.players();
        List<PlayerRef> out = new ArrayList<>(players.size());
        for (ServerPlayer player : players) {
            out.add(ModRefs.player(player));
        }
        return out;
    }

    @Override
    public List<EntityRef> entitiesIn(Box box, Predicate<EntityRef> filter) {
        List<Entity> found = level.getEntities((Entity) null, ModConversions.aabb(box), (Entity entity) -> true);
        List<EntityRef> out = new ArrayList<>(found.size());
        for (Entity entity : found) {
            EntityRef ref = ModRefs.entity(entity);
            if (filter == null || filter.test(ref)) {
                out.add(ref);
            }
        }
        return out;
    }

    @Override
    public EntityRef rayTraceEntity(Vec3 start, Vec3 direction, double maxDistance, double raySize, Predicate<EntityRef> filter) {
        Vec3 unit = direction.normalize();
        if (unit.lengthSquared() == 0.0D || maxDistance <= 0.0D) {
            return null;
        }
        net.minecraft.world.phys.Vec3 from = ModConversions.vec(start);
        net.minecraft.world.phys.Vec3 to = ModConversions.vec(start.add(unit.multiply(maxDistance)));
        AABB search = new AABB(from, to).inflate(Math.max(0.0D, raySize) + 1.0D);
        EntityRef nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Entity entity : level.getEntities((Entity) null, search, (Entity candidate) -> true)) {
            AABB box = entity.getBoundingBox().inflate(Math.max(0.0D, raySize));
            Optional<net.minecraft.world.phys.Vec3> hit = box.contains(from) ? Optional.of(from) : box.clip(from, to);
            if (hit.isEmpty()) {
                continue;
            }
            double distance = from.distanceToSqr(hit.get());
            if (distance >= nearestDistance) {
                continue;
            }
            EntityRef ref = ModRefs.entity(entity);
            if (filter == null || filter.test(ref)) {
                nearest = ref;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    @Override
    public BlockRef rayTraceBlock(Vec3 start, Vec3 direction, double maxDistance, boolean includeFluids) {
        Vec3 unit = direction.normalize();
        if (unit.lengthSquared() == 0.0D || maxDistance <= 0.0D) {
            return null;
        }
        net.minecraft.world.phys.Vec3 from = ModConversions.vec(start);
        net.minecraft.world.phys.Vec3 to = ModConversions.vec(start.add(unit.multiply(maxDistance)));
        BlockHitResult hit = level.clip(ModWorldCompat.clip(from, to, includeFluids));
        return hit.getType() == HitResult.Type.BLOCK ? new ModBlockRef(this, hit.getBlockPos()) : null;
    }

    @Override
    public BlockHit rayTraceBlockHit(Vec3 start, Vec3 direction, double maxDistance, boolean includeFluids) {
        Vec3 unit = direction.normalize();
        if (unit.lengthSquared() == 0.0D || maxDistance <= 0.0D) {
            return null;
        }
        net.minecraft.world.phys.Vec3 from = ModConversions.vec(start);
        net.minecraft.world.phys.Vec3 to = ModConversions.vec(start.add(unit.multiply(maxDistance)));
        BlockHitResult hit = level.clip(ModWorldCompat.clip(from, to, includeFluids));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        net.minecraft.world.phys.Vec3 at = hit.getLocation();
        return new BlockHit(new Vec3(at.x, at.y, at.z), new ModBlockRef(this, hit.getBlockPos()),
                ModConversions.face(hit.getDirection()));
    }

    @Override
    public RayHit rayTraceEntityHit(Vec3 start, Vec3 direction, double maxDistance, double raySize,
                                    Predicate<EntityRef> filter) {
        Vec3 unit = direction.normalize();
        if (unit.lengthSquared() == 0.0D || maxDistance <= 0.0D) {
            return null;
        }
        net.minecraft.world.phys.Vec3 from = ModConversions.vec(start);
        net.minecraft.world.phys.Vec3 to = ModConversions.vec(start.add(unit.multiply(maxDistance)));
        AABB search = new AABB(from, to).inflate(Math.max(0.0D, raySize) + 1.0D);
        EntityRef nearest = null;
        net.minecraft.world.phys.Vec3 nearestHit = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Entity entity : level.getEntities((Entity) null, search, (Entity candidate) -> true)) {
            AABB box = entity.getBoundingBox().inflate(Math.max(0.0D, raySize));
            Optional<net.minecraft.world.phys.Vec3> hit = box.contains(from) ? Optional.of(from) : box.clip(from, to);
            if (hit.isEmpty()) {
                continue;
            }
            double distance = from.distanceToSqr(hit.get());
            if (distance >= nearestDistance) {
                continue;
            }
            EntityRef ref = ModRefs.entity(entity);
            if (filter == null || filter.test(ref)) {
                nearest = ref;
                nearestHit = hit.get();
                nearestDistance = distance;
            }
        }
        return nearest == null ? null : new RayHit(new Vec3(nearestHit.x, nearestHit.y, nearestHit.z), null, nearest);
    }

    @Override
    public RayHit rayTrace(Vec3 start, Vec3 direction, double maxDistance, boolean includeFluids, double raySize,
                           Predicate<EntityRef> filter) {
        Vec3 unit = direction.normalize();
        if (unit.lengthSquared() == 0.0D || maxDistance <= 0.0D) {
            return null;
        }
        net.minecraft.world.phys.Vec3 from = ModConversions.vec(start);
        net.minecraft.world.phys.Vec3 to = ModConversions.vec(start.add(unit.multiply(maxDistance)));
        BlockHitResult blockHit = level.clip(ModWorldCompat.clip(from, to, includeFluids));
        boolean hitBlock = blockHit.getType() == HitResult.Type.BLOCK;
        double reach = hitBlock ? from.distanceTo(blockHit.getLocation()) : maxDistance;
        net.minecraft.world.phys.Vec3 reachEnd = ModConversions.vec(start.add(unit.multiply(reach)));
        double inflate = Math.max(0.0D, raySize);
        AABB search = new AABB(from, reachEnd).inflate(inflate + 1.0D);
        EntityRef nearest = null;
        net.minecraft.world.phys.Vec3 nearestHit = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Entity entity : level.getEntities((Entity) null, search, (Entity candidate) -> true)) {
            AABB box = entity.getBoundingBox().inflate(inflate);
            Optional<net.minecraft.world.phys.Vec3> hit = box.contains(from) ? Optional.of(from) : box.clip(from, reachEnd);
            if (hit.isEmpty()) {
                continue;
            }
            double distance = from.distanceToSqr(hit.get());
            if (distance >= nearestDistance) {
                continue;
            }
            EntityRef ref = ModRefs.entity(entity);
            if (filter == null || filter.test(ref)) {
                nearest = ref;
                nearestHit = hit.get();
                nearestDistance = distance;
            }
        }
        if (nearest != null && (!hitBlock || nearestDistance < reach * reach)) {
            return new RayHit(ModConversions.vec(nearestHit), null, nearest);
        }
        return hitBlock
                ? new RayHit(ModConversions.vec(blockHit.getLocation()), new ModBlockRef(this, blockHit.getBlockPos()), null)
                : null;
    }

    @Override
    public ItemEntityRef dropItem(Vec3 position, ItemRef item) {
        return drop(position.x(), position.y(), position.z(), item);
    }

    @Override
    public ItemEntityRef dropItem(Vec3 position, ItemRef item, Consumer<ItemEntityRef> prepare) {
        ItemStack stack = ModRefs.item(item).copy();
        if (stack.isEmpty()) {
            return null;
        }
        ItemEntity entity = new ItemEntity(level, position.x(), position.y(), position.z(), stack);
        entity.setDefaultPickUpDelay();
        ItemEntityRef ref = (ItemEntityRef) ModRefs.entity(entity);
        if (prepare != null) {
            prepare.accept(ref);
        }
        level.addFreshEntity(entity);
        return ref;
    }

    @Override
    public ItemEntityRef dropItemNaturally(Vec3 position, ItemRef item) {
        double x = position.x() + (level.getRandom().nextFloat() * 0.5D) + 0.25D - 0.5D;
        double y = position.y() + (level.getRandom().nextFloat() * 0.5D) + 0.25D - 0.5D;
        double z = position.z() + (level.getRandom().nextFloat() * 0.5D) + 0.25D - 0.5D;
        return drop(x, y, z, item);
    }

    @Override
    public ItemEntityRef createItemEntity(Vec3 position, ItemRef item) {
        ItemEntity entity = new ItemEntity(level, position.x(), position.y(), position.z(), ModRefs.item(item).copy());
        entity.setDefaultPickUpDelay();
        return (ItemEntityRef) ModRefs.entity(entity);
    }

    private ItemEntityRef drop(double x, double y, double z, ItemRef item) {
        ItemStack stack = ModRefs.item(item).copy();
        if (stack.isEmpty()) {
            return null;
        }
        ItemEntity entity = new ItemEntity(level, x, y, z, stack);
        entity.setDefaultPickUpDelay();
        level.addFreshEntity(entity);
        return (ItemEntityRef) ModRefs.entity(entity);
    }

    @Override
    public void spawnExperience(Vec3 position, int amount) {
        if (amount > 0) {
            ExperienceOrb.award(level, ModConversions.vec(position), amount);
        }
    }

    @Override
    public EntityRef spawn(Id entityType, Loc location, Consumer<EntityRef> prepare) {
        ResourceLocation registered = ModContentAliases.location(BuiltInRegistries.ENTITY_TYPE, RegistryKind.ENTITY_TYPE, entityType);
        Optional<EntityType<?>> type = registered == null ? Optional.empty() : BuiltInRegistries.ENTITY_TYPE.getOptional(registered);
        if (type.isEmpty()) {
            return null;
        }
        Entity entity = type.get().create(level);
        if (entity == null) {
            return null;
        }
        entity.moveTo(location.x(), location.y(), location.z(), location.yaw(), location.pitch());
        if (entity instanceof Mob mob) {
            ModWorldCompat.finalizeSpawn(mob, level);
        }
        EntityRef ref = ModRefs.entity(entity);
        if (prepare != null) {
            prepare.accept(ref);
        }
        return level.addFreshEntity(entity) ? ref : null;
    }

    @Override
    public ItemDisplayRef spawnItemDisplay(Loc location, ItemRef item, Consumer<ItemDisplayRef> prepare) {
        return displays().spawnItem(level, location, item, prepare);
    }

    @Override
    public BlockDisplayRef spawnBlockDisplay(Loc location, BlockStateRef block, Consumer<BlockDisplayRef> prepare) {
        return displays().spawnBlock(level, location, block, prepare);
    }

    @Override
    public TextDisplayRef spawnTextDisplay(Loc location, String markup, Consumer<TextDisplayRef> prepare) {
        return displays().spawnText(level, location, markup, prepare);
    }

    @Override
    public EntityRef spawnInteraction(Loc location, float width, float height, boolean visibleByDefault, Consumer<EntityRef> prepare) {
        return displays().spawnInteraction(level, location, width, height, visibleByDefault, prepare);
    }

    @Override
    public EntityRef spawnArmorStand(Loc location, ArmorStandStyle style, Consumer<EntityRef> prepare) {
        Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(
            ModConversions.identifier(Id.minecraft("armor_stand")));
        if (type.isEmpty() || !(type.get().create(level) instanceof ArmorStand stand)) {
            return null;
        }
        stand.moveTo(location.x(), location.y(), location.z(), location.yaw(), location.pitch());
        ArmorStandAccess access = (ArmorStandAccess) stand;
        access.adapt$setSmall(style.small());
        access.adapt$setMarker(style.marker());
        stand.setNoBasePlate(!style.basePlate());
        stand.setShowArms(style.arms());
        EntityRef ref = ModRefs.entity(stand);
        if (prepare != null) {
            prepare.accept(ref);
        }
        return level.addFreshEntity(stand) ? ref : null;
    }

    @Override
    public List<ChunkPos> loadedChunks() {
        List<ChunkPos> loaded = new ArrayList<>();
        for (ChunkHolder holder : ((ChunkMapAccess) level.getChunkSource().chunkMap).adapt$chunks()) {
            LevelChunk chunk = ModWorldCompat.readyChunk(holder);
            if (chunk != null) {
                loaded.add(new ChunkPos(chunk.getPos().x, chunk.getPos().z));
            }
        }
        return loaded;
    }

    @Override
    public List<BlockRef> blockEntities(int chunkX, int chunkZ) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (chunk == null) {
            return List.of();
        }
        List<BlockRef> blocks = new ArrayList<>(chunk.getBlockEntities().size());
        for (net.minecraft.core.BlockPos pos : chunk.getBlockEntities().keySet()) {
            blocks.add(new ModBlockRef(this, pos));
        }
        return blocks;
    }

    @Override
    public List<EntityRef> chunkEntities(int chunkX, int chunkZ) {
        if (level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) {
            return List.of();
        }
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        AABB bounds = new AABB(minX, level.getMinBuildHeight(), minZ, minX + 16, level.getMaxBuildHeight(), minZ + 16);
        List<Entity> found = level.getEntities((Entity) null, bounds,
            (Entity entity) -> entity.chunkPosition().x == chunkX && entity.chunkPosition().z == chunkZ);
        List<EntityRef> out = new ArrayList<>(found.size());
        for (Entity entity : found) {
            out.add(ModRefs.entity(entity));
        }
        return out;
    }

    @Override
    public List<GeneratedStructure> structuresAt(int chunkX, int chunkZ) {
        return ModStructures.structuresAt(level, chunkX, chunkZ);
    }

    @Override
    public CompletableFuture<ChunkStructures> generatedStructures(int chunkX, int chunkZ) {
        return ModStructures.generatedStructures(level, chunkX, chunkZ);
    }

    @Override
    public Loc locateNearestStructure(Loc origin, Id structureType, int radiusChunks, boolean findUnexplored) {
        return ModStructures.locateNearest(this, level, origin, structureType, radiusChunks, findUnexplored);
    }

    @Override
    public void strikeLightningEffect(Vec3 position) {
        LightningBolt bolt = Compat.lightningBolt().create(level);
        if (bolt == null) {
            return;
        }
        bolt.moveTo(position.x(), position.y(), position.z(), 0.0F, 0.0F);
        bolt.setVisualOnly(true);
        level.addFreshEntity(bolt);
    }

    @Override
    public boolean createExplosion(Vec3 position, float power, boolean setFire, boolean breakBlocks, EntityRef source) {
        Entity exploder = source == null ? null : ModRefs.entity(source);
        level.explode(exploder, position.x(), position.y(), position.z(), power, setFire,
                breakBlocks ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE);
        return true;
    }

    @Override
    public List<BlockChange> planTree(Id feature, BlockPos origin, long seed) {
        if (feature == null || !ModTreeFeatures.exists(level, ModConversions.identifier(feature))) {
            return List.of();
        }
        ModTreeCapture capture = ModTreeCapture.of(level);
        ModTreeFeatures.place(level, capture.view(), ModConversions.identifier(feature), RandomSource.create(seed),
            new net.minecraft.core.BlockPos(origin.x(), origin.y(), origin.z()));
        List<BlockChange> planned = new ArrayList<>(capture.placed().size());
        for (Map.Entry<net.minecraft.core.BlockPos, BlockState> entry : capture.placed().entrySet()) {
            planned.add(new BlockChange(new ModBlockRef(this, entry.getKey()), ModRefs.state(entry.getValue())));
        }
        return planned;
    }

    @Override
    public boolean growTree(Id feature, BlockPos origin, long seed) {
        return feature != null && ModTreeFeatures.place(level, level, ModConversions.identifier(feature), RandomSource.create(seed),
            new net.minecraft.core.BlockPos(origin.x(), origin.y(), origin.z()));
    }

    @Override
    public Loc spawnLocation() {
        ServerLevel spawnLevel = level.getServer().overworld();
        net.minecraft.core.BlockPos pos = spawnLevel.getSharedSpawnPos();
        return new Loc(ModRefs.world(spawnLevel), pos.getX(), pos.getY(), pos.getZ(), spawnLevel.getSharedSpawnAngle(), 0.0F);
    }

    @Override
    public boolean addChunkTicket(int chunkX, int chunkZ) {
        net.minecraft.world.level.ChunkPos chunk = new net.minecraft.world.level.ChunkPos(chunkX, chunkZ);
        level.getChunkSource().addRegionTicket(ADAPT_TICKET, chunk, ADAPT_TICKET_RADIUS, chunk);
        return true;
    }

    @Override
    public void removeChunkTicket(int chunkX, int chunkZ) {
        net.minecraft.world.level.ChunkPos chunk = new net.minecraft.world.level.ChunkPos(chunkX, chunkZ);
        level.getChunkSource().removeRegionTicket(ADAPT_TICKET, chunk, ADAPT_TICKET_RADIUS, chunk);
    }

    @Override
    public void requestChunkUnload(int chunkX, int chunkZ) {
    }

    @Override
    public Object unwrap() {
        return level;
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
        return "ModWorldRef[" + key + "]";
    }

    private static ModDisplayFactory displays() {
        ModDisplayFactory factory = ModHosts.displays();
        if (factory == null) {
            throw new IllegalStateException("No display factory registered");
        }
        return factory;
    }
}
