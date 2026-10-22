package art.arcane.adapt.api.xp;


import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class CombatXpLedger {
  public static final long WINDOW_MILLIS = 600_000L;
  private static final int MAX_ENTRIES = 256;

  private final Map<String, DamageBudget> victims = new LinkedHashMap<>();

  public synchronized double claim(String skill, UUID victim, double damage, double healthBudget, long now) {
    if (skill == null || victim == null || !Double.isFinite(damage) || damage <= 0D
        || !Double.isFinite(healthBudget) || healthBudget <= 0D) {
      return 0D;
    }
    String key = skill + ":" + victim;
    DamageBudget budget = victims.get(key);
    if (budget == null || now - budget.startedAt >= WINDOW_MILLIS) {
      trim(now);
      if (!victims.containsKey(key) && victims.size() >= MAX_ENTRIES) {
        return 0D;
      }
      budget = new DamageBudget(now, healthBudget);
      victims.put(key, budget);
    }
    double credited = Math.min(damage, Math.max(0D, budget.limit - budget.credited));
    budget.credited += credited;
    return credited;
  }

  private void trim(long now) {
    victims.values().removeIf(budget -> now - budget.startedAt >= WINDOW_MILLIS);
  }

  private static final class DamageBudget {
    private final long startedAt;
    private final double limit;
    private double credited;

    private DamageBudget(long startedAt, double limit) {
      this.startedAt = startedAt;
      this.limit = limit;
    }
  }
}
