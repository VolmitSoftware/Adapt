package art.arcane.adapt.gameplay;

import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.ProjectileLaunchEvent;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.scheduling.Tasks;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class CombatAuditFixtures {
    private static final DataKey<Byte> RECOVERY = DataKey.adapt("arrow-recovery", DataCodecs.BYTE);
    private static final DataKey<Byte> MIRROR = DataKey.adapt("mirror-reflected", DataCodecs.BYTE);
    private static final DataKey<Double> MIRROR_DAMAGE = DataKey.adapt("mirror-damage-factor", DataCodecs.DOUBLE);
    private static final Map<UUID, JsonObject> LAST_HITS = new ConcurrentHashMap<>();
    private static final Map<UUID, JsonObject> LAST_DAMAGE = new ConcurrentHashMap<>();
    private static final Map<UUID, ConcurrentLinkedQueue<JsonObject>> LAUNCHES = new ConcurrentHashMap<>();

    private CombatAuditFixtures() {
    }

    public static void install() {
        Fixtures.register(new CombatAuditFixtures());
    }

    public static JsonObject snapshot(PlayerRef player) {
        JsonObject result = new JsonObject();
        result.addProperty("player", player.name());
        result.addProperty("health", player.health());
        Loc location = player.location();
        result.addProperty("x", location.x());
        result.addProperty("y", location.y());
        result.addProperty("z", location.z());
        result.addProperty("world", player.world().name());
        result.addProperty("thread", Thread.currentThread().getName());
        result.addProperty("foliaThreading", Tasks.isFoliaThreading());
        result.addProperty("nativeOwned", Fixtures.platform().nativeOwns(player));
        JsonObject hit = LAST_HITS.get(player.uuid());
        if (hit != null) {
            result.add("lastHit", hit);
        }
        JsonObject damage = LAST_DAMAGE.get(player.uuid());
        if (damage != null) {
            result.add("lastDamage", damage);
        }
        JsonArray launches = new JsonArray();
        ConcurrentLinkedQueue<JsonObject> records = LAUNCHES.get(player.uuid());
        if (records != null) {
            for (JsonObject record : records) {
                launches.add(record);
            }
        }
        result.add("launches", launches);
        return result;
    }

    @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(ProjectileLaunchEvent event) {
        ProjectileRef projectile = event.projectile();
        if (!(projectile.shooter() instanceof PlayerRef shooter)) {
            return;
        }
        JsonObject launch = new JsonObject();
        launch.addProperty("entityId", projectile.entityId());
        launch.addProperty("uuid", projectile.uuid().toString());
        launch.addProperty("recovery", projectile.data().getOrDefault(RECOVERY, (byte) 0) == 1);
        launch.addProperty("reflected", projectile.data().has(MIRROR));
        launch.addProperty("mirrorDamage", projectile.data().getOrDefault(MIRROR_DAMAGE, 1D));
        if (projectile.has(EntityTrait.ARROW)) {
            launch.addProperty("pierceLevel", projectile.pierceLevel());
        }
        LAUNCHES.computeIfAbsent(shooter.uuid(), ignored -> new ConcurrentLinkedQueue<>()).add(launch);
    }

    @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(EntityDamageEvent event) {
        if (!(event.entity() instanceof PlayerRef player)) {
            return;
        }
        JsonObject damage = new JsonObject();
        damage.addProperty("cause", event.cause().name());
        damage.addProperty("nativeOwned", Fixtures.platform().nativeOwns(player));
        damage.addProperty("thread", Thread.currentThread().getName());
        damage.addProperty("amount", event.finalDamage());
        LAST_DAMAGE.put(player.uuid(), damage);
    }

    @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(EntityDamageByEntityEvent event) {
        if (!(event.entity() instanceof PlayerRef victim)
                || !(event.damager() instanceof ProjectileRef projectile)
                || !(projectile.shooter() instanceof PlayerRef shooter)) {
            return;
        }
        JsonObject hit = new JsonObject();
        hit.addProperty("shooter", shooter.name());
        hit.addProperty("shooterOwned", Tasks.isOwnedByCurrentRegion(shooter));
        hit.addProperty("nativeShooterOwned", Fixtures.platform().nativeOwns(shooter));
        hit.addProperty("foliaThreading", Tasks.isFoliaThreading());
        hit.addProperty("thread", Thread.currentThread().getName());
        hit.addProperty("victimOwned", Tasks.isOwnedByCurrentRegion(victim));
        hit.addProperty("damage", event.finalDamage());
        LAST_HITS.put(victim.uuid(), hit);
    }
}
