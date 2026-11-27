package art.arcane.adapt.content.adaptation.golemancy;

final class GolemancyRules {
  private GolemancyRules() {
  }

  static boolean unlocked(int level, int unlockLevel) {
    return level >= Math.max(1, unlockLevel);
  }

  static double nonNegative(double value) {
    return Double.isFinite(value) ? Math.max(0D, value) : 0D;
  }

  static double fraction(double value) {
    return Double.isFinite(value) ? Math.clamp(value, 0D, 1D) : 0D;
  }
}
