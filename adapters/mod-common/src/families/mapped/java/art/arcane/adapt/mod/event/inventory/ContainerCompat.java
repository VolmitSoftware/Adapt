package art.arcane.adapt.mod.event.inventory;

import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.value.Id;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.EnchantingTableBlock;

/** Container calls whose vanilla shape differs between 1.21.1 and 1.20.1; the 1.20.1 overlay replaces this class. */
final class ContainerCompat {
    private ContainerCompat() {
    }

    static Recipe<?> recipe(ResultContainer results) {
        RecipeHolder<?> holder = results.getRecipeUsed();
        return holder == null ? null : holder.value();
    }

    static Id recipeId(ResultContainer results) {
        RecipeHolder<?> holder = results.getRecipeUsed();
        return holder == null ? null : ModRefs.id(holder.id());
    }

    /** Bookshelves an enchanting table at this position counts. */
    static int bookshelves(ServerLevel level, BlockPos table) {
        int count = 0;
        for (BlockPos offset : EnchantingTableBlock.BOOKSHELF_OFFSETS) {
            if (EnchantingTableBlock.isValidBookShelf(level, table, offset)) {
                count++;
            }
        }
        return count;
    }

    /** The numeric enchantment ids the enchanting menu sends as clues. */
    static IdMap<Holder<Enchantment>> enchantmentIds(ServerLevel level) {
        return level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).asHolderIdMap();
    }
}
