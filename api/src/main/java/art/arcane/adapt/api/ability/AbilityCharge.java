package art.arcane.adapt.api.ability;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.UUID;

/**
 * What charging one ability cost came to, as returned by a deferred charge. Pass {@link #activationId()} to
 * {@link art.arcane.adapt.api.content.Adaptation#settleCost(java.util.UUID)} once the ability went through, or to
 * {@link art.arcane.adapt.api.content.Adaptation#refundCost(java.util.UUID, AbilityRefundReason)} when it did not.
 * A deferred charge left open for 30 seconds is refunded with {@link AbilityRefundReason#EXPIRED} the next time a
 * cost is charged. Adapt creates charges; new accessors may be added.
 */
@ApiStatus.AvailableSince("1.0.0")
@ApiStatus.NonExtendable
public interface AbilityCharge {
  /** Identifies the open charge for the settle or refund. */
  UUID activationId();

  /** How the charge ended. */
  AbilityOutcome outcome();

  /** True when a provider waived or took the price, so the default cost was not taken. */
  boolean defaultCostSuppressed();

  /** The provider's description of a refusal, or empty. */
  String reason();

  /** The provider that refused or failed, or empty. */
  String providerId();

  /** The providers that reserved a price, in the order they were asked. */
  List<String> chargedProviderIds();

  /** Whether the ability may go ahead: the outcome is one of the allowing ones. */
  default boolean allowed() {
    return outcome().allowed();
  }
}
