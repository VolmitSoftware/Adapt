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

package art.arcane.adapt.api.world;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.util.reflect.registries.Materials;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import static org.bukkit.Material.BARREL;
import static org.bukkit.Material.BLACK_SHULKER_BOX;
import static org.bukkit.Material.BLAST_FURNACE;
import static org.bukkit.Material.BLUE_SHULKER_BOX;
import static org.bukkit.Material.BOW;
import static org.bukkit.Material.BROWN_MUSHROOM_BLOCK;
import static org.bukkit.Material.BROWN_SHULKER_BOX;
import static org.bukkit.Material.CHAINMAIL_BOOTS;
import static org.bukkit.Material.CHAINMAIL_CHESTPLATE;
import static org.bukkit.Material.CHAINMAIL_HELMET;
import static org.bukkit.Material.CHAINMAIL_LEGGINGS;
import static org.bukkit.Material.CHEST;
import static org.bukkit.Material.COAL_ORE;
import static org.bukkit.Material.COPPER_ORE;
import static org.bukkit.Material.CROSSBOW;
import static org.bukkit.Material.CYAN_SHULKER_BOX;
import static org.bukkit.Material.DEEPSLATE_COAL_ORE;
import static org.bukkit.Material.DEEPSLATE_COPPER_ORE;
import static org.bukkit.Material.DEEPSLATE_DIAMOND_ORE;
import static org.bukkit.Material.DEEPSLATE_EMERALD_ORE;
import static org.bukkit.Material.DEEPSLATE_GOLD_ORE;
import static org.bukkit.Material.DEEPSLATE_IRON_ORE;
import static org.bukkit.Material.DEEPSLATE_LAPIS_ORE;
import static org.bukkit.Material.DEEPSLATE_REDSTONE_ORE;
import static org.bukkit.Material.DIAMOND_AXE;
import static org.bukkit.Material.DIAMOND_BOOTS;
import static org.bukkit.Material.DIAMOND_CHESTPLATE;
import static org.bukkit.Material.DIAMOND_HELMET;
import static org.bukkit.Material.DIAMOND_HOE;
import static org.bukkit.Material.DIAMOND_LEGGINGS;
import static org.bukkit.Material.DIAMOND_ORE;
import static org.bukkit.Material.DIAMOND_PICKAXE;
import static org.bukkit.Material.DIAMOND_SHOVEL;
import static org.bukkit.Material.DIAMOND_SWORD;
import static org.bukkit.Material.DISPENSER;
import static org.bukkit.Material.DROPPER;
import static org.bukkit.Material.ELYTRA;
import static org.bukkit.Material.EMERALD_ORE;
import static org.bukkit.Material.FURNACE;
import static org.bukkit.Material.GOLDEN_AXE;
import static org.bukkit.Material.GOLDEN_BOOTS;
import static org.bukkit.Material.GOLDEN_CHESTPLATE;
import static org.bukkit.Material.GOLDEN_HELMET;
import static org.bukkit.Material.GOLDEN_HOE;
import static org.bukkit.Material.GOLDEN_LEGGINGS;
import static org.bukkit.Material.GOLDEN_PICKAXE;
import static org.bukkit.Material.GOLDEN_SHOVEL;
import static org.bukkit.Material.GOLDEN_SWORD;
import static org.bukkit.Material.GOLD_ORE;
import static org.bukkit.Material.GRAY_SHULKER_BOX;
import static org.bukkit.Material.GREEN_SHULKER_BOX;
import static org.bukkit.Material.HOPPER;
import static org.bukkit.Material.IRON_AXE;
import static org.bukkit.Material.IRON_BOOTS;
import static org.bukkit.Material.IRON_CHESTPLATE;
import static org.bukkit.Material.IRON_HELMET;
import static org.bukkit.Material.IRON_HOE;
import static org.bukkit.Material.IRON_LEGGINGS;
import static org.bukkit.Material.IRON_ORE;
import static org.bukkit.Material.IRON_PICKAXE;
import static org.bukkit.Material.IRON_SHOVEL;
import static org.bukkit.Material.IRON_SWORD;
import static org.bukkit.Material.LAPIS_ORE;
import static org.bukkit.Material.LEATHER_BOOTS;
import static org.bukkit.Material.LEATHER_CHESTPLATE;
import static org.bukkit.Material.LEATHER_HELMET;
import static org.bukkit.Material.LEATHER_LEGGINGS;
import static org.bukkit.Material.LEGACY_ELYTRA;
import static org.bukkit.Material.LIGHT_BLUE_SHULKER_BOX;
import static org.bukkit.Material.LIGHT_GRAY_SHULKER_BOX;
import static org.bukkit.Material.LIME_SHULKER_BOX;
import static org.bukkit.Material.MAGENTA_SHULKER_BOX;
import static org.bukkit.Material.MANGROVE_ROOTS;
import static org.bukkit.Material.MUDDY_MANGROVE_ROOTS;
import static org.bukkit.Material.MUSHROOM_STEM;
import static org.bukkit.Material.NETHERITE_AXE;
import static org.bukkit.Material.NETHERITE_BOOTS;
import static org.bukkit.Material.NETHERITE_CHESTPLATE;
import static org.bukkit.Material.NETHERITE_HELMET;
import static org.bukkit.Material.NETHERITE_HOE;
import static org.bukkit.Material.NETHERITE_LEGGINGS;
import static org.bukkit.Material.NETHERITE_PICKAXE;
import static org.bukkit.Material.NETHERITE_SHOVEL;
import static org.bukkit.Material.NETHERITE_SWORD;
import static org.bukkit.Material.NETHER_GOLD_ORE;
import static org.bukkit.Material.NETHER_QUARTZ_ORE;
import static org.bukkit.Material.ORANGE_SHULKER_BOX;
import static org.bukkit.Material.PINK_SHULKER_BOX;
import static org.bukkit.Material.PURPLE_SHULKER_BOX;
import static org.bukkit.Material.REDSTONE_ORE;
import static org.bukkit.Material.RED_MUSHROOM_BLOCK;
import static org.bukkit.Material.RED_SHULKER_BOX;
import static org.bukkit.Material.SEA_PICKLE;
import static org.bukkit.Material.SHULKER_BOX;
import static org.bukkit.Material.SMOKER;
import static org.bukkit.Material.STONE_AXE;
import static org.bukkit.Material.STONE_HOE;
import static org.bukkit.Material.STONE_PICKAXE;
import static org.bukkit.Material.STONE_SHOVEL;
import static org.bukkit.Material.STONE_SWORD;
import static org.bukkit.Material.TRAPPED_CHEST;
import static org.bukkit.Material.TRIDENT;
import static org.bukkit.Material.TURTLE_HELMET;
import static org.bukkit.Material.WHITE_SHULKER_BOX;
import static org.bukkit.Material.WOODEN_AXE;
import static org.bukkit.Material.WOODEN_HOE;
import static org.bukkit.Material.WOODEN_PICKAXE;
import static org.bukkit.Material.WOODEN_SHOVEL;
import static org.bukkit.Material.WOODEN_SWORD;
import static org.bukkit.Material.YELLOW_SHULKER_BOX;

