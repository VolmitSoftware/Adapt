package art.arcane.adapt.mod.feature.recipe;

import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/** Builds vanilla recipe holders from Adapt recipe specs. Throws IllegalArgumentException for unknown items or tags. */
final class RecipeFactory {
    private static final int DEFAULT_COOK_TICKS = 200;
    private static final String GROUP = "";

    private RecipeFactory() {
    }

    static ResourceLocation key(Id id) {
        return ModRefs.identifier(id);
    }

    static RecipeHolder<?> build(RecipeSpec spec) {
        Collector collector = new Collector();
        ResourceLocation key = key(spec.key());
        ItemStack result = result(spec.result());
        Recipe<?> recipe = switch (spec) {
            case RecipeSpec.Shaped shaped -> shaped(shaped, collector, result);
            case RecipeSpec.Shapeless shapeless -> shapeless(shapeless, collector, result);
            case RecipeSpec.Cooking cooking -> cooking(cooking, collector, result);
            case RecipeSpec.Stonecutting cutting -> stonecutting(cutting, collector, result);
            case RecipeSpec.Smithing smithing -> smithing(smithing, collector, result);
        };
        return new RecipeHolder<>(key, recipe);
    }

    private static Recipe<?> shaped(RecipeSpec.Shaped spec, Collector collector, ItemStack result) {
        Map<Character, Ingredient> key = new HashMap<>();
        for (Map.Entry<Character, art.arcane.adapt.platform.recipe.Ingredient> entry : spec.ingredients().entrySet()) {
            if (entry.getKey() == ' ') {
                continue;
            }
            key.put(entry.getKey(), collector.ingredient(entry.getValue()));
        }
        ShapedRecipePattern pattern;
        try {
            pattern = ShapedRecipePattern.of(key, spec.shape());
        } catch (IllegalStateException invalid) {
            throw new IllegalArgumentException("Invalid shape for " + spec.key() + ": " + invalid.getMessage(), invalid);
        }
        ExactMatcher exact = collector.matcher();
        return exact.isEmpty() ? new ShapedRecipe(GROUP, CraftingBookCategory.MISC, pattern, result, true)
                : new ExactRecipes.Shaped(GROUP, CraftingBookCategory.MISC, pattern, result, exact);
    }

    private static Recipe<?> shapeless(RecipeSpec.Shapeless spec, Collector collector, ItemStack result) {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        for (art.arcane.adapt.platform.recipe.Ingredient ingredient : spec.ingredients()) {
            ingredients.add(collector.ingredient(ingredient));
        }
        ExactMatcher exact = collector.matcher();
        return exact.isEmpty() ? new ShapelessRecipe(GROUP, CraftingBookCategory.MISC, result, ingredients)
                : new ExactRecipes.Shapeless(GROUP, CraftingBookCategory.MISC, result, ingredients, exact);
    }

    private static Recipe<?> cooking(RecipeSpec.Cooking spec, Collector collector, ItemStack result) {
        Ingredient input = collector.ingredient(spec.input());
        ExactMatcher exact = collector.matcher();
        float experience = Math.max(0.0F, spec.experience());
        int ticks = spec.cookTicks() > 0 ? spec.cookTicks() : DEFAULT_COOK_TICKS;
        boolean plain = exact.isEmpty();
        CookingBookCategory book = CookingBookCategory.MISC;
        return switch (spec.station()) {
            case FURNACE -> plain ? new SmeltingRecipe(GROUP, book, input, result, experience, ticks)
                    : new ExactRecipes.Smelting(GROUP, book, input, result, experience, ticks, exact);
            case SMOKER -> plain ? new SmokingRecipe(GROUP, book, input, result, experience, ticks)
                    : new ExactRecipes.Smoking(GROUP, book, input, result, experience, ticks, exact);
            case BLAST_FURNACE -> plain ? new BlastingRecipe(GROUP, book, input, result, experience, ticks)
                    : new ExactRecipes.Blasting(GROUP, book, input, result, experience, ticks, exact);
            case CAMPFIRE -> plain ? new CampfireCookingRecipe(GROUP, book, input, result, experience, ticks)
                    : new ExactRecipes.Campfire(GROUP, book, input, result, experience, ticks, exact);
        };
    }

