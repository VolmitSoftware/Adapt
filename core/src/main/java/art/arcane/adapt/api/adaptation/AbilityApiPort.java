package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.api.ability.AbilityCharge;
import art.arcane.adapt.api.ability.AbilityDefaultCost;
import art.arcane.adapt.api.ability.AbilityRefundReason;
import art.arcane.adapt.api.ability.internal.AbilityDenial;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.platform.entity.PlayerRef;

import java.util.UUID;

/**
 * Adapt's public ability API for other plugins: the adaptation-use event, use policies and cost providers. Only
 * the Bukkit plugin hosts third-party providers; without a port every use is allowed and every cost falls back to
 * the adaptation's own default cost.
 */
public interface AbilityApiPort {
  AbilityApiPort NONE = new AbilityApiPort() {
  };

  /** Posts the adaptation-use event to other plugins; false when one cancelled it. */
  default boolean useEventAllows(AdaptPlayer player, Adaptation<?> adaptation) {
    return true;
  }

  default boolean hasUsePolicies(String abilityId, String skillId) {
    return false;
  }

  default boolean allowedByUsePolicy(String abilityId, String skillId, int level, PlayerRef player) {
    return true;
  }

  /** True when cost providers are registered and the cost gateway is not idle. */
  default boolean costsActive() {
    return false;
  }

  default AbilityCharge charge(AbilityCostRequest request, AbilityDefaultCost defaultCost) {
    throw new IllegalStateException("No ability cost gateway is installed");
  }

  default AbilityCharge chargeDeferred(AbilityCostRequest request, AbilityDefaultCost defaultCost) {
    throw new IllegalStateException("No ability cost gateway is installed");
  }

  default boolean settle(UUID activationId) {
    return false;
  }

  default boolean refund(UUID activationId, AbilityRefundReason reason) {
    return false;
  }

  default AbilityDenial lastDenial(UUID playerId) {
    return null;
  }
}
