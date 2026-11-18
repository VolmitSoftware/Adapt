package art.arcane.adapt.gameplay;

import art.arcane.adapt.content.item.BoundEnderPearl;
import art.arcane.adapt.content.item.BoundEyeOfEnder;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityTargetEvent;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.player.PlayerTeleportEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.PotionContents;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RiftFixtures {
    private static final Set<String> STAGES = Set.of("rift-descent", "rift-blink", "rift-magnet", "rift-pocket",
            "rift-rebound", "rift-visage", "rift-taglock", "rift-void-skin", "rift-gate", "rift-access", "rift-conduit");
    private static final Map<UUID, JsonObject> TARGETS = new HashMap<>();
    private static final Map<UUID, JsonObject> TELEPORTS = new HashMap<>();

    private RiftFixtures() {
    }

    public static void install() {
        Fixtures.register(new RiftFixtures());
    }

    public static boolean stage(String name, WorldRef world, PlayerRef actor, PlayerRef opponent) {
        if (!STAGES.contains(name)) {
            return false;
        }
        TARGETS.remove(actor.uuid());
        TELEPORTS.remove(actor.uuid());
        clear(actor.enderChest());
        actor.setCooldown(Id.minecraft("ender_pearl"), 0);
        actor.setCooldown(Id.minecraft("ender_eye"), 0);
        actor.setFoodLevel(20);
        actor.setSaturation(0);
        Fixtures.gameRule(world, "natural_health_regeneration", "false");
        Fixtures.teleport(opponent, world, 0.5, 100, 10.5);
        switch (name) {
            case "rift-descent" -> {
                ItemRef potion = Fixtures.item("potion");
                potion.setPotionContents(new PotionContents(Id.minecraft("water"), null,
                        List.of(EffectInstance.of(Id.minecraft("levitation"), 200, 0)), null));
                actor.inventory().addItem(potion);
            }
            case "rift-magnet" -> world.dropItem(new Vec3(5.5, 100.1, 0.5), Fixtures.item("diamond", 3)).setPickupDelay(0);
            case "rift-pocket" -> {
                actor.enderChest().addItem(Fixtures.item("stone", 16));
                Fixtures.set(world, 3, 100, 0, "stone");
                Fixtures.give(actor, "diamond", 3);
            }
            case "rift-rebound" -> {
                Fixtures.give(actor, "ender_pearl", 1);
                for (int x : new int[]{-8, 8}) {
                    for (int y = 100; y <= 105; y++) {
                        for (int z = -3; z <= 3; z++) {
                            Fixtures.set(world, x, y, z, "stone");
                        }
                    }
                }
            }
            case "rift-visage" -> {
                Fixtures.commandIn(world, "time set 18000");
                Fixtures.give(actor, "ender_pearl", 1);
                EntityRef enderman = Fixtures.spawn(world, "enderman", 4.5, 100, 0.5);
                Fixtures.tag(enderman, "adapt-qa-rift-target");
                for (int x = 3; x <= 5; x++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x != 4 || z != 0) {
                            Fixtures.set(world, x, 100, z, "oak_fence");
                        }
                    }
                }
                Fixtures.set(world, 4, 103, 0, "stone");
            }
            case "rift-taglock" -> {
                Fixtures.give(actor, "ender_pearl", 1);
                LivingRef cow = Fixtures.spawnStill(world, "cow", 2.5, 100, 0.5);
                Fixtures.tag(cow, "adapt-qa-rift-target");
                for (int y = 100; y <= 104; y++) {
                    for (int z = -2; z <= 2; z++) {
                        Fixtures.set(world, 9, y, z, "stone");
                    }
                }
            }
            case "rift-void-skin" -> {
                actor.setHealth(2);
                Fixtures.give(actor, "ender_pearl", 1);
                Fixtures.teleport(opponent, world, 2.5, 100, 0.5);
                Fixtures.give(opponent, "wooden_sword", 1);
            }
            case "rift-gate" -> Fixtures.give(actor, BoundEyeOfEnder.io.withData(new BoundEyeOfEnder.Data(null)));
            case "rift-access" -> {
                Fixtures.give(actor, BoundEnderPearl.io.withData(new BoundEnderPearl.Data(null)));
                chest(world, 3, 0, 4);
            }
            case "rift-conduit" -> {
                Fixtures.give(actor, "ender_pearl", 1);
                chest(world, 3, -2, 7);
                chest(world, 3, 2, 0);
            }
            default -> { }
        }
        return true;
    }

    public static JsonObject snapshot(PlayerRef player) {
        JsonObject result = new JsonObject();
        result.add("enderChest", contents(player.enderChest()));
        JsonObject targeting = TARGETS.get(player.uuid());
        if (targeting != null) {
            result.add("targeting", targeting);
        }
        JsonObject teleport = TELEPORTS.get(player.uuid());
        if (teleport != null) {
            result.add("teleport", teleport);
        }
        JsonObject drops = new JsonObject();
        for (EntityRef entity : player.world().entities()) {
            if (entity instanceof ItemEntityRef item) {
                String type = item.item().type().legacyName();
                drops.addProperty(type, (drops.has(type) ? drops.get(type).getAsInt() : 0) + item.item().amount());
            }
            if (entity instanceof LivingRef target && Fixtures.tags(entity).contains("adapt-qa-rift-target")) {
                JsonObject state = new JsonObject();
                state.addProperty("entityId", target.entityId());
                state.addProperty("health", target.health());
                state.addProperty("x", target.location().x());
                state.addProperty("y", target.location().y());
                state.addProperty("z", target.location().z());
                state.addProperty("targetingPlayer", target instanceof MobRef mob && mob.target() != null
                        && player.uuid().equals(mob.target().uuid()));
                result.add("target", state);
            }
        }
        result.add("drops", drops);
        JsonObject chests = new JsonObject();
        for (int z : new int[]{-2, 0, 2}) {
            InventoryRef container = player.world().blockAt(3, 100, z).container();
            if (container != null) {
                chests.add(Integer.toString(z), contents(container));
            }
        }
        result.add("chests", chests);
        ItemRef hand = player.inventory().mainHand();
        result.addProperty("accessBound", BoundEnderPearl.isBindableItem(hand) && BoundEnderPearl.getBlock(hand) != null);
        result.addProperty("gateBound", BoundEyeOfEnder.isBindableItem(hand) && BoundEyeOfEnder.getLocation(hand) != null);
        return result;
    }

    @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onTarget(EntityTargetEvent event) {
        if (!(event.target() instanceof PlayerRef player)
                || !Fixtures.tags(event.entity()).contains("adapt-qa-rift-target")) {
            return;
        }
        JsonObject result = new JsonObject();
        result.addProperty("cancelled", event.isCancelled());
        result.addProperty("reason", event.reason().name());
        result.addProperty("entityId", event.entity().entityId());
        TARGETS.put(player.uuid(), result);
    }

    @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        PlayerRef player = event.player();
        if (!Fixtures.owns(player) || !Fixtures.isFixtureWorld(player.world())
                || event.cause() == TeleportCause.PLUGIN && Fixtures.consumeFixtureTeleport(player, event.to())) {
            return;
        }
        JsonObject result = new JsonObject();
        result.addProperty("cause", event.cause().name());
        result.addProperty("fromX", event.from().x());
        result.addProperty("fromZ", event.from().z());
        result.addProperty("toX", event.to().x());
        result.addProperty("toZ", event.to().z());
        TELEPORTS.put(player.uuid(), result);
    }

    private static JsonObject contents(InventoryRef inventory) {
        JsonObject result = new JsonObject();
        for (ItemRef item : inventory.contents()) {
            if (item == null || item.isEmpty()) {
                continue;
            }
            String type = item.type().legacyName();
            result.addProperty(type, (result.has(type) ? result.get(type).getAsInt() : 0) + item.amount());
        }
        return result;
    }

    private static void clear(InventoryRef inventory) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            inventory.clear(slot);
        }
    }

    private static void chest(WorldRef world, int x, int z, int diamonds) {
        BlockRef block = world.blockAt(x, 100, z);
        block.setType(Id.minecraft("chest"), false);
        InventoryRef chest = block.container();
        clear(chest);
        if (diamonds > 0) {
            chest.addItem(Fixtures.item("diamond", diamonds));
        }
    }
}
