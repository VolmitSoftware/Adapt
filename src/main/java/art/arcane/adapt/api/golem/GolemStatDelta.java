package art.arcane.adapt.api.golem;

import org.bukkit.attribute.AttributeModifier;

import java.util.Objects;

public record GolemStatDelta(GolemStat stat, AttributeModifier.Operation operation, double amount) {
  public GolemStatDelta {
    Objects.requireNonNull(stat);
    Objects.requireNonNull(operation);
  }

  public static GolemStatDelta add(GolemStat stat, double amount) {
    return new GolemStatDelta(stat, AttributeModifier.Operation.ADD_NUMBER, amount);
  }

  public static GolemStatDelta multiplyBase(GolemStat stat, double fraction) {
    return new GolemStatDelta(stat, AttributeModifier.Operation.ADD_SCALAR, fraction);
  }

  public static GolemStatDelta multiplyTotal(GolemStat stat, double fraction) {
    return new GolemStatDelta(stat, AttributeModifier.Operation.MULTIPLY_SCALAR_1, fraction);
  }
}
