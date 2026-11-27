package art.arcane.adapt.engine.ability.internal;

import art.arcane.adapt.platform.value.Id;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class AbilityProviderIndex<T> {
  private static final AbilityProviderIndex<?> EMPTY = new AbilityProviderIndex<>(List.of());
  private static final int MAX_MEMO_ENTRIES = 4096;

  private final List<AbilityProviderRegistration<T>> ordered;
  private final List<AbilityProviderRegistration<T>> unscoped;
  private final boolean scopedPresent;
  private final Map<Id, Memo<T>> memo = new ConcurrentHashMap<>();

  public AbilityProviderIndex(List<AbilityProviderRegistration<T>> ordered) {
    this.ordered = ordered == null ? List.of() : List.copyOf(ordered);
    List<AbilityProviderRegistration<T>> everything = new ArrayList<>(this.ordered.size());
    boolean scoped = false;

    for (int index = 0; index < this.ordered.size(); index++) {
      AbilityProviderRegistration<T> registration = this.ordered.get(index);

      if (registration.unscoped()) {
        everything.add(registration);
        continue;
      }

      scoped = true;
    }

    this.unscoped = List.copyOf(everything);
    this.scopedPresent = scoped;
  }

  @SuppressWarnings("unchecked")
  public static <T> AbilityProviderIndex<T> empty() {
    return (AbilityProviderIndex<T>) EMPTY;
  }

  public boolean isEmpty() {
    return ordered.isEmpty();
  }

  public int size() {
    return ordered.size();
  }

  public List<AbilityProviderRegistration<T>> all() {
    return ordered;
  }

  public List<AbilityProviderRegistration<T>> matching(Id ability, Id skill) {
    if (ordered.isEmpty()) {
      return List.of();
    }

    if (!scopedPresent) {
      return unscoped;
    }

    Memo<T> cached = memo.get(ability);

    if (cached != null && Objects.equals(cached.skill(), skill)) {
      return cached.registrations();
    }

    List<AbilityProviderRegistration<T>> resolved = new ArrayList<>(ordered.size());

    for (int index = 0; index < ordered.size(); index++) {
      AbilityProviderRegistration<T> registration = ordered.get(index);

      if (registration.matches(ability, skill)) {
        resolved.add(registration);
      }
    }

    List<AbilityProviderRegistration<T>> immutable = List.copyOf(resolved);

    if (memo.size() < MAX_MEMO_ENTRIES) {
      memo.putIfAbsent(ability, new Memo<>(skill, immutable));
    }

    return immutable;
  }

  private record Memo<T>(Id skill, List<AbilityProviderRegistration<T>> registrations) {
  }
}
