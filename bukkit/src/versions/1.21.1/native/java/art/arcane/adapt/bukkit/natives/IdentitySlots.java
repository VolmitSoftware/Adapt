package art.arcane.adapt.bukkit.natives;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Per-ingredient stack tests of a crafting recipe with {@link IdentityChoice} ingredients. An identity ingredient is
 * registered as the exact ingredient of its representative (what the client shows); a stack fills it when it passes the
 * choice's test. Every other ingredient keeps its own test.
 */
final class IdentitySlots {
  /** Reads one cell of a crafting grid. */
  @FunctionalInterface
  interface Grid {
    ItemStack at(int x, int y);
  }

  private final List<Predicate<ItemStack>> identities;

  /** {@code identities} lines up with the recipe's ingredients; null entries are ordinary ingredients. */
  IdentitySlots(List<Predicate<ItemStack>> identities) {
    this.identities = new ArrayList<>(identities);
  }

  boolean test(int index, Ingredient ingredient, ItemStack stack) {
    Predicate<ItemStack> identity = identities.get(index);
    return identity == null ? ingredient.test(stack) : !stack.isEmpty() && identity.test(stack);
  }

  /**
   * True when the pattern placed at ({@code offsetX}, {@code offsetY}) of a {@code gridWidth} x {@code gridHeight} grid
   * matches every cell, the cells outside the pattern being empty.
   */
  boolean shaped(List<Ingredient> ingredients, int width, int height, Grid grid, int gridWidth, int gridHeight,
                 int offsetX, int offsetY, boolean mirrored) {
    for (int x = 0; x < gridWidth; x++) {
      for (int y = 0; y < gridHeight; y++) {
        int patternX = x - offsetX;
        int patternY = y - offsetY;
        ItemStack stack = grid.at(x, y);
        if (patternX < 0 || patternY < 0 || patternX >= width || patternY >= height) {
          if (!stack.isEmpty()) {
            return false;
          }
          continue;
        }
        int index = (mirrored ? width - patternX - 1 : patternX) + patternY * width;
        if (!test(index, ingredients.get(index), stack)) {
          return false;
        }
      }
    }
    return true;
  }

  /** True when the non-empty stacks fill every ingredient exactly once, in any order. */
  boolean shapeless(List<Ingredient> ingredients, List<ItemStack> stacks) {
    List<ItemStack> present = new ArrayList<>(stacks.size());
    for (ItemStack stack : stacks) {
      if (!stack.isEmpty()) {
        present.add(stack);
      }
    }
    return present.size() == ingredients.size() && assign(ingredients, present, 0, new boolean[present.size()]);
  }

  private boolean assign(List<Ingredient> ingredients, List<ItemStack> present, int index, boolean[] used) {
    if (index == ingredients.size()) {
      return true;
    }
    for (int stack = 0; stack < present.size(); stack++) {
      if (!used[stack] && test(index, ingredients.get(index), present.get(stack))) {
        used[stack] = true;
        if (assign(ingredients, present, index + 1, used)) {
          return true;
        }
        used[stack] = false;
      }
    }
    return false;
  }

  /**
   * The ingredients for one recipe book placement: each identity ingredient becomes an exact ingredient of its
   * representative plus every {@code available} stack it accepts, so the server's exact-ingredient placement counts and
   * moves those stacks (a renamed one included) and nothing else.
   */
  NonNullList<Ingredient> forPlacement(List<Ingredient> ingredients, List<ItemStack> available) {
    NonNullList<Ingredient> placement = NonNullList.withSize(ingredients.size(), Ingredient.EMPTY);
    for (int index = 0; index < ingredients.size(); index++) {
      Ingredient ingredient = ingredients.get(index);
      if (identities.get(index) == null) {
        placement.set(index, ingredient);
        continue;
      }
      List<ItemStack> accepted = new ArrayList<>(List.of(ingredient.getItems()));
      for (ItemStack stack : available) {
        if (test(index, ingredient, stack)) {
          accepted.add(stack.copyWithCount(1));
        }
      }
      Ingredient expanded = new Ingredient(accepted.stream().map(Ingredient.ItemValue::new));
      expanded.exact = true;
      placement.set(index, expanded);
    }
    return placement;
  }
}
