package art.arcane.adapt.api.golem.plinth;

import art.arcane.adapt.platform.value.Id;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public record PlinthSettings(Set<Id> pedestals, InfusionRecipes recipes) {
  public PlinthSettings {
    pedestals = Set.copyOf(pedestals);
    Objects.requireNonNull(recipes);
  }

  public static PlinthSettings defaults() {
    Set<Id> pedestals = new LinkedHashSet<>();
    for (String name : PlinthStructure.defaultPedestals()) {
      pedestals.add(Id.minecraft(name.toLowerCase(Locale.ROOT)));
    }
    return new PlinthSettings(pedestals, InfusionRecipes.EMPTY);
  }
}
