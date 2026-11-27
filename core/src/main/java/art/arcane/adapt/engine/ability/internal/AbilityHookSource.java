package art.arcane.adapt.engine.ability.internal;

import art.arcane.adapt.api.ability.AbilityScope;
import art.arcane.adapt.platform.PlatformLog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Indexes one public hook list ({@code AdaptApi.abilities()} policies or cost providers). The list is read on every
 * use and the index is rebuilt only when its contents changed; two hooks with one provider id keep the first.
 */
public final class AbilityHookSource<T> implements AbilityProviderSource<T> {
  private final String lane;
  private final Supplier<List<T>> hooks;
  private final Function<T, String> providerId;
  private final Function<T, AbilityScope> scope;
  private final PlatformLog log;

  private volatile Snapshot<T> snapshot = new Snapshot<>(List.of(), AbilityProviderIndex.empty());

  public AbilityHookSource(String lane, Supplier<List<T>> hooks, Function<T, String> providerId,
                           Function<T, AbilityScope> scope, PlatformLog log) {
    this.lane = Objects.requireNonNull(lane, "lane");
    this.hooks = Objects.requireNonNull(hooks, "hooks");
    this.providerId = Objects.requireNonNull(providerId, "providerId");
    this.scope = Objects.requireNonNull(scope, "scope");
    this.log = Objects.requireNonNull(log, "log");
  }

  @Override
  public AbilityProviderIndex<T> index() {
    List<T> current = hooks.get();
    Snapshot<T> known = snapshot;

    if (known.hooks().equals(current)) {
      return known.index();
    }

    AbilityProviderIndex<T> rebuilt = new AbilityProviderIndex<>(registrations(current));
    snapshot = new Snapshot<>(current, rebuilt);
    return rebuilt;
  }

  private List<AbilityProviderRegistration<T>> registrations(List<T> current) {
    List<AbilityProviderRegistration<T>> registrations = new ArrayList<>(current.size());
    Set<String> seen = new HashSet<>(current.size());

    for (int index = 0; index < current.size(); index++) {
      T hook = current.get(index);
      String id;
      AbilityScope hookScope;

      try {
        id = providerId.apply(hook);
        hookScope = scope.apply(hook);
      } catch (RuntimeException error) {
        log.error("Skipped an Adapt " + lane + " hook " + hook.getClass().getName()
            + " whose provider id or scope failed", error);
        continue;
      }

      if (id == null || id.isBlank()) {
        log.warn("Skipped an Adapt " + lane + " hook " + hook.getClass().getName() + " with a blank provider id");
        continue;
      }

      if (!seen.add(id)) {
        log.warn("Ignored a second Adapt " + lane + " hook with provider id '" + id + "' ("
            + hook.getClass().getName() + ")");
        continue;
      }

      registrations.add(new AbilityProviderRegistration<>(hook, id, hookScope));
    }

    return registrations;
  }

  private record Snapshot<T>(List<T> hooks, AbilityProviderIndex<T> index) {
  }
}
