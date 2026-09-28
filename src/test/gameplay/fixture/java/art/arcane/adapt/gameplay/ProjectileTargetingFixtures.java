package art.arcane.adapt.gameplay;

import art.arcane.adapt.content.adaptation.ranged.RangedHeartseeker;
import art.arcane.adapt.content.adaptation.ranged.RangedRicochetBolt;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Husk;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ProjectileTargetingFixtures implements Listener {
    private static final Map<String, LivingEntity> TARGETS = new LinkedHashMap<>();
    private static JsonArray impacts = new JsonArray();
    private static JsonArray damage = new JsonArray();

    private ProjectileTargetingFixtures() {
    }

    public static void install(JavaPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(new ProjectileTargetingFixtures(), plugin);
    }

    public static void cleanup() {
        TARGETS.clear();
        impacts = new JsonArray();
        damage = new JsonArray();
    }

    public static boolean stage(String name, World world, Player actor, Player opponent) {
        if (!name.startsWith("projectile-qa-targeting-")) return false;
        cleanup();
        String trial = name.substring("projectile-qa-targeting-".length());
        String mob = trial.substring(trial.lastIndexOf('-') + 1);
        actor.setFoodLevel(17);
        actor.setSaturation(0);
        actor.setCooldown(Material.BOW, 0);
        actor.setCooldown(Material.IRON_AXE, 0);
        actor.setCooldown(Material.WITHER_SKELETON_SKULL, 0);
        opponent.teleport(new Location(world, 18.5, 100, 18.5));
        if (trial.startsWith("skull-")) {
            actor.getInventory().addItem(new ItemStack(Material.WITHER_SKELETON_SKULL, 4));
            wall(world, 9);
            spawn(world, actor, "target", mob, 8.0, 2.0);
        } else if (trial.startsWith("axe-")) {
            actor.getInventory().addItem(new ItemStack(Material.IRON_AXE));
            wall(world, 6);
            spawn(world, actor, "target", mob, 3.5, 3.17);
        } else if (trial.startsWith("heart-")) {
            actor.getInventory().addItem(new ItemStack(Material.BOW), new ItemStack(Material.ARROW, 16));
            if (trial.startsWith("heart-chain-")) {
                spawn(world, actor, "primary", "husk", 8.5, 0.5);
                spawn(world, actor, "target", mob, 8.5, 4.5);
            } else {
                spawn(world, actor, "target", mob, 8.5, 0.5);
            }
        } else {
            throw new IllegalArgumentException("Unknown projectile targeting trial: " + trial);
        }
        return true;
    }

    public static JsonObject snapshot(Player player) {
        JsonObject result = new JsonObject();
        JsonObject targets = new JsonObject();
        for (Map.Entry<String, LivingEntity> entry : TARGETS.entrySet()) {
            LivingEntity target = entry.getValue();
            if (target.getWorld() != player.getWorld()) continue;
            JsonObject state = new JsonObject();
            state.addProperty("entityId", target.getEntityId());
            state.addProperty("health", target.getHealth());
            state.addProperty("dead", target.isDead());
            state.addProperty("valid", target.isValid());
            state.addProperty("x", target.getLocation().getX());
            state.addProperty("y", target.getLocation().getY());
            state.addProperty("z", target.getLocation().getZ());
            state.addProperty("height", target.getHeight());
            targets.add(entry.getKey(), state);
        }
        result.add("targets", targets);
        result.add("impacts", impacts);
        result.add("damage", damage);
        return result;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onImpact(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (TARGETS.isEmpty() || projectile.getWorld() != TARGETS.values().iterator().next().getWorld()) return;
        JsonObject impact = new JsonObject();
        impact.addProperty("entityId", projectile.getEntityId());
        impact.addProperty("type", projectile.getType().name());
        impact.addProperty("seeking", RangedHeartseeker.isSeekingProjectile(projectile));
        impact.addProperty("ricochets", RangedRicochetBolt.impactOf(projectile).count());
        impact.addProperty("cancelled", event.isCancelled());
        if (event.getHitEntity() != null) impact.addProperty("targetId", event.getHitEntity().getEntityId());
        impacts.add(impact);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!TARGETS.containsValue(event.getEntity())) return;
        JsonObject hit = new JsonObject();
        hit.addProperty("targetId", event.getEntity().getEntityId());
        hit.addProperty("cause", event.getCause().name());
        hit.addProperty("damage", event.getFinalDamage());
        hit.addProperty("cancelled", event.isCancelled());
        Entity source = event.getDamager();
        hit.addProperty("source", source.getType().name());
        hit.addProperty("seeking", RangedHeartseeker.isSeekingProjectile(source));
        damage.add(hit);
    }

    private static void wall(World world, int x) {
        for (int y = 100; y <= 104; y++) {
            for (int z = -3; z <= 3; z++) world.getBlockAt(x, y, z).setType(Material.OBSIDIAN, false);
        }
    }

    private static void spawn(World world, Player owner, String key, String mob, double x, double z) {
        Class<? extends LivingEntity> type = switch (mob) {
            case "cow" -> Cow.class;
            case "wolf" -> Wolf.class;
            case "husk" -> Husk.class;
            case "servant" -> Skeleton.class;
            default -> throw new IllegalArgumentException("Unknown mob: " + mob);
        };
        LivingEntity target = world.spawn(new Location(world, x, 100, z), type);
        target.setAI(false);
        target.getAttribute(Attribute.MAX_HEALTH).setBaseValue(100);
        target.setHealth(100);
        if (mob.equals("servant")) {
            target.getEquipment().setHelmet(new ItemStack(Material.LEATHER_HELMET));
            target.getPersistentDataContainer().set(NamespacedKey.fromString("adapt:tragoul_servant_owner"),
                    PersistentDataType.STRING, owner.getUniqueId().toString());
        }
        TARGETS.put(key, target);
    }
}