public interface AdaptComponent {
  default AdaptServer getServer() {
    return Adapt.instance.getAdaptServer();
  }

  default AdaptPlayer getPlayer(Player p) {
    return getServer().getPlayer(p);
  }

  default boolean isItem(ItemStack is) {
    return is != null && !is.getType().equals(Material.AIR);
  }

  default boolean isTool(ItemStack is) {
    return isAxe(is) || isPickaxe(is) || isHoe(is) || isShovel(is) || isSword(is) || isTrident(is) || isSpear(is) || isMace(is);
  }

  default boolean isMelee(ItemStack is) {
    return isTool(is);
  }

  default boolean isMace(ItemStack is) {
    return isItem(is) && is.getType() == Materials.MACE;
  }

  default boolean isSpear(ItemStack is) {
    if (isItem(is)) {
      return switch (is.getType()) {
        case WOODEN_SPEAR, STONE_SPEAR, COPPER_SPEAR, IRON_SPEAR,
             GOLDEN_SPEAR, DIAMOND_SPEAR, NETHERITE_SPEAR -> true;
        default -> false;
      };
    }

    return false;
  }

  default boolean isShield(ItemStack is) {
    return isItem(is) && is.getType() == Material.SHIELD;
  }

  default boolean isXpBlock(Material material) {
    return material == Material.EXPERIENCE_BOTTLE;
  }

