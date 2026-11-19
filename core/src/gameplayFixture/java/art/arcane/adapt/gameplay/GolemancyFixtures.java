package art.arcane.adapt.gameplay;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.data.BlockStorePort;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemRoster;
import art.arcane.adapt.api.golem.GolemRune;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.item.GolemItemKind;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeService;
import art.arcane.adapt.api.golem.plinth.PlinthService;
import art.arcane.adapt.api.golem.plinth.PlinthSlotCodec;
import art.arcane.adapt.api.golem.plinth.PlinthSlotItem;
import art.arcane.adapt.api.golem.plinth.PlinthStructure;
import art.arcane.adapt.api.preference.PlayerPreferenceData;
import art.arcane.adapt.api.preference.PlayerPreferences;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.adaptation.tragoul.TragoulSkeletalServant;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.display.ItemDisplayRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.WorldRef;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class GolemancyFixtures {
    private static final String GOLEMANCY_SKILL = "golemancy";
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
    private static final List<String> MASTERWORK_ITEMS = List.of("heart_of_the_sea", "diamond_block", "iron_block",
        "copper_block", "snow_block");
    private static final List<BlockFace> FACES = List.of(BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST);
    private static final Id SNOW_GOLEM = Id.minecraft("snow_golem");
    private static final Id COPPER_GOLEM = Id.minecraft("copper_golem");
    private static final Id IRON_GOLEM = Id.minecraft("iron_golem");
    private static final Id SKELETON = Id.minecraft("skeleton");
    private static final Id TEXT_DISPLAY = Id.minecraft("text_display");
    private static final Id MAX_HEALTH = Id.minecraft("max_health");
    private static final Id SCALE = Id.minecraft("scale");
    private static final Id MOVEMENT_SPEED = Id.minecraft("movement_speed");
    private static final Id ARMOR = Id.minecraft("armor");
    private static final Id ATTACK_DAMAGE = Id.minecraft("attack_damage");

    private GolemancyFixtures() {
    }

    public static boolean stage(String name, WorldRef world, PlayerRef actor, PlayerRef opponent) {
        if (name.equals("golemancy") || name.startsWith("golemancy-")) {
            Fixtures.commandIn(world, "fillbiome -8 96 -8 8 108 8 minecraft:plains");
        }
        if (BINDING_STAGE.equals(name)) {
            bindingStage(world, actor, opponent, 0, GolemItems.blankCore(), "snow_golem", GOLEM_TAG);
            return true;
        }
        if (GATHERER_BINDING_STAGE.equals(name)) {
            bindingStage(world, actor, opponent, 0, GolemItems.core(GolemCoreType.GATHERER), "snow_golem", GOLEM_TAG);
            return true;
        }
        if (GUARDIAN_BINDING_STAGE.equals(name)) {
            bindingStage(world, actor, opponent, 0, GolemItems.core(GolemCoreType.GUARDIAN), "snow_golem", GOLEM_TAG);
            return true;
        }
        if (HARVESTER_BINDING_STAGE.equals(name)) {
            bindingStage(world, actor, opponent, 0, GolemItems.core(GolemCoreType.HARVESTER), "snow_golem", GOLEM_TAG);
            return true;
        }
        if (LUMBER_BINDING_STAGE.equals(name)) {
            workerBindingStage(world, actor, opponent, GolemItems.core(GolemCoreType.LUMBER));
            return true;
        }
        if (SORTER_BINDING_STAGE.equals(name)) {
            bindingStage(world, actor, opponent, 5, GolemItems.core(GolemCoreType.SORTER), "copper_golem", COPPER_GOLEM_TAG);
            return true;
        }
        if (BREAKER_BINDING_STAGE.equals(name)) {
            bindingStage(world, actor, opponent, 5, GolemItems.core(GolemCoreType.BREAKER), "iron_golem", IRON_GOLEM_TAG);
            return true;
        }
        if (RANCHER_BINDING_STAGE.equals(name)) {
            workerBindingStage(world, actor, opponent, GolemItems.core(GolemCoreType.RANCHER));
            return true;
        }
        if (ANGLER_BINDING_STAGE.equals(name)) {
            workerBindingStage(world, actor, opponent, GolemItems.core(GolemCoreType.ANGLER));
            return true;
        }
        if (OVERSEER_STAGE.equals(name)) {
            Fixtures.teleport(opponent, world, 0.5, 100, 8.5);
            actor.setLevel(0);
            Fixtures.clearInventory(actor);
            actor.inventory().setItem(0, GolemItems.blankCore());
            actor.inventory().setItem(1, GolemItems.crook());
            actor.inventory().setHeldSlot(0);
            spawnGolem(world, "snow_golem", GOLEM_TAG);
            return true;
        }
        if (COPPER_BINDING_STAGE.equals(name)) {
            bindingStage(world, actor, opponent, 5, GolemItems.blankCore(), "copper_golem", COPPER_GOLEM_TAG);
            return true;
        }
        if (IRON_BINDING_STAGE.equals(name)) {
            bindingStage(world, actor, opponent, 5, GolemItems.blankCore(), "iron_golem", IRON_GOLEM_TAG);
            return true;
        }
        if (MENDING_STAGE.equals(name)) {
            Fixtures.teleport(opponent, world, 0.5, 100, 8.5);
            Fixtures.clearInventory(actor);
            actor.inventory().setItem(0, Fixtures.item("snow_block", 4));
            actor.inventory().setHeldSlot(0);
            MobRef golem = spawnGolem(world, "snow_golem", GOLEM_TAG);
            GolemancyRuntime.registry().orElseThrow().bind(actor, golem, GolemBody.FROST, GolemCoreType.BLANK);
            if (golem.hasAttribute(MAX_HEALTH)) {
                golem.setAttributeBase(MAX_HEALTH, MENDING_MAX_HEALTH);
            }
            golem.setHealth(MENDING_START_HEALTH);
            return true;
        }
        if (STASIS_STAGE.equals(name)) {
            Fixtures.teleport(opponent, world, 0.5, 100, 8.5);
            Fixtures.clearInventory(actor);
            actor.inventory().setItem(0, GolemItems.crook());
            actor.inventory().setHeldSlot(0);
            MobRef golem = spawnGolem(world, "snow_golem", GOLEM_TAG);
            GolemancyRuntime.registry().orElseThrow().bind(actor, golem, GolemBody.FROST, GolemCoreType.BLANK);
            return true;
        }
        if (BONE_FRAME_STAGE.equals(name)) {
            Fixtures.teleport(opponent, world, 0.5, 100, 8.5);
            Fixtures.clearInventory(actor);
            actor.inventory().setItem(0, Fixtures.item("bone", BONE_FRAME_BONES));
            actor.inventory().setItem(1, GolemItems.crook());
            actor.inventory().setHeldSlot(0);
            return true;
        }
        if (NODE_STAGE.equals(name)) {
            Fixtures.teleport(opponent, world, 0.5, 100, 8.5);
            clearNodes(world, actor);
            Fixtures.clearInventory(actor);
            Fixtures.set(world, NODE_X, NODE_Y, NODE_Z, "chest");
            ItemRef nodes = GolemItems.node(NodeType.DEPOSIT);
            nodes.setAmount(2);
            Fixtures.give(actor, nodes);
            return true;
        }
        if (PLINTH_STAGE.equals(name)) {
            Fixtures.teleport(opponent, world, 0.5, 100, 8.5);
            Fixtures.clearInventory(actor);
            buildPlinth(world);
            actor.inventory().setItem(0, GolemItems.blankRune());
            actor.inventory().setHeldSlot(0);
            return true;
        }
        if (MASTERWORK_STAGE.equals(name)) {
            Fixtures.teleport(opponent, world, 0.5, 100, 8.5);
            Fixtures.clearInventory(actor);
            actor.setLevel(MASTERWORK_LEVELS);
            buildPlinth(world);
            GolemancyRuntime.plinths().ifPresent(PlinthService::indexLoadedChunks);
            for (int slot = 0; slot < MASTERWORK_ITEMS.size(); slot++) {
                actor.inventory().setItem(slot, Fixtures.item(MASTERWORK_ITEMS.get(slot)));
            }
            actor.inventory().setItem(MASTERWORK_ITEMS.size(), GolemItems.crook());
            actor.inventory().setHeldSlot(0);
            return true;
        }
        return false;
    }

    private static void bindingStage(WorldRef world, PlayerRef actor, PlayerRef opponent, int level, ItemRef core, String type, String tag) {
        Fixtures.teleport(opponent, world, 0.5, 100, 8.5);
        actor.setLevel(level);
        Fixtures.give(actor, core);
        spawnGolem(world, type, tag);
    }

    /**
     * Lumber, Rancher and Angler cores fit copper and iron golems; before copper golems exist (1.21.9) the stage binds an
     * iron golem instead.
     */
    private static void workerBindingStage(WorldRef world, PlayerRef actor, PlayerRef opponent, ItemRef core) {
        if (Fixtures.host().registries().exists(RegistryKind.ENTITY_TYPE, COPPER_GOLEM)) {
            bindingStage(world, actor, opponent, 5, core, "copper_golem", COPPER_GOLEM_TAG);
        } else {
            bindingStage(world, actor, opponent, 5, core, "iron_golem", IRON_GOLEM_TAG);
        }
    }

    private static MobRef spawnGolem(WorldRef world, String type, String tag) {
        MobRef golem = (MobRef) Fixtures.spawn(world, type, 2.5, 100, 0.5);
        if (type.equals("iron_golem")) {
            golem.setPlayerCreated(true);
        }
        Fixtures.tag(golem, tag);
        return golem;
    }

    public static void clearRosters(PlayerRef... players) {
        for (PlayerRef player : players) {
            AdaptPlayer adaptPlayer = Fixtures.adaptPlayer(player);
            if (adaptPlayer == null) {
                continue;
            }
            clearRoster(adaptPlayer.getData().getGolemancy());
            resetPreferences(adaptPlayer);
        }
    }

    private static void clearRoster(GolemRoster roster) {
        for (GolemRoster.GolemEntry entry : roster.golems()) {
            roster.remove(entry.id());
        }
        List<GolemRune> lattice = roster.lattice();
        for (int index = lattice.size() - 1; index >= 0; index--) {
            roster.removeLattice(index, lattice.get(index));
        }
        roster.setServantChannel(null);
    }

    private static void resetPreferences(AdaptPlayer learner) {
        Map<String, Adaptation<?>> adaptations = golemancyAdaptations();
        for (String name : resetStored(learner.getData().getPreferences(), adaptations.keySet())) {
            PlayerPreferences.reconcile(adaptations.get(name), learner);
        }
    }

    private static List<String> resetStored(PlayerPreferenceData data, Collection<String> adaptationNames) {
        List<String> reset = new ArrayList<>(adaptationNames.size());
        for (String name : adaptationNames) {
            if (data.reset(name)) {
                reset.add(name);
            }
        }
        return reset;
    }

    private static Map<String, Adaptation<?>> golemancyAdaptations() {
        Map<String, Adaptation<?>> adaptations = new LinkedHashMap<>();
        for (Skill<?> skill : Fixtures.core().getAdaptServer().getSkillRegistry().getAllSkills()) {
            if (!skill.getName().equals(GOLEMANCY_SKILL)) {
                continue;
            }
            for (Adaptation<?> adaptation : skill.getAdaptations()) {
                adaptations.put(adaptation.getName(), adaptation);
            }
        }
        return adaptations;
    }

    public static JsonObject snapshot(PlayerRef player) {
        JsonObject result = new JsonObject();
        for (EntityRef entity : player.world().entities()) {
            if (!(entity instanceof MobRef golem)) {
                continue;
            }
            if (golem.type().equals(SNOW_GOLEM) && Fixtures.tags(golem).contains(GOLEM_TAG)) {
                result.add("golem", state(golem, player));
            }
            if (golem.type().equals(COPPER_GOLEM) && Fixtures.tags(golem).contains(COPPER_GOLEM_TAG)) {
                result.add("copperGolem", state(golem, player));
            }
            if (golem.type().equals(IRON_GOLEM) && Fixtures.tags(golem).contains(IRON_GOLEM_TAG)) {
                result.add("ironGolem", state(golem, player));
            }
        }
        result.add("node", node(player));
        result.add("plinth", plinth(player));
        result.addProperty("dormantGolems", dormantGolems(player));
        result.add("servants", servants(player));
        return result;
    }

    private static JsonObject servants(PlayerRef player) {
        int servants = 0;
        int labeled = 0;
        for (EntityRef skeleton : player.world().entities()) {
            if (!skeleton.type().equals(SKELETON) || !player.uuid().equals(TragoulSkeletalServant.getServantOwnerId(skeleton))) {
                continue;
            }
            servants++;
            for (EntityRef passenger : skeleton.passengers()) {
                if (passenger.type().equals(TEXT_DISPLAY)) {
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

    private static int dormantGolems(PlayerRef player) {
        int dormant = 0;
        for (ItemRef item : player.inventory().contents()) {
            if (!item.isEmpty() && GolemItems.kindOf(item).orElse(null) == GolemItemKind.DORMANT_GOLEM) {
                dormant += item.amount();
            }
        }
        return dormant;
    }

    private static void buildPlinth(WorldRef world) {
        String pedestal = PlinthStructure.defaultPedestals().get(0).toLowerCase(Locale.ROOT);
        Fixtures.set(world, PLINTH_X, PLINTH_Y, PLINTH_Z, "lodestone");
        Fixtures.set(world, PLINTH_X + PEDESTAL_DISTANCE, PLINTH_Y, PLINTH_Z, pedestal);
        Fixtures.set(world, PLINTH_X - PEDESTAL_DISTANCE, PLINTH_Y, PLINTH_Z, pedestal);
        Fixtures.set(world, PLINTH_X, PLINTH_Y, PLINTH_Z + PEDESTAL_DISTANCE, pedestal);
        Fixtures.set(world, PLINTH_X, PLINTH_Y, PLINTH_Z - PEDESTAL_DISTANCE, pedestal);
    }

    private static void clearNodes(WorldRef world, PlayerRef actor) {
        Optional<NodeService> service = GolemancyRuntime.nodes();
        if (service.isEmpty()) {
            return;
        }
        for (BlockFace face : FACES) {
            service.get().remove(actor, new NodeKey(world.uid(), NODE_X, NODE_Y, NODE_Z, face));
        }
    }

    private static JsonObject node(PlayerRef player) {
        JsonObject result = new JsonObject();
        int nodes = 0;
        Optional<NodeService> service = GolemancyRuntime.nodes();
        for (BlockFace face : FACES) {
            Optional<NodeRecord> record = service.flatMap(nodeService -> nodeService.node(new NodeKey(player.world().uid(), NODE_X, NODE_Y, NODE_Z, face)));
            if (record.isEmpty()) {
                continue;
            }
            nodes++;
            result.addProperty("type", record.get().type().id());
            result.addProperty("face", face.name());
            result.addProperty("ownedByPlayer", record.get().owner().equals(player.uuid()));
        }
        result.addProperty("nodes", nodes);
        int items = 0;
        for (ItemRef item : player.inventory().contents()) {
            if (!item.isEmpty() && GolemItems.nodeTypeOf(item).isPresent()) {
                items += item.amount();
            }
        }
        result.addProperty("nodeItems", items);
        return result;
    }

    private static JsonObject plinth(PlayerRef player) {
        JsonObject result = new JsonObject();
        WorldRef world = player.world();
        Optional<String> stored = Optional.ofNullable(world.isLoaded()
                ? BlockStorePort.current().get(world, PLINTH_X, PLINTH_Y, PLINTH_Z, PlinthSlotCodec.SLICE) : null)
            .filter(PlinthSlotItem::readable)
            .flatMap(item -> GolemItems.kindOf(item.item()))
            .map(GolemItemKind::id);
        result.addProperty("center", stored.orElse(null));
        result.addProperty("ritual", centerLifted(world));
        int blankRunes = 0;
        for (ItemRef item : player.inventory().contents()) {
            if (!item.isEmpty() && GolemItems.kindOf(item).orElse(null) == GolemItemKind.BLANK_RUNE) {
                blankRunes += item.amount();
            }
        }
        result.addProperty("blankRunes", blankRunes);
        return result;
    }

    private static boolean centerLifted(WorldRef world) {
        Vec3 above = new Vec3(PLINTH_X + 0.5D, PLINTH_Y + 1.35D, PLINTH_Z + 0.5D);
        for (EntityRef entity : world.nearbyEntities(above, 0.3D, 0.3D, 0.3D)) {
            if (entity instanceof ItemDisplayRef display && display.transform().translation().y() > 0F) {
                return true;
            }
        }
        return false;
    }

    private static JsonObject state(MobRef golem, PlayerRef player) {
        JsonObject result = new JsonObject();
        Optional<GolemState> state = GolemState.read(golem);
        result.addProperty("entityId", golem.entityId());
        result.addProperty("bound", state.isPresent());
        result.addProperty("boundToPlayer", state.map(bound -> bound.owner().equals(player.uuid())).orElse(false));
        result.addProperty("core", state.map(bound -> bound.primaryCore().id()).orElse(""));
        result.addProperty("mode", state.map(bound -> bound.mode().id()).orElse(""));
        result.addProperty("aware", golem.isAware());
        result.addProperty("scale", golem.hasAttribute(SCALE) ? golem.attributeValue(SCALE) : 1D);
        addAttribute(result, golem, MAX_HEALTH, "maxHealth");
        result.addProperty("health", golem.health());
        addAttribute(result, golem, MOVEMENT_SPEED, "speed");
        addAttribute(result, golem, ARMOR, "armor");
        addAttribute(result, golem, ATTACK_DAMAGE, "attack");
        return result;
    }

    private static void addAttribute(JsonObject result, LivingRef entity, Id attribute, String key) {
        boolean present = entity.hasAttribute(attribute);
        result.addProperty(key, present ? entity.attributeValue(attribute) : 0D);
        result.addProperty(key + "Base", present ? entity.attributeBase(attribute) : 0D);
    }
}
