package art.arcane.adapt.gameplay;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.Environment;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.generator.ChunkGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

public final class BukkitFixturePlatform implements FixturePlatform {
    private static final Set<String> SUPPORTED_SERVERS = Set.of("Paper", "Leaf", "Folia");
    private static final int UNLOAD_ATTEMPTS = 40;

    @Override
    public String name() {
        return Bukkit.getName().toLowerCase(Locale.ROOT);
    }

    @Override
    public void requireIsolatedServer() {
        String source;
        try {
            source = Files.readString(Path.of(".server-source"));
        } catch (IOException failure) {
            throw new IllegalStateException("Fixture requires a Multiplexor instance with .server-source", failure);
        }
        if (!SUPPORTED_SERVERS.contains(Bukkit.getName()) || Bukkit.getOnlineMode() || !"127.0.0.1".equals(Bukkit.getIp())
                || source.lines().noneMatch("isolated=true"::equals)) {
            throw new IllegalStateException("Fixture requires an isolated offline loopback Paper, Leaf or Folia Multiplexor instance");
        }
    }

    @Override
    public WorldRef createArena(String name) {
        if (Tasks.isFoliaThreading()) {
            WorldRef overworld = BukkitRefs.world(Bukkit.getWorlds().getFirst());
            Fixtures.commandIn(overworld, "time set 6000");
            Fixtures.gameRule(overworld, "spawn_mobs", "false");
            Fixtures.gameRule(overworld, "pvp", "true");
            return overworld;
        }
        World arena = Objects.requireNonNull(new WorldCreator(name).generator(new EmptyGenerator()).generateStructures(false).createWorld());
        arena.setTime(6000);
        arena.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        arena.setPVP(true);
        arena.setSpawnLocation(0, 100, 0);
        return BukkitRefs.world(arena);
    }

    @Override
    public WorldRef generatedWorld(String name, long seed, Environment environment) {
        if (Tasks.isFoliaThreading()) {
            NamespacedKey key = switch (environment) {
                case NORMAL -> new NamespacedKey("adaptqa", "exploration");
                case NETHER -> NamespacedKey.minecraft("the_nether");
                default -> throw new IllegalArgumentException("No fixture dimension for " + environment);
            };
            return BukkitRefs.world(Objects.requireNonNull(Bukkit.getWorld(key), "Folia fixture dimension missing: " + key));
        }
        if (Bukkit.getWorld(name) != null) {
            throw new IllegalStateException("Fixture world already exists: " + name);
        }
        World world = switch (environment) {
            case NORMAL -> new WorldCreator(name).seed(seed).generateStructures(true).createWorld();
            case NETHER -> new WorldCreator(name).environment(World.Environment.NETHER)
                    .generator(new EmptyGenerator()).generateStructures(false).createWorld();
            default -> throw new IllegalArgumentException("No fixture world for " + environment);
        };
        return BukkitRefs.world(Objects.requireNonNull(world));
    }

    @Override
    public void unloadWorld(WorldRef world) {
        if (Tasks.isFoliaThreading()) {
            return;
        }
        unloadWhenEmpty((World) world.unwrap(), UNLOAD_ATTEMPTS);
    }

    private static void unloadWhenEmpty(World world, int attempts) {
        if (!world.getPlayers().isEmpty()) {
            if (attempts <= 0) {
                throw new IllegalStateException("Return players before unloading fixture world " + world.getName());
            }
            Tasks.s(() -> unloadWhenEmpty(world, attempts - 1), 1);
            return;
        }
        if (!Bukkit.unloadWorld(world, false)) {
            throw new IllegalStateException("Could not unload fixture world " + world.getName());
        }
    }

    @Override
    public Set<String> tags(EntityRef entity) {
        return Set.copyOf(((Entity) entity.unwrap()).getScoreboardTags());
    }

    @Override
    public void addTag(EntityRef entity, String tag) {
        ((Entity) entity.unwrap()).addScoreboardTag(tag);
    }

    @Override
    public void sendPlain(PlayerRef player, String text) {
        ((Player) player.unwrap()).sendMessage(text);
    }

    @Override
    public double tps() {
        return Bukkit.getTPS()[0];
    }

    @Override
    public boolean integrationStage(String name, WorldRef world, PlayerRef actor, PlayerRef opponent) {
        return IntegrationFixtures.stage(name, (World) world.unwrap(), (Player) actor.unwrap(), (Player) opponent.unwrap());
    }

    @Override
    public Loc integrationSite(String name) {
        World world = IntegrationFixtures.siteWorld(name);
        return world == null ? null : BukkitRefs.loc(new Location(world, 0.5, 100, 0.5));
    }

    @Override
    public JsonObject integrationSnapshot(PlayerRef player) {
        return IntegrationFixtures.snapshot((Player) player.unwrap());
    }

