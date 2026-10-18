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
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GolemBreakerCore extends SimpleAdaptation<GolemBreakerCore.Config> {
  static final String CORE_RECIPE = "golemancy-breaker-core";
  static final String NODE_RECIPE = "golemancy-breaker-node";
  private static final int RECIPE_LEVEL = 1;
  private static final int ENCHANTMENT_LEVEL = 1;
  private static final double TICKS_PER_SECOND = 20D;

  private final Map<Material, Boolean> tileEntities = new ConcurrentHashMap<>();
  private final Map<Tool, ItemStack> tools = new ConcurrentHashMap<>();
  private final BreakerNodeBehavior breaker = new BreakerNodeBehavior(this);
  private final BreakerNodePanel panel = new BreakerNodePanel(this, ItemStack::new);
  private volatile ParsedBlacklist parsed;

  public GolemBreakerCore() {
    super("golem-breaker-core");
    registerConfiguration(Config.class);
    setLocalizationKey("golemancy.breaker_core");
    setIcon(Material.IRON_PICKAXE);
    ConfiguredRecipeParser parser = ConfiguredRecipeParser.bukkit(GolemItems.resolver(), GolemItems.recipeBook());
    registerGolemRecipe(parser, CORE_RECIPE, getConfig().breakerCoreRecipe);
    registerGolemRecipe(parser, NODE_RECIPE, getConfig().breakerNodeRecipe);
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
    loadedConfig.workXpPerBlock = BreakerRules.nonNegative(loadedConfig.workXpPerBlock);
  }

  @Override
  public void addStats(int level, Element v) {
    Config config = getConfig();
    statLore(v, BreakerRules.maxHardness(config.maxHardnessByLevel, level), 1);
    statLore(v, C.YELLOW, "* ", BreakerRules.intervalTicks(config.intervalTicksByLevel, level) / TICKS_PER_SECOND, 2);
    statLore(v, Form.capitalizeWords(BreakerRules.tool(config.toolByLevel, level).name().toLowerCase(Locale.ROOT).replace('_', ' ')), 3);
    if (BreakerRules.unlocked(level, config.silkTouchLevel)) {
      unlockLore(v, GolemancyMessages.BREAKER_CORE_LORE4);
    }
    if (BreakerRules.unlocked(level, config.fortuneLevel)) {
      unlockLore(v, GolemancyMessages.BREAKER_CORE_LORE5);
    }
  }

  Config config() {
    return getConfig();
  }

  Set<Material> blacklist() {
    List<String> names = getConfig().blacklist;
    ParsedBlacklist current = parsed;
    if (current == null || current.names() != names) {
      current = new ParsedBlacklist(names, BreakerRules.blacklist(names, GolemBreakerCore::blockTag));
      parsed = current;
    }
    return current.blocks();
  }

  int ownerLevel(UUID owner) {
    AdaptPlayer player = getServer().getOnlineAdaptPlayer(owner);
    return player == null ? 0 : getLevel(player);
  }

  float hardness(Material type) {
    return type.getHardness();
  }

  boolean tileEntity(Block block) {
    return tileEntities.computeIfAbsent(block.getType(), ignored -> PaperCompat.liveState(block) instanceof TileState);
  }

  ItemStack tool(Material type, boolean silk, boolean fortune) {
    return tools.computeIfAbsent(new Tool(type, silk, fortune), GolemBreakerCore::virtualTool);
  }

  private void registerGolemRecipe(ConfiguredRecipeParser parser, String name, ConfiguredShapedRecipe spec) {
    NamespacedKey key = new NamespacedKey(Adapt.instance, name);
    parser.builder(key, spec).ifPresent(builder -> registerRecipe(builder.requiredLevel(RECIPE_LEVEL).build()));
    GolemItems.recipeTithe().set(key, spec.isEnabled() ? spec.getTithe() : 0);
  }

  private static ItemStack virtualTool(Tool spec) {
    ItemStack tool = new ItemStack(spec.type());
    if (spec.silk()) {
      tool.addUnsafeEnchantment(Enchantment.SILK_TOUCH, ENCHANTMENT_LEVEL);
    } else if (spec.fortune()) {
      tool.addUnsafeEnchantment(Enchantment.FORTUNE, ENCHANTMENT_LEVEL);
    }
    return tool;
  }

  private static Collection<Material> blockTag(String entry) {
    NamespacedKey key = NamespacedKey.fromString(entry.substring(1).toLowerCase(Locale.ROOT));
    Tag<Material> tag = key == null ? null : Bukkit.getTag(Tag.REGISTRY_BLOCKS, key, Material.class);
    return tag == null ? List.of() : tag.getValues();
  }

  private static void unlockLore(Element v, TextKey key) {
    v.addLore(C.GREEN + "+ " + C.GRAY + AdaptLanguage.text(key));
  }

  private record ParsedBlacklist(List<String> names, Set<Material> blocks) {
  }

  private record Tool(Material type, boolean silk, boolean fortune) {
  }

  @ConfigDescription("Golems bound with a Breaker Core break the blocks a Breaker Node's filter lists inside its area and carry the drops, mining harder blocks with better pickaxes as the core grows stronger.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Ticks a Breaker spends on each block after it reaches it, by Breaker Core level from level 1 upward. Copper Overdrive and Swiftness runes shorten it. Values below 1 count as 1.", impact = "Lower values make Breakers mine faster.")
    List<Integer> intervalTicksByLevel = new ArrayList<>(List.of(40, 30, 20, 15, 10));
    @ConfigDoc(value = "Hardest block a Breaker breaks, by Breaker Core level from level 1 upward. Stone is 1.5, cobblestone 2, deepslate 3 and obsidian 50.", impact = "Higher values let Breakers mine harder blocks sooner.")
    List<Double> maxHardnessByLevel = new ArrayList<>(List.of(1.5D, 3.0D, 5.0D, 10.0D, 50.0D));
    @ConfigDoc(value = "Pickaxe a Breaker mines with, by item name and Breaker Core level from level 1 upward. Drops are rolled as if this pickaxe broke the block, and blocks it cannot harvest are left alone. Unknown names use the default pickaxe for that level.", impact = "Better pickaxes harvest more blocks, such as diamond ore with an iron pickaxe or obsidian with a diamond pickaxe.")
    List<String> toolByLevel = new ArrayList<>(BreakerRules.DEFAULT_TOOLS);
    @ConfigDoc(value = "Breaker Core level at which Breaker Nodes can turn on Silk Touch, so Breakers mine blocks whole.", impact = "Lower values unlock Silk Touch sooner.")
    int silkTouchLevel = 3;
    @ConfigDoc(value = "Breaker Core level at which Breaker Nodes can turn on Fortune I, which raises ore drops. Silk Touch and Fortune I never apply together.", impact = "Lower values unlock Fortune I sooner.")
    int fortuneLevel = 5;
    @ConfigDoc(value = "Blocks Breakers never break, by block name or #block_tag. Breakers also never break blocks with a block entity, such as chests, furnaces, signs and spawners, or the block under any node.", impact = "Add a block or tag to protect it from Breakers.")
    List<String> blacklist = new ArrayList<>(BreakerRules.DEFAULT_BLACKLIST);
    @ConfigDoc(value = "Golemancy work XP paid for each block a Breaker breaks. Values below 0 count as 0.", impact = "Higher values pay more XP for mining.")
    double workXpPerBlock = 0.4;
    @ConfigDoc(value = "Crafting recipe for the Breaker Core, unlocked at Breaker Core level 1.", impact = "Edit the rows, keys, result, amount or tithe to change the recipe; changes apply after a restart.")
    ConfiguredShapedRecipe breakerCoreRecipe = new ConfiguredShapedRecipe()
        .rows(" X ", "YBY", " Z ")
        .key('X', "IRON_PICKAXE")
        .key('Y', "REDSTONE")
        .key('Z', "PISTON")
        .key('B', "golemancy:blank_core")
        .result("golemancy:core/breaker", 1)
        .tithe(2);
    @ConfigDoc(value = "Crafting recipe for the Breaker Node, unlocked at Breaker Core level 1.", impact = "Edit the rows, keys, result, amount or tithe to change the recipe; changes apply after a restart.")
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
