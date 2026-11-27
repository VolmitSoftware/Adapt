package art.arcane.adapt.engine.ability.internal;

import art.arcane.adapt.api.ability.AbilityScope;
import art.arcane.adapt.platform.value.Id;

import java.util.Objects;

public record AbilityProviderRegistration<T>(T provider, String providerId, AbilityScope scope) {
  public AbilityProviderRegistration {
    Objects.requireNonNull(provider, "provider");
    Objects.requireNonNull(providerId, "providerId");
    scope = scope == null ? AbilityScope.everything() : scope;
  }

  public boolean unscoped() {
    return scope.isEverything();
  }

  public boolean matches(Id ability, Id skill) {
    return scope.matches(ability, skill);
  }
}
