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
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.gui.NodePanels;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.node.NodeBehaviors;
import art.arcane.adapt.api.recipe.ConfiguredRecipeParser;
import art.arcane.adapt.api.recipe.ConfiguredShapedRecipe;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class GolemAnglerCore extends SimpleAdaptation<GolemAnglerCore.Config> {
  static final String CORE_RECIPE = "golemancy-angler-core";
  static final String NODE_RECIPE = "golemancy-fishing-node";
  private static final int RECIPE_LEVEL = 1;
  private static final long SWEEP_INTERVAL_MILLIS = 5_000L;

  private final AnglerWaters waters = new AnglerWaters();
  private final FishingNodeBehavior fishing = new FishingNodeBehavior(this, waters, () -> ThreadLocalRandom.current().nextDouble());
  private final FishingNodePanel panel = new FishingNodePanel(this, waters, ItemStack::new);
  private volatile ParsedLoot parsed;

  public GolemAnglerCore() {
    super("golem-angler-core");
    registerConfiguration(Config.class);
    setLocalizationKey("golemancy.angler_core");
    setIcon(Material.FISHING_ROD);
    setInterval(SWEEP_INTERVAL_MILLIS);
    ConfiguredRecipeParser parser = ConfiguredRecipeParser.bukkit(GolemItems.resolver(), GolemItems.recipeBook());
    registerGolemRecipe(parser, CORE_RECIPE, getConfig().anglerCoreRecipe);
    registerGolemRecipe(parser, NODE_RECIPE, getConfig().fishingNodeRecipe);
  }

  @Override
  public void unregister() {
    NodeBehaviors.unregister(NodeType.FISHING, fishing);
    NodePanels.unregister(NodeType.FISHING, panel);
    waters.forget();
    super.unregister();
  }

  @Override
  protected void onRuntimeActivated() {
    NodeBehaviors.register(NodeType.FISHING, fishing);
    NodePanels.register(NodeType.FISHING, panel);
  }

  @Override
  protected void normalizeLoadedConfig(Config loadedConfig) {
    loadedConfig.rainIntervalMultiplier = AnglerRules.rainMultiplier(loadedConfig.rainIntervalMultiplier);
    loadedConfig.doubleCatchChance = AnglerRules.fraction(loadedConfig.doubleCatchChance);
    loadedConfig.maxAnglersPerChunk = AnglerRules.atLeastOne(loadedConfig.maxAnglersPerChunk);
    loadedConfig.treasureEnchantLevels = AnglerRules.enchantLevels(loadedConfig.treasureEnchantLevels);
    loadedConfig.workXpPerCatch = AnglerRules.nonNegative(loadedConfig.workXpPerCatch);
  }

  @Override
  public void addStats(int level, Element v) {
    Config config = getConfig();
    statLore(v, C.YELLOW, "* ", AnglerRules.catchSeconds(config.catchIntervalSecondsByLevel, level), 1);
    int luck = AnglerRules.luck(config.luckByLevel, level);
    if (luck > 0) {
      statLore(v, luck, 2);
    }
    if (AnglerRules.unlocked(level, config.junkFilterLevel)) {
      v.addLore(C.GREEN + "+ " + C.GRAY + AdaptLanguage.text(GolemancyMessages.ANGLER_CORE_LORE3));
    }
    if (AnglerRules.unlocked(level, config.rainLevel)) {
      statLore(v, Form.pc(1D - config.rainIntervalMultiplier, 0), 4);
    }
    if (AnglerRules.unlocked(level, config.doubleCatchLevel)) {
      statLore(v, Form.pc(config.doubleCatchChance, 0), 5);
    }
  }

  @Override
  public boolean hasTickDemand() {
    return waters.pending();
  }

  @Override
  public void onTick() {
    waters.sweep(M.ms(), AnglerWaters.forgetMillis(GolemancyRuntime.nodeSettings().scanIntervalTicks()));
  }

  Config config() {
    return getConfig();
  }

  int ownerLevel(UUID owner) {
    AdaptPlayer player = getServer().getOnlineAdaptPlayer(owner);
    return player == null ? 0 : getLevel(player);
  }

  AnglerLoot.Tables loot() {
    Config config = getConfig();
    ParsedLoot current = parsed;
    if (current == null || current.fish() != config.fish || current.junk() != config.junk || current.treasure() != config.treasure) {
      current = new ParsedLoot(config.fish, config.junk, config.treasure,
          new AnglerLoot.Tables(table(config.fish, "fish"), table(config.junk, "junk"), table(config.treasure, "treasure")));
      parsed = current;
    }
    return current.tables();
  }

  ItemStack item(AnglerLoot.Catch caught) {
    Material base = AnglerLoot.base(caught);
    ItemStack item = new ItemStack(base, Math.min(caught.amount(), base.getMaxStackSize()));
    if (!AnglerLoot.enchanted(caught, base.getMaxDurability())) {
      return item;
    }
    return PaperCompat.enchantWithLevels(item, getConfig().treasureEnchantLevels, ThreadLocalRandom.current());
  }

  private void registerGolemRecipe(ConfiguredRecipeParser parser, String name, ConfiguredShapedRecipe spec) {
    NamespacedKey key = new NamespacedKey(Adapt.instance, name);
    parser.builder(key, spec).ifPresent(builder -> registerRecipe(builder.requiredLevel(RECIPE_LEVEL).build()));
    GolemItems.recipeTithe().set(key, spec.isEnabled() ? spec.getTithe() : 0);
  }

  private static AnglerLoot.Table table(List<String> entries, String field) {
    return AnglerLoot.parse(entries, Material::isItem,
        entry -> Adapt.warn("Angler Core ignores " + entry + " in " + field + " because it is not MATERIAL:weight[:min[:max]] with an item and a weight above 0."));
  }

  private record ParsedLoot(List<String> fish, List<String> junk, List<String> treasure, AnglerLoot.Tables tables) {
  }

  @ConfigDescription("Golems bound with an Angler Core fish the open water in front of a Fishing Node and carry the catch, with more luck, a junk filter, faster catches in the rain and double catches as the core grows stronger.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Seconds an Angler waits for each catch, by Angler Core level from level 1 upward. Static Overdrive on copper golems and Swiftness runes shorten it. Values below 1 count as 1.", impact = "Lower values make Anglers catch more often.")
    List<Integer> catchIntervalSecondsByLevel = new ArrayList<>(List.of(60, 50, 40, 32, 25));
    @ConfigDoc(value = "Fishing luck of each catch, by Angler Core level from level 1 upward. Each point changes the category weights by their luck shifts, as Luck of the Sea does. Values below 0 count as 0.", impact = "Higher values catch more treasure and less junk.")
    List<Integer> luckByLevel = new ArrayList<>(List.of(0, 0, 1, 1, 2));
    @ConfigDoc(value = "Angler Core level at which Fishing Nodes can turn on the junk filter, which throws junk catches back.", impact = "Lower values unlock the junk filter sooner.")
    int junkFilterLevel = 3;
    @ConfigDoc(value = "Angler Core level at which Anglers catch faster while it rains on their water.", impact = "Lower values unlock faster rain fishing sooner.")
    int rainLevel = 4;
    @ConfigDoc(value = "Multiplier on the catch time while it rains on the water, from rainLevel. 0.75 is 25% shorter. Values load between 0.05 and 1.", impact = "Lower values make rain fishing faster.")
    double rainIntervalMultiplier = 0.75;
    @ConfigDoc(value = "Angler Core level at which a catch can bring up a second catch.", impact = "Lower values unlock double catches sooner.")
    int doubleCatchLevel = 5;
    @ConfigDoc(value = "Chance, from 0 to 1, that a catch brings up a second catch from doubleCatchLevel. 0.1 is 10%.", impact = "Higher values give more double catches.")
    double doubleCatchChance = 0.1;
    @ConfigDoc(value = "Most Anglers that can fish at once in one chunk, counting every Fishing Node in it. Each Fishing Node seats up to 5 of them. Values below 1 count as 1.", impact = "Higher values let more Anglers share the same water.")
    int maxAnglersPerChunk = 2;
    @ConfigDoc(value = "Weight of the fish category at 0 luck.", impact = "Higher values catch fish more often.")
    int fishWeight = 85;
    @ConfigDoc(value = "Change to the fish weight per point of luck.", impact = "Lower values make luck replace more fish with treasure.")
    int fishLuckShift = -1;
    @ConfigDoc(value = "Weight of the junk category at 0 luck.", impact = "Higher values catch junk more often.")
    int junkWeight = 10;
    @ConfigDoc(value = "Change to the junk weight per point of luck. Weights never drop below 0.", impact = "Lower values make luck remove junk faster.")
    int junkLuckShift = -2;
    @ConfigDoc(value = "Weight of the treasure category at 0 luck.", impact = "Higher values catch treasure more often.")
    int treasureWeight = 5;
    @ConfigDoc(value = "Change to the treasure weight per point of luck.", impact = "Higher values make luck bring up treasure more often.")
    int treasureLuckShift = 2;
    @ConfigDoc(value = "Fish catches as MATERIAL:weight, or MATERIAL:weight:min:max for a stack size from min to max. Entries that are not an item or have no weight above 0 are ignored with a warning.", impact = "Add, remove or reweight entries to change which fish Anglers catch.")
    List<String> fish = new ArrayList<>(AnglerLoot.DEFAULT_FISH);
    @ConfigDoc(value = "Junk catches as MATERIAL:weight, or MATERIAL:weight:min:max for a stack size from min to max. Entries that are not an item or have no weight above 0 are ignored with a warning.", impact = "Add, remove or reweight entries to change which junk Anglers catch.")
    List<String> junk = new ArrayList<>(AnglerLoot.DEFAULT_JUNK);
    @ConfigDoc(value = "Treasure catches as MATERIAL:weight, or MATERIAL:weight:min:max for a stack size from min to max. ENCHANTED_BOOK and items with durability, such as bows and fishing rods, are enchanted with treasureEnchantLevels. Entries that are not an item or have no weight above 0 are ignored with a warning.", impact = "Add, remove or reweight entries to change which treasure Anglers catch.")
    List<String> treasure = new ArrayList<>(AnglerLoot.DEFAULT_TREASURE);
    @ConfigDoc(value = "Enchantment levels treasure enchanted books, bows, fishing rods and other durable treasure are enchanted with, as at an enchanting table, including treasure enchantments. Values load between 1 and 30.", impact = "Higher values give treasure stronger enchantments.")
    int treasureEnchantLevels = 30;
    @ConfigDoc(value = "Golemancy work XP paid for each catch, including junk the junk filter throws back. Values below 0 count as 0.", impact = "Higher values pay more XP for fishing.")
    double workXpPerCatch = 6;
    @ConfigDoc(value = "Crafting recipe for the Angler Core, unlocked at Angler Core level 1.", impact = "Edit the rows, keys, result, amount or tithe to change the recipe; changes apply after a restart.")
    ConfiguredShapedRecipe anglerCoreRecipe = new ConfiguredShapedRecipe()
        .rows(" X ", "YBY", " Z ")
        .key('X', "FISHING_ROD")
        .key('Y', "PRISMARINE_SHARD")
        .key('Z', "DRIED_KELP")
        .key('B', "golemancy:blank_core")
        .result("golemancy:core/angler", 1)
        .tithe(2);
    @ConfigDoc(value = "Crafting recipe for the Fishing Node, unlocked at Angler Core level 1.", impact = "Edit the rows, keys, result, amount or tithe to change the recipe; changes apply after a restart.")
    ConfiguredShapedRecipe fishingNodeRecipe = new ConfiguredShapedRecipe()
        .rows(" X ", " B ", "   ")
        .key('X', "FISHING_ROD")
        .key('B', "golemancy:blank_node")
        .result("golemancy:node/fishing", 1);

    public Config() {
      requires = new ArrayList<>(List.of("golem-node-weaving:1"));
    }
  }
}
