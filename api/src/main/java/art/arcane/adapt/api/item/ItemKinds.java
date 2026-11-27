package art.arcane.adapt.api.item;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockStateRef;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/** What kind of vanilla item, block or material something is, the way built-in adaptations ask. */
@ApiStatus.AvailableSince("1.0.0")
public final class ItemKinds {
  private static final Set<String> SPEARS = Set.of("wooden_spear", "stone_spear", "copper_spear", "iron_spear",
      "golden_spear", "diamond_spear", "netherite_spear");
  private static final Set<String> RANGED = Set.of("bow", "crossbow");
  private static final Set<String> SWORDS = Set.of("diamond_sword", "golden_sword", "iron_sword", "netherite_sword",
      "stone_sword", "wooden_sword", "copper_sword");
  private static final Set<String> AXES = Set.of("diamond_axe", "golden_axe", "iron_axe", "netherite_axe", "stone_axe",
      "wooden_axe", "copper_axe");
  private static final Set<String> PICKAXES = Set.of("diamond_pickaxe", "golden_pickaxe", "iron_pickaxe",
      "netherite_pickaxe", "stone_pickaxe", "wooden_pickaxe", "copper_pickaxe");
  private static final Set<String> SHOVELS = Set.of("diamond_shovel", "golden_shovel", "iron_shovel", "netherite_shovel",
      "stone_shovel", "wooden_shovel", "copper_shovel");
  private static final Set<String> LOG_EXTRAS = Set.of("mushroom_stem", "brown_mushroom_block", "red_mushroom_block",
      "mangrove_roots", "muddy_mangrove_roots");
  private static final Set<String> LEAF_EXTRAS = Set.of("mangrove_roots", "muddy_mangrove_roots");
  private static final Set<String> BOOTS = Set.of("diamond_boots", "golden_boots", "iron_boots", "netherite_boots",
      "chainmail_boots", "leather_boots", "copper_boots");
  private static final Set<String> HELMETS = Set.of("chainmail_helmet", "diamond_helmet", "golden_helmet", "iron_helmet",
      "leather_helmet", "netherite_helmet", "turtle_helmet", "copper_helmet");
  private static final Set<String> LEGGINGS = Set.of("diamond_leggings", "golden_leggings", "iron_leggings",
      "netherite_leggings", "chainmail_leggings", "leather_leggings", "copper_leggings");
  private static final Set<String> CHESTPLATES = Set.of("diamond_chestplate", "golden_chestplate", "iron_chestplate",
      "netherite_chestplate", "chainmail_chestplate", "leather_chestplate", "copper_chestplate");
  private static final Set<String> HOES = Set.of("diamond_hoe", "golden_hoe", "iron_hoe", "netherite_hoe", "stone_hoe",
      "wooden_hoe", "copper_hoe");
  private static final Set<String> ORES = Set.of("copper_ore", "deepslate_copper_ore", "coal_ore", "gold_ore", "iron_ore",
      "diamond_ore", "lapis_ore", "emerald_ore", "nether_quartz_ore", "nether_gold_ore", "redstone_ore",
      "deepslate_coal_ore", "deepslate_iron_ore", "deepslate_gold_ore", "deepslate_lapis_ore",
      "deepslate_diamond_ore", "deepslate_emerald_ore", "deepslate_redstone_ore");
  private static final Set<String> STORAGE = Set.of("chest", "smoker", "trapped_chest", "shulker_box", "white_shulker_box",
      "orange_shulker_box", "magenta_shulker_box", "light_blue_shulker_box", "yellow_shulker_box",
      "lime_shulker_box", "pink_shulker_box", "gray_shulker_box", "light_gray_shulker_box", "cyan_shulker_box",
      "purple_shulker_box", "blue_shulker_box", "brown_shulker_box", "green_shulker_box", "red_shulker_box",
      "black_shulker_box", "barrel", "dispenser", "dropper", "furnace", "blast_furnace", "hopper");


  private ItemKinds() {
  }

  /** Whether the stack is something other than null or air. */
  public static boolean isItem(ItemRef is) {
    return is != null && !is.type().isAir();
  }

  /** Whether the stack is an axe, pickaxe, hoe, shovel, sword, trident, spear or mace. */
  public static boolean isTool(ItemRef is) {
    return isAxe(is) || isPickaxe(is) || isHoe(is) || isShovel(is) || isSword(is) || isTrident(is) || isSpear(is) || isMace(is);
  }

  /** The same as {@link #isTool(ItemRef)}. */
  public static boolean isMelee(ItemRef is) {
    return isTool(is);
  }

