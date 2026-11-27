package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.content.golem.plinth.InfusionIngredient;
import art.arcane.adapt.content.golem.plinth.InfusionRecipe;
import art.arcane.adapt.content.golem.plinth.InfusionRecipes;
import art.arcane.adapt.content.golem.plinth.PlinthSettings;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.item.ItemRef;

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
    public InfusionIngredient exact(Id id) {
      return item -> true;
    }

    @Override
    public Supplier<ItemRef> result(Id id) {
      return () -> null;
    }
  }
}
