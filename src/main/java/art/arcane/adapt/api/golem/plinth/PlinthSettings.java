package art.arcane.adapt.api.golem.plinth;

import art.arcane.adapt.Adapt;
import org.bukkit.Material;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public record PlinthSettings(Set<Material> pedestals, InfusionRecipes recipes) {
  public PlinthSettings {
    EnumSet<Material> copy = EnumSet.noneOf(Material.class);
    copy.addAll(pedestals);
    pedestals = Collections.unmodifiableSet(copy);
    Objects.requireNonNull(recipes);
  }

  public static PlinthSettings defaults() {
    return new PlinthSettings(PlinthStructure.pedestals(PlinthStructure.DEFAULT_PEDESTALS, material -> true, Adapt::warn), InfusionRecipes.EMPTY);
  }
}
