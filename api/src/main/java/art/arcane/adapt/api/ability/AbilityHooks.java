package art.arcane.adapt.api.ability;

import org.jetbrains.annotations.ApiStatus;


/**
 * Where other plugins and mods gate and price ability use. Available at any time, also before Adapt has started; a
 * registration holds across core restarts until it is removed.
 *
 * <p>An addon registers with its namespace: uninstalling the addon then removes the hook on every platform, also
 * where all mods share one class loader. A plugin that is not an addon registers without one and unregisters itself.
 */
@ApiStatus.AvailableSince("1.0.0")
@ApiStatus.NonExtendable
public interface AbilityHooks {
  /** Adds a policy. Every policy in scope must allow a use. */
  void registerPolicy(AbilityUsePolicy policy);

  /** Adds a policy owned by the addon with {@code namespace}; uninstalling that addon removes it. */
  void registerPolicy(String namespace, AbilityUsePolicy policy);

  /** Removes a policy; does nothing for one that is not registered. */
  void unregisterPolicy(AbilityUsePolicy policy);

  /**
   * Adds a cost provider. Every provider in scope is asked, in registration order; the first {@code INSUFFICIENT} or
   * {@code DENIED} quote refuses the use. Then every {@code PAYABLE} provider is reserved and charged, and one failed
   * reservation rolls back those before it. A {@code WAIVED} quote or any reservation replaces Adapt's default cost.
   */
  void registerCostProvider(AbilityCostProvider provider);

  /** Adds a cost provider owned by the addon with {@code namespace}; uninstalling that addon removes it. */
  void registerCostProvider(String namespace, AbilityCostProvider provider);

  /** Removes a cost provider; does nothing for one that is not registered. */
  void unregisterCostProvider(AbilityCostProvider provider);
}
