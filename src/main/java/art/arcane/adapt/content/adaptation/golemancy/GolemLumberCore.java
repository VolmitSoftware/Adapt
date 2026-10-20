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
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.node.NodeBehaviors;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.recipe.ConfiguredRecipeParser;
import art.arcane.adapt.api.recipe.ConfiguredShapedRecipe;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GolemLumberCore extends SimpleAdaptation<GolemLumberCore.Config> {
  static final String CORE_RECIPE = "golemancy-lumber-core";
  static final String NODE_RECIPE = "golemancy-lumber-node";
  private static final int RECIPE_LEVEL = 1;
  private static final long SWEEP_INTERVAL_MILLIS = 5_000L;
  private static final List<PlayerPreference<?>> PREFERENCES = List.of(CommonPreferences.ENABLED, GolemancyPreferences.LEAF_SWEEP);

  private final OwnerPreferences owners = new OwnerPreferences(this);
  private final LumberFellings fellings = new LumberFellings();
  private final LumberRejections rejections = new LumberRejections();
  private final LumberNodeBehavior lumber = new LumberNodeBehavior(this, fellings, rejections,
      new LumberActions(this, XpProvenance::wasPlaced));

  public GolemLumberCore() {
    super("golem-lumber-core");
    registerConfiguration(Config.class);
    setLocalizationKey("golemancy.lumber_core");
    setIcon(Material.IRON_AXE);
    setInterval(SWEEP_INTERVAL_MILLIS);
    ConfiguredRecipeParser parser = ConfiguredRecipeParser.bukkit(GolemItems.resolver(), GolemItems.recipeBook());
    GolemancySupport.registerRecipe(this, parser, CORE_RECIPE, getConfig().lumberCoreRecipe, RECIPE_LEVEL);
    GolemancySupport.registerRecipe(this, parser, NODE_RECIPE, getConfig().lumberNodeRecipe, RECIPE_LEVEL);
  }

  @Override
  public void unregister() {
    NodeBehaviors.unregister(NodeType.LUMBER, lumber);
    fellings.forget();
    rejections.forget();
    super.unregister();
  }

  @Override
  protected void onRuntimeActivated() {
    NodeBehaviors.register(NodeType.LUMBER, lumber);
  }

  @Override
  protected void normalizeLoadedConfig(Config loadedConfig) {
    loadedConfig.workXpPerLog = GolemancyRules.nonNegative(loadedConfig.workXpPerLog);
  }

  @Override
  public void addStats(int level, Element v) {
    Config config = getConfig();
    statLore(v, LumberRules.logsPerSecond(config.logsPerSecondByLevel, level), 1);
    statLore(v, LumberRules.treeCap(config.copperTreeCapByLevel, level), 2);
    statLore(v, LumberRules.treeCap(config.ironTreeCapByLevel, level), 3);
    if (GolemancyRules.unlocked(level, config.replantLevel)) {
      unlockLore(v, GolemancyMessages.LUMBER_CORE_LORE4);
    }
    if (GolemancyRules.unlocked(level, config.leafSweepLevel)) {
      unlockLore(v, GolemancyMessages.LUMBER_CORE_LORE5);
    }
    if (GolemancyRules.unlocked(level, config.megaTreeLevel)) {
      unlockLore(v, GolemancyMessages.LUMBER_CORE_LORE6);
    }
    if (GolemancyRules.unlocked(level, config.netherLevel)) {
      unlockLore(v, GolemancyMessages.LUMBER_CORE_LORE7);
    }
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return PREFERENCES;
  }

  @Override
  public boolean isPlayerPreferenceVisible(AdaptPlayer player, PlayerPreference<?> preference) {
    return preference != GolemancyPreferences.LEAF_SWEEP || GolemancyRules.unlocked(getLevel(player), getConfig().leafSweepLevel);
  }

  @Override
  public boolean hasTickDemand() {
    return fellings.pending() || rejections.pending();
  }

  @Override
  public void onTick() {
    sweep(M.ms());
  }

  Config config() {
    return getConfig();
  }

  boolean sweepsLeaves(UUID owner) {
    return owners.enabled(owner, GolemancyPreferences.LEAF_SWEEP);
  }

  void sweep(long nowMillis) {
    fellings.sweep(nowMillis);
    rejections.sweep(nowMillis);
  }

  @ConfigDescription("Golems bound with a Lumber Core fell natural trees in a Lumber Node's area from the top down and collect the logs, then replant, sweep leaves and fell bigger trees as the core grows stronger.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Logs a Lumber golem fells per second once it reaches a tree, by Lumber Core level from level 1 upward. Swiftness runes and Copper Frame's Static Overdrive raise it. Values load between 0.05 and 64.", impact = "Higher values fell trees faster.")
    List<Double> logsPerSecondByLevel = new ArrayList<>(List.of(1.0, 1.5, 2.0, 3.0, 4.0));
    @ConfigDoc(value = "Most logs a tree can have for a copper golem, or any body other than iron, to fell it, by Lumber Core level from level 1 upward. Values below 1 count as 1.", impact = "Higher values let copper golems fell bigger trees.")
    List<Integer> copperTreeCapByLevel = new ArrayList<>(List.of(24, 32, 48, 64, 96));
    @ConfigDoc(value = "Most logs a tree can have for an iron golem to fell it, by Lumber Core level from level 1 upward. Values below 1 count as 1.", impact = "Higher values let iron golems fell bigger trees.")
    List<Integer> ironTreeCapByLevel = new ArrayList<>(List.of(64, 96, 128, 192, 256));
    @ConfigDoc(value = "Lumber Core level that unlocks replanting from carried saplings, the home chest, or eligible matching drops around the felled tree. Empty stumps receive another attempt every five seconds for up to five minutes.", impact = "Lower values unlock replanting sooner.")
    int replantLevel = 2;
    @ConfigDoc(value = "Lumber Core level at which Lumber golems break the leaves a felled tree leaves behind once they start to decay, and collect their saplings, sticks and apples.", impact = "Lower values unlock Leaf Sweep sooner.")
    int leafSweepLevel = 3;
    @ConfigDoc(value = "Lumber Core level at which Lumber golems fell mega trees: trees with a trunk wider than one block, such as a 2x2 spruce, jungle, dark oak or pale oak, and trees whose trunks are joined by roots or branches.", impact = "Lower values unlock mega trees sooner.")
    int megaTreeLevel = 4;
    @ConfigDoc(value = "Lumber Core level at which Lumber golems fell huge crimson and warped fungi with their wart blocks and shroomlights, and replant fungus on nylium.", impact = "Lower values unlock nether fungi sooner.")
    int netherLevel = 5;
    @ConfigDoc(value = "Golemancy work XP paid for each log or stem a Lumber golem fells. Leaves, wart blocks, shroomlights and replanting pay none. Values below 0 count as 0.", impact = "Higher values pay more XP for felling.")
    double workXpPerLog = 0.6;
    @ConfigDoc(value = "Crafting recipe for the Lumber Core, unlocked at Lumber Core level 1.", impact = "Edit the rows, keys, result or amount to change the recipe; changes apply after a restart.")
    ConfiguredShapedRecipe lumberCoreRecipe = new ConfiguredShapedRecipe()
        .rows(" X ", "YBY", " Z ")
        .key('X', "IRON_AXE")
        .key('Y', "#logs")
        .key('Z', "#saplings")
        .key('B', "golemancy:blank_core")
        .result("golemancy:core/lumber", 1);
    @ConfigDoc(value = "Crafting recipe for the Lumber Node, unlocked at Lumber Core level 1.", impact = "Edit the rows, keys, result or amount to change the recipe; changes apply after a restart.")
    ConfiguredShapedRecipe lumberNodeRecipe = new ConfiguredShapedRecipe()
        .rows(" X ", " B ", "   ")
        .key('X', "IRON_AXE")
        .key('B', "golemancy:blank_node")
        .result("golemancy:node/lumber", 1);

    public Config() {
      requires = new ArrayList<>(List.of("golem-node-weaving:1"));
    }
  }
}
