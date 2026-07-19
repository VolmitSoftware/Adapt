package art.arcane.adapt.content.skill.kinetics;

public final class KineticsLevitation {
  private KineticsLevitation() {
  }

  public static double receiveXp(double baseXp, int amplifier, int durationTicks, double cap) {
    return scaledXp(baseXp, amplifier, durationTicks, cap);
  }

  public static double applyXp(double baseXp, int amplifier, int durationTicks, double cap) {
    return scaledXp(baseXp, amplifier, durationTicks, cap);
  }

  public static double applyXpForTargets(double baseXp, int amplifier, int durationTicks,
      int affectedTargets, int targetLimit, double cap) {
    if (affectedTargets <= 0 || targetLimit <= 0) {
      return 0D;
    }
    double perTarget = scaledXp(baseXp, amplifier, durationTicks, cap);
    double total = perTarget * Math.min(affectedTargets, targetLimit);
    if (!Double.isFinite(total) || total <= 0D) {
      return 0D;
    }
    return Math.min(cap, total);
  }

  public static double pulseXp(double ratePerCadence, long elapsedMs, long cadenceMs) {
    if (!Double.isFinite(ratePerCadence) || ratePerCadence <= 0 || elapsedMs <= 0 || cadenceMs <= 0) {
      return 0;
    }

    double payout = ratePerCadence * elapsedMs / cadenceMs;
    return Double.isFinite(payout) && payout > 0D ? payout : 0D;
  }

  private static double scaledXp(double baseXp, int amplifier, int durationTicks, double cap) {
    if (!Double.isFinite(baseXp) || !Double.isFinite(cap) || baseXp <= 0 || cap <= 0 || durationTicks <= 0) {
      return 0;
    }

    double amplifierScale = 1.0D + 0.25D * Math.max(0, amplifier);
    double durationScale = Math.min(1.0D, durationTicks / 200.0D);
    double payout = Math.min(cap, baseXp * amplifierScale * durationScale);
    return Double.isFinite(payout) && payout > 0D ? payout : 0D;
  }
}