  default boolean isRanged(ItemStack it) {
    if (isItem(it)) {
      return switch (it.getType()) {
        case BOW, CROSSBOW -> true;
        default -> false;
      };
    }

    return false;
  }

  default boolean isSword(ItemStack it) {
    if (isItem(it)) {
      return switch (it.getType()) {
        case DIAMOND_SWORD, GOLDEN_SWORD, IRON_SWORD, NETHERITE_SWORD,
             STONE_SWORD, WOODEN_SWORD, COPPER_SWORD -> true;
        default -> false;
      };
    }

    return false;
  }

  default boolean isTrident(ItemStack it) {
    if (isItem(it)) {
      return switch (it.getType()) {
        case TRIDENT -> true;
        default -> false;
      };
    }

    return false;
  }

  default boolean isAxe(ItemStack it) {
    if (isItem(it)) {
      return switch (it.getType()) {
        case DIAMOND_AXE, GOLDEN_AXE, IRON_AXE, NETHERITE_AXE, STONE_AXE,
             WOODEN_AXE, COPPER_AXE -> true;
        default -> false;
      };
    }

    return false;
  }

  default boolean isPickaxe(ItemStack it) {
    if (isItem(it)) {
      return switch (it.getType()) {
        case DIAMOND_PICKAXE, GOLDEN_PICKAXE, IRON_PICKAXE, NETHERITE_PICKAXE,
             STONE_PICKAXE, WOODEN_PICKAXE, COPPER_PICKAXE -> true;
        default -> false;
      };
    }

    return false;
  }

  default boolean isShovel(ItemStack it) {
    if (isItem(it)) {
      return switch (it.getType()) {
        case DIAMOND_SHOVEL, GOLDEN_SHOVEL, IRON_SHOVEL, NETHERITE_SHOVEL,
             STONE_SHOVEL, WOODEN_SHOVEL, COPPER_SHOVEL -> true;
        default -> false;
      };
    }
    return false;
  }

  default boolean isLog(ItemStack it) {
    return isItem(it) && isLog(it.getType());
  }

  default boolean isLog(Material type) {
    return switch (type) {
      case MUSHROOM_STEM, BROWN_MUSHROOM_BLOCK, RED_MUSHROOM_BLOCK,
           MANGROVE_ROOTS, MUDDY_MANGROVE_ROOTS -> true;
      default -> type.name().endsWith("_LOG") || type.name().endsWith("_WOOD");
    };
  }

  default boolean isLeaves(ItemStack it) {
    return isItem(it) && isLeaves(it.getType());
  }

  default boolean isLeaves(Material type) {
    return type == MANGROVE_ROOTS || type == MUDDY_MANGROVE_ROOTS || type.name().endsWith("_LEAVES");
  }

  default boolean isBoots(ItemStack it) {
    if (isItem(it)) {
      return switch (it.getType()) {
        case DIAMOND_BOOTS, GOLDEN_BOOTS, IRON_BOOTS, NETHERITE_BOOTS,
             CHAINMAIL_BOOTS, LEATHER_BOOTS, COPPER_BOOTS -> true;
        default -> false;
      };
    }

    return false;
  }

