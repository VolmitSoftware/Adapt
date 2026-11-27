package art.arcane.adapt.api.protection;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;

/**
 * The protections adaptations respect. Available at any time, also before Adapt has started; a registration holds
 * across core restarts until it is removed.
 *
 * <p>An addon registers with its namespace: uninstalling the addon then removes the protector on every platform,
 * also where all mods share one class loader. A plugin that is not an addon registers without one and unregisters
 * itself.
 */
@ApiStatus.AvailableSince("1.0.0")
@ApiStatus.NonExtendable
public interface ProtectionHooks {
  /**
   * Adds a protector. An adaptation acts only when every protector its config enables allows it.
   *
   * @throws IllegalArgumentException when a protector with the same name is registered
   */
  void register(Protector protector);

  /**
   * Adds a protector owned by the addon with {@code namespace}; uninstalling that addon removes it.
   *
   * @throws IllegalArgumentException when a protector with the same name is registered
   */
  void register(String namespace, Protector protector);

  /** Removes a protector; does nothing for one that is not registered. */
  void unregister(Protector protector);

  /**
   * The names of the registered protectors, in registration order, for admin config. Only the code that registered a
   * protector holds it, so only that code can unregister it.
   */
  List<String> names();
}
