package art.arcane.adapt.gameplay;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.papermc.paper.event.entity.EntityAttemptSmashAttackEvent;
import io.papermc.paper.event.entity.EntityLungeEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.data.Rail;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class KineticsFixtures implements Listener {
    private static final Set<String> STAGES = Set.of("kinetic-smash", "kinetic-charge", "kinetic-dead-zone",
            "kinetic-pin", "kinetic-lunge", "kinetic-mounted");
    private static final Map<UUID, JsonObject> SMASHES = new HashMap<>();
    private static final Map<UUID, JsonObject> LUNGES = new HashMap<>();
    private static final Map<UUID, JsonObject> HITS = new HashMap<>();
    private static final Map<UUID, JsonObject> MOVES = new HashMap<>();

    private KineticsFixtures() {
    }

    public static void install(JavaPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(new KineticsFixtures(), plugin);
    }

    public static boolean stage(String name, World world, Player actor, Player opponent) {
        if (!STAGES.contains(name)) return false;
        SMASHES.clear();
        LUNGES.clear();
        HITS.clear();
        MOVES.clear();
        actor.setFoodLevel(20);
        actor.setSaturation(0);
        opponent.setFoodLevel(10);
        opponent.setSaturation(0);
        opponent.teleport(new Location(world, -8.5, 100, 8.5));
        switch (name) {
            case "kinetic-smash" -> {
                world.getBlockAt(0, 105, 0).setType(Material.STONE, false);
                actor.teleport(new Location(world, 0.5, 106, 0.5, -90, 0));
                actor.getInventory().addItem(new ItemStack(Material.MACE));
                Cow primary = cow(world, "primary", 2.5, 0.5);
                Objects.requireNonNull(primary.getAttribute(Attribute.ARMOR)).setBaseValue(20);
                Objects.requireNonNull(primary.getAttribute(Attribute.ARMOR_TOUGHNESS)).setBaseValue(8);
                Cow secondary = cow(world, "secondary", 6.25, 0.5);
                secondary.setAI(true);
                Objects.requireNonNull(secondary.getAttribute(Attribute.MOVEMENT_SPEED)).setBaseValue(0);
            }
            case "kinetic-charge" -> {
                actor.teleport(new Location(world, -3.5, 100, 0.5, -90, 0));
                actor.getInventory().addItem(new ItemStack(Material.WOODEN_SPEAR));
                cow(world, "primary", 2.5, 0.5);
            }
            case "kinetic-dead-zone" -> {
                for (int y = 100; y <= 103; y++) {
                    for (int z = -1; z <= 1; z++) {
                        world.getBlockAt(5, y, z).setType(Material.STONE, false);
                        world.getBlockAt(-1, y, z).setType(Material.STONE, false);
                    }
                }
                actor.getInventory().addItem(new ItemStack(Material.WOODEN_SPEAR));
                opponent.teleport(new Location(world, 2.5, 100, 0.5, 90, 0));
                opponent.getInventory().setItemInMainHand(new ItemStack(Material.STICK));
            }
            case "kinetic-pin" -> {
                actor.getInventory().addItem(new ItemStack(Material.WOODEN_SPEAR));
                cow(world, "primary", 4.5, 0.5);
            }
            case "kinetic-lunge" -> {
                ItemStack spear = new ItemStack(Material.WOODEN_SPEAR);
                spear.addUnsafeEnchantment(Enchantment.LUNGE, 1);
                actor.getInventory().addItem(spear);
                cow(world, "primary", 3.5, 0.5);
            }
            case "kinetic-mounted" -> {
                actor.teleport(new Location(world, -4.5, 100, 1.5, -90, 0));
                actor.getInventory().addItem(new ItemStack(Material.WOODEN_SPEAR));
                for (int x = -4; x <= 9; x++) {
                    world.getBlockAt(x, 99, 0).setType(Material.STONE, false);
                    Rail rail = (Rail) Material.POWERED_RAIL.createBlockData();
                    rail.setShape(Rail.Shape.EAST_WEST);
                    world.getBlockAt(x, 100, 0).setBlockData(rail, false);
                }
                world.getBlockAt(-5, 100, 0).setType(Material.STONE, false);
                Minecart cart = world.spawn(new Location(world, -3.5, 100.1, 0.5), Minecart.class);
                cart.addScoreboardTag("adapt-qa-kinetic-mount");
                cow(world, "primary", 1.5, 1.5);
            }
            default -> throw new IllegalStateException("Unhandled kinetics stage " + name);
        }
        return true;
    }

    public static JsonObject snapshot(Player player) {
        JsonObject result = state(player);
        result.addProperty("eyeHeight", player.getEyeHeight());
        result.add("smash", SMASHES.getOrDefault(player.getUniqueId(), new JsonObject()));
        result.add("lunge", LUNGES.getOrDefault(player.getUniqueId(), new JsonObject()));
        result.addProperty("mounted", player.isInsideVehicle());
        Entity vehicle = player.getVehicle();
        if (vehicle != null) {
            result.addProperty("vehicleSpeed", vehicle.getVelocity().length());
            result.addProperty("vehicleId", vehicle.getEntityId());
        }
        JsonObject targets = new JsonObject();
        for (Entity entity : player.getWorld().getEntities()) {
            if (entity.getScoreboardTags().contains("adapt-qa-kinetic-mount")) result.addProperty("mountId", entity.getEntityId());
            if (!(entity instanceof LivingEntity living)) continue;
            for (String tag : living.getScoreboardTags()) {
                if (!tag.startsWith("adapt-qa-kinetic-")) continue;
                JsonObject detail = state(living);
                detail.addProperty("entityId", living.getEntityId());
                targets.add(tag.substring("adapt-qa-kinetic-".length()), detail);
            }
        }
        result.add("targets", targets);
        return result;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(PlayerMoveEvent event) {
        if (!event.hasChangedPosition()) return;
        JsonObject move = new JsonObject();
        move.addProperty("distance", Math.hypot(event.getTo().getX() - event.getFrom().getX(), event.getTo().getZ() - event.getFrom().getZ()));
        move.addProperty("tick", Bukkit.getCurrentTick());
        MOVES.put(event.getPlayer().getUniqueId(), move);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(EntityDamageByEntityEvent event) {
        JsonObject hit = new JsonObject();
        hit.addProperty("damage", event.getFinalDamage());
        hit.addProperty("rawDamage", event.getDamage());
        hit.add("attackerMove", MOVES.get(event.getDamager().getUniqueId()));
        hit.addProperty("tick", Bukkit.getCurrentTick());
        hit.addProperty("attackerSpeed", event.getDamager().getVelocity().clone().setY(0).length());
        if (event.getDamager().getVehicle() != null) hit.addProperty("mountSpeed", event.getDamager().getVehicle().getVelocity().length());
        HITS.put(event.getEntity().getUniqueId(), hit);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void on(EntityAttemptSmashAttackEvent event) {
        JsonObject smash = new JsonObject();
        smash.addProperty("landed", event.getResult() == Event.Result.ALLOW || event.getResult() == Event.Result.DEFAULT && event.getOriginalResult());
        smash.addProperty("fallDistance", event.getEntity().getFallDistance());
        SMASHES.put(event.getEntity().getUniqueId(), smash);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(EntityLungeEvent event) {
        JsonObject lunge = new JsonObject();
        lunge.addProperty("power", event.getLungePower());
        LUNGES.put(event.getEntity().getUniqueId(), lunge);
    }

    private static JsonObject state(LivingEntity entity) {
        JsonObject state = new JsonObject();
        state.addProperty("health", entity.getHealth());
        state.addProperty("x", entity.getLocation().getX());
        state.addProperty("y", entity.getLocation().getY());
        state.addProperty("z", entity.getLocation().getZ());
        state.addProperty("speed", entity.getVelocity().clone().setY(0).length());
        JsonObject attributes = new JsonObject();
        for (Attribute attribute : Registry.ATTRIBUTE) {
            AttributeInstance value = entity.getAttribute(attribute);
            if (value != null) attributes.addProperty(attribute.getKey().getKey(), value.getValue());
        }
        state.add("attributes", attributes);
        JsonArray effects = new JsonArray();
        for (PotionEffect effect : entity.getActivePotionEffects()) {
            JsonObject value = new JsonObject();
            value.addProperty("type", effect.getType().getKey().getKey());
            value.addProperty("amplifier", effect.getAmplifier());
            effects.add(value);
        }
        state.add("effects", effects);
        state.add("lastHit", HITS.getOrDefault(entity.getUniqueId(), new JsonObject()));
        return state;
    }

    private static Cow cow(World world, String label, double x, double z) {
        Cow cow = world.spawn(new Location(world, x, 100, z), Cow.class);
        cow.setAI(false);
        Objects.requireNonNull(cow.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(200);
        cow.setHealth(200);
        cow.addScoreboardTag("adapt-qa-kinetic-" + label);
        return cow;
    }
}
