package art.arcane.adapt.gameplay;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.content.item.ChalkWandItem;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockReceiveGameEvent;
import art.arcane.adapt.platform.event.entity.EndermanAttackPlayerEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityTargetEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.RecipeView;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.WorldRef;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class UtilityFixtures {
    private static final Set<String> STAGES = Set.of("utility-cat", "utility-wall", "utility-chalk", "utility-elevator",
            "utility-wireless", "utility-cutpurse", "utility-decoy", "utility-veil", "utility-trap");
    private static final Map<UUID, JsonArray> EVENTS = new HashMap<>();
    private static final Id ENDERMAN = Id.minecraft("enderman");
    private static final Id SCULK_SENSOR = Id.minecraft("sculk_sensor");

    private UtilityFixtures() {
    }

    public static void install() {
        Fixtures.register(new UtilityFixtures());
    }

    public static boolean stage(String name, WorldRef world, PlayerRef actor, PlayerRef opponent) {
        if (!STAGES.contains(name)) {
            return false;
        }
        EVENTS.remove(actor.uuid());
        Fixtures.gameRule(world, "natural_health_regeneration", "false");
        actor.setFoodLevel(20);
        actor.setSaturation(20);
        Fixtures.teleport(opponent, world, 0.5, 100, 7.5);
        switch (name) {
            case "utility-cat" -> {
                actor.inventory().setArmor(EquipmentSlot.FEET, Fixtures.enchanted("netherite_boots", "protection", 4));
                actor.inventory().setArmor(EquipmentSlot.LEGS, Fixtures.enchanted("netherite_leggings", "protection", 4));
                actor.inventory().setArmor(EquipmentSlot.CHEST, Fixtures.enchanted("netherite_chestplate", "protection", 4));
                actor.inventory().setArmor(EquipmentSlot.HEAD, Fixtures.enchanted("netherite_helmet", "protection", 4));
                Fixtures.teleport(opponent, world, 0.5, 100, -4.5);
                Fixtures.give(opponent, "bow", 1);
                Fixtures.give(opponent, "arrow", 64);
            }
            case "utility-wall" -> {
                for (int y = 100; y <= 106; y++) {
                    for (int z = -2; z <= 2; z++) {
                        Fixtures.set(world, 1, y, z, "stone");
                    }
                }
            }
            case "utility-chalk" -> Fixtures.give(actor, ChalkWandItem.create(ChalkWandItem.Tool.STRAIGHTEDGE));
            case "utility-elevator" -> {
                Fixtures.set(world, 2, 99, 0, "air");
                Fixtures.set(world, 2, 98, 0, "stone");
                Fixtures.set(world, 3, 102, 0, "stone");
                ItemRef elevators = recipe("elevator");
                elevators.setAmount(2);
                Fixtures.give(actor, elevators);
            }
            case "utility-wireless" -> {
                Fixtures.set(world, 3, 100, 0, "redstone_lamp");
                Fixtures.give(actor, recipe("remote-redstone-torch"));
            }
            case "utility-cutpurse" -> {
                LivingRef target = (LivingRef) world.spawn(Fixtures.id("vindicator"), Fixtures.loc(world, 2.5, 100, 0.5, -90, 0), entity -> { });
                target.setAi(false);
                target.setAttributeBase(Id.minecraft("max_health"), 80);
                target.setAttributeBase(Id.minecraft("knockback_resistance"), 1);
                target.setHealth(80);
                Fixtures.tag(target, "adapt-qa-utility-target");
            }
            case "utility-veil" -> {
                Fixtures.commandIn(world, "time set 18000");
                EntityRef target = Fixtures.spawn(world, "enderman", 6.5, 100, 0.5);
                Fixtures.tag(target, "adapt-qa-utility-target");
            }
            case "utility-trap" -> Fixtures.set(world, 3, 100, 0, "sculk_sensor");
            case "utility-decoy" -> { }
            default -> throw new IllegalStateException("Unhandled utility stage " + name);
        }
        return true;
    }

    public static JsonObject snapshot(PlayerRef player) {
        JsonObject result = new JsonObject();
        result.addProperty("veilSuppressions", Fixtures.adaptPlayer(player).getData().getStat("stealth.ender-veil.stares-survived"));
        result.add("events", EVENTS.getOrDefault(player.uuid(), new JsonArray()));
        JsonArray anchors = new JsonArray();
        for (EntityRef entity : player.world().entities()) {
            if (entity.has(EntityTrait.ARMOR_STAND) && entity instanceof LivingRef stand) {
                anchors.add(state(stand));
            }
            if (Fixtures.tags(entity).contains("adapt-qa-utility-target") && entity instanceof LivingRef living) {
                result.add("target", state(living));
            }
        }
        result.add("anchors", anchors);
        result.addProperty("lamp", "true".equals(player.world().blockAt(3, 100, 0).state().property("lit")));
        result.addProperty("lowerElevator", player.world().blockAt(2, 99, 0).type().legacyName());
        result.addProperty("upperElevator", player.world().blockAt(2, 102, 0).type().legacyName());
        ChalkWandItem.WandData wand = ChalkWandItem.read(player.inventory().mainHand());
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

    @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onProjectileDamage(EntityDamageByEntityEvent event) {
        if (event.entity() instanceof PlayerRef player && event.damager() instanceof ProjectileRef && eligible(player)) {
            JsonObject entry = event("projectile", event.isCancelled());
            entry.addProperty("damage", event.finalDamage());
            entry.addProperty("projectileId", event.damager().entityId());
            entry.addProperty("sprinting", player.isSprinting());
            record(player, entry);
        }
    }

    @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onStare(EndermanAttackPlayerEvent event) {
        if (eligible(event.player())) {
            record(event.player(), event("stare", event.isCancelled()));
        }
    }

    @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onTarget(EntityTargetEvent event) {
        if (event.entity().type().equals(ENDERMAN) && event.target() instanceof PlayerRef player && eligible(player)) {
            record(player, event("target", event.isCancelled()));
        }
    }

    @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onVibration(BlockReceiveGameEvent event) {
        if (event.block().type().equals(SCULK_SENSOR) && event.entity() instanceof PlayerRef player && eligible(player)) {
            JsonObject entry = event("vibration", event.isCancelled());
            entry.addProperty("gameEvent", event.gameEvent().toString());
            record(player, entry);
        }
    }

    private static boolean eligible(PlayerRef player) {
        return Fixtures.owns(player) && Fixtures.isFixtureWorld(player.world());
    }

    private static JsonObject event(String type, boolean cancelled) {
        JsonObject entry = new JsonObject();
        entry.addProperty("type", type);
        entry.addProperty("cancelled", cancelled);
        return entry;
    }

    private static void record(PlayerRef player, JsonObject event) {
        JsonArray events = EVENTS.computeIfAbsent(player.uuid(), ignored -> new JsonArray());
        if (events.size() < 128) {
            events.add(event);
        }
    }

    private static JsonObject state(LivingRef entity) {
        JsonObject result = new JsonObject();
        result.addProperty("entityId", entity.entityId());
        result.addProperty("health", entity.health());
        result.addProperty("x", entity.location().x());
        result.addProperty("y", entity.location().y());
        result.addProperty("z", entity.location().z());
        if (entity instanceof MobRef mob) {
            result.addProperty("hasTarget", mob.target() != null);
        }
        return result;
    }

    private static ItemRef recipe(String key) {
        Optional<RecipeView> recipe = AdaptCore.platform().recipes().recipe(Id.adapt(key));
        if (recipe.isEmpty()) {
            throw new IllegalStateException("Required utility recipe unavailable: " + key);
        }
        ItemRef item = recipe.get().result().copy();
        item.setAmount(1);
        return item;
    }
}
