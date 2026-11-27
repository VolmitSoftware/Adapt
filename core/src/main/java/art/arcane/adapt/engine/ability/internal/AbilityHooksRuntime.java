package art.arcane.adapt.engine.ability.internal;

import art.arcane.adapt.engine.bridge.HostKeys;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.ability.AbilityCostProvider;
import art.arcane.adapt.api.internal.AbilityHookList;
import art.arcane.adapt.api.ability.AbilityUsePolicy;
import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.engine.adaptation.PlayerStateRegistry;
import art.arcane.adapt.platform.PlatformLog;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The engine side of {@code AdaptApi.abilities()}: one use-policy gateway and one cost gateway over the public hook
 * lists, shared by every platform. The lists live in the API jar, so nothing is installed or removed here; a
 * stopping core only refunds the cost tickets still open.
 */
public final class AbilityHooksRuntime {
  private static final PlatformLog LOG = new PlatformLog() {
    @Override
    public void info(String message) {
      AdaptLog.info(message);
    }

    @Override
    public void warn(String message) {
      AdaptLog.warn(message);
    }

    @Override
    public void error(String message, Throwable error) {
      AdaptLog.error(message, error);
    }

    @Override
    public void verbose(String message) {
      AdaptLog.verbose(message);
    }
  };
  private static final AbilityHooksRuntime INSTANCE = create(Engine.abilities(), AbilityHooksRuntime::configured,
      LOG, System::currentTimeMillis, AbilityHooksRuntime::ownsPlayer, PlayerStateRegistry.newPlayerMap());

  private final AbilityUsePolicyGateway usePolicy;
  private final AbilityCostGateway cost;

  private AbilityHooksRuntime(AbilityUsePolicyGateway usePolicy, AbilityCostGateway cost) {
    this.usePolicy = usePolicy;
    this.cost = cost;
  }

  public static AbilityHooksRuntime get() {
    return INSTANCE;
  }

  public static AbilityHooksRuntime create(AbilityHookList hooks, Supplier<AbilityApiPolicy> policy, PlatformLog log,
                                           LongSupplier clock, Predicate<PlayerRef> onOwningThread,
                                           Map<UUID, AbilityDenial> denials) {
    AbilityHookSource<AbilityUsePolicy> policies = new AbilityHookSource<>("use policy", () -> hooks.policies(HostKeys.key()),
        AbilityUsePolicy::providerId, AbilityUsePolicy::scope, log);
    AbilityHookSource<AbilityCostProvider> providers = new AbilityHookSource<>("cost", () -> hooks.costProviders(HostKeys.key()),
        AbilityCostProvider::providerId, AbilityCostProvider::scope, log);
    return new AbilityHooksRuntime(
        new AbilityUsePolicyGateway(policies, policy, new AbilityProviderGuard("use policy", log, clock), log, clock,
            onOwningThread, denials),
        new AbilityCostGateway(providers, policy, new AbilityProviderGuard("cost", log, clock), log, clock,
            onOwningThread));
  }

  public AbilityUsePolicyGateway usePolicy() {
    return usePolicy;
  }

  public AbilityCostGateway cost() {
    return cost;
  }

  static boolean ownsPlayer(PlayerRef player) {
    return player != null && Tasks.isPrimaryThread()
        && (!Tasks.isFoliaThreading() || Tasks.isOwnedByCurrentRegion(player));
  }

  private static AbilityApiPolicy configured() {
    AdaptConfig config = AdaptConfig.get();
    AdaptConfig.AbilityApi api = config == null ? null : config.getAbilityApi();
    if (api == null) {
      return AbilityApiPolicy.defaults();
    }
    return AbilityApiPolicy.of(api.isEnabled(), api.getUsePolicyFailureMode(), api.getCostProviderFailureMode(),
        api.getProviderFaultLimit(), api.getSlowProviderMillis(), api.getDenyMessageThrottleMillis());
  }
}
