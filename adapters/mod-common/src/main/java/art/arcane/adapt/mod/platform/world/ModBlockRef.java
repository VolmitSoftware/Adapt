package art.arcane.adapt.mod.platform.world;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import art.arcane.adapt.mod.platform.ModConversions;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.mod.platform.data.AdaptBlockEntityData;
import art.arcane.adapt.mod.platform.data.ChunkDataStore;
import art.arcane.adapt.mod.platform.data.NbtDataHolder;
import art.arcane.adapt.mod.platform.entity.ModInventoryRef;
import art.arcane.adapt.mod.platform.mixin.BiomeAccess;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.BrewingStandRef;
import art.arcane.adapt.platform.world.CampfireRef;
import art.arcane.adapt.platform.world.FurnaceRef;
import art.arcane.adapt.platform.world.LidRef;
import art.arcane.adapt.platform.world.WorldRef;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LevelEvent;
import art.arcane.adapt.platform.value.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A block coordinate in a loaded level. Reads reflect the live block. */
public final class ModBlockRef implements BlockRef {
    private static final int BONE_MEAL_PARTICLES = 15;

    private final ModWorldRef world;
    private final BlockPos pos;

    public ModBlockRef(ModWorldRef world, BlockPos pos) {
        this.world = Objects.requireNonNull(world, "world");
        this.pos = pos.immutable();
    }

    public BlockPos blockPos() {
        return pos;
    }

    public ServerLevel level() {
        return world.level();
    }

    public BlockState blockState() {
        return level().getBlockState(pos);
    }

    @Override
    public WorldRef world() {
        return world;
    }

    @Override
    public art.arcane.adapt.platform.value.BlockPos pos() {
        return ModConversions.pos(pos);
    }

    @Override
    public Loc location() {
        return new Loc(world, pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public Id type() {
        return ModConversions.id(BuiltInRegistries.BLOCK.getKey(blockState().getBlock()));
    }

    @Override
    public BlockStateRef state() {
        return new ModBlockStateRef(blockState());
    }

    @Override
    public void setState(BlockStateRef state, boolean applyPhysics) {
        level().setBlock(pos, ((ModBlockStateRef) state).state(), flags(applyPhysics));
    }

    @Override
    public void setType(Id type, boolean applyPhysics) {
        Block block = BuiltInRegistries.BLOCK.getOptional(ModConversions.identifier(type)).orElse(null);
        if (block != null) {
            level().setBlock(pos, block.defaultBlockState(), flags(applyPhysics));
        }
    }

    @Override
    public BlockRef relative(BlockFace face) {
        return relative(face.dx(), face.dy(), face.dz());
    }

    @Override
    public BlockRef relative(BlockFace face, int distance) {
        return relative(face.dx() * distance, face.dy() * distance, face.dz() * distance);
    }

    @Override
    public BlockRef relative(int dx, int dy, int dz) {
        return new ModBlockRef(world, pos.offset(dx, dy, dz));
    }

    @Override
    public boolean isAir() {
        return blockState().isAir();
    }

    @Override
    public boolean isLiquid() {
        return blockState().getBlock() instanceof LiquidBlock;
    }

    @Override
    public boolean isPassable() {
        return blockState().getCollisionShape(level(), pos).isEmpty();
    }

    @Override
    public boolean isReplaceable() {
        return blockState().canBeReplaced();
    }

    @Override
    public InventoryRef blockInventory() {
        ServerLevel level = level();
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof LecternBlockEntity) {
            return new ModInventoryRef(() -> level.getBlockEntity(pos) instanceof LecternBlockEntity lectern
                ? new LecternBook(lectern) : new SimpleContainer(0));
        }
        if (!(entity instanceof Container)) {
            return null;
        }
        return new ModInventoryRef(() -> level.getBlockEntity(pos) instanceof Container container
            ? container : new SimpleContainer(0));
    }

    /** The lectern's single book slot as a container, like Bukkit's lectern inventory. */
    private record LecternBook(LecternBlockEntity lectern) implements Container {
        @Override
        public int getContainerSize() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return !lectern.hasBook();
        }

        @Override
        public ItemStack getItem(int slot) {
            return slot == 0 ? lectern.getBook() : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int count) {
            if (slot != 0 || !lectern.hasBook()) {
                return ItemStack.EMPTY;
            }
            ItemStack book = lectern.getBook().copy();
            lectern.clearContent();
            return book;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return removeItem(slot, 1);
        }

        @Override
        public void setItem(int slot, ItemStack item) {
            if (slot != 0) {
                return;
            }
            if (item.isEmpty()) {
                lectern.clearContent();
            } else {
                lectern.setBook(item);
            }
        }

        @Override
        public void setChanged() {
            lectern.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            lectern.clearContent();
        }
    }

    @Override
    public boolean isSolid() {
        return blockState().isSolid();
    }

