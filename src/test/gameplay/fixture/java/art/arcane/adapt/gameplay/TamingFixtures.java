package art.arcane.adapt.gameplay;

import com.google.gson.JsonObject;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TamingFixtures implements Listener {
    private static final Set<String> STAGES = Set.of("taming-passive", "taming-regeneration", "taming-recall",
            "taming-fetch", "taming-shared", "taming-last-breath", "taming-alpha", "taming-mounted",
            "taming-stable", "taming-battle");
    private static final Map<UUID, JsonObject> PET_HITS = new HashMap<>();

    private TamingFixtures() {
    }

    public static void install(JavaPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(new TamingFixtures(), plugin);
    }

    public static boolean stage(String name, World world, Player actor, Player opponent) {
        if (!STAGES.contains(name)) {
            return false;
        }
        PET_HITS.remove(actor.getUniqueId());
        actor.setFoodLevel(10);
        actor.setSaturation(0);
        opponent.setFoodLevel(10);
        opponent.setSaturation(0);
        opponent.teleport(new Location(world, 0.5, 100, 5.5));
        actor.setCooldown(Material.LEAD, 0);
        if (name.equals("taming-mounted")) {
            opponent.teleport(new Location(world, 4.5, 100, 0.5));
            Horse horse = world.spawn(new Location(world, 2.5, 100, 0.5), Horse.class, pet -> pet.setOwner(actor));
            horse.setAI(false);
            horse.getInventory().setSaddle(new ItemStack(Material.SADDLE));
            horse.addScoreboardTag("adapt-qa-taming-pet");
            return true;
        }
        double x = name.equals("taming-recall") ? 9.5 : 2.5;
        Wolf wolf = world.spawn(new Location(world, x, 100, 0.5), Wolf.class, pet -> {
            if (!name.equals("taming-stable")) {
                pet.setOwner(actor);
            }
        });
        wolf.addScoreboardTag("adapt-qa-taming-pet");
        wolf.setAI(name.equals("taming-fetch") || name.equals("taming-battle"));
        wolf.setSitting(name.equals("taming-alpha"));
        switch (name) {
            case "taming-regeneration" -> wolf.setHealth(8);
            case "taming-recall" -> actor.getInventory().addItem(new ItemStack(Material.LEAD));
            case "taming-fetch" -> world.dropItem(new Location(world, 7.5, 100.1, 0.5), new ItemStack(Material.DIAMOND, 3)).setPickupDelay(0);
            case "taming-shared" -> {
                wolf.teleport(new Location(world, 0.5, 100, 2.5));
                opponent.teleport(new Location(world, 2.5, 100, 0.5));
                opponent.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD));
            }
            case "taming-last-breath" -> {
                wolf.setHealth(1);
                actor.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD));
            }
            case "taming-alpha" -> {
                wolf.teleport(new Location(world, 0.5, 100, 2.5));
                actor.getInventory().addItem(new ItemStack(Material.BONE, 16));
                cow(world, 2.5, 10);
            }
            case "taming-stable" -> actor.getInventory().addItem(new ItemStack(Material.BONE, 64));
            case "taming-battle" -> {
                wolf.teleport(new Location(world, 2.5, 100, 2.5));
                cow(world, 2.5, 3);
            }
            default -> { }
        }
        return true;
    }

    public static JsonObject snapshot(Player player) {
        JsonObject result = new JsonObject();
        result.addProperty("mounted", player.isInsideVehicle());
        JsonObject hit = PET_HITS.get(player.getUniqueId());
        if (hit != null) {
            result.add("petHit", hit);
        }
        for (Entity entity : player.getWorld().getEntities()) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            for (String tag : living.getScoreboardTags()) {
                if (tag.startsWith("adapt-qa-taming-")) {
                    result.add(tag.substring("adapt-qa-taming-".length()), state(living));
                }
            }
        }
        return result;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPetDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Tameable pet)
                || !(pet.getOwner() instanceof Player owner)
                || !owner.getName().startsWith("AQA")
                || !owner.getWorld().getName().startsWith("adapt_gameplay_")) {
            return;
        }
        JsonObject hit = new JsonObject();
        hit.addProperty("targetId", event.getEntity().getEntityId());
        hit.addProperty("petId", pet.getEntityId());
        hit.addProperty("damage", event.getFinalDamage());
        PET_HITS.put(owner.getUniqueId(), hit);
    }

    private static JsonObject state(LivingEntity entity) {
        JsonObject result = new JsonObject();
        result.addProperty("entityId", entity.getEntityId());
        result.addProperty("health", entity.getHealth());
        result.addProperty("alive", !entity.isDead());
        result.addProperty("x", entity.getLocation().getX());
        result.addProperty("y", entity.getLocation().getY());
        result.addProperty("z", entity.getLocation().getZ());
        if (entity instanceof Tameable tameable) {
            result.addProperty("tamed", tameable.isTamed());
        }
        if (entity instanceof Wolf wolf) {
            result.addProperty("sitting", wolf.isSitting());
        }
        if (entity instanceof Mob mob && mob.getTarget() != null) {
            result.addProperty("targetId", mob.getTarget().getEntityId());
        }
        JsonObject attributes = new JsonObject();
        for (Attribute attribute : Registry.ATTRIBUTE) {
            AttributeInstance instance = entity.getAttribute(attribute);
            if (instance != null) {
                attributes.addProperty(attribute.getKey().toString(), instance.getValue());
            }
        }
        result.add("attributes", attributes);
        JsonObject effects = new JsonObject();
        for (PotionEffect effect : entity.getActivePotionEffects()) {
            effects.addProperty(effect.getType().getKey().toString(), effect.getAmplifier());
        }
        result.add("effects", effects);
        return result;
    }

    private static void cow(World world, double x, double health) {
        Cow cow = world.spawn(new Location(world, x, 100, 0.5), Cow.class);
        cow.setAI(false);
        cow.setHealth(health);
        cow.addScoreboardTag("adapt-qa-taming-target");
    }
}
