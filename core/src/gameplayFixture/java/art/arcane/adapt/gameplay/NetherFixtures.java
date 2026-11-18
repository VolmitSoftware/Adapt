package art.arcane.adapt.gameplay;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.ProjectileHitEvent;
import art.arcane.adapt.platform.event.entity.ProjectileLaunchEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.Environment;
import art.arcane.adapt.platform.world.WorldRef;
import com.google.gson.JsonObject;

import java.util.UUID;

public final class NetherFixtures {
    private static final Id WITHER_SKULL = Id.minecraft("wither_skull");
    private static WorldRef nether;
    private static UUID skull;
    private static Loc skullStart;
    private static double skullTravel;

    private NetherFixtures() {
    }

    public static void install() {
        Fixtures.register(new NetherFixtures());
    }

    static boolean usesNether(String name) {
        return name.startsWith("nether-qa-");
    }

    public static boolean stage(String name, WorldRef arena, PlayerRef actor, PlayerRef opponent) {
        if (!usesNether(name)) return false;
        skull = null;
        skullStart = null;
        skullTravel = 0;
        WorldRef world = world(arena);
        for (EntityRef entity : world.entities()) if (!(entity instanceof PlayerRef)) entity.remove();
        for (int x = -10; x <= 10; x++) {
            for (int z = -20; z <= 15; z++) {
                Fixtures.set(world, x, 98, z, "obsidian");
                Fixtures.set(world, x, 99, z, "obsidian");
                for (int y = 100; y <= 105; y++) Fixtures.set(world, x, y, z, "air");
            }
        }
        actor.setFoodLevel(17);
        actor.setSaturation(0);
        Fixtures.teleport(actor, world, 0.5, 100, 0.5);
        Fixtures.teleport(opponent, world, 8.5, 100, 8.5);
        switch (name) {
            case "nether-qa-ash" -> {
                for (int x = -1; x <= 1; x++) {
                    for (int z = -8; z < 0; z++) {
                        Fixtures.set(world, x, 99, z, "soul_soil");
                        Fixtures.set(world, x, 100, z, "soul_fire");
                    }
                }
            }
            case "nether-qa-feast" -> {
                actor.setFoodLevel(10);
                Fixtures.give(actor, "crimson_fungus", 2);
            }
            case "nether-qa-ward" -> {
                Fixtures.set(world, 0, 100, -3, "tnt");
                Fixtures.give(actor, "flint_and_steel", 1);
            }
            case "nether-qa-lava" -> {
                for (int x = -2; x <= 2; x++) {
                    for (int z = -15; z < 0; z++) Fixtures.set(world, x, 99, z, "lava");
                }
            }
            case "nether-qa-magma" -> {
                Fixtures.set(world, 0, 99, -1, "netherrack");
                Fixtures.set(world, 0, 100, -1, "fire");
                Fixtures.teleport(opponent, world, 2.5, 100, -0.5);
            }
            case "nether-qa-mason" -> {
                // 30 blocks: at the 40% max-level bonus chance, 15 left about 1 run in 2,000 with no bonus at all.
                for (int x = -2; x <= 2; x++) {
                    for (int z = -3; z <= -1; z++) {
                        Fixtures.set(world, x, 100, z, "blackstone");
                        Fixtures.set(world, x, 101, z, "blackstone");
                    }
                }
                Fixtures.give(actor, "wooden_pickaxe", 1);
            }
            case "nether-qa-skull" -> {
                Fixtures.give(actor, "wither_skeleton_skull", 2);
                for (int x = -3; x <= 3; x++) {
                    for (int y = 100; y <= 104; y++) Fixtures.set(world, x, y, -10, "stone");
                }
            }
            case "nether-qa-soul" -> {
                for (int x = -2; x <= 2; x++) {
                    for (int z = -15; z <= 1; z++) Fixtures.set(world, x, 99, z, "soul_soil");
                }
            }
            case "nether-qa-wither" -> {
                Fixtures.set(world, 0, 99, -1, "dirt");
                Fixtures.set(world, 0, 100, -1, "wither_rose");
                actor.inventory().setArmor(EquipmentSlot.HEAD, Fixtures.item("netherite_helmet"));
                actor.inventory().setArmor(EquipmentSlot.CHEST, Fixtures.item("netherite_chestplate"));
                actor.inventory().setArmor(EquipmentSlot.LEGS, Fixtures.item("netherite_leggings"));
                actor.inventory().setArmor(EquipmentSlot.FEET, Fixtures.item("netherite_boots"));
            }
            case "nether-qa-harvest" -> {
                Fixtures.give(actor, "wooden_sword", 1);
                LivingRef skeleton = Fixtures.spawnStill(world, "wither_skeleton", 0.5, 100, -1.5);
                skeleton.setHealth(1);
            }
            default -> throw new IllegalArgumentException("Unknown Nether fixture: " + name);
        }
        return true;
    }

    public static void cleanup() {
        if (nether == null) return;
        if (!nether.players().isEmpty()) throw new IllegalStateException("Return players before unloading the Nether fixture");
        Fixtures.unloadGeneratedWorld(nether);
        nether = null;
    }

    public static JsonObject snapshot(PlayerRef player) {
        JsonObject result = new JsonObject();
        result.addProperty("environment", player.world().environment().name());
        result.addProperty("fireTicks", player.fireTicks());
        EntityRef current = skull == null ? null : Fixtures.entity(skull);
        if (current != null && current.isValid()) skullTravel = Math.max(skullTravel, current.location().distance(skullStart));
        result.addProperty("skullTravel", skullTravel);
        return result;
    }

    @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void launched(ProjectileLaunchEvent event) {
        ProjectileRef launched = event.projectile();
        if (nether == null || !launched.type().equals(WITHER_SKULL) || !launched.world().uid().equals(nether.uid())) return;
        skull = launched.uuid();
        skullStart = launched.location();
    }

    @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void hit(ProjectileHitEvent event) {
        if (skull != null && event.projectile().uuid().equals(skull) && skullStart != null) {
            skullTravel = Math.max(skullTravel, event.projectile().location().distance(skullStart));
        }
    }

    static WorldRef world(WorldRef arena) {
        if (nether != null) return nether;
        nether = Fixtures.generatedWorld(arena.name() + "_nether", arena.seed(), Environment.NETHER);
        Fixtures.gameRule(nether, "pvp", "true");
        Fixtures.gameRule(nether, "spawn_mobs", "false");
        Fixtures.gameRule(nether, "natural_health_regeneration", "false");
        return nether;
    }
}
