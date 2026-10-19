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
import art.arcane.adapt.api.golem.GolemCapacity;
import art.arcane.adapt.api.golem.GolemCoreBehaviors;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemMastery;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.node.NodeBehaviors;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.recipe.ConfiguredRecipeParser;
import art.arcane.adapt.api.recipe.ConfiguredShapedRecipe;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GolemGathererCore extends SimpleAdaptation<GolemGathererCore.Config> {
  static final String CORE_RECIPE = "golemancy-gatherer-core";
  static final String NODE_RECIPE = "golemancy-collection-node";
  static final String CARRY_SOURCE = "gatherer_core";
  private static final int RECIPE_LEVEL = 1;
  private static final long SWEEP_INTERVAL_MILLIS = 5_000L;
  private static final long MILLIS_PER_SECOND = 1_000L;
  private static final List<PlayerPreference<?>> PREFERENCES =
      List.of(CommonPreferences.ENABLED, GolemancyPreferences.MAGNET, GolemancyPreferences.ABSORB_ORBS);

  private final OwnerPreferences owners = new OwnerPreferences(this);
  private final GatherDeathSites deathSites = new GatherDeathSites();
  private final GathererWork work = new GathererWork(this, deathSites);
  private final CollectionNodeBehavior collection = new CollectionNodeBehavior(this, work);
  private final GolemCapacity.Source carry = new GathererCarry();

  public GolemGathererCore() {
    super("golem-gatherer-core");
    registerConfiguration(Config.class);
    setLocalizationKey("golemancy.gatherer_core");
    setIcon(Material.HOPPER);
    setInterval(SWEEP_INTERVAL_MILLIS);
    ConfiguredRecipeParser parser = ConfiguredRecipeParser.bukkit(GolemItems.resolver(), GolemItems.recipeBook());
    GolemancySupport.registerRecipe(this, parser, CORE_RECIPE, getConfig().gathererCoreRecipe, RECIPE_LEVEL);
    GolemancySupport.registerRecipe(this, parser, NODE_RECIPE, getConfig().collectionNodeRecipe, RECIPE_LEVEL);
  }

  @Override
  public void unregister() {
    GolemCoreBehaviors.unregister(GolemCoreType.GATHERER, work);
    NodeBehaviors.unregister(NodeType.COLLECTION, collection);
    GolemCapacity.unregister(carry);
    deathSites.clear();
    work.forget();
    super.unregister();
  }

  @Override
  protected void onRuntimeActivated() {
    GolemCoreBehaviors.register(GolemCoreType.GATHERER, work);
    NodeBehaviors.register(NodeType.COLLECTION, collection);
    GolemCapacity.register(carry);
  }

  @Override
  protected void normalizeLoadedConfig(Config loadedConfig) {
    loadedConfig.magnetRadius = GathererRules.radius(loadedConfig.magnetRadius, GathererRules.MAX_MAGNET_RADIUS);
    loadedConfig.instantPickupRadius = GathererRules.radius(loadedConfig.instantPickupRadius, GathererRules.MAX_INSTANT_PICKUP_RADIUS);
    loadedConfig.deathSiteRadius = GathererRules.radius(loadedConfig.deathSiteRadius, GathererRules.MAX_DEATH_SITE_RADIUS);
    loadedConfig.deathSiteSeconds = GathererRules.seconds(loadedConfig.deathSiteSeconds, GathererRules.MAX_DEATH_SITE_SECONDS);
    loadedConfig.foreignItemSeconds = GathererRules.seconds(loadedConfig.foreignItemSeconds, GathererRules.MAX_FOREIGN_ITEM_SECONDS);
    loadedConfig.carryBonusFraction = GolemancyRules.nonNegative(loadedConfig.carryBonusFraction);
    loadedConfig.workXpPerStack = GolemancyRules.nonNegative(loadedConfig.workXpPerStack);
  }

  @Override
  public void addStats(int level, Element v) {
    Config config = getConfig();
    statLore(v, GathererRules.blocks(GathererRules.pickupRadius(config.pickupRadiusByLevel, level)), 1);
    int cap = GathererRules.essenceCap(config.essenceCapByLevel, level);
    if (cap > 0) {
      statLore(v, cap, 2);
    }
    if (GathererRules.unlocked(level, config.magnetLevel) && config.magnetRadius > 0D) {
      statLore(v, GathererRules.blocks(config.magnetRadius), 3);
    }
    if (GathererRules.unlocked(level, config.instantPickupLevel) && config.instantPickupRadius > 0D) {
      statLore(v, GathererRules.blocks(config.instantPickupRadius), 4);
    }
    if (GathererRules.unlocked(level, config.carryBonusLevel) && config.carryBonusFraction > 0D) {
      statLore(v, Form.pc(config.carryBonusFraction, 0), 5);
    }
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return PREFERENCES;
  }

  @Override
  public boolean isPlayerPreferenceVisible(AdaptPlayer player, PlayerPreference<?> preference) {
    Config config = getConfig();
    if (preference == GolemancyPreferences.MAGNET) {
      return GathererRules.unlocked(getLevel(player), config.magnetLevel) && config.magnetRadius > 0D;
    }
    return preference != GolemancyPreferences.ABSORB_ORBS || GathererRules.essenceCap(config.essenceCapByLevel, getLevel(player)) > 0;
  }

  @Override
  public boolean hasTickDemand() {
    return !deathSites.isEmpty() || work.pending();
  }

  @Override
  public void onTick() {
    long now = M.ms();
    deathSites.sweep(now);
    work.sweep(now);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerDeathEvent e) {
    Location at = e.getEntity().getLocation();
    World world = at.getWorld();
    if (world != null) {
      deathSites.record(world.getUID(), at.getX(), at.getY(), at.getZ(), M.ms(), getConfig().deathSiteSeconds * MILLIS_PER_SECOND);
    }
  }

  Config config() {
    return getConfig();
  }

  boolean magnets(UUID owner) {
    return owners.enabled(owner, GolemancyPreferences.MAGNET);
  }

  boolean absorbsOrbs(UUID owner) {
    return owners.enabled(owner, GolemancyPreferences.ABSORB_ORBS);
  }

  private final class GathererCarry implements GolemCapacity.Source {
    @Override
    public String id() {
      return CARRY_SOURCE;
    }

    @Override
    public GolemCapacity.Contribution carry(GolemHandle handle) {
      List<GolemCoreType> cores = GolemMastery.cores(handle);
      if (!cores.contains(GolemCoreType.GATHERER)) {
        return GolemCapacity.Contribution.NONE;
      }
      Config config = getConfig();
      return GathererRules.carryBonus(cores, ownerLevel(handle.owner()), config.carryBonusLevel, config.carryBonusFraction);
    }
  }

  @ConfigDescription("Golems bound with a Gatherer Core pick up dropped items around their home or a Collection Node and deliver them, and store experience orbs as essence as the core grows stronger.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Blocks around a Gatherer's home within which it picks up dropped items when none of your Collection Nodes serve it, by Gatherer Core level from level 1 upward. A Tide rune adds its bonus. Values above 64 count as 64.", impact = "Higher values let Gatherers collect items farther from home.")
    List<Integer> pickupRadiusByLevel = new ArrayList<>(List.of(4, 6, 8, 10, 12));
    @ConfigDoc(value = "Experience points a Gatherer can store as essence, by Gatherer Core level from level 1 upward. 0 means it absorbs no experience orbs.", impact = "Higher values let Gatherers store more experience for you to withdraw.")
    List<Integer> essenceCapByLevel = new ArrayList<>(List.of(0, 500, 1500, 1500, 1500));
    @ConfigDoc(value = "Gatherer Core level at which Gatherers pull nearby items toward themselves with Magnet.", impact = "Lower values unlock Magnet sooner.")
    int magnetLevel = 4;
    @ConfigDoc(value = "Blocks around a Gatherer within which Magnet pulls items toward it, from 0 to 16.", impact = "Higher values pull items from farther away.")
    double magnetRadius = 3.0;
    @ConfigDoc(value = "Gatherer Core level at which Gatherers pick up items next to them at once.", impact = "Lower values unlock instant pickup sooner.")
    int instantPickupLevel = 5;
    @ConfigDoc(value = "Blocks around a Gatherer within which it picks up items and absorbs experience orbs at once, from 0 to 4.", impact = "Higher values let Gatherers grab items without walking to them from farther away.")
    double instantPickupRadius = 1.5;
    @ConfigDoc(value = "Gatherer Core level at which Gatherers carry more items.", impact = "Lower values unlock the carry bonus sooner.")
    int carryBonusLevel = 5;
    @ConfigDoc(value = "Fraction added to a Gatherer's carry capacity from carryBonusLevel. 0.5 is 50% more.", impact = "Higher values let Gatherers carry more items per trip.")
    double carryBonusFraction = 0.5;
    @ConfigDoc(value = "Seconds after an item drops during which Gatherers leave it alone when another player threw it, from 0 to 600. Items another player owns are never picked up.", impact = "Higher values give other players longer to pick up the items they threw.")
    int foreignItemSeconds = 30;
    @ConfigDoc(value = "Blocks around the spot where a player died within which Gatherers pick up nothing, from 0 to 64.", impact = "Higher values keep Gatherers farther from death drops.")
    double deathSiteRadius = 8.0;
    @ConfigDoc(value = "Seconds after a player dies during which Gatherers leave items and experience orbs near that spot alone, from 0 to 3600. 0 turns the protection off.", impact = "Higher values protect death drops longer.")
    int deathSiteSeconds = 300;
    @ConfigDoc(value = "Golemancy work XP a Gatherer earns you for each item stack it picks up.", impact = "Higher values make gathering level Golemancy faster.")
    double workXpPerStack = 0.5;
    @ConfigDoc(value = "Crafting recipe for the Gatherer Core, unlocked at Gatherer Core level 1.", impact = "Edit the rows, keys, result, amount or tithe to change the recipe; changes apply after a restart.")
    ConfiguredShapedRecipe gathererCoreRecipe = new ConfiguredShapedRecipe()
        .rows(" X ", "YBY", " Z ")
        .key('X', "BUNDLE")
        .key('Y', "STRING")
        .key('Z', "HOPPER")
        .key('B', "golemancy:blank_core")
        .result("golemancy:core/gatherer", 1)
        .tithe(2);
    @ConfigDoc(value = "Crafting recipe for the Collection Node, unlocked at Gatherer Core level 1.", impact = "Edit the rows, keys, result, amount or tithe to change the recipe; changes apply after a restart.")
    ConfiguredShapedRecipe collectionNodeRecipe = new ConfiguredShapedRecipe()
        .rows(" X ", " B ", "   ")
        .key('X', "BUNDLE")
        .key('B', "golemancy:blank_node")
        .result("golemancy:node/collection", 1);

    public Config() {
      requires = new ArrayList<>(List.of("golem-node-weaving:1"));
    }
  }
}
