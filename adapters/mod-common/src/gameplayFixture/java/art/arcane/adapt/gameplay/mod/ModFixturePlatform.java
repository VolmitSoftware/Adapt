package art.arcane.adapt.gameplay.mod;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.gameplay.FixturePlatform;
import art.arcane.adapt.gameplay.Fixtures;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.mod.platform.data.AdaptSunBurn;
import art.arcane.adapt.mod.platform.entity.ModPlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.Environment;
import art.arcane.adapt.platform.world.WorldRef;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.projectile.FishingHook;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public final class ModFixturePlatform implements FixturePlatform {
    static final String NAMESPACE = "adaptqa";

    private final String loader;
    private final MinecraftServer server;

    public ModFixturePlatform(String loader, MinecraftServer server) {
        this.loader = loader;
        this.server = server;
    }

    @Override
    public String name() {
        return loader;
    }

    @Override
    public void requireIsolatedServer() {
        String source;
        try {
            source = Files.readString(Path.of(".server-source"));
        } catch (IOException failure) {
            throw new IllegalStateException("Fixture requires a Multiplexor instance with .server-source", failure);
        }
        if (server.usesAuthentication() || !"127.0.0.1".equals(server.getLocalIp())
                || source.lines().noneMatch("isolated=true"::equals)) {
            throw new IllegalStateException("Fixture requires an isolated offline loopback Multiplexor instance");
        }
    }

    @Override
    public WorldRef createArena(String name) {
        WorldRef arena = dimension(Id.minecraft("overworld"));
        Fixtures.commandIn(arena, "time set 6000");
        Fixtures.gameRule(arena, "spawn_mobs", "false");
        Fixtures.gameRule(arena, "pvp", "true");
        // Players of earlier trials wait far outside the arena, as they stay in the main world on Bukkit.
        for (PlayerRef player : Fixtures.host().onlinePlayers()) {
            Fixtures.teleport(player, Fixtures.loc(arena, 0.5, -60, -4095.5));
        }
        return arena;
    }

    @Override
    public WorldRef generatedWorld(String name, long seed, Environment environment) {
        return switch (environment) {
            case NORMAL -> dimension(Id.of(NAMESPACE, "exploration"));
            case NETHER -> dimension(Id.minecraft("the_nether"));
            default -> throw new IllegalArgumentException("No fixture dimension for " + environment);
        };
    }

    @Override
    public void unloadWorld(WorldRef world) {
    }

    @Override
    public Set<String> tags(EntityRef entity) {
        return Set.copyOf(((Entity) entity.unwrap()).entityTags());
    }

    @Override
    public void addTag(EntityRef entity, String tag) {
        ((Entity) entity.unwrap()).addTag(tag);
    }

    @Override
    public void sendPlain(PlayerRef player, String text) {
        ((ServerPlayer) player.unwrap()).sendSystemMessage(Component.literal(text));
    }

    @Override
    public double tps() {
        long nanos = server.getAverageTickTimeNanos();
        return nanos <= 0L ? 20.0D : Math.min(20.0D, 1.0E9D / nanos);
    }

    @Override
    public boolean integrationStage(String name, WorldRef world, PlayerRef actor, PlayerRef opponent) {
        if (name.startsWith("integration-")) {
            throw new IllegalStateException("Iris and Gloss integrations are Bukkit plugins; " + name + " is unavailable on " + loader);
        }
        return false;
    }

    @Override
    public JsonObject integrationSnapshot(PlayerRef player) {
        JsonObject result = new JsonObject();
        result.addProperty("world", player.world().name());
        return result;
    }

    @Override
    public boolean nativeOwns(EntityRef entity) {
        return server.isSameThread();
    }

    @Override
    public JsonObject fishingHookState(EntityRef hook) {
        FishingHook fishingHook = (FishingHook) hook.unwrap();
        JsonObject result = new JsonObject();
        String state = String.valueOf(field(fishingHook, "currentState"));
        result.addProperty("state", switch (state) {
            case "HOOKED_IN_ENTITY" -> "HOOKED_ENTITY";
            case "BOBBING" -> "BOBBING";
            default -> "UNHOOKED";
        });
        result.addProperty("openWater", fishingHook.isOpenWaterFishing());
        result.addProperty("waitTime", (Integer) field(fishingHook, "timeUntilLured"));
        result.addProperty("timeUntilBite", (Integer) field(fishingHook, "timeUntilHooked"));
        return result;
    }

    @Override
    public Loc compassTarget(PlayerRef player) {
        Loc target = ModPlayerRef.compassTarget(player.uuid());
        if (target != null) {
            return target;
        }
        WorldRef overworld = AdaptCore.platform().host().world(Id.minecraft("overworld"));
        return overworld == null ? player.location() : overworld.spawnLocation();
    }

    @Override
    public void setOwner(EntityRef pet, PlayerRef owner) {
        Entity entity = (Entity) pet.unwrap();
        ServerPlayer player = (ServerPlayer) owner.unwrap();
        if (entity instanceof TamableAnimal tamable) {
            tamable.setTame(true, true);
            tamable.setOwner(player);
            return;
        }
        try {
            entity.getClass().getMethod("setTamed", boolean.class).invoke(entity, true);
            entity.getClass().getMethod("setOwner", LivingEntity.class).invoke(entity, player);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot tame " + entity.getType(), failure);
        }
    }

    @Override
    public java.util.OptionalInt customModelData(art.arcane.adapt.platform.item.ItemRef item) {
        net.minecraft.world.item.component.CustomModelData data = art.arcane.adapt.mod.platform.ModRefs.item(item)
                .get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
        return data == null || data.floats().isEmpty() ? java.util.OptionalInt.empty() : java.util.OptionalInt.of((int) (float) data.floats().getFirst());
    }

    @Override
    public String openMenuTitle(PlayerRef player) {
        art.arcane.adapt.platform.menu.MenuSession session = AdaptCore.platform().menus().current(player);
        return session == null ? "" : art.arcane.adapt.localization.MarkupPort.current().legacy(session.menu().title());
    }

    @Override
    public Runnable grantPermission(PlayerRef player, String node) {
        try {
            Field field = art.arcane.adapt.mod.platform.ModPermissionHost.class.getDeclaredField("bridge");
            field.setAccessible(true);
            art.arcane.adapt.mod.platform.PermissionBridge previous = (art.arcane.adapt.mod.platform.PermissionBridge) field.get(null);
            java.util.UUID id = player.uuid();
            art.arcane.adapt.mod.platform.ModPermissionHost.bridge((ServerPlayer handle, String checked) -> {
                if (handle.getUUID().equals(id) && checked.equals(node)) {
                    return Boolean.TRUE;
                }
                return previous == null ? null : previous.check(handle, checked);
            });
            return () -> art.arcane.adapt.mod.platform.ModPermissionHost.bridge(previous);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot grant fixture permission " + node, failure);
        }
    }

    @Override
    public void restoreAir(PlayerRef player) {
        ServerPlayer handle = (ServerPlayer) player.unwrap();
        handle.setAirSupply(handle.getMaxAirSupply());
    }

    @Override
    public void forgetCombat(PlayerRef player) {
        ServerPlayer handle = (ServerPlayer) player.unwrap();
        handle.setLastHurtMob(null);
        handle.setLastHurtByMob(null);
    }

    @Override
    public void setSneaking(PlayerRef player, boolean sneaking) {
        ((ServerPlayer) player.unwrap()).setShiftKeyDown(sneaking);
    }

    @Override
    public void meleeAttack(MobRef attacker, LivingRef target) {
        Mob mob = (Mob) attacker.unwrap();
        mob.doHurtTarget((ServerLevel) mob.level(), (Entity) target.unwrap());
    }

    @Override
    public boolean burnsInDaylight(MobRef mob) {
        return ((AdaptSunBurn) mob.unwrap()).adapt$burnsInDaylight();
    }

    private static Object field(FishingHook hook, String name) {
        try {
            Field field = FishingHook.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(hook);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot read FishingHook." + name, failure);
        }
    }

    private static WorldRef dimension(Id key) {
        WorldRef world = AdaptCore.platform().host().world(key);
        if (world == null) {
            throw new IllegalStateException("Fixture dimension " + key + " is missing; install the adaptqa world datapack");
        }
        return world;
    }
}
