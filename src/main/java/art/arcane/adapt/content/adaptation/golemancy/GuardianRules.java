package art.arcane.adapt.content.adaptation.golemancy;

import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import java.util.List;

final class GuardianRules {
  static final int OFFLINE_LEVEL = 1;
  private static final double MIN_SENTRY_RANGE = 1D;
  private static final double MAX_SENTRY_RANGE = 10D;
  private static final double MIN_PERCEPTION = 1D;
  private static final long MIN_INTERCEPT_INTERVAL_MILLIS = 500L;
  private static final double MAX_BULWARK_RADIUS = 16D;
  private static final double DEFAULT_BULWARK_RADIUS = 4D;

  private GuardianRules() {
  }

  static double perception(List<Integer> byLevel, int level, double maxRadius) {
    double configured = AnimusBindingRules.atLevel(byLevel, Math.max(OFFLINE_LEVEL, level), 0);
    return Math.clamp(configured, MIN_PERCEPTION, Math.max(MIN_PERCEPTION, maxRadius));
  }

  static boolean unlocked(int level, int unlockLevel) {
    return level >= Math.max(1, unlockLevel);
  }

  static Reason reason(Candidate candidate, Options options) {
    if (candidate.protectedFriendly()) {
      return Reason.NONE;
    }
    if (candidate.attackedOwner() && options.ownerOnline() && options.retaliate()) {
      return Reason.RETALIATE;
    }
    if (candidate.player()
        || (candidate.creeper() && !options.targetCreepers())
        || (candidate.named() && options.ignoreNamedMobs())) {
      return Reason.NONE;
    }
    if (candidate.hostile()) {
      return Reason.HOSTILE;
    }
    return candidate.threatens() && options.ownerOnline() ? Reason.THREAT : Reason.NONE;
  }

  static boolean shields(DamageCause cause) {
    return switch (cause) {
      case VOID, KILL, WORLD_BORDER, SUICIDE -> false;
      default -> true;
    };
  }

  static double bulwarkDamage(double damage, double reduction) {
    return damage * (1D - fraction(reduction, 0D));
  }

  static boolean lastStand(double remaining, double maxHealth, double fraction) {
    return remaining > 0D && remaining < maxHealth * fraction;
  }

  static double sentryRange(double configured) {
    return Double.isFinite(configured) ? Math.clamp(configured, MIN_SENTRY_RANGE, MAX_SENTRY_RANGE) : MAX_SENTRY_RANGE;
  }

  static long interceptInterval(long configured) {
    return Math.max(MIN_INTERCEPT_INTERVAL_MILLIS, configured);
  }

  static double bulwarkRadius(double configured) {
    return Double.isFinite(configured) ? Math.clamp(configured, 0D, MAX_BULWARK_RADIUS) : DEFAULT_BULWARK_RADIUS;
  }

  static double fraction(double configured, double fallback) {
    return Double.isNaN(configured) ? fallback : Math.clamp(configured, 0D, 1D);
  }

  enum Reason {
    NONE,
    HOSTILE,
    THREAT,
    RETALIATE
  }

  record Candidate(boolean protectedFriendly, boolean player, boolean hostile, boolean creeper, boolean named, boolean threatens,
                   boolean attackedOwner) {
  }

  record Options(boolean ownerOnline, boolean retaliate, boolean targetCreepers, boolean ignoreNamedMobs) {
  }
}
