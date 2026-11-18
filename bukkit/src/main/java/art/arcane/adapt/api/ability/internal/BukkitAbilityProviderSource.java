package art.arcane.adapt.api.ability.internal;

import art.arcane.adapt.api.ability.AbilityScope;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class BukkitAbilityProviderSource<T> implements AbilityProviderSource<T> {
  private final Class<T> service;
  private final String lane;
  private final Logger logger;
  private final Function<T, String> providerIdOf;
  private final Function<T, AbilityScope> scopeOf;
  private final Set<String> warned = ConcurrentHashMap.newKeySet();

  private volatile AbilityProviderIndex<T> cached;

  public BukkitAbilityProviderSource(Class<T> service, String lane, Logger logger, Function<T, String> providerIdOf,
                                     Function<T, AbilityScope> scopeOf) {
    this.service = Objects.requireNonNull(service, "service");
    this.lane = Objects.requireNonNull(lane, "lane");
    this.logger = Objects.requireNonNull(logger, "logger");
    this.providerIdOf = Objects.requireNonNull(providerIdOf, "providerIdOf");
    this.scopeOf = Objects.requireNonNull(scopeOf, "scopeOf");
  }

  public Class<T> service() {
    return service;
  }

  @Override
  public AbilityProviderIndex<T> index() {
    AbilityProviderIndex<T> snapshot = cached;

    if (snapshot != null) {
      return snapshot;
    }

    try {
      snapshot = rebuild();
    } catch (Throwable error) {
      snapshot = AbilityProviderIndex.empty();
      logger.log(Level.SEVERE, "Adapt could not build its " + lane + " provider list from the ServicesManager; "
          + "every " + lane + " provider is being treated as absent until the service registrations change", error);
    }

    cached = snapshot;
    return snapshot;
  }

  public void invalidate() {
    cached = null;
  }

  private AbilityProviderIndex<T> rebuild() {
    Collection<RegisteredServiceProvider<T>> raw = Bukkit.getServicesManager().getRegistrations(service);

    if (raw.isEmpty()) {
      return AbilityProviderIndex.empty();
    }

    List<AbilityProviderRegistration<T>> mapped = new ArrayList<>(raw.size());

    for (RegisteredServiceProvider<T> registration : raw) {
      AbilityProviderRegistration<T> resolved = map(registration);

      if (resolved != null) {
        mapped.add(resolved);
      }
    }

    return new AbilityProviderIndex<>(AbilityProviderRegistrations.order(mapped, this::reportDuplicate));
  }

  private AbilityProviderRegistration<T> map(RegisteredServiceProvider<T> registration) {
    try {
      T provider = registration.getProvider();
      Plugin owner = registration.getPlugin();

      if (provider == null || owner == null) {
        return null;
      }

      String pluginName = owner.getName();
      String providerId = resolveProviderId(provider, pluginName);

      if (providerId == null) {
        return null;
      }

      return new AbilityProviderRegistration<>(provider, providerId, pluginName, registration.getPriority(),
          resolveScope(provider, pluginName, providerId), owner::isEnabled);
    } catch (Throwable error) {
      logger.log(Level.WARNING, "Ignoring one Adapt " + lane + " service registration because reading it threw", error);
      return null;
    }
  }

  String resolveProviderId(T provider, String pluginName) {
    try {
      String providerId = providerIdOf.apply(provider);

      if (providerId == null || providerId.isBlank()) {
        logger.warning("Ignoring Adapt " + lane + " provider from plugin '" + pluginName
            + "' because providerId() returned a blank value");
        return null;
      }

      String resolved = providerId.strip();
      warnAboutGeneratedId(provider, pluginName, resolved);
      return resolved;
    } catch (Throwable error) {
      logger.log(Level.WARNING, "Ignoring Adapt " + lane + " provider from plugin '" + pluginName
          + "' because providerId() threw", error);
      return null;
    }
  }

  AbilityScope resolveScope(T provider, String pluginName, String providerId) {
    AbilityScope scope;

    try {
      scope = scopeOf.apply(provider);
    } catch (Throwable error) {
      logger.log(Level.WARNING, "Adapt " + lane + " provider '" + providerId + "' from plugin '" + pluginName
          + "' threw from scope(); treating it as unscoped", error);
      scope = AbilityScope.everything();
    }

    if (scope == null) {
      scope = AbilityScope.everything();
    }

    if (scope.unscoped() && warned.add(lane + "#unscoped#" + providerId)) {
      logger.warning("Adapt " + lane + " provider '" + providerId + "' from plugin '" + pluginName
          + "' declared no scope(), so Adapt must consult it for every adaptation check on every player. "
          + "Return AbilityScope.abilities(...) or AbilityScope.skill(...) unless you genuinely need all of them");
    }

    return scope;
  }

  private void warnAboutGeneratedId(T provider, String pluginName, String providerId) {
    Class<?> type = provider.getClass();

    if (!providerId.equals(type.getName()) || !generated(type) || !warned.add(lane + "#generated#" + pluginName)) {
      return;
    }

    logger.warning("Adapt " + lane + " provider from plugin '" + pluginName + "' did not override providerId(); "
        + "using the generated name '" + providerId + "', which changes on every restart and on unrelated edits "
        + "to that plugin. Override providerId() if this provider moves value");
  }

  private static boolean generated(Class<?> type) {
    return type.isHidden() || type.isSynthetic() || type.isAnonymousClass() || type.isLocalClass();
  }

  private void reportDuplicate(AbilityProviderRegistration<T> kept, AbilityProviderRegistration<T> dropped) {
    logger.warning("Two Adapt " + lane + " providers claim the id '" + dropped.providerId() + "'; keeping the one "
        + "from '" + (kept == null ? "unknown" : kept.pluginName()) + "' and ignoring the one from '"
        + dropped.pluginName() + "'");
  }
}
