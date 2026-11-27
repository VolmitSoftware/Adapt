/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.PlatformNeed;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.api.item.ShapedRecipeParser;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.content.golem.GolemancyRuntime;
import art.arcane.adapt.content.golem.NodeType;
import art.arcane.adapt.content.golem.item.ConfiguredShapedRecipe;
import art.arcane.adapt.content.golem.item.GolemItems;
import art.arcane.adapt.content.golem.node.DepositBehavior;
import art.arcane.adapt.content.golem.node.NodeBehaviors;
import art.arcane.adapt.content.golem.node.NodeCuboid;
import art.arcane.adapt.content.golem.node.NodePlacementVerdict;
import art.arcane.adapt.content.golem.node.NodeRecord;
import art.arcane.adapt.content.golem.node.NodeRules;
import art.arcane.adapt.content.golem.node.NodeService;
import art.arcane.adapt.content.golem.node.NodeWeavingPolicy;
import art.arcane.adapt.platform.EngineFeature;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


public class GolemNodeWeaving extends SimpleAdaptation<GolemNodeWeaving.Config> implements NodeWeavingPolicy {
  public static final Id ID = Id.adapt("golem/node_weaving");
  static final String BLANK_NODE_RECIPE = "golemancy-blank-node";
  static final String DEPOSIT_NODE_RECIPE = "golemancy-deposit-node";
  private static final int RECIPE_LEVEL = 1;
  private static final double FEEDBACK_FACE_DISTANCE = 0.6D;
  private static final List<PlayerPreference<?>> PREFERENCES =
      List.of(CommonPreferences.ENABLED, GolemancyPreferences.NODE_REVEAL, GolemancyPreferences.WORK_AREA_OUTLINE);

  private final DepositBehavior deposits = new DepositBehavior(this);

  public GolemNodeWeaving() {
    super(ID);
    needs(PlatformNeed.partialFeature(EngineFeature.ITEM_STACK_LIMITS, "golem items stack up to their base item's limit"));
    registerConfiguration(Config.class);
    setLocalizationKey("adapt.golemancy.node_weaving");
    setIcon(Id.minecraft("clay_ball"));
    GolemancyRuntime.setNodeWeaving(this);
    ShapedRecipeParser parser = ShapedRecipeParser.platform(GolemItems.resolver());
    GolemancySupport.registerRecipe(this, parser, BLANK_NODE_RECIPE, getConfig().blankNodeRecipe, RECIPE_LEVEL);
    GolemancySupport.registerRecipe(this, parser, DEPOSIT_NODE_RECIPE, getConfig().depositNodeRecipe, RECIPE_LEVEL);
  }

  @Override
  public void unregister() {
    NodeBehaviors.unregister(NodeType.DEPOSIT, deposits);
    GolemancyRuntime.clearNodeWeaving(this);
    super.unregister();
  }

