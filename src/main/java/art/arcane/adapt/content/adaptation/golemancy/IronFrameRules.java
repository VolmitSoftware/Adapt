package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemStat;
import art.arcane.adapt.api.golem.GolemStatDelta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

final class IronFrameRules {
  static final double DEFAULT_SWEEP_RADIUS = 2D;
  static final double MAX_SWEEP_RADIUS = 8D;
  private static final long MILLIS_PER_TICK = 50L;
  private static final long RESIZE_GRACE_MILLIS = 1_000L;

  private IronFrameRules() {
  }

  static List<GolemStatDelta> frameDeltas(List<Integer> healthByLevel, List<Integer> armorByLevel, List<Double> attackByLevel, int level) {
    if (level < 1) {
      return List.of();
    }
    List<GolemStatDelta> deltas = new ArrayList<>(3);
    int health = AnimusBindingRules.atLevel(healthByLevel, level, 0);
    if (health > 0) {
      deltas.add(GolemStatDelta.add(GolemStat.MAX_HEALTH, health));
    }
    int armor = AnimusBindingRules.atLevel(armorByLevel, level, 0);
    if (armor > 0) {
      deltas.add(GolemStatDelta.add(GolemStat.ARMOR, armor));
    }
    double attack = AnimusBindingRules.atLevel(attackByLevel, level, 0D);
    if (Double.isFinite(attack) && attack > 0D) {
      deltas.add(GolemStatDelta.add(GolemStat.ATTACK_DAMAGE, attack));
    }
    return List.copyOf(deltas);
  }

  static List<GolemStatDelta> colossusDeltas(boolean colossus, int level, int colossusLevel, double attackBonus) {
    if (!colossus || !unlocked(level, colossusLevel) || !Double.isFinite(attackBonus) || attackBonus == 0D) {
      return List.of();
    }
    return List.of(GolemStatDelta.multiplyBase(GolemStat.ATTACK_DAMAGE, attackBonus));
  }

  static double ingotHealBonus(List<Integer> bonusByLevel, int level) {
    return Math.max(0, AnimusBindingRules.atLevel(bonusByLevel, level, 0));
  }

  static int sweepTargets(List<Integer> targetsByLevel, int level, int sweepLevel) {
    return unlocked(level, sweepLevel) ? Math.max(0, AnimusBindingRules.atLevel(targetsByLevel, level, 0)) : 0;
  }

  static double sweepDamage(double damage, double fraction) {
    if (!Double.isFinite(damage) || !Double.isFinite(fraction)) {
      return 0D;
    }
    return Math.max(0D, damage * fraction);
  }

  static double sweepRadius(double radius) {
    return Double.isFinite(radius) ? Math.max(0D, Math.min(MAX_SWEEP_RADIUS, radius)) : DEFAULT_SWEEP_RADIUS;
  }

  static <T> List<T> sweepVictims(List<SweepCandidate<T>> candidates, double radius, int cap, Predicate<T> damageable) {
    if (cap < 1) {
      return List.of();
    }
    double reach = radius * radius;
    List<SweepCandidate<T>> eligible = new ArrayList<>(candidates.size());
    for (SweepCandidate<T> candidate : candidates) {
      if (!candidate.excluded() && candidate.hostile() && candidate.distanceSquared() <= reach) {
        eligible.add(candidate);
      }
    }
    eligible.sort(Comparator.comparingDouble(SweepCandidate::distanceSquared));
    List<T> victims = new ArrayList<>(Math.min(cap, eligible.size()));
    for (SweepCandidate<T> candidate : eligible) {
      if (damageable.test(candidate.target())) {
        victims.add(candidate.target());
        if (victims.size() >= cap) {
          break;
        }
      }
    }
    return List.copyOf(victims);
  }

  static ColossusToggle colossusToggle(GolemBody body, int level, int colossusLevel, boolean colossus, boolean resizing) {
    if (body != GolemBody.IRON || (!colossus && !unlocked(level, colossusLevel))) {
      return ColossusToggle.UNAVAILABLE;
    }
    if (resizing) {
      return ColossusToggle.BUSY;
    }
    return colossus ? ColossusToggle.SHRINK : ColossusToggle.GROW;
  }

  static int growTicks(int ticks) {
    return Math.max(1, ticks);
  }

  static Resize resize(long token, long now, int growTicks) {
    return new Resize(token, now + growTicks(growTicks) * MILLIS_PER_TICK + RESIZE_GRACE_MILLIS);
  }

  static boolean resizing(Resize resize, long now) {
    return resize != null && now < resize.until();
  }

  static boolean unlocked(int level, int unlockLevel) {
    return level >= Math.max(1, unlockLevel);
  }

  enum ColossusToggle {
    UNAVAILABLE,
    BUSY,
    GROW,
    SHRINK
  }

  record SweepCandidate<T>(T target, boolean excluded, boolean hostile, double distanceSquared) {
  }

  record Resize(long token, long until) {
  }
}
