package art.arcane.adapt.bukkit.feature.recipe;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.SmokingRecipe;
import org.bukkit.inventory.StonecuttingRecipe;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Recipe specs to the same Bukkit recipe classes {@code AdaptRecipe} registers. Smithing ingredients given as
 * item types become exact choices, as the plugin's smithing recipes always were. Matching ingredients become
 * Paper predicate choices, or exact choices of their example where the server has none. Returns null when an
 * ingredient or the result does not resolve.
 */
final class RecipeTranslator {
  private static final Method PREDICATE_CHOICE = predicateChoiceFactory();

  private final Function<Id, Material> materials;
  private final Function<ItemRef, ItemStack> items;
  private final Function<Id, Tag<Material>> tags;

  RecipeTranslator(Function<Id, Material> materials, Function<ItemRef, ItemStack> items, Function<Id, Tag<Material>> tags) {
    this.materials = Objects.requireNonNull(materials, "materials");
    this.items = Objects.requireNonNull(items, "items");
    this.tags = Objects.requireNonNull(tags, "tags");
  }

  static NamespacedKey key(Id id) {
    return new NamespacedKey(id.namespace(), id.path());
  }

  Recipe translate(RecipeSpec spec) {
    ItemStack result = items.apply(spec.result());
    if (result == null || result.isEmpty()) {
      return null;
    }
    NamespacedKey key = key(spec.key());
    return switch (spec) {
      case RecipeSpec.Shaped shaped -> shaped(key, shaped, result);
      case RecipeSpec.Shapeless shapeless -> shapeless(key, shapeless, result);
      case RecipeSpec.Cooking cooking -> cooking(key, cooking, result);
      case RecipeSpec.Stonecutting stonecutting -> {
        RecipeChoice input = choice(stonecutting.input());
        yield input == null ? null : new StonecuttingRecipe(key, result, input);
      }
      case RecipeSpec.Smithing smithing -> smithing(key, smithing, result);
    };
  }

  private Recipe shaped(NamespacedKey key, RecipeSpec.Shaped spec, ItemStack result) {
    ShapedRecipe recipe = new ShapedRecipe(key, result);
    recipe.shape(spec.shape().toArray(new String[0]));
    for (Map.Entry<Character, Ingredient> entry : spec.ingredients().entrySet()) {
      RecipeChoice choice = choice(entry.getValue());
      if (choice == null) {
        return null;
      }
      recipe.setIngredient(entry.getKey(), choice);
    }
    return recipe;
  }

  private Recipe shapeless(NamespacedKey key, RecipeSpec.Shapeless spec, ItemStack result) {
    ShapelessRecipe recipe = new ShapelessRecipe(key, result);
    for (Ingredient ingredient : spec.ingredients()) {
      RecipeChoice choice = choice(ingredient);
      if (choice == null) {
        return null;
      }
      recipe.addIngredient(choice);
    }
    return recipe;
  }

  private Recipe cooking(NamespacedKey key, RecipeSpec.Cooking spec, ItemStack result) {
    RecipeChoice input = choice(spec.input());
    if (input == null) {
      return null;
    }
    return switch (spec.station()) {
      case FURNACE -> new FurnaceRecipe(key, result, input, spec.experience(), spec.cookTicks());
      case SMOKER -> new SmokingRecipe(key, result, input, spec.experience(), spec.cookTicks());
      case BLAST_FURNACE -> new BlastingRecipe(key, result, input, spec.experience(), spec.cookTicks());
      case CAMPFIRE -> new CampfireRecipe(key, result, input, spec.experience(), spec.cookTicks());
    };
  }

  private Recipe smithing(NamespacedKey key, RecipeSpec.Smithing spec, ItemStack result) {
    RecipeChoice base = smithingChoice(spec.base());
    RecipeChoice addition = spec.addition() == null ? RecipeChoice.empty() : smithingChoice(spec.addition());
    if (base == null || addition == null) {
      return null;
    }
    if (spec.template() == null) {
      return new SmithingRecipe(key, result, base, addition);
    }
    RecipeChoice template = smithingChoice(spec.template());
    return template == null ? null : new SmithingTransformRecipe(key, result, template, base, addition);
  }

  private RecipeChoice smithingChoice(Ingredient ingredient) {
    if (!(ingredient instanceof Ingredient.AnyOf anyOf)) {
      return choice(ingredient);
    }
    List<ItemStack> stacks = new ArrayList<>(anyOf.types().size());
    for (Material material : resolve(anyOf)) {
      stacks.add(new ItemStack(material));
    }
    return stacks.isEmpty() ? null : new RecipeChoice.ExactChoice(stacks);
  }

  RecipeChoice choice(Ingredient ingredient) {
    return switch (ingredient) {
      case Ingredient.AnyOf anyOf -> {
        List<Material> resolved = resolve(anyOf);
        yield resolved.isEmpty() ? null : new RecipeChoice.MaterialChoice(resolved);
      }
      case Ingredient.Tag tag -> {
        Tag<Material> resolved = tags.apply(tag.tag());
        yield resolved == null ? null : new RecipeChoice.MaterialChoice(resolved);
      }
      case Ingredient.Exact exact -> {
        ItemStack stack = items.apply(exact.item());
        yield stack == null || stack.isEmpty() ? null : new RecipeChoice.ExactChoice(stack);
      }
      case Ingredient.Matching matching -> matchingChoice(matching);
    };
  }

  private RecipeChoice matchingChoice(Ingredient.Matching matching) {
    Material base = materials.apply(matching.base());
    ItemStack example = items.apply(matching.example());
    if (base == null || example == null || example.isEmpty()) {
      return null;
    }
    ItemStack representative = example.clone();
    representative.setAmount(1);
    if (PREDICATE_CHOICE == null) {
      return new RecipeChoice.ExactChoice(representative);
    }
    Predicate<ItemRef> test = matching.matches();
    Predicate<ItemStack> matches = (ItemStack item) -> item != null && item.getAmount() > 0
        && item.getType() == base && test.test(BukkitRefs.item(item));
    try {
      return (RecipeChoice) PREDICATE_CHOICE.invoke(null, matches, representative);
    } catch (IllegalAccessException | InvocationTargetException failure) {
      throw new IllegalStateException("Could not register custom recipe ingredient", failure);
    }
  }

  private static Method predicateChoiceFactory() {
    try {
      return RecipeChoice.class.getMethod("predicateChoice", Predicate.class, ItemStack.class);
    } catch (NoSuchMethodException unavailable) {
      return null;
    }
  }

  private List<Material> resolve(Ingredient.AnyOf anyOf) {
    List<Material> resolved = new ArrayList<>(anyOf.types().size());
    for (Id type : anyOf.types().stream().sorted().toList()) {
      Material material = materials.apply(type);
      if (material != null) {
        resolved.add(material);
      }
    }
    return resolved;
  }
}
