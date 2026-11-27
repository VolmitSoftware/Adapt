package art.arcane.adapt.api.internal;

import art.arcane.adapt.api.service.ServiceRegistration;
import art.arcane.adapt.api.service.ServiceRegistry;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** The provided services, newest provider of each type last, each bound to the namespace that provided it. */
@ApiStatus.Internal
public final class Services implements ServiceRegistry {
  private final Map<Class<?>, CopyOnWriteArrayList<Provider>> providers = new ConcurrentHashMap<>();

  Services() {
  }

  @Override
  public <T> ServiceRegistration provide(String namespace, Class<T> type, T implementation) {
    String owner = Id.of(Objects.requireNonNull(namespace, "namespace"), "service").namespace();
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(implementation, "implementation");
    if (!type.isInterface()) {
      throw new IllegalArgumentException("A service type must be an interface: " + type.getName());
    }
    CopyOnWriteArrayList<Provider> list = providers.computeIfAbsent(type,
        (Class<?> added) -> new CopyOnWriteArrayList<>());
    Provider provider = new Provider(owner, implementation);
    Provider previous;
    synchronized (list) {
      previous = list.isEmpty() ? null : list.get(list.size() - 1);
      list.add(provider);
    }
    if (previous != null) {
      Engine.log().warn("Service " + type.getName() + ": " + describe(provider) + " takes over from "
          + describe(previous));
    }
    return () -> list.remove(provider);
  }

  @Override
  public <T> Optional<T> find(Class<T> type) {
    List<Provider> list = providers.get(Objects.requireNonNull(type, "type"));
    if (list == null) {
      return Optional.empty();
    }
    Object[] snapshot = list.toArray();
    return snapshot.length == 0
        ? Optional.empty()
        : Optional.of(type.cast(((Provider) snapshot[snapshot.length - 1]).implementation()));
  }

  @Override
  public <T> T get(Class<T> type, T fallback) {
    return find(type).orElse(fallback);
  }

  /** Withdraws every service the namespace provided; runs when its addon is uninstalled. */
  void withdrawAll(String namespace) {
    for (CopyOnWriteArrayList<Provider> list : providers.values()) {
      list.removeIf((Provider provider) -> provider.namespace().equals(namespace));
    }
  }

  private static String describe(Provider provider) {
    return provider.namespace() + " (" + provider.implementation().getClass().getName() + ")";
  }

  private static final class Provider {
    private final String namespace;
    private final Object implementation;

    private Provider(String namespace, Object implementation) {
      this.namespace = namespace;
      this.implementation = implementation;
    }

    String namespace() {
      return namespace;
    }

    Object implementation() {
      return implementation;
    }
  }
}
