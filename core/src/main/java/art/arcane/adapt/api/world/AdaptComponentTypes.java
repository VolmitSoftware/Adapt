package art.arcane.adapt.api.world;

import art.arcane.adapt.platform.value.Id;

import java.util.Set;

final class AdaptComponentTypes {
  static final Set<String> SPEARS = Set.of("wooden_spear", "stone_spear", "copper_spear", "iron_spear",
      "golden_spear", "diamond_spear", "netherite_spear");
  static final Set<String> RANGED = Set.of("bow", "crossbow");
  static final Set<String> SWORDS = Set.of("diamond_sword", "golden_sword", "iron_sword", "netherite_sword",
      "stone_sword", "wooden_sword", "copper_sword");
  static final Set<String> AXES = Set.of("diamond_axe", "golden_axe", "iron_axe", "netherite_axe", "stone_axe",
      "wooden_axe", "copper_axe");
  static final Set<String> PICKAXES = Set.of("diamond_pickaxe", "golden_pickaxe", "iron_pickaxe",
      "netherite_pickaxe", "stone_pickaxe", "wooden_pickaxe", "copper_pickaxe");
  static final Set<String> SHOVELS = Set.of("diamond_shovel", "golden_shovel", "iron_shovel", "netherite_shovel",
      "stone_shovel", "wooden_shovel", "copper_shovel");
  static final Set<String> LOG_EXTRAS = Set.of("mushroom_stem", "brown_mushroom_block", "red_mushroom_block",
      "mangrove_roots", "muddy_mangrove_roots");
  static final Set<String> LEAF_EXTRAS = Set.of("mangrove_roots", "muddy_mangrove_roots");
  static final Set<String> BOOTS = Set.of("diamond_boots", "golden_boots", "iron_boots", "netherite_boots",
      "chainmail_boots", "leather_boots", "copper_boots");
  static final Set<String> HELMETS = Set.of("chainmail_helmet", "diamond_helmet", "golden_helmet", "iron_helmet",
      "leather_helmet", "netherite_helmet", "turtle_helmet", "copper_helmet");
  static final Set<String> LEGGINGS = Set.of("diamond_leggings", "golden_leggings", "iron_leggings",
      "netherite_leggings", "chainmail_leggings", "leather_leggings", "copper_leggings");
  static final Set<String> CHESTPLATES = Set.of("diamond_chestplate", "golden_chestplate", "iron_chestplate",
      "netherite_chestplate", "chainmail_chestplate", "leather_chestplate", "copper_chestplate");
  static final Set<String> HOES = Set.of("diamond_hoe", "golden_hoe", "iron_hoe", "netherite_hoe", "stone_hoe",
      "wooden_hoe", "copper_hoe");
  static final Set<String> ORES = Set.of("copper_ore", "deepslate_copper_ore", "coal_ore", "gold_ore", "iron_ore",
      "diamond_ore", "lapis_ore", "emerald_ore", "nether_quartz_ore", "nether_gold_ore", "redstone_ore",
      "deepslate_coal_ore", "deepslate_iron_ore", "deepslate_gold_ore", "deepslate_lapis_ore",
      "deepslate_diamond_ore", "deepslate_emerald_ore", "deepslate_redstone_ore");
  static final Set<String> STORAGE = Set.of("chest", "smoker", "trapped_chest", "shulker_box", "white_shulker_box",
      "orange_shulker_box", "magenta_shulker_box", "light_blue_shulker_box", "yellow_shulker_box",
      "lime_shulker_box", "pink_shulker_box", "gray_shulker_box", "light_gray_shulker_box", "cyan_shulker_box",
      "purple_shulker_box", "blue_shulker_box", "brown_shulker_box", "green_shulker_box", "red_shulker_box",
      "black_shulker_box", "barrel", "dispenser", "dropper", "furnace", "blast_furnace", "hopper");

  private AdaptComponentTypes() {
  }

  static boolean vanilla(Id type, Set<String> paths) {
    return type != null && type.isMinecraft() && paths.contains(type.path());
  }

  static boolean vanilla(Id type, String path) {
    return type != null && type.isMinecraft() && type.path().equals(path);
  }
}
