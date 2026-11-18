package art.arcane.adapt.gameplay;

import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.Environment;
import art.arcane.adapt.platform.world.WorldRef;

public final class MovementFixtures {
    private static final int PRESSURE_DEPTH = 18;

    private MovementFixtures() {
    }

    public static boolean stage(String name, WorldRef arena, PlayerRef actor) {
        switch (name) {
            case "move-runway" -> runway(arena);
            case "move-mace" -> Fixtures.give(actor, "mace", 1);
            case "move-spear" -> Fixtures.give(actor, "iron_spear", 1);
            case "move-smoke" -> Fixtures.give(actor, "gunpowder", 2);
            case "move-snatch" -> {
                ItemEntityRef item = arena.dropItem(new Vec3(0.5, 100.2, -4.5), Fixtures.item("diamond"));
                item.setVelocity(Vec3.ZERO);
                item.setPickupDelay(200);
            }
            case "move-umbral" -> {
                actor.setFoodLevel(10);
                actor.setSaturation(0);
                Fixtures.give(actor, "wooden_sword", 1);
                LivingRef cow = Fixtures.spawnStill(arena, "cow", 0.5, 100, 2.5);
                cow.setHealth(1);
            }
            case "move-pressure" -> {
                Loc site = pressureSite(arena);
                waterChannel(site.world(), site.blockY(), -4, 4);
                Fixtures.teleport(actor, site);
            }
            case "move-hydro" -> waterChannel(arena, 100, -30, 5);
            case "move-fall", "move-meteor" -> {
                waterChannel(arena, 100, -20, 3);
                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        Fixtures.set(arena, x, 119, z, "stone");
                    }
                }
                Fixtures.teleport(actor, arena, 0.5, 120, 0.5);
                if (name.equals("move-meteor")) {
                    Fixtures.give(actor, "mace", 1);
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private static void runway(WorldRef arena) {
        for (int x = -3; x <= 3; x++) {
            for (int z = -75; z <= 5; z++) {
                Fixtures.set(arena, x, 99, z, "stone");
                for (int y = 100; y <= 104; y++) {
                    Fixtures.set(arena, x, y, z, "air");
                }
            }
        }
    }

    /**
     * Pressure Diver measures depth below the world's sea level. A flat overworld arena (Folia, modded) sits at
     * sea level -63 with no room below it, so the channel moves to the generated exploration dimension there.
     */
    static Loc pressureSite(WorldRef arena) {
        WorldRef world = arena.seaLevel() - PRESSURE_DEPTH > arena.minHeight() + 1
            ? arena
            : Fixtures.generatedWorld(arena.name() + "_discovery", 424242L, Environment.NORMAL);
        return Fixtures.loc(world, 0.5, world.seaLevel() - PRESSURE_DEPTH, 0.5);
    }

    private static void waterChannel(WorldRef arena, int baseY, int startZ, int endZ) {
        for (int x = -4; x <= 4; x++) {
            for (int z = startZ - 1; z <= endZ + 1; z++) {
                Fixtures.set(arena, x, baseY - 1, z, "stone");
                for (int y = baseY; y <= baseY + 3; y++) {
                    boolean wall = x == -4 || x == 4 || z == startZ - 1 || z == endZ + 1;
                    Fixtures.set(arena, x, y, z, wall ? "glass" : "water");
                }
            }
        }
    }
}
