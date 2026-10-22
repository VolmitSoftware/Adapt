package art.arcane.adapt.api.xp;

import art.arcane.adapt.AdaptConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class XpEarnings {
  private static final int MAX_SOURCES = 512;

  private final Map<String, Allowance> skills = new HashMap<>();
  private final Map<String, Totals> sources = new LinkedHashMap<>();
  private final Allowance player = new Allowance();
  private final Allowance absolute = new Allowance();

  public synchronized double claim(String skill, double requested, double boost, long now) {
    if (!Double.isFinite(requested) || requested <= 0D || !Double.isFinite(boost) || boost < 0D) {
      return 0D;
    }
    AdaptConfig.XpEarningLimits limits = AdaptConfig.get().getXpEarningLimits();
    if (!limits.isEnabled()) {
      return requested;
    }
    double allowanceBoost = Math.max(1D, Math.min(limits.getMaximumAllowanceBoost(), boost));
    Allowance line = skills.computeIfAbsent(skill, ignored -> new Allowance());
    double lineAvailable = line.available(now, limits.getSkillBurstXp(), limits.getSkillXpPerMinute());
    double playerAvailable = player.available(now, limits.getPlayerBurstXp(), limits.getPlayerXpPerMinute());
    double absoluteAvailable = absolute.available(now, limits.getAbsoluteBurstXp(), limits.getAbsoluteXpPerMinute());
    double remaining = Math.min(lineAvailable, playerAvailable) * allowanceBoost;
    double reduction = Math.min(line.multiplier(limits.getSkillBurstXp()), player.multiplier(limits.getPlayerBurstXp()));
    double credited = Math.max(0D, Math.min(requested * reduction, Math.min(remaining, absoluteAvailable)));
    line.used += credited / allowanceBoost;
    player.used += credited / allowanceBoost;
    absolute.used += credited;
    return credited;
  }

  public synchronized void record(XpReceipt receipt) {
    String key = receipt.skill() + "/" + receipt.source();
    Totals totals = sources.get(key);
    if (totals == null) {
      if (sources.size() >= MAX_SOURCES) {
        sources.remove(sources.keySet().iterator().next());
      }
      totals = new Totals();
      sources.put(key, totals);
    }
    totals.requested = Math.min(Double.MAX_VALUE, totals.requested + receipt.requested());
    totals.contextual = Math.min(Double.MAX_VALUE, totals.contextual + receipt.contextual());
    totals.boosted = Math.min(Double.MAX_VALUE, totals.boosted + receipt.boosted());
    totals.eligible = Math.min(Double.MAX_VALUE, totals.eligible + receipt.eligible());
    totals.credited = Math.min(Double.MAX_VALUE, totals.credited + receipt.credited());
    totals.awards++;
    totals.lastAwardAt = receipt.atMillis();
  }

  public synchronized List<SourceSnapshot> snapshot() {
    List<SourceSnapshot> result = new ArrayList<>(sources.size());
    for (Map.Entry<String, Totals> entry : sources.entrySet()) {
      Totals value = entry.getValue();
      result.add(new SourceSnapshot(entry.getKey(), value.requested, value.contextual, value.boosted,
          value.eligible, value.credited, value.awards, value.lastAwardAt));
    }
    return List.copyOf(result);
  }

  public synchronized BudgetSnapshot budget(String skill) {
    Allowance line = skills.get(skill);
    return new BudgetSnapshot(line == null ? 0D : line.used, player.used, absolute.used,
        line == null ? 0L : line.updatedAt);
  }

  public record SourceSnapshot(String source, double requested, double contextual, double boosted,
                               double eligible, double credited, long awards, long lastAwardAt) {
  }

  public record BudgetSnapshot(double skillUsed, double playerUsed, double absoluteUsed, long updatedAt) {
  }

  private static final class Allowance {
    private double used;
    private long updatedAt;

    private double available(long now, double capacity, double perMinute) {
      if (updatedAt > 0L && now > updatedAt) {
        used = Math.max(0D, used - ((now - updatedAt) / 60000D) * perMinute);
      }
      updatedAt = Math.max(updatedAt, now);
      return Math.max(0D, capacity - used);
    }

    private double multiplier(double capacity) {
      double pressure = Math.max(0D, Math.min(1D, 2D * used / capacity - 1D));
      return 1D - 0.75D * pressure;
    }
  }

  private static final class Totals {
    private double requested;
    private double contextual;
    private double boosted;
    private double eligible;
    private double credited;
    private long awards;
    private long lastAwardAt;
  }
}
