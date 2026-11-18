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

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.golem.GolemCoreBehaviors;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.gui.NodePanels;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.node.NodeBehaviors;
import art.arcane.adapt.api.recipe.ConfiguredRecipeParser;
import art.arcane.adapt.api.recipe.ConfiguredShapedRecipe;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.AnimalOps;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityBreedEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.M;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GolemRancherCore extends SimpleAdaptation<GolemRancherCore.Config> {
  static final String CORE_RECIPE = "golemancy-rancher-core";
  static final String NODE_RECIPE = "golemancy-pasture-node";
  static final long FED_MARK_MILLIS = 40_000L;
  static final DataKey<Integer> FED_TICK_KEY = DataKey.of(Id.of("adapt", "golem_fed_tick"), DataCodecs.INT);
  private static final int FED_TICK_GRACE = 20;
  private static final int RECIPE_LEVEL = 1;
  private static final int COOLDOWN_DELAY_TICKS = 1;
  private static final long SWEEP_INTERVAL_MILLIS = 5_000L;

  private final Map<UUID, Fed> fed = new ConcurrentHashMap<>();
  private final RancherActions actions = new RancherActions(this, RancherActions.Hooks.server());
  private final RancherBehavior work = new RancherBehavior(this, actions);
  private final RancherNodePanel panel = new RancherNodePanel(this, type -> AdaptCore.platform().host().items().create(type));
  private volatile ParsedSpecies parsed;

  public GolemRancherCore() {
    super("golem-rancher-core");
    registerConfiguration(Config.class);
    setLocalizationKey("golemancy.rancher_core");
    setIcon(Id.minecraft("shears"));
    setInterval(SWEEP_INTERVAL_MILLIS);
    ConfiguredRecipeParser parser = ConfiguredRecipeParser.platform(GolemItems.resolver(), GolemItems.recipeBook());
    GolemancySupport.registerRecipe(this, parser, CORE_RECIPE, getConfig().rancherCoreRecipe, RECIPE_LEVEL);
    GolemancySupport.registerRecipe(this, parser, NODE_RECIPE, getConfig().pastureNodeRecipe, RECIPE_LEVEL);
  }

  @Override
  public void unregister() {
    NodeBehaviors.unregister(NodeType.PASTURE, work);
    GolemCoreBehaviors.unregister(GolemCoreType.RANCHER, work);
    NodePanels.unregister(NodeType.PASTURE, panel);
    fed.clear();
    super.unregister();
  }

  @Override
  protected void onRuntimeActivated() {
    NodeBehaviors.register(NodeType.PASTURE, work);
    GolemCoreBehaviors.register(GolemCoreType.RANCHER, work);
    NodePanels.register(NodeType.PASTURE, panel);
  }

  @Override
  protected void normalizeLoadedConfig(Config loadedConfig) {
    loadedConfig.defaultKeep = RancherRules.clampKeep(loadedConfig.defaultKeep);
    loadedConfig.actionIntervalTicks = Math.max(1, loadedConfig.actionIntervalTicks);
    loadedConfig.husbandryCooldownMultiplier = RancherRules.fraction(loadedConfig.husbandryCooldownMultiplier);
    loadedConfig.husbandryExtraLootRolls = Math.clamp(loadedConfig.husbandryExtraLootRolls, 0, RancherActions.MAX_EXTRA_LOOT_ROLLS);
    loadedConfig.workXpPerPair = GolemancyRules.nonNegative(loadedConfig.workXpPerPair);
    loadedConfig.workXpPerShear = GolemancyRules.nonNegative(loadedConfig.workXpPerShear);
    loadedConfig.workXpPerMilk = GolemancyRules.nonNegative(loadedConfig.workXpPerMilk);
    loadedConfig.workXpPerCull = GolemancyRules.nonNegative(loadedConfig.workXpPerCull);
  }

  @Override
  public void addStats(int level, StatLines v) {
    Config config = getConfig();
    unlockLore(v, GolemancyMessages.RANCHER_CORE_LORE1);
    if (GolemancyRules.unlocked(level, config.shearLevel)) {
      unlockLore(v, GolemancyMessages.RANCHER_CORE_LORE2);
    }
    if (GolemancyRules.unlocked(level, config.milkLevel)) {
      unlockLore(v, GolemancyMessages.RANCHER_CORE_LORE3);
    }
    if (GolemancyRules.unlocked(level, config.cullLevel)) {
      unlockLore(v, GolemancyMessages.RANCHER_CORE_LORE4);
    }
    if (GolemancyRules.unlocked(level, config.husbandryLevel)) {
      statLore(v, Form.pc(1D - config.husbandryCooldownMultiplier, 0), 5);
      statLore(v, config.husbandryExtraLootRolls, 6);
    }
  }

  @Override
  protected List<?> createCompanionListeners() {
    return List.of(actions);
  }

  @Override
  public boolean hasTickDemand() {
    return !fed.isEmpty();
  }

  @Override
  public void onTick() {
    sweep(M.ms());
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(EntityBreedEvent e) {
    long now = M.ms();
    if (golemFed(e.mother(), now) || golemFed(e.father(), now)) {
      e.setExperience(0);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void afterBreeding(EntityBreedEvent e) {
    long now = M.ms();
    shortenCooldown(e.mother(), now);
    shortenCooldown(e.father(), now);
    clearFedTick(e.mother());
    clearFedTick(e.father());
  }

  static void markFedTick(LivingRef animal) {
    animal.data().set(FED_TICK_KEY, animal.ticksLived());
  }

  Config config() {
    return getConfig();
  }

  List<Id> species() {
    List<String> names = getConfig().species;
    ParsedSpecies current = parsed;
    if (current == null || current.names() != names) {
      current = new ParsedSpecies(names, RancherRules.parseSpecies(names));
      parsed = current;
    }
    return current.species();
  }

  void markFed(UUID animal, long nowMillis, boolean husbandry) {
    fed.put(animal, new Fed(nowMillis, husbandry));
  }

  void sweep(long nowMillis) {
    fed.values().removeIf(mark -> !mark.freshAt(nowMillis));
  }

  private boolean golemFed(LivingRef parent, long nowMillis) {
    return fresh(parent, nowMillis) || fedThisLove(parent);
  }

  private boolean fresh(LivingRef parent, long nowMillis) {
    if (parent == null) {
      return false;
    }
    Fed mark = fed.get(parent.uuid());
    return mark != null && mark.freshAt(nowMillis);
  }

  private static boolean fedThisLove(LivingRef parent) {
    if (parent == null) {
      return false;
    }
    Integer fedTick = parent.data().get(FED_TICK_KEY);
    return fedTick != null && parent.ticksLived() - fedTick <= RancherRules.LOVE_TICKS + FED_TICK_GRACE;
  }

  private static void clearFedTick(LivingRef parent) {
    if (parent != null) {
      parent.data().remove(FED_TICK_KEY);
    }
  }

  private void shortenCooldown(LivingRef parent, long nowMillis) {
    if (parent == null) {
      return;
    }
    Fed mark = fed.remove(parent.uuid());
    if (mark == null || !mark.husbandry() || !mark.freshAt(nowMillis)) {
      return;
    }
    AnimalOps animals = AdaptCore.platform().host().animals();
    double multiplier = getConfig().husbandryCooldownMultiplier;
    Tasks.runEntity(parent, () -> animals.setAge(parent, RancherRules.cooldown(animals.age(parent), multiplier)), COOLDOWN_DELAY_TICKS);
  }

  private record ParsedSpecies(List<String> names, List<Id> species) {
  }

  private record Fed(long at, boolean husbandry) {
    boolean freshAt(long nowMillis) {
      return nowMillis - at < FED_MARK_MILLIS;
    }
  }

  @ConfigDescription("Golems bound with a Rancher Core tend the animals in a Pasture Node's area: they breed pairs up to the node's keep count, then shear sheep, milk cows and goats, and cull spare adults as the core grows stronger.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Animals Pasture Nodes tend, by entity type name. Each Pasture Node tends all of them or the one species chosen in its Node GUI. Names that are not animals, and hostile animals such as hoglins, are ignored.", impact = "Remove a name to have Ranchers leave that animal alone, or add a breedable animal to have them tend it.")
    List<String> species = new ArrayList<>(RancherRules.DEFAULT_SPECIES);
    @ConfigDoc(value = "Keep count of a Pasture Node whose keep count was never changed in its Node GUI. Ranchers breed a species while its adults and babies in the area, plus one for each pair in love, number fewer than this; a lone animal in love counts as a pair. They cull adults above it, counting adults only. Values load between 2 and 64.", impact = "Higher values keep bigger herds.")
    int defaultKeep = 8;
    @ConfigDoc(value = "Ticks a Rancher spends on each breeding, shearing, milking or culling action after it reaches the animal. Swiftness runes and Copper Frame's Static Overdrive shorten it. Values below 1 load as 1.", impact = "Lower values make Ranchers work faster.")
    int actionIntervalTicks = 40;
    @ConfigDoc(value = "Rancher Core level at which Ranchers shear sheep.", impact = "Lower values unlock shearing sooner.")
    int shearLevel = 2;
    @ConfigDoc(value = "Rancher Core level at which Ranchers milk cows, goats and mooshrooms with buckets or bowls from their linked home chest.", impact = "Lower values unlock milking sooner.")
    int milkLevel = 3;
    @ConfigDoc(value = "Rancher Core level at which iron Ranchers cull adults above a Pasture Node's keep count.", impact = "Lower values unlock culling sooner.")
    int cullLevel = 4;
    @ConfigDoc(value = "Rancher Core level at which Husbandry shortens the breeding cooldown of animals a Rancher fed and adds loot rolls to each cull.", impact = "Lower values unlock Husbandry sooner.")
    int husbandryLevel = 5;
    @ConfigDoc(value = "Multiplier, from 0 to 1, on the breeding cooldown of parents a Rancher fed once Husbandry is unlocked. 0.5 halves it. Values outside that range load as the nearest limit.", impact = "Lower values let animals breed again sooner.")
    double husbandryCooldownMultiplier = 0.5;
    @ConfigDoc(value = "Extra times a culled animal's loot is rolled once Husbandry is unlocked, from 0 to 8. Values outside that range load as the nearest limit.", impact = "Higher values give more drops from each cull.")
    int husbandryExtraLootRolls = 1;
    @ConfigDoc(value = "Golemancy work XP paid for each pair of animals a Rancher feeds to breed, half for each animal fed. Values below 0 load as 0.", impact = "Higher values pay more XP for breeding.")
    double workXpPerPair = 4.0;
    @ConfigDoc(value = "Golemancy work XP paid for each sheep a Rancher shears. Values below 0 load as 0.", impact = "Higher values pay more XP for shearing.")
    double workXpPerShear = 2.0;
    @ConfigDoc(value = "Golemancy work XP paid for each bucket or bowl a Rancher fills. Values below 0 load as 0.", impact = "Higher values pay more XP for milking.")
    double workXpPerMilk = 1.0;
    @ConfigDoc(value = "Golemancy work XP paid for each animal a Rancher culls. Values below 0 load as 0.", impact = "Higher values pay more XP for culling.")
    double workXpPerCull = 1.0;
    @ConfigDoc(value = "Crafting recipe for the Rancher Core, unlocked at Rancher Core level 1.", impact = "Edit the rows, keys, result or amount to change the recipe; changes apply after a restart.")
    ConfiguredShapedRecipe rancherCoreRecipe = new ConfiguredShapedRecipe()
        .rows(" X ", "YBY", " Z ")
        .key('X', "SHEARS")
        .key('Y', "WHEAT")
        .key('Z', "LEAD")
        .key('B', "golemancy:blank_core")
        .result("golemancy:core/rancher", 1);
    @ConfigDoc(value = "Crafting recipe for the Pasture Node, unlocked at Rancher Core level 1.", impact = "Edit the rows, keys, result or amount to change the recipe; changes apply after a restart.")
    ConfiguredShapedRecipe pastureNodeRecipe = new ConfiguredShapedRecipe()
        .rows(" X ", " B ", "   ")
        .key('X', "LEAD")
        .key('B', "golemancy:blank_node")
        .result("golemancy:node/pasture", 1);

    public Config() {
      requires = new ArrayList<>(List.of("golem-node-weaving:1"));
    }
  }
}
