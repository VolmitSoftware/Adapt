package com.volmit.adapt.api.world;

import com.volmit.adapt.Adapt;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
        return isAxe(is) || isPickaxe(is) || isHoe(is) || isShovel(is) || isSword(is);
    }

    default boolean isMelee(ItemStack is) {
        return isTool(is);
    }

    default boolean isRanged(ItemStack it) {
        if(isItem(it)) {
            switch(it.getType()) {
                case BOW:
                case CROSSBOW:
                    return true;
                default:
                    return false;
            }
        }

        return false;
    }

    default boolean isSword(ItemStack it) {
        if(isItem(it)) {
            switch(it.getType()) {
                case DIAMOND_SWORD:
                case GOLDEN_SWORD:
                case IRON_SWORD:
                case NETHERITE_SWORD:
                case STONE_SWORD:
                case WOODEN_SWORD:
                    return true;
                default:
                    return false;
            }
        }

        return false;
    }

    default boolean isAxe(ItemStack it) {
        if(isItem(it)) {
            switch(it.getType()) {
                case DIAMOND_AXE:
                case GOLDEN_AXE:
                case IRON_AXE:
                case NETHERITE_AXE:
                case STONE_AXE:
                case WOODEN_AXE:
                    return true;
                default:
                    return false;
            }
        }

        return false;
    }

    default boolean isPickaxe(ItemStack it) {
        if(isItem(it)) {
            switch(it.getType()) {
                case DIAMOND_PICKAXE:
                case GOLDEN_PICKAXE:
                case IRON_PICKAXE:
                case NETHERITE_PICKAXE:
                case STONE_PICKAXE:
                case WOODEN_PICKAXE:
                    return true;
                default:
                    return false;
            }
        }

        return false;
    }

    default boolean isShovel(ItemStack it) {
        if(isItem(it)) {
            switch(it.getType()) {
                case DIAMOND_SHOVEL:
                case GOLDEN_SHOVEL:
                case IRON_SHOVEL:
                case NETHERITE_SHOVEL:
                case STONE_SHOVEL:
                case WOODEN_SHOVEL:
                    return true;
                default:
                    return false;
            }
        }

        return false;
    }

    default boolean isBoots(ItemStack it) {
        if(isItem(it)) {
            switch(it.getType()) {
                case DIAMOND_BOOTS:
                case GOLDEN_BOOTS:
                case IRON_BOOTS:
                case NETHERITE_BOOTS:
                case CHAINMAIL_BOOTS:
                case LEATHER_BOOTS:
                    return true;
                default:
                    return false;
            }
        }

        return false;
    }

    default boolean isHelmet(ItemStack it) {
        if(isItem(it)) {
            switch(it.getType()) {
                case CHAINMAIL_HELMET:
                case DIAMOND_HELMET:
                case GOLDEN_HELMET:
                case IRON_HELMET:
                case LEATHER_HELMET:
                case NETHERITE_HELMET:
                case TURTLE_HELMET:
                    return true;
                default:
                    return false;
            }
        }

        return false;
    }

    default boolean isLeggings(ItemStack it) {
        if(isItem(it)) {
            switch(it.getType()) {
                case DIAMOND_LEGGINGS:
                case GOLDEN_LEGGINGS:
                case IRON_LEGGINGS:
                case NETHERITE_LEGGINGS:
                case CHAINMAIL_LEGGINGS:
                case LEATHER_LEGGINGS:
                    return true;
                default:
                    return false;
            }
        }

        return false;
    }

    default boolean isChestplate(ItemStack it) {
        if(isItem(it)) {
            switch(it.getType()) {
                case DIAMOND_CHESTPLATE:
                case GOLDEN_CHESTPLATE:
                case IRON_CHESTPLATE:
                case NETHERITE_CHESTPLATE:
                case CHAINMAIL_CHESTPLATE:
                case LEATHER_CHESTPLATE:
                    return true;
                default:
                    return false;
            }
        }

        return false;
    }

    default boolean isHoe(ItemStack it) {
        if(isItem(it)) {
            switch(it.getType()) {
                case DIAMOND_HOE:
                case GOLDEN_HOE:
                case IRON_HOE:
                case NETHERITE_HOE:
                case STONE_HOE:
                case WOODEN_HOE:
                    return true;
                default:
                    return false;
            }
        }

        return false;
    }

    default boolean isOre(BlockData b) {
        return switch(b.getMaterial()) {
            case COPPER_ORE, DEEPSLATE_COPPER_ORE, COAL_ORE, GOLD_ORE, IRON_ORE, DIAMOND_ORE, LAPIS_ORE, EMERALD_ORE, NETHER_QUARTZ_ORE, NETHER_GOLD_ORE, REDSTONE_ORE, DEEPSLATE_COAL_ORE, DEEPSLATE_IRON_ORE, DEEPSLATE_GOLD_ORE, DEEPSLATE_LAPIS_ORE, DEEPSLATE_DIAMOND_ORE, DEEPSLATE_EMERALD_ORE, DEEPSLATE_REDSTONE_ORE -> true;
            default -> false;
        };
    }

    default boolean isStorage(BlockData b) {
        return switch(b.getMaterial()) {
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
