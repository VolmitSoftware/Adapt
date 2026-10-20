package art.arcane.adapt.gameplay;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.data.WorldData;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemMode;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemRoster;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeCuboid;
import art.arcane.adapt.api.golem.node.NodeFaces;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodePlacementVerdict;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeService;
import art.arcane.adapt.api.golem.node.NodeWeavingPolicy;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class LumberReplantFixtures {
    private static final int TREE_X = 2;
    private static final int TREE_Y = 100;
    private static final int TREE_Z = 0;
    private static final int TREE_LOGS = 5;
    private static final int CHEST_X = -4;
    private static final List<String> REQUIRED = List.of("golem-animus-binding", "golem-node-weaving", "golem-copper-frame", "golem-lumber-core");
    private static final Map<UUID, Stage> STAGES = new HashMap<>();
    private static final Map<UUID, Integer> PREPARED = new HashMap<>();

    private LumberReplantFixtures() {
    }

    public static JsonObject execute(Player player, String[] args) {
        return switch (args[2]) {
            case "prepare" -> prepare(player);
            case "ready" -> ready(player);
            case "stage" -> stage(player);
            case "seed" -> seed(player);
            case "snapshot" -> snapshot(player, current(player));
            case "cleanup" -> cleanup(player);
            default -> throw new IllegalArgumentException("Unknown lumber replant action " + args[2]);
        };
    }

    private static JsonObject prepare(Player player) {
        World world = player.getWorld();
        if (!world.getName().startsWith("adapt_gameplay_")) {
            throw new IllegalStateException("Lumber stage requires the gameplay arena");
        }
        player.teleport(new Location(world, -6.5D, TREE_Y, -5.5D));
        AdaptGameplayFixture fixture = JavaPlugin.getPlugin(AdaptGameplayFixture.class);
        for (int x = -1; x <= 0; x++) {
            for (int z = -1; z <= 0; z++) {
                world.addPluginChunkTicket(x, z, fixture);
            }
        }
        PREPARED.put(player.getUniqueId(), Bukkit.getCurrentTick());
        return ready(player);
    }

    private static JsonObject ready(Player player) {
        World world = player.getWorld();
        Integer prepared = PREPARED.get(player.getUniqueId());
        boolean ready = prepared != null && Bukkit.getCurrentTick() - prepared >= 2 && WorldData.loaded(world).isPresent();
        NodeService nodes = GolemancyRuntime.nodes().orElseThrow();
        for (int x = -1; x <= 0; x++) {
            for (int z = -1; z <= 0; z++) {
                ready &= world.isChunkLoaded(x, z) && nodes.index().isReady(world.getUID(), GolemRoster.chunkKey(x, z));
            }
        }
        JsonObject result = new JsonObject();
        result.addProperty("ready", ready);
        return result;
    }

    private static JsonObject stage(Player player) {
        if (STAGES.containsKey(player.getUniqueId())) {
            throw new IllegalStateException("Clean up the previous Lumber stage");
        }
        World world = player.getWorld();
        if (!world.getName().startsWith("adapt_gameplay_")) {
            throw new IllegalStateException("Lumber stage requires the gameplay arena");
        }
        if (!ready(player).get("ready").getAsBoolean()) {
            throw new IllegalStateException("Prepare the Lumber chunks and wait until ready before placement");
        }
        AdaptPlayer runtime = Objects.requireNonNull(Adapt.instance.getAdaptServer().getPlayer(player));
        if (!runtime.isRuntimeReady()) {
            throw new IllegalStateException("Player runtime is not ready");
        }
        Skill<?> skill = Objects.requireNonNull(Adapt.instance.getAdaptServer().getSkillRegistry().getSkill("golemancy"));
        Map<Adaptation<?>, Integer> previous = new HashMap<>();
        for (Adaptation<?> adaptation : skill.getAdaptations()) {
            if (REQUIRED.contains(adaptation.getName())) {
                previous.put(adaptation, adaptation.getLevel(runtime));
                runtime.getData().getSkillLine(skill.getName()).setAdaptation(adaptation,
                    adaptation.getName().equals("golem-lumber-core") ? 2 : adaptation.getMaxLevel());
            }
        }
        player.getInventory().clear();
        world.getBlockAt(TREE_X, TREE_Y - 1, TREE_Z).setType(Material.GRASS_BLOCK, false);
        for (int y = TREE_Y; y < TREE_Y + TREE_LOGS; y++) {
            world.getBlockAt(TREE_X, y, TREE_Z).setType(Material.OAK_LOG, false);
        }
        Leaves leaves = (Leaves) Material.OAK_LEAVES.createBlockData();
        leaves.setPersistent(true);
        for (int y = TREE_Y + TREE_LOGS - 1; y <= TREE_Y + TREE_LOGS; y++) {
            for (int x = TREE_X - 1; x <= TREE_X + 1; x++) {
                for (int z = TREE_Z - 1; z <= TREE_Z + 1; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType().isAir()) {
                        block.setBlockData(leaves, false);
                    }
                }
            }
        }
        world.getBlockAt(CHEST_X, TREE_Y, TREE_Z).setType(Material.CHEST, false);
        ((Chest) world.getBlockAt(CHEST_X, TREE_Y, TREE_Z).getState()).getBlockInventory().clear();
        NodeService nodes = GolemancyRuntime.nodes().orElseThrow();
        NodeWeavingPolicy policy = GolemancyRuntime.nodeWeaving().orElseThrow();
        Block anchor = world.getBlockAt(0, TREE_Y - 1, 0);
        NodePlacementVerdict verdict = nodes.place(player, anchor, BlockFace.UP, NodeType.LUMBER, policy);
        if (verdict != NodePlacementVerdict.READY) {
            throw new IllegalStateException("Lumber node placement failed: " + verdict);
        }
        NodeKey node = NodeKey.of(anchor, BlockFace.UP);
        nodes.update(player.getUniqueId(), node, record -> record.toBuilder().area(NodeCuboid.symmetric(3, 0, 3)).build()).orElseThrow();
        CopperGolem entity = world.spawn(new Location(world, 0.5D, TREE_Y, 2.5D), CopperGolem.class);
        GolemHandle handle = GolemancyRuntime.registry().orElseThrow().bind(player, entity, GolemBody.COPPER, GolemCoreType.LUMBER);
        handle.setHomeChest(GolemState.Anchor.of(world.getUID(), CHEST_X, TREE_Y, TREE_Z));
        handle.setMode(GolemMode.WORK);
        GolemancyRuntime.bindListener().bound(player, handle);
        Stage stage = new Stage(handle, node, previous);
        STAGES.put(player.getUniqueId(), stage);
        return snapshot(player, stage);
    }

    private static JsonObject seed(Player player) {
        Stage stage = current(player);
        JsonObject before = snapshot(player, stage);
        if (stage.seed != null || before.get("logs").getAsInt() != 0 || before.get("tasksCompleted").getAsLong() < 1L
            || !before.get("stump").getAsString().equals(Material.AIR.name())) {
            throw new IllegalStateException("Seed must follow a completed felling without a sapling");
        }
        GolemancyRuntime.nodes().orElseThrow().update(player.getUniqueId(), stage.node,
            record -> record.toBuilder().area(NodeCuboid.symmetric(32, 32, 32)).build()).orElseThrow();
        stage.seed = player.getWorld().dropItem(new Location(player.getWorld(), TREE_X + 1.5D, TREE_Y + 0.2D, TREE_Z + 0.5D),
            new ItemStack(Material.OAK_SAPLING, 2));
        stage.seed.setOwner(player.getUniqueId());
        stage.seed.setThrower(player.getUniqueId());
        stage.seed.setPickupDelay(20);
        stage.seed.setVelocity(new Vector());
        return snapshot(player, stage);
    }

    private static JsonObject snapshot(Player player, Stage stage) {
        World world = player.getWorld();
        int logs = 0;
        for (int y = TREE_Y; y < TREE_Y + TREE_LOGS; y++) {
            if (world.getBlockAt(TREE_X, y, TREE_Z).getType() == Material.OAK_LOG) {
                logs++;
            }
        }
        JsonObject result = new JsonObject();
        result.addProperty("treeX", TREE_X);
        result.addProperty("treeY", TREE_Y);
        result.addProperty("treeZ", TREE_Z);
        result.addProperty("logs", logs);
        result.addProperty("stump", world.getBlockAt(TREE_X, TREE_Y, TREE_Z).getType().name());
        result.addProperty("tasksCompleted", stage.handle.state().stats().tasks());
        result.addProperty("golemAlive", stage.handle.entity().isValid() && !stage.handle.entity().isDead());
        result.addProperty("golemId", stage.handle.entity().getEntityId());
        result.addProperty("cargoSaplings", count(stage.handle.state().carriedItems().toArray(ItemStack[]::new), Material.OAK_SAPLING));
        result.addProperty("chestSaplings", count(((Chest) world.getBlockAt(CHEST_X, TREE_Y, TREE_Z).getState()).getBlockInventory().getContents(),
            Material.OAK_SAPLING));
        result.addProperty("playerSaplings", count(player.getInventory().getContents(), Material.OAK_SAPLING));
        result.addProperty("seedRemaining", stage.seed == null || !stage.seed.isValid() ? 0 : stage.seed.getItemStack().getAmount());
        result.addProperty("seedId", stage.seed == null ? -1 : stage.seed.getEntityId());
        NodeService nodes = GolemancyRuntime.nodes().orElseThrow();
        NodeRecord record = nodes.node(stage.node).orElseThrow(() -> new IllegalStateException("Lumber node disappeared: chunkLoaded="
            + world.isChunkLoaded(0, 0) + ", indexReady=" + nodes.index().isReady(world.getUID(), stage.node.chunkKey()) + ", stored="
            + WorldData.loaded(world).map(data -> data.get(stage.node.x(), stage.node.y(), stage.node.z(), NodeFaces.class)).orElse(null)));
        result.addProperty("areaVolume", record.area().volume());
        result.addProperty("openTasks", GolemancyRuntime.boards().orElseThrow().board(world.getUID()).tasks(stage.node).size());
        return result;
    }

    private static JsonObject cleanup(Player player) {
        Stage stage = STAGES.remove(player.getUniqueId());
        if (stage != null) {
            GolemancyRuntime.nodes().orElseThrow().remove(player, stage.node);
            GolemRegistry registry = GolemancyRuntime.registry().orElseThrow();
            registry.release(stage.handle);
            stage.handle.entity().remove();
            if (stage.seed != null) {
                stage.seed.remove();
            }
            AdaptPlayer runtime = Objects.requireNonNull(Adapt.instance.getAdaptServer().getPlayer(player));
            for (Map.Entry<Adaptation<?>, Integer> entry : stage.previous.entrySet()) {
                runtime.getData().getSkillLine(entry.getKey().getSkill().getName()).setAdaptation(entry.getKey(), entry.getValue());
            }
        }
        if (PREPARED.remove(player.getUniqueId()) != null) {
            AdaptGameplayFixture fixture = JavaPlugin.getPlugin(AdaptGameplayFixture.class);
            for (int x = -1; x <= 0; x++) {
                for (int z = -1; z <= 0; z++) {
                    player.getWorld().removePluginChunkTicket(x, z, fixture);
                }
            }
        }
        JsonObject result = new JsonObject();
        result.addProperty("cleaned", stage != null);
        return result;
    }

    private static Stage current(Player player) {
        return Objects.requireNonNull(STAGES.get(player.getUniqueId()), "Stage Lumber first");
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

    private static final class Stage {
        private final GolemHandle handle;
        private final NodeKey node;
        private final Map<Adaptation<?>, Integer> previous;
        private Item seed;

        private Stage(GolemHandle handle, NodeKey node, Map<Adaptation<?>, Integer> previous) {
            this.handle = handle;
            this.node = node;
            this.previous = Map.copyOf(previous);
        }
    }
}
