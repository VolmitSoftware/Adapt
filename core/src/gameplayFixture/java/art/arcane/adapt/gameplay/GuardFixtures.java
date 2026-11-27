package art.arcane.adapt.gameplay;

import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.entity.EntityTargetEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSprintEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.WorldRef;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class GuardFixtures {
    private static final Set<String> STAGES = Set.of("guard-block", "guard-temper", "guard-mirror", "guard-interpose",
            "guard-wall", "guard-bash", "guard-cyclone", "guard-multiarmor", "guard-heirloom", "guard-machete",
            "guard-whetstone", "guard-charge", "guard-disarm", "guard-clap");
    private static final Map<UUID, JsonObject> FIRST_HITS = new HashMap<>();
    private static final Map<UUID, JsonObject> LAST_HITS = new HashMap<>();
    private static final Map<UUID, Integer> KILLS = new HashMap<>();
    private static final Map<UUID, Long> SPRINTS = new HashMap<>();
    private static final Id SHIELD = Id.minecraft("shield");
    private static final Id MAX_HEALTH = Id.minecraft("max_health");
    private static final Id KNOCKBACK_RESISTANCE = Id.minecraft("knockback_resistance");
    private static final Id MOVEMENT_SPEED = Id.minecraft("movement_speed");
    private static long hitSequence;
    private static UUID wallAlly;

    private GuardFixtures() {
    }

    public static void install() {
        Fixtures.register(new GuardFixtures());
    }

    public static boolean stage(String name, WorldRef world, PlayerRef actor, PlayerRef opponent) {
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
        actor.setExpProgress(0);
        actor.setCooldown(SHIELD, 0);
        opponent.setCooldown(SHIELD, 0);
        switch (name) {
            case "guard-block", "guard-temper" -> {
                actor.inventory().setOffHand(damaged("shield", name.equals("guard-temper") ? 100 : 0));
                opponent.inventory().setMainHand(Fixtures.item("wooden_sword"));
                if (name.equals("guard-temper")) {
                    actor.inventory().setArmor(EquipmentSlot.FEET, damaged("iron_boots", 80));
                }
            }
            case "guard-mirror" -> {
                actor.inventory().setOffHand(Fixtures.item("shield"));
                Fixtures.teleport(opponent, Fixtures.loc(world, 6.5, 100, 0.5, 90, 0));
                opponent.inventory().setMainHand(Fixtures.item("bow"));
                Fixtures.give(opponent, "arrow", 40);
            }
            case "guard-interpose" -> {
                actor.inventory().setOffHand(Fixtures.item("shield"));
                Fixtures.teleport(opponent, Fixtures.loc(world, 1.5, 100, 0.5, -90, 0));
                opponent.setHealth(6);
                Fixtures.set(world, 3, 98, 0, "stone");
                Fixtures.set(world, 3, 99, 0, "sand");
                Fixtures.set(world, 3, 100, 0, "cactus");
            }
            case "guard-wall" -> {
                wallAlly = opponent.uuid();
                actor.addEffect(EffectInstance.of(Id.minecraft("invisibility"), 600, 0).withParticles(false).withIcon(false));
                Fixtures.teleport(actor, Fixtures.loc(world, 1.5, 100, 1.5, -90, 0));
                Fixtures.teleport(opponent, world, 0.5, 100, 0.5);
                actor.inventory().setOffHand(Fixtures.item("shield"));
                MobRef skeleton = (MobRef) Fixtures.spawnStill(world, "skeleton", 3.5, 100, 0.5);
                skeleton.setEquipment(EquipmentSlot.HEAD, Fixtures.item("iron_helmet"));
                skeleton.setEquipment(EquipmentSlot.MAIN_HAND, Fixtures.item("bow"));
                skeleton.setAttributeBase(MOVEMENT_SPEED, 0);
                skeleton.setTarget(opponent);
                Fixtures.tag(skeleton, "adapt-qa-guard-shooter");
            }
            case "guard-bash", "guard-cyclone" -> {
                Fixtures.give(actor, "wooden_sword", 1);
                if (name.equals("guard-bash")) actor.inventory().setOffHand(Fixtures.item("shield"));
                Fixtures.teleport(opponent, world, -5.5, 100, 5.5);
                cow(world, "primary", 2.5, 0.5, 20);
                cow(world, "secondary", 3.5, 1.5, 20);
            }
            case "guard-multiarmor" -> {
                Fixtures.teleport(opponent, world, -5.5, 100, 5.5);
                Fixtures.give(actor, "elytra", 1);
                Fixtures.give(actor, "iron_chestplate", 1);
            }
            case "guard-heirloom" -> {
                Fixtures.give(actor, "wooden_sword", 1);
                Fixtures.teleport(opponent, world, -5.5, 100, 5.5);
                Fixtures.set(world, 2, 100, 2, "anvil");
                for (int index = 0; index < 5; index++) {
                    cow(world, "heirloom" + index, 2.5, -0.3 + index * 0.4, 0.5);
                }
            }
            case "guard-machete" -> {
                Fixtures.give(actor, "wooden_sword", 1);
                Fixtures.teleport(opponent, world, -5.5, 100, 5.5);
                for (int x = 2; x <= 3; x++) {
                    Fixtures.set(world, x, 101, 0, "oak_leaves[persistent=true]");
                }
            }
            case "guard-whetstone" -> {
                Fixtures.give(actor, "wooden_sword", 1);
                Fixtures.teleport(opponent, world, 2.5, 100, 2.5);
                Fixtures.set(world, 2, 100, 0, "grindstone");
            }
            case "guard-charge" -> Fixtures.teleport(actor, Fixtures.loc(world, -3.5, 100, 0.5, -90, 0));
            case "guard-disarm" -> {
                Fixtures.teleport(opponent, world, -5.5, 100, 5.5);
                LivingRef target = Fixtures.spawnStill(world, "zombie", 2.5, 100, 0.5);
                target.setSilent(true);
                target.setEquipment(EquipmentSlot.HEAD, Fixtures.item("iron_helmet"));
                target.setEquipment(EquipmentSlot.MAIN_HAND, Fixtures.item("iron_sword"));
                target.setAttributeBase(MAX_HEALTH, 200);
                target.setAttributeBase(KNOCKBACK_RESISTANCE, 1);
                target.setHealth(200);
                Fixtures.tag(target, "adapt-qa-guard-disarm");
            }
            case "guard-clap" -> { }
            default -> throw new IllegalStateException("Unhandled guard fixture stage " + name);
        }
        return true;
    }

    public static JsonObject snapshot(PlayerRef player) {
        JsonObject result = state(player);
        result.addProperty("kills", KILLS.getOrDefault(player.uuid(), 0));
        result.addProperty("level", player.level());
        result.addProperty("blocking", player.isBlocking());
        result.addProperty("sprinting", player.isSprinting());
        result.addProperty("shieldCooldown", player.cooldownTicks(SHIELD));
        result.add("inventory", inventory(player.inventory().contents()));
        result.add("chest", item(player.inventory().armor(EquipmentSlot.CHEST)));
        result.add("boots", item(player.inventory().armor(EquipmentSlot.FEET)));
        result.add("offhand", item(player.inventory().offHand()));
        JsonObject targets = new JsonObject();
        JsonArray drops = new JsonArray();
        JsonArray projectiles = new JsonArray();
        for (EntityRef entity : player.world().entities()) {
            if (entity instanceof ItemEntityRef dropped && dropped.location().distanceSquared(player.location()) < 144) {
                drops.add(item(dropped.item()));
            }
            if (entity instanceof ProjectileRef projectile) {
                JsonObject detail = new JsonObject();
                detail.addProperty("entityId", projectile.entityId());
                detail.addProperty("x", projectile.location().x());
                detail.addProperty("y", projectile.location().y());
                detail.addProperty("z", projectile.location().z());
                detail.addProperty("velocityX", projectile.velocity().x());
                EntityRef shooter = projectile.shooter();
                if (shooter != null) detail.addProperty("shooter", shooter.uuid().toString());
                projectiles.add(detail);
            }
            if (!(entity instanceof LivingRef target)) continue;
            for (String tag : Fixtures.tags(target)) {
                if (!tag.startsWith("adapt-qa-guard-")) continue;
                JsonObject detail = state(target);
                detail.addProperty("entityId", target.entityId());
                detail.add("hand", item(target.equipment(EquipmentSlot.MAIN_HAND)));
                targets.add(tag.substring("adapt-qa-guard-".length()), detail);
            }
        }
        result.add("targets", targets);
        result.add("drops", drops);
        result.add("projectiles", projectiles);
        return result;
    }

    @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void observeSprint(PlayerToggleSprintEvent event) {
        if (event.isSprinting() && !event.isCancelled()) SPRINTS.put(event.player().uuid(), System.currentTimeMillis());
    }

    @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void aimWallArcher(EntityTargetEvent event) {
        PlayerRef ally = wallAlly == null ? null : Fixtures.host().player(wallAlly);
        if (ally != null && ally.isOnline() && !ally.isDead()
                && Fixtures.tags(event.entity()).contains("adapt-qa-guard-shooter")) {
            event.setTarget(ally);
        }
    }

    @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void observeDamage(EntityDamageEvent event) {
        JsonObject hit = new JsonObject();
        hit.addProperty("sequence", ++hitSequence);
        hit.addProperty("damage", event.finalDamage());
        hit.addProperty("originalDamage", event.originalDamage());
        hit.addProperty("cancelled", event.isCancelled());
        hit.addProperty("cause", event.cause().name());
        if (event instanceof EntityDamageByEntityEvent attack) {
            hit.addProperty("critical", attack.isCritical());
            hit.addProperty("damager", attack.damager().uuid().toString());
            if (attack.damager() instanceof PlayerRef player) {
                hit.addProperty("attackerFallDistance", player.fallDistance());
                hit.addProperty("attackerSprinting", player.isSprinting());
                Long sprint = SPRINTS.get(player.uuid());
                if (sprint != null) hit.addProperty("sprintAgeMs", System.currentTimeMillis() - sprint);
            }
            if (attack.damager() instanceof ProjectileRef projectile && projectile.shooter() != null) {
                hit.addProperty("shooter", projectile.shooter().uuid().toString());
            }
        }
        FIRST_HITS.putIfAbsent(event.entity().uuid(), hit);
        LAST_HITS.put(event.entity().uuid(), hit);
    }

    @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void observeDeath(EntityDeathEvent event) {
        PlayerRef killer = event.entity().killer();
        if (killer != null) KILLS.merge(killer.uuid(), 1, Integer::sum);
    }

    private static JsonObject state(LivingRef entity) {
        JsonObject state = new JsonObject();
        state.addProperty("health", entity.health());
        state.addProperty("x", entity.location().x());
        state.addProperty("y", entity.location().y());
        state.addProperty("z", entity.location().z());
        if (entity instanceof MobRef mob && mob.target() != null) state.addProperty("targetId", mob.target().entityId());
        if (FIRST_HITS.containsKey(entity.uuid())) state.add("firstHit", FIRST_HITS.get(entity.uuid()));
        if (LAST_HITS.containsKey(entity.uuid())) state.add("lastHit", LAST_HITS.get(entity.uuid()));
        JsonArray effects = new JsonArray();
        for (EffectInstance effect : entity.effects()) {
            effects.add(effect.type().toString());
        }
        state.add("effects", effects);
        return state;
    }

    private static JsonArray inventory(List<ItemRef> items) {
        JsonArray result = new JsonArray();
        for (ItemRef stack : items) {
            if (stack != null && !stack.isEmpty()) result.add(item(stack));
        }
        return result;
    }

    private static JsonObject item(ItemRef item) {
        JsonObject result = new JsonObject();
        result.addProperty("type", item == null || item.isEmpty() ? "minecraft:air" : item.type().toString());
        if (item != null && !item.isEmpty()) {
            result.addProperty("amount", item.amount());
            result.addProperty("damage", item.damage());
        }
        return result;
    }

    private static ItemRef damaged(String type, int amount) {
        ItemRef item = Fixtures.item(type);
        item.setDamage(amount);
        return item;
    }

    private static void cow(WorldRef world, String label, double x, double z, double health) {
        LivingRef cow = Fixtures.spawnStill(world, "cow", x, 100, z);
        cow.setAttributeBase(MAX_HEALTH, Math.max(20, health));
        cow.setHealth(health);
        Fixtures.tag(cow, "adapt-qa-guard-" + label);
    }
}
