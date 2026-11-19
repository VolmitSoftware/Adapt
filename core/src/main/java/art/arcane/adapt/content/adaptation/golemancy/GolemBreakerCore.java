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

import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.gui.NodePanels;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.node.NodeBehaviors;
import art.arcane.adapt.api.recipe.ConfiguredRecipeParser;
import art.arcane.adapt.api.recipe.ConfiguredShapedRecipe;
import art.arcane.adapt.api.support.PlatformNeed;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.platform.EngineFeature;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.item.ItemRef;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class GolemBreakerCore extends SimpleAdaptation<GolemBreakerCore.Config> {
  static final String CORE_RECIPE = "golemancy-breaker-core";
  static final String NODE_RECIPE = "golemancy-breaker-node";
  private static final int RECIPE_LEVEL = 1;
  private static final double TICKS_PER_SECOND = 20D;
  private static final Id PICKAXES = Id.minecraft("pickaxes");

  private final Map<Id, Boolean> tileEntities = new ConcurrentHashMap<>();
  private final Map<Id, ItemRef> tools = new ConcurrentHashMap<>();
  private final BreakerNodeBehavior breaker = new BreakerNodeBehavior(this);
  private final BreakerNodePanel panel = new BreakerNodePanel(this, type -> AdaptCore.platform().host().items().create(type));
  private volatile ParsedBlacklist parsed;
  private volatile ParsedTools parsedTools;

  public GolemBreakerCore() {
    super("golem-breaker-core");
    needs(PlatformNeed.partialFeature(EngineFeature.ITEM_STACK_LIMITS, "golem items stack up to their base item's limit"));
    registerConfiguration(Config.class);
    setLocalizationKey("golemancy.breaker_core");
    setIcon(Id.minecraft("iron_pickaxe"));
    ConfiguredRecipeParser parser = ConfiguredRecipeParser.platform(GolemItems.resolver(), GolemItems.recipeBook());
    GolemancySupport.registerRecipe(this, parser, CORE_RECIPE, getConfig().breakerCoreRecipe, RECIPE_LEVEL);
    GolemancySupport.registerRecipe(this, parser, NODE_RECIPE, getConfig().breakerNodeRecipe, RECIPE_LEVEL);
  }

  @Override
  public void unregister() {
    NodeBehaviors.unregister(NodeType.BREAKER, breaker);
    NodePanels.unregister(NodeType.BREAKER, panel);
    super.unregister();
  }

  @Override
  protected void onRuntimeActivated() {
    NodeBehaviors.register(NodeType.BREAKER, breaker);
    NodePanels.register(NodeType.BREAKER, panel);
  }

  @Override
  protected void normalizeLoadedConfig(Config loadedConfig) {
    loadedConfig.workXpPerBlock = GolemancyRules.nonNegative(loadedConfig.workXpPerBlock);
  }

  @Override
  public void addStats(int level, StatLines v) {
    Config config = getConfig();
    statLore(v, BreakerRules.maxHardness(config.maxHardnessByLevel, level), 1);
    statLore(v, C.YELLOW, "* ", BreakerRules.intervalTicks(config.intervalTicksByLevel, level) / TICKS_PER_SECOND, 2);
    statLore(v, Form.capitalizeWords(pickaxe(level).legacyName().toLowerCase(Locale.ROOT).replace('_', ' ')), 3);
    if (GolemancyRules.unlocked(level, config.silkTouchLevel)) {
      unlockLore(v, GolemancyMessages.BREAKER_CORE_LORE4);
    }
    if (GolemancyRules.unlocked(level, config.fortuneLevel)) {
      unlockLore(v, GolemancyMessages.BREAKER_CORE_LORE5);
    }
    if (BreakerRules.aboveFree(pickaxe(level))) {
      unlockLore(v, GolemancyMessages.BREAKER_CORE_LORE6);
    }
  }

  Config config() {
    return getConfig();
  }

  Set<Id> blacklist() {
    List<String> names = getConfig().blacklist;
    ParsedBlacklist current = parsed;
    if (current == null || current.names() != names) {
      current = new ParsedBlacklist(names, BreakerRules.blacklist(names, GolemBreakerCore::blockTag, GolemBreakerCore::knownBlock));
      parsed = current;
    }
    return current.blocks();
  }

  Id pickaxe(int level) {
    List<String> names = getConfig().toolByLevel;
    ParsedTools current = parsedTools;
    if (current == null || current.names() != names) {
      current = new ParsedTools(names, BreakerRules.tools(names, GolemBreakerCore::isPickaxe,
          name -> AdaptLog.warn("Breaker Core uses the level's default pickaxe instead of " + name + " in toolByLevel because it is not a pickaxe.")));
      parsedTools = current;
    }
    return BreakerRules.toolAt(current.tools(), level);
  }

  float hardness(Id type) {
    return AdaptCore.platform().host().items().blockHardness(type);
  }

  boolean tileEntity(Id type) {
    return tileEntities.computeIfAbsent(type, GolemBreakerCore::blockEntity);
  }

  ItemRef tool(Id type) {
    return tools.computeIfAbsent(type, item -> AdaptCore.platform().host().items().create(item));
  }

  private static boolean blockEntity(Id type) {
    return AdaptCore.platform().host().items().hasBlockEntity(type);
  }

  private static boolean isPickaxe(Id type) {
    return AdaptCore.platform().host().registries().isTagged(RegistryKind.ITEM, PICKAXES, type);
  }

  private static Collection<Id> blockTag(String entry) {
    Id key = Id.tryParse(entry.substring(1).toLowerCase(Locale.ROOT));
    return key == null ? List.of() : AdaptCore.platform().host().registries().tagValues(RegistryKind.BLOCK, key);
  }

  private static boolean knownBlock(Id type) {
    return AdaptCore.platform().host().registries().exists(RegistryKind.BLOCK, type);
  }

  private record ParsedBlacklist(List<String> names, Set<Id> blocks) {
  }

  private record ParsedTools(List<String> names, List<Id> tools) {
  }

  @ConfigDescription("Golems bound with a Breaker Core break the blocks a Breaker Node's filter lists inside its area and carry the drops, mining harder blocks with better pickaxes as the core grows stronger.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Ticks a Breaker spends on each block after it reaches it, by Breaker Core level from level 1 upward. Swiftness runes shorten it. Values below 1 count as 1.", impact = "Lower values make Breakers mine faster.")
    List<Integer> intervalTicksByLevel = new ArrayList<>(List.of(40, 30, 20, 15, 10));
    @ConfigDoc(value = "Hardest block a Breaker breaks, by Breaker Core level from level 1 upward. Stone is 1.5, cobblestone 2, deepslate 3 and obsidian 50.", impact = "Higher values let Breakers mine harder blocks sooner.")
    List<Double> maxHardnessByLevel = new ArrayList<>(List.of(2.0D, 3.0D, 5.0D, 10.0D, 50.0D));
    @ConfigDoc(value = "Best pickaxe tier a Breaker may mine with, by item name and Breaker Core level from level 1 upward. Up to an iron pickaxe it mines without a tool, and drops are rolled as if that pickaxe broke the block. Blocks that need a better pickaxe, such as obsidian, are broken only while the Breaker holds a pickaxe of that tier from its home chest, which wears with each of those breaks. Blocks this tier cannot harvest are left alone. Names that are not a pickaxe use the default pickaxe for that level, with a warning.", impact = "Better tiers let Breakers harvest more blocks, such as diamond ore with an iron pickaxe or obsidian with a diamond pickaxe from the home chest.")
    List<String> toolByLevel = new ArrayList<>(BreakerRules.DEFAULT_TOOLS);
    @ConfigDoc(value = "Breaker Core level at which Breaker Nodes can turn on Silk Touch. A Breaker mines blocks whole only while it holds a Silk Touch pickaxe from its home chest, which wears with each of those breaks.", impact = "Lower values unlock Silk Touch sooner.")
    int silkTouchLevel = 3;
    @ConfigDoc(value = "Breaker Core level at which Breaker Nodes can turn on Fortune, which raises ore drops at the Fortune level of the pickaxe the Breaker holds from its home chest. Without a Fortune pickaxe no Fortune applies. Silk Touch and Fortune never apply together.", impact = "Lower values unlock Fortune sooner.")
    int fortuneLevel = 5;
    @ConfigDoc(value = "Blocks Breakers never break, by block name or #block_tag. Breakers also never break blocks with a block entity, such as chests, furnaces, signs and spawners, the block under any node, or the blocks of an Animus Plinth.", impact = "Add a block or tag to protect it from Breakers.")
    List<String> blacklist = new ArrayList<>(BreakerRules.DEFAULT_BLACKLIST);
    @ConfigDoc(value = "Golemancy work XP paid for each block a Breaker breaks. Values below 0 count as 0.", impact = "Higher values pay more XP for mining.")
    double workXpPerBlock = 0.4;
    @ConfigDoc(value = "Crafting recipe for the Breaker Core, unlocked at Breaker Core level 1.", impact = "Edit the rows, keys, result or amount to change the recipe; changes apply after a restart.")
    ConfiguredShapedRecipe breakerCoreRecipe = new ConfiguredShapedRecipe()
        .rows(" X ", "YBY", " Z ")
        .key('X', "IRON_PICKAXE")
        .key('Y', "REDSTONE")
        .key('Z', "PISTON")
        .key('B', "golemancy:blank_core")
        .result("golemancy:core/breaker", 1);
    @ConfigDoc(value = "Crafting recipe for the Breaker Node, unlocked at Breaker Core level 1.", impact = "Edit the rows, keys, result or amount to change the recipe; changes apply after a restart.")
    ConfiguredShapedRecipe breakerNodeRecipe = new ConfiguredShapedRecipe()
        .rows(" X ", " B ", "   ")
        .key('X', "IRON_PICKAXE")
        .key('B', "golemancy:blank_node")
        .result("golemancy:node/breaker", 1);

    public Config() {
      requires = new ArrayList<>(List.of("golem-node-weaving:1"));
    }
  }
}
