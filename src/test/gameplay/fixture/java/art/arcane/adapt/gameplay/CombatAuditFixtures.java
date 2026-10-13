package art.arcane.adapt.gameplay;

import art.arcane.adapt.util.common.scheduling.J;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.NamespacedKey;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class CombatAuditFixtures implements Listener {
    private static final NamespacedKey RECOVERY = NamespacedKey.fromString("adapt:arrow-recovery");
    private static final NamespacedKey MIRROR = NamespacedKey.fromString("adapt:mirror-reflected");
    private static final NamespacedKey MIRROR_DAMAGE = NamespacedKey.fromString("adapt:mirror-damage-factor");
    private static final Map<UUID, JsonObject> LAST_HITS = new ConcurrentHashMap<>();
    private static final Map<UUID, JsonObject> LAST_DAMAGE = new ConcurrentHashMap<>();
    private static final Map<UUID, ConcurrentLinkedQueue<JsonObject>> LAUNCHES = new ConcurrentHashMap<>();

    private CombatAuditFixtures() {
    }

    public static void install(JavaPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(new CombatAuditFixtures(), plugin);
    }

    public static JsonObject snapshot(Player player) {
        JsonObject result = new JsonObject();
        result.addProperty("player", player.getName());
        result.addProperty("health", player.getHealth());
        Location location = player.getLocation();
        result.addProperty("x", location.getX());
        result.addProperty("y", location.getY());
        result.addProperty("z", location.getZ());
        result.addProperty("world", player.getWorld().getName());
        result.addProperty("thread", Thread.currentThread().getName());
        result.addProperty("foliaThreading", J.isFoliaThreading());
        result.addProperty("nativeOwned", Bukkit.isOwnedByCurrentRegion(player));
        JsonObject hit = LAST_HITS.get(player.getUniqueId());
        if (hit != null) {
            result.add("lastHit", hit);
        }
        JsonObject damage = LAST_DAMAGE.get(player.getUniqueId());
        if (damage != null) {
            result.add("lastDamage", damage);
        }
        JsonArray launches = new JsonArray();
        ConcurrentLinkedQueue<JsonObject> records = LAUNCHES.get(player.getUniqueId());
        if (records != null) {
            for (JsonObject record : records) {
                launches.add(record);
            }
        }
        result.add("launches", launches);
        return result;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player shooter)) {
            return;
        }
        JsonObject launch = new JsonObject();
        launch.addProperty("entityId", projectile.getEntityId());
        launch.addProperty("uuid", projectile.getUniqueId().toString());
        launch.addProperty("recovery", projectile.hasMetadata("adapt-recover-arrow")
                || projectile.getPersistentDataContainer().getOrDefault(RECOVERY, PersistentDataType.BYTE, (byte) 0) == 1);
        launch.addProperty("reflected", projectile.hasMetadata("adapt-mirror-reflected")
                || projectile.getPersistentDataContainer().has(MIRROR));
        launch.addProperty("mirrorDamage", projectile.getPersistentDataContainer().getOrDefault(MIRROR_DAMAGE, PersistentDataType.DOUBLE, 1D));
        if (projectile instanceof AbstractArrow arrow) {
            launch.addProperty("pierceLevel", arrow.getPierceLevel());
        }
        LAUNCHES.computeIfAbsent(shooter.getUniqueId(), ignored -> new ConcurrentLinkedQueue<>()).add(launch);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        JsonObject damage = new JsonObject();
        damage.addProperty("cause", event.getCause().name());
        damage.addProperty("nativeOwned", Bukkit.isOwnedByCurrentRegion(player));
        damage.addProperty("thread", Thread.currentThread().getName());
        damage.addProperty("amount", event.getFinalDamage());
        LAST_DAMAGE.put(player.getUniqueId(), damage);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)
                || !(event.getDamager() instanceof Projectile projectile)
                || !(projectile.getShooter() instanceof Player shooter)) {
            return;
        }
        JsonObject hit = new JsonObject();
        hit.addProperty("shooter", shooter.getName());
        hit.addProperty("shooterOwned", J.isOwnedByCurrentRegion(shooter));
        hit.addProperty("nativeShooterOwned", Bukkit.isOwnedByCurrentRegion(shooter));
        hit.addProperty("foliaThreading", J.isFoliaThreading());
        hit.addProperty("thread", Thread.currentThread().getName());
        hit.addProperty("victimOwned", J.isOwnedByCurrentRegion(victim));
        hit.addProperty("damage", event.getFinalDamage());
        LAST_HITS.put(victim.getUniqueId(), hit);
    }
}
