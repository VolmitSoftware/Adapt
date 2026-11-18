package art.arcane.adapt.mod.feature.recipe;

import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
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
    private static final Recipe.CommonInfo COMMON = new Recipe.CommonInfo(true);
    private static final CraftingRecipe.CraftingBookInfo CRAFTING_BOOK = new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, "");
    private static final AbstractCookingRecipe.CookingBookInfo COOKING_BOOK = new AbstractCookingRecipe.CookingBookInfo(CookingBookCategory.MISC, "");

    private RecipeFactory() {
    }

    static ResourceKey<Recipe<?>> key(Id id) {
        return ResourceKey.create(Registries.RECIPE, ModRefs.identifier(id));
    }

    static RecipeHolder<?> build(RecipeSpec spec) {
        Collector collector = new Collector();
        ResourceKey<Recipe<?>> key = key(spec.key());
        ItemStackTemplate result = result(spec.result());
        Recipe<?> recipe = switch (spec) {
            case RecipeSpec.Shaped shaped -> shaped(shaped, collector, result);
            case RecipeSpec.Shapeless shapeless -> shapeless(shapeless, collector, result);
            case RecipeSpec.Cooking cooking -> cooking(cooking, collector, result);
            case RecipeSpec.Stonecutting cutting -> stonecutting(cutting, collector, result);
            case RecipeSpec.Smithing smithing -> smithing(smithing, collector, result);
        };
        return new RecipeHolder<>(key, recipe);
    }

    private static Recipe<?> shaped(RecipeSpec.Shaped spec, Collector collector, ItemStackTemplate result) {
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
        return exact.isEmpty() ? new ShapedRecipe(COMMON, CRAFTING_BOOK, pattern, result)
                : new ExactRecipes.Shaped(COMMON, CRAFTING_BOOK, pattern, result, exact);
    }

    private static Recipe<?> shapeless(RecipeSpec.Shapeless spec, Collector collector, ItemStackTemplate result) {
        List<Ingredient> ingredients = new ArrayList<>(spec.ingredients().size());
        for (art.arcane.adapt.platform.recipe.Ingredient ingredient : spec.ingredients()) {
            ingredients.add(collector.ingredient(ingredient));
        }
        List<Ingredient> fixed = List.copyOf(ingredients);
        ExactMatcher exact = collector.matcher();
        return exact.isEmpty() ? new ShapelessRecipe(COMMON, CRAFTING_BOOK, result, fixed)
                : new ExactRecipes.Shapeless(COMMON, CRAFTING_BOOK, result, fixed, exact);
    }

    private static Recipe<?> cooking(RecipeSpec.Cooking spec, Collector collector, ItemStackTemplate result) {
        Ingredient input = collector.ingredient(spec.input());
        ExactMatcher exact = collector.matcher();
        float experience = Math.max(0.0F, spec.experience());
        int ticks = spec.cookTicks() > 0 ? spec.cookTicks() : DEFAULT_COOK_TICKS;
        boolean plain = exact.isEmpty();
        return switch (spec.station()) {
            case FURNACE -> plain ? new SmeltingRecipe(COMMON, COOKING_BOOK, input, result, experience, ticks)
                    : new ExactRecipes.Smelting(COMMON, COOKING_BOOK, input, result, experience, ticks, exact);
            case SMOKER -> plain ? new SmokingRecipe(COMMON, COOKING_BOOK, input, result, experience, ticks)
                    : new ExactRecipes.Smoking(COMMON, COOKING_BOOK, input, result, experience, ticks, exact);
            case BLAST_FURNACE -> plain ? new BlastingRecipe(COMMON, COOKING_BOOK, input, result, experience, ticks)
                    : new ExactRecipes.Blasting(COMMON, COOKING_BOOK, input, result, experience, ticks, exact);
            case CAMPFIRE -> plain ? new CampfireCookingRecipe(COMMON, COOKING_BOOK, input, result, experience, ticks)
                    : new ExactRecipes.Campfire(COMMON, COOKING_BOOK, input, result, experience, ticks, exact);
        };
    }

    private static Recipe<?> stonecutting(RecipeSpec.Stonecutting spec, Collector collector, ItemStackTemplate result) {
        Ingredient input = collector.ingredient(spec.input());
        ExactMatcher exact = collector.matcher();
        return exact.isEmpty() ? new StonecutterRecipe(COMMON, input, result)
                : new ExactRecipes.Stonecutting(COMMON, input, result, exact);
    }

    private static Recipe<?> smithing(RecipeSpec.Smithing spec, Collector collector, ItemStackTemplate result) {
        Optional<Ingredient> template = spec.template() == null ? Optional.empty() : Optional.of(collector.ingredient(spec.template()));
        Ingredient base = collector.ingredient(spec.base());
        Optional<Ingredient> addition = spec.addition() == null ? Optional.empty() : Optional.of(collector.ingredient(spec.addition()));
        ExactMatcher exact = collector.matcher();
        return exact.isEmpty() ? new SmithingTransformRecipe(COMMON, template, base, addition, result)
                : new ExactRecipes.Smithing(COMMON, template, base, addition, result, exact);
    }

    private static ItemStackTemplate result(ItemRef item) {
        ItemStack stack = ModRefs.item(item);
        if (stack == null || stack.isEmpty()) {
            throw new IllegalArgumentException("Recipe result is empty");
        }
        return ItemStackTemplate.fromNonEmptyStack(stack);
    }

    private static Item item(Id id) {
        Identifier identifier = ModRefs.identifier(id);
        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(identifier);
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
                    Ingredient built = Ingredient.of(items.stream());
                    loose.add(built);
                    yield built;
                }
                case art.arcane.adapt.platform.recipe.Ingredient.Tag tag -> {
                    TagKey<Item> key = TagKey.create(Registries.ITEM, ModRefs.identifier(tag.tag()));
                    Optional<HolderSet.Named<Item>> set = BuiltInRegistries.ITEM.get(key);
                    if (set.isEmpty()) {
                        throw new IllegalArgumentException("Unknown item tag " + tag.tag());
                    }
                    Ingredient built = Ingredient.of(set.get());
                    loose.add(built);
                    yield built;
                }
                case art.arcane.adapt.platform.recipe.Ingredient.Exact wanted -> {
                    ItemStack stack = ModRefs.item(wanted.item());
                    if (stack == null || stack.isEmpty()) {
                        throw new IllegalArgumentException("Exact ingredient is empty");
                    }
                    Ingredient built = Ingredient.of(stack.getItem());
                    exact.put(built, ExactMatcher.Wanted.same(stack.copyWithCount(1)));
                    yield built;
                }
                case art.arcane.adapt.platform.recipe.Ingredient.Matching matching -> {
                    Item base = item(matching.base());
                    Predicate<ItemRef> test = matching.matches();
                    ItemStack example = ModRefs.item(matching.example());
                    ItemStack shown = example == null || example.isEmpty() ? new ItemStack(base) : example.copyWithCount(1);
                    Ingredient built = Ingredient.of(base);
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
