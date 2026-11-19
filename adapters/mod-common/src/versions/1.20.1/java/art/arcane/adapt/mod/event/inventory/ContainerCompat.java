package art.arcane.adapt.mod.event.inventory;

import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.value.Id;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.EnchantmentTableBlock;

/** Container calls whose vanilla shape differs between 1.21.1 and 1.20.1 (this is the 1.20.1 side). */
final class ContainerCompat {
    private ContainerCompat() {
    }

    static Recipe<?> recipe(ResultContainer results) {
        return results.getRecipeUsed();
    }

    static Id recipeId(ResultContainer results) {
        Recipe<?> recipe = results.getRecipeUsed();
        return recipe == null ? null : ModRefs.id(recipe.getId());
    }

    /** Bookshelves an enchanting table at this position counts. */
    static int bookshelves(ServerLevel level, BlockPos table) {
        int count = 0;
        for (BlockPos offset : EnchantmentTableBlock.BOOKSHELF_OFFSETS) {
            if (EnchantmentTableBlock.isValidBookShelf(level, table, offset)) {
                count++;
            }
        }
        return count;
    }

    /** The numeric enchantment ids the enchanting menu sends as clues. */
    static IdMap<Holder<Enchantment>> enchantmentIds(ServerLevel level) {
        return BuiltInRegistries.ENCHANTMENT.asHolderIdMap();
    }
}
