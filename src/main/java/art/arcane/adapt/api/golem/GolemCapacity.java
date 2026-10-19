package art.arcane.adapt.api.golem;

import art.arcane.adapt.Adapt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class GolemCapacity {
  private static final Map<String, Source> SOURCES = new ConcurrentHashMap<>();
  private static final Set<String> FAILURES_LOGGED = ConcurrentHashMap.newKeySet();

  private GolemCapacity() {
  }

  public static void register(Source source) {
    String id = Objects.requireNonNull(source.id());
    SOURCES.put(id, source);
    FAILURES_LOGGED.remove(id);
  }

  public static void unregister(Source source) {
    SOURCES.remove(source.id(), source);
  }

  public static int carry(GolemHandle handle) {
    List<Contribution> contributions = new ArrayList<>(SOURCES.size());
    for (Source source : SOURCES.values()) {
      try {
        contributions.add(source.carry(handle));
      } catch (RuntimeException failure) {
        if (FAILURES_LOGGED.add(source.id())) {
          Adapt.warn("Golem carry source " + source.id() + " failed for bound golem " + handle.id() + "; it adds nothing.", failure);
        }
      }
    }
    return carry(GolemancyRuntime.settings().bodyCarry(handle.body()), contributions);
  }

  public static int carry(int baseCarry, Collection<Contribution> contributions) {
    int minimum = Math.max(0, baseCarry);
    double bonus = 0D;
    for (Contribution contribution : contributions) {
      if (contribution != null) {
        minimum = Math.max(minimum, contribution.minimum());
        bonus += contribution.bonusFraction();
      }
    }
    double carry = minimum * Math.max(0D, 1D + bonus);
    return (int) Math.min(Integer.MAX_VALUE, Math.floor(carry));
  }

  public interface Source {
    String id();

    Contribution carry(GolemHandle handle);
  }

  public record Contribution(int minimum, double bonusFraction) {
    public static final Contribution NONE = new Contribution(0, 0D);

    public Contribution {
      minimum = Math.max(0, minimum);
      bonusFraction = Double.isFinite(bonusFraction) ? bonusFraction : 0D;
    }

    public static Contribution atLeast(int carry) {
      return new Contribution(carry, 0D);
    }

    public static Contribution bonus(double fraction) {
      return new Contribution(0, fraction);
    }
  }
}
