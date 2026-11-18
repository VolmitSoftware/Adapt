package art.arcane.adapt.gameplay;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.data.BlockStorePort;
import art.arcane.adapt.api.golem.GolemAnchor;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemMode;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemRoster;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeCodec;
import art.arcane.adapt.api.golem.node.NodeCuboid;
import art.arcane.adapt.api.golem.node.NodeFaces;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodePlacementVerdict;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeService;
import art.arcane.adapt.api.golem.node.NodeWeavingPolicy;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import com.google.gson.JsonObject;

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
    private static final Map<UUID, Long> PREPARED = new HashMap<>();

    private LumberReplantFixtures() {
    }

    public static JsonObject execute(PlayerRef player, String[] args) {
        return switch (args[2]) {
            case "prepare" -> prepare(player);
            case "ready" -> ready(player);
            case "stage" -> stage(player);
            case "seed" -> seed(player);
            case "snapshot" -> snapshot(player, current(player));
            case "cleanup" -> cleanup(player);
            case "settled" -> settled(player);
            default -> throw new IllegalArgumentException("Unknown lumber replant action " + args[2]);
        };
    }

    private static JsonObject prepare(PlayerRef player) {
        WorldRef world = player.world();
        if (!Fixtures.isFixtureWorld(world)) {
            throw new IllegalStateException("Lumber stage requires the gameplay arena");
        }
        Fixtures.teleport(player, world, -6.5D, TREE_Y, -5.5D);
        for (int x = -1; x <= 0; x++) {
            for (int z = -1; z <= 0; z++) {
                world.addChunkTicket(x, z);
            }
        }
        PREPARED.put(player.uuid(), Fixtures.host().currentTick());
        return ready(player);
    }

    private static JsonObject ready(PlayerRef player) {
        WorldRef world = player.world();
        Long prepared = PREPARED.get(player.uuid());
        boolean ready = prepared != null && Fixtures.host().currentTick() - prepared >= 2 && GolemControlsFixtures.storeLoaded(world);
        NodeService nodes = GolemancyRuntime.nodes().orElseThrow();
        for (int x = -1; x <= 0; x++) {
            for (int z = -1; z <= 0; z++) {
                ready &= world.isChunkLoaded(x, z) && nodes.index().isReady(world.uid(), GolemRoster.chunkKey(x, z));
            }
        }
        JsonObject result = new JsonObject();
        result.addProperty("ready", ready);
        return result;
    }

    private static JsonObject stage(PlayerRef player) {
        if (STAGES.containsKey(player.uuid())) {
            throw new IllegalStateException("Clean up the previous Lumber stage");
        }
        WorldRef world = player.world();
        if (!Fixtures.isFixtureWorld(world)) {
            throw new IllegalStateException("Lumber stage requires the gameplay arena");
        }
        if (!ready(player).get("ready").getAsBoolean()) {
            throw new IllegalStateException("Prepare the Lumber chunks and wait until ready before placement");
        }
        AdaptPlayer runtime = Objects.requireNonNull(Fixtures.adaptPlayer(player));
        if (!runtime.isRuntimeReady()) {
            throw new IllegalStateException("Player runtime is not ready");
        }
        Skill<?> skill = Objects.requireNonNull(Fixtures.skill("golemancy"));
        Map<Adaptation<?>, Integer> previous = new HashMap<>();
        for (Adaptation<?> adaptation : skill.getAdaptations()) {
            if (REQUIRED.contains(adaptation.getName())) {
                previous.put(adaptation, adaptation.getLevel(runtime));
                runtime.getData().getSkillLine(skill.getName()).setAdaptation(adaptation,
                    adaptation.getName().equals("golem-lumber-core") ? 2 : adaptation.getMaxLevel());
            }
        }
        Fixtures.clearInventory(player);
        Fixtures.set(world, TREE_X, TREE_Y - 1, TREE_Z, "grass_block");
        for (int y = TREE_Y; y < TREE_Y + TREE_LOGS; y++) {
            Fixtures.set(world, TREE_X, y, TREE_Z, "oak_log");
        }
        for (int y = TREE_Y + TREE_LOGS - 1; y <= TREE_Y + TREE_LOGS; y++) {
            for (int x = TREE_X - 1; x <= TREE_X + 1; x++) {
                for (int z = TREE_Z - 1; z <= TREE_Z + 1; z++) {
                    if (world.blockAt(x, y, z).isAir()) {
                        Fixtures.set(world, x, y, z, "oak_leaves[persistent=true]");
                    }
                }
            }
        }
        Fixtures.set(world, CHEST_X, TREE_Y, TREE_Z, "chest");
        InventoryRef chest = world.blockAt(CHEST_X, TREE_Y, TREE_Z).blockInventory();
        for (int slot = 0; slot < chest.size(); slot++) {
            chest.clear(slot);
        }
        NodeService nodes = GolemancyRuntime.nodes().orElseThrow();
        NodeWeavingPolicy policy = GolemancyRuntime.nodeWeaving().orElseThrow();
        BlockRef anchor = world.blockAt(0, TREE_Y - 1, 0);
        NodePlacementVerdict verdict = nodes.place(player, anchor, BlockFace.UP, NodeType.LUMBER, policy);
        if (verdict != NodePlacementVerdict.READY) {
            throw new IllegalStateException("Lumber node placement failed: " + verdict);
        }
        NodeKey node = NodeKey.of(anchor, BlockFace.UP);
        nodes.update(player.uuid(), node, record -> record.toBuilder().area(NodeCuboid.symmetric(3, 0, 3)).build()).orElseThrow();
        MobRef entity = (MobRef) Fixtures.spawn(world, "copper_golem", 0.5D, TREE_Y, 2.5D);
        GolemHandle handle = GolemancyRuntime.registry().orElseThrow().bind(player, entity, GolemBody.COPPER, GolemCoreType.LUMBER);
        handle.setHomeChest(GolemAnchor.of(world.uid(), CHEST_X, TREE_Y, TREE_Z));
        handle.setMode(GolemMode.WORK);
        GolemancyRuntime.bindListener().bound(player, handle);
        Stage stage = new Stage(handle, node, previous);
        STAGES.put(player.uuid(), stage);
        return snapshot(player, stage);
    }

    private static JsonObject seed(PlayerRef player) {
        Stage stage = current(player);
        JsonObject before = snapshot(player, stage);
        if (stage.seed != null || before.get("logs").getAsInt() != 0 || before.get("tasksCompleted").getAsLong() < 1L
            || !before.get("stump").getAsString().equals("AIR")) {
            throw new IllegalStateException("Seed must follow a completed felling without a sapling");
        }
        GolemancyRuntime.nodes().orElseThrow().update(player.uuid(), stage.node,
            record -> record.toBuilder().area(new NodeCuboid(-8, -8, -8, 7, 7, 7)).build()).orElseThrow();
        ItemEntityRef seed = player.world().dropItem(new Vec3(TREE_X + 1.5D, TREE_Y + 0.2D, TREE_Z + 0.5D),
            Fixtures.item("oak_sapling", 2));
        seed.setOwner(player.uuid());
        seed.setThrower(player.uuid());
        seed.setPickupDelay(20);
        seed.setVelocity(Vec3.ZERO);
        stage.seed = seed.uuid();
        stage.seedId = seed.entityId();
        return snapshot(player, stage);
    }

    private static ItemEntityRef seedEntity(Stage stage) {
        return stage.seed != null && Fixtures.entity(stage.seed) instanceof ItemEntityRef item && item.isValid() ? item : null;
    }

    private static JsonObject snapshot(PlayerRef player, Stage stage) {
        WorldRef world = player.world();
        Id oakLog = Id.minecraft("oak_log");
        int logs = 0;
        for (int y = TREE_Y; y < TREE_Y + TREE_LOGS; y++) {
            if (world.blockAt(TREE_X, y, TREE_Z).type().equals(oakLog)) {
                logs++;
            }
        }
        JsonObject result = new JsonObject();
        result.addProperty("treeX", TREE_X);
        result.addProperty("treeY", TREE_Y);
        result.addProperty("treeZ", TREE_Z);
        result.addProperty("logs", logs);
        result.addProperty("stump", GolemControlsFixtures.name(world.blockAt(TREE_X, TREE_Y, TREE_Z).type()));
        result.addProperty("tasksCompleted", stage.handle.state().stats().tasks());
        result.addProperty("golemAlive", stage.handle.entity().isValid() && !stage.handle.entity().isDead());
        result.addProperty("golemId", stage.handle.entity().entityId());
        result.addProperty("cargoSaplings", GolemControlsFixtures.count(stage.handle.state().carriedItems(), "oak_sapling"));
        result.addProperty("chestSaplings", GolemControlsFixtures.count(
            world.blockAt(CHEST_X, TREE_Y, TREE_Z).blockInventory().contents(), "oak_sapling"));
        result.addProperty("playerSaplings", GolemControlsFixtures.count(player.inventory().contents(), "oak_sapling"));
        ItemEntityRef seed = seedEntity(stage);
        result.addProperty("seedRemaining", seed == null ? 0 : seed.item().amount());
        result.addProperty("seedId", stage.seed == null ? -1 : stage.seedId);
        NodeService nodes = GolemancyRuntime.nodes().orElseThrow();
        NodeRecord record = nodes.node(stage.node).orElseThrow(() -> new IllegalStateException("Lumber node disappeared: chunkLoaded="
            + world.isChunkLoaded(0, 0) + ", indexReady=" + nodes.index().isReady(world.uid(), stage.node.chunkKey()) + ", stored="
            + storedFaces(world, stage.node.x(), stage.node.y(), stage.node.z())));
        result.addProperty("areaVolume", record.area().volume());
        result.addProperty("openTasks", GolemancyRuntime.boards().orElseThrow().board(world.uid()).tasks(stage.node).size());
        return result;
    }

    private static NodeFaces storedFaces(WorldRef world, int x, int y, int z) {
        if (!GolemControlsFixtures.storeLoaded(world)) {
            return null;
        }
        return BlockStorePort.current().get(world, x, y, z, NodeCodec.SLICE);
    }

    private static JsonObject cleanup(PlayerRef player) {
        Stage stage = STAGES.remove(player.uuid());
        if (stage != null) {
            GolemancyRuntime.nodes().orElseThrow().remove(player, stage.node);
            GolemRegistry registry = GolemancyRuntime.registry().orElseThrow();
            registry.release(stage.handle);
            stage.handle.entity().remove();
            ItemEntityRef seed = seedEntity(stage);
            if (seed != null) {
                seed.remove();
            }
            AdaptPlayer runtime = Objects.requireNonNull(Fixtures.adaptPlayer(player));
            for (Map.Entry<Adaptation<?>, Integer> entry : stage.previous.entrySet()) {
                runtime.getData().getSkillLine(entry.getKey().getSkill().getName()).setAdaptation(entry.getKey(), entry.getValue());
            }
        }
        if (PREPARED.remove(player.uuid()) != null) {
            for (int x = -1; x <= 0; x++) {
                for (int z = -1; z <= 0; z++) {
                    player.world().removeChunkTicket(x, z);
                }
            }
        }
        JsonObject result = new JsonObject();
        result.addProperty("cleaned", stage != null);
        return result;
    }

    private static Stage current(PlayerRef player) {
        return Objects.requireNonNull(STAGES.get(player.uuid()), "Stage Lumber first");
    }

    private static JsonObject settled(PlayerRef player) {
        JsonObject result = new JsonObject();
        WorldRef world = player.world();
        boolean settled = false;
        if (GolemControlsFixtures.storeLoaded(world)) {
            NodeFaces faces = BlockStorePort.current().get(world, 0, TREE_Y - 1, 0, NodeCodec.SLICE);
            settled = faces == null || faces.get(BlockFace.UP) == null;
        }
        result.addProperty("settled", settled);
        return result;
    }

    private static final class Stage {
        private final GolemHandle handle;
        private final NodeKey node;
        private final Map<Adaptation<?>, Integer> previous;
        private UUID seed;
        private int seedId;

        private Stage(GolemHandle handle, NodeKey node, Map<Adaptation<?>, Integer> previous) {
            this.handle = handle;
            this.node = node;
            this.previous = Map.copyOf(previous);
        }
    }
}
