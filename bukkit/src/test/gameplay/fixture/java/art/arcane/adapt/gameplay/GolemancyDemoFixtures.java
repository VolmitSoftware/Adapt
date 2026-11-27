package art.arcane.adapt.gameplay;

import art.arcane.adapt.content.golem.GolemRoster;
import art.arcane.adapt.engine.player.PlayerApi;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.Adapt;
import art.arcane.adapt.engine.data.WorldData;
import art.arcane.adapt.content.golem.GolemBody;
import art.arcane.adapt.content.golem.GolemCoreType;
import art.arcane.adapt.content.golem.GolemHandle;
import art.arcane.adapt.content.golem.GolemRegistry;
import art.arcane.adapt.content.golem.GolemTask;
import art.arcane.adapt.content.golem.GolemTaskBoard;
import art.arcane.adapt.content.golem.GolemTaskBoards;
import art.arcane.adapt.content.golem.GolemRune;
import art.arcane.adapt.content.golem.GolemRuneType;
import art.arcane.adapt.content.golem.GolemState;
import art.arcane.adapt.content.golem.GolemancyRuntime;
import art.arcane.adapt.content.golem.NodeType;
import art.arcane.adapt.content.golem.item.GolemItemKind;
import art.arcane.adapt.content.golem.item.GolemItems;
import art.arcane.adapt.content.golem.node.NodeIndex;
import art.arcane.adapt.content.golem.node.NodeKey;
import art.arcane.adapt.content.golem.node.NodeRecord;
import art.arcane.adapt.content.golem.node.NodeService;
import art.arcane.adapt.content.golem.plinth.PlinthService;
import art.arcane.adapt.content.golem.plinth.PlinthSlotItem;
import art.arcane.adapt.engine.preference.PlayerPreferences;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.content.adaptation.tragoul.TragoulSkeletalServant;
import art.arcane.adapt.gameplay.demo.DemoScope;
import art.arcane.adapt.gameplay.demo.ProbeFormat;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Snowman;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class GolemancyDemoFixtures {
    private static final String GOLEMANCY_SKILL = "golemancy";
    private static final String WARM_REPLY = "ADAPT_QA DEMO WARM golemancy";
    private static final String BINDING_STAGE = "golemancy-binding";
    private static final String GATHERER_BINDING_STAGE = "golemancy-gatherer-binding";
    private static final String GUARDIAN_BINDING_STAGE = "golemancy-guardian-binding";
    private static final String HARVESTER_BINDING_STAGE = "golemancy-harvester-binding";
    private static final String LUMBER_BINDING_STAGE = "golemancy-lumber-binding";
    private static final String BREAKER_BINDING_STAGE = "golemancy-breaker-binding";
    private static final String RANCHER_BINDING_STAGE = "golemancy-rancher-binding";
    private static final String SORTER_BINDING_STAGE = "golemancy-sorter-binding";
    private static final String ANGLER_BINDING_STAGE = "golemancy-angler-binding";
    private static final String COPPER_BINDING_STAGE = "golemancy-copper-binding";
    private static final String OVERSEER_STAGE = "golemancy-overseer";
    private static final String IRON_BINDING_STAGE = "golemancy-iron-binding";
    private static final String NODE_STAGE = "golemancy-node";
    private static final String PLINTH_STAGE = "golemancy-plinth";
    private static final String MENDING_STAGE = "golemancy-mending";
    private static final String STASIS_STAGE = "golemancy-stasis";
    private static final String MASTERWORK_STAGE = "golemancy-masterwork";
    private static final String BONE_FRAME_STAGE = "golemancy-bone-frame";
    private static final int BONE_FRAME_BONES = 16;
    private static final String GOLEM_TAG = "adapt-qa-golemancy-golem";
    private static final String COPPER_GOLEM_TAG = "adapt-qa-golemancy-copper-golem";
    private static final String IRON_GOLEM_TAG = "adapt-qa-golemancy-iron-golem";
    private static final int NODE_X = 2;
    private static final int NODE_Y = 100;
    private static final int NODE_Z = 0;
    private static final int PLINTH_X = 2;
    private static final int PLINTH_Y = 100;
    private static final int PLINTH_Z = 3;
    private static final int PEDESTAL_DISTANCE = 2;
    private static final double MENDING_MAX_HEALTH = 20D;
    private static final double MENDING_START_HEALTH = 2D;
    private static final int MASTERWORK_LEVELS = 20;
    private static final List<Material> MASTERWORK_ITEMS = List.of(Material.HEART_OF_THE_SEA, Material.DIAMOND_BLOCK, Material.IRON_BLOCK,
        Material.COPPER_BLOCK, Material.SNOW_BLOCK);
    private static final double PROBE_RADIUS = 80D;
    private static final int PROBE_TASKS = 3;
    private static final List<BlockFace> FACES = List.of(BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST);

    private static boolean itemsWarmed;

    private GolemancyDemoFixtures() {
    }

    public static Cleared clearDemoState(World world, BoundingBox plate) {
        List<Player> owners = demoOwners();
        stripNodeItems(owners);
        int golems = clearGolems(owners);
        Optional<NodeService> service = GolemancyRuntime.nodes();
        if (service.isEmpty()) {
            return new Cleared(golems, 0);
        }
        Vector center = plate.getCenter();
        int nodes = 0;
        for (NodeIndex.Entry entry : service.get().index().near(world.getUID(), center.getX(), center.getY(), center.getZ(), DemoScope.searchRadius(plate))) {
            NodeKey key = entry.key();
            Player owner = Bukkit.getPlayer(entry.record().owner());
            if (owner != null && DemoScope.ownsDemoState(owner.getName()) && DemoScope.contains(plate, key.x(), key.y(), key.z())
                && service.get().remove(BukkitRefs.player(owner), key)) {
                nodes++;
            }
        }
        return new Cleared(golems, nodes);
    }

    private static List<Player> demoOwners() {
        List<Player> owners = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (DemoScope.ownsDemoState(player.getName())) {
                owners.add(player);
            }
        }
        return owners;
    }

    private static int clearGolems(List<Player> owners) {
        int golems = 0;
        for (Player owner : owners) {
            AdaptPlayer adaptPlayer = Adapt.instance.getAdaptServer().getPlayer(BukkitRefs.player(owner));
            if (adaptPlayer != null) {
                golems += adaptPlayer.getData().section(GolemRoster.section()).golems().size();
            }
        }
        GolemancyFixtures.clearRosters(owners.stream().map(BukkitRefs::player).toArray(PlayerRef[]::new));
        return golems;
    }

    private static void stripNodeItems(List<Player> owners) {
        for (Player owner : owners) {
            ItemStack[] contents = owner.getInventory().getContents();
            for (int slot = 0; slot < contents.length; slot++) {
                if (contents[slot] != null && GolemItems.nodeTypeOf(BukkitRefs.item(contents[slot])).isPresent()) {
                    owner.getInventory().setItem(slot, null);
                }
            }
        }
    }

    private static int nodeItems(List<Player> owners) {
        int items = 0;
        for (Player owner : owners) {
            for (ItemStack item : owner.getInventory().getContents()) {
                if (item != null && GolemItems.nodeTypeOf(BukkitRefs.item(item)).isPresent()) {
                    items += item.getAmount();
                }
            }
        }
        return items;
    }

    public static String bind(Player owner, String tag, String coreId) {
        GolemRegistry registry = GolemancyRuntime.registry().orElseThrow(() -> new IllegalStateException("Golemancy is not running"));
        GolemCoreType core = GolemCoreType.fromId(coreId).orElseThrow(() -> new IllegalArgumentException("Unknown golem core " + coreId));
        for (Entity entity : owner.getWorld().getEntities()) {
            if (entity instanceof Mob golem && golem.getScoreboardTags().contains(tag)) {
                GolemBody body = GolemBody.of(BukkitRefs.entity(golem)).orElseThrow(() -> new IllegalArgumentException("Tagged entity " + tag + " is not a golem"));
                GolemHandle handle = registry.bind(BukkitRefs.player(owner), BukkitRefs.mob(golem), body, core);
                GolemancyRuntime.bindListener().bound(BukkitRefs.player(owner), handle);
                return "ADAPT_QA DEMO BIND " + tag + " " + coreId;
            }
        }
        throw new IllegalArgumentException("No golem tagged " + tag);
    }

    public static String warm() {
        if (itemsWarmed) {
            return WARM_REPLY;
        }
        itemsWarmed = true;
        BukkitRefs.item(GolemItems.crook());
        BukkitRefs.item(GolemItems.blankCore());
        BukkitRefs.item(GolemItems.core(GolemCoreType.GATHERER));
        BukkitRefs.item(GolemItems.blankNode());
        BukkitRefs.item(GolemItems.node(NodeType.DEPOSIT));
        BukkitRefs.item(GolemItems.blankRune());
        BukkitRefs.item(GolemItems.rune(new GolemRune(GolemRuneType.SWIFTNESS, 1)));
        BukkitRefs.item(GolemItems.masterworkHeart());
        GolemItems.mayBeOwnerBound(BukkitRefs.id(Material.SNOW_BLOCK));
        return WARM_REPLY;
    }

    public static String give(Player player, int hotbarSlot, String itemId, int count) {
        if (hotbarSlot < 0 || hotbarSlot > 8) {
            throw new IllegalArgumentException("Hotbar slot must be 0 to 8, found " + hotbarSlot);
        }
        ItemStack item = "masterwork_heart".equals(itemId) ? BukkitRefs.item(GolemItems.masterworkHeart()) : BukkitRefs.item(GolemItems.resolve(Id.of(GolemItems.NAMESPACE, itemId)));
        if (item == null) {
            throw new IllegalArgumentException("Unknown Golemancy item " + itemId);
        }
        item.setAmount(count);
        player.getInventory().setItem(hotbarSlot, item);
        player.updateInventory();
        return "ADAPT_QA DEMO GIVE " + hotbarSlot + " " + itemId + " " + count;
    }

    public static String probe(Player owner) {
        World world = owner.getWorld();
        Location at = owner.getLocation();
        long now = System.currentTimeMillis();
        List<GolemHandle> golems = loadedGolems(owner);
        StringBuilder out = new StringBuilder(ProbeFormat.header(nodeItems(demoOwners()), golems.size(), nearestGolem(owner, golems)));
        GolemancyRuntime.nodes().ifPresent(service -> describeNodes(out, service, world, at, now));
        for (GolemHandle handle : golems) {
            describeGolem(out, handle);
        }
        describeLevels(out, owner);
        return out.toString();
    }

    public static String near(Player owner) {
        return ProbeFormat.near(nearestGolem(owner, loadedGolems(owner)));
    }

    private static List<GolemHandle> loadedGolems(Player owner) {
        return GolemancyRuntime.registry().map(registry -> List.copyOf(registry.loadedOf(owner.getUniqueId()))).orElse(List.of());
    }

    private static Double nearestGolem(Player owner, List<GolemHandle> golems) {
        Location at = owner.getLocation();
        List<Double> distances = new ArrayList<>(golems.size());
        for (GolemHandle handle : golems) {
            Location where = BukkitRefs.mob(handle.entity()).getLocation();
            if (where.getWorld() == at.getWorld()) {
                distances.add(where.distance(at));
            }
        }
        return ProbeFormat.nearest(distances);
    }

    private static void describeLevels(StringBuilder out, Player owner) {
        AdaptPlayer learner = Adapt.instance.getAdaptServer().getPlayer(BukkitRefs.player(owner));
        if (learner == null) {
            return;
        }
        Map<String, Integer> levels = new LinkedHashMap<>();
        for (Adaptation<?> adaptation : Fixtures.skill(GOLEMANCY_SKILL).getAdaptations()) {
            levels.put(adaptation.getId().toString(), adaptation.getLevel(PlayerApi.profile(learner)));
        }
        out.append(ProbeFormat.levels(levels));
    }

    private static void describeNodes(StringBuilder out, NodeService service, World world, Location at, long now) {
        Optional<GolemTaskBoards> boards = GolemancyRuntime.boards();
        for (NodeIndex.Entry entry : service.index().near(world.getUID(), at.getX(), at.getY(), at.getZ(), PROBE_RADIUS)) {
            NodeKey key = entry.key();
            out.append(" | node ").append(entry.record().type().id()).append('@').append(key.x()).append(',').append(key.y()).append(',').append(key.z())
                .append(' ').append(key.face());
            if (boards.isPresent()) {
                describeBoard(out, boards.get().board(world.getUID()), key, now);
            }
        }
    }

    private static void describeBoard(StringBuilder out, GolemTaskBoard board, NodeKey key, long now) {
        List<GolemTask> tasks = board.tasks(key);
        out.append(" status=").append(board.status(key, now)).append(" tasks=").append(tasks.size());
        for (int index = 0; index < Math.min(PROBE_TASKS, tasks.size()); index++) {
            GolemTask task = tasks.get(index);
            out.append(" {").append(task.type()).append(' ').append(task.target()).append(" holder=").append(task.holder(now).isPresent()).append('}');
        }
    }

    private static void describeGolem(StringBuilder out, GolemHandle handle) {
        Location where = BukkitRefs.mob(handle.entity()).getLocation();
        out.append(ProbeFormat.golem(String.valueOf(handle.body()), String.valueOf(handle.state().mode()), String.valueOf(handle.state().cores()),
            where.getX(), where.getY(), where.getZ(), handle.isDormant()));
    }


    public record Cleared(int golems, int nodes) {
        public int total() {
            return golems + nodes;
        }
    }
}
