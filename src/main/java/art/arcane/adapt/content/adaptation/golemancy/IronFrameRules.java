package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemColossus;
import art.arcane.adapt.api.golem.GolemStat;
import art.arcane.adapt.api.golem.GolemStatDelta;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

final class IronFrameRules {
  static final double DEFAULT_SWEEP_RADIUS = 2D;
  static final double MAX_SWEEP_RADIUS = 8D;
  static final double DEFAULT_COLOSSUS_SCALE = 1D;
  static final double MIN_COLOSSUS_SCALE = 0.36D;
  static final double MAX_COLOSSUS_SCALE = 1.5D;
  static final double CLEARANCE_INSET = 0.01D;
  private static final double IRON_GOLEM_WIDTH = 1.4D;
  private static final double IRON_GOLEM_HEIGHT = 2.7D;
  private static final long MILLIS_PER_TICK = 50L;
  private static final long LEAK_TICK_FACTOR = 4L;
  private static final long LEAK_GRACE_MILLIS = 5_000L;

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

  static List<GolemStatDelta> colossusDeltas(int level, int colossusLevel, double attackBonus) {
    if (!unlocked(level, colossusLevel) || !Double.isFinite(attackBonus) || attackBonus == 0D) {
      return List.of();
    }
    return List.of(GolemStatDelta.multiplyTotal(GolemStat.ATTACK_DAMAGE, attackBonus));
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

  static GolemColossus.Toggle colossusToggle(GolemBody body, int level, int colossusLevel, boolean colossus, boolean busy, BooleanSupplier room) {
    if (body != GolemBody.IRON || !unlocked(level, colossusLevel)) {
      return GolemColossus.Toggle.UNAVAILABLE;
    }
    if (busy) {
      return GolemColossus.Toggle.BUSY;
    }
    if (colossus) {
      return GolemColossus.Toggle.SHRINK;
    }
    return room.getAsBoolean() ? GolemColossus.Toggle.GROW : GolemColossus.Toggle.NO_ROOM;
  }

  static boolean outgrown(boolean colossus, int level, int colossusLevel) {
    return colossus && !unlocked(level, colossusLevel);
  }

  static double colossusScale(double scale) {
    return Double.isFinite(scale) ? Math.max(MIN_COLOSSUS_SCALE, Math.min(MAX_COLOSSUS_SCALE, scale)) : DEFAULT_COLOSSUS_SCALE;
  }

  static BoundingBox colossusBox(double x, double y, double z, double scale) {
    double halfWidth = IRON_GOLEM_WIDTH * scale / 2D - CLEARANCE_INSET;
    return new BoundingBox(x - halfWidth, y + CLEARANCE_INSET, z - halfWidth, x + halfWidth, y + IRON_GOLEM_HEIGHT * scale, z + halfWidth);
  }

  static int growTicks(int ticks) {
    return Math.max(1, ticks);
  }

  static long leakDeadline(long now, int growTicks) {
    return now + growTicks(growTicks) * MILLIS_PER_TICK * LEAK_TICK_FACTOR + LEAK_GRACE_MILLIS;
  }

  static boolean leaked(long deadline, long now) {
    return now >= deadline;
  }

  static boolean unlocked(int level, int unlockLevel) {
    return level >= Math.max(1, unlockLevel);
  }

  record SweepCandidate<T>(T target, boolean excluded, boolean hostile, double distanceSquared) {
  }
}
