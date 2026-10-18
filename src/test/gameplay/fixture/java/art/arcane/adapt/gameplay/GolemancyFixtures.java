package art.arcane.adapt.gameplay;

import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeService;
import com.google.gson.JsonObject;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowman;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;

public final class GolemancyFixtures {
    private static final String BINDING_STAGE = "golemancy-binding";
    private static final String NODE_STAGE = "golemancy-node";
    private static final String GOLEM_TAG = "adapt-qa-golemancy-golem";
    private static final int NODE_X = 2;
    private static final int NODE_Y = 100;
    private static final int NODE_Z = 0;
    private static final List<BlockFace> FACES = List.of(BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST);

    private GolemancyFixtures() {
    }

    public static boolean stage(String name, World world, Player actor, Player opponent) {
        if (BINDING_STAGE.equals(name)) {
            opponent.teleport(new Location(world, 0.5, 100, 8.5));
            actor.setLevel(0);
            actor.getInventory().addItem(GolemItems.blankCore());
            Snowman golem = world.spawn(new Location(world, 2.5, 100, 0.5), Snowman.class);
            golem.addScoreboardTag(GOLEM_TAG);
            return true;
        }
        if (NODE_STAGE.equals(name)) {
            opponent.teleport(new Location(world, 0.5, 100, 8.5));
            clearNodes(world, actor);
            actor.getInventory().clear();
            world.getBlockAt(NODE_X, NODE_Y, NODE_Z).setType(Material.CHEST, false);
            ItemStack nodes = GolemItems.node(NodeType.DEPOSIT);
            nodes.setAmount(2);
            actor.getInventory().addItem(nodes);
            return true;
        }
        return false;
    }

    public static JsonObject snapshot(Player player) {
        JsonObject result = new JsonObject();
        for (Entity entity : player.getWorld().getEntities()) {
            if (entity instanceof Snowman golem && golem.getScoreboardTags().contains(GOLEM_TAG)) {
                result.add("golem", state(golem, player));
            }
        }
        result.add("node", node(player));
        return result;
    }

    private static void clearNodes(World world, Player actor) {
        Optional<NodeService> service = GolemancyRuntime.nodes();
        if (service.isEmpty()) {
            return;
        }
        for (BlockFace face : FACES) {
            service.get().remove(actor, new NodeKey(world.getUID(), NODE_X, NODE_Y, NODE_Z, face));
        }
    }

    private static JsonObject node(Player player) {
        JsonObject result = new JsonObject();
        int nodes = 0;
        Optional<NodeService> service = GolemancyRuntime.nodes();
        for (BlockFace face : FACES) {
            Optional<NodeRecord> record = service.flatMap(nodeService -> nodeService.node(new NodeKey(player.getWorld().getUID(), NODE_X, NODE_Y, NODE_Z, face)));
            if (record.isEmpty()) {
                continue;
            }
            nodes++;
            result.addProperty("type", record.get().type().id());
            result.addProperty("face", face.name());
            result.addProperty("ownedByPlayer", record.get().owner().equals(player.getUniqueId()));
        }
        result.addProperty("nodes", nodes);
        int items = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && GolemItems.nodeTypeOf(item).isPresent()) {
                items += item.getAmount();
            }
        }
        result.addProperty("nodeItems", items);
        return result;
    }

    private static JsonObject state(Snowman golem, Player player) {
        JsonObject result = new JsonObject();
        Optional<GolemState> state = GolemState.read(golem);
        result.addProperty("entityId", golem.getEntityId());
        result.addProperty("bound", state.isPresent());
        result.addProperty("boundToPlayer", state.map(bound -> bound.owner().equals(player.getUniqueId())).orElse(false));
        result.addProperty("aware", golem.isAware());
        AttributeInstance scale = golem.getAttribute(Attribute.SCALE);
        result.addProperty("scale", scale == null ? 1D : scale.getValue());
        AttributeInstance maxHealth = golem.getAttribute(Attribute.MAX_HEALTH);
        result.addProperty("maxHealth", maxHealth == null ? 0D : maxHealth.getValue());
        result.addProperty("health", golem.getHealth());
        return result;
    }
}
