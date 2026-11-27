package art.arcane.adapt.api.ability;

import org.jetbrains.annotations.ApiStatus;

/**
 * Decides whether a player may use an ability right now: a region plugin, a minigame, a combat tag. Asked on every
 * check and activation inside its {@link #scope()}, on the thread that owns the player, so it must be fast.
 *
 * <p>Three checks are answered without calling the policy, by the server's use-policy failure mode (deny by
 * default): a check made off the thread that owns the player, a check made while a policy is being asked (a policy
 * that triggers another ability check), and any check after the policy was quarantined for failing repeatedly. Under
 * the default mode all three deny; each case is logged once per policy.
 */
@ApiStatus.AvailableSince("1.0.0")
@FunctionalInterface
public interface AbilityUsePolicy {
  /**
   * Allows or denies one use. A policy that throws or returns null counts as failed: by default the use is denied,
   * unless the server config lets use-policy failures through, and a policy that keeps failing is quarantined.
   */
  AbilityUseDecision evaluate(AbilityContext context);

  /** Names the policy in logs and in the reason shown for a denial. */
  default String providerId() {
    return getClass().getName();
  }

  /** The abilities this policy is asked about; everything unless overridden. */
  default AbilityScope scope() {
    return AbilityScope.everything();
  }
}
