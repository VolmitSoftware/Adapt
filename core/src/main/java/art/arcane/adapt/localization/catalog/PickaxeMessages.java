package art.arcane.adapt.localization.catalog;

import art.arcane.volmlib.util.localization.LinesKey;
import art.arcane.volmlib.util.localization.MessageCatalog;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.List;

public final class PickaxeMessages {
  public static final TextKey LOW_DURABILITY = TextKey.of("pickaxe.preferences.low_durability", "Your pickaxe has less than 10% durability remaining.");
  public static final TextKey PREFERENCE_BYPASS = TextKey.of("pickaxe.preferences.bypass", "Sneak to bypass");
  public static final TextKey PREFERENCE_REQUIRE_SNEAK = TextKey.of("pickaxe.preferences.require_sneak", "Require sneaking");
  public static final TextKey PREFERENCE_NOTICE = TextKey.of("pickaxe.preferences.notice", "Low durability notice");
  public static final TextKey PREFERENCE_TRIGGER = TextKey.of("pickaxe.preferences.trigger", "Activation gesture");
  public static final TextKey PREFERENCE_SNEAK = TextKey.of("pickaxe.preferences.sneak", "While sneaking");
  public static final TextKey PREFERENCE_NOT_SNEAKING = TextKey.of("pickaxe.preferences.not_sneaking", "While not sneaking");
  public static final TextKey PREFERENCE_ALWAYS = TextKey.of("pickaxe.preferences.always", "Always");
  public static final TextKey PREFERENCE_MATERIALS = TextKey.of("pickaxe.preferences.materials", "Eligible materials");
  public static final TextKey PREFERENCE_ALL = TextKey.of("pickaxe.preferences.all", "All permitted");
  public static final TextKey PREFERENCE_ORES = TextKey.of("pickaxe.preferences.ores", "Ores and raw minerals");
  public static final TextKey PREFERENCE_IRON = TextKey.of("pickaxe.preferences.iron", "Iron only");
  public static final TextKey PREFERENCE_GOLD = TextKey.of("pickaxe.preferences.gold", "Gold only");
  public static final TextKey PREFERENCE_COPPER = TextKey.of("pickaxe.preferences.copper", "Copper only");
  public static final TextKey PREFERENCE_GEMS = TextKey.of("pickaxe.preferences.gems", "Diamond and emerald");
  public static final TextKey PREFERENCE_STONE = TextKey.of("pickaxe.preferences.stone", "Stone and deepslate");
  public static final TextKey PREFERENCE_RESERVE = TextKey.of("pickaxe.preferences.reserve", "Durability remaining after use");
  public static final TextKey PREFERENCE_NO_RESERVE = TextKey.of("pickaxe.preferences.no_reserve", "No extra reserve");
  public static final TextKey PREFERENCE_TENTH = TextKey.of("pickaxe.preferences.tenth", "Keep 10%");
  public static final TextKey PREFERENCE_QUARTER = TextKey.of("pickaxe.preferences.quarter", "Keep 25%");
  public static final TextKey PREFERENCE_HALF = TextKey.of("pickaxe.preferences.half", "Keep 50%");
  public static final TextKey PREFERENCE_SIZE = TextKey.of("pickaxe.preferences.size", "Tunnel size limit");
  public static final TextKey PREFERENCE_FULL = TextKey.of("pickaxe.preferences.full", "Full learned size");
  public static final TextKey PREFERENCE_THREE_BY_TWO = TextKey.of("pickaxe.preferences.three_by_two", "At most 3 by 2");
  public static final TextKey PREFERENCE_ONE_BY_TWO = TextKey.of("pickaxe.preferences.one_by_two", "At most 1 by 2");
  public static final TextKey PREFERENCE_GLOW = TextKey.of("pickaxe.preferences.glow", "Ore outline color");
  public static final TextKey PREFERENCE_ORE_COLOR = TextKey.of("pickaxe.preferences.ore_color", "Match ore");
  public static final TextKey PREFERENCE_WHITE = TextKey.of("pickaxe.preferences.white", "White");
  public static final TextKey PREFERENCE_CYAN = TextKey.of("pickaxe.preferences.cyan", "Cyan");
  public static final TextKey PREFERENCE_GOLD_COLOR = TextKey.of("pickaxe.preferences.gold_color", "Gold");
  private static final List<TextKey> PREFERENCE_KEYS = List.of(
      LOW_DURABILITY,
      PREFERENCE_BYPASS,
      PREFERENCE_REQUIRE_SNEAK,
      PREFERENCE_NOTICE,
      PREFERENCE_TRIGGER,
      PREFERENCE_SNEAK,
      PREFERENCE_NOT_SNEAKING,
      PREFERENCE_ALWAYS,
      PREFERENCE_MATERIALS,
      PREFERENCE_ALL,
      PREFERENCE_ORES,
      PREFERENCE_IRON,
      PREFERENCE_GOLD,
      PREFERENCE_COPPER,
      PREFERENCE_GEMS,
      PREFERENCE_STONE,
      PREFERENCE_RESERVE,
      PREFERENCE_NO_RESERVE,
      PREFERENCE_TENTH,
      PREFERENCE_QUARTER,
      PREFERENCE_HALF,
      PREFERENCE_SIZE,
      PREFERENCE_FULL,
      PREFERENCE_THREE_BY_TWO,
      PREFERENCE_ONE_BY_TWO,
      PREFERENCE_GLOW,
      PREFERENCE_ORE_COLOR,
      PREFERENCE_WHITE,
      PREFERENCE_CYAN,
      PREFERENCE_GOLD_COLOR);
  public static final TextKey AUTO_SMELT_NAME = TextKey.of("pickaxe.auto_smelt.name", "Autosmelt");
  public static final TextKey AUTO_SMELT_DESCRIPTION = TextKey.of("pickaxe.auto_smelt.description", "Allows you to smelt mined Vanilla ores");
  public static final TextKey AUTO_SMELT_LORE1 = TextKey.of("pickaxe.auto_smelt.lore1", "Ores that can be smelted are smelted automatically");
  public static final TextKey AUTO_SMELT_LORE2 = TextKey.of("pickaxe.auto_smelt.lore2", "% chance for an extra");
  public static final LinesKey AUTO_SMELT_LORE = LinesKey.of("pickaxe.auto_smelt.lore", "Ores that can be smelted are smelted automatically", "% chance for an extra");
  public static final TextKey CHISEL_NAME = TextKey.of("pickaxe.chisel.name", "Ore Chisel");
  public static final TextKey CHISEL_DESCRIPTION = TextKey.of("pickaxe.chisel.description", "Right Click Ores to Chisel more ore out of them, at a severe durability cost.");
  public static final TextKey CHISEL_LORE1 = TextKey.of("pickaxe.chisel.lore1", "Chance to Drop");
  public static final TextKey CHISEL_LORE2 = TextKey.of("pickaxe.chisel.lore2", "Tool Wear");
  public static final LinesKey CHISEL_LORE = LinesKey.of("pickaxe.chisel.lore", "Chance to Drop", "Tool Wear");
  public static final TextKey DROP_TO_INVENTORY_NAME = TextKey.of("pickaxe.drop_to_inventory.name", "Pickaxe Drop to Inventory");
  public static final TextKey DROP_TO_INVENTORY_DESCRIPTION = TextKey.of("pickaxe.drop_to_inventory.description", "Blocks you break send their drops straight into your inventory.");
  public static final TextKey DROP_TO_INVENTORY_LORE1 = TextKey.of("pickaxe.drop_to_inventory.lore1", "Whenever an item is dropped from a block you break it goes into your inventory if it can.");
  public static final LinesKey DROP_TO_INVENTORY_LORE = LinesKey.of("pickaxe.drop_to_inventory.lore", "Whenever an item is dropped from a block you break it goes into your inventory if it can.");
  public static final TextKey SILK_SPAWNER_NAME = TextKey.of("pickaxe.silk_spawner.name", "Pickaxe Silk-Spawner");
  public static final TextKey SILK_SPAWNER_DESCRIPTION = TextKey.of("pickaxe.silk_spawner.description", "Makes Spawners drop when broken");
  public static final TextKey SILK_SPAWNER_LORE1 = TextKey.of("pickaxe.silk_spawner.lore1", "Makes Spawners breakable with silk touch.");
  public static final TextKey SILK_SPAWNER_LORE2 = TextKey.of("pickaxe.silk_spawner.lore2", "Makes Spawners breakable while sneaking.");
  public static final LinesKey SILK_SPAWNER_LORE = LinesKey.of("pickaxe.silk_spawner.lore", "Makes Spawners breakable with silk touch.", "Makes Spawners breakable while sneaking.");
  public static final TextKey VEIN_MINER_NAME = TextKey.of("pickaxe.vein_miner.name", "Veinminer");
  public static final TextKey VEIN_MINER_DESCRIPTION = TextKey.of("pickaxe.vein_miner.description", "Allows you to break blocks in a Vein/Cluster of Vanilla ores");
  public static final TextKey VEIN_MINER_LORE1 = TextKey.of("pickaxe.vein_miner.lore1", "Sneak, and mine ORES");
  public static final TextKey VEIN_MINER_LORE2 = TextKey.of("pickaxe.vein_miner.lore2", "range of vein-mining");
  public static final TextKey VEIN_MINER_LORE3 = TextKey.of("pickaxe.vein_miner.lore3", "This skill does NOT group all drops together!");
  public static final LinesKey VEIN_MINER_LORE = LinesKey.of("pickaxe.vein_miner.lore", "Sneak, and mine ORES", "range of vein-mining", "This skill does NOT group all drops together!");
  public static final TextKey QUARRY_SENSE_NAME = TextKey.of("pickaxe.quarry_sense.name", "Quarry Sense");
  public static final TextKey QUARRY_SENSE_DESCRIPTION = TextKey.of("pickaxe.quarry_sense.description", "Sneak-right-click a block with an iron+ pickaxe to reveal nearby ores as private glowing block displays.");
  public static final TextKey QUARRY_SENSE_LORE1 = TextKey.of("pickaxe.quarry_sense.lore1", "Ore Scan Radius");
  public static final TextKey QUARRY_SENSE_LORE2 = TextKey.of("pickaxe.quarry_sense.lore2", "Durability Cost (% of Max Durability)");
  public static final TextKey QUARRY_SENSE_LORE3 = TextKey.of("pickaxe.quarry_sense.lore3", "Sense Cooldown");
  public static final LinesKey QUARRY_SENSE_LORE = LinesKey.of("pickaxe.quarry_sense.lore", "Ore Scan Radius", "Durability Cost (% of Max Durability)", "Sense Cooldown");
  public static final TextKey TUNNEL_BORE_NAME = TextKey.of("pickaxe.tunnel_bore.name", "Tunnel Bore");
  public static final TextKey TUNNEL_BORE_DESCRIPTION = TextKey.of("pickaxe.tunnel_bore.description", "Sneak and mine stone-type blocks to bore out a whole tunnel face at once");
  public static final TextKey TUNNEL_BORE_LORE1 = TextKey.of("pickaxe.tunnel_bore.lore1", "Sneak, and mine STONE");
  public static final TextKey TUNNEL_BORE_LORE2 = TextKey.of("pickaxe.tunnel_bore.lore2", "tunnel face bored per block");
  public static final TextKey TUNNEL_BORE_LORE3 = TextKey.of("pickaxe.tunnel_bore.lore3", "extra durability per bonus block");
  public static final LinesKey TUNNEL_BORE_LORE = LinesKey.of("pickaxe.tunnel_bore.lore", "Sneak, and mine STONE", "tunnel face bored per block", "extra durability per bonus block");
  public static final TextKey DEEP_CORE_NAME = TextKey.of("pickaxe.deep_core.name", "Deep Core");
  public static final TextKey DEEP_CORE_DESCRIPTION = TextKey.of("pickaxe.deep_core.description", "Mining deepslate grants Haste so it digs like normal stone");
  public static final TextKey DEEP_CORE_LORE1 = TextKey.of("pickaxe.deep_core.lore1", "Mine DEEPSLATE to gain Haste");
  public static final TextKey DEEP_CORE_LORE2 = TextKey.of("pickaxe.deep_core.lore2", "Haste level while mining deepslate");
  public static final LinesKey DEEP_CORE_LORE = LinesKey.of("pickaxe.deep_core.lore", "Mine DEEPSLATE to gain Haste", "Haste level while mining deepslate");
  public static final TextKey OBSIDIAN_RUSH_NAME = TextKey.of("pickaxe.obsidian_rush.name", "Obsidian Rush");
  public static final TextKey OBSIDIAN_RUSH_DESCRIPTION = TextKey.of("pickaxe.obsidian_rush.description", "Mining obsidian with a diamond or netherite pickaxe grants a strong Haste burst");
  public static final TextKey OBSIDIAN_RUSH_LORE1 = TextKey.of("pickaxe.obsidian_rush.lore1", "Mine OBSIDIAN with a diamond+ pickaxe");
  public static final TextKey OBSIDIAN_RUSH_LORE2 = TextKey.of("pickaxe.obsidian_rush.lore2", "Haste level while mining obsidian");
  public static final TextKey OBSIDIAN_RUSH_LORE3 = TextKey.of("pickaxe.obsidian_rush.lore3", "Also works on crying obsidian!");
  public static final LinesKey OBSIDIAN_RUSH_LORE = LinesKey.of("pickaxe.obsidian_rush.lore", "Mine OBSIDIAN with a diamond+ pickaxe", "Haste level while mining obsidian", "Also works on crying obsidian!");
  public static final TextKey UNBREAKABLE_PACT_NAME = TextKey.of("pickaxe.unbreakable_pact.name", "Unbreakable Pact");
  public static final TextKey UNBREAKABLE_PACT_DESCRIPTION = TextKey.of("pickaxe.unbreakable_pact.description", "Your pickaxe refuses to break, surviving at 1 durability instead");
  public static final TextKey UNBREAKABLE_PACT_LORE1 = TextKey.of("pickaxe.unbreakable_pact.lore1", "Pickaxes never break, stopping at 1 durability");
  public static final TextKey UNBREAKABLE_PACT_LORE2 = TextKey.of("pickaxe.unbreakable_pact.lore2", "chance to ignore durability loss entirely");
  public static final LinesKey UNBREAKABLE_PACT_LORE = LinesKey.of("pickaxe.unbreakable_pact.lore", "Pickaxes never break, stopping at 1 durability", "chance to ignore durability loss entirely");
  public static final TextKey REPAIR_RHYTHM_NAME = TextKey.of("pickaxe.repair_rhythm.name", "Repair Rhythm");
  public static final TextKey REPAIR_RHYTHM_DESCRIPTION = TextKey.of("pickaxe.repair_rhythm.description", "Sustained mining has a chance to restore durability to your pickaxe");
  public static final TextKey REPAIR_RHYTHM_LORE1 = TextKey.of("pickaxe.repair_rhythm.lore1", "Each broken block can restore 1-2 durability");
  public static final TextKey REPAIR_RHYTHM_LORE2 = TextKey.of("pickaxe.repair_rhythm.lore2", "chance to repair per broken block");
  public static final LinesKey REPAIR_RHYTHM_LORE = LinesKey.of("pickaxe.repair_rhythm.lore", "Each broken block can restore 1-2 durability", "chance to repair per broken block");
  public static final TextKey GEM_POLISH_NAME = TextKey.of("pickaxe.gem_polish.name", "Trophy Polish");
  public static final TextKey GEM_POLISH_DESCRIPTION = TextKey.of("pickaxe.gem_polish.description", "Mining naturally generated heads, skulls, and dragon eggs grants bounded vanilla XP");
  public static final TextKey GEM_POLISH_LORE1 = TextKey.of("pickaxe.gem_polish.lore1", "Mine natural heads, skulls, or dragon eggs");
  public static final TextKey GEM_POLISH_LORE2 = TextKey.of("pickaxe.gem_polish.lore2", "Player-placed trophies grant no XP");
  public static final TextKey GEM_POLISH_LORE3 = TextKey.of("pickaxe.gem_polish.lore3", "Vanilla XP per trophy");
  public static final LinesKey GEM_POLISH_LORE = LinesKey.of("pickaxe.gem_polish.lore", "Mine natural heads, skulls, or dragon eggs", "Player-placed trophies grant no XP", "Vanilla XP per trophy");
  public static final TextKey STONE_SKIN_NAME = TextKey.of("pickaxe.stone_skin.name", "Stone Skin");
  public static final TextKey STONE_SKIN_DESCRIPTION = TextKey.of("pickaxe.stone_skin.description", "Breaking stone-type blocks builds short-lived stacking damage resistance");
  public static final TextKey STONE_SKIN_LORE1 = TextKey.of("pickaxe.stone_skin.lore1", "Mine stone to build Stone Skin stacks");
  public static final TextKey STONE_SKIN_LORE2 = TextKey.of("pickaxe.stone_skin.lore2", "maximum Resistance level");
  public static final LinesKey STONE_SKIN_LORE = LinesKey.of("pickaxe.stone_skin.lore", "Mine stone to build Stone Skin stacks", "maximum Resistance level");

