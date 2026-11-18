package art.arcane.adapt.api.ability;

import java.util.Objects;
import java.util.OptionalLong;

public record AbilityQuote(AbilityQuoteStatus status, String description, OptionalLong amount, String unit) {
  private static final AbilityQuote PASS = new AbilityQuote(AbilityQuoteStatus.PASS, "");

  public AbilityQuote {
    Objects.requireNonNull(status, "status");
    description = AbilityText.sanitize(description);
    amount = amount == null ? OptionalLong.empty() : amount;

    if (amount.isPresent() && amount.getAsLong() < 0L) {
      throw new IllegalArgumentException("an ability quote amount must not be negative");
    }

    unit = amount.isPresent() ? AbilityText.sanitize(unit) : "";
  }

  public AbilityQuote(AbilityQuoteStatus status, String description) {
    this(status, description, OptionalLong.empty(), "");
  }

  public static AbilityQuote pass() {
    return PASS;
  }

  public static AbilityQuote waived(String description) {
    return new AbilityQuote(AbilityQuoteStatus.WAIVED, description);
  }

  public static AbilityQuote payable(String description) {
    return new AbilityQuote(AbilityQuoteStatus.PAYABLE, description);
  }

  public static AbilityQuote insufficient(String description) {
    return new AbilityQuote(AbilityQuoteStatus.INSUFFICIENT, description);
  }

  public static AbilityQuote denied(String reason) {
    return new AbilityQuote(AbilityQuoteStatus.DENIED, reason);
  }

  public AbilityQuote withPrice(long amount, String unit) {
    return new AbilityQuote(status, description, OptionalLong.of(amount), unit);
  }

  public boolean payable() {
    return status == AbilityQuoteStatus.PAYABLE;
  }

  public boolean suppressesDefaultCost() {
    return switch (status) {
      case WAIVED, PAYABLE -> true;
      default -> false;
    };
  }
}
