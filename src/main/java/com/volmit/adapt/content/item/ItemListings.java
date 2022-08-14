package com.volmit.adapt.content.item;

import lombok.Getter;
import org.bukkit.Material;

import java.util.List;

public class ItemListings {

    @Getter
    public static List<Material> shearList = List.of(
            Material.ACACIA_LEAVES,
            Material.AZALEA_LEAVES,
            Material.BIRCH_LEAVES,
            Material.DARK_OAK_LEAVES,
            Material.JUNGLE_LEAVES,
            Material.OAK_LEAVES,
            Material.SPRUCE_LEAVES,
            Material.MANGROVE_LEAVES
    );

    @Getter
    public static List<Material> smeltOre = List.of(
            Material.NETHER_GOLD_ORE,
            Material.IRON_ORE,
            Material.GOLD_ORE,
            Material.COPPER_ORE,
            Material.DEEPSLATE_IRON_ORE,
            Material.DEEPSLATE_GOLD_ORE,
            Material.DEEPSLATE_COPPER_ORE
    );

    @Getter
    public static List<Material> food = List.of(
            Material.APPLE,
            Material.BAKED_POTATO,
            Material.BEETROOT,
            Material.BEETROOT_SOUP,
            Material.BREAD,
            Material.CARROT,
            Material.CHORUS_FRUIT,
            Material.COOKED_CHICKEN,
            Material.COOKED_COD,
            Material.COOKED_MUTTON,
            Material.COOKED_PORKCHOP,
            Material.COOKED_RABBIT,
            Material.COOKED_SALMON,
            Material.COOKIE,
            Material.DRIED_KELP,
            Material.GOLDEN_APPLE,
            Material.GLOW_BERRIES,
            Material.GOLDEN_CARROT,
            Material.HONEY_BLOCK,
            Material.MELON_SLICE,
            Material.MUSHROOM_STEW,
            Material.POISONOUS_POTATO,
            Material.POTATO,
            Material.PUFFERFISH,
            Material.PUMPKIN_PIE,
            Material.RABBIT_STEW,
            Material.BEEF,
            Material.CHICKEN,
            Material.COD,
            Material.MUTTON,
            Material.PORKCHOP,
            Material.SALMON,
            Material.ROTTEN_FLESH,
            Material.SPIDER_EYE,
            Material.COOKED_BEEF,
            Material.SUSPICIOUS_STEW,
            Material.SWEET_BERRIES,
            Material.TROPICAL_FISH

    );

    @Getter
    public static List<Material> stripList = List.of(
            Material.ACACIA_LOG,
            Material.ACACIA_WOOD,
            Material.STRIPPED_ACACIA_LOG,
            Material.BIRCH_LOG,
            Material.BIRCH_WOOD,
            Material.STRIPPED_BIRCH_LOG,
            Material.DARK_OAK_LOG,
            Material.DARK_OAK_WOOD,
            Material.STRIPPED_DARK_OAK_LOG,
            Material.JUNGLE_LOG,
            Material.JUNGLE_WOOD,
            Material.STRIPPED_JUNGLE_LOG,
            Material.OAK_LOG,
            Material.OAK_WOOD,
            Material.STRIPPED_OAK_LOG,
            Material.SPRUCE_LOG,
            Material.SPRUCE_WOOD,
            Material.STRIPPED_SPRUCE_LOG,
            Material.MANGROVE_LOG,
            Material.MANGROVE_WOOD,
            Material.STRIPPED_MANGROVE_LOG,
            Material.CRIMSON_STEM,
            Material.CRIMSON_HYPHAE
    );


    @Getter
    public static List<Material> ignitable = List.of(
            Material.OBSIDIAN,
            Material.NETHERRACK,
            Material.SOUL_SAND,
            Material.TNT
    );

    @Getter
    public static List<Material> oreList = List.of(
            Material.COAL_ORE,
            Material.DIAMOND_ORE,
            Material.EMERALD_ORE,
            Material.GOLD_ORE,
            Material.IRON_ORE,
            Material.LAPIS_ORE,
            Material.REDSTONE_ORE,
            Material.NETHER_QUARTZ_ORE,
            Material.NETHER_GOLD_ORE,
            Material.COPPER_ORE,
            Material.DEEPSLATE_COAL_ORE,
            Material.DEEPSLATE_COPPER_ORE,
            Material.DEEPSLATE_GOLD_ORE,
            Material.DEEPSLATE_IRON_ORE,
            Material.DEEPSLATE_DIAMOND_ORE,
            Material.DEEPSLATE_LAPIS_ORE,
            Material.DEEPSLATE_EMERALD_ORE,
            Material.DEEPSLATE_REDSTONE_ORE
    );

