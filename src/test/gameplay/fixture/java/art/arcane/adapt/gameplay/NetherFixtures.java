package art.arcane.adapt.gameplay;

import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.entity.WitherSkull;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.Random;

public final class NetherFixtures implements Listener {
    private static World nether;
    private static WitherSkull skull;
    private static Location skullStart;
    private static double skullTravel;

    public NetherFixtures() {
    }

    public static boolean stage(String name, World arena, Player actor, Player opponent) {
        if (!name.startsWith("nether-qa-")) return false;
        skull = null;
        skullStart = null;
        skullTravel = 0;
        World world = world(arena);
        for (Entity entity : world.getEntities()) if (!(entity instanceof Player)) entity.remove();
        for (int x = -10; x <= 10; x++) {
            for (int z = -20; z <= 15; z++) {
                world.getBlockAt(x, 98, z).setType(Material.OBSIDIAN, false);
                world.getBlockAt(x, 99, z).setType(Material.OBSIDIAN, false);
                for (int y = 100; y <= 105; y++) world.getBlockAt(x, y, z).setType(Material.AIR, false);
            }
        }
        actor.setFoodLevel(17);
        actor.setSaturation(0);
        actor.teleport(new Location(world, 0.5, 100, 0.5));
        opponent.teleport(new Location(world, 8.5, 100, 8.5));
        switch (name) {
            case "nether-qa-ash" -> {
                world.getBlockAt(0, 99, -1).setType(Material.SOUL_SOIL, false);
                world.getBlockAt(0, 100, -1).setType(Material.SOUL_FIRE, false);
            }
            case "nether-qa-feast" -> {
                actor.setFoodLevel(10);
                actor.getInventory().addItem(new ItemStack(Material.CRIMSON_FUNGUS, 2));
            }
            case "nether-qa-ward" -> {
                world.getBlockAt(0, 100, -3).setType(Material.TNT, false);
                actor.getInventory().addItem(new ItemStack(Material.FLINT_AND_STEEL));
            }
            case "nether-qa-lava" -> {
                for (int x = -2; x <= 2; x++) {
                    for (int z = -15; z < 0; z++) world.getBlockAt(x, 99, z).setType(Material.LAVA, false);
                }
            }
            case "nether-qa-magma" -> {
                world.getBlockAt(0, 99, -1).setType(Material.NETHERRACK, false);
                world.getBlockAt(0, 100, -1).setType(Material.FIRE, false);
                opponent.teleport(new Location(world, 2.5, 100, -0.5));
            }
            case "nether-qa-mason" -> {
                for (int x = -2; x <= 2; x++) {
                    for (int z = -3; z <= -1; z++) world.getBlockAt(x, 100, z).setType(Material.BLACKSTONE, false);
                }
                actor.getInventory().addItem(new ItemStack(Material.WOODEN_PICKAXE));
            }
            case "nether-qa-skull" -> {
                actor.getInventory().addItem(new ItemStack(Material.WITHER_SKELETON_SKULL, 2));
                for (int x = -3; x <= 3; x++) {
                    for (int y = 100; y <= 104; y++) world.getBlockAt(x, y, -10).setType(Material.STONE, false);
                }
            }
            case "nether-qa-soul" -> {
                for (int x = -2; x <= 2; x++) {
                    for (int z = -15; z <= 1; z++) world.getBlockAt(x, 99, z).setType(Material.SOUL_SOIL, false);
                }
            }
            case "nether-qa-wither" -> {
                world.getBlockAt(0, 99, -1).setType(Material.DIRT, false);
                world.getBlockAt(0, 100, -1).setType(Material.WITHER_ROSE, false);
                actor.getInventory().setHelmet(new ItemStack(Material.NETHERITE_HELMET));
                actor.getInventory().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
                actor.getInventory().setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
                actor.getInventory().setBoots(new ItemStack(Material.NETHERITE_BOOTS));
            }
            case "nether-qa-harvest" -> {
                actor.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD));
                WitherSkeleton skeleton = world.spawn(new Location(world, 0.5, 100, -1.5), WitherSkeleton.class);
                skeleton.setAI(false);
                skeleton.setHealth(1);
            }
            default -> throw new IllegalArgumentException("Unknown Nether fixture: " + name);
        }
        return true;
    }

    public static void cleanup() {
        if (nether == null) return;
        if (!nether.getPlayers().isEmpty()) throw new IllegalStateException("Return players before unloading the Nether fixture");
        if (!Bukkit.unloadWorld(nether, false)) throw new IllegalStateException("Could not unload Nether fixture " + nether.getName());
        nether = null;
    }

    public static JsonObject snapshot(Player player) {
        JsonObject result = new JsonObject();
        result.addProperty("environment", player.getWorld().getEnvironment().name());
        result.addProperty("fireTicks", player.getFireTicks());
        if (skull != null && skull.isValid()) skullTravel = Math.max(skullTravel, skull.getLocation().distance(skullStart));
        result.addProperty("skullTravel", skullTravel);
        return result;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void launched(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof WitherSkull launched) || launched.getWorld() != nether) return;
        skull = launched;
        skullStart = launched.getLocation();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void hit(ProjectileHitEvent event) {
        if (event.getEntity() == skull && skullStart != null) skullTravel = Math.max(skullTravel, event.getEntity().getLocation().distance(skullStart));
    }

    private static World world(World arena) {
        if (nether != null) return nether;
        String name = arena.getName() + "_nether";
        if (Bukkit.getWorld(name) != null) throw new IllegalStateException("Nether fixture world already exists: " + name);
        nether = Objects.requireNonNull(new WorldCreator(name).environment(World.Environment.NETHER)
                .generator(new EmptyGenerator()).generateStructures(false).createWorld());
        nether.setPVP(true);
        nether.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        nether.setGameRule(GameRule.NATURAL_REGENERATION, false);
        return nether;
    }

    private static final class EmptyGenerator extends ChunkGenerator {
        @Override
        public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome) {
            return createChunkData(world);
        }
    }
}
