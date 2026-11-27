package art.arcane.adapt.gameplay;

import art.arcane.adapt.content.golem.GolemRoster;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.content.golem.GolemBody;
import art.arcane.adapt.content.golem.GolemCoreType;
import art.arcane.adapt.content.golem.GolemHandle;
import art.arcane.adapt.content.golem.GolemMode;
import art.arcane.adapt.content.golem.GolemRune;
import art.arcane.adapt.content.golem.GolemRuneType;
import art.arcane.adapt.content.golem.GolemancyRuntime;
import art.arcane.adapt.content.golem.NodeType;
import art.arcane.adapt.content.golem.ServantFrame;
import art.arcane.adapt.content.golem.gui.CrookGestures;
import art.arcane.adapt.content.golem.gui.GolemGui;
import art.arcane.adapt.content.golem.gui.NodeAreaSelector;
import art.arcane.adapt.content.golem.gui.NodeGui;
import art.arcane.adapt.content.golem.gui.NodeTagPrompt;
import art.arcane.adapt.content.golem.gui.RosterGui;
import art.arcane.adapt.content.golem.item.GolemItems;
import art.arcane.adapt.content.golem.node.NodeKey;
import art.arcane.adapt.content.golem.node.NodePlacementVerdict;
import art.arcane.adapt.content.golem.node.NodeService;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.engine.tick.TickedObject;
import art.arcane.adapt.content.adaptation.golemancy.GolemSorterCore;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import com.google.gson.JsonObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Opens each golemancy menu for a real client without world interaction. The first call builds, around the player, the
 * state those menus show in play: a bound iron golem with runes, a channelled harvest node, a guard node, an input node
 * on a stocked chest beside a second stocked chest, and every golemancy adaptation at its top level.
 */
public final class GolemMenuFixtures {
    private static final List<String> ADAPTATIONS = List.of("adapt:golem/animus_binding", "adapt:golem/node_weaving", "adapt:golem/iron_frame",
        "adapt:golem/gatherer_core", "adapt:golem/runic_augments", "adapt:golem/masterwork", "adapt:golem/bone_frame", "adapt:golem/sorter_core");
    private static final String GOLEM_TAG = "adapt-qa-golem-menu";
    private static final Map<UUID, Setup> setups = new HashMap<>();

    private GolemMenuFixtures() {
    }

    public static JsonObject execute(PlayerRef player, String[] args) {
        Setup setup = setups.get(player.uuid());
        if (setup == null) {
            setup = prepare(player);
            setups.put(player.uuid(), setup);
        }
        GolemHandle golem = GolemancyRuntime.registry().orElseThrow().handle(setup.golem().entity()).orElse(setup.golem());
        switch (args[2]) {
            case "prepare" -> {
            }
            case "golem" -> GolemGui.open(player, golem, new CrookGestures<>());
            case "node" -> NodeGui.open(player, setup.harvest(), companion(NodeTagPrompt.class), companion(NodeAreaSelector.class));
            case "guard" -> NodeGui.open(player, setup.guard(), companion(NodeTagPrompt.class), companion(NodeAreaSelector.class));
            case "sorter" -> NodeGui.open(player, setup.input(), companion(NodeTagPrompt.class), companion(NodeAreaSelector.class));
            case "sorter-fetch" -> openFetch(player, setup.input());
            case "roster" -> RosterGui.open(player);
            case "lattice" -> ServantFrame.frame().orElseThrow().openLattice(player, 0);
            default -> throw new IllegalArgumentException("Unknown golem menu: " + args[2]);
        }
        JsonObject result = new JsonObject();
        result.addProperty("menu", args[2]);
        result.addProperty("golemEntityId", golem.entity().entityId());
        result.addProperty("golems", GolemancyRuntime.registry().orElseThrow().loadedOf(player.uuid()).size());
        result.addProperty("nodes", Fixtures.adaptPlayer(player).getData().section(GolemRoster.section()).nodeCount());
        result.addProperty("origin", setup.x() + " " + setup.y() + " " + setup.z());
        return result;
    }

    public static void cleanup() {
        setups.clear();
    }

