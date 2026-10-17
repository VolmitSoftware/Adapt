package art.arcane.adapt.api.recipe;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.function.Predicate;

public record ExactIngredient(Material base, Predicate<ItemStack> matches) {
  public ExactIngredient {
    Objects.requireNonNull(base);
    Objects.requireNonNull(matches);
  }
}
