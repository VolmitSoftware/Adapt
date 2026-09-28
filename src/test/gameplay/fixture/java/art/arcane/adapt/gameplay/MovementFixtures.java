package art.arcane.adapt.gameplay;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public final class MovementFixtures {
    private MovementFixtures() {
    }

    public static boolean stage(String name, World arena, Player actor) {
        switch (name) {
            case "move-runway" -> runway(arena);
            case "move-mace" -> actor.getInventory().addItem(new ItemStack(Material.MACE));
            case "move-spear" -> actor.getInventory().addItem(new ItemStack(Material.IRON_SPEAR));
            case "move-smoke" -> actor.getInventory().addItem(new ItemStack(Material.GUNPOWDER, 2));
            case "move-snatch" -> {
                Item item = arena.dropItem(new Location(arena, 0.5, 100.2, -4.5), new ItemStack(Material.DIAMOND));
                item.setVelocity(new Vector());
                item.setPickupDelay(200);
            }
            case "move-umbral" -> {
                actor.setFoodLevel(10);
                actor.setSaturation(0);
                actor.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD));
                Cow cow = arena.spawn(new Location(arena, 0.5, 100, 2.5), Cow.class);
                cow.setAI(false);
                cow.setHealth(1);
            }
            case "move-pressure" -> {
                waterChannel(arena, 45, -4, 4);
                actor.teleport(new Location(arena, 0.5, 45, 0.5));
            }
            case "move-hydro" -> waterChannel(arena, 100, -30, 5);
            case "move-fall", "move-meteor" -> {
                waterChannel(arena, 100, -20, 3);
                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        arena.getBlockAt(x, 119, z).setType(Material.STONE, false);
                    }
                }
                actor.teleport(new Location(arena, 0.5, 120, 0.5));
                if (name.equals("move-meteor")) {
                    actor.getInventory().addItem(new ItemStack(Material.MACE));
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private static void runway(World arena) {
        for (int x = -3; x <= 3; x++) {
            for (int z = -75; z <= 5; z++) {
                arena.getBlockAt(x, 99, z).setType(Material.STONE, false);
                for (int y = 100; y <= 104; y++) {
                    arena.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        }
    }

    private static void waterChannel(World arena, int baseY, int startZ, int endZ) {
        for (int x = -4; x <= 4; x++) {
            for (int z = startZ - 1; z <= endZ + 1; z++) {
                arena.getBlockAt(x, baseY - 1, z).setType(Material.STONE, false);
                for (int y = baseY; y <= baseY + 3; y++) {
                    boolean wall = x == -4 || x == 4 || z == startZ - 1 || z == endZ + 1;
                    arena.getBlockAt(x, y, z).setType(wall ? Material.GLASS : Material.WATER, false);
                }
            }
        }
    }
}
