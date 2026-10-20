package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.plinth.InfusionIngredient;
import art.arcane.adapt.api.golem.plinth.InfusionRecipe;
import art.arcane.adapt.api.golem.plinth.InfusionRecipes;
import art.arcane.adapt.api.golem.plinth.PlinthSettings;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

final class InfusionCostFixtures {
  private InfusionCostFixtures() {
  }

  static PlinthSettings none() {
    return settings(Map.of());
  }

  static PlinthSettings defaults() {
    return settings(InfusionRecipe.defaults());
  }

  static PlinthSettings settings(Map<String, InfusionRecipe> specs) {
    return new PlinthSettings(Set.of(), InfusionRecipes.parse(specs, new AnyItems(), message -> {
      throw new AssertionError(message);
    }));
  }

  private static final class AnyItems implements InfusionRecipes.Resolver {
    @Override
    public InfusionIngredient material(String name) {
      return item -> true;
    }

    @Override
    public InfusionIngredient exact(NamespacedKey id) {
      return item -> true;
    }

    @Override
    public Supplier<ItemStack> result(NamespacedKey id) {
      return () -> null;
    }
  }
}
