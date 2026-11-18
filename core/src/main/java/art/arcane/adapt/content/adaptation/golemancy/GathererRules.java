package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemCapacity;
import art.arcane.adapt.api.golem.GolemCoreType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

final class GathererRules {
  static final double MAX_PICKUP_RADIUS = 64D;
  static final double MAX_MAGNET_RADIUS = 16D;
  static final double MAX_INSTANT_PICKUP_RADIUS = 4D;
  static final double MAX_DEATH_SITE_RADIUS = 64D;
  static final int MAX_DEATH_SITE_SECONDS = 3_600;
  static final int MAX_FOREIGN_ITEM_SECONDS = 600;
  static final int TICKS_PER_SECOND = 20;

  private GathererRules() {
  }

  static double pickupRadius(List<Integer> byLevel, int level) {
    return Math.min(MAX_PICKUP_RADIUS, Math.max(0, byLevel(byLevel, Math.max(1, level))));
  }

  static int essenceCap(List<Integer> byLevel, int level) {
    return level < 1 ? 0 : Math.max(0, byLevel(byLevel, level));
  }

  static boolean unlocked(int level, int unlockLevel) {
    return unlockLevel >= 1 && level >= unlockLevel;
  }

  static boolean waiting(int pickupDelay) {
    return pickupDelay > 0;
  }

  static boolean foreign(UUID thrower, UUID itemOwner, UUID golemOwner, int ticksLived, int foreignTicks) {
    if (itemOwner != null && !itemOwner.equals(golemOwner)) {
      return true;
    }
    return ticksLived < foreignTicks && thrower != null && !thrower.equals(golemOwner);
  }

  static int essenceRoom(int essencePoints, int cap) {
    return Math.max(0, cap - Math.max(0, essencePoints));
  }

  static int absorbed(int orbTotal, int room) {
    return Math.max(0, Math.min(orbTotal, room));
  }

  static int orbTotal(int experience, int count) {
    if (experience <= 0) {
      return 0;
    }
    return (int) Math.min(Integer.MAX_VALUE, (long) experience * Math.max(1, count));
  }

  static GolemCapacity.Contribution carryBonus(List<GolemCoreType> cores, int level, int unlockLevel, double fraction) {
    return cores.contains(GolemCoreType.GATHERER) && unlocked(level, unlockLevel)
        ? GolemCapacity.Contribution.bonus(fraction)
        : GolemCapacity.Contribution.NONE;
  }

  static String blocks(double value) {
    return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
  }

  static double radius(double value, double max) {
    if (!Double.isFinite(value) || value <= 0D) {
      return 0D;
    }
    return Math.min(value, max);
  }

  static int seconds(int value, int max) {
    return Math.clamp(value, 0, max);
  }

  private static int byLevel(List<Integer> values, int level) {
    if (values.isEmpty()) {
      return 0;
    }
    Integer value = values.get(Math.min(level, values.size()) - 1);
    return Objects.requireNonNullElse(value, 0);
  }
}