    @Override
    public boolean nativeOwns(EntityRef entity) {
        return Bukkit.isOwnedByCurrentRegion((Entity) entity.unwrap());
    }

    @Override
    public JsonObject fishingHookState(EntityRef hook) {
        FishHook fishHook = (FishHook) hook.unwrap();
        JsonObject result = new JsonObject();
        result.addProperty("state", fishHook.getState().name());
        result.addProperty("openWater", fishHook.isInOpenWater());
        result.addProperty("waitTime", fishHook.getWaitTime());
        result.addProperty("timeUntilBite", FixtureVersion.timeUntilBite(fishHook));
        return result;
    }

    @Override
    public Loc compassTarget(PlayerRef player) {
        return BukkitRefs.loc(((Player) player.unwrap()).getCompassTarget());
    }

    @Override
    public void restoreAir(PlayerRef player) {
        Player bukkit = BukkitRefs.player(player);
        bukkit.setRemainingAir(bukkit.getMaximumAir());
    }

    @Override
    public void forgetCombat(PlayerRef player) {
        // Paper has no API for the vanilla combat memory; the fixture reaches the server player directly.
        Player bukkit = BukkitRefs.player(player);
        try {
            Object handle = bukkit.getClass().getMethod("getHandle").invoke(bukkit);
            Class<?> living = Class.forName(FixtureVersion.LIVING_ENTITY_CLASS, false, handle.getClass().getClassLoader());
            invokeWithNull(handle, living.getDeclaredMethod(FixtureVersion.SET_LAST_HURT_MOB, living.getSuperclass()));
            invokeWithNull(handle, living.getDeclaredMethod(FixtureVersion.SET_LAST_HURT_BY_MOB, living));
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot clear the combat memory of " + bukkit.getName(), failure);
        }
    }

    private static void invokeWithNull(Object handle, java.lang.reflect.Method setter) throws ReflectiveOperationException {
        setter.setAccessible(true);
        setter.invoke(handle, (Object) null);
    }

    @Override
    public EntityRef summon(WorldRef world, String type, double x, double y, double z, String nbt) {
        if (!Tasks.isFoliaThreading() || nbt.isEmpty()) {
            return FixturePlatform.super.summon(world, type, x, y, z, nbt);
        }
        Location at = new Location((World) world.unwrap(), x, y, z);
        String fields = nbt.substring(1, nbt.length() - 1).trim();
        String snapshot = "{id:\"" + Fixtures.id(type) + "\"" + (fields.isEmpty() ? "" : "," + fields) + "}";
        return BukkitRefs.entity(FixtureVersion.summonSnapshot(snapshot, at));
    }

    @Override
    public void teleport(EntityRef entity, Loc destination) {
        if (Tasks.isFoliaThreading()) {
            FixturePlatform.super.teleport(entity, destination);
            return;
        }
        ((Entity) entity.unwrap()).teleport(BukkitRefs.location(destination), PlayerTeleportEvent.TeleportCause.PLUGIN);
    }

    @Override
    public void setOwner(EntityRef pet, PlayerRef owner) {
        ((Tameable) pet.unwrap()).setOwner((Player) owner.unwrap());
    }

    @Override
    public java.util.OptionalInt customModelData(art.arcane.adapt.platform.item.ItemRef item) {
        org.bukkit.inventory.meta.ItemMeta meta = BukkitRefs.item(item).getItemMeta();
        return meta != null && meta.hasCustomModelData() ? java.util.OptionalInt.of(meta.getCustomModelData()) : java.util.OptionalInt.empty();
    }

    @Override
    public String openMenuTitle(PlayerRef player) {
        return ((Player) player.unwrap()).getOpenInventory().getTitle();
    }

    @Override
    public Runnable grantPermission(PlayerRef player, String node) {
        Player handle = (Player) player.unwrap();
        org.bukkit.permissions.PermissionAttachment attachment = handle.addAttachment(art.arcane.adapt.Adapt.instance, node, true);
        return () -> handle.removeAttachment(attachment);
    }

    @Override
    public void setSneaking(PlayerRef player, boolean sneaking) {
        ((Player) player.unwrap()).setSneaking(sneaking);
    }

    @Override
    public void meleeAttack(MobRef attacker, LivingRef target) {
        ((Mob) attacker.unwrap()).attack((Entity) target.unwrap());
    }

    @Override
    public boolean burnsInDaylight(MobRef mob) {
        return mob.unwrap() instanceof AbstractSkeleton skeleton && skeleton.shouldBurnInDay();
    }

    private static final class EmptyGenerator extends ChunkGenerator {
        @Override
        public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome) {
            return createChunkData(world);
        }

    }
}
