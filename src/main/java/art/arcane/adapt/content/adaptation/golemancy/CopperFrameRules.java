package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemStat;
import art.arcane.adapt.api.golem.GolemStatDelta;

import java.util.ArrayList;
import java.util.List;

final class CopperFrameRules {
  static final double DEFAULT_OVERDRIVE_RADIUS = 16D;
  static final double MAX_OVERDRIVE_RADIUS = 64D;
  private static final int MAX_PATINA_STAGE = 3;
  private static final long MILLIS_PER_SECOND = 1_000L;

  private CopperFrameRules() {
  }

  static int carry(List<Integer> carryByLevel, int level) {
    return Math.max(0, AnimusBindingRules.atLevel(carryByLevel, level, 0));
  }

  static List<GolemStatDelta> frameDeltas(List<Integer> healthByLevel, List<Double> speedByLevel, int level) {
    if (level < 1) {
      return List.of();
    }
    List<GolemStatDelta> deltas = new ArrayList<>(2);
    int health = Math.max(0, AnimusBindingRules.atLevel(healthByLevel, level, 0));
    if (health > 0) {
      deltas.add(GolemStatDelta.add(GolemStat.MAX_HEALTH, health));
    }
    double speed = AnimusBindingRules.atLevel(speedByLevel, level, 0D);
    if (Double.isFinite(speed) && speed != 0D) {
      deltas.add(GolemStatDelta.multiplyBase(GolemStat.MOVEMENT_SPEED, speed));
    }
    return List.copyOf(deltas);
  }

  static List<GolemStatDelta> patinaDeltas(int stage, int level, int armorPerStage, double penaltyPerStage, int doubleLevel, int removedLevel) {
    List<GolemStatDelta> deltas = new ArrayList<>(2);
    double armor = patinaArmor(stage, level, armorPerStage, doubleLevel);
    if (armor > 0D) {
      deltas.add(GolemStatDelta.add(GolemStat.ARMOR, armor));
    }
    double speed = patinaSpeed(stage, level, penaltyPerStage, removedLevel);
    if (speed < 0D) {
      deltas.add(GolemStatDelta.multiplyBase(GolemStat.MOVEMENT_SPEED, speed));
    }
    return List.copyOf(deltas);
  }

  static double patinaArmor(int stage, int level, int armorPerStage, int doubleLevel) {
    if (level < 1) {
      return 0D;
    }
    int multiplier = unlocked(level, doubleLevel) ? 2 : 1;
    return (double) clampStage(stage) * Math.max(0, armorPerStage) * multiplier;
  }

  static double patinaSpeed(int stage, int level, double penaltyPerStage, int removedLevel) {
    if (level < 1 || unlocked(level, removedLevel) || !Double.isFinite(penaltyPerStage) || penaltyPerStage <= 0D) {
      return 0D;
    }
    return -Math.min(1D, clampStage(stage) * penaltyPerStage);
  }

  static boolean unlocked(int level, int unlockLevel) {
    return level >= Math.max(1, unlockLevel);
  }

  static boolean withinRadius(double distanceSquared, double radius) {
    if (!Double.isFinite(radius)) {
      return false;
    }
    double clamped = Math.max(0D, radius);
    return distanceSquared <= clamped * clamped;
  }

  static Overdrive overdrive(Overdrive current, long now, int seconds, int cooldownSeconds) {
    if (current != null && now < current.readyAt()) {
      return null;
    }
    return new Overdrive(now + Math.max(1, seconds) * MILLIS_PER_SECOND, now + Math.max(0, cooldownSeconds) * MILLIS_PER_SECOND);
  }

  static double workIntervalMultiplier(Overdrive current, long now, double multiplier) {
    if (current == null || now >= current.activeUntil() || !Double.isFinite(multiplier) || multiplier <= 0D) {
      return 1D;
    }
    return multiplier;
  }

  static double overdriveRadius(double radius) {
    return Double.isFinite(radius) ? Math.max(0D, Math.min(MAX_OVERDRIVE_RADIUS, radius)) : DEFAULT_OVERDRIVE_RADIUS;
  }

  static boolean expired(Overdrive current, long now) {
    return now >= Math.max(current.activeUntil(), current.readyAt());
  }

  private static int clampStage(int stage) {
    return Math.max(0, Math.min(MAX_PATINA_STAGE, stage));
  }

  record Overdrive(long activeUntil, long readyAt) {
  }
}
