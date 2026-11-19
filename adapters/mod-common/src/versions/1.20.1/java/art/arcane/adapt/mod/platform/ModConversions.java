package art.arcane.adapt.mod.platform;

import art.arcane.adapt.platform.ContentAliases;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.fx.SoundCategory;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

/** Value conversions between SPI types and vanilla types. */
public final class ModConversions {
    private ModConversions() {
    }

    public static Id id(ResourceLocation identifier) {
        return new Id(identifier.getNamespace(), identifier.getPath());
    }

    public static ResourceLocation identifier(Id id) {
        return new ResourceLocation(id.namespace(), id.path());
    }

    public static net.minecraft.world.phys.Vec3 vec(art.arcane.adapt.platform.value.Vec3 vector) {
        return new net.minecraft.world.phys.Vec3(vector.x(), vector.y(), vector.z());
    }

    public static art.arcane.adapt.platform.value.Vec3 vec(net.minecraft.world.phys.Vec3 vector) {
        return new art.arcane.adapt.platform.value.Vec3(vector.x, vector.y, vector.z);
    }

    public static net.minecraft.core.BlockPos pos(art.arcane.adapt.platform.value.BlockPos pos) {
        return new net.minecraft.core.BlockPos(pos.x(), pos.y(), pos.z());
    }

    public static art.arcane.adapt.platform.value.BlockPos pos(net.minecraft.core.BlockPos pos) {
        return new art.arcane.adapt.platform.value.BlockPos(pos.getX(), pos.getY(), pos.getZ());
    }

    public static Box box(AABB box) {
        return new Box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    public static AABB aabb(Box box) {
        return new AABB(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
    }

    /** The vanilla slot, or null for {@code BODY} and {@code SADDLE}, which this version keeps in the mount's inventory. */
    public static EquipmentSlot slot(art.arcane.adapt.platform.entity.EquipmentSlot slot) {
        return switch (slot) {
            case MAIN_HAND -> EquipmentSlot.MAINHAND;
            case OFF_HAND -> EquipmentSlot.OFFHAND;
            case HEAD -> EquipmentSlot.HEAD;
            case CHEST -> EquipmentSlot.CHEST;
            case LEGS -> EquipmentSlot.LEGS;
            case FEET -> EquipmentSlot.FEET;
            case BODY, SADDLE -> null;
        };
    }

    public static art.arcane.adapt.platform.entity.EquipmentSlot slot(EquipmentSlot slot) {
        return switch (slot) {
            case MAINHAND -> art.arcane.adapt.platform.entity.EquipmentSlot.MAIN_HAND;
            case OFFHAND -> art.arcane.adapt.platform.entity.EquipmentSlot.OFF_HAND;
            case HEAD -> art.arcane.adapt.platform.entity.EquipmentSlot.HEAD;
            case CHEST -> art.arcane.adapt.platform.entity.EquipmentSlot.CHEST;
            case LEGS -> art.arcane.adapt.platform.entity.EquipmentSlot.LEGS;
            case FEET -> art.arcane.adapt.platform.entity.EquipmentSlot.FEET;
        };
    }

    public static InteractionHand hand(Hand hand) {
        return hand == Hand.MAIN_HAND ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }

    public static Hand hand(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? Hand.MAIN_HAND : Hand.OFF_HAND;
    }

    public static GameType gameType(GameMode mode) {
        return switch (mode) {
            case SURVIVAL -> GameType.SURVIVAL;
            case CREATIVE -> GameType.CREATIVE;
            case ADVENTURE -> GameType.ADVENTURE;
            case SPECTATOR -> GameType.SPECTATOR;
        };
    }

    public static GameMode gameMode(GameType type) {
        return switch (type) {
            case SURVIVAL -> GameMode.SURVIVAL;
            case CREATIVE -> GameMode.CREATIVE;
            case ADVENTURE -> GameMode.ADVENTURE;
            case SPECTATOR -> GameMode.SPECTATOR;
        };
    }

    public static SoundSource soundSource(SoundCategory category) {
        return switch (category) {
            case MASTER -> SoundSource.MASTER;
            case MUSIC -> SoundSource.MUSIC;
            case RECORDS -> SoundSource.RECORDS;
            case WEATHER -> SoundSource.WEATHER;
            case BLOCKS -> SoundSource.BLOCKS;
            case HOSTILE -> SoundSource.HOSTILE;
            case NEUTRAL -> SoundSource.NEUTRAL;
            case PLAYERS -> SoundSource.PLAYERS;
            case AMBIENT -> SoundSource.AMBIENT;
            case VOICE -> SoundSource.VOICE;
        };
    }

    /** The vanilla direction of an axis face; null for the diagonal faces, which vanilla has no direction for (as CraftBlock). */
    public static Direction direction(BlockFace face) {
        return switch (face) {
            case DOWN -> Direction.DOWN;
            case UP, SELF -> Direction.UP;
            case NORTH -> Direction.NORTH;
            case SOUTH -> Direction.SOUTH;
            case WEST -> Direction.WEST;
            case EAST -> Direction.EAST;
            case NORTH_EAST, NORTH_WEST, SOUTH_EAST, SOUTH_WEST, WEST_NORTH_WEST, NORTH_NORTH_WEST, NORTH_NORTH_EAST,
                 EAST_NORTH_EAST, EAST_SOUTH_EAST, SOUTH_SOUTH_EAST, SOUTH_SOUTH_WEST, WEST_SOUTH_WEST -> null;
        };
    }

    public static BlockFace face(Direction direction) {
        return switch (direction) {
            case DOWN -> BlockFace.DOWN;
            case UP -> BlockFace.UP;
            case NORTH -> BlockFace.NORTH;
            case SOUTH -> BlockFace.SOUTH;
            case WEST -> BlockFace.WEST;
            case EAST -> BlockFace.EAST;
        };
    }

    /** The attribute under its name on this version ({@code generic.}/{@code player.} prefixes), or null. */
    public static Holder<Attribute> attribute(Id id) {
        return ModContentAliases.holder(BuiltInRegistries.ATTRIBUTE, RegistryKind.ATTRIBUTE, id);
    }

    public static Holder<MobEffect> effect(Id id) {
        return ModContentAliases.holder(BuiltInRegistries.MOB_EFFECT, RegistryKind.EFFECT, id);
    }

    /**
     * Registered sound (or the closest older one, see {@link ContentAliases#resolveFx}), else a direct holder for
     * sounds only a resource pack defines.
     */
    public static Holder<SoundEvent> sound(Id id) {
        Id resolved = ContentAliases.resolveFx(RegistryKind.SOUND, id,
                (Id candidate) -> BuiltInRegistries.SOUND_EVENT.containsKey(identifier(candidate)));
        Holder<SoundEvent> registered = resolved == null ? null
                : BuiltInRegistries.SOUND_EVENT.getHolder(ResourceKey.create(Registries.SOUND_EVENT, identifier(resolved))).orElse(null);
        return registered != null ? registered : Holder.direct(SoundEvent.createVariableRangeEvent(identifier(id)));
    }

    /** Enchantments are fixed objects on this version, registered in the built-in registry. */
    public static Holder<Enchantment> enchantment(Id id) {
        return ModContentAliases.holder(BuiltInRegistries.ENCHANTMENT, RegistryKind.ENCHANTMENT, id);
    }

    public static Id enchantmentId(Holder<Enchantment> enchantment) {
        return ModContentAliases.modern(RegistryKind.ENCHANTMENT, enchantment);
    }
}
