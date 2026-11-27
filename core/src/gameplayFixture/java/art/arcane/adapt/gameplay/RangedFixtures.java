package art.arcane.adapt.gameplay;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.ProjectileLaunchEvent;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.WorldRef;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RangedFixtures {
    private static final Set<String> STAGES = Set.of(
            "ranged-dual-target", "ranged-collection", "ranged-fetch",
            "adapt:hunter/big_game", "hunter-low-health", "hunter-predator");
    private static final Map<UUID, JsonObject> LAUNCHES = new HashMap<>();
    private static final Map<UUID, Double> INITIAL_SPEEDS = new HashMap<>();
    private static long launchSequence;

    private RangedFixtures() {
    }

    public static void install() {
        Fixtures.register(new RangedFixtures());
    }

    public static boolean stage(String name, WorldRef world, PlayerRef actor, PlayerRef opponent) {
        if (!STAGES.contains(name)) {
            return false;
        }
        LAUNCHES.remove(actor.uuid());
        actor.setFoodLevel(10);
        actor.setSaturation(0);
        opponent.setFoodLevel(10);
        opponent.setSaturation(0);
        Fixtures.teleport(opponent, world, 0.5, 100, 6.5);
        switch (name) {
            case "ranged-dual-target" -> {
                equipBow(actor);
                cow(world, "first", 4.5, 10);
                cow(world, "second", 8.5, 10);
            }
            case "ranged-collection" -> {
                equipBow(actor);
                cow(world, "collection", 8.5, 1);
            }
            case "ranged-fetch" -> {
                equipBow(actor);
                for (int y = 100; y <= 103; y++) {
                    for (int z = -1; z <= 1; z++) {
                        Fixtures.set(world, 8, y, z, "stone");
                    }
                }
                world.dropItem(new Vec3(7.5, 100.1, 0.5), Fixtures.item("diamond", 3)).setPickupDelay(0);
            }
            case "adapt:hunter/big_game" -> {
                Fixtures.give(actor, "wooden_sword", 1);
                LivingRef golem = Fixtures.spawnStill(world, "iron_golem", 2.5, 100, 0.5);
                Fixtures.tag(golem, "adapt-qa-target-bigGame");
            }
            case "hunter-low-health" -> {
                actor.setHealth(6);
                Fixtures.give(actor, "wooden_sword", 1);
                Fixtures.teleport(opponent, world, 2.5, 100, 0.5);
            }
            case "hunter-predator" -> Fixtures.teleport(opponent, world, 2.5, 100, 0.5);
            default -> throw new IllegalStateException("Unhandled ranged fixture stage " + name);
        }
        return true;
    }

    public static JsonObject snapshot(PlayerRef player) {
        JsonObject result = new JsonObject();
        JsonObject launch = LAUNCHES.get(player.uuid());
        if (launch != null) {
            result.add("lastLaunch", launch);
        }
        JsonObject targets = new JsonObject();
        for (EntityRef entity : player.world().entities()) {
            if (!(entity instanceof LivingRef target)) {
                continue;
            }
            for (String tag : Fixtures.tags(entity)) {
                if (!tag.startsWith("adapt-qa-target-")) {
                    continue;
                }
                JsonObject state = new JsonObject();
                state.addProperty("uuid", target.uuid().toString());
                state.addProperty("entityId", target.entityId());
                state.addProperty("health", target.health());
                state.addProperty("alive", !target.isDead());
                targets.add(tag.substring("adapt-qa-target-".length()), state);
            }
        }
        result.add("targets", targets);
        return result;
    }

    @Handle(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void beforeProjectileLaunch(ProjectileLaunchEvent event) {
        ProjectileRef projectile = event.projectile();
        if (projectile.shooter() instanceof PlayerRef player
                && Fixtures.owns(player)
                && Fixtures.isFixtureWorld(player.world())) {
            INITIAL_SPEEDS.put(projectile.uuid(), projectile.velocity().length());
        }
    }

    @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        ProjectileRef projectile = event.projectile();
        Double initialSpeed = INITIAL_SPEEDS.remove(projectile.uuid());
        if (event.isCancelled() || !(projectile.shooter() instanceof PlayerRef player)
                || !Fixtures.owns(player)
                || !Fixtures.isFixtureWorld(player.world())) {
            return;
        }
        Vec3 velocity = projectile.velocity();
        JsonObject result = new JsonObject();
        result.addProperty("sequence", ++launchSequence);
        result.addProperty("entityId", projectile.entityId());
        result.addProperty("type", projectile.type().path().toUpperCase(Locale.ROOT));
        result.addProperty("speed", velocity.length());
        if (initialSpeed != null) result.addProperty("initialSpeed", initialSpeed);
        result.addProperty("vx", velocity.x());
        result.addProperty("vy", velocity.y());
        result.addProperty("vz", velocity.z());
        if (projectile.has(EntityTrait.ARROW)) {
            result.addProperty("pierceLevel", projectile.pierceLevel());
        }
        LAUNCHES.put(player.uuid(), result);
    }

    private static void equipBow(PlayerRef player) {
        Fixtures.give(player, "bow", 1);
        Fixtures.give(player, "arrow", 16);
    }

    private static void cow(WorldRef world, String name, double x, double health) {
        LivingRef target = Fixtures.spawnStill(world, "cow", x, 100, 0.5);
        target.setHealth(health);
        Fixtures.tag(target, "adapt-qa-target-" + name);
    }
}
