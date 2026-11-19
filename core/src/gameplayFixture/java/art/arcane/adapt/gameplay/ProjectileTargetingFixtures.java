package art.arcane.adapt.gameplay;

import art.arcane.adapt.content.adaptation.ranged.RangedHeartseeker;
import art.arcane.adapt.content.adaptation.ranged.RangedRicochetBolt;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.ProjectileHitEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.WorldRef;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class ProjectileTargetingFixtures {
    private static final DataKey<String> SERVANT_OWNER = DataKey.adapt("tragoul_servant_owner", DataCodecs.STRING);
    private static final Map<String, LivingRef> TARGETS = new LinkedHashMap<>();
    private static JsonArray impacts = new JsonArray();
    private static JsonArray damage = new JsonArray();

    private ProjectileTargetingFixtures() {
    }

    public static void install() {
        Fixtures.register(new ProjectileTargetingFixtures());
    }

    public static void cleanup() {
        TARGETS.clear();
        impacts = new JsonArray();
        damage = new JsonArray();
    }

    public static boolean stage(String name, WorldRef world, PlayerRef actor, PlayerRef opponent) {
        if (!name.startsWith("projectile-qa-targeting-")) return false;
        cleanup();
        String trial = name.substring("projectile-qa-targeting-".length());
        String mob = trial.substring(trial.lastIndexOf('-') + 1);
        actor.setFoodLevel(17);
        actor.setSaturation(0);
        actor.setCooldown(Id.minecraft("bow"), 0);
        actor.setCooldown(Id.minecraft("iron_axe"), 0);
        actor.setCooldown(Id.minecraft("wither_skeleton_skull"), 0);
        Fixtures.teleport(opponent, world, 18.5, 100, 18.5);
        if (trial.startsWith("skull-")) {
            Fixtures.give(actor, "wither_skeleton_skull", 4);
            wall(world, 9);
            spawn(world, actor, "target", mob, 8.0, 2.0);
        } else if (trial.startsWith("axe-")) {
            Fixtures.give(actor, "iron_axe", 1);
            wall(world, 6);
            spawn(world, actor, "target", mob, 3.5, 3.17);
        } else if (trial.startsWith("heart-")) {
            Fixtures.give(actor, "bow", 1);
            Fixtures.give(actor, "arrow", 16);
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

    public static JsonObject snapshot(PlayerRef player) {
        JsonObject result = new JsonObject();
        JsonObject targets = new JsonObject();
        for (Map.Entry<String, LivingRef> entry : TARGETS.entrySet()) {
            LivingRef target = resolve(entry.getValue());
            if (!target.world().uid().equals(player.world().uid())) continue;
            JsonObject state = new JsonObject();
            Loc location = target.location();
            state.addProperty("entityId", target.entityId());
            state.addProperty("health", target.health());
            state.addProperty("dead", target.isDead());
            state.addProperty("valid", target.isValid());
            state.addProperty("x", location.x());
            state.addProperty("y", location.y());
            state.addProperty("z", location.z());
            state.addProperty("height", target.height());
            targets.add(entry.getKey(), state);
        }
        result.add("targets", targets);
        result.add("impacts", impacts);
        result.add("damage", damage);
        return result;
    }

    @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onImpact(ProjectileHitEvent event) {
        ProjectileRef projectile = event.projectile();
        if (TARGETS.isEmpty() || !projectile.world().uid().equals(TARGETS.values().iterator().next().world().uid())) return;
        JsonObject impact = new JsonObject();
        impact.addProperty("entityId", projectile.entityId());
        impact.addProperty("type", typeName(projectile));
        impact.addProperty("seeking", RangedHeartseeker.isSeekingProjectile(projectile));
        impact.addProperty("ricochets", RangedRicochetBolt.impactOf(projectile).count());
        impact.addProperty("cancelled", event.isCancelled());
        Loc at = projectile.location();
        Vec3 velocity = projectile.velocity();
        impact.addProperty("x", at.x());
        impact.addProperty("y", at.y());
        impact.addProperty("z", at.z());
        impact.addProperty("vx", velocity.x());
        impact.addProperty("vy", velocity.y());
        impact.addProperty("vz", velocity.z());
        if (event.hitEntity() != null) impact.addProperty("targetId", event.hitEntity().entityId());
        impacts.add(impact);
    }

    @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!isTarget(event.entity())) return;
        JsonObject hit = new JsonObject();
        hit.addProperty("targetId", event.entity().entityId());
        hit.addProperty("cause", event.cause().name());
        hit.addProperty("damage", event.finalDamage());
        hit.addProperty("cancelled", event.isCancelled());
        EntityRef source = event.damager();
        hit.addProperty("source", typeName(source));
        hit.addProperty("seeking", RangedHeartseeker.isSeekingProjectile(source));
        damage.add(hit);
    }

    private static boolean isTarget(EntityRef entity) {
        for (LivingRef target : TARGETS.values()) {
            if (target.uuid().equals(entity.uuid())) return true;
        }
        return false;
    }

    private static LivingRef resolve(LivingRef held) {
        return Fixtures.entity(held.uuid()) instanceof LivingRef fresh ? fresh : held;
    }

    private static String typeName(EntityRef entity) {
        return entity.type().path().toUpperCase(Locale.ROOT);
    }

    private static void wall(WorldRef world, int x) {
        for (int y = 100; y <= 104; y++) {
            for (int z = -3; z <= 3; z++) Fixtures.set(world, x, y, z, "obsidian");
        }
    }

    private static void spawn(WorldRef world, PlayerRef owner, String key, String mob, double x, double z) {
        String type = switch (mob) {
            case "cow" -> "cow";
            case "wolf" -> "wolf";
            case "husk" -> "husk";
            case "servant" -> "skeleton";
            default -> throw new IllegalArgumentException("Unknown mob: " + mob);
        };
        LivingRef target = Fixtures.spawnStill(world, type, x, 100, z);
        target.setAttributeBase(Id.minecraft("max_health"), 100);
        target.setHealth(100);
        if (mob.equals("servant")) {
            target.setEquipment(EquipmentSlot.HEAD, Fixtures.item("leather_helmet"));
            target.data().set(SERVANT_OWNER, owner.uuid().toString());
        }
        TARGETS.put(key, target);
    }
}
