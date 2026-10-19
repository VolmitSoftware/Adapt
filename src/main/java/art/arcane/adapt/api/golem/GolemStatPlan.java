package art.arcane.adapt.api.golem;

import org.bukkit.NamespacedKey;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record GolemStatPlan(Map<NamespacedKey, GolemStatDelta> modifiers) {
  public static final GolemStatPlan EMPTY = new GolemStatPlan(Map.of());
  private static final double EPSILON = 1.0e-9D;

  public GolemStatPlan {
    modifiers = Map.copyOf(modifiers);
  }

  public static GolemStatPlan of(Map<String, List<GolemStatDelta>> deltasBySource) {
    Map<NamespacedKey, GolemStatDelta> merged = new HashMap<>();
    for (Map.Entry<String, List<GolemStatDelta>> source : deltasBySource.entrySet()) {
      for (GolemStatDelta delta : source.getValue()) {
        merged.merge(GolemStats.key(source.getKey(), delta.stat()), delta, (current, next) -> sum(source.getKey(), current, next));
      }
    }
    merged.values().removeIf(delta -> !Double.isFinite(delta.amount()) || Math.abs(delta.amount()) < EPSILON);
    return merged.isEmpty() ? EMPTY : new GolemStatPlan(merged);
  }

  public Map<NamespacedKey, GolemStatDelta> modifiers(GolemStat stat) {
    Map<NamespacedKey, GolemStatDelta> matching = new HashMap<>();
    for (Map.Entry<NamespacedKey, GolemStatDelta> entry : modifiers.entrySet()) {
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
