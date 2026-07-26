package art.arcane.adapt.api.ability.internal;

public record AbilityApiPolicy(boolean enabled, AbilityFailureMode usePolicyFailureMode,
                               AbilityFailureMode costProviderFailureMode, int providerFaultLimit,
                               long slowProviderMillis, long denyThrottleMillis) {
  public static final boolean DEFAULT_ENABLED = true;
  public static final String DEFAULT_USE_POLICY_FAILURE_MODE = "deny";
  public static final String DEFAULT_COST_PROVIDER_FAILURE_MODE = "allow";
  public static final int DEFAULT_PROVIDER_FAULT_LIMIT = 5;
  public static final long DEFAULT_SLOW_PROVIDER_MILLIS = 2L;
  public static final long DEFAULT_DENY_THROTTLE_MILLIS = 2_000L;
  public static final int MAX_PROVIDER_FAULT_LIMIT = 1000;
  public static final long MAX_SLOW_PROVIDER_MILLIS = 60_000L;

  private static final AbilityApiPolicy DEFAULTS = new AbilityApiPolicy(DEFAULT_ENABLED, AbilityFailureMode.DENY,
      AbilityFailureMode.ALLOW, DEFAULT_PROVIDER_FAULT_LIMIT, DEFAULT_SLOW_PROVIDER_MILLIS,
      DEFAULT_DENY_THROTTLE_MILLIS);

  public AbilityApiPolicy {
    usePolicyFailureMode = usePolicyFailureMode == null ? AbilityFailureMode.DENY : usePolicyFailureMode;
    costProviderFailureMode = costProviderFailureMode == null ? AbilityFailureMode.ALLOW : costProviderFailureMode;
    providerFaultLimit = Math.max(0, Math.min(MAX_PROVIDER_FAULT_LIMIT, providerFaultLimit));
    slowProviderMillis = Math.max(0L, Math.min(MAX_SLOW_PROVIDER_MILLIS, slowProviderMillis));
    denyThrottleMillis = Math.max(0L, denyThrottleMillis);
  }

  public static AbilityApiPolicy defaults() {
    return DEFAULTS;
  }

  public static AbilityApiPolicy of(boolean enabled, String usePolicyFailureMode, String costProviderFailureMode,
                                    int providerFaultLimit, long slowProviderMillis, long denyThrottleMillis) {
    return new AbilityApiPolicy(enabled, AbilityFailureMode.parse(usePolicyFailureMode, AbilityFailureMode.DENY),
        AbilityFailureMode.parse(costProviderFailureMode, AbilityFailureMode.ALLOW), providerFaultLimit,
        slowProviderMillis, denyThrottleMillis);
  }

  public boolean useFailClosed() {
    return usePolicyFailureMode == AbilityFailureMode.DENY;
  }

  public boolean costFailClosed() {
    return costProviderFailureMode == AbilityFailureMode.DENY;
  }

  public boolean quarantineEnabled() {
    return providerFaultLimit > 0;
  }

  public boolean watchdogEnabled() {
    return slowProviderMillis > 0L;
  }
}
