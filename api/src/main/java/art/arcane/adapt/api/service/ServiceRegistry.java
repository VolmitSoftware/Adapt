package art.arcane.adapt.api.service;

import org.jetbrains.annotations.ApiStatus;

import java.util.Optional;

/**
 * Optional services content can use when something provides them: an integration with another plugin, a platform
 * feature only some hosts have. The interface is the key. Content declares the interface, a host or another addon
 * provides the implementation, and content that finds none falls back.
 *
 * <p>Available at any time, also before Adapt has started.
 */
@ApiStatus.AvailableSince("1.0.0")
@ApiStatus.NonExtendable
public interface ServiceRegistry {
  /**
   * Provides a service owned by the addon with {@code namespace}. A later provider of the same type takes over until
   * it withdraws, and the takeover is logged with both owners. Uninstalling the addon withdraws its services.
   *
   * @param type an interface
   */
  <T> ServiceRegistration provide(String namespace, Class<T> type, T implementation);

  /** The current provider of {@code type}; empty when nobody provides one. */
  <T> Optional<T> find(Class<T> type);

  /** The provided service, or {@code fallback} when nobody provides one. */
  <T> T get(Class<T> type, T fallback);
}
