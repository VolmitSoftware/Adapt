package art.arcane.adapt.bukkit.port;

import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.value.MaterialCount;
import art.arcane.adapt.api.value.MaterialRecipe;
import art.arcane.adapt.api.value.MaterialValuePort;
import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.platform.value.Id;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.StonecuttingRecipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class BukkitMaterialValuePort implements MaterialValuePort {
  private static final String ADAPT_NAMESPACE = "adapt";

  @Override
  public List<MaterialRecipe> recipesFor(Id material) {
    Material mat = BukkitRefs.material(material);
    if (mat == null) {
      return List.of();
    }
    List<MaterialRecipe> recipes = new ArrayList<>();
    ItemStack stack = new ItemStack(mat);
    try {
      stack.setDurability((short) -1);
    } catch (Throwable e) {
      AdaptLog.verbose("Failed to set durability of " + mat.name());
    }
    for (Recipe recipe : BukkitRecipeOrder.canonical(Bukkit.getRecipesFor(stack))) {
      if (isAdaptRecipe(recipe)) {
        AdaptLog.verbose("Skipping Adapt Recipe to prevent duplicates, " + mat.name() + " -> " + ((Keyed) recipe).getKey());
        continue;
      }
      MaterialRecipe converted = toMaterial(recipe);
      if (converted != null) {
        recipes.add(converted);
      }
    }
    return recipes;
  }

  @Override
  public boolean breaksInstantly(Id material) {
    Material mat = BukkitRefs.material(material);
    return mat != null && mat.isBlock() && mat.getHardness() == 0;
  }

  static boolean isAdaptRecipe(Recipe recipe) {
    return recipe instanceof Keyed keyed && keyed.getKey() != null && ADAPT_NAMESPACE.equals(keyed.getKey().getNamespace());
  }

  private static MaterialCount count(ItemStack stack) {
    return new MaterialCount(BukkitRefs.id(stack.getType()), stack.getAmount());
  }

  private static MaterialCount one(ItemStack stack) {
    return new MaterialCount(BukkitRefs.id(stack.getType()), 1);
  }

  private static MaterialRecipe toMaterial(Recipe r) {
    try {
      if (r instanceof ShapelessRecipe recipe) {
        return MaterialRecipe.builder()
            .input(new ArrayList<>(recipe.getIngredientList().stream().map(BukkitMaterialValuePort::one).toList()))
            .output(count(recipe.getResult()))
            .build();
      } else if (r instanceof ShapedRecipe recipe) {
        MaterialRecipe converted = MaterialRecipe.builder()
            .input(new ArrayList<>())
            .output(count(recipe.getResult()))
            .build();
        Map<Material, Integer> counts = new HashMap<>();
        Map<Character, ItemStack> ingredients = recipe.getIngredientMap();
        for (String row : recipe.getShape()) {
          for (int column = 0; column < row.length(); column++) {
            ItemStack ingredient = ingredients.get(row.charAt(column));
            if (ingredient != null && !ingredient.getType().isAir()) {
              counts.merge(ingredient.getType(), 1, Integer::sum);
            }
          }
        }
        counts.forEach((material, amount) -> converted.getInput().add(new MaterialCount(BukkitRefs.id(material), amount)));
        return converted;
      } else if (r instanceof CookingRecipe<?> recipe) {
        List<MaterialCount> input = new ArrayList<>();
        input.add(one(recipe.getInput()));
        return MaterialRecipe.builder().input(input).output(count(recipe.getResult())).build();
      } else if (r instanceof MerchantRecipe recipe) {
        return MaterialRecipe.builder()
            .input(new ArrayList<>(recipe.getIngredients().stream().map(BukkitMaterialValuePort::one).toList()))
            .output(count(recipe.getResult()))
            .build();
      } else if (r instanceof StonecuttingRecipe recipe) {
        List<MaterialCount> input = new ArrayList<>();
        input.add(one(recipe.getInput()));
        return MaterialRecipe.builder().input(input).output(count(recipe.getResult())).build();
      }
    } catch (Throwable e) {
      AdaptLog.error(e);
    }
    return null;
  }
}
