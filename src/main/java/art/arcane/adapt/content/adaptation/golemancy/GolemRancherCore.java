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
import art.arcane.adapt.api.golem.gui.NodePanels;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.node.NodeBehaviors;
import art.arcane.adapt.api.recipe.ConfiguredRecipeParser;
import art.arcane.adapt.api.recipe.ConfiguredShapedRecipe;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GolemRancherCore extends SimpleAdaptation<GolemRancherCore.Config> {
  static final String CORE_RECIPE = "golemancy-rancher-core";
  static final String NODE_RECIPE = "golemancy-pasture-node";
  static final long FED_MARK_MILLIS = 40_000L;
  private static final int RECIPE_LEVEL = 1;
  private static final int COOLDOWN_DELAY_TICKS = 1;
  private static final long SWEEP_INTERVAL_MILLIS = 5_000L;

  private final Map<UUID, Fed> fed = new ConcurrentHashMap<>();
  private final RancherActions actions = new RancherActions(this, RancherActions.Hooks.server());
  private final RancherBehavior work = new RancherBehavior(this, actions);
  private final RancherNodePanel panel = new RancherNodePanel(this, ItemStack::new);
  private volatile ParsedSpecies parsed;

  public GolemRancherCore() {
    super("golem-rancher-core");
    registerConfiguration(Config.class);
    setLocalizationKey("golemancy.rancher_core");
    setIcon(Material.SHEARS);
    setInterval(SWEEP_INTERVAL_MILLIS);
    ConfiguredRecipeParser parser = ConfiguredRecipeParser.bukkit(GolemItems.resolver(), GolemItems.recipeBook());
    registerGolemRecipe(parser, CORE_RECIPE, getConfig().rancherCoreRecipe);
    registerGolemRecipe(parser, NODE_RECIPE, getConfig().pastureNodeRecipe);
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
    loadedConfig.workXpPerPair = RancherRules.nonNegative(loadedConfig.workXpPerPair);
    loadedConfig.workXpPerShear = RancherRules.nonNegative(loadedConfig.workXpPerShear);
    loadedConfig.workXpPerMilk = RancherRules.nonNegative(loadedConfig.workXpPerMilk);
    loadedConfig.workXpPerCull = RancherRules.nonNegative(loadedConfig.workXpPerCull);
  }

  @Override
  public void addStats(int level, Element v) {
    Config config = getConfig();
    unlockLore(v, GolemancyMessages.RANCHER_CORE_LORE1);
    if (RancherRules.unlocked(level, config.shearLevel)) {
      unlockLore(v, GolemancyMessages.RANCHER_CORE_LORE2);
    }
    if (RancherRules.unlocked(level, config.milkLevel)) {
      unlockLore(v, GolemancyMessages.RANCHER_CORE_LORE3);
    }
    if (RancherRules.unlocked(level, config.cullLevel)) {
      unlockLore(v, GolemancyMessages.RANCHER_CORE_LORE4);
    }
    if (RancherRules.unlocked(level, config.husbandryLevel)) {
      statLore(v, Form.pc(1D - config.husbandryCooldownMultiplier, 0), 5);
      statLore(v, config.husbandryExtraLootRolls, 6);
    }
  }

  @Override
  protected List<Listener> createCompanionListeners() {
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

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void on(EntityBreedEvent e) {
    long now = M.ms();
    if (fresh(e.getMother(), now) || fresh(e.getFather(), now)) {
      e.setExperience(0);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void afterBreeding(EntityBreedEvent e) {
    long now = M.ms();
    shortenCooldown(e.getMother(), now);
    shortenCooldown(e.getFather(), now);
  }

  Config config() {
    return getConfig();
  }

  List<EntityType> species() {
    List<String> names = getConfig().species;
    ParsedSpecies current = parsed;
    if (current == null || current.names() != names) {
      current = new ParsedSpecies(names, RancherRules.parseSpecies(names));
      parsed = current;
    }
    return current.species();
  }

  int ownerLevel(UUID owner) {
    AdaptPlayer player = getServer().getOnlineAdaptPlayer(owner);
    return player == null ? 0 : getLevel(player);
  }

  void markFed(UUID animal, long nowMillis, boolean husbandry) {
    fed.put(animal, new Fed(nowMillis, husbandry));
  }

  void sweep(long nowMillis) {
    fed.values().removeIf(mark -> !mark.freshAt(nowMillis));
  }

  private boolean fresh(LivingEntity parent, long nowMillis) {
    if (parent == null) {
      return false;
    }
    Fed mark = fed.get(parent.getUniqueId());
    return mark != null && mark.freshAt(nowMillis);
  }

  private void shortenCooldown(LivingEntity parent, long nowMillis) {
    if (parent == null) {
      return;
    }
    Fed mark = fed.remove(parent.getUniqueId());
    if (mark == null || !mark.husbandry() || !mark.freshAt(nowMillis) || !(parent instanceof Ageable ageable)) {
      return;
    }
    double multiplier = getConfig().husbandryCooldownMultiplier;
    J.runEntity(parent, () -> ageable.setAge(RancherRules.cooldown(ageable.getAge(), multiplier)), COOLDOWN_DELAY_TICKS);
  }

  private void registerGolemRecipe(ConfiguredRecipeParser parser, String name, ConfiguredShapedRecipe spec) {
    NamespacedKey key = new NamespacedKey(Adapt.instance, name);
    parser.builder(key, spec).ifPresent(builder -> registerRecipe(builder.requiredLevel(RECIPE_LEVEL).build()));
    GolemItems.recipeTithe().set(key, spec.isEnabled() ? spec.getTithe() : 0);
  }

  private static void unlockLore(Element v, TextKey key) {
    v.addLore(C.GREEN + "+ " + C.GRAY + AdaptLanguage.text(key));
  }

  private record ParsedSpecies(List<String> names, List<EntityType> species) {
  }

  private record Fed(long at, boolean husbandry) {
    boolean freshAt(long nowMillis) {
      return nowMillis - at < FED_MARK_MILLIS;
    }
  }

  @ConfigDescription("Golems bound with a Rancher Core tend the animals in a Pasture Node's area: they breed pairs up to the node's keep count, then shear sheep, milk cows and goats, and cull spare adults as the core grows stronger.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Animals Pasture Nodes tend, by entity type name. Each Pasture Node tends all of them or the one species chosen in its Node GUI. Names that are not animals are ignored.", impact = "Remove a name to have Ranchers leave that animal alone, or add a breedable animal to have them tend it.")
    List<String> species = new ArrayList<>(RancherRules.DEFAULT_SPECIES);
    @ConfigDoc(value = "Keep count of a Pasture Node whose keep count was never changed in its Node GUI: Ranchers breed a species while its adults in the area number fewer than this and cull adults above it. Values load between 2 and 64.", impact = "Higher values keep bigger herds.")
    int defaultKeep = 8;
    @ConfigDoc(value = "Ticks a Rancher spends on each breeding, shearing, milking or culling action after it reaches the animal. Copper Overdrive and Swiftness runes shorten it. Values below 1 load as 1.", impact = "Lower values make Ranchers work faster.")
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
    @ConfigDoc(value = "Crafting recipe for the Rancher Core, unlocked at Rancher Core level 1.", impact = "Edit the rows, keys, result, amount or tithe to change the recipe; changes apply after a restart.")
    ConfiguredShapedRecipe rancherCoreRecipe = new ConfiguredShapedRecipe()
        .rows(" X ", "YBY", " Z ")
        .key('X', "SHEARS")
        .key('Y', "WHEAT")
        .key('Z', "LEAD")
        .key('B', "golemancy:blank_core")
        .result("golemancy:core/rancher", 1)
        .tithe(2);
    @ConfigDoc(value = "Crafting recipe for the Pasture Node, unlocked at Rancher Core level 1.", impact = "Edit the rows, keys, result, amount or tithe to change the recipe; changes apply after a restart.")
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
