package art.arcane.adapt.api.golem;

import art.arcane.adapt.Adapt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class GolemWorkSpeed {
  private static final Map<String, Source> SOURCES = new ConcurrentHashMap<>();
  private static final Set<String> FAILURES_LOGGED = ConcurrentHashMap.newKeySet();

  private GolemWorkSpeed() {
  }

  public static void register(Source source) {
    String id = Objects.requireNonNull(source.id());
    SOURCES.put(id, source);
    FAILURES_LOGGED.remove(id);
  }

  public static void unregister(Source source) {
    SOURCES.remove(source.id(), source);
  }

  public static double intervalMultiplier(GolemHandle handle) {
    if (SOURCES.isEmpty()) {
      return 1D;
    }
    List<Double> multipliers = new ArrayList<>(SOURCES.size());
    for (Source source : SOURCES.values()) {
      try {
        multipliers.add(source.intervalMultiplier(handle));
      } catch (RuntimeException failure) {
        if (FAILURES_LOGGED.add(source.id())) {
          Adapt.warn("Golem work speed source " + source.id() + " failed for bound golem " + handle.id() + "; it is ignored.", failure);
        }
      }
    }
    return combine(multipliers);
  }

  public static double combine(Collection<Double> multipliers) {
    double combined = 1D;
    for (Double multiplier : multipliers) {
      if (multiplier != null && Double.isFinite(multiplier) && multiplier > 0D) {
        combined *= multiplier;
      }
    }
    return combined;
  }

  public interface Source {
    String id();

    double intervalMultiplier(GolemHandle handle);
  }
}
