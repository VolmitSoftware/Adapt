package art.arcane.adapt.gameplay;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.content.item.ChalkWandItem;
import com.destroystokyo.paper.event.entity.EndermanAttackPlayerEvent;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.data.Lightable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vindicator;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockReceiveGameEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class UtilityFixtures implements Listener {
    private static final Set<String> STAGES = Set.of("utility-cat", "utility-wall", "utility-chalk", "utility-elevator",
            "utility-wireless", "utility-cutpurse", "utility-decoy", "utility-veil", "utility-trap");
    private static final Map<UUID, JsonArray> EVENTS = new HashMap<>();

    private UtilityFixtures() {
    }

    public static void install(JavaPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(new UtilityFixtures(), plugin);
    }

    public static boolean stage(String name, World world, Player actor, Player opponent) {
        if (!STAGES.contains(name)) {
            return false;
        }
        EVENTS.remove(actor.getUniqueId());
        world.setGameRule(GameRule.NATURAL_REGENERATION, false);
        actor.setFoodLevel(20);
        actor.setSaturation(20);
        opponent.teleport(new Location(world, 0.5, 100, 7.5));
        switch (name) {
            case "utility-cat" -> {
                ItemStack[] armor = new ItemStack[]{new ItemStack(Material.NETHERITE_BOOTS),
                        new ItemStack(Material.NETHERITE_LEGGINGS), new ItemStack(Material.NETHERITE_CHESTPLATE),
                        new ItemStack(Material.NETHERITE_HELMET)};
                for (ItemStack piece : armor) {
                    piece.addEnchantment(Enchantment.PROTECTION, 4);
                }
                actor.getInventory().setArmorContents(armor);
                opponent.teleport(new Location(world, 0.5, 100, -4.5));
                opponent.getInventory().addItem(new ItemStack(Material.BOW), new ItemStack(Material.ARROW, 64));
            }
            case "utility-wall" -> {
                for (int y = 100; y <= 106; y++) {
                    for (int z = -2; z <= 2; z++) {
                        world.getBlockAt(1, y, z).setType(Material.STONE, false);
                    }
                }
            }
            case "utility-chalk" -> actor.getInventory().addItem(ChalkWandItem.create(ChalkWandItem.Tool.STRAIGHTEDGE));
            case "utility-elevator" -> {
                world.getBlockAt(2, 99, 0).setType(Material.AIR, false);
                world.getBlockAt(2, 98, 0).setType(Material.STONE, false);
                world.getBlockAt(3, 102, 0).setType(Material.STONE, false);
                ItemStack elevators = recipe("elevator");
                elevators.setAmount(2);
                actor.getInventory().addItem(elevators);
            }
            case "utility-wireless" -> {
                world.getBlockAt(3, 100, 0).setType(Material.REDSTONE_LAMP, false);
                actor.getInventory().addItem(recipe("remote-redstone-torch"));
            }
            case "utility-cutpurse" -> {
                Vindicator target = world.spawn(new Location(world, 2.5, 100, 0.5, -90, 0), Vindicator.class);
                target.setAI(false);
                target.getAttribute(Registry.ATTRIBUTE.get(NamespacedKey.minecraft("max_health"))).setBaseValue(80);
                target.getAttribute(Registry.ATTRIBUTE.get(NamespacedKey.minecraft("knockback_resistance"))).setBaseValue(1);
                target.setHealth(80);
                target.addScoreboardTag("adapt-qa-utility-target");
            }
            case "utility-veil" -> {
                world.setTime(18000);
                Enderman target = world.spawn(new Location(world, 6.5, 100, 0.5), Enderman.class);
                target.addScoreboardTag("adapt-qa-utility-target");
            }
            case "utility-trap" -> world.getBlockAt(3, 100, 0).setType(Material.SCULK_SENSOR, false);
            case "utility-decoy" -> { }
            default -> throw new IllegalStateException("Unhandled utility stage " + name);
        }
        return true;
    }

    public static JsonObject snapshot(Player player) {
        JsonObject result = new JsonObject();
        result.addProperty("veilSuppressions", Adapt.instance.getAdaptServer().getPlayer(player).getData().getStat("stealth.ender-veil.stares-survived"));
        result.add("events", EVENTS.getOrDefault(player.getUniqueId(), new JsonArray()));
        JsonArray anchors = new JsonArray();
        for (Entity entity : player.getWorld().getEntities()) {
            if (entity instanceof ArmorStand stand) {
                anchors.add(state(stand));
            }
            if (entity.getScoreboardTags().contains("adapt-qa-utility-target") && entity instanceof LivingEntity living) {
                result.add("target", state(living));
            }
        }
        result.add("anchors", anchors);
        result.addProperty("lamp", player.getWorld().getBlockAt(3, 100, 0).getBlockData() instanceof Lightable light && light.isLit());
        result.addProperty("lowerElevator", player.getWorld().getBlockAt(2, 99, 0).getType().name());
        result.addProperty("upperElevator", player.getWorld().getBlockAt(2, 102, 0).getType().name());
        ChalkWandItem.WandData wand = ChalkWandItem.read(player.getInventory().getItemInMainHand());
        JsonArray points = new JsonArray();
        if (wand != null) {
            for (ChalkWandItem.Point point : wand.plan().points()) {
                JsonObject entry = new JsonObject();
                entry.addProperty("x", point.x());
                entry.addProperty("y", point.y());
                entry.addProperty("z", point.z());
                points.add(entry);
            }
        }
        result.add("chalkPoints", points);
        return result;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProjectileDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player player && event.getDamager() instanceof Projectile && eligible(player)) {
            JsonObject entry = event("projectile", event.isCancelled());
            entry.addProperty("damage", event.getFinalDamage());
            entry.addProperty("projectileId", event.getDamager().getEntityId());
            entry.addProperty("sprinting", player.isSprinting());
            record(player, entry);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onStare(EndermanAttackPlayerEvent event) {
        if (eligible(event.getPlayer())) {
            record(event.getPlayer(), event("stare", event.isCancelled()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getEntity() instanceof Enderman && event.getTarget() instanceof Player player && eligible(player)) {
            record(player, event("target", event.isCancelled()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVibration(BlockReceiveGameEvent event) {
        if (event.getBlock().getType() == Material.SCULK_SENSOR && event.getEntity() instanceof Player player && eligible(player)) {
            JsonObject entry = event("vibration", event.isCancelled());
            entry.addProperty("gameEvent", event.getEvent().getKey().toString());
            record(player, entry);
        }
    }

    private static boolean eligible(Player player) {
        return player.getName().startsWith("AQA") && player.getWorld().getName().startsWith("adapt_gameplay_");
    }

    private static JsonObject event(String type, boolean cancelled) {
        JsonObject entry = new JsonObject();
        entry.addProperty("type", type);
        entry.addProperty("cancelled", cancelled);
        return entry;
    }

    private static void record(Player player, JsonObject event) {
        JsonArray events = EVENTS.computeIfAbsent(player.getUniqueId(), ignored -> new JsonArray());
        if (events.size() < 128) {
            events.add(event);
        }
    }

    private static JsonObject state(LivingEntity entity) {
        JsonObject result = new JsonObject();
        result.addProperty("entityId", entity.getEntityId());
        result.addProperty("health", entity.getHealth());
        result.addProperty("x", entity.getLocation().getX());
        result.addProperty("y", entity.getLocation().getY());
        result.addProperty("z", entity.getLocation().getZ());
        if (entity instanceof Mob mob) {
            result.addProperty("hasTarget", mob.getTarget() != null);
        }
        return result;
    }

    private static ItemStack recipe(String key) {
        Recipe recipe = Bukkit.getRecipe(new NamespacedKey("adapt", key));
        if (recipe == null) {
            throw new IllegalStateException("Required utility recipe unavailable: " + key);
        }
        ItemStack item = recipe.getResult().clone();
        item.setAmount(1);
        return item;
    }
}
