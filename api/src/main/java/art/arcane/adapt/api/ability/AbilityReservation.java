package art.arcane.adapt.api.ability;

import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * A cost provider's answer to {@link AbilityCostProvider#reserve(AbilityCostContext, AbilityQuote)}. A failed
 * reservation refuses the use with {@link AbilityOutcome#DENIED_INSUFFICIENT} and rolls back earlier reservations.
 * Made through the factories.
 */
@ApiStatus.AvailableSince("1.0.0")
public final class AbilityReservation {
  private final AbilityReservationStatus status;
  private final AbilityReceipt receipt;
  private final String reason;

  private AbilityReservation(AbilityReservationStatus status, AbilityReceipt receipt, String reason) {
    this.status = status;
    this.receipt = receipt;
    this.reason = AbilityText.sanitize(reason);
  }

  /** The price is held under {@code receipt}. */
  public static AbilityReservation reserved(AbilityReceipt receipt) {
    return new AbilityReservation(AbilityReservationStatus.RESERVED, Objects.requireNonNull(receipt, "receipt"), "");
  }

  /** The price could not be taken. */
  public static AbilityReservation failed(String reason) {
    return new AbilityReservation(AbilityReservationStatus.FAILED, null, reason);
  }

  /** Whether the price is held. */
  public AbilityReservationStatus status() {
    return status;
  }

  /** The receipt of a held price; null for a failed reservation. */
  public AbilityReceipt receipt() {
    return receipt;
  }

  /** Why the price could not be taken, cut to 128 characters; empty when reserved. */
  public String reason() {
    return reason;
  }

  /** Whether the status is {@link AbilityReservationStatus#RESERVED}. */
  public boolean reserved() {
    return status == AbilityReservationStatus.RESERVED;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof AbilityReservation reservation && status == reservation.status
        && Objects.equals(receipt, reservation.receipt) && reason.equals(reservation.reason);
  }

  @Override
  public int hashCode() {
    return Objects.hash(status, receipt, reason);
  }

  @Override
  public String toString() {
    return "AbilityReservation[status=" + status + ", receipt=" + receipt + ", reason=" + reason + "]";
  }
}
