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

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.node.DepositBehavior;
import art.arcane.adapt.api.golem.node.NodeBehaviors;
import art.arcane.adapt.api.golem.node.NodePlacementVerdict;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeRules;
import art.arcane.adapt.api.golem.node.NodeService;
import art.arcane.adapt.api.golem.node.NodeWeavingPolicy;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.api.recipe.ConfiguredRecipeParser;
import art.arcane.adapt.api.recipe.ConfiguredShapedRecipe;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public class GolemNodeWeaving extends SimpleAdaptation<GolemNodeWeaving.Config> implements NodeWeavingPolicy {
  static final String BLANK_NODE_RECIPE = "golemancy-blank-node";
  static final String DEPOSIT_NODE_RECIPE = "golemancy-deposit-node";
  private static final int RECIPE_LEVEL = 1;
  private static final double FEEDBACK_FACE_DISTANCE = 0.6D;

  private final DepositBehavior deposits = new DepositBehavior(this);

  public GolemNodeWeaving() {
    super("golem-node-weaving");
    registerConfiguration(Config.class);
    setLocalizationKey("golemancy.node_weaving");
    setIcon(Material.CLAY_BALL);
    GolemancyRuntime.setNodeWeaving(this);
    ConfiguredRecipeParser parser = ConfiguredRecipeParser.bukkit(GolemItems.resolver(), GolemItems.recipeBook());
    registerGolemRecipe(parser, BLANK_NODE_RECIPE, getConfig().blankNodeRecipe);
    registerGolemRecipe(parser, DEPOSIT_NODE_RECIPE, getConfig().depositNodeRecipe);
  }

  @Override
  public void unregister() {
    NodeBehaviors.unregister(NodeType.DEPOSIT, deposits);
    super.unregister();
  }

  @Override
  protected void onRuntimeActivated() {
    NodeBehaviors.register(NodeType.DEPOSIT, deposits);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, nodeCap(level), 1);
    statLore(v, maxAreaRadius(level), 2);
    statLore(v, filterSlots(level), 3);
    if (NodeRules.allowsPriority(level)) {
      unlockLore(v, GolemancyMessages.NODE_WEAVING_LORE4);
    }
    if (NodeRules.allowsRedstone(level)) {
      unlockLore(v, GolemancyMessages.NODE_WEAVING_LORE5);
    }
    if (NodeRules.allowsBlacklistAndTags(level)) {
      unlockLore(v, GolemancyMessages.NODE_WEAVING_LORE6);
    }
  }

  @Override
  public int level(Player player) {
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
  public boolean canPlace(Player player, Location anchor, Location placement) {
    return canInteract(player, anchor) && canBlockPlace(player, placement);
  }

  @Override
  public boolean canAccessContainer(Player player, Location container) {
    return canAccessChest(player, container);
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void on(PlayerInteractEvent e) {
    Player player = e.getPlayer();
    Block anchor = e.getClickedBlock();
    if (e.getAction() != Action.RIGHT_CLICK_BLOCK || anchor == null || !player.isSneaking() || onPlinth(anchor)) {
      return;
    }
    if (e.getHand() != EquipmentSlot.HAND) {
      if (GolemItems.nodeTypeOf(player.getInventory().getItemInMainHand()).isPresent() && getActiveLevel(player) > 0) {
        e.setCancelled(true);
      }
      return;
    }
    Optional<NodeType> type = GolemItems.nodeTypeOf(e.getItem());
    BlockFace face = e.getBlockFace();
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
    return Math.max(0, Math.min(NodeRecord.MAX_RADIUS, AnimusBindingRules.atLevel(config.maxAreaRadiusByLevel, level, 0)));
  }

  static int filterSlots(Config config, int level) {
    return Math.max(0, Math.min(NodeRecord.MAX_FILTER_ENTRIES, AnimusBindingRules.atLevel(config.filterSlotsByLevel, level, 0)));
  }

  private void registerGolemRecipe(ConfiguredRecipeParser parser, String name, ConfiguredShapedRecipe spec) {
    NamespacedKey key = new NamespacedKey(Adapt.instance, name);
    parser.builder(key, spec).ifPresent(builder -> registerRecipe(builder.requiredLevel(RECIPE_LEVEL).build()));
    GolemItems.recipeTithe().set(key, spec.isEnabled() ? spec.getTithe() : 0);
  }

  private void place(Player player, Block anchor, BlockFace face, NodeType type) {
    Optional<NodeService> service = GolemancyRuntime.nodes();
    if (service.isEmpty()) {
      warn(player, GolemancyMessages.NODE_UNAVAILABLE);
      return;
    }
    NodePlacementVerdict verdict = service.get().place(player, anchor, face, type, this);
    if (verdict != NodePlacementVerdict.READY) {
      deny(player, verdict, type);
      return;
    }
    decrementItemstack(player.getInventory().getItemInMainHand(), player);
    player.saveData();
    Location at = anchor.getLocation().add(0.5D + face.getModX() * FEEDBACK_FACE_DISTANCE,
        0.5D + face.getModY() * FEEDBACK_FACE_DISTANCE, 0.5D + face.getModZ() * FEEDBACK_FACE_DISTANCE);
    J.runEntity(player, () -> confirmPlacement(player, at, type));
  }

  private static boolean onPlinth(Block block) {
    return GolemancyRuntime.plinths().map(plinths -> plinths.isPlinthBlock(block)).orElse(false);
  }

  private void confirmPlacement(Player player, Location at, NodeType type) {
    fx(at, FxPriority.GAMEPLAY)
        .burst(Particles.END_ROD, 8, 0.2D)
        .sound(Sound.BLOCK_AMETHYST_BLOCK_PLACE, 0.6F, 1.4F);
    AdaptHud.actionBar(player, C.AQUA + AdaptLanguage.text(GolemancyMessages.NODE_PLACED, trusted("node", AdaptLanguage.text(type.displayName()))));
  }

  private void deny(Player player, NodePlacementVerdict verdict, NodeType type) {
    switch (verdict) {
      case READY -> {
      }
      case UNAVAILABLE -> warn(player, GolemancyMessages.NODE_UNAVAILABLE);
      case NOT_LOADED -> warn(player, GolemancyMessages.NODE_LOADING);
      case OCCUPIED -> warn(player, GolemancyMessages.NODE_OCCUPIED);
      case PROTECTED -> warn(player, GolemancyMessages.NODE_PROTECTED);
      case NEEDS_SIDE_FACE -> warn(player, GolemancyMessages.NODE_NEEDS_SIDE_FACE, trusted("node", AdaptLanguage.text(type.displayName())));
      case NEEDS_CONTAINER -> warn(player, GolemancyMessages.NODE_NEEDS_CONTAINER, trusted("node", AdaptLanguage.text(type.displayName())));
      case CONTAINER_PROTECTED -> warn(player, GolemancyMessages.NODE_CONTAINER_PROTECTED);
      case CHUNK_FULL -> warn(player, GolemancyMessages.NODE_CHUNK_FULL, trusted("count", GolemancyRuntime.nodeSettings().maxNodesPerChunk()));
      case NODE_CAP -> warn(player, GolemancyMessages.NODE_CAP, trusted("count", nodeCap(getActiveLevel(player))));
    }
  }

  private static void unlockLore(Element v, TextKey key) {
    v.addLore(C.GREEN + "+ " + C.GRAY + AdaptLanguage.text(key));
  }

  private static void warn(Player player, TextKey key, MessageArgument... arguments) {
    J.runEntity(player, () -> AdaptHud.actionBar(player, C.RED + AdaptLanguage.text(key, arguments)));
  }

  @ConfigDescription("Sneak-right-click a block face while holding a node to anchor it there and direct your golems' work.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Most nodes you can own at once, by Node Weaving level from level 1 upward.", impact = "Higher values let players place more nodes.")
    List<Integer> nodeCapByLevel = new ArrayList<>(List.of(4, 8, 12, 18, 24));
    @ConfigDoc(value = "Largest work area radius in blocks a node can be set to, by Node Weaving level from level 1 upward. Each value is capped at 32.", impact = "Higher values let nodes cover larger areas; new nodes start at radius 2 or this cap, whichever is lower.")
    List<Integer> maxAreaRadiusByLevel = new ArrayList<>(List.of(2, 3, 4, 6, 8));
    @ConfigDoc(value = "Filter entries a node can hold, by Node Weaving level from level 1 upward. Each value is capped at 18.", impact = "Higher values let a node filter more items and tags.")
    List<Integer> filterSlotsByLevel = new ArrayList<>(List.of(3, 6, 6, 9, 18));
    @ConfigDoc(value = "Crafting recipe for the Blank Node, unlocked at Node Weaving level 1.", impact = "Edit the rows, keys, result, amount or tithe to change the recipe; changes apply after a restart.")
    ConfiguredShapedRecipe blankNodeRecipe = new ConfiguredShapedRecipe()
        .rows("CRC", "RAR", "CRC")
        .key('C', "CLAY_BALL")
        .key('R', "REDSTONE")
        .key('A', "AMETHYST_SHARD")
        .result("golemancy:blank_node", 4);
    @ConfigDoc(value = "Crafting recipe for the Deposit Node, unlocked at Node Weaving level 1.", impact = "Edit the rows, keys, result, amount or tithe to change the recipe; changes apply after a restart.")
    ConfiguredShapedRecipe depositNodeRecipe = new ConfiguredShapedRecipe()
        .rows(" X ", " B ", "   ")
        .key('X', "CHEST")
        .key('B', "golemancy:blank_node")
        .result("golemancy:node/deposit", 1);

    public Config() {
      requires = new ArrayList<>(List.of("golem-animus-binding:1"));
    }
  }
}