    private static Setup prepare(PlayerRef player) {
        // Adaptations are inactive in creative unless the server allows it, which leaves a node cap of 0 and locked menus.
        if (player.gameMode() != GameMode.SURVIVAL) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        for (String name : ADAPTATIONS) {
            Adaptation<?> adaptation = Fixtures.adaptation(name);
            GolemControlsFixtures.learn(player, name, adaptation.getMaxLevel());
        }
        WorldRef world = player.world();
        Loc at = player.location();
        int x = at.blockX();
        int y = at.blockY();
        int z = at.blockZ();
        if (player.inventory().mainHand().isEmpty()) {
            player.inventory().setItem(player.inventory().heldSlot(), GolemItems.crook());
        }
        List<NodeKey> placed = new ArrayList<>();
        GolemHandle golem = null;
        try {
            Fixtures.set(world, x - 2, y, z + 3, "stone");
            Fixtures.set(world, x, y, z + 3, "stone");
            Fixtures.set(world, x + 2, y, z + 3, "chest");
            Fixtures.set(world, x + 4, y, z + 3, "chest");
            stock(world.blockAt(x + 2, y, z + 3).blockInventory(), "cobblestone", "oak_log", "wheat");
            stock(world.blockAt(x + 4, y, z + 3).blockInventory(), "iron_ingot", "bread", "torch");
            NodeKey harvest = place(player, world, x - 2, y, z + 3, NodeType.HARVEST, placed);
            NodeKey guard = place(player, world, x, y, z + 3, NodeType.GUARD, placed);
            NodeKey input = place(player, world, x + 2, y, z + 3, NodeType.INPUT, placed);
            GolemancyRuntime.nodes().orElseThrow()
                .update(player.uuid(), harvest, current -> current.toBuilder().channel(DyeColor.RED).build()).orElseThrow();
            MobRef entity = (MobRef) Fixtures.spawn(world, "iron_golem", x + 0.5D, y, z - 2.5D);
            Fixtures.tag(entity, GOLEM_TAG);
            golem = GolemancyRuntime.registry().orElseThrow().bind(player, entity, GolemBody.IRON, GolemCoreType.GATHERER);
            golem.setMode(GolemMode.STAY);
            golem.setEssence(60);
            golem.setRunes(List.of(new GolemRune(GolemRuneType.MIGHT, 1), new GolemRune(GolemRuneType.SWIFTNESS, 2)));
            return new Setup(golem, harvest, guard, input, x, y, z);
        } catch (RuntimeException failure) {
            NodeService service = GolemancyRuntime.nodes().orElseThrow();
            for (NodeKey key : placed) {
                service.remove(player, key);
            }
            if (golem != null) {
                GolemancyRuntime.registry().orElseThrow().release(golem);
            }
            throw failure;
        }
    }

    private static NodeKey place(PlayerRef owner, WorldRef world, int x, int y, int z, NodeType type, List<NodeKey> placed) {
        NodeService service = GolemancyRuntime.nodes().orElseThrow();
        NodePlacementVerdict verdict = service.place(owner, world.blockAt(x, y, z), BlockFace.UP, type,
            GolemancyRuntime.nodeWeaving().orElseThrow());
        if (verdict != NodePlacementVerdict.READY) {
            throw new IllegalStateException("Could not place " + type + " node at " + x + " " + y + " " + z + ": " + verdict);
        }
        NodeKey key = new NodeKey(world.uid(), x, y, z, BlockFace.UP);
        placed.add(key);
        return key;
    }

    private static void stock(InventoryRef chest, String... types) {
        for (int slot = 0; slot < chest.size(); slot++) {
            chest.clear(slot);
        }
        for (int index = 0; index < types.length; index++) {
            chest.setItem(index, Fixtures.item(types[index], 16 + index * 8));
        }
    }

    /** The node menus take the prompt and area selector the golemancy skill registered, so typed tags and area edits work. */
    private static <T> T companion(Class<T> type) {
        Skill<?> skill = Fixtures.skill("golemancy");
        try {
            Field field = TickedObject.class.getDeclaredField("companionListeners");
            field.setAccessible(true);
            for (Object listener : (List<?>) field.get(skill)) {
                if (type.isInstance(listener)) {
                    return type.cast(listener);
                }
            }
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot read golemancy listeners", failure);
        }
        throw new IllegalStateException("Golemancy has no active " + type.getSimpleName());
    }

    /** The fetch menu is reached in play from the input node panel's fetch control; this calls the same open. */
    private static void openFetch(PlayerRef player, NodeKey input) {
        GolemSorterCore core = Fixtures.adaptation(GolemSorterCore.class);
        try {
            Field field = GolemSorterCore.class.getDeclaredField("fetchGui");
            field.setAccessible(true);
            Object gui = Objects.requireNonNull(field.get(core));
            Method open = gui.getClass().getDeclaredMethod("open", PlayerRef.class, NodeKey.class);
            open.setAccessible(true);
            open.invoke(gui, player, input);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot open the sorter fetch menu", failure);
        }
    }

    private record Setup(GolemHandle golem, NodeKey harvest, NodeKey guard, NodeKey input, int x, int y, int z) {
    }
}
