package art.arcane.adapt.gameplay;

import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityAttemptSmashAttackEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityLungeEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class KineticsFixtures {
    private static final Set<String> STAGES = Set.of("kinetic-smash", "kinetic-charge", "kinetic-dead-zone",
            "kinetic-pin", "kinetic-lunge", "kinetic-mounted");
    private static final Map<UUID, JsonObject> SMASHES = new HashMap<>();
    private static final Map<UUID, JsonObject> LUNGES = new HashMap<>();
    private static final Map<UUID, JsonObject> HITS = new HashMap<>();
    private static final Map<UUID, JsonObject> MOVES = new HashMap<>();

    private KineticsFixtures() {
    }

    public static void install() {
        Fixtures.register(new KineticsFixtures());
    }

    public static boolean stage(String name, WorldRef world, PlayerRef actor, PlayerRef opponent) {
        if (!STAGES.contains(name)) return false;
        SMASHES.clear();
        LUNGES.clear();
        HITS.clear();
        MOVES.clear();
        actor.setFoodLevel(20);
        actor.setSaturation(0);
        opponent.setFoodLevel(10);
        opponent.setSaturation(0);
        Fixtures.teleport(opponent, world, -8.5, 100, 8.5);
        switch (name) {
            case "kinetic-smash" -> {
                Fixtures.set(world, 0, 105, 0, "stone");
                Fixtures.teleport(actor, Fixtures.loc(world, 0.5, 106, 0.5, -90, 0));
                Fixtures.give(actor, "mace", 1);
                LivingRef primary = cow(world, "primary", 2.5, 0.5);
                primary.setAttributeBase(Id.minecraft("armor"), 20);
                primary.setAttributeBase(Id.minecraft("armor_toughness"), 8);
                LivingRef secondary = cow(world, "secondary", 6.25, 0.5);
                secondary.setAi(true);
                secondary.setAttributeBase(Id.minecraft("movement_speed"), 0);
            }
            case "kinetic-charge" -> {
                Fixtures.teleport(actor, Fixtures.loc(world, -3.5, 100, 0.5, -90, 0));
                Fixtures.give(actor, "wooden_spear", 1);
                cow(world, "primary", 2.5, 0.5);
            }
            case "kinetic-dead-zone" -> {
                for (int y = 100; y <= 103; y++) {
                    for (int z = -1; z <= 1; z++) {
                        Fixtures.set(world, 5, y, z, "stone");
                        Fixtures.set(world, -1, y, z, "stone");
                    }
                }
                Fixtures.give(actor, "wooden_spear", 1);
                Fixtures.teleport(opponent, Fixtures.loc(world, 2.5, 100, 0.5, 90, 0));
                opponent.inventory().setMainHand(Fixtures.item("stick"));
            }
            case "kinetic-pin" -> {
                Fixtures.give(actor, "wooden_spear", 1);
                cow(world, "primary", 4.5, 0.5);
            }
            case "kinetic-lunge" -> {
                Fixtures.give(actor, Fixtures.enchanted("wooden_spear", "lunge", 1));
                cow(world, "primary", 3.5, 0.5);
            }
            case "kinetic-mounted" -> {
                Fixtures.teleport(actor, Fixtures.loc(world, -4.5, 100, 1.5, -90, 0));
                Fixtures.give(actor, "wooden_spear", 1);
                for (int x = -4; x <= 9; x++) {
                    Fixtures.set(world, x, 99, 0, "stone");
                    Fixtures.set(world, x, 100, 0, "powered_rail[shape=east_west]");
                }
                Fixtures.set(world, -5, 100, 0, "stone");
                EntityRef cart = Fixtures.spawn(world, "minecart", -3.5, 100.1, 0.5);
                Fixtures.tag(cart, "adapt-qa-kinetic-mount");
                cow(world, "primary", 1.5, 1.5);
            }
            default -> throw new IllegalStateException("Unhandled kinetics stage " + name);
        }
        return true;
    }

    public static JsonObject snapshot(PlayerRef player) {
        JsonObject result = state(player);
        result.addProperty("eyeHeight", player.eyeLocation().y() - player.location().y());
        result.add("smash", SMASHES.getOrDefault(player.uuid(), new JsonObject()));
        result.add("lunge", LUNGES.getOrDefault(player.uuid(), new JsonObject()));
        EntityRef vehicle = player.vehicle();
        result.addProperty("mounted", vehicle != null);
        if (vehicle != null) {
            result.addProperty("vehicleSpeed", vehicle.velocity().length());
            result.addProperty("vehicleId", vehicle.entityId());
        }
        JsonObject targets = new JsonObject();
        for (EntityRef entity : player.world().entities()) {
            Set<String> tags = Fixtures.tags(entity);
            if (tags.contains("adapt-qa-kinetic-mount")) result.addProperty("mountId", entity.entityId());
            if (!(entity instanceof LivingRef living)) continue;
            for (String tag : tags) {
                if (!tag.startsWith("adapt-qa-kinetic-")) continue;
                JsonObject detail = state(living);
                detail.addProperty("entityId", living.entityId());
                targets.add(tag.substring("adapt-qa-kinetic-".length()), detail);
            }
        }
        result.add("targets", targets);
        return result;
    }

    @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(PlayerMoveEvent event) {
        if (!event.changedPosition()) return;
        Loc from = event.from();
        Loc to = event.to();
        JsonObject move = new JsonObject();
        move.addProperty("distance", Math.hypot(to.x() - from.x(), to.z() - from.z()));
        move.addProperty("tick", Fixtures.host().currentTick());
        MOVES.put(event.player().uuid(), move);
    }

    @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(EntityDamageByEntityEvent event) {
        EntityRef damager = event.damager();
        JsonObject hit = new JsonObject();
        hit.addProperty("damage", event.finalDamage());
        hit.addProperty("rawDamage", event.damage());
        hit.add("attackerMove", MOVES.get(damager.uuid()));
        hit.addProperty("tick", Fixtures.host().currentTick());
        hit.addProperty("attackerSpeed", damager.velocity().withY(0).length());
        EntityRef mount = damager.vehicle();
        if (mount != null) hit.addProperty("mountSpeed", mount.velocity().length());
        HITS.put(event.entity().uuid(), hit);
    }

    @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void on(EntityAttemptSmashAttackEvent event) {
        JsonObject smash = new JsonObject();
        smash.addProperty("landed", event.result() == UseResult.ALLOW || event.result() == UseResult.DEFAULT && event.originalResult());
        smash.addProperty("fallDistance", event.entity().fallDistance());
        SMASHES.put(event.entity().uuid(), smash);
    }

    @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(EntityLungeEvent event) {
        JsonObject lunge = new JsonObject();
        lunge.addProperty("power", event.power());
        LUNGES.put(event.entity().uuid(), lunge);
    }

    private static JsonObject state(LivingRef entity) {
        JsonObject state = new JsonObject();
        Loc location = entity.location();
        state.addProperty("health", entity.health());
        state.addProperty("x", location.x());
        state.addProperty("y", location.y());
        state.addProperty("z", location.z());
        state.addProperty("speed", entity.velocity().withY(0).length());
        JsonObject attributes = new JsonObject();
        for (Id attribute : Fixtures.host().registries().ids(RegistryKind.ATTRIBUTE)) {
            if (entity.hasAttribute(attribute)) attributes.addProperty(attribute.path(), entity.attributeValue(attribute));
        }
        state.add("attributes", attributes);
        JsonArray effects = new JsonArray();
        for (EffectInstance effect : entity.effects()) {
            JsonObject value = new JsonObject();
            value.addProperty("type", effect.type().path());
            value.addProperty("amplifier", effect.amplifier());
            effects.add(value);
        }
        state.add("effects", effects);
        state.add("lastHit", HITS.getOrDefault(entity.uuid(), new JsonObject()));
        return state;
    }

    private static LivingRef cow(WorldRef world, String label, double x, double z) {
        LivingRef cow = Fixtures.spawnStill(world, "cow", x, 100, z);
        cow.setAttributeBase(Id.minecraft("max_health"), 200);
        cow.setHealth(200);
        Fixtures.tag(cow, "adapt-qa-kinetic-" + label);
        return cow;
    }
}