    @Getter
    public static List<Material> farmable = List.of(
            Material.GRASS_BLOCK,
            Material.DIRT,
            Material.COARSE_DIRT,
            Material.ROOTED_DIRT,
            Material.WHEAT,
            Material.ATTACHED_MELON_STEM,
            Material.ATTACHED_PUMPKIN_STEM,
            Material.MELON_STEM,
            Material.PUMPKIN_STEM,
            Material.POTATOES,
            Material.SWEET_BERRY_BUSH,
            Material.CARROTS,
            Material.BEETROOTS
    );

    @Getter
    public static List<Material> toolPickaxes = List.of(
            Material.WOODEN_PICKAXE,
            Material.STONE_PICKAXE,
            Material.IRON_PICKAXE,
            Material.GOLDEN_PICKAXE,
            Material.DIAMOND_PICKAXE,
            Material.NETHERITE_PICKAXE
    );

    @Getter
    public static List<Material> toolAxes = List.of(
            Material.WOODEN_AXE,
            Material.STONE_AXE,
            Material.IRON_AXE,
            Material.GOLDEN_AXE,
            Material.DIAMOND_AXE,
            Material.NETHERITE_AXE
    );

    @Getter
    public static List<Material> toolSwords = List.of(
            Material.WOODEN_SWORD,
            Material.STONE_SWORD,
            Material.IRON_SWORD,
            Material.GOLDEN_SWORD,
            Material.DIAMOND_SWORD,
            Material.NETHERITE_SWORD
    );

    @Getter
    public static List<Material> toolShovels = List.of(
            Material.WOODEN_SHOVEL,
            Material.STONE_SHOVEL,
            Material.IRON_SHOVEL,
            Material.GOLDEN_SHOVEL,
            Material.DIAMOND_SHOVEL,
            Material.NETHERITE_SHOVEL
    );

    @Getter
    public static List<Material> toolHoes = List.of(
            Material.WOODEN_HOE,
            Material.STONE_HOE,
            Material.IRON_HOE,
            Material.GOLDEN_HOE,
            Material.DIAMOND_HOE,
            Material.NETHERITE_HOE
    );

    @Getter
    public static List<Material> tool = List.of(
            Material.WOODEN_PICKAXE,
            Material.STONE_PICKAXE,
            Material.IRON_PICKAXE,
            Material.GOLDEN_PICKAXE,
            Material.DIAMOND_PICKAXE,
            Material.NETHERITE_PICKAXE,
            //AXE
            Material.WOODEN_AXE,
            Material.STONE_AXE,
            Material.IRON_AXE,
            Material.GOLDEN_AXE,
            Material.DIAMOND_AXE,
            Material.NETHERITE_AXE,
            //SWORD
            Material.WOODEN_SWORD,
            Material.STONE_SWORD,
            Material.IRON_SWORD,
            Material.GOLDEN_SWORD,
            Material.DIAMOND_SWORD,
            Material.NETHERITE_SWORD,
            //SHOVEL
            Material.WOODEN_SHOVEL,
            Material.STONE_SHOVEL,
            Material.IRON_SHOVEL,
            Material.GOLDEN_SHOVEL,
            Material.DIAMOND_SHOVEL,
            Material.NETHERITE_SHOVEL,
            //HOE
            Material.WOODEN_HOE,
            Material.STONE_HOE,
            Material.IRON_HOE,
            Material.GOLDEN_HOE,
            Material.DIAMOND_HOE,
            Material.NETHERITE_HOE,

            //EXTRA
            Material.SHEARS
    );

    @Getter
    public static List<Material> shovel = List.of(
            Material.CLAY,
            Material.DIRT,
            Material.FARMLAND,
            Material.GRASS_BLOCK,
            Material.GRAVEL,
            Material.MYCELIUM,
            Material.SAND,
            Material.SOUL_SAND,
            Material.SNOW,
            Material.SNOW_BLOCK,
            Material.POWDER_SNOW,
            Material.PODZOL,
            Material.RED_SAND
    );

