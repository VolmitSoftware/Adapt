package art.arcane.adapt.api.golem.plinth;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public record Infusion(String id, NamespacedKey result, Supplier<ItemStack> resultItem, InfusionIngredient center,
                       List<InfusionIngredient> pedestals, int essenceLevels, int durationTicks, String unlockAdaptation, int unlockLevel) {
  public Infusion {
    Objects.requireNonNull(id);
    Objects.requireNonNull(result);
    Objects.requireNonNull(resultItem);
    Objects.requireNonNull(center);
    pedestals = List.copyOf(pedestals);
    Objects.requireNonNull(unlockAdaptation);
  }
}
