package art.arcane.adapt.mod.feature.recipe;

import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;

/**
 * Vanilla recipe types that additionally enforce exact (NBT-matching) ingredients. The client shows the exact
 * stacks because an exact ingredient is built from the wanted stack, which the recipe sync sends with its tag.
 * Cooking stations pass their whole block entity as the recipe container; only its input slot (0) is checked.
 */
final class ExactRecipes {
    private static final int COOKING_INPUT = 0;

    private ExactRecipes() {
    }

    /** A recipe whose ingredients are narrowed by an exact matcher. */
    interface Exact {
        ExactMatcher exact();
    }

    static final class Shaped extends ShapedRecipe implements Exact {
        private final ExactMatcher exact;

        Shaped(ResourceLocation id, String group, CraftingBookCategory category, int width, int height, NonNullList<Ingredient> cells,
               ItemStack result, ExactMatcher exact) {
            super(id, group, category, width, height, cells, result, true);
            this.exact = exact;
        }

        @Override
        public ExactMatcher exact() {
            return exact;
        }

        @Override
        public boolean matches(CraftingContainer input, Level level) {
            return super.matches(input, level) && exact.test(input);
        }
    }

    static final class Shapeless extends ShapelessRecipe implements Exact {
        private final ExactMatcher exact;

        Shapeless(ResourceLocation id, String group, CraftingBookCategory category, ItemStack result, NonNullList<Ingredient> ingredients,
                  ExactMatcher exact) {
            super(id, group, category, result, ingredients);
            this.exact = exact;
        }

        @Override
        public ExactMatcher exact() {
            return exact;
        }

        @Override
        public boolean matches(CraftingContainer input, Level level) {
            return super.matches(input, level) && exact.test(input);
        }
    }

    static final class Smelting extends SmeltingRecipe implements Exact {
        private final ExactMatcher exact;

        Smelting(ResourceLocation id, String group, CookingBookCategory category, Ingredient input, ItemStack result, float experience, int ticks,
                 ExactMatcher exact) {
            super(id, group, category, input, result, experience, ticks);
            this.exact = exact;
        }

        @Override
        public ExactMatcher exact() {
            return exact;
        }

        @Override
        public boolean matches(Container input, Level level) {
            return super.matches(input, level) && exact.accepts(input.getItem(COOKING_INPUT));
        }
    }

    static final class Smoking extends SmokingRecipe implements Exact {
        private final ExactMatcher exact;

        Smoking(ResourceLocation id, String group, CookingBookCategory category, Ingredient input, ItemStack result, float experience, int ticks,
                ExactMatcher exact) {
            super(id, group, category, input, result, experience, ticks);
            this.exact = exact;
        }

        @Override
        public ExactMatcher exact() {
            return exact;
        }

        @Override
        public boolean matches(Container input, Level level) {
            return super.matches(input, level) && exact.accepts(input.getItem(COOKING_INPUT));
        }
    }

    static final class Blasting extends BlastingRecipe implements Exact {
        private final ExactMatcher exact;

        Blasting(ResourceLocation id, String group, CookingBookCategory category, Ingredient input, ItemStack result, float experience, int ticks,
                 ExactMatcher exact) {
            super(id, group, category, input, result, experience, ticks);
            this.exact = exact;
        }

        @Override
        public ExactMatcher exact() {
            return exact;
        }

        @Override
        public boolean matches(Container input, Level level) {
            return super.matches(input, level) && exact.accepts(input.getItem(COOKING_INPUT));
        }
    }

    static final class Campfire extends CampfireCookingRecipe implements Exact {
        private final ExactMatcher exact;

        Campfire(ResourceLocation id, String group, CookingBookCategory category, Ingredient input, ItemStack result, float experience, int ticks,
                 ExactMatcher exact) {
            super(id, group, category, input, result, experience, ticks);
            this.exact = exact;
        }

        @Override
        public ExactMatcher exact() {
            return exact;
        }

        @Override
        public boolean matches(Container input, Level level) {
            return super.matches(input, level) && exact.accepts(input.getItem(COOKING_INPUT));
        }
    }

    static final class Stonecutting extends StonecutterRecipe implements Exact {
        private final ExactMatcher exact;

        Stonecutting(ResourceLocation id, String group, Ingredient input, ItemStack result, ExactMatcher exact) {
            super(id, group, input, result);
            this.exact = exact;
        }

        @Override
        public ExactMatcher exact() {
            return exact;
        }

        @Override
        public boolean matches(Container input, Level level) {
            return super.matches(input, level) && exact.test(input);
        }
    }

    static final class Smithing extends SmithingTransformRecipe implements Exact {
        private final ExactMatcher exact;

        Smithing(ResourceLocation id, Ingredient template, Ingredient base, Ingredient addition, ItemStack result, ExactMatcher exact) {
            super(id, template, base, addition, result);
            this.exact = exact;
        }

        @Override
        public ExactMatcher exact() {
            return exact;
        }

        @Override
        public boolean matches(Container input, Level level) {
            return super.matches(input, level) && exact.test(input);
        }
    }
}