    private static Recipe<?> stonecutting(RecipeSpec.Stonecutting spec, Collector collector, ItemStack result) {
        Ingredient input = collector.ingredient(spec.input());
        ExactMatcher exact = collector.matcher();
        return exact.isEmpty() ? new StonecutterRecipe(GROUP, input, result)
                : new ExactRecipes.Stonecutting(GROUP, input, result, exact);
    }

    private static Recipe<?> smithing(RecipeSpec.Smithing spec, Collector collector, ItemStack result) {
        Ingredient template = spec.template() == null ? Ingredient.EMPTY : collector.ingredient(spec.template());
        Ingredient base = collector.ingredient(spec.base());
        Ingredient addition = spec.addition() == null ? Ingredient.EMPTY : collector.ingredient(spec.addition());
        ExactMatcher exact = collector.matcher();
        return exact.isEmpty() ? new SmithingTransformRecipe(template, base, addition, result)
                : new ExactRecipes.Smithing(template, base, addition, result, exact);
    }

    private static ItemStack result(ItemRef item) {
        ItemStack stack = ModRefs.item(item);
        if (stack == null || stack.isEmpty()) {
            throw new IllegalArgumentException("Recipe result is empty");
        }
        return stack.copy();
    }

    private static Item item(Id id) {
        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(ModRefs.identifier(id));
        if (item.isEmpty() || item.get() == net.minecraft.world.item.Items.AIR) {
            throw new IllegalArgumentException("Unknown item " + id);
        }
        return item.get();
    }

    private static final class Collector {
        private final Map<Ingredient, ExactMatcher.Wanted> exact = new IdentityHashMap<>();
        private final List<Ingredient> loose = new ArrayList<>();

        Ingredient ingredient(art.arcane.adapt.platform.recipe.Ingredient ingredient) {
            Ingredient vanilla = switch (ingredient) {
                case art.arcane.adapt.platform.recipe.Ingredient.AnyOf any -> {
                    List<Item> items = new ArrayList<>(any.types().size());
                    for (Id type : any.types()) {
                        items.add(item(type));
                    }
                    Ingredient built = Ingredient.of(items.toArray(new Item[0]));
                    loose.add(built);
                    yield built;
                }
                case art.arcane.adapt.platform.recipe.Ingredient.Tag tag -> {
                    TagKey<Item> key = TagKey.create(Registries.ITEM, ModRefs.identifier(tag.tag()));
                    if (BuiltInRegistries.ITEM.getTag(key).isEmpty()) {
                        throw new IllegalArgumentException("Unknown item tag " + tag.tag());
                    }
                    Ingredient built = Ingredient.of(key);
                    loose.add(built);
                    yield built;
                }
                case art.arcane.adapt.platform.recipe.Ingredient.Exact wanted -> {
                    ItemStack stack = ModRefs.item(wanted.item());
                    if (stack == null || stack.isEmpty()) {
                        throw new IllegalArgumentException("Exact ingredient is empty");
                    }
                    ItemStack one = stack.copyWithCount(1);
                    Ingredient built = Ingredient.of(one.copy());
                    exact.put(built, ExactMatcher.Wanted.same(one));
                    yield built;
                }
                case art.arcane.adapt.platform.recipe.Ingredient.Matching matching -> {
                    Item base = item(matching.base());
                    Predicate<ItemRef> test = matching.matches();
                    ItemStack example = ModRefs.item(matching.example());
                    ItemStack shown = example == null || example.isEmpty() ? new ItemStack(base) : example.copyWithCount(1);
                    Ingredient built = Ingredient.of(shown.is(base) ? shown.copy() : new ItemStack(base));
                    exact.put(built, new ExactMatcher.Wanted(base, shown, (ItemStack stack) -> test.test(ModRefs.item(stack))));
                    yield built;
                }
            };
            return vanilla;
        }

        ExactMatcher matcher() {
            return new ExactMatcher(exact, loose);
        }
    }
}
