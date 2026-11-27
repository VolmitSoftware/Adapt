package art.arcane.adapt.api.ability;

import org.jetbrains.annotations.ApiStatus;

/**
 * Replaces or waives what an ability costs: mana instead of hunger, money instead of items.
 *
 * <p>For each cost the engine asks {@link #quote}. A provider that answers {@code PAYABLE} is then asked to
 * {@link #reserve} the price, and later told to {@link #commit} it when the ability went through or to
 * {@link #refund} it when it did not. A provider that passes leaves the ability's default cost in place.
 */
@ApiStatus.AvailableSince("1.0.0")
public interface AbilityCostProvider {
  /**
   * Prices one cost. Called on the thread that owns the player, during the activation, so it must be fast. A
   * provider that throws or returns null counts as failed: by default the use goes ahead with the default cost,
   * unless the server config makes cost failures deny, and a provider that keeps failing is skipped.
   */
  AbilityQuote quote(AbilityCostContext context);

  /** Names the provider in logs and in {@link AbilityCharge}; the class name unless overridden. */
  default String providerId() {
    return getClass().getName();
  }

  /** The abilities this provider is asked about; everything unless overridden. */
  default AbilityScope scope() {
    return AbilityScope.everything();
  }

  /** Takes or holds the quoted price. The receipt comes back in {@link #commit} or {@link #refund}. */
  default AbilityReservation reserve(AbilityCostContext context, AbilityQuote quote) {
    return AbilityReservation.failed("this provider quoted a price but does not implement reserve");
  }

  /** Makes a reserved price final once the ability went through. Exceptions are logged and ignored. */
  default void commit(AbilityReceipt receipt) {
  }

  /**
   * Gives a reserved price back because the ability did not go through. Several reservations of one use are
   * refunded newest first.
   */
  default void refund(AbilityReceipt receipt, AbilityRefundReason reason) {
  }
}