  public static final TextKey MOTHERLODE_LORE1 = TextKey.of("upgrade.pickaxe.motherlode.lore1", "Vein Radius");
  public static final TextKey MOTHERLODE_LORE2 = TextKey.of("upgrade.pickaxe.motherlode.lore2", "Most Blocks per Vein");
  public static final TextKey MOTHERLODE_LORE3 = TextKey.of("upgrade.pickaxe.motherlode.lore3", "Bonus Ores per Vein");
  public static final TextKey EARTHSHAKER_LORE1 = TextKey.of("upgrade.pickaxe.earthshaker.lore1", "Bored Face");
  public static final TextKey EARTHSHAKER_LORE2 = TextKey.of("upgrade.pickaxe.earthshaker.lore2", "Share of Stone Speed on Deepslate and Obsidian");
  public static final TextKey EARTHSHAKER_LORE3 = TextKey.of("upgrade.pickaxe.earthshaker.lore3", "Durability per Bored Block");
  public static final TextKey ETERNAL_PICK_LORE1 = TextKey.of("upgrade.pickaxe.eternal_pick.lore1", "Repair Chance per Block");
  public static final TextKey ETERNAL_PICK_LORE2 = TextKey.of("upgrade.pickaxe.eternal_pick.lore2", "Durability per Repair");
  public static final TextKey ETERNAL_PICK_LORE3 = TextKey.of("upgrade.pickaxe.eternal_pick.lore3", "Highest Resistance Tier");
  public static final TextKey MOTHERLODE_LORE4 = TextKey.of("upgrade.pickaxe.motherlode.lore4", "Durability per Kept Spawner");
  public static final TextKey MOTHERLODE_SPAWNER_MOB = TextKey.of("upgrade.pickaxe.motherlode.spawner_mob", "Mob: {mob}");
  public static final TextKey MOTHERLODE_SPAWNER_WORN = TextKey.of("upgrade.pickaxe.motherlode.spawner_worn", "Needs {durability} pickaxe durability");
  public static final TextKey EARTHSHAKER_LORE4 = TextKey.of("upgrade.pickaxe.earthshaker.lore4", "Charge Radius");
  public static final TextKey PROSPECTORS_CHARGE_NAME = TextKey.of("tool.pickaxe.prospectors_charge.name", "Prospector's Charge");
  public static final TextKey PROSPECTORS_CHARGE_STONE_DROP = TextKey.of("tool.pickaxe.prospectors_charge.stone_drop", "Stone: Drop");
  public static final TextKey PROSPECTORS_CHARGE_STONE_DISCARD = TextKey.of("tool.pickaxe.prospectors_charge.stone_discard", "Stone: Discard");
  public static final TextKey PROSPECTORS_CHARGE_BUSY = TextKey.of("tool.pickaxe.prospectors_charge.busy", "A charge is already set");
  public static final TextKey PROSPECTORS_CHARGE_NO_PICKAXE = TextKey.of("tool.pickaxe.prospectors_charge.no_pickaxe", "No pickaxe in your hotbar");
  public static final TextKey PROSPECTORS_CHARGE_STONE_ONLY = TextKey.of("tool.pickaxe.prospectors_charge.stone_only", "Stone only");
  public static final TextKey PROSPECTORS_CHARGE_OPENED = TextKey.of("tool.pickaxe.prospectors_charge.opened", "{blocks} blocks opened");
  public static final TextKey PROSPECTORS_CHARGE_WORN = TextKey.of("tool.pickaxe.prospectors_charge.worn", "Pickaxe worn out");
  public static final TextKey PROSPECTORS_CHARGE_PROTECTED = TextKey.of("tool.pickaxe.prospectors_charge.protected", "Stopped at protected ground");

