package art.arcane.adapt.mod.feature.recipe;

import art.arcane.adapt.mod.feature.mixin.ShapedRecipeAccess;
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
import net.minecraft.world.item.crafting.ShapedRecipe;
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

/** Builds vanilla recipes from Adapt recipe specs. Throws IllegalArgumentException for unknown items or tags. */
final class RecipeFactory {
    private static final int DEFAULT_COOK_TICKS = 200;
    private static final String GROUP = "";
    private static final String BLANK = " ";

    private RecipeFactory() {
    }

    static ResourceLocation key(Id id) {
        return ModRefs.identifier(id);
    }

    static Recipe<?> build(RecipeSpec spec) {
        Collector collector = new Collector();
        ResourceLocation key = key(spec.key());
        ItemStack result = result(spec.result());
        return switch (spec) {
            case RecipeSpec.Shaped shaped -> shaped(key, shaped, collector, result);
            case RecipeSpec.Shapeless shapeless -> shapeless(key, shapeless, collector, result);
            case RecipeSpec.Cooking cooking -> cooking(key, cooking, collector, result);
            case RecipeSpec.Stonecutting cutting -> stonecutting(key, cutting, collector, result);
            case RecipeSpec.Smithing smithing -> smithing(key, smithing, collector, result);
        };
    }

    private static Recipe<?> shaped(ResourceLocation id, RecipeSpec.Shaped spec, Collector collector, ItemStack result) {
        Map<String, Ingredient> key = new HashMap<>();
        key.put(BLANK, Ingredient.EMPTY);
        for (Map.Entry<Character, art.arcane.adapt.platform.recipe.Ingredient> entry : spec.ingredients().entrySet()) {
            if (entry.getKey() == ' ') {
                continue;
            }
            key.put(String.valueOf(entry.getKey()), collector.ingredient(entry.getValue()));
        }
        String[] rows = ShapedRecipeAccess.adapt$shrink(spec.shape().toArray(new String[0]));
        if (rows.length == 0) {
            throw new IllegalArgumentException("Invalid shape for " + spec.key() + ": empty pattern");
        }
        int width = rows[0].length();
        for (String row : rows) {
            if (row.length() != width) {
                throw new IllegalArgumentException("Invalid shape for " + spec.key() + ": rows differ in width");
            }
        }
        NonNullList<Ingredient> cells;
        try {
            cells = ShapedRecipeAccess.adapt$dissolvePattern(rows, key, width, rows.length);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Invalid shape for " + spec.key() + ": " + invalid.getMessage(), invalid);
        }
        ExactMatcher exact = collector.matcher();
        return exact.isEmpty() ? new ShapedRecipe(id, GROUP, CraftingBookCategory.MISC, width, rows.length, cells, result, true)
                : new ExactRecipes.Shaped(id, GROUP, CraftingBookCategory.MISC, width, rows.length, cells, result, exact);
    }

    private static Recipe<?> shapeless(ResourceLocation id, RecipeSpec.Shapeless spec, Collector collector, ItemStack result) {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        for (art.arcane.adapt.platform.recipe.Ingredient ingredient : spec.ingredients()) {
            ingredients.add(collector.ingredient(ingredient));
        }
        ExactMatcher exact = collector.matcher();
        return exact.isEmpty() ? new ShapelessRecipe(id, GROUP, CraftingBookCategory.MISC, result, ingredients)
                : new ExactRecipes.Shapeless(id, GROUP, CraftingBookCategory.MISC, result, ingredients, exact);
    }

    private static Recipe<?> cooking(ResourceLocation id, RecipeSpec.Cooking spec, Collector collector, ItemStack result) {
        Ingredient input = collector.ingredient(spec.input());
        ExactMatcher exact = collector.matcher();
        float experience = Math.max(0.0F, spec.experience());
        int ticks = spec.cookTicks() > 0 ? spec.cookTicks() : DEFAULT_COOK_TICKS;
        boolean plain = exact.isEmpty();
        CookingBookCategory book = CookingBookCategory.MISC;
        return switch (spec.station()) {
            case FURNACE -> plain ? new SmeltingRecipe(id, GROUP, book, input, result, experience, ticks)
                    : new ExactRecipes.Smelting(id, GROUP, book, input, result, experience, ticks, exact);
            case SMOKER -> plain ? new SmokingRecipe(id, GROUP, book, input, result, experience, ticks)
                    : new ExactRecipes.Smoking(id, GROUP, book, input, result, experience, ticks, exact);
            case BLAST_FURNACE -> plain ? new BlastingRecipe(id, GROUP, book, input, result, experience, ticks)
                    : new ExactRecipes.Blasting(id, GROUP, book, input, result, experience, ticks, exact);
            case CAMPFIRE -> plain ? new CampfireCookingRecipe(id, GROUP, book, input, result, experience, ticks)
                    : new ExactRecipes.Campfire(id, GROUP, book, input, result, experience, ticks, exact);
        };
    }

    private static Recipe<?> stonecutting(ResourceLocation id, RecipeSpec.Stonecutting spec, Collector collector, ItemStack result) {
        Ingredient input = collector.ingredient(spec.input());
        ExactMatcher exact = collector.matcher();
        return exact.isEmpty() ? new StonecutterRecipe(id, GROUP, input, result)
                : new ExactRecipes.Stonecutting(id, GROUP, input, result, exact);
    }

    private static Recipe<?> smithing(ResourceLocation id, RecipeSpec.Smithing spec, Collector collector, ItemStack result) {
        Ingredient template = spec.template() == null ? Ingredient.EMPTY : collector.ingredient(spec.template());
        Ingredient base = collector.ingredient(spec.base());
        Ingredient addition = spec.addition() == null ? Ingredient.EMPTY : collector.ingredient(spec.addition());
        ExactMatcher exact = collector.matcher();
        return exact.isEmpty() ? new SmithingTransformRecipe(id, template, base, addition, result)
                : new ExactRecipes.Smithing(id, template, base, addition, result, exact);
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
