package art.arcane.adapt.gameplay;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.data.WorldData;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemMode;
import art.arcane.adapt.api.golem.GolemRune;
import art.arcane.adapt.api.golem.GolemRuneType;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.node.NodeArea;
import art.arcane.adapt.api.golem.node.NodeCuboid;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodePlacementVerdict;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeService;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowman;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;

import java.util.List;
import java.util.Objects;

public final class GolemControlsFixtures {
    private static final int HEIGHT = 100;
    private static final String GOLEM_TAG = "adapt-qa-golem-controls";
    private static final String PLANTER_TAG = "adapt-qa-golem-controls-planter";

    private GolemControlsFixtures() {
    }

    public static JsonObject execute(Player player, String[] args) {
        switch (args[2]) {
            case "ready" -> {
                JsonObject ready = new JsonObject();
                ready.addProperty("ready", WorldData.loaded(player.getWorld()).isPresent()
                    && GolemancyRuntime.nodes().orElseThrow().index().isReady(player.getWorld().getUID(), key(player.getWorld(), 2).chunkKey()));
                return ready;
            }
            case "prepare" -> prepare(player, Objects.requireNonNull(Bukkit.getPlayerExact(args[3])));
            case "runes" -> runes(player);
            case "masterwork" -> GolemancyRuntime.registry().orElseThrow().loadedOf(player.getUniqueId()).stream()
                .filter(handle -> handle.entity().getScoreboardTags().contains(GOLEM_TAG)).findFirst().orElseThrow().setMasterwork(true);
            case "unlearn-runes" -> learn(player, "golem-runic-augments", 0);
            case "planting" -> planting(player);
            case "snapshot" -> {
            }
            default -> throw new IllegalArgumentException("Unknown golem controls action: " + args[2]);
        }
        return snapshot(player);
    }

    private static void prepare(Player player, Player opponent) {
        for (Player owner : List.of(player, opponent)) {
            learn(owner, "golem-animus-binding", 5);
            learn(owner, "golem-node-weaving", 5);
        }
        player.getInventory().clear();
        player.getInventory().setItem(0, GolemItems.crook());
        player.getInventory().setItem(1, new ItemStack(Material.PAPER));
        player.getInventory().setItem(2, new ItemStack(Material.WHEAT_SEEDS, 12));
        player.getInventory().setItem(8, GolemItems.crook());
        player.getInventory().setHeldItemSlot(0);
        player.teleport(new Location(player.getWorld(), 0.5D, HEIGHT, 0.5D));
        opponent.teleport(new Location(player.getWorld(), 0.5D, HEIGHT, 3.5D));
        place(player, 2, NodeType.HARVEST);
        place(player, 4, NodeType.HARVEST);
        place(player, 6, NodeType.GUARD);
        place(opponent, 8, NodeType.HARVEST);
        NodeService service = GolemancyRuntime.nodes().orElseThrow();
        service.update(player.getUniqueId(), key(player.getWorld(), 2), current -> current.toBuilder()
            .area(new NodeCuboid(-8, -8, -8, 7, 7, 7)).build()).orElseThrow();
    }

    private static void place(Player owner, int x, NodeType type) {
        World world = owner.getWorld();
        world.getBlockAt(x, HEIGHT, 0).setType(Material.STONE, false);
        NodeService service = GolemancyRuntime.nodes().orElseThrow();
        NodePlacementVerdict result = service.place(owner, world.getBlockAt(x, HEIGHT, 0), BlockFace.UP, type,
            GolemancyRuntime.nodeWeaving().orElseThrow());
        if (result != NodePlacementVerdict.READY) {
            throw new IllegalStateException("Could not prepare " + type + " at " + x + ": " + result);
        }
    }

