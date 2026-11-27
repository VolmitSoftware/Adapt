package art.arcane.adapt.content.golem;

import art.arcane.adapt.platform.entity.AttributeOperation;

import java.util.Objects;

public record GolemStatDelta(GolemStat stat, AttributeOperation operation, double amount) {
  public GolemStatDelta {
    Objects.requireNonNull(stat);
    Objects.requireNonNull(operation);
  }

  public static GolemStatDelta add(GolemStat stat, double amount) {
    return new GolemStatDelta(stat, AttributeOperation.ADD_VALUE, amount);
  }

  public static GolemStatDelta multiplyBase(GolemStat stat, double fraction) {
    return new GolemStatDelta(stat, AttributeOperation.ADD_MULTIPLIED_BASE, fraction);
  }

  public static GolemStatDelta multiplyTotal(GolemStat stat, double fraction) {
    return new GolemStatDelta(stat, AttributeOperation.ADD_MULTIPLIED_TOTAL, fraction);
  }
}
