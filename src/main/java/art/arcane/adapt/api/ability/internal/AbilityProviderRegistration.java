package art.arcane.adapt.api.ability.internal;

import art.arcane.adapt.api.ability.AbilityScope;
import org.bukkit.plugin.ServicePriority;

import java.util.Objects;
import java.util.function.BooleanSupplier;

public record AbilityProviderRegistration<T>(T provider, String providerId, String pluginName,
                                             ServicePriority priority, AbilityScope scope,
                                             BooleanSupplier pluginEnabled) {
  public AbilityProviderRegistration {
    Objects.requireNonNull(provider, "provider");
    Objects.requireNonNull(providerId, "providerId");
    Objects.requireNonNull(pluginName, "pluginName");
    Objects.requireNonNull(priority, "priority");
    Objects.requireNonNull(pluginEnabled, "pluginEnabled");
    scope = scope == null ? AbilityScope.everything() : scope;
  }

  public static <T> AbilityProviderRegistration<T> of(T provider, String providerId, String pluginName,
                                                      ServicePriority priority, AbilityScope scope) {
    return new AbilityProviderRegistration<>(provider, providerId, pluginName, priority, scope, () -> true);
  }

  public boolean unscoped() {
    return scope.unscoped();
  }

  public boolean ownerEnabled() {
    return pluginEnabled.getAsBoolean();
  }

  public boolean matches(String abilityId, String skillId) {
    return scope.matches(abilityId, skillId);
  }
}
