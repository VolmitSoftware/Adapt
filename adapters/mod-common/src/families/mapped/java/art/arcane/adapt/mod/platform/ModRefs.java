package art.arcane.adapt.mod.platform;

import art.arcane.adapt.mod.platform.entity.ModEntityRef;
import art.arcane.adapt.mod.platform.entity.ModExperienceOrbRef;
import art.arcane.adapt.mod.platform.entity.ModItemEntityRef;
import art.arcane.adapt.mod.platform.entity.ModLivingRef;
import art.arcane.adapt.mod.platform.entity.ModMobRef;
import art.arcane.adapt.mod.platform.entity.ModPlayerRef;
import art.arcane.adapt.mod.platform.entity.ModProjectileRef;
import art.arcane.adapt.mod.platform.item.ModItemRef;
import art.arcane.adapt.mod.platform.world.ModBlockRef;
import art.arcane.adapt.mod.platform.world.ModBlockStateRef;
import art.arcane.adapt.mod.platform.world.ModWorldRef;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.WorldRef;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wraps vanilla objects into SPI handles and unwraps them again. Player and world handles are
 * cached (one per UUID or dimension); other handles are cheap wrappers created per call.
 */
public final class ModRefs {
    private static final Map<UUID, ModPlayerRef> PLAYERS = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, ModWorldRef> WORLDS = new ConcurrentHashMap<>();

    private ModRefs() {
    }

    public static PlayerRef player(ServerPlayer player) {
        ModPlayerRef ref = PLAYERS.get(player.getUUID());
        if (ref == null) {
            ModPlayerRef created = new ModPlayerRef(player);
            ref = PLAYERS.putIfAbsent(player.getUUID(), created);
            if (ref == null) {
                return created;
            }
        }
        ref.rebind(player);
        return ref;
    }

    public static EntityRef entity(Entity entity) {
        return switch (entity) {
            case ServerPlayer player -> player(player);
            case Display display -> display(display);
            case Mob mob -> new ModMobRef(mob);
            case LivingEntity living -> new ModLivingRef(living);
            case Projectile projectile -> new ModProjectileRef(projectile);
            case ItemEntity item -> new ModItemEntityRef(item);
            case ExperienceOrb orb -> new ModExperienceOrbRef(orb);
            default -> new ModEntityRef(entity);
        };
    }

    public static LivingRef living(LivingEntity entity) {
        return (LivingRef) entity(entity);
    }

    public static MobRef mob(Mob mob) {
        return new ModMobRef(mob);
    }

    public static WorldRef world(ServerLevel level) {
        ModWorldRef ref = WORLDS.get(level.dimension());
        if (ref == null || ref.level() != level) {
            ref = new ModWorldRef(level);
            WORLDS.put(level.dimension(), ref);
        }
        return ref;
    }

    public static BlockRef block(ServerLevel level, net.minecraft.core.BlockPos pos) {
        return new ModBlockRef((ModWorldRef) world(level), pos);
    }

    public static BlockStateRef state(BlockState state) {
        return new ModBlockStateRef(state);
    }

    public static ItemRef item(ItemStack stack) {
        return new ModItemRef(stack == null ? ItemStack.EMPTY : stack);
    }

    public static Loc loc(ServerLevel level, Vec3 position, float yaw, float pitch) {
        return new Loc(world(level), position.x, position.y, position.z, yaw, pitch);
    }

    public static Id id(ResourceLocation identifier) {
        return ModConversions.id(identifier);
    }

    public static ResourceLocation identifier(Id id) {
        return ModConversions.identifier(id);
    }

    public static ServerPlayer player(PlayerRef player) {
        return ((ModPlayerRef) player).handle();
    }

    public static Entity entity(EntityRef entity) {
        return ((ModEntityRef) entity).handle();
    }

    public static LivingEntity living(LivingRef entity) {
        return (LivingEntity) ((ModEntityRef) entity).handle();
    }

    public static ServerLevel world(WorldRef world) {
        ModWorldRef ref = (ModWorldRef) world;
        return ref.isLoaded() ? ref.level() : null;
    }

    public static ItemStack item(ItemRef item) {
        return item == null ? ItemStack.EMPTY : ((ModItemRef) item).stack();
    }

    public static BlockState state(BlockStateRef state) {
        return ((ModBlockStateRef) state).state();
    }

    public static net.minecraft.core.BlockPos pos(BlockPos pos) {
        return ModConversions.pos(pos);
    }

    public static BlockPos pos(net.minecraft.core.BlockPos pos) {
        return ModConversions.pos(pos);
    }

    public static Vec3 vec(art.arcane.adapt.platform.value.Vec3 vector) {
        return ModConversions.vec(vector);
    }

    public static art.arcane.adapt.platform.value.Vec3 vec(Vec3 vector) {
        return ModConversions.vec(vector);
    }

    /** Cached player handle, or null when the player is not tracked. Any thread. */
    public static PlayerRef cachedPlayer(UUID id) {
        return PLAYERS.get(id);
    }

    /** Tracked player handles. Any thread. */
    public static Collection<PlayerRef> cachedPlayers() {
        return List.copyOf(PLAYERS.values());
    }

    /** Brings the player cache in line with the player list. Server thread. */
    static void syncPlayers(List<ServerPlayer> online) {
        for (ServerPlayer player : online) {
            player(player);
        }
        if (PLAYERS.size() != online.size()) {
            PLAYERS.values().removeIf((ModPlayerRef ref) -> !ref.isOnline());
        }
    }

    static void clear() {
        PLAYERS.clear();
        WORLDS.clear();
    }

    private static EntityRef display(Display display) {
        ModDisplayFactory factory = ModHosts.displays();
        return factory == null ? new ModEntityRef(display) : factory.wrap(display);
    }
}