  private PickaxeMessages() {
  }

  public static void addTo(MessageCatalog.Builder builder) {
    PREFERENCE_KEYS.forEach(builder::add);
    builder.add(AUTO_SMELT_NAME);
    builder.add(AUTO_SMELT_DESCRIPTION);
    builder.add(AUTO_SMELT_LORE1);
    builder.add(AUTO_SMELT_LORE2);
    builder.add(AUTO_SMELT_LORE);
    builder.add(CHISEL_NAME);
    builder.add(CHISEL_DESCRIPTION);
    builder.add(CHISEL_LORE1);
    builder.add(CHISEL_LORE2);
    builder.add(CHISEL_LORE);
    builder.add(DROP_TO_INVENTORY_NAME);
    builder.add(DROP_TO_INVENTORY_DESCRIPTION);
    builder.add(DROP_TO_INVENTORY_LORE1);
    builder.add(DROP_TO_INVENTORY_LORE);
    builder.add(SILK_SPAWNER_NAME);
    builder.add(SILK_SPAWNER_DESCRIPTION);
    builder.add(SILK_SPAWNER_LORE1);
    builder.add(SILK_SPAWNER_LORE2);
    builder.add(SILK_SPAWNER_LORE);
    builder.add(VEIN_MINER_NAME);
    builder.add(VEIN_MINER_DESCRIPTION);
    builder.add(VEIN_MINER_LORE1);
    builder.add(VEIN_MINER_LORE2);
    builder.add(VEIN_MINER_LORE3);
    builder.add(VEIN_MINER_LORE);
    builder.add(QUARRY_SENSE_NAME);
    builder.add(QUARRY_SENSE_DESCRIPTION);
    builder.add(QUARRY_SENSE_LORE1);
    builder.add(QUARRY_SENSE_LORE2);
    builder.add(QUARRY_SENSE_LORE3);
    builder.add(QUARRY_SENSE_LORE);
    builder.add(TUNNEL_BORE_NAME);
    builder.add(TUNNEL_BORE_DESCRIPTION);
    builder.add(TUNNEL_BORE_LORE1);
    builder.add(TUNNEL_BORE_LORE2);
    builder.add(TUNNEL_BORE_LORE3);
    builder.add(TUNNEL_BORE_LORE);
    builder.add(DEEP_CORE_NAME);
    builder.add(DEEP_CORE_DESCRIPTION);
    builder.add(DEEP_CORE_LORE1);
    builder.add(DEEP_CORE_LORE2);
    builder.add(DEEP_CORE_LORE);
    builder.add(OBSIDIAN_RUSH_NAME);
    builder.add(OBSIDIAN_RUSH_DESCRIPTION);
    builder.add(OBSIDIAN_RUSH_LORE1);
    builder.add(OBSIDIAN_RUSH_LORE2);
    builder.add(OBSIDIAN_RUSH_LORE3);
    builder.add(OBSIDIAN_RUSH_LORE);
    builder.add(UNBREAKABLE_PACT_NAME);
    builder.add(UNBREAKABLE_PACT_DESCRIPTION);
    builder.add(UNBREAKABLE_PACT_LORE1);
    builder.add(UNBREAKABLE_PACT_LORE2);
    builder.add(UNBREAKABLE_PACT_LORE);
    builder.add(REPAIR_RHYTHM_NAME);
    builder.add(REPAIR_RHYTHM_DESCRIPTION);
    builder.add(REPAIR_RHYTHM_LORE1);
    builder.add(REPAIR_RHYTHM_LORE2);
    builder.add(REPAIR_RHYTHM_LORE);
    builder.add(GEM_POLISH_NAME);
    builder.add(GEM_POLISH_DESCRIPTION);
    builder.add(GEM_POLISH_LORE1);
    builder.add(GEM_POLISH_LORE2);
    builder.add(GEM_POLISH_LORE3);
    builder.add(GEM_POLISH_LORE);
    builder.add(STONE_SKIN_NAME);
    builder.add(STONE_SKIN_DESCRIPTION);
    builder.add(STONE_SKIN_LORE1);
    builder.add(STONE_SKIN_LORE2);
    builder.add(STONE_SKIN_LORE);
    builder.add(MOTHERLODE_LORE1);
    builder.add(MOTHERLODE_LORE2);
    builder.add(MOTHERLODE_LORE3);
    builder.add(EARTHSHAKER_LORE1);
    builder.add(EARTHSHAKER_LORE2);
    builder.add(EARTHSHAKER_LORE3);
    builder.add(ETERNAL_PICK_LORE1);
    builder.add(ETERNAL_PICK_LORE2);
    builder.add(ETERNAL_PICK_LORE3);
    builder.add(MOTHERLODE_LORE4);
    builder.add(MOTHERLODE_SPAWNER_MOB);
    builder.add(MOTHERLODE_SPAWNER_WORN);
    builder.add(EARTHSHAKER_LORE4);
    builder.add(PROSPECTORS_CHARGE_NAME);
    builder.add(PROSPECTORS_CHARGE_STONE_DROP);
    builder.add(PROSPECTORS_CHARGE_STONE_DISCARD);
    builder.add(PROSPECTORS_CHARGE_BUSY);
    builder.add(PROSPECTORS_CHARGE_NO_PICKAXE);
    builder.add(PROSPECTORS_CHARGE_STONE_ONLY);
    builder.add(PROSPECTORS_CHARGE_OPENED);
    builder.add(PROSPECTORS_CHARGE_WORN);
    builder.add(PROSPECTORS_CHARGE_PROTECTED);
  }
}
