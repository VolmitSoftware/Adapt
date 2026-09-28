package art.arcane.adapt.gameplay;

import com.google.gson.JsonObject;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DefenseFixtures implements Listener {
    private static final Set<String> STAGES = Set.of("defense-unarmed", "defense-sword", "defense-axe",
            "defense-riposte", "defense-resolve", "defense-splitter", "defense-kill", "defense-cleave", "defense-log");
    private static final Map<UUID, JsonObject> HITS = new HashMap<>();
    private static long hitSequence;

    private DefenseFixtures() {
    }

    public static void install(JavaPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(new DefenseFixtures(), plugin);
    }

    public static boolean stage(String name, World world, Player actor, Player opponent) {
        if (!STAGES.contains(name)) {
            return false;
        }
        HITS.clear();
        actor.setFoodLevel(10);
        actor.setSaturation(0);
        opponent.setFoodLevel(10);
        opponent.setSaturation(0);
        actor.setCooldown(Material.SHIELD, 0);
        opponent.setCooldown(Material.SHIELD, 0);
        actor.teleport(new Location(world, 0.5, 100, 0.5, -90, 0));
        opponent.teleport(new Location(world, 2.5, 100, 0.5, 90, 0));
        switch (name) {
            case "defense-unarmed" -> { }
            case "defense-sword" -> actor.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD));
            case "defense-axe" -> actor.getInventory().addItem(new ItemStack(Material.WOODEN_AXE));
            case "defense-riposte" -> {
                actor.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD));
                actor.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
                opponent.getInventory().setItemInMainHand(new ItemStack(Material.WOODEN_SWORD));
            }
            case "defense-resolve" -> {
                actor.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
                opponent.getInventory().setItemInMainHand(new ItemStack(Material.WOODEN_AXE));
            }
            case "defense-splitter" -> {
                actor.getInventory().addItem(new ItemStack(Material.WOODEN_AXE));
                opponent.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
            }
            case "defense-kill" -> {
                actor.setHealth(12);
                opponent.teleport(new Location(world, -5.5, 100, 5.5));
                cow(world, "kill", 2.5, 0.5, 0.5);
            }
            case "defense-cleave" -> {
                actor.getInventory().addItem(new ItemStack(Material.WOODEN_AXE));
                opponent.teleport(new Location(world, -5.5, 100, 5.5));
                cow(world, "primary", 2.5, 0.5, 10);
                cow(world, "secondary", 2.8, 1.4, 10);
            }
            case "defense-log" -> {
                actor.getInventory().addItem(new ItemStack(Material.DIAMOND_AXE));
                opponent.teleport(new Location(world, -5.5, 100, 5.5));
                for (int y = 100; y < 103; y++) {
                    world.getBlockAt(2, y, 0).setType(Material.OAK_LOG, false);
                }
            }
            default -> throw new IllegalStateException("Unhandled defense fixture stage " + name);
        }
        return true;
    }

    public static JsonObject snapshot(Player player) {
        JsonObject result = new JsonObject();
        result.addProperty("blocking", player.isBlocking());
        result.addProperty("sprinting", player.isSprinting());
        result.addProperty("sneaking", player.isSneaking());
        result.addProperty("shieldCooldown", player.getCooldown(Material.SHIELD));
        result.addProperty("absorption", player.getAbsorptionAmount());
        ItemStack shield = player.getInventory().getItemInOffHand();
        if (shield.getItemMeta() instanceof Damageable damageable) {
            result.addProperty("offhandDamage", damageable.getDamage());
        }
        JsonObject hit = HITS.get(player.getUniqueId());
        if (hit != null) {
            result.add("lastHit", hit);
        }
        JsonObject targets = new JsonObject();
        for (Entity entity : player.getWorld().getEntities()) {
            if (!(entity instanceof LivingEntity target)) {
                continue;
            }
            for (String tag : target.getScoreboardTags()) {
                if (tag.startsWith("adapt-qa-defense-")) {
                    JsonObject state = new JsonObject();
                    state.addProperty("entityId", target.getEntityId());
                    state.addProperty("health", target.getHealth());
                    targets.add(tag.substring("adapt-qa-defense-".length()), state);
                }
            }
        }
        result.add("targets", targets);
        return result;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void observeHit(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        JsonObject result = new JsonObject();
        result.addProperty("sequence", ++hitSequence);
        result.addProperty("cause", event.getCause().name());
        result.addProperty("damage", event.getFinalDamage());
        result.addProperty("cancelled", event.isCancelled());
        if (event instanceof EntityDamageByEntityEvent attack) {
            result.addProperty("attacker", attack.getDamager().getUniqueId().toString());
        }
        HITS.put(event.getEntity().getUniqueId(), result);
    }

    private static void cow(World world, String label, double x, double z, double health) {
        Cow cow = world.spawn(new Location(world, x, 100, z), Cow.class);
        cow.setAI(false);
        cow.setHealth(health);
        cow.addScoreboardTag("adapt-qa-defense-" + label);
    }
}
