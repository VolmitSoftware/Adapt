package art.arcane.adapt.engine.ability;

import art.arcane.adapt.api.ability.AbilityContext;
import art.arcane.adapt.api.ability.AbilityCostContext;
import art.arcane.adapt.api.ability.AbilityCostKind;
import art.arcane.adapt.platform.item.ItemRef;

import java.util.Objects;
import java.util.Optional;

public record EngineAbilityCostContext(AbilityContext ability, String costKey, AbilityCostKind kind,
                                       Optional<ItemRef> defaultItem, int defaultAmount) implements AbilityCostContext {
  public EngineAbilityCostContext {
    Objects.requireNonNull(ability, "ability");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(defaultItem, "defaultItem");
    costKey = AbilityStrings.requireId(costKey, "costKey");
    defaultItem = defaultItem.map(ItemRef::copy);
    if (defaultAmount < 0) {
      throw new IllegalArgumentException("an ability cost amount must not be negative");
    }
  }
}
