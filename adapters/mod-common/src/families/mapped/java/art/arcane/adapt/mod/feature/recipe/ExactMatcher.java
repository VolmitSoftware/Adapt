package art.arcane.adapt.mod.feature.recipe;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeInput;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Component check for exact and matching ingredients. Vanilla ingredients match by item type only, so a recipe
 * that names an exact item (or a matching test) also requires every input stack of that type to carry the same
 * components (or pass the test), unless a type-only ingredient of the same recipe accepts it.
 */
final class ExactMatcher {
    private final List<Wanted> exact;
    private final List<Ingredient> loose;
    private final Map<Ingredient, Wanted> byIngredient;

    ExactMatcher(Map<Ingredient, Wanted> exact, List<Ingredient> loose) {
        this.exact = List.copyOf(exact.values());
        this.loose = List.copyOf(loose);
        this.byIngredient = new IdentityHashMap<>(exact);
    }

    boolean isEmpty() {
        return exact.isEmpty();
    }

    boolean test(RecipeInput input) {
        int size = input.size();
        for (int index = 0; index < size; index++) {
            if (!accepts(input.getItem(index))) {
                return false;
            }
        }
        return true;
    }

    boolean accepts(ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        boolean typed = false;
        for (Wanted wanted : exact) {
            if (wanted.item() != stack.getItem()) {
                continue;
            }
            typed = true;
            if (wanted.test().test(stack)) {
                return true;
            }
        }
        if (!typed) {
            return true;
        }
        for (Ingredient ingredient : loose) {
            if (ingredient.test(stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * True when recipe-book placement may use this stack: stacks of a narrowed item type only when an exact or
     * matching ingredient accepts them (custom names included), every other stack under the vanilla rule.
     */
    boolean placeable(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        boolean typed = false;
        for (Wanted wanted : exact) {
            if (wanted.item() != stack.getItem()) {
                continue;
            }
            typed = true;
            if (wanted.test().test(stack)) {
                return true;
            }
        }
        if (stack.isDamaged() || stack.isEnchanted() || stack.has(DataComponents.CUSTOM_NAME)) {
            return false;
        }
        if (!typed) {
            return true;
        }
        for (Ingredient ingredient : loose) {
            if (ingredient.test(stack)) {
                return true;
            }
        }
        return false;
    }

    /** The exact or example stack an ingredient of this recipe stands for, or null for a type-only ingredient. */
    ItemStack shown(Ingredient ingredient) {
        Wanted wanted = byIngredient.get(ingredient);
        return wanted == null ? null : wanted.shown();
    }

    record Wanted(Item item, ItemStack shown, Predicate<ItemStack> test) {
        static Wanted same(ItemStack wanted) {
            return new Wanted(wanted.getItem(), wanted, (ItemStack stack) -> ItemStack.isSameItemSameComponents(wanted, stack));
        }
    }
}