    @Override
    public Box boundingBox() {
        VoxelShape shape = blockState().getShape(level(), pos);
        if (shape.isEmpty()) {
            return new Box(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        return ModConversions.box(shape.bounds().move(pos));
    }

    @Override
    public List<Box> collisionBoxes() {
        VoxelShape shape = blockState().getCollisionShape(level(), pos);
        if (shape.isEmpty()) {
            return List.of();
        }
        List<AABB> boxes = shape.toAabbs();
        List<Box> out = new ArrayList<>(boxes.size());
        for (AABB box : boxes) {
            out.add(ModConversions.box(box.move(pos)));
        }
        return out;
    }

    @Override
    public int lightLevel() {
        return level().getMaxLocalRawBrightness(pos);
    }

    @Override
    public int skyLight() {
        return level().getBrightness(LightLayer.SKY, pos);
    }

    @Override
    public float hardness() {
        return blockState().getDestroySpeed(level(), pos);
    }

    @Override
    public boolean isPreferredTool(ItemRef tool) {
        BlockState state = blockState();
        return !state.requiresCorrectToolForDrops() || ModRefs.item(tool).isCorrectToolForDrops(state);
    }

    @Override
    public List<ItemRef> drops(ItemRef tool, EntityRef breaker) {
        ServerLevel level = level();
        BlockState state = blockState();
        BlockEntity blockEntity = level.getBlockEntity(pos);
        Entity entity = breaker == null ? null : ModRefs.entity(breaker);
        ItemStack stack = tool == null ? ItemStack.EMPTY : ModRefs.item(tool);
        List<ItemStack> drops = Block.getDrops(state, level, pos, blockEntity, entity, stack);
        List<ItemRef> out = new ArrayList<>(drops.size());
        for (ItemStack drop : drops) {
            out.add(ModRefs.item(drop));
        }
        return out;
    }

    @Override
    public boolean breakNaturally(ItemRef tool) {
        ServerLevel level = level();
        BlockState state = blockState();
        if (state.isAir()) {
            return false;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        ItemStack stack = tool == null ? ItemStack.EMPTY : ModRefs.item(tool);
        level.levelEvent(2001, pos, Block.getId(state));
        Block.dropResources(state, level, pos, blockEntity, null, stack);
        return level.setBlock(pos, state.getFluidState().createLegacyBlock(), Block.UPDATE_ALL);
    }

    @Override
    public Id biome() {
        Holder<Biome> biome = noiseBiome(level());
        return biome.unwrapKey().map((ResourceKey<Biome> key) -> ModConversions.id(key.identifier())).orElse(Id.minecraft("plains"));
    }

    @Override
    public double temperature() {
        ServerLevel level = level();
        return ((BiomeAccess) (Object) noiseBiome(level).value()).adapt$temperature(pos, level.getSeaLevel());
    }

    /** The stored biome of this block's 4x4x4 cell, as CraftWorld reads it; {@code getBiome} blends neighbouring cells. */
    private Holder<Biome> noiseBiome(ServerLevel level) {
        return level.getNoiseBiome(QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(pos.getY()), QuartPos.fromBlock(pos.getZ()));
    }

    @Override
    public boolean applyBoneMeal(BlockFace face) {
        ServerLevel level = level();
        ItemStack boneMeal = new ItemStack(Items.BONE_MEAL);
        if (BoneMealItem.growCrop(boneMeal, level, pos)) {
            level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, pos, BONE_MEAL_PARTICLES);
            return true;
        }
        Direction direction = ModConversions.direction(face);
        if (direction == null) {
            return false;
        }
        BlockPos beside = pos.relative(direction);
        boolean solid = blockState().isFaceSturdy(level, pos, direction);
        if (solid && BoneMealItem.growWaterPlant(boneMeal, level, beside, direction)) {
            level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, beside, BONE_MEAL_PARTICLES);
            return true;
        }
        return false;
    }

    @Override
    public InventoryRef container() {
        ServerLevel level = level();
        if (!(level.getBlockEntity(pos) instanceof Container)) {
            return null;
        }
        return new ModInventoryRef(() -> {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof ChestBlock chest) {
                Container combined = ChestBlock.getContainer(chest, state, level, pos, true);
                if (combined != null) {
                    return combined;
                }
            }
            return (Container) level.getBlockEntity(pos);
        });
    }

    @Override
    public Optional<BrewingStandRef> brewingStand() {
        return level().getBlockEntity(pos) instanceof BrewingStandBlockEntity ? Optional.of(new ModBrewingStandRef(this)) : Optional.empty();
    }

    @Override
    public Optional<FurnaceRef> furnace() {
        return level().getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity ? Optional.of(new ModFurnaceRef(this)) : Optional.empty();
    }

    @Override
    public Optional<CampfireRef> campfire() {
        return level().getBlockEntity(pos) instanceof CampfireBlockEntity ? Optional.of(new ModCampfireRef(this)) : Optional.empty();
    }

