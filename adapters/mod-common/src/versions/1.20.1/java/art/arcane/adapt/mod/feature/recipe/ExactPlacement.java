package art.arcane.adapt.mod.feature.recipe;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

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
    public static boolean narrows(Recipe<?> recipe) {
        return matcher(recipe) != null;
    }

    public static void fillInventory(Inventory inventory, StackedContents contents, Recipe<?> recipe) {
        ExactMatcher matcher = matcher(recipe);
        for (ItemStack stack : inventory.items) {
            if (matcher.placeable(stack)) {
                contents.accountStack(stack);
            }
        }
    }

    /** Counts the crafting grid of {@code menu}: every slot of its grid area except the result slot. */
    public static void fillGrid(RecipeBookMenu<?> menu, StackedContents contents, Recipe<?> recipe) {
        ExactMatcher matcher = matcher(recipe);
        int slots = menu.getGridWidth() * menu.getGridHeight() + 1;
        for (int slot = 0; slot < slots; slot++) {
            if (slot == menu.getResultSlotIndex()) {
                continue;
            }
            ItemStack stack = menu.getSlot(slot).getItem();
            if (matcher.placeable(stack)) {
                contents.accountStack(stack);
            }
        }
    }

    public static <T> T placing(Recipe<?> recipe, Supplier<T> placement) {
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
    public static Integer slotFor(Inventory inventory, ItemStack wanted, ItemStack existing) {
        ExactMatcher matcher = PLACING.get();
        if (matcher == null) {
            return null;
        }
        List<ItemStack> items = inventory.items;
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = items.get(slot);
            if (!stack.isEmpty() && stack.is(wanted.getItem()) && matcher.placeable(stack)
                && (existing.isEmpty() || ItemStack.isSameItemSameTags(existing, stack))) {
                return slot;
            }
        }
        return -1;
    }

    private static ExactMatcher matcher(Recipe<?> recipe) {
        return recipe instanceof ExactRecipes.Exact narrowed ? narrowed.exact() : null;
    }
}
