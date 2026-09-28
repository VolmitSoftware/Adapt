package art.arcane.adapt.gameplay;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class GuardFixtures implements Listener {
    private static final Set<String> STAGES = Set.of("guard-block", "guard-temper", "guard-mirror", "guard-interpose",
            "guard-wall", "guard-bash", "guard-cyclone", "guard-multiarmor", "guard-heirloom", "guard-machete",
            "guard-whetstone", "guard-charge", "guard-disarm", "guard-clap");
    private static final Map<UUID, JsonObject> FIRST_HITS = new HashMap<>();
    private static final Map<UUID, JsonObject> LAST_HITS = new HashMap<>();
    private static final Map<UUID, Integer> KILLS = new HashMap<>();
    private static final Map<UUID, Long> SPRINTS = new HashMap<>();
    private static long hitSequence;
    private static Player wallAlly;

    private GuardFixtures() {
    }

    public static void install(JavaPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(new GuardFixtures(), plugin);
    }

    public static boolean stage(String name, World world, Player actor, Player opponent) {
        if (!STAGES.contains(name)) {
            return false;
        }
        FIRST_HITS.clear();
        LAST_HITS.clear();
        KILLS.clear();
        SPRINTS.clear();
        wallAlly = null;
        actor.setFoodLevel(10);
        actor.setSaturation(0);
        opponent.setFoodLevel(10);
        opponent.setSaturation(0);
        actor.setLevel(10);
        actor.setExp(0);
        actor.setCooldown(Material.SHIELD, 0);
        opponent.setCooldown(Material.SHIELD, 0);
        switch (name) {
            case "guard-block", "guard-temper" -> {
                actor.getInventory().setItemInOffHand(damaged(Material.SHIELD, name.equals("guard-temper") ? 100 : 0));
                opponent.getInventory().setItemInMainHand(new ItemStack(Material.WOODEN_SWORD));
                if (name.equals("guard-temper")) {
                    actor.getInventory().setBoots(damaged(Material.IRON_BOOTS, 80));
                }
            }
            case "guard-mirror" -> {
                actor.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
                opponent.teleport(new Location(world, 6.5, 100, 0.5, 90, 0));
                opponent.getInventory().setItemInMainHand(new ItemStack(Material.BOW));
                opponent.getInventory().addItem(new ItemStack(Material.ARROW, 40));
            }
            case "guard-interpose" -> {
                actor.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
                opponent.teleport(new Location(world, 1.5, 100, 0.5, -90, 0));
                opponent.setHealth(6);
                world.getBlockAt(3, 99, 0).setType(Material.SAND, false);
                world.getBlockAt(3, 100, 0).setType(Material.CACTUS, false);
            }
            case "guard-wall" -> {
                wallAlly = opponent;
                actor.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 600, 0, false, false));
                actor.teleport(new Location(world, 1.5, 100, 1.5, -90, 0));
                opponent.teleport(new Location(world, 0.5, 100, 0.5));
                actor.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
                Skeleton skeleton = world.spawn(new Location(world, 3.5, 100, 0.5), Skeleton.class);
                skeleton.setAI(false);
                skeleton.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));
                skeleton.getEquipment().setItemInMainHand(new ItemStack(Material.BOW));
                Objects.requireNonNull(skeleton.getAttribute(Attribute.MOVEMENT_SPEED)).setBaseValue(0);
                skeleton.setTarget(opponent);
                skeleton.addScoreboardTag("adapt-qa-guard-shooter");
            }
            case "guard-bash", "guard-cyclone" -> {
                actor.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD));
                if (name.equals("guard-bash")) actor.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
                opponent.teleport(new Location(world, -5.5, 100, 5.5));
                cow(world, "primary", 2.5, 0.5, 20);
                cow(world, "secondary", 3.5, 1.5, 20);
            }
            case "guard-multiarmor" -> {
                opponent.teleport(new Location(world, -5.5, 100, 5.5));
                actor.getInventory().addItem(new ItemStack(Material.ELYTRA), new ItemStack(Material.IRON_CHESTPLATE));
            }
            case "guard-heirloom" -> {
                actor.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD));
                opponent.teleport(new Location(world, -5.5, 100, 5.5));
                world.getBlockAt(2, 100, 2).setType(Material.ANVIL, false);
                for (int index = 0; index < 5; index++) {
                    cow(world, "heirloom" + index, 2.5, -0.3 + index * 0.4, 0.5);
                }
            }
            case "guard-machete" -> {
                actor.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD));
                opponent.teleport(new Location(world, -5.5, 100, 5.5));
                for (int x = 2; x <= 3; x++) {
                    Leaves leaves = (Leaves) Material.OAK_LEAVES.createBlockData();
                    leaves.setPersistent(true);
                    world.getBlockAt(x, 101, 0).setBlockData(leaves, false);
                }
            }
            case "guard-whetstone" -> {
                actor.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD));
                opponent.teleport(new Location(world, 2.5, 100, 2.5));
                world.getBlockAt(2, 100, 0).setType(Material.GRINDSTONE, false);
            }
            case "guard-charge" -> actor.teleport(new Location(world, -3.5, 100, 0.5, -90, 0));
            case "guard-disarm" -> {
                opponent.teleport(new Location(world, -5.5, 100, 5.5));
                Zombie target = world.spawn(new Location(world, 2.5, 100, 0.5), Zombie.class);
                target.setAI(false);
                target.setSilent(true);
                target.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));
                target.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
                Objects.requireNonNull(target.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(200);
                Objects.requireNonNull(target.getAttribute(Attribute.KNOCKBACK_RESISTANCE)).setBaseValue(1);
                target.setHealth(200);
                target.addScoreboardTag("adapt-qa-guard-disarm");
            }
            case "guard-clap" -> { }
            default -> throw new IllegalStateException("Unhandled guard fixture stage " + name);
        }
        return true;
    }

    public static JsonObject snapshot(Player player) {
        JsonObject result = state(player);
        result.addProperty("kills", KILLS.getOrDefault(player.getUniqueId(), 0));
        result.addProperty("level", player.getLevel());
        result.addProperty("blocking", player.isBlocking());
        result.addProperty("sprinting", player.isSprinting());
        result.addProperty("shieldCooldown", player.getCooldown(Material.SHIELD));
        result.add("inventory", inventory(player.getInventory().getContents()));
        result.add("chest", item(player.getInventory().getChestplate()));
        result.add("boots", item(player.getInventory().getBoots()));
        result.add("offhand", item(player.getInventory().getItemInOffHand()));
        JsonObject targets = new JsonObject();
        JsonArray drops = new JsonArray();
        JsonArray projectiles = new JsonArray();
        for (Entity entity : player.getWorld().getEntities()) {
            if (entity instanceof Item dropped && dropped.getLocation().distanceSquared(player.getLocation()) < 144) {
                drops.add(item(dropped.getItemStack()));
            }
            if (entity instanceof Projectile projectile) {
                JsonObject detail = new JsonObject();
                detail.addProperty("entityId", projectile.getEntityId());
                detail.addProperty("x", projectile.getLocation().getX());
                detail.addProperty("y", projectile.getLocation().getY());
                detail.addProperty("z", projectile.getLocation().getZ());
                detail.addProperty("velocityX", projectile.getVelocity().getX());
                if (projectile.getShooter() instanceof Entity shooter) detail.addProperty("shooter", shooter.getUniqueId().toString());
                projectiles.add(detail);
            }
            if (!(entity instanceof LivingEntity target)) continue;
            for (String tag : target.getScoreboardTags()) {
                if (!tag.startsWith("adapt-qa-guard-")) continue;
                JsonObject detail = state(target);
                detail.addProperty("entityId", target.getEntityId());
                if (target.getEquipment() != null) detail.add("hand", item(target.getEquipment().getItemInMainHand()));
                targets.add(tag.substring("adapt-qa-guard-".length()), detail);
            }
        }
        result.add("targets", targets);
        result.add("drops", drops);
        result.add("projectiles", projectiles);
        return result;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void observeSprint(PlayerToggleSprintEvent event) {
        if (event.isSprinting() && !event.isCancelled()) SPRINTS.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void aimWallArcher(EntityTargetLivingEntityEvent event) {
        if (wallAlly != null && wallAlly.isOnline() && !wallAlly.isDead()
                && event.getEntity().getScoreboardTags().contains("adapt-qa-guard-shooter")) {
            event.setTarget(wallAlly);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void observeDamage(EntityDamageEvent event) {
        JsonObject hit = new JsonObject();
        hit.addProperty("sequence", ++hitSequence);
        hit.addProperty("damage", event.getFinalDamage());
        hit.addProperty("originalDamage", event.getOriginalDamage(EntityDamageEvent.DamageModifier.BASE));
        hit.addProperty("cancelled", event.isCancelled());
        hit.addProperty("cause", event.getCause().name());
        if (event instanceof EntityDamageByEntityEvent attack) {
            hit.addProperty("critical", attack.isCritical());
            hit.addProperty("damager", attack.getDamager().getUniqueId().toString());
            if (attack.getDamager() instanceof Player player) {
                hit.addProperty("attackerFallDistance", player.getFallDistance());
                hit.addProperty("attackerSprinting", player.isSprinting());
                Long sprint = SPRINTS.get(player.getUniqueId());
                if (sprint != null) hit.addProperty("sprintAgeMs", System.currentTimeMillis() - sprint);
            }
            if (attack.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
                hit.addProperty("shooter", shooter.getUniqueId().toString());
            }
        }
        FIRST_HITS.putIfAbsent(event.getEntity().getUniqueId(), hit);
        LAST_HITS.put(event.getEntity().getUniqueId(), hit);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void observeDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null) KILLS.merge(killer.getUniqueId(), 1, Integer::sum);
    }

    private static JsonObject state(LivingEntity entity) {
        JsonObject state = new JsonObject();
        state.addProperty("health", entity.getHealth());
        state.addProperty("x", entity.getLocation().getX());
        state.addProperty("y", entity.getLocation().getY());
        state.addProperty("z", entity.getLocation().getZ());
        if (entity instanceof Mob mob && mob.getTarget() != null) state.addProperty("targetId", mob.getTarget().getEntityId());
        if (FIRST_HITS.containsKey(entity.getUniqueId())) state.add("firstHit", FIRST_HITS.get(entity.getUniqueId()));
        if (LAST_HITS.containsKey(entity.getUniqueId())) state.add("lastHit", LAST_HITS.get(entity.getUniqueId()));
        JsonArray effects = new JsonArray();
        for (PotionEffect effect : entity.getActivePotionEffects()) {
            effects.add(effect.getType().getKey().toString());
        }
        state.add("effects", effects);
        return state;
    }

    private static JsonArray inventory(ItemStack[] items) {
        JsonArray result = new JsonArray();
        for (ItemStack stack : items) {
            if (stack != null && !stack.getType().isAir()) result.add(item(stack));
        }
        return result;
    }

    private static JsonObject item(ItemStack item) {
        JsonObject result = new JsonObject();
        result.addProperty("type", item == null ? "minecraft:air" : item.getType().getKey().toString());
        if (item != null && !item.getType().isAir()) {
            result.addProperty("amount", item.getAmount());
            if (item.getItemMeta() instanceof Damageable damageable) result.addProperty("damage", damageable.getDamage());
        }
        return result;
    }

    private static ItemStack damaged(Material material, int amount) {
        ItemStack item = new ItemStack(material);
        Damageable meta = (Damageable) item.getItemMeta();
        meta.setDamage(amount);
        item.setItemMeta(meta);
        return item;
    }

    private static void cow(World world, String label, double x, double z, double health) {
        Cow cow = world.spawn(new Location(world, x, 100, z), Cow.class);
        cow.setAI(false);
        Objects.requireNonNull(cow.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(Math.max(20, health));
        cow.setHealth(health);
        cow.addScoreboardTag("adapt-qa-guard-" + label);
    }
}