    @Override
    public List<BlockRef> containerHalves() {
        ServerLevel level = level();
        if (!(level.getBlockEntity(pos) instanceof Container)) {
            return List.of();
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos other = ChestBlock.getConnectedBlockPos(pos, state);
            BlockState otherState = level.getBlockState(other);
            if (otherState.getBlock() == state.getBlock() && otherState.getValue(ChestBlock.TYPE) != ChestType.SINGLE
                    && level.getBlockEntity(other) instanceof Container) {
                ModBlockRef partner = new ModBlockRef(world, other);
                return state.getValue(ChestBlock.TYPE) == ChestType.RIGHT ? List.of(this, partner) : List.of(partner, this);
            }
        }
        return List.of(this);
    }

    @Override
    public Optional<LidRef> lid() {
        return Optional.ofNullable(ModLidRef.at(level(), pos));
    }

    @Override
    public boolean isRedstonePowered() {
        ServerLevel level = level();
        return level.getDirectSignalTo(pos) > 0 || level.hasNeighborSignal(pos);
    }

    @Override
    public DataHolder data() {
        return new NbtDataHolder(new BlockStore());
    }

    @Override
    public boolean hasData() {
        CompoundTag tag = new BlockStore().read();
        return tag != null && !tag.isEmpty();
    }

    @Override
    public DataHolder blockEntityData() {
        return blockEntityData(level().getBlockEntity(pos));
    }

    /**
     * Paper's {@code BlockStateMeta#setBlockState}: the block entity's components over the item's defaults, plus its
     * remaining custom data as {@code BLOCK_ENTITY_DATA}.
     */
    @Override
    public ItemRef itemWithBlockState(ItemRef item) {
        ItemStack copy = ModRefs.item(item).copy();
        BlockEntity entity = level().getBlockEntity(pos);
        if (entity == null || !(copy.getItem() instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof EntityBlock)) {
            return null;
        }
        PatchedDataComponentMap components = new PatchedDataComponentMap(entity.getBlockState().getBlock().asItem().components());
        components.setAll(entity.collectComponents());
        copy.applyComponents(components.asPatch());
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level().registryAccess());
        entity.saveCustomOnly(output);
        entity.removeComponentsFromTag(output);
        if (!output.isEmpty()) {
            copy.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(entity.getType(), output.buildResult()));
        }
        return ModRefs.item(copy);
    }

    /** Live data of a block entity (saved with it), null for no block entity. */
    public static DataHolder blockEntityData(BlockEntity blockEntity) {
        if (!(blockEntity instanceof AdaptBlockEntityData holder)) {
            return null;
        }
        return new NbtDataHolder(new NbtDataHolder.Store() {
            @Override
            public CompoundTag read() {
                return holder.adapt$data();
            }

            @Override
            public void write(NbtDataHolder.Edit edit) {
                CompoundTag tag = holder.adapt$dataOrCreate();
                edit.apply(tag);
                if (tag.isEmpty()) {
                    holder.adapt$setData(null);
                }
                blockEntity.setChanged();
            }
        });
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BlockRef that && world.equals(that.world()) && pos().equals(that.pos());
    }

    @Override
    public int hashCode() {
        return world.hashCode() * 31 + pos.hashCode();
    }

    @Override
    public String toString() {
        return "ModBlockRef[" + world.name() + " " + pos.toShortString() + "]";
    }

    private static int flags(boolean applyPhysics) {
        return applyPhysics ? Block.UPDATE_ALL : Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_ON_PLACE;
    }

    private final class BlockStore implements NbtDataHolder.Store {
        private final long chunk = ChunkPos.key(pos.getX() >> 4, pos.getZ() >> 4);
        private final String key = Long.toString(pos.asLong());

        @Override
        public CompoundTag read() {
            CompoundTag tag = ChunkDataStore.of(level()).chunk(chunk);
            return tag == null ? null : tag.getCompoundOrEmpty(ChunkDataStore.BLOCKS).getCompound(key).orElse(null);
        }

        @Override
        public void write(NbtDataHolder.Edit edit) {
            ChunkDataStore store = ChunkDataStore.of(level());
            CompoundTag chunkTag = store.chunkOrCreate(chunk);
            CompoundTag blocks = chunkTag.getCompoundOrEmpty(ChunkDataStore.BLOCKS);
            CompoundTag block = blocks.getCompoundOrEmpty(key);
            edit.apply(block);
            if (block.isEmpty()) {
                blocks.remove(key);
            } else {
                blocks.put(key, block);
            }
            if (blocks.isEmpty()) {
                chunkTag.remove(ChunkDataStore.BLOCKS);
            } else {
                chunkTag.put(ChunkDataStore.BLOCKS, blocks);
            }
            store.changed(chunk);
        }
    }
}
