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
import art.arcane.adapt.api.golem.node.NodeItemTags;
import art.arcane.adapt.api.recipe.ConfiguredRecipeParser;
import art.arcane.adapt.api.recipe.ConfiguredShapedRecipe;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GolemSorterCore extends SimpleAdaptation<GolemSorterCore.Config> {
  static final String CORE_RECIPE = "golemancy-sorter-core";
  static final String INPUT_RECIPE = "golemancy-input-node";
  static final String FILTER_RECIPE = "golemancy-filter-node";
  private static final int CORE_RECIPE_LEVEL = 1;
  private static final long SWEEP_INTERVAL_MILLIS = 5_000L;
  private static final long MILLIS_PER_SECOND = 1_000L;
  private static final int MAX_SECONDS = 3_600;
  private static final int MIN_FETCH_TIMEOUT_SECONDS = 10;
  private static final int MAX_INDEXED_CONTAINERS = 16_384;
  private static final int MAX_SERVED_CONTAINERS = 1_024;
  private static final int MAX_DESTINATION_RADIUS = 64;
  private static final int MAX_STACKS_PER_TRIP = 64;
  private static final long TICK_MILLIS = 50L;

  private final SorterIndex index = new SorterIndex(() -> getConfig().maxIndexedContainers, () -> getConfig().indexRefreshSeconds * MILLIS_PER_SECOND);
  private final SorterPlans plans = new SorterPlans();
  private final SorterFetches fetches = new SorterFetches(() -> getConfig().fetchTimeoutSeconds * MILLIS_PER_SECOND);
  private final SorterNetwork network = new SorterNetwork(this, BukkitSorterSpace.INSTANCE, index, NodeItemTags.shared(), SorterVariants::of,
      () -> GolemancyRuntime.nodeSettings().scanIntervalTicks() * TICK_MILLIS);
  private final InputNodeBehavior input = new InputNodeBehavior(this, network, plans, fetches, index, Bukkit::getPlayer);
  private final FilterNodeBehavior filter = new FilterNodeBehavior(this);
  private final SorterFetchGui fetchGui = new SorterFetchGui(this, network, fetches);
  private final InputNodePanel inputPanel = new InputNodePanel(this, ItemStack::new, fetchGui::open);
  private final FilterNodePanel filterPanel = new FilterNodePanel(this, ItemStack::new);
  private volatile ParsedGroups parsedGroups;

  public GolemSorterCore() {
    super("golem-sorter-core");
    registerConfiguration(Config.class);
    setLocalizationKey("golemancy.sorter_core");
    setIcon(Material.COMPARATOR);
    setInterval(SWEEP_INTERVAL_MILLIS);
    Config config = getConfig();
    ConfiguredRecipeParser parser = ConfiguredRecipeParser.bukkit(GolemItems.resolver(), GolemItems.recipeBook());
    registerGolemRecipe(parser, CORE_RECIPE, config.sorterCoreRecipe, CORE_RECIPE_LEVEL);
    registerGolemRecipe(parser, INPUT_RECIPE, config.inputNodeRecipe, CORE_RECIPE_LEVEL);
    registerGolemRecipe(parser, FILTER_RECIPE, config.filterNodeRecipe, Math.max(CORE_RECIPE_LEVEL, config.filterLevel));
  }

  @Override
  public void unregister() {
    NodeBehaviors.unregister(NodeType.INPUT, input);
    NodeBehaviors.unregister(NodeType.FILTER, filter);
    NodePanels.unregister(NodeType.INPUT, inputPanel);
    NodePanels.unregister(NodeType.FILTER, filterPanel);
    index.clear();
    network.clear();
    plans.clear();
    fetches.clear();
    super.unregister();
  }

  @Override
  protected void onRuntimeActivated() {
    NodeBehaviors.register(NodeType.INPUT, input);
    NodeBehaviors.register(NodeType.FILTER, filter);
    NodePanels.register(NodeType.INPUT, inputPanel);
    NodePanels.register(NodeType.FILTER, filterPanel);
  }

  @Override
  protected void normalizeLoadedConfig(Config loadedConfig) {
    loadedConfig.workXpPerStack = SorterRules.nonNegative(loadedConfig.workXpPerStack);
    loadedConfig.indexRefreshSeconds = SorterRules.clamp(loadedConfig.indexRefreshSeconds, 1, MAX_SECONDS);
    loadedConfig.maxIndexedContainers = SorterRules.clamp(loadedConfig.maxIndexedContainers, 1, MAX_INDEXED_CONTAINERS);
    loadedConfig.fetchTimeoutSeconds = SorterRules.clamp(loadedConfig.fetchTimeoutSeconds, MIN_FETCH_TIMEOUT_SECONDS, MAX_SECONDS);
    loadedConfig.maxServedContainers = SorterRules.clamp(loadedConfig.maxServedContainers, 1, MAX_SERVED_CONTAINERS);
    loadedConfig.destinationRadiusByLevel = SorterRules.clampAll(loadedConfig.destinationRadiusByLevel, 1, MAX_DESTINATION_RADIUS);
    loadedConfig.stacksPerTripByLevel = SorterRules.clampAll(loadedConfig.stacksPerTripByLevel, 1, MAX_STACKS_PER_TRIP);
  }

  @Override
  public void addStats(int level, Element v) {
    Config config = getConfig();
    statLore(v, SorterRules.destinationRadius(config.destinationRadiusByLevel, level), 1);
    statLore(v, SorterRules.stacksPerTrip(config.stacksPerTripByLevel, level), 2);
    unlockLore(v, level, config.groupsLevel, GolemancyMessages.SORTER_CORE_LORE3);
    unlockLore(v, level, config.filterLevel, GolemancyMessages.SORTER_CORE_LORE4);
    unlockLore(v, level, config.preciseLevel, GolemancyMessages.SORTER_CORE_LORE5);
    unlockLore(v, level, config.shulkerBoxLevel, GolemancyMessages.SORTER_CORE_LORE6);
    unlockLore(v, level, config.restockLevel, GolemancyMessages.SORTER_CORE_LORE7);
    unlockLore(v, level, config.indexLevel, GolemancyMessages.SORTER_CORE_LORE8);
    unlockLore(v, level, config.fetchLevel, GolemancyMessages.SORTER_CORE_LORE9);
  }

  @Override
  public boolean hasTickDemand() {
    return !index.isEmpty() || !network.isEmpty() || !plans.isEmpty() || !fetches.isEmpty();
  }

  @Override
  public void onTick() {
    long now = M.ms();
    index.sweep(now);
    network.sweep(now);
    plans.sweep(now);
    fetches.sweep(now);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(InventoryMoveItemEvent e) {
    if (index.isEmpty()) {
      return;
    }
    invalidateStorage(e.getSource());
    invalidateStorage(e.getDestination());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(InventoryClickEvent e) {
    if (!index.isEmpty()) {
      index.invalidate(e.getView().getTopInventory());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(InventoryCloseEvent e) {
    if (!index.isEmpty()) {
      index.invalidate(e.getView().getTopInventory());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    forgetFetches(e.getPlayer().getUniqueId());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerChangedWorldEvent e) {
    forgetFetches(e.getPlayer().getUniqueId());
  }

  Config config() {
    return getConfig();
  }

  int ownerLevel(UUID owner) {
    AdaptPlayer player = getServer().getOnlineAdaptPlayer(owner);
    return player == null ? 0 : getLevel(player);
  }

  Map<Material, String> groups() {
    List<String> entries = getConfig().groups;
    ParsedGroups current = parsedGroups;
    if (current == null || current.entries() != entries) {
      current = new ParsedGroups(entries, SorterRules.groups(entries, GolemSorterCore::itemTag,
          entry -> Adapt.warn("Sorter Core ignores the group entry " + entry + " because it names no item or item tag.")));
      parsedGroups = current;
    }
    return current.groups();
  }

  private void forgetFetches(UUID player) {
    fetches.cancel(player);
    plans.forgetPlayer(player);
  }

  private void invalidateStorage(Inventory inventory) {
    InventoryType type = inventory.getType();
    if (type == InventoryType.CHEST || type == InventoryType.BARREL || type == InventoryType.SHULKER_BOX) {
      index.invalidate(inventory);
    }
  }

  private void registerGolemRecipe(ConfiguredRecipeParser parser, String name, ConfiguredShapedRecipe spec, int level) {
    NamespacedKey key = new NamespacedKey(Adapt.instance, name);
    parser.builder(key, spec).ifPresent(builder -> registerRecipe(builder.requiredLevel(level).build()));
    GolemItems.recipeTithe().set(key, spec.isEnabled() ? spec.getTithe() : 0);
  }

  private static void unlockLore(Element v, int level, int unlockLevel, TextKey key) {
    if (SorterRules.unlocked(level, unlockLevel)) {
      v.addLore(C.GREEN + "+ " + C.GRAY + AdaptLanguage.text(key));
    }
  }

  private static Collection<Material> itemTag(String tag) {
    NamespacedKey key = NamespacedKey.fromString(tag.substring(1));
    Tag<Material> found = key == null ? null : Bukkit.getTag(Tag.REGISTRY_ITEMS, key, Material.class);
    return found == null ? List.of() : found.getValues();
  }

  private record ParsedGroups(List<String> entries, Map<Material, String> groups) {
  }

  @ConfigDescription("Copper golems bound with a Sorter Core empty the container under an Input Node into nearby containers that already hold each item, and learn item groups, Filter Nodes, restocking and fetching as the core grows stronger.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Blocks around an Input Node within which a Sorter puts items away, by Sorter Core level from level 1 upward. Each Input Node can narrow it in its menu. Values outside 1 to 64 load as the nearest limit.", impact = "Higher values let Sorters serve containers farther from the Input Node.")
    List<Integer> destinationRadiusByLevel = new ArrayList<>(List.of(8, 12, 16, 20, 24));
    @ConfigDoc(value = "Item stacks a Sorter takes from a container on each trip, by Sorter Core level from level 1 upward. Its carry limit still applies. Values outside 1 to 64 load as the nearest limit.", impact = "Higher values move more stacks per trip.")
    List<Integer> stacksPerTripByLevel = new ArrayList<>(List.of(1, 1, 2, 2, 4));
    @ConfigDoc(value = "Sorter Core level at which Sorters also put items into containers that hold another item of the same group.", impact = "Lower values unlock item groups sooner.")
    int groupsLevel = 2;
    @ConfigDoc(value = "Item groups, written as name=members with members separated by commas. A member is an item name or #item_tag. An item that belongs to two groups counts for the first.", impact = "Add a group to keep related items together, such as dyes or music discs.")
    List<String> groups = new ArrayList<>(SorterRules.DEFAULT_GROUPS);
    @ConfigDoc(value = "Sorter Core level at which Sorters use Filter Nodes, including overflow Filter Nodes, and the Filter Node recipe unlocks.", impact = "Lower values unlock Filter Nodes sooner.")
    int filterLevel = 3;
    @ConfigDoc(value = "Sorter Core level at which Sorters tell enchanted books apart by their enchantments and potions and tipped arrows by their potion.", impact = "Lower values unlock enchantment and potion matching sooner.")
    int preciseLevel = 4;
    @ConfigDoc(value = "Sorter Core level at which Sorters move shulker boxes, which they move whole with their contents.", impact = "Lower values let Sorters move shulker boxes sooner.")
    int shulkerBoxLevel = 4;
    @ConfigDoc(value = "Sorter Core level at which Filter Nodes can set a restock amount that Sorters keep topped up.", impact = "Lower values unlock restocking sooner.")
    int restockLevel = 4;
    @ConfigDoc(value = "Sorter Core level at which Sorters keep an index of the contents of the containers they serve instead of reading every container on each trip.", impact = "Lower values unlock the index sooner.")
    int indexLevel = 5;
    @ConfigDoc(value = "Sorter Core level at which players can fetch items from the Input Node menu.", impact = "Lower values unlock fetching sooner.")
    int fetchLevel = 5;
    @ConfigDoc(value = "Seconds an indexed container's contents are trusted before a Sorter reads the container again, from 1 to 3600. A player clicking in or closing the container, a hopper moving items in or out of it, or a Sorter working it refreshes it at once.", impact = "Lower values read containers more often.")
    int indexRefreshSeconds = 30;
    @ConfigDoc(value = "Most containers the server keeps in the Sorter index at once, from 1 to 16384. The oldest entry is dropped when it is full.", impact = "Higher values index more containers but use more memory.")
    int maxIndexedContainers = 1024;
    @ConfigDoc(value = "Seconds a fetch request waits for a Sorter before it is dropped, from 10 to 3600.", impact = "Higher values keep fetch requests open longer.")
    int fetchTimeoutSeconds = 120;
    @ConfigDoc(value = "Most containers one Input Node serves, nearest first, from 1 to 1024. Containers beyond the limit are left alone.", impact = "Higher values let one Input Node serve bigger storage rooms but read more containers.")
    int maxServedContainers = 128;
    @ConfigDoc(value = "Golemancy work XP paid for each item stack a Sorter puts away or brings to you. Values below 0 count as 0.", impact = "Higher values pay more XP for sorting.")
    double workXpPerStack = 0.3;
    @ConfigDoc(value = "Crafting recipe for the Sorter Core, unlocked at Sorter Core level 1.", impact = "Edit the rows, keys, result, amount or tithe to change the recipe; changes apply after a restart.")
    ConfiguredShapedRecipe sorterCoreRecipe = new ConfiguredShapedRecipe()
        .rows(" X ", "YBY", " Z ")
        .key('X', "COMPARATOR")
        .key('Y', "QUARTZ")
        .key('Z', "COPPER_CHEST")
        .key('B', "golemancy:blank_core")
        .result("golemancy:core/sorter", 1)
        .tithe(2);
    @ConfigDoc(value = "Crafting recipe for the Input Node, unlocked at Sorter Core level 1.", impact = "Edit the rows, keys, result, amount or tithe to change the recipe; changes apply after a restart.")
    ConfiguredShapedRecipe inputNodeRecipe = new ConfiguredShapedRecipe()
        .rows(" X ", " B ", "   ")
        .key('X', "COPPER_CHEST")
        .key('B', "golemancy:blank_node")
        .result("golemancy:node/input", 1);
    @ConfigDoc(value = "Crafting recipe for the Filter Node, unlocked at the filterLevel Sorter Core level.", impact = "Edit the rows, keys, result, amount or tithe to change the recipe; changes apply after a restart.")
    ConfiguredShapedRecipe filterNodeRecipe = new ConfiguredShapedRecipe()
        .rows(" X ", " B ", "   ")
        .key('X', "ITEM_FRAME")
        .key('B', "golemancy:blank_node")
        .result("golemancy:node/filter", 1);

    public Config() {
      requires = new ArrayList<>(List.of("golem-node-weaving:1"));
    }
  }
}
