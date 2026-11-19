package art.arcane.adapt.mod.feature.recipe;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;

/**
 * Vanilla recipe types that additionally enforce exact (component-matching) ingredients. The client shows the exact
 * stacks because an exact ingredient is built from the wanted stack, which the recipe sync sends with its components.
 */
final class ExactRecipes {
    private ExactRecipes() {
    }

    /** A recipe whose ingredients are narrowed by an exact matcher. */
    interface Exact {
        ExactMatcher exact();
    }

    static final class Shaped extends ShapedRecipe implements Exact {
        private final ExactMatcher exact;

        Shaped(String group, CraftingBookCategory category, ShapedRecipePattern pattern, ItemStack result, ExactMatcher exact) {
            super(group, category, pattern, result, true);
            this.exact = exact;
        }

        @Override
        public ExactMatcher exact() {
            return exact;
        }

        @Override
        public boolean matches(CraftingInput input, Level level) {
            return super.matches(input, level) && exact.test(input);
        }
    }

    static final class Shapeless extends ShapelessRecipe implements Exact {
        private final ExactMatcher exact;

        Shapeless(String group, CraftingBookCategory category, ItemStack result, NonNullList<Ingredient> ingredients, ExactMatcher exact) {
            super(group, category, result, ingredients);
            this.exact = exact;
        }

        @Override
        public ExactMatcher exact() {
            return exact;
        }

        @Override
        public boolean matches(CraftingInput input, Level level) {
            return super.matches(input, level) && exact.test(input);
        }
    }

    static final class Smelting extends SmeltingRecipe implements Exact {
        private final ExactMatcher exact;

        Smelting(String group, CookingBookCategory category, Ingredient input, ItemStack result, float experience, int ticks, ExactMatcher exact) {
            super(group, category, input, result, experience, ticks);
            this.exact = exact;
        }

        @Override
        public ExactMatcher exact() {
            return exact;
        }

        @Override
        public boolean matches(SingleRecipeInput input, Level level) {
            return super.matches(input, level) && exact.test(input);
        }
    }

    static final class Smoking extends SmokingRecipe implements Exact {
        private final ExactMatcher exact;

        Smoking(String group, CookingBookCategory category, Ingredient input, ItemStack result, float experience, int ticks, ExactMatcher exact) {
            super(group, category, input, result, experience, ticks);
            this.exact = exact;
        }

        @Override
        public ExactMatcher exact() {
            return exact;
        }

        @Override
        public boolean matches(SingleRecipeInput input, Level level) {
            return super.matches(input, level) && exact.test(input);
        }
    }

    static final class Blasting extends BlastingRecipe implements Exact {
        private final ExactMatcher exact;

        Blasting(String group, CookingBookCategory category, Ingredient input, ItemStack result, float experience, int ticks, ExactMatcher exact) {
            super(group, category, input, result, experience, ticks);
            this.exact = exact;
        }

        @Override
        public ExactMatcher exact() {
            return exact;
        }

        @Override
        public boolean matches(SingleRecipeInput input, Level level) {
            return super.matches(input, level) && exact.test(input);
        }
    }

    static final class Campfire extends CampfireCookingRecipe implements Exact {
        private final ExactMatcher exact;

        Campfire(String group, CookingBookCategory category, Ingredient input, ItemStack result, float experience, int ticks, ExactMatcher exact) {
            super(group, category, input, result, experience, ticks);
            this.exact = exact;
        }

        @Override
        public ExactMatcher exact() {
            return exact;
        }

        @Override
        public boolean matches(SingleRecipeInput input, Level level) {
            return super.matches(input, level) && exact.test(input);
        }
    }

    static final class Stonecutting extends StonecutterRecipe implements Exact {
        private final ExactMatcher exact;

        Stonecutting(String group, Ingredient input, ItemStack result, ExactMatcher exact) {
            super(group, input, result);
            this.exact = exact;
        }

        @Override
        public ExactMatcher exact() {
            return exact;
        }

        @Override
        public boolean matches(SingleRecipeInput input, Level level) {
            return super.matches(input, level) && exact.test(input);
        }
    }

    static final class Smithing extends SmithingTransformRecipe implements Exact {
        private final ExactMatcher exact;

        Smithing(Ingredient template, Ingredient base, Ingredient addition, ItemStack result, ExactMatcher exact) {
            super(template, base, addition, result);
            this.exact = exact;
        }

        @Override
        public ExactMatcher exact() {
            return exact;
        }

        @Override
        public boolean matches(SmithingRecipeInput input, Level level) {
            return super.matches(input, level) && exact.test(input);
        }
    }
}
