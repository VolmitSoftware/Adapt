package art.arcane.adapt.gameplay;

import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.EntityExhaustionEvent;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TraversalFixtures {
    private static LivingRef target;
    private static final List<BlockRef> elevatedBlocks = new ArrayList<>();
    private static final Map<String, double[]> exhaustionEvents = new HashMap<>();
    private static UUID actorId;

    private TraversalFixtures() {
    }

    public static void install() {
        Fixtures.register(new TraversalFixtures());
    }

    public static boolean stage(String name, WorldRef arena, PlayerRef actor, PlayerRef opponent) {
        if (!name.startsWith("traverse-qa-")) return false;
        target = null;
        actorId = actor.uuid();
        exhaustionEvents.clear();
        actor.setExhaustion(0);
        actor.setFoodLevel(20);
        actor.setSaturation(5);
        opponent.setFoodLevel(17);
        opponent.setSaturation(0);
        switch (name) {
            case "traverse-qa-runway" -> MovementFixtures.stage("move-runway", arena, actor);
            case "traverse-qa-plate" -> Fixtures.set(arena, 0, 100, -3, "stone_pressure_plate");
            case "traverse-qa-vault" -> {
                for (int x = -2; x <= 2; x++) arena.blockAt(x, 100, -2).setType(Fixtures.id("oak_fence"), true);
            }
            case "traverse-qa-ladder" -> {
                for (int y = 100; y <= 120; y++) {
                    Fixtures.set(arena, 0, y, -1, "stone");
                    Fixtures.set(arena, 0, y, 0, "ladder[facing=south]");
                    if (y > 108) {
                        elevatedBlocks.add(arena.blockAt(0, y, -1));
                        elevatedBlocks.add(arena.blockAt(0, y, 0));
                    }
                }
                Fixtures.teleport(actor, arena, 0.5, 104, 0.5);
            }
            case "traverse-qa-roll", "traverse-qa-soft" -> {
                for (int x = -2; x <= 2; x++) {
                    for (int z = -8; z <= -1; z++) {
                        Fixtures.set(arena, x, 99, z, name.equals("traverse-qa-soft") ? "sponge" : "stone");
                    }
                    for (int z = 0; z <= 2; z++) Fixtures.set(arena, x, 107, z, "stone");
                }
                Fixtures.teleport(actor, arena, 0.5, 108, 0.5);
            }
            case "traverse-qa-rubber" -> {
                for (int x = -2; x <= 2; x++) {
                    for (int z = -2; z <= 2; z++) Fixtures.set(arena, x, 99, z, "honey_block");
                }
            }
            case "traverse-qa-coral" -> Fixtures.give(actor, "tube_coral_block", 2);
            case "traverse-qa-salvage", "traverse-qa-fish", "traverse-qa-ink", "traverse-qa-tide" -> {
                MovementFixtures.stage("move-hydro", arena, actor);
                if (name.equals("traverse-qa-salvage")) Fixtures.set(arena, 2, 100, 2, "chest");
                if (name.equals("traverse-qa-fish")) {
                    MobRef fish = (MobRef) Fixtures.spawnLiving(arena, "cod", 2.5, 101, -3.5);
                    fish.setAware(false);
                    fish.setGravity(false);
                    target = fish;
                }
                if (name.equals("traverse-qa-ink")) {
                    target = Fixtures.spawnStill(arena, "drowned", 2.5, 100, -1.5);
                }
            }
            case "traverse-qa-storm" -> {
                MovementFixtures.stage("move-hydro", arena, actor);
                Fixtures.give(actor, "trident", 1);
                target = Fixtures.spawnStill(arena, "drowned", 2.5, 100, -1.5);
            }
            case "traverse-qa-rainfed" -> {
                MovementFixtures.stage("move-hydro", arena, actor);
                actor.setFoodLevel(10);
            }
            case "traverse-qa-trident" -> {
                actor.setFoodLevel(17);
                Fixtures.give(actor, "trident", 1);
            }
            case "traverse-qa-assassinate", "traverse-qa-shadowmeld" -> {
                Fixtures.teleport(opponent, arena, 18.5, 100, 18.5);
                if (name.equals("traverse-qa-assassinate")) {
                    target = Fixtures.spawnStill(arena, "cow", 2.5, 100, 0.5);
                }
            }
            default -> throw new IllegalArgumentException("Unknown traversal fixture: " + name);
        }
        return true;
    }

    public static void reset() {
        for (BlockRef block : elevatedBlocks) block.setType(Fixtures.id("air"), false);
        elevatedBlocks.clear();
        exhaustionEvents.clear();
        actorId = null;
    }

    @Handle(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void beforeExhaustion(EntityExhaustionEvent event) {
        recordExhaustion(event, 0);
    }

    @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterExhaustion(EntityExhaustionEvent event) {
        recordExhaustion(event, 1);
    }

    private void recordExhaustion(EntityExhaustionEvent event, int phase) {
        if (!event.player().uuid().equals(actorId)) return;
        double[] totals = exhaustionEvents.computeIfAbsent(event.reason().name(), key -> new double[2]);
        totals[phase] += event.exhaustion();
    }

    public static JsonObject snapshot(PlayerRef player) {
        JsonObject result = new JsonObject();
        result.addProperty("exhaustion", player.exhaustion());
        JsonObject reasons = new JsonObject();
        for (Map.Entry<String, double[]> entry : exhaustionEvents.entrySet()) {
            JsonObject totals = new JsonObject();
            totals.addProperty("before", entry.getValue()[0]);
            totals.addProperty("after", entry.getValue()[1]);
            reasons.add(entry.getKey(), totals);
        }
        result.add("exhaustionEvents", reasons);
        result.addProperty("pose", player.pose().name());
        result.addProperty("climbing", player.isClimbing());
        if (target != null && target.world().uid().equals(player.world().uid())) {
            JsonObject state = new JsonObject();
            state.addProperty("entityId", target.entityId());
            state.addProperty("health", target.health());
            state.addProperty("dead", target.isDead());
            state.addProperty("distance", target.location().distance(player.location()));
            JsonArray effects = new JsonArray();
            for (EffectInstance effect : target.effects()) {
                JsonObject detail = new JsonObject();
                detail.addProperty("type", effect.type().toString());
                detail.addProperty("amplifier", effect.amplifier());
                effects.add(detail);
            }
            state.add("effects", effects);
            result.add("target", state);
        }
        return result;
    }
}
