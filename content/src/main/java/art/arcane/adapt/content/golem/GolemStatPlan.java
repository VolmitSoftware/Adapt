package art.arcane.adapt.content.golem;

import art.arcane.adapt.platform.value.Id;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record GolemStatPlan(Map<Id, GolemStatDelta> modifiers) {
  public static final GolemStatPlan EMPTY = new GolemStatPlan(Map.of());
  private static final double EPSILON = 1.0e-9D;

  public GolemStatPlan {
    modifiers = Map.copyOf(modifiers);
  }

  public static GolemStatPlan of(Map<String, List<GolemStatDelta>> deltasBySource) {
    Map<Id, GolemStatDelta> merged = new HashMap<>();
    for (Map.Entry<String, List<GolemStatDelta>> source : deltasBySource.entrySet()) {
      for (GolemStatDelta delta : source.getValue()) {
        merged.merge(GolemStats.key(source.getKey(), delta.stat()), delta, (current, next) -> sum(source.getKey(), current, next));
      }
    }
    merged.values().removeIf(delta -> !Double.isFinite(delta.amount()) || Math.abs(delta.amount()) < EPSILON);
    return merged.isEmpty() ? EMPTY : new GolemStatPlan(merged);
  }

  public Map<Id, GolemStatDelta> modifiers(GolemStat stat) {
    Map<Id, GolemStatDelta> matching = new HashMap<>();
    for (Map.Entry<Id, GolemStatDelta> entry : modifiers.entrySet()) {
      if (entry.getValue().stat() == stat) {
        matching.put(entry.getKey(), entry.getValue());
      }
    }
    return matching;
  }

  private static GolemStatDelta sum(String source, GolemStatDelta current, GolemStatDelta next) {
    if (current.operation() != next.operation()) {
      throw new IllegalArgumentException("Golem stat source " + source + " mixes " + current.operation() + " and " + next.operation()
          + " on " + current.stat().id());
    }
    return new GolemStatDelta(current.stat(), current.operation(), current.amount() + next.amount());
  }
}
