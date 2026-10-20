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
import art.arcane.adapt.api.golem.GolemCoreBehaviors;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.node.NodeBehaviors;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.recipe.ConfiguredRecipeParser;
import art.arcane.adapt.api.recipe.ConfiguredShapedRecipe;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class GolemHarvesterCore extends SimpleAdaptation<GolemHarvesterCore.Config> {
  static final String CORE_RECIPE = "golemancy-harvester-core";
  static final String NODE_RECIPE = "golemancy-harvest-node";
  private static final int RECIPE_LEVEL = 1;
  private static final double TICKS_PER_SECOND = 20D;
  private static final long SWEEP_INTERVAL_MILLIS = 5_000L;
  private static final List<PlayerPreference<?>> PREFERENCES =
      List.of(CommonPreferences.ENABLED, GolemancyPreferences.TENDING, GolemancyPreferences.TILLING);

  private final OwnerPreferences owners = new OwnerPreferences(this);
  private final Map<UUID, Long> tends = new ConcurrentHashMap<>();
  private final HarvestSpots spots = new HarvestSpots();
  private final HarvestActions actions = new HarvestActions(this, ThreadLocalRandom::current);
  private final HarvestNodeBehavior harvest = new HarvestNodeBehavior(this, spots, actions);
  private final HarvesterCoreBehavior work = new HarvesterCoreBehavior(this, spots, actions);
  private volatile ParsedCrops parsed;

  public GolemHarvesterCore() {
    super("golem-harvester-core");
    registerConfiguration(Config.class);
    setLocalizationKey("golemancy.harvester_core");
    setIcon(Material.IRON_HOE);
    setInterval(SWEEP_INTERVAL_MILLIS);
    ConfiguredRecipeParser parser = ConfiguredRecipeParser.bukkit(GolemItems.resolver(), GolemItems.recipeBook());
    GolemancySupport.registerRecipe(this, parser, CORE_RECIPE, getConfig().harvesterCoreRecipe, RECIPE_LEVEL);
    GolemancySupport.registerRecipe(this, parser, NODE_RECIPE, getConfig().harvestNodeRecipe, RECIPE_LEVEL);
  }

  @Override
  public void unregister() {
    NodeBehaviors.unregister(NodeType.HARVEST, harvest);
    GolemCoreBehaviors.unregister(GolemCoreType.HARVESTER, work);
    tends.clear();
    spots.forget();
    super.unregister();
  }

  @Override
  protected void onRuntimeActivated() {
    NodeBehaviors.register(NodeType.HARVEST, harvest);
    GolemCoreBehaviors.register(GolemCoreType.HARVESTER, work);
  }

  @Override
  protected void normalizeLoadedConfig(Config loadedConfig) {
    loadedConfig.bountifulChance = GolemancyRules.fraction(loadedConfig.bountifulChance);
    loadedConfig.workXpPerCrop = GolemancyRules.nonNegative(loadedConfig.workXpPerCrop);
    loadedConfig.tendingIntervalMillis = HarvesterRules.tendingInterval(loadedConfig.tendingIntervalMillis);
  }

  @Override
  public void addStats(int level, Element v) {
    Config config = getConfig();
    statLore(v, HarvesterRules.cropsPerAction(config.cropsPerActionByLevel, level), 1);
    statLore(v, C.YELLOW, "* ", HarvesterRules.actionIntervalTicks(config.actionIntervalTicksByLevel, level) / TICKS_PER_SECOND, 2);
    if (GolemancyRules.unlocked(level, config.extendedCropsLevel)) {
      unlockLore(v, GolemancyMessages.HARVESTER_CORE_LORE3);
    }
    if (GolemancyRules.unlocked(level, config.tendingLevel)) {
      unlockLore(v, GolemancyMessages.HARVESTER_CORE_LORE4);
    }
    if (GolemancyRules.unlocked(level, config.tillingLevel)) {
      unlockLore(v, GolemancyMessages.HARVESTER_CORE_LORE5);
    }
    if (GolemancyRules.unlocked(level, config.bountifulLevel)) {
      statLore(v, Form.pc(config.bountifulChance, 0), 6);
    }
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return PREFERENCES;
  }

  @Override
  public boolean isPlayerPreferenceVisible(AdaptPlayer player, PlayerPreference<?> preference) {
    Config config = getConfig();
    if (preference == GolemancyPreferences.TENDING) {
      return GolemancyRules.unlocked(getLevel(player), config.tendingLevel);
    }
    return preference != GolemancyPreferences.TILLING || GolemancyRules.unlocked(getLevel(player), config.tillingLevel);
  }

  @Override
  public boolean hasTickDemand() {
    return !tends.isEmpty() || spots.pending();
  }

  @Override
  public void onTick() {
    sweep(M.ms());
  }

  Config config() {
    return getConfig();
  }

  HarvestCrops crops() {
    List<String> names = getConfig().crops;
    ParsedCrops current = parsed;
    if (current == null || current.names() != names) {
      current = new ParsedCrops(names, HarvestCrops.parse(names));
      parsed = current;
    }
    return current.crops();
  }

  boolean tendReady(UUID golem, long nowMillis) {
    Long tendedAt = tends.get(golem);
    return tendedAt == null || nowMillis - tendedAt >= getConfig().tendingIntervalMillis;
  }

  void tended(UUID golem, long nowMillis) {
    tends.put(golem, nowMillis);
  }

  boolean tends(UUID owner) {
    return owners.enabled(owner, GolemancyPreferences.TENDING);
  }

  boolean tills(UUID owner) {
    return owners.enabled(owner, GolemancyPreferences.TILLING);
  }

  void sweep(long nowMillis) {
    long interval = getConfig().tendingIntervalMillis;
    tends.values().removeIf(tendedAt -> nowMillis - tendedAt >= interval);
    spots.sweep(nowMillis);
  }

  private record ParsedCrops(List<String> names, HarvestCrops crops) {
  }

  @ConfigDescription("Golems bound with a Harvester Core harvest and replant ripe crops in a Harvest Node's area, and tend and till the field as the core grows stronger.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Ticks a Harvester spends on each harvest, tilling, planting or tending action after it reaches the spot, by Harvester Core level from level 1 upward. Swiftness runes and Copper Frame's Static Overdrive shorten it. Values below 1 count as 1.", impact = "Lower values make Harvesters work faster.")
    List<Integer> actionIntervalTicksByLevel = new ArrayList<>(List.of(40, 30, 24, 18, 12));
    @ConfigDoc(value = "Crops a Harvester harvests in one action, by Harvester Core level from level 1 upward: the crop it walked to, plus other ripe crops of the same Harvest Node within 3 blocks that no other golem is walking to. Values below 1 count as 1.", impact = "Higher values let Harvesters clear a field faster.")
    List<Integer> cropsPerActionByLevel = new ArrayList<>(List.of(1, 1, 2, 3, 4));
    @ConfigDoc(value = "Blocks Harvesters work, by block name. TORCHFLOWER_CROP covers grown torchflowers on farmland, CAVE_VINES covers both cave vine blocks, MELON, PUMPKIN, SUGAR_CANE, BAMBOO and CACTUS need extendedCropsLevel, and any other name is harvested at its maximum growth age. Unknown names are ignored, and melon and pumpkin stems are ignored with a warning.", impact = "Remove a name to have Harvesters leave that crop alone, or add an ageable crop block to have them harvest it.")
    List<String> crops = new ArrayList<>(HarvestCrops.DEFAULTS);
    @ConfigDoc(value = "Harvester Core level at which Harvesters also harvest melons, pumpkins, sugar cane, bamboo and cactus.", impact = "Lower values unlock these crops sooner.")
    int extendedCropsLevel = 2;
    @ConfigDoc(value = "Harvester Core level at which Harvesters bone meal young crops with Tending, using bone meal from their linked home chest.", impact = "Lower values unlock Tending sooner.")
    int tendingLevel = 3;
    @ConfigDoc(value = "Milliseconds after a Harvester uses bone meal before it can use another, at least 1000.", impact = "Lower values let Harvesters bone meal crops more often and use more bone meal.")
    long tendingIntervalMillis = 10_000L;
    @ConfigDoc(value = "Harvester Core level at which Harvesters re-till bare dirt into farmland and plant empty farmland with seeds they carry.", impact = "Lower values unlock Tilling sooner.")
    int tillingLevel = 4;
    @ConfigDoc(value = "Harvester Core level at which each harvest has a chance to roll its drops a second time with Bountiful.", impact = "Lower values unlock Bountiful sooner.")
    int bountifulLevel = 5;
    @ConfigDoc(value = "Chance, from 0 to 1, that a Bountiful harvest rolls its drops a second time. 0.1 is 10%.", impact = "Higher values give more extra drops.")
    double bountifulChance = 0.1;
    @ConfigDoc(value = "Golemancy work XP paid for each crop a Harvester harvests. Tilling, planting and tending pay none. Values below 0 count as 0.", impact = "Higher values pay more XP for harvests.")
    double workXpPerCrop = 0.4;
    @ConfigDoc(value = "Crafting recipe for the Harvester Core, unlocked at Harvester Core level 1.", impact = "Edit the rows, keys, result or amount to change the recipe; changes apply after a restart.")
    ConfiguredShapedRecipe harvesterCoreRecipe = new ConfiguredShapedRecipe()
        .rows(" X ", "YBY", " Z ")
        .key('X', "IRON_HOE")
        .key('Y', "WHEAT")
        .key('Z', "COMPOSTER")
        .key('B', "golemancy:blank_core")
        .result("golemancy:core/harvester", 1);
    @ConfigDoc(value = "Crafting recipe for the Harvest Node, unlocked at Harvester Core level 1.", impact = "Edit the rows, keys, result or amount to change the recipe; changes apply after a restart.")
    ConfiguredShapedRecipe harvestNodeRecipe = new ConfiguredShapedRecipe()
        .rows(" X ", " B ", "   ")
        .key('X', "IRON_HOE")
        .key('B', "golemancy:blank_node")
        .result("golemancy:node/harvest", 1);

    public Config() {
      requires = new ArrayList<>(List.of("golem-node-weaving:1"));
    }
  }
}
