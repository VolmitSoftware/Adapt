package art.arcane.adapt.gameplay;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.data.BlockStorePort;
import art.arcane.adapt.api.golem.GolemAnchor;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemMode;
import art.arcane.adapt.api.golem.GolemRune;
import art.arcane.adapt.api.golem.GolemRuneType;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.node.NodeArea;
import art.arcane.adapt.api.golem.node.NodeCodec;
import art.arcane.adapt.api.golem.node.NodeCuboid;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodePlacementVerdict;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeService;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.display.BlockDisplayRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.WorldRef;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class GolemControlsFixtures {
    private static final int HEIGHT = 100;
    private static final String GOLEM_TAG = "adapt-qa-golem-controls";
    private static final String PLANTER_TAG = "adapt-qa-golem-controls-planter";

    private GolemControlsFixtures() {
    }

    public static JsonObject execute(PlayerRef player, String[] args) {
        switch (args[2]) {
            case "ready" -> {
                JsonObject ready = new JsonObject();
                ready.addProperty("ready", storeLoaded(player.world())
                    && GolemancyRuntime.nodes().orElseThrow().index().isReady(player.world().uid(), key(player.world(), 2).chunkKey()));
                return ready;
            }
            case "prepare" -> prepare(player, Objects.requireNonNull(Fixtures.host().player(args[3])));
            case "runes" -> runes(player);
            case "masterwork" -> GolemancyRuntime.registry().orElseThrow().loadedOf(player.uuid()).stream()
                .filter(handle -> Fixtures.tags(handle.entity()).contains(GOLEM_TAG)).findFirst().orElseThrow().setMasterwork(true);
            case "unlearn-runes" -> learn(player, "golem-runic-augments", 0);
            case "planting" -> planting(player);
            case "snapshot" -> {
            }
            default -> throw new IllegalArgumentException("Unknown golem controls action: " + args[2]);
        }
        return snapshot(player);
    }

    static boolean storeLoaded(WorldRef world) {
        if (!world.isLoaded()) {
            return false;
        }
        try {
            BlockStorePort.current().get(world, 0, world.minHeight(), 0, NodeCodec.SLICE);
            return true;
        } catch (IllegalStateException unloaded) {
            return false;
        }
    }

    private static void prepare(PlayerRef player, PlayerRef opponent) {
        for (PlayerRef owner : List.of(player, opponent)) {
            learn(owner, "golem-animus-binding", 5);
            learn(owner, "golem-node-weaving", 5);
        }
        Fixtures.clearInventory(player);
        player.inventory().setItem(0, GolemItems.crook());
        player.inventory().setItem(1, Fixtures.item("paper"));
        player.inventory().setItem(2, Fixtures.item("wheat_seeds", 12));
        player.inventory().setItem(8, GolemItems.crook());
        player.inventory().setHeldSlot(0);
        Fixtures.teleport(player, player.world(), 0.5D, HEIGHT, 0.5D);
        Fixtures.teleport(opponent, player.world(), 0.5D, HEIGHT, 3.5D);
        place(player, 2, NodeType.HARVEST);
        place(player, 4, NodeType.HARVEST);
        place(player, 6, NodeType.GUARD);
        place(opponent, 8, NodeType.HARVEST);
        NodeService service = GolemancyRuntime.nodes().orElseThrow();
        service.update(player.uuid(), key(player.world(), 2), current -> current.toBuilder()
            .area(new NodeCuboid(-8, -8, -8, 7, 7, 7)).build()).orElseThrow();
    }

    private static void place(PlayerRef owner, int x, NodeType type) {
        WorldRef world = owner.world();
        Fixtures.set(world, x, HEIGHT, 0, "stone");
        NodeService service = GolemancyRuntime.nodes().orElseThrow();
        NodePlacementVerdict result = service.place(owner, world.blockAt(x, HEIGHT, 0), BlockFace.UP, type,
            GolemancyRuntime.nodeWeaving().orElseThrow());
        if (result != NodePlacementVerdict.READY) {
            throw new IllegalStateException("Could not prepare " + type + " at " + x + ": " + result);
        }
    }

    private static void runes(PlayerRef player) {
        learn(player, "golem-runic-augments", 3);
        learn(player, "golem-masterwork", 3);
        MobRef entity = (MobRef) Fixtures.spawn(player.world(), "snow_golem", 2.5D, HEIGHT, 3.5D);
        Fixtures.tag(entity, GOLEM_TAG);
        GolemHandle handle = GolemancyRuntime.registry().orElseThrow().bind(player, entity, GolemBody.FROST, GolemCoreType.BLANK);
        handle.setMode(GolemMode.STAY);
        handle.setRunes(List.of(new GolemRune(GolemRuneType.MIGHT, 1), new GolemRune(GolemRuneType.EMBER, 1),
            new GolemRune(GolemRuneType.SWIFTNESS, 3)));
    }

    private static JsonObject snapshot(PlayerRef player) {
        WorldRef world = player.world();
        JsonObject result = new JsonObject();
        result.addProperty("heldSlot", player.inventory().heldSlot());
        result.addProperty("sneaking", player.isSneaking());
        result.addProperty("world", world.uid().toString());
        JsonArray nodes = new JsonArray();
        NodeService service = GolemancyRuntime.nodes().orElseThrow();
        for (int x : new int[]{2, 4, 6, 8}) {
            NodeKey key = key(world, x);
            service.node(key).ifPresent(record -> nodes.add(node(player, key, record)));
        }
        result.add("nodes", nodes);
        JsonArray displays = new JsonArray();
        for (EntityRef entity : world.entities()) {
            if (!(entity instanceof BlockDisplayRef display) || display.isVisibleByDefault()
                || !AdaptCore.platform().viewers().canSee(player, display)) {
                continue;
            }
            JsonObject shown = new JsonObject();
            shown.addProperty("entityId", display.entityId());
            shown.addProperty("block", name(display.block().type()));
            shown.addProperty("glowing", display.isGlowing());
            Vec3 size = display.transform().scale();
            JsonArray scale = new JsonArray();
            scale.add((float) size.x());
            scale.add((float) size.y());
            scale.add((float) size.z());
            shown.add("scale", scale);
            displays.add(shown);
        }
        result.add("displays", displays);
        JsonArray inventory = new JsonArray();
        for (int slot = 0; slot < player.inventory().size(); slot++) {
            ItemRef item = player.inventory().item(slot);
            if (item == null || item.isEmpty()) {
                continue;
            }
            JsonObject stack = new JsonObject();
            stack.addProperty("type", name(item.type()));
            stack.addProperty("amount", item.amount());
            inventory.add(stack);
        }
        result.add("inventory", inventory);
        for (GolemHandle handle : GolemancyRuntime.registry().orElseThrow().loadedOf(player.uuid())) {
            if (Fixtures.tags(handle.entity()).contains(GOLEM_TAG)) {
                result.addProperty("golemEntityId", handle.entity().entityId());
                result.addProperty("masterwork", handle.state().masterwork());
            }
            if (Fixtures.tags(handle.entity()).contains(PLANTER_TAG)) {
                JsonObject planting = new JsonObject();
                int crops = 0;
                for (int x = -1; x <= 1; x++) {
                    for (int z = 5; z <= 7; z++) {
                        if (world.blockAt(x, HEIGHT, z).type().equals(Id.minecraft("wheat"))) {
                            crops++;
                        }
                    }
                }
                planting.addProperty("crops", crops);
                planting.addProperty("chestSeeds", count(world.blockAt(0, HEIGHT, 9).blockInventory().contents(), "wheat_seeds"));
                planting.addProperty("cargoSeeds", count(handle.state().carriedItems(), "wheat_seeds"));
                planting.addProperty("entityId", handle.entity().entityId());
                result.add("planting", planting);
            }
        }
        return result;
    }

    private static void planting(PlayerRef player) {
        learn(player, "golem-harvester-core", 5);
        WorldRef world = player.world();
        // The arena can sit in a generated overworld biome where snow golems melt; the frost planter needs a mild one.
        Fixtures.commandIn(world, "fillbiome -4 " + (HEIGHT - 4) + " 0 4 " + (HEIGHT + 4) + " 12 minecraft:plains");
        Fixtures.set(world, 2, HEIGHT - 1, 6, "water");
        for (int x = -1; x <= 1; x++) {
            for (int z = 5; z <= 7; z++) {
                Fixtures.set(world, x, HEIGHT - 1, z, "farmland");
                Fixtures.set(world, x, HEIGHT, z, "air");
            }
        }
        Fixtures.set(world, 0, HEIGHT, 9, "chest");
        InventoryRef chest = world.blockAt(0, HEIGHT, 9).blockInventory();
        for (int slot = 0; slot < chest.size(); slot++) {
            chest.clear(slot);
        }
        chest.setItem(0, Fixtures.item("wheat_seeds", 8));
        NodeService service = GolemancyRuntime.nodes().orElseThrow();
        NodePlacementVerdict placed = service.place(player, world.blockAt(0, HEIGHT - 1, 5), BlockFace.UP, NodeType.HARVEST,
            GolemancyRuntime.nodeWeaving().orElseThrow());
        if (placed != NodePlacementVerdict.READY) {
            throw new IllegalStateException("Could not prepare planting node: " + placed);
        }
        NodeKey key = new NodeKey(world.uid(), 0, HEIGHT - 1, 5, BlockFace.UP);
        service.update(player.uuid(), key, record -> record.toBuilder().area(new NodeCuboid(-1, -1, 0, 1, 0, 2)).build()).orElseThrow();
        MobRef entity = (MobRef) Fixtures.spawn(world, "snow_golem", 0.5D, HEIGHT, 3.5D);
        Fixtures.tag(entity, PLANTER_TAG);
        GolemHandle handle = GolemancyRuntime.registry().orElseThrow().bind(player, entity, GolemBody.FROST, GolemCoreType.HARVESTER);
        handle.setHomeChest(GolemAnchor.of(world.uid(), 0, HEIGHT, 9));
        handle.setMode(GolemMode.WORK);
        GolemancyRuntime.bindListener().bound(player, handle);
    }

    static int count(List<ItemRef> items, String type) {
        Id id = Fixtures.id(type);
        int count = 0;
        for (ItemRef item : items) {
            if (item != null && !item.isEmpty() && item.type().equals(id)) {
                count += item.amount();
            }
        }
        return count;
    }

    static String name(Id type) {
        return type.path().toUpperCase(Locale.ROOT);
    }

    private static JsonObject node(PlayerRef player, NodeKey key, NodeRecord record) {
        JsonObject result = new JsonObject();
        result.addProperty("x", key.x());
        result.addProperty("type", record.type().id());
        result.addProperty("face", record.face().name());
        result.addProperty("owner", record.owner().toString());
        result.addProperty("owned", record.owner().equals(player.uuid()));
        result.addProperty("channel", record.channel() == null ? "NONE" : record.channel().name());
        result.addProperty("filters", record.filterCount());
        result.addProperty("priority", record.priority());
        result.addProperty("redstone", record.redstone().name());
        JsonArray filters = new JsonArray();
        for (ItemRef item : record.filterItems()) {
            filters.add(name(item.type()));
        }
        result.add("filterItems", filters);
        NodeCuboid area = record.area();
        JsonArray minimum = new JsonArray();
        minimum.add(area.minX());
        minimum.add(area.minY());
        minimum.add(area.minZ());
        JsonArray maximum = new JsonArray();
        maximum.add(area.maxX());
        maximum.add(area.maxY());
        maximum.add(area.maxZ());
        result.add("min", minimum);
        result.add("max", maximum);
        result.addProperty("containsNode", area.contains(0, 0, 0));
        result.addProperty("volume", area.volume());
        GolemancyRuntime.nodes().orElseThrow().renderer().bundle(key)
            .ifPresent(bundle -> result.addProperty("interactionId", bundle.hitbox().entityId()));
        int[] center = NodeArea.center(key);
        result.addProperty("centerY", center[1]);
        return result;
    }

    private static NodeKey key(WorldRef world, int x) {
        return new NodeKey(world.uid(), x, HEIGHT, 0, BlockFace.UP);
    }

    static void learn(PlayerRef player, String name, int level) {
        AdaptPlayer runtime = Objects.requireNonNull(Fixtures.adaptPlayer(player));
        Adaptation<?> adaptation = Fixtures.adaptation(name);
        runtime.getData().getSkillLine(adaptation.getSkill().getName()).setAdaptation(adaptation, level);
    }
}
