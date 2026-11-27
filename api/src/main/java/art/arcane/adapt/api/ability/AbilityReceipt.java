package art.arcane.adapt.api.ability;

import org.jetbrains.annotations.ApiStatus;

/**
 * A cost provider's own record of a reserved price. Adapt hands it back unchanged to
 * {@link AbilityCostProvider#commit(AbilityReceipt)} or
 * {@link AbilityCostProvider#refund(AbilityReceipt, AbilityRefundReason)}; implement it to carry whatever the
 * provider needs to finish or undo the payment.
 */
@ApiStatus.AvailableSince("1.0.0")
public interface AbilityReceipt {
  /** A receipt that carries only a label, for providers that need nothing more. */
  static AbilityReceipt of(String label) {
    return new SimpleReceipt(label);
  }

  /**
   * The receipt {@link #of(String)} returns.
   *
   * @param label cut to 128 characters, control characters replaced by spaces
   */
  record SimpleReceipt(String label) implements AbilityReceipt {
    public SimpleReceipt {
      label = AbilityText.sanitize(label);
    }
  }
}