    private static void runes(Player player) {
        learn(player, "golem-runic-augments", 3);
        learn(player, "golem-masterwork", 3);
        Snowman entity = player.getWorld().spawn(new Location(player.getWorld(), 2.5D, HEIGHT, 3.5D), Snowman.class);
        entity.addScoreboardTag(GOLEM_TAG);
        GolemHandle handle = GolemancyRuntime.registry().orElseThrow().bind(player, entity, GolemBody.FROST, GolemCoreType.BLANK);
        handle.setMode(GolemMode.STAY);
        handle.setRunes(List.of(new GolemRune(GolemRuneType.MIGHT, 1), new GolemRune(GolemRuneType.EMBER, 1),
            new GolemRune(GolemRuneType.SWIFTNESS, 3)));
    }

    private static JsonObject snapshot(Player player) {
        JsonObject result = new JsonObject();
        result.addProperty("heldSlot", player.getInventory().getHeldItemSlot());
        result.addProperty("sneaking", player.isSneaking());
        result.addProperty("world", player.getWorld().getUID().toString());
        JsonArray nodes = new JsonArray();
        NodeService service = GolemancyRuntime.nodes().orElseThrow();
        for (int x : new int[]{2, 4, 6, 8}) {
            NodeKey key = key(player.getWorld(), x);
            service.node(key).ifPresent(record -> nodes.add(node(player, key, record)));
        }
        result.add("nodes", nodes);
        JsonArray displays = new JsonArray();
        for (BlockDisplay display : player.getWorld().getEntitiesByClass(BlockDisplay.class)) {
            if (!display.getScoreboardTags().contains("adapt_viewer_display") || !player.canSee(display)) {
                continue;
            }
            JsonObject shown = new JsonObject();
            shown.addProperty("entityId", display.getEntityId());
            shown.addProperty("block", display.getBlock().getMaterial().name());
            shown.addProperty("glowing", display.isGlowing());
            Transformation transform = display.getTransformation();
            JsonArray scale = new JsonArray();
            scale.add(transform.getScale().x);
            scale.add(transform.getScale().y);
            scale.add(transform.getScale().z);
            shown.add("scale", scale);
            displays.add(shown);
        }
        result.add("displays", displays);
        JsonArray inventory = new JsonArray();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            JsonObject stack = new JsonObject();
            stack.addProperty("type", item.getType().name());
            stack.addProperty("amount", item.getAmount());
            inventory.add(stack);
        }
        result.add("inventory", inventory);
        for (GolemHandle handle : GolemancyRuntime.registry().orElseThrow().loadedOf(player.getUniqueId())) {
            if (handle.entity().getScoreboardTags().contains(GOLEM_TAG)) {
                result.addProperty("golemEntityId", handle.entity().getEntityId());
                result.addProperty("masterwork", handle.state().masterwork());
            }
            if (handle.entity().getScoreboardTags().contains(PLANTER_TAG)) {
                JsonObject planting = new JsonObject();
                int crops = 0;
                for (int x = -1; x <= 1; x++) {
                    for (int z = 5; z <= 7; z++) {
                        if (player.getWorld().getBlockAt(x, HEIGHT, z).getType() == Material.WHEAT) {
                            crops++;
                        }
                    }
                }
                planting.addProperty("crops", crops);
                Chest chest = (Chest) player.getWorld().getBlockAt(0, HEIGHT, 9).getState();
                planting.addProperty("chestSeeds", count(chest.getBlockInventory().getContents(), Material.WHEAT_SEEDS));
                planting.addProperty("cargoSeeds", count(handle.state().carriedItems().toArray(ItemStack[]::new), Material.WHEAT_SEEDS));
                planting.addProperty("entityId", handle.entity().getEntityId());
                result.add("planting", planting);
            }
        }
        return result;
    }

    private static void planting(Player player) {
        learn(player, "golem-harvester-core", 5);
        World world = player.getWorld();
        world.getBlockAt(2, HEIGHT - 1, 6).setType(Material.WATER, false);
        for (int x = -1; x <= 1; x++) {
            for (int z = 5; z <= 7; z++) {
                world.getBlockAt(x, HEIGHT - 1, z).setType(Material.FARMLAND, false);
                world.getBlockAt(x, HEIGHT, z).setType(Material.AIR, false);
            }
        }
        world.getBlockAt(0, HEIGHT, 9).setType(Material.CHEST, false);
        Chest chest = (Chest) world.getBlockAt(0, HEIGHT, 9).getState();
        chest.getBlockInventory().clear();
        chest.getBlockInventory().setItem(0, new ItemStack(Material.WHEAT_SEEDS, 8));
        NodeService service = GolemancyRuntime.nodes().orElseThrow();
        NodePlacementVerdict placed = service.place(player, world.getBlockAt(0, HEIGHT - 1, 5), BlockFace.UP, NodeType.HARVEST,
            GolemancyRuntime.nodeWeaving().orElseThrow());
        if (placed != NodePlacementVerdict.READY) {
            throw new IllegalStateException("Could not prepare planting node: " + placed);
        }
        NodeKey key = new NodeKey(world.getUID(), 0, HEIGHT - 1, 5, BlockFace.UP);
        service.update(player.getUniqueId(), key, record -> record.toBuilder().area(new NodeCuboid(-1, -1, 0, 1, 0, 2)).build()).orElseThrow();
        Snowman entity = world.spawn(new Location(world, 0.5D, HEIGHT, 3.5D), Snowman.class);
        entity.addScoreboardTag(PLANTER_TAG);
        GolemHandle handle = GolemancyRuntime.registry().orElseThrow().bind(player, entity, GolemBody.FROST, GolemCoreType.HARVESTER);
        handle.setHomeChest(GolemState.Anchor.of(world.getUID(), 0, HEIGHT, 9));
        handle.setMode(GolemMode.WORK);
        GolemancyRuntime.bindListener().bound(player, handle);
    }

    private static int count(ItemStack[] items, Material type) {
        int count = 0;
        for (ItemStack item : items) {
            if (item != null && item.getType() == type) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private static JsonObject node(Player player, NodeKey key, NodeRecord record) {
        JsonObject result = new JsonObject();
        result.addProperty("x", key.x());
        result.addProperty("type", record.type().id());
        result.addProperty("face", record.face().name());
        result.addProperty("owner", record.owner().toString());
        result.addProperty("owned", record.owner().equals(player.getUniqueId()));
        result.addProperty("channel", record.channel() == null ? "NONE" : record.channel().name());
        result.addProperty("filters", record.filterCount());
        result.addProperty("priority", record.priority());
        result.addProperty("redstone", record.redstone().name());
        JsonArray filters = new JsonArray();
        for (ItemStack item : record.filterItems()) {
            filters.add(item.getType().name());
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
        serviceHitbox(key, result);
        int[] center = NodeArea.center(key);
        result.addProperty("centerY", center[1]);
        return result;
    }

    private static void serviceHitbox(NodeKey key, JsonObject result) {
        GolemancyRuntime.nodes().orElseThrow().renderer().bundle(key)
            .ifPresent(bundle -> result.addProperty("interactionId", bundle.hitbox().getEntityId()));
    }

    private static NodeKey key(World world, int x) {
        return new NodeKey(world.getUID(), x, HEIGHT, 0, BlockFace.UP);
    }

    private static void learn(Player player, String name, int level) {
        AdaptPlayer runtime = Objects.requireNonNull(Adapt.instance.getAdaptServer().getPlayer(player));
        for (Skill<?> skill : Adapt.instance.getAdaptServer().getSkillRegistry().getAllSkills()) {
            for (Adaptation<?> adaptation : skill.getAdaptations()) {
                if (adaptation.getName().equals(name)) {
                    runtime.getData().getSkillLine(skill.getName()).setAdaptation(adaptation, level);
                    return;
                }
            }
        }
        throw new IllegalArgumentException("Unknown adaptation: " + name);
    }
}
