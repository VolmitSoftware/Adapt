package art.arcane.adapt.api.ability;

import java.util.Objects;

public record AbilityReservation(AbilityReservationStatus status, AbilityReceipt receipt, String reason) {
  public AbilityReservation {
    Objects.requireNonNull(status, "status");
    reason = AbilityText.sanitize(reason);

    if (status == AbilityReservationStatus.RESERVED && receipt == null) {
      throw new IllegalArgumentException("a RESERVED reservation requires a receipt");
    }
  }

  public static AbilityReservation reserved(AbilityReceipt receipt) {
    return new AbilityReservation(AbilityReservationStatus.RESERVED, Objects.requireNonNull(receipt, "receipt"), "");
  }

  public static AbilityReservation failed(String reason) {
    return new AbilityReservation(AbilityReservationStatus.FAILED, null, reason);
  }

  public boolean reserved() {
    return status == AbilityReservationStatus.RESERVED;
  }
}
