package art.arcane.adapt.api.ability;

public interface AbilityCostProvider {
  AbilityQuote quote(AbilityCostContext context);

  default String providerId() {
    return getClass().getName();
  }

  default AbilityScope scope() {
    return AbilityScope.everything();
  }

  default AbilityReservation reserve(AbilityCostContext context, AbilityQuote quote) {
    return AbilityReservation.failed("this provider quoted a price but does not implement reserve");
  }

  default void commit(AbilityReceipt receipt) {
  }

  default void refund(AbilityReceipt receipt, AbilityRefundReason reason) {
  }
}
