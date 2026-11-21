package art.arcane.adapt.api.insight;

/**
 * What Insight reveals at a level, by the fraction of the adaptation's level range: health, armor, attack and
 * ownership always; effects from 40%, movement from 60%, defenses and detection range from 80%, nearby creatures at the
 * maximum level. A viewer who turned full details off sees the base readout only.
 */
public record InsightTier(boolean effects, boolean movement, boolean defenses, boolean marks) {
  public static final InsightTier BASE = new InsightTier(false, false, false, false);
  private static final double EPSILON = 1.0E-6D;

  public static InsightTier of(double levelPercent, boolean fullDetails) {
    if (!fullDetails || !(levelPercent > 0D)) {
      return BASE;
    }
    return new InsightTier(levelPercent >= 0.4D - EPSILON, levelPercent >= 0.6D - EPSILON,
        levelPercent >= 0.8D - EPSILON, levelPercent >= 1D - EPSILON);
  }
}