  default boolean isHelmet(ItemStack it) {
    if (isItem(it)) {
      return switch (it.getType()) {
        case CHAINMAIL_HELMET, DIAMOND_HELMET, GOLDEN_HELMET, IRON_HELMET,
             LEATHER_HELMET, NETHERITE_HELMET, TURTLE_HELMET, COPPER_HELMET -> true;
        default -> false;
      };
    }

    return false;
  }

  default boolean isLeggings(ItemStack it) {
    if (isItem(it)) {
      return switch (it.getType()) {
        case DIAMOND_LEGGINGS, GOLDEN_LEGGINGS, IRON_LEGGINGS,
             NETHERITE_LEGGINGS, CHAINMAIL_LEGGINGS, LEATHER_LEGGINGS,
             COPPER_LEGGINGS -> true;
        default -> false;
      };
    }

    return false;
  }

  default boolean isChestplate(ItemStack it) {
    if (isItem(it)) {
      return switch (it.getType()) {
        case DIAMOND_CHESTPLATE, GOLDEN_CHESTPLATE, IRON_CHESTPLATE,
             NETHERITE_CHESTPLATE, CHAINMAIL_CHESTPLATE, LEATHER_CHESTPLATE,
             COPPER_CHESTPLATE -> true;
        default -> false;
      };
    }

    return false;
  }

  default boolean isElytra(ItemStack it) {
    if (isItem(it)) {
      return switch (it.getType()) {
        case ELYTRA, LEGACY_ELYTRA -> true;
        default -> false;
      };
    }

    return false;
  }

  default boolean isHoe(ItemStack it) {
    if (isItem(it)) {
      return switch (it.getType()) {
        case DIAMOND_HOE, GOLDEN_HOE, IRON_HOE, NETHERITE_HOE, STONE_HOE,
             WOODEN_HOE, COPPER_HOE -> true;
        default -> false;
      };
    }

    return false;
  }

  default boolean isOre(BlockData b) {
    if (b == null) {
      return false;
    }
    return switch (b.getMaterial()) {
      case COPPER_ORE, DEEPSLATE_COPPER_ORE, COAL_ORE, GOLD_ORE, IRON_ORE,
           DIAMOND_ORE, LAPIS_ORE, EMERALD_ORE, NETHER_QUARTZ_ORE,
           NETHER_GOLD_ORE, REDSTONE_ORE, DEEPSLATE_COAL_ORE,
           DEEPSLATE_IRON_ORE, DEEPSLATE_GOLD_ORE, DEEPSLATE_LAPIS_ORE,
           DEEPSLATE_DIAMOND_ORE, DEEPSLATE_EMERALD_ORE,
           DEEPSLATE_REDSTONE_ORE -> true;
      default -> false;
    };
  }

  default boolean isStorage(BlockData b) {
    if (b == null) {
      return false;
    }
    return switch (b.getMaterial()) {
      case CHEST,
           SMOKER,
           TRAPPED_CHEST,
           SHULKER_BOX,
           WHITE_SHULKER_BOX,
           ORANGE_SHULKER_BOX,
           MAGENTA_SHULKER_BOX,
           LIGHT_BLUE_SHULKER_BOX,
           YELLOW_SHULKER_BOX,
           LIME_SHULKER_BOX,
           PINK_SHULKER_BOX,
           GRAY_SHULKER_BOX,
           LIGHT_GRAY_SHULKER_BOX,
           CYAN_SHULKER_BOX,
           PURPLE_SHULKER_BOX,
           BLUE_SHULKER_BOX,
           BROWN_SHULKER_BOX,
           GREEN_SHULKER_BOX,
           RED_SHULKER_BOX,
           BLACK_SHULKER_BOX,
           BARREL,
           DISPENSER,
           DROPPER,
           FURNACE,
           BLAST_FURNACE,
           HOPPER -> true;
      default -> false;
    };
  }
}
