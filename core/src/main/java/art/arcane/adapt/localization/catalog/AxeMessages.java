package art.arcane.adapt.localization.catalog;

import art.arcane.volmlib.util.localization.LinesKey;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.List;

public final class AxeMessages {
  public static final TextKey PREFERENCE_LATCH = TextKey.of("axe.preferences.latch", "Latch tree felling; sneak again to cancel");
  public static final TextKey PREFERENCE_SNEAK = TextKey.of("axe.preferences.sneak", "Require sneaking");
  public static final TextKey PREFERENCE_IGNORE_PASSIVE = TextKey.of("axe.preferences.ignore_passive", "Ignore passive mobs");
  public static final TextKey PREFERENCE_PLAYERS = TextKey.of("axe.preferences.players", "Affect players");
  public static final TextKey PREFERENCE_WORK = TextKey.of("axe.preferences.work", "Work limit");
  public static final TextKey PREFERENCE_RESERVE = TextKey.of("axe.preferences.reserve", "Hunger remaining after use");
  public static final TextKey PREFERENCE_NONE = TextKey.of("axe.preferences.none", "No extra reserve");
  public static final TextKey PREFERENCE_FIVE = TextKey.of("axe.preferences.five", "Keep 5 hunger");
  public static final TextKey PREFERENCE_TEN = TextKey.of("axe.preferences.ten", "Keep 10 hunger");
  public static final TextKey PREFERENCE_FIFTEEN = TextKey.of("axe.preferences.fifteen", "Keep 15 hunger");
  public static final TextKey PREFERENCE_ITEMS = TextKey.of("axe.preferences.items", "Collected items");
  public static final TextKey PREFERENCE_ALL = TextKey.of("axe.preferences.all", "All permitted");
  public static final TextKey PREFERENCE_LOGS = TextKey.of("axe.preferences.logs", "Logs and stems");
  public static final TextKey PREFERENCE_SAPLINGS = TextKey.of("axe.preferences.saplings", "Saplings and propagules");
  public static final TextKey PREFERENCE_APPLES = TextKey.of("axe.preferences.apples", "Apples only");
  public static final TextKey PREFERENCE_TRIGGER = TextKey.of("axe.preferences.trigger", "Activation gesture");
  public static final TextKey PREFERENCE_WHILE_SNEAKING = TextKey.of("axe.preferences.while_sneaking", "While sneaking");
  public static final TextKey PREFERENCE_NOT_SNEAKING = TextKey.of("axe.preferences.not_sneaking", "While not sneaking");
  public static final TextKey PREFERENCE_ALWAYS = TextKey.of("axe.preferences.always", "Always");
  public static final TextKey PREFERENCE_FELLED_LOGS = TextKey.of("axe.preferences.felled_logs", "Felled logs");
  public static final TextKey PREFERENCE_STRIPPED = TextKey.of("axe.preferences.stripped", "Stripped logs");
  public static final TextKey PREFERENCE_PLANKS = TextKey.of("axe.preferences.planks", "Planks");
  private static final List<TextKey> PREFERENCE_KEYS = List.of(
      PREFERENCE_LATCH,
      PREFERENCE_SNEAK,
      PREFERENCE_IGNORE_PASSIVE,
      PREFERENCE_PLAYERS,
      PREFERENCE_WORK,
      PREFERENCE_RESERVE,
      PREFERENCE_NONE,
      PREFERENCE_FIVE,
      PREFERENCE_TEN,
      PREFERENCE_FIFTEEN,
      PREFERENCE_ITEMS,
      PREFERENCE_ALL,
      PREFERENCE_LOGS,
      PREFERENCE_SAPLINGS,
      PREFERENCE_APPLES,
      PREFERENCE_TRIGGER,
      PREFERENCE_WHILE_SNEAKING,
      PREFERENCE_NOT_SNEAKING,
      PREFERENCE_ALWAYS,
      PREFERENCE_FELLED_LOGS,
      PREFERENCE_STRIPPED,
      PREFERENCE_PLANKS);
  public static final TextKey CHOP_NAME = TextKey.of("axe.chop.name", "Axe Chop");
  public static final TextKey CHOP_DESCRIPTION = TextKey.of("axe.chop.description", "Chop down trees by right clicking the base log!");
  public static final TextKey CHOP_LORE1 = TextKey.of("axe.chop.lore1", "Blocks Per Chop");
  public static final TextKey CHOP_LORE2 = TextKey.of("axe.chop.lore2", "Chop Cooldown");
  public static final TextKey CHOP_LORE3 = TextKey.of("axe.chop.lore3", "Tool Wear");
  public static final LinesKey CHOP_LORE = LinesKey.of("axe.chop.lore", "Blocks Per Chop", "Chop Cooldown", "Tool Wear");
  public static final TextKey LOG_SWAP_NAME = TextKey.of("axe.log_swap.name", "Lucy's Log-Swapper");
  public static final TextKey LOG_SWAP_DESCRIPTION = TextKey.of("axe.log_swap.description", "Change the flavor of logs in a Crafting Table!");
  public static final TextKey LOG_SWAP_LORE1 = TextKey.of("axe.log_swap.lore1", "8 Log of any kind + 1 sapling = 8 log of the sapling's type");
  public static final LinesKey LOG_SWAP_LORE = LinesKey.of("axe.log_swap.lore", "8 Log of any kind + 1 sapling = 8 log of the sapling's type");
  public static final TextKey DROP_TO_INVENTORY_NAME = TextKey.of("axe.drop_to_inventory.name", "Axe Drop to Inventory");
  public static final TextKey GROUND_SMASH_NAME = TextKey.of("axe.ground_smash.name", "Axe Ground Smash");
  public static final TextKey GROUND_SMASH_DESCRIPTION = TextKey.of("axe.ground_smash.description", "Jump, then crouch and smash all nearby enemies.");
  public static final TextKey GROUND_SMASH_LORE1 = TextKey.of("axe.ground_smash.lore1", "Damage");
  public static final TextKey GROUND_SMASH_LORE2 = TextKey.of("axe.ground_smash.lore2", "Block Radius");
  public static final TextKey GROUND_SMASH_LORE3 = TextKey.of("axe.ground_smash.lore3", "Force");
  public static final TextKey GROUND_SMASH_LORE4 = TextKey.of("axe.ground_smash.lore4", "Smash Cooldown");
  public static final LinesKey GROUND_SMASH_LORE = LinesKey.of("axe.ground_smash.lore", "Damage", "Block Radius", "Force", "Smash Cooldown");
  public static final TextKey LEAF_MINER_NAME = TextKey.of("axe.leaf_miner.name", "Leaf-Miner");
  public static final TextKey LEAF_MINER_DESCRIPTION = TextKey.of("axe.leaf_miner.description", "Allows you to break bulk leaves at once!");
  public static final TextKey LEAF_MINER_LORE1 = TextKey.of("axe.leaf_miner.lore1", "Sneak, and mine LEAVES");
  public static final TextKey LEAF_MINER_LORE2 = TextKey.of("axe.leaf_miner.lore2", "range of Leaf-mining");
  public static final TextKey LEAF_MINER_LORE3 = TextKey.of("axe.leaf_miner.lore3", "You will not get the drops from the leaves (Exploit Prevention)");
  public static final LinesKey LEAF_MINER_LORE = LinesKey.of("axe.leaf_miner.lore", "Sneak, and mine LEAVES", "range of Leaf-mining", "You will not get the drops from the leaves (Exploit Prevention)");
  public static final TextKey WOOD_MINER_NAME = TextKey.of("axe.wood_miner.name", "Wood-Miner");
  public static final TextKey WOOD_MINER_DESCRIPTION = TextKey.of("axe.wood_miner.description", "Allows you to break bulk wood at once!");
  public static final TextKey WOOD_MINER_LORE1 = TextKey.of("axe.wood_miner.lore1", "Sneak, and mine WOOD/LOGS ( Not Planks )");
  public static final TextKey WOOD_MINER_LORE2 = TextKey.of("axe.wood_miner.lore2", "range of Wood-mining");
  public static final TextKey WOOD_MINER_LORE3 = TextKey.of("axe.wood_miner.lore3", "Works with Drop to inventory");
  public static final LinesKey WOOD_MINER_LORE = LinesKey.of("axe.wood_miner.lore", "Sneak, and mine WOOD/LOGS ( Not Planks )", "range of Wood-mining", "Works with Drop to inventory");
  public static final TextKey IRIS_FELLER_NAME = TextKey.of("axe.iris_feller.name", "Iris Feller");
  public static final TextKey IRIS_FELLER_DESCRIPTION = TextKey.of("axe.iris_feller.description", "Sneak-break an Iris tree with an axe, then keep sneaking and keep that original axe held while the tree erodes outward. Each successfully eroded log costs hunger; the run halts when sneaking stops, the original axe is no longer held, or hunger cannot fund the next log. Accepted runs start an activation cooldown.");
  public static final TextKey IRIS_FELLER_LORE1 = TextKey.of("axe.iris_feller.lore1", "chance to preserve axe durability per felled log");
  public static final TextKey IRIS_FELLER_LORE2 = TextKey.of("axe.iris_feller.lore2", "Remaining axe durability and hunger cap how many logs can fall");
  public static final TextKey IRIS_FELLER_LORE3 = TextKey.of("axe.iris_feller.lore3", "The tree keeps eroding only while both runtime controls remain active");
  public static final TextKey IRIS_FELLER_LORE4 = TextKey.of("axe.iris_feller.lore4", "hunger points per successfully eroded log");
  public static final TextKey IRIS_FELLER_LORE5 = TextKey.of("axe.iris_feller.lore5", "cooldown after Iris accepts the run");
  public static final TextKey IRIS_FELLER_LORE6 = TextKey.of("axe.iris_feller.lore6", "Sneak continuously; releasing sneak halts the run");
  public static final TextKey IRIS_FELLER_LORE7 = TextKey.of("axe.iris_feller.lore7", "Keep the original axe held; switching or losing it halts the run");
  public static final LinesKey IRIS_FELLER_LORE = LinesKey.of("axe.iris_feller.lore", "Axe Durability Preservation Per Log", "Remaining axe durability and hunger cap how many logs can fall", "The tree keeps eroding only while both runtime controls remain active", "Hunger Cost Per Eroded Log", "Activation Cooldown", "Continuous Sneaking Required", "Original Axe Must Stay Held");
  public static final TextKey THROWING_AXE_NAME = TextKey.of("axe.throwing_axe.name", "Throwing Axe");
  public static final TextKey THROWING_AXE_DESCRIPTION = TextKey.of("axe.throwing_axe.description", "Left-click air with an axe to hurl it as a spinning projectile that deals its melee damage.");
  public static final TextKey THROWING_AXE_LORE1 = TextKey.of("axe.throwing_axe.lore1", "Melee Damage Multiplier");
  public static final TextKey THROWING_AXE_LORE2 = TextKey.of("axe.throwing_axe.lore2", "Throw Speed");
  public static final TextKey THROWING_AXE_LORE3 = TextKey.of("axe.throwing_axe.lore3", "Throw Cooldown");
  public static final TextKey THROWING_AXE_LORE4 = TextKey.of("axe.throwing_axe.lore4", "Returns to your hand at max level");
  public static final LinesKey THROWING_AXE_LORE = LinesKey.of("axe.throwing_axe.lore", "Melee Damage Multiplier", "Throw Speed", "Throw Cooldown", "Returns to your hand at max level");
  public static final TextKey SUNDER_NAME = TextKey.of("axe.sunder.name", "Sunder");
  public static final TextKey SUNDER_DESCRIPTION = TextKey.of("axe.sunder.description", "Axe hits shred a target's armor and armor toughness in stacking layers that fade over time.");
  public static final TextKey SUNDER_LORE1 = TextKey.of("axe.sunder.lore1", "Armor Shred Per Stack");
  public static final TextKey SUNDER_LORE2 = TextKey.of("axe.sunder.lore2", "Max Stacks");
  public static final TextKey SUNDER_LORE3 = TextKey.of("axe.sunder.lore3", "Shred Duration");
  public static final TextKey SUNDER_LORE4 = TextKey.of("axe.sunder.lore4", "Toughness Shred Per Stack");
  public static final LinesKey SUNDER_LORE = LinesKey.of("axe.sunder.lore", "Armor Shred Per Stack", "Max Stacks", "Shred Duration", "Toughness Shred Per Stack");
  public static final TextKey CLEAVE_NAME = TextKey.of("axe.cleave.name", "Cleave");
  public static final TextKey CLEAVE_DESCRIPTION = TextKey.of("axe.cleave.description", "Melee axe swings cleave extra enemies in a short frontal arc.");
  public static final TextKey CLEAVE_LORE1 = TextKey.of("axe.cleave.lore1", "Cleave Arc");
  public static final TextKey CLEAVE_LORE2 = TextKey.of("axe.cleave.lore2", "Max Extra Targets");
  public static final TextKey CLEAVE_LORE3 = TextKey.of("axe.cleave.lore3", "Damage Share");
  public static final LinesKey CLEAVE_LORE = LinesKey.of("axe.cleave.lore", "Cleave Arc", "Max Extra Targets", "Damage Share");
  public static final TextKey BARK_HIDE_NAME = TextKey.of("axe.bark_hide.name", "Bark Hide");
  public static final TextKey BARK_HIDE_DESCRIPTION = TextKey.of("axe.bark_hide.description", "Chopping logs layers on short-lived absorption that fades once you stop working.");
  public static final TextKey BARK_HIDE_LORE1 = TextKey.of("axe.bark_hide.lore1", "Absorption Cap");
  public static final TextKey BARK_HIDE_LORE2 = TextKey.of("axe.bark_hide.lore2", "Decay Grace");
  public static final LinesKey BARK_HIDE_LORE = LinesKey.of("axe.bark_hide.lore", "Absorption Cap", "Decay Grace");
  public static final TextKey SHIELD_SPLITTER_NAME = TextKey.of("axe.shield_splitter.name", "Shield Splitter");
  public static final TextKey SHIELD_SPLITTER_DESCRIPTION = TextKey.of("axe.shield_splitter.description", "Axe hits on blocking foes disable their shield longer and deal bonus damage.");
  public static final TextKey SHIELD_SPLITTER_LORE1 = TextKey.of("axe.shield_splitter.lore1", "Shield Disable Duration");
  public static final TextKey SHIELD_SPLITTER_LORE2 = TextKey.of("axe.shield_splitter.lore2", "Bonus Damage vs Blocking");
  public static final LinesKey SHIELD_SPLITTER_LORE = LinesKey.of("axe.shield_splitter.lore", "Shield Disable Duration", "Bonus Damage vs Blocking");

