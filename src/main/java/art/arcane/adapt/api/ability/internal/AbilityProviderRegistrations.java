package art.arcane.adapt.api.ability.internal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

public final class AbilityProviderRegistrations {
  private AbilityProviderRegistrations() {
  }

  public static <T> List<AbilityProviderRegistration<T>> order(List<AbilityProviderRegistration<T>> raw,
                                                               BiConsumer<AbilityProviderRegistration<T>, AbilityProviderRegistration<T>> duplicateReporter) {
    if (raw == null || raw.isEmpty()) {
      return List.of();
    }

    List<AbilityProviderRegistration<T>> sorted = new ArrayList<>(raw);
    sorted.sort(order());
    return dedupe(sorted, duplicateReporter);
  }

  public static <T> List<AbilityProviderRegistration<T>> dedupe(List<AbilityProviderRegistration<T>> raw,
                                                                BiConsumer<AbilityProviderRegistration<T>, AbilityProviderRegistration<T>> duplicateReporter) {
    if (raw == null || raw.isEmpty()) {
      return List.of();
    }

    if (raw.size() == 1) {
      return List.copyOf(raw);
    }

    List<AbilityProviderRegistration<T>> deduped = new ArrayList<>(raw.size());
    Set<String> seenIds = new HashSet<>(raw.size());
    Set<ProviderIdentity> seenProviders = new HashSet<>(raw.size());

    for (AbilityProviderRegistration<T> registration : raw) {
      if (!seenProviders.add(new ProviderIdentity(registration.provider()))) {
        continue;
      }

      if (!seenIds.add(registration.providerId())) {
        if (duplicateReporter != null) {
          duplicateReporter.accept(findById(deduped, registration.providerId()), registration);
        }
        continue;
      }

      deduped.add(registration);
    }

    return List.copyOf(deduped);
  }

  private static <T> Comparator<AbilityProviderRegistration<T>> order() {
    return Comparator.comparingInt((AbilityProviderRegistration<T> registration) -> registration.priority().ordinal())
        .reversed()
        .thenComparing(AbilityProviderRegistration::pluginName)
        .thenComparing(AbilityProviderRegistration::providerId);
  }

  private static <T> AbilityProviderRegistration<T> findById(List<AbilityProviderRegistration<T>> kept,
                                                             String providerId) {
    for (AbilityProviderRegistration<T> registration : kept) {
      if (registration.providerId().equals(providerId)) {
        return registration;
      }
    }

    return null;
  }

  private record ProviderIdentity(Object provider) {
    @Override
    public boolean equals(Object other) {
      return other instanceof ProviderIdentity identity && identity.provider == provider;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(provider);
    }
  }
}
