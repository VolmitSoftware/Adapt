package art.arcane.adapt.api.ability;

import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;
import java.util.OptionalLong;

/**
 * A cost provider's answer to {@link AbilityCostProvider#quote(AbilityCostContext)}. Text is cut to 128 characters
 * and control characters become spaces. Made through the factories.
 */
@ApiStatus.AvailableSince("1.0.0")
public final class AbilityQuote {
  private static final AbilityQuote PASS = new AbilityQuote(AbilityQuoteStatus.PASS, "", OptionalLong.empty(), "");

  private final AbilityQuoteStatus status;
  private final String description;
  private final OptionalLong amount;
  private final String unit;

  private AbilityQuote(AbilityQuoteStatus status, String description, OptionalLong amount, String unit) {
    this.status = Objects.requireNonNull(status, "status");
    this.description = AbilityText.sanitize(description);
    if (amount.isPresent() && amount.getAsLong() < 0L) {
      throw new IllegalArgumentException("an ability quote amount must not be negative");
    }
    this.amount = amount;
    this.unit = amount.isPresent() ? AbilityText.sanitize(unit) : "";
  }

  private static AbilityQuote of(AbilityQuoteStatus status, String description) {
    return new AbilityQuote(status, description, OptionalLong.empty(), "");
  }

  /** The provider has no say in this cost; the default cost stays. */
  public static AbilityQuote pass() {
    return PASS;
  }

  /** The cost is free; the default cost is not taken. */
  public static AbilityQuote waived(String description) {
    return of(AbilityQuoteStatus.WAIVED, description);
  }

  /** The provider charges for the cost itself and is asked to reserve the price next. */
  public static AbilityQuote payable(String description) {
    return of(AbilityQuoteStatus.PAYABLE, description);
  }

  /** The player cannot afford the provider's price; the use is refused. */
  public static AbilityQuote insufficient(String description) {
    return of(AbilityQuoteStatus.INSUFFICIENT, description);
  }

  /** The provider refuses the use outright. */
  public static AbilityQuote denied(String reason) {
    return of(AbilityQuoteStatus.DENIED, reason);
  }

  /** A copy of this quote that names its price; {@code amount} must not be negative. */
  public AbilityQuote withPrice(long amount, String unit) {
    return new AbilityQuote(status, description, OptionalLong.of(amount), unit);
  }

  /** What the provider wants to happen to the cost. */
  public AbilityQuoteStatus status() {
    return status;
  }

  /** A short explanation; for a refusal it becomes {@link AbilityCharge#reason()}. */
  public String description() {
    return description;
  }

  /** The price, when the provider names one; never negative. */
  public OptionalLong amount() {
    return amount;
  }

  /** What {@link #amount()} counts, such as {@code mana}; empty when there is no amount. */
  public String unit() {
    return unit;
  }

  /** Whether the status is {@link AbilityQuoteStatus#PAYABLE}. */
  public boolean payable() {
    return status == AbilityQuoteStatus.PAYABLE;
  }

  /** Whether this quote replaces the default cost: true for {@code WAIVED} and {@code PAYABLE}. */
  public boolean suppressesDefaultCost() {
    return switch (status) {
      case WAIVED, PAYABLE -> true;
      default -> false;
    };
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof AbilityQuote quote && status == quote.status && description.equals(quote.description)
        && amount.equals(quote.amount) && unit.equals(quote.unit);
  }

  @Override
  public int hashCode() {
    return Objects.hash(status, description, amount, unit);
  }

  @Override
  public String toString() {
    return "AbilityQuote[status=" + status + ", description=" + description + ", amount=" + amount + ", unit=" + unit
        + "]";
  }
}
