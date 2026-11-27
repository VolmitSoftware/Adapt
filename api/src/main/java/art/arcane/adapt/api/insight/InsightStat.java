package art.arcane.adapt.api.insight;

import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * One attribute chip of the readout, keyed by the attribute's id such as {@code minecraft:movement_speed}. The Adapt
 * client gives known vanilla attributes their own glyph and shows any other id by its name.
 */
@ApiStatus.Experimental
public final class InsightStat {
  private final Id attribute;
  private final double value;

  private InsightStat(Id attribute, double value) {
    this.attribute = Objects.requireNonNull(attribute, "attribute");
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException("value of " + attribute + " must be finite, was " + value);
    }
    this.value = value;
  }

  /** A chip showing {@code value}, which must be finite, for {@code attribute}. */
  public static InsightStat of(Id attribute, double value) {
    return new InsightStat(attribute, value);
  }

  /** The attribute shown. */
  public Id attribute() {
    return attribute;
  }

  /** Its current value. */
  public double value() {
    return value;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof InsightStat that && attribute.equals(that.attribute)
        && Double.compare(value, that.value) == 0;
  }

  @Override
  public int hashCode() {
    return Objects.hash(attribute, value);
  }

  @Override
  public String toString() {
    return "InsightStat[" + attribute + " " + value + "]";
  }
}
