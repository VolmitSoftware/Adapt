package art.arcane.adapt.gameplay;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class ArchitectFixtures {
    private ArchitectFixtures() {
    }

    public static boolean stage(String name, World arena, Player actor) {
        if (name.equals("build-foundation")) {
            arena.getBlockAt(0, 104, 0).setType(Material.STONE, false);
            actor.teleport(new Location(arena, 0.5, 105, 0.5));
            return true;
        }
        if (!name.equals("build-bridge") && !name.equals("build-refill") && !name.equals("build-demolition")) {
            return false;
        }
        arena.getBlockAt(2, 101, 2).setType(Material.STONE, false);
        arena.getBlockAt(2, 100, 1).setType(Material.AIR, false);
        actor.getInventory().setHeldItemSlot(0);
        actor.getInventory().setItem(0, new ItemStack(Material.STONE, name.equals("build-refill") ? 1 : 4));
        if (name.equals("build-refill")) {
            actor.getInventory().setItem(9, new ItemStack(Material.STONE, 32));
        }
        return true;
    }
}
