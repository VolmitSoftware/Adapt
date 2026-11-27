package art.arcane.adapt.api.internal;

import art.arcane.adapt.api.ability.AbilityCostProvider;
import art.arcane.adapt.api.ability.AbilityHooks;
import art.arcane.adapt.api.ability.AbilityUsePolicy;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** The registered ability hooks. The engine reads the lists on every ability use. */
@ApiStatus.Internal
public final class AbilityHookList implements AbilityHooks {
  private final CopyOnWriteArrayList<AbilityUsePolicy> policies = new CopyOnWriteArrayList<>();
  private final CopyOnWriteArrayList<AbilityCostProvider> providers = new CopyOnWriteArrayList<>();
  private final Map<Object, String> owners = new ConcurrentHashMap<>();

  AbilityHookList() {
  }

  @Override
  public void registerPolicy(AbilityUsePolicy policy) {
    policies.addIfAbsent(Objects.requireNonNull(policy, "policy"));
  }

  @Override
  public void registerPolicy(String namespace, AbilityUsePolicy policy) {
    String owner = HookOwners.require(namespace);
    registerPolicy(policy);
    owners.put(policy, owner);
  }

  @Override
  public void unregisterPolicy(AbilityUsePolicy policy) {
    policies.remove(policy);
    owners.remove(policy);
  }

  @Override
  public void registerCostProvider(AbilityCostProvider provider) {
    providers.addIfAbsent(Objects.requireNonNull(provider, "provider"));
  }

  @Override
  public void registerCostProvider(String namespace, AbilityCostProvider provider) {
    String owner = HookOwners.require(namespace);
    registerCostProvider(provider);
    owners.put(provider, owner);
  }

  @Override
  public void unregisterCostProvider(AbilityCostProvider provider) {
    providers.remove(provider);
    owners.remove(provider);
  }

  /** The registered policies, for the engine. */
  public List<AbilityUsePolicy> policies(HostKey key) {
    Engine.requireHost(key);
    return allPolicies();
  }

  /** The registered cost providers, for the engine. */
  public List<AbilityCostProvider> costProviders(HostKey key) {
    Engine.requireHost(key);
    return allCostProviders();
  }

  /** The namespace a hook was registered with, or null. */
  public String owner(HostKey key, Object hook) {
    Engine.requireHost(key);
    return ownerOf(hook);
  }

  List<AbilityUsePolicy> allPolicies() {
    return List.copyOf(policies);
  }

  List<AbilityCostProvider> allCostProviders() {
    return List.copyOf(providers);
  }

  String ownerOf(Object hook) {
    return hook == null ? null : owners.get(hook);
  }
}