  public static final TextKey TIMBERFALL_LORE1 = TextKey.of("upgrade.axes.timberfall.lore1", "Logs Felled per Swing");
  public static final TextKey TIMBERFALL_LORE2 = TextKey.of("upgrade.axes.timberfall.lore2", "Leaves Cleared per Swing");
  public static final TextKey TIMBERFALL_LORE3 = TextKey.of("upgrade.axes.timberfall.lore3", "Log Search Radius");
  public static final TextKey SUNDERING_CLEAVE_LORE1 = TextKey.of("upgrade.axes.sundering_cleave.lore1", "Arc Width");
  public static final TextKey SUNDERING_CLEAVE_LORE2 = TextKey.of("upgrade.axes.sundering_cleave.lore2", "Arc Damage Share");
  public static final TextKey SUNDERING_CLEAVE_LORE3 = TextKey.of("upgrade.axes.sundering_cleave.lore3", "Armor Shred per Stack");
  public static final TextKey WORLDSPLITTER_LORE1 = TextKey.of("upgrade.axes.worldsplitter.lore1", "Throw Damage Multiplier");
  public static final TextKey WORLDSPLITTER_LORE2 = TextKey.of("upgrade.axes.worldsplitter.lore2", "Smash Damage");
  public static final TextKey WORLDSPLITTER_LORE3 = TextKey.of("upgrade.axes.worldsplitter.lore3", "Smash Radius");
  public static final TextKey WORLDSPLITTER_LORE4 = TextKey.of("upgrade.axes.worldsplitter.lore4", "Cooldown");
  public static final TextKey WORLDSPLITTER_LORE5 = TextKey.of("upgrade.axes.worldsplitter.lore5", "Return Hits");

