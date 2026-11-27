package art.arcane.adapt.api.ability;

import org.jetbrains.annotations.ApiStatus;

/**
 * Why a reserved price is given back through {@link AbilityCostProvider#refund(AbilityReceipt, AbilityRefundReason)}.
 * Adaptations pick the reason for their own refunds; Adapt itself uses the ones noted below.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.AvailableSince("1.0.0")
public enum AbilityRefundReason {
  /** The activation was called off; also used for a refund that names no reason. */
  ACTIVATION_ABORTED,
  ACTIVATION_FAILED,
  TARGET_LOST,
  PLAYER_DIED,
  PLAYER_LEFT,
  /** A later provider's reservation or the default cost failed, so earlier reservations of the same use roll back. */
  CHARGE_ROLLBACK,
  /** The adaptation turned off, or a provider with an open charge was unregistered. */
  ADAPTATION_DISABLED,
  /** A deferred charge stayed open for 30 seconds without being settled or refunded. */
  EXPIRED,
  /** The core stopped with deferred charges still open. */
  SERVER_SHUTDOWN
}
