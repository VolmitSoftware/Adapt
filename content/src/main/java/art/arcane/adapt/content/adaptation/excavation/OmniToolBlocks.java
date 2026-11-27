package art.arcane.adapt.content.adaptation.excavation;

import art.arcane.adapt.platform.value.Id;

import java.util.Set;

final class OmniToolBlocks {
  private static final Set<String> FARMABLE = Set.of(
      "grass_block", "dirt", "coarse_dirt", "rooted_dirt", "wheat", "attached_melon_stem", "attached_pumpkin_stem",
      "melon_stem", "pumpkin_stem", "potatoes", "sweet_berry_bush", "carrots", "beetroots", "dirt_path");
  private static final Set<String> BURNABLE = Set.of(
      "obsidian", "netherrack", "soul_sand", "acacia_leaves", "birch_leaves", "dark_oak_leaves", "jungle_leaves",
      "oak_leaves", "spruce_leaves", "mangrove_leaves", "cherry_leaves", "pale_oak_leaves", "white_wool",
      "orange_wool", "magenta_wool", "light_blue_wool", "yellow_wool", "lime_wool", "pink_wool", "gray_wool",
      "light_gray_wool", "cyan_wool", "purple_wool", "blue_wool", "brown_wool", "green_wool", "red_wool",
      "black_wool");
  private static final Set<String> SWORD_PREFERENCE = Set.of(
      "cobweb", "cave_vines", "cave_vines_plant", "bamboo", "cocoa", "cocoa_beans", "hay_block");
  private static final Set<String> SHOVEL_PREFERENCE = Set.of(
      "clay", "dirt", "farmland", "grass_block", "gravel", "mycelium", "sand", "soul_sand", "soul_soil", "snow",
      "snow_block", "powder_snow", "podzol", "red_sand", "mud", "muddy_mangrove_roots");
  private static final Set<String> AXE_PREFERENCE = Set.of(
      "acacia_fence", "birch_fence", "dark_oak_fence", "jungle_fence", "spruce_fence", "mangrove_fence", "oak_fence",
      "crimson_fence", "warped_fence", "cherry_fence", "bamboo_fence", "pale_oak_fence", "acacia_fence_gate",
      "birch_fence_gate", "dark_oak_fence_gate", "jungle_fence_gate", "spruce_fence_gate", "mangrove_fence_gate",
      "oak_fence_gate", "crimson_fence_gate", "warped_fence_gate", "cherry_fence_gate", "bamboo_fence_gate",
      "pale_oak_fence_gate", "acacia_log", "acacia_wood", "stripped_acacia_log", "birch_log", "birch_wood",
      "stripped_birch_log", "dark_oak_log", "dark_oak_wood", "stripped_dark_oak_log", "jungle_log", "jungle_wood",
      "stripped_jungle_log", "oak_log", "oak_wood", "stripped_oak_log", "spruce_log", "spruce_wood",
      "stripped_spruce_log", "mangrove_log", "mangrove_wood", "stripped_mangrove_log", "crimson_stem",
      "crimson_hyphae", "cherry_log", "cherry_wood", "stripped_cherry_log", "stripped_cherry_wood", "bamboo_block",
      "stripped_bamboo_block", "pale_oak_log", "pale_oak_wood", "stripped_pale_oak_log", "stripped_pale_oak_wood",
      "acacia_sign", "acacia_wall_sign", "acacia_hanging_sign", "acacia_wall_hanging_sign", "birch_sign",
      "birch_wall_sign", "birch_hanging_sign", "birch_wall_hanging_sign", "dark_oak_sign", "dark_oak_wall_sign",
      "dark_oak_hanging_sign", "dark_oak_wall_hanging_sign", "jungle_sign", "jungle_wall_sign",
      "jungle_hanging_sign", "jungle_wall_hanging_sign", "oak_sign", "oak_wall_sign", "oak_hanging_sign",
      "oak_wall_hanging_sign", "spruce_sign", "spruce_wall_sign", "spruce_hanging_sign", "spruce_wall_hanging_sign",
      "mangrove_sign", "mangrove_wall_sign", "mangrove_hanging_sign", "mangrove_wall_hanging_sign", "crimson_sign",
      "crimson_wall_sign", "crimson_hanging_sign", "crimson_wall_hanging_sign", "warped_sign", "warped_wall_sign",
      "warped_hanging_sign", "warped_wall_hanging_sign", "cherry_sign", "cherry_wall_sign", "cherry_hanging_sign",
      "cherry_wall_hanging_sign", "bamboo_sign", "bamboo_wall_sign", "bamboo_hanging_sign",
      "bamboo_wall_hanging_sign", "pale_oak_sign", "pale_oak_wall_sign", "pale_oak_hanging_sign",
      "pale_oak_wall_hanging_sign", "acacia_button", "birch_button", "dark_oak_button", "jungle_button",
      "oak_button", "spruce_button", "mangrove_button", "crimson_button", "warped_button", "cherry_button",
      "bamboo_button", "pale_oak_button", "acacia_door", "birch_door", "dark_oak_door", "jungle_door", "oak_door",
      "spruce_door", "mangrove_door", "crimson_door", "warped_door", "cherry_door", "bamboo_door", "pale_oak_door",
      "acacia_pressure_plate", "birch_pressure_plate", "dark_oak_pressure_plate", "jungle_pressure_plate",
      "oak_pressure_plate", "spruce_pressure_plate", "mangrove_pressure_plate", "crimson_pressure_plate",
      "warped_pressure_plate", "cherry_pressure_plate", "bamboo_pressure_plate", "pale_oak_pressure_plate",
      "acacia_trapdoor", "birch_trapdoor", "dark_oak_trapdoor", "jungle_trapdoor", "oak_trapdoor", "spruce_trapdoor",
      "mangrove_trapdoor", "crimson_trapdoor", "warped_trapdoor", "cherry_trapdoor", "bamboo_trapdoor",
      "pale_oak_trapdoor", "acacia_stairs", "birch_stairs", "dark_oak_stairs", "jungle_stairs", "oak_stairs",
      "spruce_stairs", "mangrove_stairs", "crimson_stairs", "warped_stairs", "cherry_stairs", "bamboo_stairs",
      "bamboo_mosaic_stairs", "pale_oak_stairs", "acacia_slab", "birch_slab", "dark_oak_slab", "jungle_slab",
      "oak_slab", "spruce_slab", "mangrove_slab", "crimson_slab", "warped_slab", "cherry_slab", "bamboo_slab",
      "bamboo_mosaic_slab", "pale_oak_slab", "acacia_planks", "birch_planks", "dark_oak_planks", "jungle_planks",
      "oak_planks", "spruce_planks", "mangrove_planks", "crimson_planks", "warped_planks", "cherry_planks",
      "bamboo_planks", "bamboo_mosaic", "pale_oak_planks", "bee_nest", "dried_kelp_block", "beehive", "chest",
      "crafting_table", "jukebox", "ladder", "loom", "note_block", "barrel", "bookshelf", "cartography_table",
      "fletching_table", "campfire", "soul_campfire", "honeycomb", "lectern", "cocoa", "jack_o_lantern", "pumpkin",
      "melon", "trapped_chest");
  private static final Set<String> TOOLS = Set.of(
      "wooden_pickaxe", "stone_pickaxe", "copper_pickaxe", "iron_pickaxe", "golden_pickaxe", "diamond_pickaxe",
      "netherite_pickaxe", "wooden_axe", "stone_axe", "copper_axe", "iron_axe", "golden_axe", "diamond_axe",
      "netherite_axe", "wooden_sword", "stone_sword", "copper_sword", "iron_sword", "golden_sword", "diamond_sword",
      "netherite_sword", "wooden_shovel", "stone_shovel", "copper_shovel", "iron_shovel", "golden_shovel",
      "diamond_shovel", "netherite_shovel", "wooden_hoe", "stone_hoe", "copper_hoe", "iron_hoe", "golden_hoe",
      "diamond_hoe", "netherite_hoe", "shears");

  private OmniToolBlocks() {
  }

  static boolean isFarmable(Id type) {
    return vanilla(type, FARMABLE);
  }

  static boolean isBurnable(Id type) {
    return vanilla(type, BURNABLE);
  }

  static boolean prefersSword(Id type) {
    return vanilla(type, SWORD_PREFERENCE);
  }

  static boolean prefersShovel(Id type) {
    return vanilla(type, SHOVEL_PREFERENCE);
  }

  static boolean prefersAxe(Id type) {
    return vanilla(type, AXE_PREFERENCE);
  }

  static boolean isTool(Id type) {
    return vanilla(type, TOOLS);
  }

  private static boolean vanilla(Id type, Set<String> paths) {
    return type.isMinecraft() && paths.contains(type.path());
  }
}
