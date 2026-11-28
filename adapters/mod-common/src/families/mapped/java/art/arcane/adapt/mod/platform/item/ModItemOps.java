package art.arcane.adapt.mod.platform.item;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import art.arcane.adapt.mod.feature.loader.LoaderToolActions;
import art.arcane.adapt.mod.platform.ModConversions;
import art.arcane.adapt.mod.platform.ModPlatform;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.ToolKind;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockStateRef;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ModItemOps implements ItemOps {
    private static final Map<Class<?>, Boolean> INTERACTABLE = new ConcurrentHashMap<>();
    /** Parameters of {@code useWithoutItem} and {@code useItemOn}; runtime names are obfuscated, the types are not. */
    private static final List<Class<?>[]> USE_PARAMETERS = List.of(
        new Class<?>[] {BlockState.class, Level.class, BlockPos.class, Player.class, BlockHitResult.class},
        new Class<?>[] {ItemStack.class, BlockState.class, Level.class, BlockPos.class, Player.class, InteractionHand.class, BlockHitResult.class});

    @Override
    public ItemRef create(Id type, int amount) {
        Item item = item(type);
        return ModRefs.item(item == null ? ItemStack.EMPTY : new ItemStack(item, Math.max(1, amount)));
    }

    @Override
    public ItemRef empty() {
        return ModRefs.item(ItemStack.EMPTY);
    }

    @Override
    public boolean isItem(Id type) {
        return item(type) != null;
    }

    @Override
    public boolean isBlock(Id type) {
        return BuiltInRegistries.BLOCK.containsKey(ModConversions.identifier(type));
    }

    @Override
    public boolean isEdible(Id type) {
        Item item = item(type);
        return item != null && item.components().has(DataComponents.FOOD);
    }

    @Override
    public int maxStackSize(Id type) {
        Item item = item(type);
        return item == null ? 0 : item.getDefaultMaxStackSize();
    }

    @Override
    public int maxDurability(Id type) {
        Item item = item(type);
        return item == null ? 0 : item.components().getOrDefault(DataComponents.MAX_DAMAGE, 0);
    }

    @Override
    public float blockHardness(Id block) {
        Block found = block(block);
        return found == null ? 0F : found.defaultDestroyTime();
    }

    @Override
    public float blastResistance(Id block) {
        Block found = block(block);
        return found == null ? 0F : found.getExplosionResistance();
    }

    @Override
    public float blockFriction(Id block) {
        Block found = block(block);
        return found == null ? 0.6F : found.getFriction();
    }

    @Override
    public boolean isOccluding(Id block) {
        Block found = block(block);
        return found != null && found.defaultBlockState().isRedstoneConductor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    @Override
    public boolean isInteractable(Id block) {
        Block found = block(block);
        return found != null && INTERACTABLE.computeIfAbsent(found.getClass(), ModItemOps::overridesUse);
    }

    @Override
    public BlockStateRef blockState(Id block) {
        Block found = block(block);
        return found == null ? null : ModRefs.state(found.defaultBlockState());
    }

    @Override
    public Set<ToolKind> toolActions(ItemRef item) {
        return LoaderToolActions.kinds(ModRefs.item(item));
    }

    @Override
    public ItemRef spawner(Id spawnedType) {
        ItemStack spawner = new ItemStack(Items.SPAWNER);
        Optional<BlockEntityType<?>> type = BuiltInRegistries.BLOCK_ENTITY_TYPE.getOptional(ResourceLocation.withDefaultNamespace("mob_spawner"));
        if (spawnedType != null && type.isPresent()) {
            CompoundTag entity = new CompoundTag();
            entity.putString("id", spawnedType.toString());
            CompoundTag spawnData = new CompoundTag();
            spawnData.put("entity", entity);
            CompoundTag data = new CompoundTag();
            data.putString("id", BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type.get()).toString());
            data.put("SpawnData", spawnData);
            spawner.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(data));
        }
        return ModRefs.item(spawner);
    }

    @Override
    public ItemRef playerHead(PlayerRef owner) {
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        ServerPlayer player = owner == null ? null : ModRefs.player(owner);
        if (player != null) {
            head.set(DataComponents.PROFILE, new ResolvableProfile(player.getGameProfile()));
        }
        return ModRefs.item(head);
    }

    @Override
    public String encode(ItemRef item) {
        ItemStack stack = ModRefs.item(item);
        if (stack.isEmpty()) {
            return "";
        }
        DataResult<Tag> encoded = ItemStack.CODEC.encodeStart(ops(), stack);
        return encoded.result().map(Tag::toString).orElse("");
    }

    @Override
    public ItemRef decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return empty();
        }
        try {
            CompoundTag tag = TagParser.parseTag(encoded);
            Optional<ItemStack> decoded = ItemStack.CODEC.parse(ops(), tag).result();
            return ModRefs.item(decoded.orElse(ItemStack.EMPTY));
        } catch (Exception invalid) {
            return empty();
        }
    }

    @Override
    public byte[] serialize(ItemRef item) {
        ItemStack stack = ModRefs.item(item);
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("Cannot serialize an empty item stack");
        }
        Tag encoded = ItemStack.CODEC.encodeStart(ops(), stack).getOrThrow(IllegalArgumentException::new);
        return encoded.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public ItemRef deserialize(byte[] data) {
        try {
            CompoundTag tag = TagParser.parseTag(new String(data, StandardCharsets.UTF_8));
            return ModRefs.item(ItemStack.CODEC.parse(ops(), tag).getOrThrow(IllegalArgumentException::new));
        } catch (CommandSyntaxException invalid) {
            throw new IllegalArgumentException("Unreadable item data", invalid);
        }
    }

    @Override
    public byte[] encodeNbt(ItemRef item) {
        ItemStack stack = ModRefs.item(item);
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("Cannot encode an empty item stack");
        }
        Tag encoded = ItemStack.CODEC.encodeStart(ops(), stack).getOrThrow(IllegalArgumentException::new);
        if (!(encoded instanceof CompoundTag compound)) {
            throw new IllegalArgumentException("Item stack did not encode to a compound");
        }
        return compressed(compound);
    }

    private static byte[] compressed(CompoundTag tag) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            NbtIo.writeCompressed(tag, bytes);
        } catch (IOException failed) {
            throw new IllegalArgumentException("Item stack could not be written", failed);
        }
        return bytes.toByteArray();
    }

    @Override
    public boolean hasBlockEntity(Id block) {
        Block found = block(block);
        return found != null && found.defaultBlockState().hasBlockEntity();
    }

    @Override
    public ItemRef enchantWithLevels(ItemRef item, int levels, boolean treasure, Random random) {
        ItemStack stack = ModRefs.item(item).copy();
        MinecraftServer server = ModPlatform.server();
        if (server == null || stack.isEmpty()) {
            return ModRefs.item(stack);
        }
        RegistryAccess access = server.registryAccess();
        Optional<HolderSet.Named<Enchantment>> pool = treasure
            ? Optional.empty()
            : access.registryOrThrow(Registries.ENCHANTMENT).getTag(EnchantmentTags.IN_ENCHANTING_TABLE);
        return ModRefs.item(EnchantmentHelper.enchantItem(RandomSource.create(random.nextLong()), stack, levels, access, pool));
    }

    @Override
    public boolean canEnchant(Id enchantment, ItemRef item) {
        Holder<Enchantment> holder = ModConversions.enchantment(enchantment);
        return holder != null && item != null && !item.isEmpty() && holder.value().canEnchant(ModRefs.item(item));
    }

    @Override
    public byte[] serializeItems(List<ItemRef> items) {
        ListTag list = new ListTag();
        for (ItemRef item : items) {
            ItemStack stack = item == null ? ItemStack.EMPTY : ModRefs.item(item);
            list.add(ItemStack.OPTIONAL_CODEC.encodeStart(ops(), stack).getOrThrow(IllegalArgumentException::new));
        }
        CompoundTag root = new CompoundTag();
        root.put("items", list);
        return root.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public List<ItemRef> deserializeItems(byte[] data) {
        if (data == null || data.length == 0) {
            return List.of();
        }
        try {
            CompoundTag root = TagParser.parseTag(new String(data, StandardCharsets.UTF_8));
            ListTag list = root.getList("items", Tag.TAG_COMPOUND);
            List<ItemRef> items = new ArrayList<>(list.size());
            for (Tag entry : list) {
                items.add(ModRefs.item(ItemStack.OPTIONAL_CODEC.parse(ops(), entry).getOrThrow(IllegalStateException::new)));
            }
            return items;
        } catch (CommandSyntaxException | RuntimeException invalid) {
            throw new IllegalStateException("Item payload could not be read", invalid);
        }
    }

    @Override
    public boolean enchantmentsConflict(Id first, Id second) {
        Holder<Enchantment> a = ModConversions.enchantment(first);
        Holder<Enchantment> b = ModConversions.enchantment(second);
        return a != null && b != null && !Enchantment.areCompatible(a, b);
    }

    @Override
    public int enchantmentMinLevel(Id enchantment) {
        Holder<Enchantment> holder = ModConversions.enchantment(enchantment);
        return holder == null ? 1 : holder.value().getMinLevel();
    }

    private static DynamicOps<Tag> ops() {
        MinecraftServer server = ModPlatform.server();
        return server == null ? NbtOps.INSTANCE : server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
    }

    private static boolean overridesUse(Class<?> type) {
        for (Class<?> current = type; current != null && current != Block.class && current != BlockBehaviour.class;
             current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                for (Class<?>[] parameters : USE_PARAMETERS) {
                    if (Arrays.equals(method.getParameterTypes(), parameters)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static Block block(Id type) {
        if (type == null || type.isAir()) {
            return null;
        }
        return BuiltInRegistries.BLOCK.getOptional(ModConversions.identifier(type)).orElse(null);
    }

    private static Item item(Id type) {
        ResourceLocation id = ModConversions.identifier(type);
        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(id);
        return item.filter((Item found) -> found != Items.AIR).orElse(null);
    }
}
