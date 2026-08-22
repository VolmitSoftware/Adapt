package art.arcane.adapt.api.ability.internal;

import art.arcane.adapt.api.ability.AbilityCostProvider;
import art.arcane.adapt.api.ability.AbilityRefundReason;
import art.arcane.adapt.api.ability.AbilityUsePolicy;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;

public final class AbilityApiRuntime {
  private static volatile AbilityApiRuntime instance;

  private final BukkitAbilityProviderSource<AbilityCostProvider> costSource;
  private final AbilityUsePolicyGateway usePolicy;
  private final AbilityCostGateway cost;
  private final AbilityServiceListener serviceListener;

  private AbilityApiRuntime(BukkitAbilityProviderSource<AbilityCostProvider> costSource,
                            AbilityUsePolicyGateway usePolicy, AbilityCostGateway cost,
                            AbilityServiceListener serviceListener) {
    this.costSource = costSource;
    this.usePolicy = usePolicy;
    this.cost = cost;
    this.serviceListener = serviceListener;
  }

  public static AbilityApiRuntime instance() {
    return instance;
  }

  public static AbilityApiRuntime install(Plugin plugin, Supplier<AbilityApiPolicy> policy,
                                          Map<UUID, AbilityDenial> denials) {
    Objects.requireNonNull(plugin, "plugin");
    Objects.requireNonNull(policy, "policy");
    Objects.requireNonNull(denials, "denials");
    uninstall();
    Logger logger = plugin.getLogger();
    LongSupplier clock = System::currentTimeMillis;
    Predicate<Player> onOwningThread = AbilityApiRuntime::ownsPlayerRegion;
    BukkitAbilityProviderSource<AbilityUsePolicy> useSource = new BukkitAbilityProviderSource<>(AbilityUsePolicy.class,
        "use policy", logger, AbilityUsePolicy::providerId, AbilityUsePolicy::scope);
    BukkitAbilityProviderSource<AbilityCostProvider> costSource =
        new BukkitAbilityProviderSource<>(AbilityCostProvider.class, "cost", logger, AbilityCostProvider::providerId,
            AbilityCostProvider::scope);
    AbilityUsePolicyGateway usePolicy = new AbilityUsePolicyGateway(useSource, policy,
        new AbilityProviderGuard("use policy", logger, clock), logger, clock, onOwningThread, denials);
    AbilityCostGateway cost = new AbilityCostGateway(costSource, policy,
        new AbilityProviderGuard("cost", logger, clock), new BukkitAbilityEventSink(logger), logger, clock,
        onOwningThread);
    AbilityServiceListener listener = new AbilityServiceListener(List.of(useSource, costSource),
        AbilityApiRuntime::notifyUnregistered);
    AbilityApiRuntime installed = new AbilityApiRuntime(costSource, usePolicy, cost, listener);
    Bukkit.getPluginManager().registerEvents(listener, plugin);
    instance = installed;
    return installed;
  }

  public static void uninstall() {
    AbilityApiRuntime current = instance;

    if (current == null) {
      return;
    }

    instance = null;

    if (current.serviceListener != null) {
      HandlerList.unregisterAll(current.serviceListener);
    }

    current.cost.drain(AbilityRefundReason.SERVER_SHUTDOWN);
  }

  public AbilityUsePolicyGateway usePolicy() {
    return usePolicy;
  }

  public AbilityCostGateway cost() {
    return cost;
  }

  static boolean ownsPlayerRegion(Player player) {
    if (player == null || !Bukkit.isPrimaryThread()) {
      return false;
    }

    return !J.isFoliaThreading() || J.isOwnedByCurrentRegion(player);
  }

  private static void notifyUnregistered(Class<?> serviceClass) {
    AbilityApiRuntime current = instance;

    if (current != null) {
      current.onServiceUnregistered(serviceClass);
    }
  }

  private void onServiceUnregistered(Class<?> serviceClass) {
    if (!AbilityCostProvider.class.equals(serviceClass)) {
      return;
    }

    List<AbilityProviderRegistration<AbilityCostProvider>> present = costSource.index().all();
    Set<String> ids = new HashSet<>(present.size());

    for (int index = 0; index < present.size(); index++) {
      ids.add(present.get(index).providerId());
    }

    cost.drainAbsent(ids, AbilityRefundReason.ADAPTATION_DISABLED);
  }
}
