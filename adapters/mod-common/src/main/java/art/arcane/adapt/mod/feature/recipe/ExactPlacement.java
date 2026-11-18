package art.arcane.adapt.mod.feature.recipe;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;
import java.util.function.Supplier;

/**
 * Recipe-book placement for recipes with exact or matching ingredients. Vanilla placement only counts and moves
 * plain stacks by item type; these recipes count and move the stacks their matcher accepts instead, so a named
 * exact ingredient is placed and a plain stack of the same type is not.
 */
public final class ExactPlacement {
    private static final ThreadLocal<ExactMatcher> PLACING = new ThreadLocal<>();

    private ExactPlacement() {
    }

    /** True when the recipe narrows its ingredients; vanilla accounting applies otherwise. */
    public static boolean narrows(RecipeHolder<?> recipe) {
        return matcher(recipe) != null;
    }

    public static void fillInventory(Inventory inventory, StackedItemContents contents, RecipeHolder<?> recipe) {
        ExactMatcher matcher = matcher(recipe);
        for (ItemStack stack : inventory.getNonEquipmentItems()) {
            if (matcher.placeable(stack)) {
                contents.accountStack(stack);
            }
        }
    }

    public static void fillGrid(List<Slot> grid, StackedItemContents contents, RecipeHolder<?> recipe) {
        ExactMatcher matcher = matcher(recipe);
        for (Slot slot : grid) {
            ItemStack stack = slot.getItem();
            if (matcher.placeable(stack)) {
                contents.accountStack(stack);
            }
        }
    }

    public static <T> T placing(RecipeHolder<?> recipe, Supplier<T> placement) {
        ExactMatcher previous = PLACING.get();
        PLACING.set(matcher(recipe));
        try {
            return placement.get();
        } finally {
            if (previous == null) {
                PLACING.remove();
            } else {
                PLACING.set(previous);
            }
        }
    }

    /** The inventory slot to take a placed ingredient from, or {@code null} when vanilla lookup applies. */
    public static Integer slotFor(Inventory inventory, Holder<Item> item, ItemStack existing) {
        ExactMatcher matcher = PLACING.get();
        if (matcher == null) {
            return null;
        }
        List<ItemStack> items = inventory.getNonEquipmentItems();
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = items.get(slot);
            if (!stack.isEmpty() && stack.is(item) && matcher.placeable(stack)
                && (existing.isEmpty() || ItemStack.isSameItemSameComponents(existing, stack))) {
                return slot;
            }
        }
        return -1;
    }

    private static ExactMatcher matcher(RecipeHolder<?> recipe) {
        return recipe != null && recipe.value() instanceof ExactRecipes.Exact narrowed ? narrowed.exact() : null;
    }
}
