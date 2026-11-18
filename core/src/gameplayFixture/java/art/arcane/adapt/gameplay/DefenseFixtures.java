package art.arcane.adapt.gameplay;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.WorldRef;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DefenseFixtures {
    private static final Set<String> STAGES = Set.of("defense-unarmed", "defense-sword", "defense-axe",
            "defense-riposte", "defense-resolve", "defense-splitter", "defense-kill", "defense-cleave", "defense-log");
    private static final Id SHIELD = Id.minecraft("shield");
    private static final Map<UUID, JsonObject> HITS = new HashMap<>();
    private static long hitSequence;

    private DefenseFixtures() {
    }

    public static void install() {
        Fixtures.register(new DefenseFixtures());
    }

    public static boolean stage(String name, WorldRef world, PlayerRef actor, PlayerRef opponent) {
        if (!STAGES.contains(name)) {
            return false;
        }
        HITS.clear();
        actor.setFoodLevel(10);
        actor.setSaturation(0);
        opponent.setFoodLevel(10);
        opponent.setSaturation(0);
        actor.setCooldown(SHIELD, 0);
        opponent.setCooldown(SHIELD, 0);
        Fixtures.teleport(actor, Fixtures.loc(world, 0.5, 100, 0.5, -90, 0));
        Fixtures.teleport(opponent, Fixtures.loc(world, 2.5, 100, 0.5, 90, 0));
        switch (name) {
            case "defense-unarmed" -> { }
            case "defense-sword" -> Fixtures.give(actor, "wooden_sword", 1);
            case "defense-axe" -> Fixtures.give(actor, "wooden_axe", 1);
            case "defense-riposte" -> {
                Fixtures.give(actor, "wooden_sword", 1);
                actor.inventory().setOffHand(Fixtures.item("shield"));
                opponent.inventory().setMainHand(Fixtures.item("wooden_sword"));
            }
            case "defense-resolve" -> {
                actor.inventory().setOffHand(Fixtures.item("shield"));
                opponent.inventory().setMainHand(Fixtures.item("wooden_axe"));
            }
            case "defense-splitter" -> {
                Fixtures.give(actor, "wooden_axe", 1);
                opponent.inventory().setOffHand(Fixtures.item("shield"));
            }
            case "defense-kill" -> {
                actor.setHealth(12);
                Fixtures.teleport(opponent, world, -5.5, 100, 5.5);
                cow(world, "kill", 2.5, 0.5, 0.5);
            }
            case "defense-cleave" -> {
                Fixtures.give(actor, "wooden_axe", 1);
                Fixtures.teleport(opponent, world, -5.5, 100, 5.5);
                cow(world, "primary", 2.5, 0.5, 10);
                cow(world, "secondary", 2.8, 1.4, 10);
            }
            case "defense-log" -> {
                Fixtures.give(actor, "diamond_axe", 1);
                Fixtures.teleport(opponent, world, -5.5, 100, 5.5);
                for (int y = 100; y < 103; y++) {
                    Fixtures.set(world, 2, y, 0, "oak_log");
                }
            }
            default -> throw new IllegalStateException("Unhandled defense fixture stage " + name);
        }
        return true;
    }

    public static JsonObject snapshot(PlayerRef player) {
        JsonObject result = new JsonObject();
        result.addProperty("blocking", player.isBlocking());
        result.addProperty("sprinting", player.isSprinting());
        result.addProperty("sneaking", player.isSneaking());
        result.addProperty("shieldCooldown", player.cooldownTicks(SHIELD));
        result.addProperty("absorption", player.absorption());
        ItemRef shield = player.inventory().offHand();
        if (shield != null && !shield.isEmpty()) {
            result.addProperty("offhandDamage", shield.damage());
        }
        JsonObject hit = HITS.get(player.uuid());
        if (hit != null) {
            result.add("lastHit", hit);
        }
        JsonObject targets = new JsonObject();
        for (EntityRef entity : player.world().entities()) {
            if (!(entity instanceof LivingRef target)) {
                continue;
            }
            for (String tag : Fixtures.tags(target)) {
                if (tag.startsWith("adapt-qa-defense-")) {
                    JsonObject state = new JsonObject();
                    state.addProperty("entityId", target.entityId());
                    state.addProperty("health", target.health());
                    targets.add(tag.substring("adapt-qa-defense-".length()), state);
                }
            }
        }
        result.add("targets", targets);
        return result;
    }

    @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void observeHit(EntityDamageEvent event) {
        if (!(event.entity() instanceof PlayerRef)) {
            return;
        }
        JsonObject result = new JsonObject();
        result.addProperty("sequence", ++hitSequence);
        result.addProperty("cause", event.cause().name());
        result.addProperty("damage", event.finalDamage());
        result.addProperty("cancelled", event.isCancelled());
        if (event instanceof EntityDamageByEntityEvent attack) {
            result.addProperty("attacker", attack.damager().uuid().toString());
        }
        HITS.put(event.entity().uuid(), result);
    }

    private static void cow(WorldRef world, String label, double x, double z, double health) {
        LivingRef cow = Fixtures.spawnStill(world, "cow", x, 100, z);
        cow.setHealth(health);
        Fixtures.tag(cow, "adapt-qa-defense-" + label);
    }
}
