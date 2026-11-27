package art.arcane.adapt.gameplay;

import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class HarvestFixtures {
    private static final Set<String> STAGES = Set.of("harvest-deep", "harvest-obsidian", "harvest-stone",
            "harvest-fragile", "harvest-spawner", "harvest-wet", "harvest-earth", "harvest-fall",
            "harvest-seeds", "harvest-spores", "harvest-compost", "harvest-growth", "harvest-bees", "harvest-shield");
    private static final List<BlockRef> CELLS = new ArrayList<>();
    private static final Map<UUID, JsonObject> FALLS = new HashMap<>();
    private static final Id MYCELIUM = Id.minecraft("mycelium");
    private static final Id WHEAT = Id.minecraft("wheat");
    private static final Id COMPOSTER = Id.minecraft("composter");

    private HarvestFixtures() {
    }

    public static void install() {
        Fixtures.register(new HarvestFixtures());
    }

    public static boolean stage(String name, WorldRef world, PlayerRef actor, PlayerRef opponent) {
        if (!STAGES.contains(name)) {
            return false;
        }
        CELLS.clear();
        FALLS.remove(actor.uuid());
        Fixtures.gameRule(world, "random_tick_speed", "0");
        Fixtures.gameRule(world, "natural_health_regeneration", "false");
        actor.setFoodLevel(20);
        actor.setSaturation(0);
        actor.setCooldown(Id.minecraft("composter"), 0);
        actor.setCooldown(Id.minecraft("wheat_seeds"), 0);
        actor.setCooldown(Id.minecraft("diamond_shovel"), 0);
        Fixtures.teleport(opponent, world, 0.5, 100, 6.5);
        switch (name) {
            case "harvest-deep" -> toolBlock(world, actor, "diamond_pickaxe", "deepslate");
            case "harvest-obsidian" -> toolBlock(world, actor, "diamond_pickaxe", "obsidian");
            case "harvest-stone" -> {
                toolBlock(world, actor, "diamond_pickaxe", "stone");
                cell(world, 3, 100, 1, "stone");
                cell(world, 3, 101, 0, "stone");
                cell(world, 3, 101, 1, "stone");
            }
            case "harvest-fragile" -> {
                cell(world, 3, 100, 0, "stone");
                ItemRef pickaxe = Fixtures.item("diamond_pickaxe");
                pickaxe.setDamage(Fixtures.items().maxDurability(Id.minecraft("diamond_pickaxe")) - 1);
                Fixtures.give(actor, pickaxe);
            }
            case "harvest-spawner" -> toolBlock(world, actor, "diamond_pickaxe", "spawner");
            case "harvest-wet" -> {
                toolBlock(world, actor, "diamond_shovel", "clay");
                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        Fixtures.set(world, x, 100, z, "water");
                    }
                }
            }
            case "harvest-earth" -> {
                Fixtures.give(actor, "diamond_shovel", 1);
                LivingRef zombie = Fixtures.spawnStill(world, "zombie", 3.5, 100, 0.5);
                zombie.setEquipment(EquipmentSlot.HEAD, Fixtures.item("leather_helmet"));
                Fixtures.tag(zombie, "adapt-qa-harvest-target");
            }
            case "harvest-fall" -> {
                for (int x = -4; x <= 4; x++) {
                    for (int z = -4; z <= 4; z++) {
                        cell(world, x, 99, z, "dirt");
                    }
                }
                Fixtures.set(world, 0, 107, 0, "stone");
                Fixtures.teleport(actor, world, 0.5, 108, 0.5);
            }
            case "harvest-seeds" -> {
                farmland(world, 3, false);
                Fixtures.give(actor, "wheat_seeds", 64);
            }
            case "harvest-spores" -> {
                for (int x = -6; x <= 9; x++) {
                    for (int z = -6; z <= 6; z++) {
                        cell(world, x, 99, z, "dirt");
                    }
                }
                Fixtures.set(world, 3, 99, 0, "mycelium");
                Fixtures.give(actor, "red_mushroom", 64);
            }
            case "harvest-compost" -> {
                cell(world, 3, 100, 0, "composter");
                world.dropItem(new Vec3(4.5, 100.1, 0.5), Fixtures.item("wheat_seeds", 64)).setPickupDelay(0);
            }
            case "harvest-growth", "harvest-bees" -> {
                Fixtures.afterCommands(actor.location(), () -> farmland(world, 18, true));
                if (name.equals("harvest-bees")) {
                    Fixtures.give(actor, "dandelion", 1);
                    Fixtures.spawnStill(world, "bee", 3.5, 102, 0.5);
                }
            }
            case "harvest-shield" -> {
                Fixtures.teleport(opponent, world, 2.5, 100, 0.5);
                Fixtures.give(opponent, "wooden_sword", 1);
            }
            default -> throw new IllegalStateException("Unhandled harvest stage " + name);
        }
        return true;
    }

    public static JsonObject snapshot(PlayerRef player) {
        JsonObject result = new JsonObject();
        if (FALLS.containsKey(player.uuid())) result.add("fall", FALLS.get(player.uuid()));
        JsonObject blocks = new JsonObject();
        int crops = 0;
        int cropAgeTotal = 0;
        int mycelium = 0;
        for (BlockRef block : CELLS) {
            if (!block.world().uid().equals(player.world().uid())) {
                continue;
            }
            Id type = block.type();
            if (CELLS.size() <= 16) {
                String key = block.x() + "," + block.y() + "," + block.z();
                blocks.addProperty(key, type.legacyName());
            }
            if (type.equals(MYCELIUM)) {
                mycelium++;
            }
            BlockRef above = block.relative(0, 1, 0);
            if (above.type().equals(WHEAT)) {
                crops++;
                cropAgeTotal += above.state().age();
            }
            if (type.equals(COMPOSTER)) {
                result.addProperty("compostLevel", Integer.parseInt(block.state().property("level")));
            }
        }
        result.add("blocks", blocks);
        result.addProperty("crops", crops);
        result.addProperty("cropAgeTotal", cropAgeTotal);
        result.addProperty("mycelium", mycelium);
        ItemRef hand = player.inventory().mainHand();
        result.addProperty("heldType", hand.type().legacyName());
        result.addProperty("heldDamage", hand.isEmpty() ? 0 : hand.damage());
        JsonObject drops = new JsonObject();
        for (EntityRef entity : player.world().entities()) {
            if (entity instanceof ItemEntityRef item) {
                String material = item.item().type().legacyName();
                int before = drops.has(material) ? drops.get(material).getAsInt() : 0;
                drops.addProperty(material, before + item.item().amount());
            }
            if (entity instanceof LivingRef target && Fixtures.tags(entity).contains("adapt-qa-harvest-target")) {
                JsonObject state = new JsonObject();
                state.addProperty("health", target.health());
                state.addProperty("x", target.location().x());
                JsonObject effects = new JsonObject();
                for (EffectInstance effect : target.effects()) {
                    effects.addProperty(effect.type().toString(), effect.amplifier());
                }
                state.add("effects", effects);
                result.add("target", state);
            }
        }
        result.add("drops", drops);
        return result;
    }

    @Handle(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void beforeFall(EntityDamageEvent event) {
        observeFall(event, "before");
    }

    @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void afterFall(EntityDamageEvent event) {
        observeFall(event, "after");
    }

    private void observeFall(EntityDamageEvent event, String phase) {
        if (event.cause() != DamageCause.FALL || !(event.entity() instanceof PlayerRef player)
                || !Fixtures.owns(player)) return;
        JsonObject state = new JsonObject();
        Loc at = player.location();
        state.addProperty("x", at.x());
        state.addProperty("y", at.y());
        state.addProperty("z", at.z());
        state.addProperty("world", at.world().name());
        state.addProperty("damage", event.damage());
        state.addProperty("cancelled", event.isCancelled());
        AdaptPlayer adapted = Fixtures.adaptPlayer(player);
        AdaptPlayer.FxPosition position = adapted.getFxPosition();
        if (position != null) {
            JsonObject feedback = new JsonObject();
            feedback.addProperty("x", position.x());
            feedback.addProperty("y", position.y());
            feedback.addProperty("z", position.z());
            feedback.addProperty("world", position.world().name());
            state.add("feedbackPosition", feedback);
        }
        FALLS.computeIfAbsent(player.uuid(), ignored -> new JsonObject()).add(phase, state);
    }

    private static void toolBlock(WorldRef world, PlayerRef actor, String tool, String block) {
        Fixtures.give(actor, tool, 1);
        cell(world, 3, 100, 0, block);
    }

    private static void farmland(WorldRef world, int radius, boolean crops) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x == 0 && z == 0) {
                    continue;
                }
                cell(world, x, 99, z, "farmland");
                if (crops) {
                    Fixtures.set(world, x, 100, z, "wheat");
                }
            }
        }
    }

    private static void cell(WorldRef world, int x, int y, int z, String material) {
        Fixtures.set(world, x, y, z, material);
        CELLS.add(world.blockAt(x, y, z));
    }
}