  private AxeMessages() {
  }

  public static void addTo(MessageCatalog.Builder builder) {
    PREFERENCE_KEYS.forEach(builder::add);
    builder.add(CHOP_NAME);
    builder.add(CHOP_DESCRIPTION);
    builder.add(CHOP_LORE1);
    builder.add(CHOP_LORE2);
    builder.add(CHOP_LORE3);
    builder.add(CHOP_LORE);
    builder.add(LOG_SWAP_NAME);
    builder.add(LOG_SWAP_DESCRIPTION);
    builder.add(LOG_SWAP_LORE1);
    builder.add(LOG_SWAP_LORE);
    builder.add(DROP_TO_INVENTORY_NAME);
    builder.add(GROUND_SMASH_NAME);
    builder.add(GROUND_SMASH_DESCRIPTION);
    builder.add(GROUND_SMASH_LORE1);
    builder.add(GROUND_SMASH_LORE2);
    builder.add(GROUND_SMASH_LORE3);
    builder.add(GROUND_SMASH_LORE4);
    builder.add(GROUND_SMASH_LORE);
    builder.add(LEAF_MINER_NAME);
    builder.add(LEAF_MINER_DESCRIPTION);
    builder.add(LEAF_MINER_LORE1);
    builder.add(LEAF_MINER_LORE2);
    builder.add(LEAF_MINER_LORE3);
    builder.add(LEAF_MINER_LORE);
    builder.add(WOOD_MINER_NAME);
    builder.add(WOOD_MINER_DESCRIPTION);
    builder.add(WOOD_MINER_LORE1);
    builder.add(WOOD_MINER_LORE2);
    builder.add(WOOD_MINER_LORE3);
    builder.add(WOOD_MINER_LORE);
    builder.add(IRIS_FELLER_NAME);
    builder.add(IRIS_FELLER_DESCRIPTION);
    builder.add(IRIS_FELLER_LORE1);
    builder.add(IRIS_FELLER_LORE2);
    builder.add(IRIS_FELLER_LORE3);
    builder.add(IRIS_FELLER_LORE4);
    builder.add(IRIS_FELLER_LORE5);
    builder.add(IRIS_FELLER_LORE6);
    builder.add(IRIS_FELLER_LORE7);
    builder.add(IRIS_FELLER_LORE);
    builder.add(THROWING_AXE_NAME);
    builder.add(THROWING_AXE_DESCRIPTION);
    builder.add(THROWING_AXE_LORE1);
    builder.add(THROWING_AXE_LORE2);
    builder.add(THROWING_AXE_LORE3);
    builder.add(THROWING_AXE_LORE4);
    builder.add(THROWING_AXE_LORE);
    builder.add(SUNDER_NAME);
    builder.add(SUNDER_DESCRIPTION);
    builder.add(SUNDER_LORE1);
    builder.add(SUNDER_LORE2);
    builder.add(SUNDER_LORE3);
    builder.add(SUNDER_LORE4);
    builder.add(SUNDER_LORE);
    builder.add(CLEAVE_NAME);
    builder.add(CLEAVE_DESCRIPTION);
    builder.add(CLEAVE_LORE1);
    builder.add(CLEAVE_LORE2);
    builder.add(CLEAVE_LORE3);
    builder.add(CLEAVE_LORE);
    builder.add(BARK_HIDE_NAME);
    builder.add(BARK_HIDE_DESCRIPTION);
    builder.add(BARK_HIDE_LORE1);
    builder.add(BARK_HIDE_LORE2);
    builder.add(BARK_HIDE_LORE);
    builder.add(SHIELD_SPLITTER_NAME);
    builder.add(SHIELD_SPLITTER_DESCRIPTION);
    builder.add(SHIELD_SPLITTER_LORE1);
    builder.add(SHIELD_SPLITTER_LORE2);
    builder.add(SHIELD_SPLITTER_LORE);
    builder.add(TIMBERFALL_LORE1);
    builder.add(TIMBERFALL_LORE2);
    builder.add(TIMBERFALL_LORE3);
    builder.add(SUNDERING_CLEAVE_LORE1);
    builder.add(SUNDERING_CLEAVE_LORE2);
    builder.add(SUNDERING_CLEAVE_LORE3);
    builder.add(WORLDSPLITTER_LORE1);
    builder.add(WORLDSPLITTER_LORE2);
    builder.add(WORLDSPLITTER_LORE3);
    builder.add(WORLDSPLITTER_LORE4);
    builder.add(WORLDSPLITTER_LORE5);
  }
}