  @Override
  protected void onRuntimeActivated() {
    NodeBehaviors.register(NodeType.DEPOSIT, deposits);
  }




  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, nodeCap(level), 1);
    statLore(v, maxAreaRadius(level), 2);
    statLore(v, filterSlots(level), 3);
    if (NodeRules.allowsPriority(level)) {
      unlockLore(v, Text.key("adapt.golemancy.node_weaving.lore4"));
    }
    if (NodeRules.allowsRedstone(level)) {
      unlockLore(v, Text.key("adapt.golemancy.node_weaving.lore5"));
    }
    if (NodeRules.allowsBlacklistAndTags(level)) {
      unlockLore(v, Text.key("adapt.golemancy.node_weaving.lore6"));
    }
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return PREFERENCES;
  }

  @Override
  public int level(PlayerRef player) {
    return getActiveLevel(player);
  }

  @Override
  public int nodeCap(int level) {
    return nodeCap(getConfig(), level);
  }

  @Override
  public int maxAreaRadius(int level) {
    return maxAreaRadius(getConfig(), level);
  }

  @Override
  public int filterSlots(int level) {
    return filterSlots(getConfig(), level);
  }

  @Override
  public boolean canPlace(PlayerRef player, Loc anchor, Loc placement) {
    return canInteract(player, anchor) && canBlockPlace(player, placement);
  }

  @Override
  public boolean canAccessContainer(PlayerRef player, Loc container) {
    return canAccessChest(player, container);
  }

  @Override
  public boolean crookOnlyReveal(PlayerRef viewer) {
    return preference(viewer, GolemancyPreferences.NODE_REVEAL) == GolemancyPreferences.NodeReveal.CROOK_ONLY;
  }

  @Override
  public boolean hidesOutline(PlayerRef viewer) {
    return !preferenceEnabled(viewer, GolemancyPreferences.WORK_AREA_OUTLINE);
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(PlayerInteractEvent e) {
    PlayerRef player = e.player();
    BlockRef anchor = e.clickedBlock();
    if (e.action() != InteractAction.RIGHT_CLICK_BLOCK || anchor == null || !player.isSneaking() || onPlinth(anchor)) {
      return;
    }
    if (e.hand() != Hand.MAIN_HAND) {
      if (GolemItems.nodeTypeOf(player.inventory().mainHand()).isPresent() && getActiveLevel(player) > 0) {
        e.setCancelled(true);
      }
      return;
    }
    Optional<NodeType> type = GolemItems.nodeTypeOf(e.item());
    BlockFace face = e.clickedFace();
    if (type.isEmpty() || !NodeRecord.isNodeFace(face) || getActiveLevel(player) <= 0) {
      return;
    }
    e.setCancelled(true);
    place(player, anchor, face, type.get());
  }

  static int nodeCap(Config config, int level) {
    return Math.max(0, AnimusBindingRules.atLevel(config.nodeCapByLevel, level, 0));
  }

  static int maxAreaRadius(Config config, int level) {
    if (level < 1) {
      return 0;
    }
    return Math.clamp(AnimusBindingRules.atLevel(config.maxAreaRadiusByLevel, level, 1), 1, NodeCuboid.MAX_SPAN - 1);
  }

  static int filterSlots(Config config, int level) {
    return Math.max(0, Math.min(NodeRecord.MAX_FILTER_ENTRIES, AnimusBindingRules.atLevel(config.filterSlotsByLevel, level, 0)));
  }

  private void place(PlayerRef player, BlockRef anchor, BlockFace face, NodeType type) {
    Optional<NodeService> service = GolemancyRuntime.nodes();
    if (service.isEmpty()) {
      warn(player, Text.key("adapt.golemancy.node_message.unavailable"));
      return;
    }
    NodePlacementVerdict verdict = service.get().place(player, anchor, face, type, this);
    if (verdict != NodePlacementVerdict.READY) {
      deny(player, verdict, type);
      return;
    }
    decrementItemstack(player.inventory().mainHand(), player);
    player.saveData();
    Loc at = anchor.location().add(0.5D + face.dx() * FEEDBACK_FACE_DISTANCE,
        0.5D + face.dy() * FEEDBACK_FACE_DISTANCE, 0.5D + face.dz() * FEEDBACK_FACE_DISTANCE);
    rewardFirstNode(player, type, at);
    AdaptApi.scheduler().runEntity(player, () -> confirmPlacement(player, at, type));
  }

  void rewardFirstNode(PlayerRef player, NodeType type, Loc at) {
    if (firstNode(player, type)) {
      xp(player, at, getConfig().firstNodeXp, "first_node:" + type.id());
    }
  }

  boolean firstNode(PlayerRef player, NodeType type) {
    String stat = "golemancy.nodes." + type.id();
    boolean first = statOf(player, stat) <= 0D;
    addStat(player, stat, 1D);
    return first;
  }

  private static boolean onPlinth(BlockRef block) {
    return GolemancyRuntime.plinths().map(plinths -> plinths.isPlinthBlock(block)).orElse(false);
  }

  private void confirmPlacement(PlayerRef player, Loc at, NodeType type) {
    fx(at, FxPriority.GAMEPLAY)
        .burst(Particles.END_ROD, 8, 0.2D)
        .sound(Sounds.BLOCK_AMETHYST_BLOCK_PLACE, 0.6F, 1.4F);
    HudStatus.show(player, TextFormat.AQUA + Text.key("adapt.golemancy.node_message.placed").withMarkup("node", type.displayName().resolve()).resolve());
  }

  private void deny(PlayerRef player, NodePlacementVerdict verdict, NodeType type) {
    switch (verdict) {
      case READY -> {
      }
      case UNAVAILABLE -> warn(player, Text.key("adapt.golemancy.node_message.unavailable"));
      case NOT_LOADED -> warn(player, Text.key("adapt.golemancy.node_message.loading"));
      case OCCUPIED -> warn(player, Text.key("adapt.golemancy.node_message.occupied"));
      case PROTECTED -> warn(player, Text.key("adapt.golemancy.node_message.protected"));
      case NEEDS_SIDE_FACE -> warn(player, Text.key("adapt.golemancy.node_message.needs_side_face").withMarkup("node", String.valueOf(type.displayName().resolve())));
      case NEEDS_CONTAINER -> warn(player, Text.key("adapt.golemancy.node_message.needs_container").withMarkup("node", String.valueOf(type.displayName().resolve())));
      case CONTAINER_PROTECTED -> warn(player, Text.key("adapt.golemancy.node_message.container_protected"));
      case CHUNK_FULL -> warn(player, Text.key("adapt.golemancy.node_message.chunk_full").withMarkup("count", String.valueOf(GolemancyRuntime.nodeSettings().maxNodesPerChunk())));
      case NODE_CAP -> warn(player, Text.key("adapt.golemancy.node_message.cap").withMarkup("count", String.valueOf(nodeCap(getActiveLevel(player)))));
    }
  }

  private static void warn(PlayerRef player, Text key) {
    AdaptApi.scheduler().runEntity(player, () -> HudStatus.show(player, TextFormat.RED + key.resolve()));
  }

  @ConfigDescription("Sneak-right-click a block face while holding a node to anchor it there and direct your golems' work.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Most nodes you can own at once, by Node Weaving level from level 1 upward.", impact = "Higher values let players place more nodes.")
    List<Integer> nodeCapByLevel = new ArrayList<>(List.of(4, 8, 12, 18, 24));
    @ConfigDoc(value = "Largest face distance from a node in blocks, by Node Weaving level from level 1 upward. Values are clamped to 1 through 15; each area dimension is at most 16 blocks.", impact = "Higher values let players shift areas farther from the node while keeping it inside; new nodes start at radius 2 or this cap, whichever is lower.")
    List<Integer> maxAreaRadiusByLevel = new ArrayList<>(List.of(8, 12, 15, 15, 15));
    @ConfigDoc(value = "Filter entries a node can hold, by Node Weaving level from level 1 upward. Each value is capped at 18.", impact = "Higher values let a node filter more items and tags.")
    List<Integer> filterSlotsByLevel = new ArrayList<>(List.of(3, 6, 6, 9, 18));
    @ConfigDoc(value = "Golemancy XP paid the first time you place each type of node.", impact = "Higher values reward trying each node type more; each type pays once per player.")
    double firstNodeXp = 200;
    @ConfigDoc(value = "Crafting recipe for the Blank Node, unlocked at Node Weaving level 1.", impact = "Edit the rows, keys, result or amount to change the recipe; changes apply after a restart.")
    ConfiguredShapedRecipe blankNodeRecipe = new ConfiguredShapedRecipe()
        .rows("CRC", "RAR", "CRC")
        .key('C', "CLAY_BALL")
        .key('R', "REDSTONE")
        .key('A', "AMETHYST_SHARD")
        .result("golemancy:blank_node", 4);
    @ConfigDoc(value = "Crafting recipe for the Deposit Node, unlocked at Node Weaving level 1.", impact = "Edit the rows, keys, result or amount to change the recipe; changes apply after a restart.")
    ConfiguredShapedRecipe depositNodeRecipe = new ConfiguredShapedRecipe()
        .rows(" X ", " B ", "   ")
        .key('X', "CHEST")
        .key('B', "golemancy:blank_node")
        .result("golemancy:node/deposit", 1);

    public Config() {
      costFactor = 0.5;
      initialCost = 1;
      baseCost = 2;
      requires = new ArrayList<>(List.of("adapt:golem/animus_binding@1"));
    }
  }
}