  /** Whether the stack is a vanilla mace. */
  public static boolean isMace(ItemRef is) {
    return isItem(is) && vanilla(is.type(), "mace");
  }

  /** Whether the stack is a vanilla spear of any material. */
  public static boolean isSpear(ItemRef is) {
    return isItem(is) && vanilla(is.type(), SPEARS);
  }

  /** Whether the stack is a vanilla shield. */
  public static boolean isShield(ItemRef is) {
    return isItem(is) && vanilla(is.type(), "shield");
  }

  /** Whether the material is {@code minecraft:experience_bottle}. */
  public static boolean isXpBlock(Id material) {
    return vanilla(material, "experience_bottle");
  }

  /** Whether the stack is a vanilla bow or crossbow. */
  public static boolean isRanged(ItemRef it) {
    return isItem(it) && vanilla(it.type(), RANGED);
  }

  /** Whether the stack is a vanilla sword of any material. */
  public static boolean isSword(ItemRef it) {
    return isItem(it) && vanilla(it.type(), SWORDS);
  }

  /** Whether the stack is a vanilla trident. */
  public static boolean isTrident(ItemRef it) {
    return isItem(it) && vanilla(it.type(), "trident");
  }

  /** Whether the stack is a vanilla axe of any material. */
  public static boolean isAxe(ItemRef it) {
    return isItem(it) && vanilla(it.type(), AXES);
  }

  /** Whether the stack is a vanilla pickaxe of any material. */
  public static boolean isPickaxe(ItemRef it) {
    return isItem(it) && vanilla(it.type(), PICKAXES);
  }

  /** Whether the stack is a vanilla shovel of any material. */
  public static boolean isShovel(ItemRef it) {
    return isItem(it) && vanilla(it.type(), SHOVELS);
  }

  /** Whether the stack is a log, as {@link #isLog(Id)} decides. */
  public static boolean isLog(ItemRef it) {
    return isItem(it) && isLog(it.type());
  }

  /**
   * Whether the id ends in {@code _log} or {@code _wood}, in any namespace, or is a vanilla mushroom stem, mushroom
   * block or mangrove roots.
   */
  public static boolean isLog(Id type) {
    if (type == null) {
      return false;
    }
    return vanilla(type, LOG_EXTRAS) || type.path().endsWith("_log") || type.path().endsWith("_wood");
  }

  /** Whether the stack is leaves, as {@link #isLeaves(Id)} decides. */
  public static boolean isLeaves(ItemRef it) {
    return isItem(it) && isLeaves(it.type());
  }

  /** Whether the id ends in {@code _leaves}, in any namespace, or is mangrove roots. */
  public static boolean isLeaves(Id type) {
    if (type == null) {
      return false;
    }
    return vanilla(type, LEAF_EXTRAS) || type.path().endsWith("_leaves");
  }

  /** Whether the stack is vanilla boots of any material. */
  public static boolean isBoots(ItemRef it) {
    return isItem(it) && vanilla(it.type(), BOOTS);
  }

  /** Whether the stack is a vanilla helmet of any material, turtle shell included. */
  public static boolean isHelmet(ItemRef it) {
    return isItem(it) && vanilla(it.type(), HELMETS);
  }

  /** Whether the stack is vanilla leggings of any material. */
  public static boolean isLeggings(ItemRef it) {
    return isItem(it) && vanilla(it.type(), LEGGINGS);
  }

  /** Whether the stack is a vanilla chestplate of any material. */
  public static boolean isChestplate(ItemRef it) {
    return isItem(it) && vanilla(it.type(), CHESTPLATES);
  }

  /** Whether the stack is a vanilla elytra. */
  public static boolean isElytra(ItemRef it) {
    return isItem(it) && vanilla(it.type(), "elytra");
  }

  /** Whether the stack is a vanilla hoe of any material. */
  public static boolean isHoe(ItemRef it) {
    return isItem(it) && vanilla(it.type(), HOES);
  }

  /** Whether the block is a vanilla ore of the overworld or the nether, deepslate variants included. */
  public static boolean isOre(BlockStateRef b) {
    return b != null && vanilla(b.type(), ORES);
  }

  /**
   * Whether the block is a vanilla container: a chest, shulker box, barrel, furnace, blast furnace, smoker,
   * dispenser, dropper or hopper.
   */
  public static boolean isStorage(BlockStateRef b) {
    return b != null && vanilla(b.type(), STORAGE);
  }

  private static boolean vanilla(@Nullable Id type, Set<String> paths) {
    return type != null && type.isMinecraft() && paths.contains(type.path());
  }

  private static boolean vanilla(@Nullable Id type, String path) {
    return type != null && type.isMinecraft() && type.path().equals(path);
  }
}
