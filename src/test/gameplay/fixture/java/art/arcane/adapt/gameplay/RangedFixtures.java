package art.arcane.adapt.gameplay;

import com.google.gson.JsonObject;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RangedFixtures implements Listener {
    private static final Set<String> STAGES = Set.of(
            "ranged-dual-target", "ranged-collection", "ranged-fetch",
            "hunter-big-game", "hunter-low-health", "hunter-predator");
    private static final Map<UUID, JsonObject> LAUNCHES = new HashMap<>();
    private static final Map<UUID, Double> INITIAL_SPEEDS = new HashMap<>();
    private static long launchSequence;

    private RangedFixtures() {
    }

    public static void install(JavaPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(new RangedFixtures(), plugin);
    }

    public static boolean stage(String name, World world, Player actor, Player opponent) {
        if (!STAGES.contains(name)) {
            return false;
        }
        LAUNCHES.remove(actor.getUniqueId());
        actor.setFoodLevel(10);
        actor.setSaturation(0);
        opponent.setFoodLevel(10);
        opponent.setSaturation(0);
        opponent.teleport(new Location(world, 0.5, 100, 6.5));
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
                        world.getBlockAt(8, y, z).setType(Material.STONE, false);
                    }
                }
                world.dropItem(new Location(world, 7.5, 100.1, 0.5), new ItemStack(Material.DIAMOND, 3)).setPickupDelay(0);
            }
            case "hunter-big-game" -> {
                actor.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD));
                IronGolem golem = world.spawn(new Location(world, 2.5, 100, 0.5), IronGolem.class);
                golem.setAI(false);
                golem.addScoreboardTag("adapt-qa-target-bigGame");
            }
            case "hunter-low-health" -> {
                actor.setHealth(6);
                actor.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD));
                opponent.teleport(new Location(world, 2.5, 100, 0.5));
            }
            case "hunter-predator" -> opponent.teleport(new Location(world, 2.5, 100, 0.5));
            default -> throw new IllegalStateException("Unhandled ranged fixture stage " + name);
        }
        return true;
    }

    public static JsonObject snapshot(Player player) {
        JsonObject result = new JsonObject();
        JsonObject launch = LAUNCHES.get(player.getUniqueId());
        if (launch != null) {
            result.add("lastLaunch", launch);
        }
        JsonObject targets = new JsonObject();
        for (Entity entity : player.getWorld().getEntities()) {
            if (!(entity instanceof LivingEntity target)) {
                continue;
            }
            for (String tag : entity.getScoreboardTags()) {
                if (!tag.startsWith("adapt-qa-target-")) {
                    continue;
                }
                JsonObject state = new JsonObject();
                state.addProperty("uuid", target.getUniqueId().toString());
                state.addProperty("entityId", target.getEntityId());
                state.addProperty("health", target.getHealth());
                state.addProperty("alive", !target.isDead());
                targets.add(tag.substring("adapt-qa-target-".length()), state);
            }
        }
        result.add("targets", targets);
        return result;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void beforeProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (projectile.getShooter() instanceof Player player
                && player.getName().startsWith("AQA")
                && player.getWorld().getName().startsWith("adapt_gameplay_")) {
            INITIAL_SPEEDS.put(projectile.getUniqueId(), projectile.getVelocity().length());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        Double initialSpeed = INITIAL_SPEEDS.remove(projectile.getUniqueId());
        if (event.isCancelled() || !(projectile.getShooter() instanceof Player player)
                || !player.getName().startsWith("AQA")
                || !player.getWorld().getName().startsWith("adapt_gameplay_")) {
            return;
        }
        Vector velocity = projectile.getVelocity();
        JsonObject result = new JsonObject();
        result.addProperty("sequence", ++launchSequence);
        result.addProperty("entityId", projectile.getEntityId());
        result.addProperty("type", projectile.getType().name());
        result.addProperty("speed", velocity.length());
        if (initialSpeed != null) result.addProperty("initialSpeed", initialSpeed);
        result.addProperty("vx", velocity.getX());
        result.addProperty("vy", velocity.getY());
        result.addProperty("vz", velocity.getZ());
        if (projectile instanceof AbstractArrow arrow) {
            result.addProperty("pierceLevel", arrow.getPierceLevel());
        }
        LAUNCHES.put(player.getUniqueId(), result);
    }

    private static void equipBow(Player player) {
        player.getInventory().addItem(new ItemStack(Material.BOW));
        player.getInventory().addItem(new ItemStack(Material.ARROW, 16));
    }

    private static void cow(World world, String name, double x, double health) {
        Cow target = world.spawn(new Location(world, x, 100, 0.5), Cow.class);
        target.setAI(false);
        target.setHealth(health);
        target.addScoreboardTag("adapt-qa-target-" + name);
    }
}
