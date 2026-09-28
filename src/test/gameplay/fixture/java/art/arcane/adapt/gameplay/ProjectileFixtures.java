package art.arcane.adapt.gameplay;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Blaze;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ProjectileFixtures implements Listener {
    private static final Set<String> STAGES = Set.of("projectile-procs", "projectile-heart", "projectile-bounce",
            "projectile-preview", "projectile-web", "projectile-blood", "projectile-snare", "projectile-speed",
            "projectile-trophies", "projectile-smash", "projectile-axe", "projectile-logswap");
    private static final Map<UUID, JsonArray> IMPACTS = new HashMap<>();

    private ProjectileFixtures() {
    }

    public static void install(JavaPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(new ProjectileFixtures(), plugin);
    }

    public static boolean stage(String name, World world, Player actor, Player opponent) {
        if (!STAGES.contains(name)) {
            return false;
        }
        IMPACTS.remove(actor.getUniqueId());
        actor.setFoodLevel(20);
        actor.setSaturation(0);
        actor.setCooldown(Material.BOW, 0);
        actor.setCooldown(Material.IRON_AXE, 0);
        world.setGameRule(GameRule.NATURAL_REGENERATION, false);
        opponent.teleport(new Location(world, 0.5, 100, 6.5));
        switch (name) {
            case "projectile-procs" -> {
                bow(actor);
                cow(world, 4.5, 200, false);
            }
            case "projectile-heart", "projectile-preview" -> {
                bow(actor);
                cow(world, 8.5, 50, false);
            }
            case "projectile-bounce" -> {
                bow(actor);
                for (int y = 100; y <= 105; y++) {
                    for (int z = -3; z <= 3; z++) {
                        world.getBlockAt(6, y, z).setType(Material.STONE, false);
                        world.getBlockAt(-6, y, z).setType(Material.STONE, false);
                    }
                }
            }
            case "projectile-web" -> {
                actor.getInventory().addItem(recipeInput("ranged-web-bomb"));
                cow(world, 5.5, 20, false);
            }
            case "projectile-blood" -> cow(world, 2.5, 4, true);
            case "projectile-snare" -> {
                actor.getInventory().addItem(recipeInput("hunter-snare"));
                Zombie zombie = world.spawn(new Location(world, 3.5, 100, 0.5), Zombie.class);
                zombie.setAI(false);
                zombie.getEquipment().setHelmet(new ItemStack(Material.LEATHER_HELMET));
                zombie.addScoreboardTag("adapt-qa-projectile-target");
            }
            case "projectile-speed" -> opponent.teleport(new Location(world, 2.5, 100, 0.5));
            case "projectile-trophies" -> {
                for (int index = 0; index < 20; index++) {
                    double angle = index * Math.PI / 10;
                    Blaze blaze = world.spawn(new Location(world, 0.5 + Math.cos(angle) * 2.7, 100, 0.5 + Math.sin(angle) * 2.7), Blaze.class);
                    blaze.setAI(false);
                    blaze.setGravity(false);
                    blaze.setHealth(1);
                    blaze.addScoreboardTag("adapt-qa-projectile-trophy-" + index);
                }
            }
            case "projectile-smash" -> {
                actor.getInventory().addItem(new ItemStack(Material.IRON_AXE));
                cow(world, 3.5, 30, false);
            }
            case "projectile-axe" -> {
                actor.getInventory().addItem(new ItemStack(Material.IRON_AXE));
                cow(world, 6.5, 40, false);
            }
            case "projectile-logswap" -> {
                actor.getInventory().addItem(new ItemStack(Material.BIRCH_LOG, 8));
                actor.getInventory().addItem(new ItemStack(Material.OAK_SAPLING));
                world.getBlockAt(2, 100, 2).setType(Material.CRAFTING_TABLE, false);
            }
            default -> throw new IllegalStateException("Unhandled projectile fixture " + name);
        }
        return true;
    }

    public static JsonObject snapshot(Player player) {
        JsonObject result = new JsonObject();
        JsonArray impacts = IMPACTS.get(player.getUniqueId());
        result.add("impacts", impacts == null ? new JsonArray() : impacts);
        JsonObject trophies = new JsonObject();
        JsonObject drops = new JsonObject();
        for (Entity entity : player.getWorld().getEntities()) {
            if (entity instanceof Item item) {
                String type = item.getItemStack().getType().name();
                drops.addProperty(type, (drops.has(type) ? drops.get(type).getAsInt() : 0) + item.getItemStack().getAmount());
            }
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            for (String tag : living.getScoreboardTags()) {
                if (tag.equals("adapt-qa-projectile-target")) {
                    result.add("target", state(living));
                } else if (tag.startsWith("adapt-qa-projectile-trophy-")) {
                    trophies.add(tag.substring("adapt-qa-projectile-trophy-".length()), state(living));
                }
            }
        }
        result.add("trophies", trophies);
        result.add("drops", drops);
        int webs = 0;
        for (int x = 3; x <= 8; x++) {
            for (int y = 99; y <= 104; y++) {
                for (int z = -3; z <= 3; z++) {
                    if (player.getWorld().getBlockAt(x, y, z).getType() == Material.COBWEB) {
                        webs++;
                    }
                }
            }
        }
        result.addProperty("webs", webs);
        ItemStack hand = player.getInventory().getItemInMainHand();
        result.addProperty("heldType", hand.getType().name());
        result.addProperty("heldDamage", hand.getItemMeta() instanceof Damageable damageable ? damageable.getDamage() : 0);
        return result;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onImpact(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player player)
                || !player.getName().startsWith("AQA")
                || !player.getWorld().getName().startsWith("adapt_gameplay_")) {
            return;
        }
        JsonObject impact = new JsonObject();
        impact.addProperty("entityId", projectile.getEntityId());
        impact.addProperty("x", projectile.getLocation().getX());
        impact.addProperty("y", projectile.getLocation().getY());
        impact.addProperty("z", projectile.getLocation().getZ());
        impact.addProperty("block", event.getHitBlock() != null);
        if (event.getHitEntity() != null) {
            impact.addProperty("targetId", event.getHitEntity().getEntityId());
        }
        IMPACTS.computeIfAbsent(player.getUniqueId(), ignored -> new JsonArray()).add(impact);
    }

    private static JsonObject state(LivingEntity target) {
        JsonObject result = new JsonObject();
        result.addProperty("entityId", target.getEntityId());
        result.addProperty("health", target.getHealth());
        result.addProperty("alive", !target.isDead());
        result.addProperty("x", target.getLocation().getX());
        result.addProperty("y", target.getLocation().getY());
        result.addProperty("z", target.getLocation().getZ());
        result.addProperty("glowing", target.isGlowing());
        JsonObject effects = new JsonObject();
        for (PotionEffect effect : target.getActivePotionEffects()) {
            effects.addProperty(effect.getType().getKey().toString(), effect.getAmplifier());
        }
        result.add("effects", effects);
        JsonObject attributes = new JsonObject();
        for (Attribute attribute : Registry.ATTRIBUTE) {
            AttributeInstance instance = target.getAttribute(attribute);
            if (instance != null) {
                attributes.addProperty(attribute.getKey().toString(), instance.getValue());
            }
        }
        result.add("attributes", attributes);
        return result;
    }

    private static void bow(Player actor) {
        actor.getInventory().addItem(new ItemStack(Material.BOW));
        actor.getInventory().addItem(new ItemStack(Material.ARROW, 64));
    }

    private static void cow(World world, double x, double health, boolean ai) {
        Cow cow = world.spawn(new Location(world, x, 100, 0.5), Cow.class);
        cow.setAI(ai);
        AttributeInstance maximum = cow.getAttribute(Registry.ATTRIBUTE.get(NamespacedKey.minecraft("max_health")));
        if (maximum == null) {
            throw new IllegalStateException("Cow max-health attribute unavailable");
        }
        maximum.setBaseValue(Math.max(10, health));
        if (health == 200) {
            AttributeInstance resistance = cow.getAttribute(Registry.ATTRIBUTE.get(NamespacedKey.minecraft("knockback_resistance")));
            if (resistance != null) {
                resistance.setBaseValue(1);
            }
        }
        cow.setHealth(health);
        cow.addScoreboardTag("adapt-qa-projectile-target");
    }

    private static ItemStack recipeInput(String name) {
        Recipe recipe = Bukkit.getRecipe(new NamespacedKey("adapt", name));
        if (recipe == null) {
            throw new IllegalStateException("Required Adapt recipe is missing: " + name);
        }
        ItemStack input = recipe.getResult().clone();
        input.setAmount(1);
        return input;
    }
}
