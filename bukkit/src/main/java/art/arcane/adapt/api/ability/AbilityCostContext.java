package art.arcane.adapt.api.ability;

import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.Optional;

public record AbilityCostContext(AbilityContext ability, String costKey, AbilityCostKind kind,
                                 Optional<ItemStack> defaultItem, int defaultAmount) {
  public AbilityCostContext {
    Objects.requireNonNull(ability, "ability");
    Objects.requireNonNull(kind, "kind");
    costKey = AbilityText.requireId(costKey, "costKey");
    defaultItem = defaultItem == null ? Optional.empty() : defaultItem.map(ItemStack::clone);

    if (defaultAmount < 0) {
      throw new IllegalArgumentException("an ability cost amount must not be negative");
    }
  }

  @Override
  public Optional<ItemStack> defaultItem() {
    return defaultItem.map(ItemStack::clone);
  }
}
