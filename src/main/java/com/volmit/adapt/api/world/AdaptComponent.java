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

package com.volmit.adapt.api.world;

import com.volmit.adapt.Adapt;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

import static org.bukkit.Material.*;

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
        return isAxe(is) || isPickaxe(is) || isHoe(is) || isShovel(is) || isSword(is) || isTrident(is);
    }

    default boolean isMelee(ItemStack is) {
        return isTool(is);
    }

    default boolean isSword(ItemStack it) {
        if (isItem(it)) {
            return switch (it.getType()) {
                case DIAMOND_SWORD, GOLDEN_SWORD, IRON_SWORD, NETHERITE_SWORD, STONE_SWORD, WOODEN_SWORD -> true;
                default -> false;
            };
        }

        return false;
    }

    default boolean isTrident(ItemStack it) {
        if (isItem(it)) {
            return switch (it.getType()) {
                case TRIDENT, SEA_PICKLE -> true;
                default -> false;
            };
        }

        return false;
    }

    default boolean isAxe(ItemStack it) {
        if (isItem(it)) {
            return switch (it.getType()) {
                case DIAMOND_AXE, GOLDEN_AXE, IRON_AXE, NETHERITE_AXE, STONE_AXE, WOODEN_AXE -> true;
                default -> false;
            };
        }

        return false;
    }

    default boolean isPickaxe(ItemStack it) {
        if (isItem(it)) {
            return switch (it.getType()) {
                case DIAMOND_PICKAXE, GOLDEN_PICKAXE, IRON_PICKAXE, NETHERITE_PICKAXE, STONE_PICKAXE, WOODEN_PICKAXE ->
                        true;
                default -> false;
            };
        }

        return false;
    }

    default boolean isShovel(ItemStack it) {
        if (isItem(it)) {
            return switch (it.getType()) {
                case DIAMOND_SHOVEL, GOLDEN_SHOVEL, IRON_SHOVEL, NETHERITE_SHOVEL, STONE_SHOVEL, WOODEN_SHOVEL -> true;
                default -> false;
            };
        }
        return false;
    }

    default boolean isLog(ItemStack it) {
        if (isItem(it)) {
            return List.of(MUSHROOM_STEM, BROWN_MUSHROOM_BLOCK, RED_MUSHROOM_BLOCK, MANGROVE_ROOTS, MUDDY_MANGROVE_ROOTS).contains(it.getType())
                    || it.getType().name().endsWith("_LOG")
                    || it.getType().name().endsWith("_WOOD");
        }

        return false;
    }

    default boolean isLeaves(ItemStack it) {
        if (isItem(it)) {
            return List.of(Material.MANGROVE_ROOTS, Material.MUDDY_MANGROVE_ROOTS).contains(it.getType())
                    || it.getType().name().endsWith("_LEAVES");
        }

        return false;
    }

    default boolean isChestplate(ItemStack it) {
        if (isItem(it)) {
            return switch (it.getType()) {
                case DIAMOND_CHESTPLATE, GOLDEN_CHESTPLATE, IRON_CHESTPLATE, NETHERITE_CHESTPLATE, CHAINMAIL_CHESTPLATE,
                     LEATHER_CHESTPLATE -> true;
                default -> false;
            };
        }

        return false;
    }

    default boolean isElytra(ItemStack it) {
        return isItem(it) && it.getType() == ELYTRA;
    }

    default boolean isHoe(ItemStack it) {
        if (isItem(it)) {
            return switch (it.getType()) {
                case DIAMOND_HOE, GOLDEN_HOE, IRON_HOE, NETHERITE_HOE, STONE_HOE, WOODEN_HOE -> true;
                default -> false;
            };
        }

        return false;
    }

    default boolean isOre(BlockData b) {
        return switch (b.getMaterial()) {
            case COPPER_ORE, DEEPSLATE_COPPER_ORE, COAL_ORE, GOLD_ORE, IRON_ORE, DIAMOND_ORE, LAPIS_ORE, EMERALD_ORE,
                 NETHER_QUARTZ_ORE, NETHER_GOLD_ORE, REDSTONE_ORE, DEEPSLATE_COAL_ORE, DEEPSLATE_IRON_ORE,
                 DEEPSLATE_GOLD_ORE, DEEPSLATE_LAPIS_ORE, DEEPSLATE_DIAMOND_ORE, DEEPSLATE_EMERALD_ORE,
                 DEEPSLATE_REDSTONE_ORE -> true;
            default -> false;
        };
    }

    default boolean isStorage(BlockData b) {
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