    @Getter
    public static List<Material> axe = List.of(
            Material.COCOA,
            Material.JACK_O_LANTERN,
            Material.PUMPKIN,
            Material.MELON,
            Material.BOOKSHELF,
            Material.CHEST,
            Material.CRAFTING_TABLE,
            Material.LADDER,
            Material.TRAPPED_CHEST,
            //FENCES
            Material.ACACIA_FENCE,
            Material.BIRCH_FENCE,
            Material.DARK_OAK_FENCE,
            Material.JUNGLE_FENCE,
            Material.SPRUCE_FENCE,
            Material.MANGROVE_FENCE,
            Material.OAK_FENCE,
            Material.CRIMSON_FENCE,
            Material.WARPED_FENCE,
            //GATES
            Material.ACACIA_FENCE_GATE,
            Material.BIRCH_FENCE_GATE,
            Material.DARK_OAK_FENCE_GATE,
            Material.JUNGLE_FENCE_GATE,
            Material.SPRUCE_FENCE_GATE,
            Material.MANGROVE_FENCE_GATE,
            Material.OAK_FENCE_GATE,
            Material.CRIMSON_FENCE_GATE,
            Material.WARPED_FENCE_GATE,
            //SIGNS
            Material.ACACIA_SIGN,
            Material.ACACIA_WALL_SIGN,
            Material.BIRCH_SIGN,
            Material.BIRCH_WALL_SIGN,
            Material.DARK_OAK_SIGN,
            Material.DARK_OAK_WALL_SIGN,
            Material.JUNGLE_SIGN,
            Material.JUNGLE_WALL_SIGN,
            Material.OAK_SIGN,
            Material.OAK_WALL_SIGN,
            Material.SPRUCE_SIGN,
            Material.SPRUCE_WALL_SIGN,
            Material.MANGROVE_SIGN,
            Material.MANGROVE_WALL_SIGN,
            //WOODS
            Material.ACACIA_LOG,
            Material.ACACIA_WOOD,
            Material.STRIPPED_ACACIA_LOG,
            Material.BIRCH_LOG,
            Material.BIRCH_WOOD,
            Material.STRIPPED_BIRCH_LOG,
            Material.DARK_OAK_LOG,
            Material.DARK_OAK_WOOD,
            Material.STRIPPED_DARK_OAK_LOG,
            Material.JUNGLE_LOG,
            Material.JUNGLE_WOOD,
            Material.STRIPPED_JUNGLE_LOG,
            Material.OAK_LOG,
            Material.OAK_WOOD,
            Material.STRIPPED_OAK_LOG,
            Material.SPRUCE_LOG,
            Material.SPRUCE_WOOD,
            Material.STRIPPED_SPRUCE_LOG,
            Material.MANGROVE_LOG,
            Material.MANGROVE_WOOD,
            Material.STRIPPED_MANGROVE_LOG,
            Material.CRIMSON_STEM,
            Material.CRIMSON_HYPHAE,
            Material.WARPED_SIGN,

            //WOODEN_BUTTONS
            Material.ACACIA_BUTTON,
            Material.BIRCH_BUTTON,
            Material.DARK_OAK_BUTTON,
            Material.JUNGLE_BUTTON,
            Material.OAK_BUTTON,
            Material.SPRUCE_BUTTON,
            Material.MANGROVE_BUTTON,
            Material.CRIMSON_BUTTON,
            Material.WARPED_BUTTON,
            //WOODEN_DOORS
            Material.ACACIA_DOOR,
            Material.BIRCH_DOOR,
            Material.DARK_OAK_DOOR,
            Material.JUNGLE_DOOR,
            Material.OAK_DOOR,
            Material.SPRUCE_DOOR,
            Material.MANGROVE_DOOR,
            Material.CRIMSON_DOOR,
            Material.WARPED_DOOR,
            //WOODEN_PRESSURE_PLATES
            Material.ACACIA_PRESSURE_PLATE,
            Material.BIRCH_PRESSURE_PLATE,
            Material.DARK_OAK_PRESSURE_PLATE,
            Material.JUNGLE_PRESSURE_PLATE,
            Material.OAK_PRESSURE_PLATE,
            Material.SPRUCE_PRESSURE_PLATE,
            Material.MANGROVE_PRESSURE_PLATE,
            Material.CRIMSON_PRESSURE_PLATE,
            Material.WARPED_PRESSURE_PLATE,
            //WOODEN_TRAPDOORS
            Material.ACACIA_TRAPDOOR,
            Material.BIRCH_TRAPDOOR,
            Material.DARK_OAK_TRAPDOOR,
            Material.JUNGLE_TRAPDOOR,
            Material.OAK_TRAPDOOR,
            Material.SPRUCE_TRAPDOOR,
            Material.MANGROVE_TRAPDOOR,
            Material.CRIMSON_TRAPDOOR,
            Material.WARPED_TRAPDOOR,
            //WOODEN_STAIRS
            Material.ACACIA_STAIRS,
            Material.BIRCH_STAIRS,
            Material.DARK_OAK_STAIRS,
            Material.JUNGLE_STAIRS,
            Material.OAK_STAIRS,
            Material.SPRUCE_STAIRS,
            Material.MANGROVE_STAIRS,
            Material.CRIMSON_STAIRS,
            Material.WARPED_STAIRS,
            //WOODEN_SLABS
            Material.ACACIA_SLAB,
            Material.BIRCH_SLAB,
            Material.DARK_OAK_SLAB,
            Material.JUNGLE_SLAB,
            Material.OAK_SLAB,
            Material.SPRUCE_SLAB,
            Material.MANGROVE_SLAB,
            Material.CRIMSON_SLAB,
            Material.WARPED_SLAB,
            //PLANKS
            Material.ACACIA_PLANKS,
            Material.BIRCH_PLANKS,
            Material.DARK_OAK_PLANKS,
            Material.JUNGLE_PLANKS,
            Material.OAK_PLANKS,
            Material.SPRUCE_PLANKS,
            Material.MANGROVE_PLANKS,
            Material.CRIMSON_PLANKS,
            Material.WARPED_PLANKS);


}
