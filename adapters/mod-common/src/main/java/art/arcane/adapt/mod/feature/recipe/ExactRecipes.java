package art.arcane.adapt.mod.feature.recipe;

import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Vanilla recipe types that additionally enforce exact (component-matching) ingredients. */
final class ExactRecipes {
    private ExactRecipes() {
    }

    /** A recipe whose ingredients are narrowed by an exact matcher. */
    interface Exact {
        ExactMatcher exact();
    }

    static final class Shaped extends ShapedRecipe implements Exact {
        private final ItemStackTemplate result;
        private final ExactMatcher exact;

        Shaped(Recipe.CommonInfo common, CraftingRecipe.CraftingBookInfo book, ShapedRecipePattern pattern, ItemStackTemplate result, ExactMatcher exact) {
            super(common, book, pattern, result);
            this.result = result;
            this.exact = exact;
        }

        @Override
        public List<RecipeDisplay> display() {
            List<SlotDisplay> cells = new ArrayList<>(getIngredients().size());
            for (Optional<Ingredient> cell : getIngredients()) {
                cells.add(exact.display(cell));
            }
            return List.of(new ShapedCraftingRecipeDisplay(getWidth(), getHeight(), cells,
                new SlotDisplay.ItemStackSlotDisplay(result), new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE)));
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
        private final ItemStackTemplate result;
        private final List<Ingredient> ingredients;
        private final ExactMatcher exact;

        Shapeless(Recipe.CommonInfo common, CraftingRecipe.CraftingBookInfo book, ItemStackTemplate result, List<Ingredient> ingredients, ExactMatcher exact) {
            super(common, book, result, ingredients);
            this.result = result;
            this.ingredients = List.copyOf(ingredients);
            this.exact = exact;
        }

        @Override
        public List<RecipeDisplay> display() {
            List<SlotDisplay> cells = new ArrayList<>(ingredients.size());
            for (Ingredient ingredient : ingredients) {
                cells.add(exact.display(ingredient));
            }
            return List.of(new ShapelessCraftingRecipeDisplay(cells,
                new SlotDisplay.ItemStackSlotDisplay(result), new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE)));
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

        Smelting(Recipe.CommonInfo common, AbstractCookingRecipe.CookingBookInfo book, Ingredient input, ItemStackTemplate result, float experience, int ticks, ExactMatcher exact) {
            super(common, book, input, result, experience, ticks);
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

        Smoking(Recipe.CommonInfo common, AbstractCookingRecipe.CookingBookInfo book, Ingredient input, ItemStackTemplate result, float experience, int ticks, ExactMatcher exact) {
            super(common, book, input, result, experience, ticks);
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

        Blasting(Recipe.CommonInfo common, AbstractCookingRecipe.CookingBookInfo book, Ingredient input, ItemStackTemplate result, float experience, int ticks, ExactMatcher exact) {
            super(common, book, input, result, experience, ticks);
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

        Campfire(Recipe.CommonInfo common, AbstractCookingRecipe.CookingBookInfo book, Ingredient input, ItemStackTemplate result, float experience, int ticks, ExactMatcher exact) {
            super(common, book, input, result, experience, ticks);
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

        Stonecutting(Recipe.CommonInfo common, Ingredient input, ItemStackTemplate result, ExactMatcher exact) {
            super(common, input, result);
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

        Smithing(Recipe.CommonInfo common, Optional<Ingredient> template, Ingredient base, Optional<Ingredient> addition, ItemStackTemplate result, ExactMatcher exact) {
            super(common, template, base, addition, result);
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
