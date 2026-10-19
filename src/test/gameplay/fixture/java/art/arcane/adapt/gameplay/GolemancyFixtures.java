package art.arcane.adapt.gameplay;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.data.WorldData;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemRoster;
import art.arcane.adapt.api.golem.GolemTask;
import art.arcane.adapt.api.golem.GolemTaskBoard;
import art.arcane.adapt.api.golem.GolemTaskBoards;
import art.arcane.adapt.api.golem.GolemRune;
import art.arcane.adapt.api.golem.GolemRuneType;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.item.GolemItemKind;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.node.NodeIndex;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeService;
import art.arcane.adapt.api.golem.plinth.PlinthService;
import art.arcane.adapt.api.golem.plinth.PlinthSlotItem;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.adaptation.tragoul.TragoulSkeletalServant;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Snowman;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;

public final class GolemancyFixtures {
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
        if (GATHERER_BINDING_STAGE.equals(name)) {
            opponent.teleport(new Location(world, 0.5, 100, 8.5));
            actor.setLevel(0);
            actor.getInventory().addItem(GolemItems.core(GolemCoreType.GATHERER));
            Snowman golem = world.spawn(new Location(world, 2.5, 100, 0.5), Snowman.class);
            golem.addScoreboardTag(GOLEM_TAG);
            return true;
        }
        if (GUARDIAN_BINDING_STAGE.equals(name)) {
            opponent.teleport(new Location(world, 0.5, 100, 8.5));
            actor.setLevel(0);
            actor.getInventory().addItem(GolemItems.core(GolemCoreType.GUARDIAN));
            Snowman golem = world.spawn(new Location(world, 2.5, 100, 0.5), Snowman.class);
            golem.addScoreboardTag(GOLEM_TAG);
            return true;
        }
        if (HARVESTER_BINDING_STAGE.equals(name)) {
            opponent.teleport(new Location(world, 0.5, 100, 8.5));
            actor.setLevel(0);
            actor.getInventory().addItem(GolemItems.core(GolemCoreType.HARVESTER));
            Snowman golem = world.spawn(new Location(world, 2.5, 100, 0.5), Snowman.class);
            golem.addScoreboardTag(GOLEM_TAG);
            return true;
        }
        if (LUMBER_BINDING_STAGE.equals(name)) {
            opponent.teleport(new Location(world, 0.5, 100, 8.5));
            actor.setLevel(5);
            actor.getInventory().addItem(GolemItems.core(GolemCoreType.LUMBER));
            CopperGolem golem = world.spawn(new Location(world, 2.5, 100, 0.5), CopperGolem.class);
            golem.addScoreboardTag(COPPER_GOLEM_TAG);
            return true;
        }
        if (SORTER_BINDING_STAGE.equals(name)) {
            opponent.teleport(new Location(world, 0.5, 100, 8.5));
            actor.setLevel(5);
            actor.getInventory().addItem(GolemItems.core(GolemCoreType.SORTER));
            CopperGolem golem = world.spawn(new Location(world, 2.5, 100, 0.5), CopperGolem.class);
            golem.addScoreboardTag(COPPER_GOLEM_TAG);
            return true;
        }
        if (BREAKER_BINDING_STAGE.equals(name)) {
            opponent.teleport(new Location(world, 0.5, 100, 8.5));
            actor.setLevel(5);
            actor.getInventory().addItem(GolemItems.core(GolemCoreType.BREAKER));
            IronGolem golem = world.spawn(new Location(world, 2.5, 100, 0.5), IronGolem.class);
            golem.setPlayerCreated(true);
            golem.addScoreboardTag(IRON_GOLEM_TAG);
            return true;
        }
        if (RANCHER_BINDING_STAGE.equals(name)) {
            opponent.teleport(new Location(world, 0.5, 100, 8.5));
            actor.setLevel(5);
            actor.getInventory().addItem(GolemItems.core(GolemCoreType.RANCHER));
            CopperGolem golem = world.spawn(new Location(world, 2.5, 100, 0.5), CopperGolem.class);
            golem.addScoreboardTag(COPPER_GOLEM_TAG);
            return true;
        }
        if (ANGLER_BINDING_STAGE.equals(name)) {
            opponent.teleport(new Location(world, 0.5, 100, 8.5));
            actor.setLevel(5);
            actor.getInventory().addItem(GolemItems.core(GolemCoreType.ANGLER));
            CopperGolem golem = world.spawn(new Location(world, 2.5, 100, 0.5), CopperGolem.class);
            golem.addScoreboardTag(COPPER_GOLEM_TAG);
            return true;
        }
        if (OVERSEER_STAGE.equals(name)) {
            opponent.teleport(new Location(world, 0.5, 100, 8.5));
            actor.setLevel(0);
            actor.getInventory().clear();
            actor.getInventory().setItem(0, GolemItems.blankCore());
            actor.getInventory().setItem(1, GolemItems.crook());
            actor.getInventory().setHeldItemSlot(0);
            Snowman golem = world.spawn(new Location(world, 2.5, 100, 0.5), Snowman.class);
            golem.addScoreboardTag(GOLEM_TAG);
            return true;
        }
        if (COPPER_BINDING_STAGE.equals(name)) {
            opponent.teleport(new Location(world, 0.5, 100, 8.5));
            actor.setLevel(5);
            actor.getInventory().addItem(GolemItems.blankCore());
            CopperGolem golem = world.spawn(new Location(world, 2.5, 100, 0.5), CopperGolem.class);
            golem.addScoreboardTag(COPPER_GOLEM_TAG);
            return true;
        }
        if (IRON_BINDING_STAGE.equals(name)) {
            opponent.teleport(new Location(world, 0.5, 100, 8.5));
            actor.setLevel(5);
            actor.getInventory().addItem(GolemItems.blankCore());
            IronGolem golem = world.spawn(new Location(world, 2.5, 100, 0.5), IronGolem.class);
            golem.setPlayerCreated(true);
            golem.addScoreboardTag(IRON_GOLEM_TAG);
            return true;
        }
        if (MENDING_STAGE.equals(name)) {
            opponent.teleport(new Location(world, 0.5, 100, 8.5));
            actor.getInventory().clear();
            actor.getInventory().setItem(0, new ItemStack(Material.SNOW_BLOCK, 4));
            actor.getInventory().setHeldItemSlot(0);
            Snowman golem = world.spawn(new Location(world, 2.5, 100, 0.5), Snowman.class);
            golem.addScoreboardTag(GOLEM_TAG);
            GolemancyRuntime.registry().orElseThrow().bind(actor, golem, GolemBody.FROST, GolemCoreType.BLANK);
            AttributeInstance maxHealth = golem.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null) {
                maxHealth.setBaseValue(MENDING_MAX_HEALTH);
            }
            golem.setHealth(MENDING_START_HEALTH);
            return true;
        }
        if (STASIS_STAGE.equals(name)) {
            opponent.teleport(new Location(world, 0.5, 100, 8.5));
            actor.getInventory().clear();
            actor.getInventory().setItem(0, GolemItems.crook());
            actor.getInventory().setHeldItemSlot(0);
            Snowman golem = world.spawn(new Location(world, 2.5, 100, 0.5), Snowman.class);
            golem.addScoreboardTag(GOLEM_TAG);
            GolemancyRuntime.registry().orElseThrow().bind(actor, golem, GolemBody.FROST, GolemCoreType.BLANK);
            return true;
        }
        if (BONE_FRAME_STAGE.equals(name)) {
            opponent.teleport(new Location(world, 0.5, 100, 8.5));
            actor.getInventory().clear();
            actor.getInventory().setItem(0, new ItemStack(Material.BONE, BONE_FRAME_BONES));
            actor.getInventory().setItem(1, GolemItems.crook());
            actor.getInventory().setHeldItemSlot(0);
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
        if (PLINTH_STAGE.equals(name)) {
            opponent.teleport(new Location(world, 0.5, 100, 8.5));
            actor.getInventory().clear();
            buildPlinth(world);
            actor.getInventory().setItem(0, GolemItems.blankRune());
            actor.getInventory().setHeldItemSlot(0);
            return true;
        }
        if (MASTERWORK_STAGE.equals(name)) {
            opponent.teleport(new Location(world, 0.5, 100, 8.5));
            actor.getInventory().clear();
            actor.setLevel(MASTERWORK_LEVELS);
            buildPlinth(world);
            GolemancyRuntime.plinths().ifPresent(PlinthService::indexLoadedChunks);
            for (int slot = 0; slot < MASTERWORK_ITEMS.size(); slot++) {
                actor.getInventory().setItem(slot, new ItemStack(MASTERWORK_ITEMS.get(slot)));
            }
            actor.getInventory().setItem(MASTERWORK_ITEMS.size(), GolemItems.crook());
            actor.getInventory().setHeldItemSlot(0);
            return true;
        }
        return false;
    }

    public static void clearRosters(Player... players) {
        for (Player player : players) {
            AdaptPlayer adaptPlayer = Adapt.instance.getAdaptServer().getPlayer(player);
            if (adaptPlayer == null) {
                continue;
            }
            GolemRoster roster = adaptPlayer.getData().getGolemancy();
            for (GolemRoster.GolemEntry entry : roster.golems()) {
                roster.remove(entry.id());
            }
        }
    }

    public static int clearDemoState(World world, int originX, int originY, int originZ, int plateRadius) {
        Player[] players = Bukkit.getOnlinePlayers().stream().filter(player -> player.getName().startsWith("AQA")).toArray(Player[]::new);
        clearRosters(players);
        Optional<NodeService> service = GolemancyRuntime.nodes();
        if (service.isEmpty()) {
            return 0;
        }
        int cleared = 0;
        for (NodeIndex.Entry entry : service.get().index().near(world.getUID(), originX, originY, originZ, plateRadius)) {
            Player owner = Bukkit.getPlayer(entry.record().owner());
            if (owner != null && service.get().remove(owner, entry.key())) {
                cleared++;
            }
        }
        return cleared;
    }

    public static String probe(Player owner) {
        StringBuilder out = new StringBuilder("ADAPT_QA DEMO PROBE");
        World world = owner.getWorld();
        Location at = owner.getLocation();
        long now = System.currentTimeMillis();
        Optional<GolemTaskBoards> boards = GolemancyRuntime.boards();
        GolemancyRuntime.nodes().ifPresent(service -> {
            for (NodeIndex.Entry entry : service.index().near(world.getUID(), at.getX(), at.getY(), at.getZ(), 80)) {
                NodeKey key = entry.key();
                out.append(" | node ").append(entry.record().type().id()).append('@').append(key.x()).append(',').append(key.y()).append(',').append(key.z())
                    .append(' ').append(key.face());
                boards.ifPresent(all -> {
                    GolemTaskBoard board = all.board(world.getUID());
                    out.append(" status=").append(board.status(key, now));
                    List<GolemTask> tasks = board.tasks(key);
                    out.append(" tasks=").append(tasks.size());
                    for (int index = 0; index < Math.min(3, tasks.size()); index++) {
                        GolemTask task = tasks.get(index);
                        out.append(" {").append(task.type()).append(' ').append(task.target()).append(" holder=").append(task.holder(now).isPresent()).append('}');
                    }
                });
            }
        });
        GolemancyRuntime.registry().ifPresent(registry -> {
            for (GolemHandle handle : registry.loadedOf(owner.getUniqueId())) {
                Location where = handle.entity().getLocation();
                out.append(" | golem ").append(handle.body()).append(' ').append(handle.state().mode()).append(" cores=").append(handle.state().cores())
                    .append(String.format(" at=%.1f,%.1f,%.1f", where.getX(), where.getY(), where.getZ())).append(" dormant=").append(handle.isDormant());
            }
        });
        return out.toString();
    }

    public static void warmItems() {
        GolemItems.crook();
        GolemItems.blankCore();
        GolemItems.core(GolemCoreType.GATHERER);
        GolemItems.blankNode();
        GolemItems.node(NodeType.DEPOSIT);
        GolemItems.blankRune();
        GolemItems.rune(new GolemRune(GolemRuneType.SWIFTNESS, 1));
        GolemItems.masterworkHeart();
        GolemItems.mayBeOwnerBound(Material.SNOW_BLOCK);
    }

    public static String give(Player player, int hotbarSlot, String itemId, int count) {
        if (hotbarSlot < 0 || hotbarSlot > 8) {
            throw new IllegalArgumentException("Hotbar slot must be 0 to 8, found " + hotbarSlot);
        }
        ItemStack item = "masterwork_heart".equals(itemId) ? GolemItems.masterworkHeart() : GolemItems.resolve(new NamespacedKey(GolemItems.NAMESPACE, itemId));
        if (item == null) {
            throw new IllegalArgumentException("Unknown Golemancy item " + itemId);
        }
        item.setAmount(count);
        player.getInventory().setItem(hotbarSlot, item);
        player.updateInventory();
        return "ADAPT_QA DEMO GIVE " + hotbarSlot + " " + itemId + " " + count;
    }

    public static JsonObject snapshot(Player player) {
        JsonObject result = new JsonObject();
        for (Entity entity : player.getWorld().getEntities()) {
            if (entity instanceof Snowman golem && golem.getScoreboardTags().contains(GOLEM_TAG)) {
                result.add("golem", state(golem, player));
            }
            if (entity instanceof CopperGolem golem && golem.getScoreboardTags().contains(COPPER_GOLEM_TAG)) {
                result.add("copperGolem", state(golem, player));
            }
            if (entity instanceof IronGolem golem && golem.getScoreboardTags().contains(IRON_GOLEM_TAG)) {
                result.add("ironGolem", state(golem, player));
            }
        }
        result.add("node", node(player));
        result.add("plinth", plinth(player));
        result.addProperty("dormantGolems", dormantGolems(player));
        result.add("servants", servants(player));
        return result;
    }

    private static JsonObject servants(Player player) {
        int servants = 0;
        int labeled = 0;
        for (Skeleton skeleton : player.getWorld().getEntitiesByClass(Skeleton.class)) {
            if (!player.getUniqueId().equals(TragoulSkeletalServant.getServantOwnerId(skeleton))) {
                continue;
            }
            servants++;
            for (Entity passenger : skeleton.getPassengers()) {
                if (passenger instanceof TextDisplay) {
                    labeled++;
                    break;
                }
            }
        }
        JsonObject result = new JsonObject();
        result.addProperty("count", servants);
        result.addProperty("labeled", labeled);
        return result;
    }

    private static int dormantGolems(Player player) {
        int dormant = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && GolemItems.kindOf(item).orElse(null) == GolemItemKind.DORMANT_GOLEM) {
                dormant += item.getAmount();
            }
        }
        return dormant;
    }

    private static void buildPlinth(World world) {
        world.getBlockAt(PLINTH_X, PLINTH_Y, PLINTH_Z).setType(Material.LODESTONE, false);
        world.getBlockAt(PLINTH_X + PEDESTAL_DISTANCE, PLINTH_Y, PLINTH_Z).setType(Material.CHISELED_COPPER, false);
        world.getBlockAt(PLINTH_X - PEDESTAL_DISTANCE, PLINTH_Y, PLINTH_Z).setType(Material.CHISELED_COPPER, false);
        world.getBlockAt(PLINTH_X, PLINTH_Y, PLINTH_Z + PEDESTAL_DISTANCE).setType(Material.CHISELED_COPPER, false);
        world.getBlockAt(PLINTH_X, PLINTH_Y, PLINTH_Z - PEDESTAL_DISTANCE).setType(Material.CHISELED_COPPER, false);
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

    private static JsonObject plinth(Player player) {
        JsonObject result = new JsonObject();
        World world = player.getWorld();
        Optional<String> stored = WorldData.loaded(world)
            .map(data -> data.get(PLINTH_X, PLINTH_Y, PLINTH_Z, PlinthSlotItem.class))
            .filter(PlinthSlotItem::readable)
            .flatMap(item -> GolemItems.kindOf(item.item()))
            .map(GolemItemKind::id);
        result.addProperty("center", stored.orElse(null));
        result.addProperty("ritual", centerLifted(world));
        int blankRunes = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && GolemItems.kindOf(item).orElse(null) == GolemItemKind.BLANK_RUNE) {
                blankRunes += item.getAmount();
            }
        }
        result.addProperty("blankRunes", blankRunes);
        return result;
    }

    private static boolean centerLifted(World world) {
        Location above = new Location(world, PLINTH_X + 0.5D, PLINTH_Y + 1.35D, PLINTH_Z + 0.5D);
        for (Entity entity : world.getNearbyEntities(above, 0.3D, 0.3D, 0.3D)) {
            if (entity instanceof ItemDisplay display && display.getTransformation().getTranslation().y() > 0F) {
                return true;
            }
        }
        return false;
    }

    private static JsonObject state(Mob golem, Player player) {
        JsonObject result = new JsonObject();
        Optional<GolemState> state = GolemState.read(golem);
        result.addProperty("entityId", golem.getEntityId());
        result.addProperty("bound", state.isPresent());
        result.addProperty("boundToPlayer", state.map(bound -> bound.owner().equals(player.getUniqueId())).orElse(false));
        result.addProperty("core", state.map(bound -> bound.primaryCore().id()).orElse(""));
        result.addProperty("mode", state.map(bound -> bound.mode().id()).orElse(""));
        result.addProperty("aware", golem.isAware());
        AttributeInstance scale = golem.getAttribute(Attribute.SCALE);
        result.addProperty("scale", scale == null ? 1D : scale.getValue());
        AttributeInstance maxHealth = golem.getAttribute(Attribute.MAX_HEALTH);
        result.addProperty("maxHealth", maxHealth == null ? 0D : maxHealth.getValue());
        result.addProperty("maxHealthBase", maxHealth == null ? 0D : maxHealth.getBaseValue());
        result.addProperty("health", golem.getHealth());
        AttributeInstance speed = golem.getAttribute(Attribute.MOVEMENT_SPEED);
        result.addProperty("speed", speed == null ? 0D : speed.getValue());
        result.addProperty("speedBase", speed == null ? 0D : speed.getBaseValue());
        AttributeInstance armor = golem.getAttribute(Attribute.ARMOR);
        result.addProperty("armor", armor == null ? 0D : armor.getValue());
        result.addProperty("armorBase", armor == null ? 0D : armor.getBaseValue());
        AttributeInstance attack = golem.getAttribute(Attribute.ATTACK_DAMAGE);
        result.addProperty("attack", attack == null ? 0D : attack.getValue());
        result.addProperty("attackBase", attack == null ? 0D : attack.getBaseValue());
        return result;
    }
}
