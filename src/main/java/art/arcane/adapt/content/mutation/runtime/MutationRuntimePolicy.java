package art.arcane.adapt.content.mutation.runtime;

import art.arcane.adapt.api.mutation.MutationClaim;
import art.arcane.adapt.api.mutation.MutationLimits;
import art.arcane.adapt.api.mutation.MutationType;

public final class MutationRuntimePolicy {
  private MutationRuntimePolicy() {
  }

  public static int maxEntities(MutationType type) {
    if (type == null) {
      return 0;
    }
    return switch (type) {
      case BASTION_SPINE -> MutationLimits.BASTION_TARGETS;
      case PACKMIND -> MutationLimits.PACK_MEMBERS;
      case MYCELIAL_NERVE -> MutationLimits.MYCELIAL_RECIPIENTS;
      case UMBRAL_ECHO -> MutationLimits.UMBRAL_TARGETS;
      case GRAVEBLOOM -> MutationLimits.GRAVEBLOOM_CROPS;
      case GALE_LUNG, VERDANT_MOLT, TEMPERBOUND, PARADOX_SCAR, ARSENAL_CORTEX,
           TROPHY_CRUCIBLE, LIVING_LATTICE, MASTERWORK_BOND, DEEPBLOOD, RESONANT_FORMULA -> 1;
    };
  }

  public static int maxBlocks(MutationType type) {
    if (type == null) {
      return 0;
    }
    return switch (type) {
      case LIVING_LATTICE, GRAVEBLOOM -> MutationLimits.LATTICE_BLOCKS;
      default -> 1;
    };
  }

  public static double clamp(double value, double minimum, double maximum) {
    if (!Double.isFinite(value)) {
      return minimum;
    }
    return Math.max(minimum, Math.min(maximum, value));
  }

  public static int copiedDuration(int sourceTicks, double fraction, int hardMaximumTicks) {
    if (sourceTicks <= 0 || hardMaximumTicks <= 0) {
      return 0;
    }
    double safeFraction = clamp(fraction, 0D, 1D);
    return Math.max(1, Math.min(hardMaximumTicks, (int) Math.floor(sourceTicks * safeFraction)));
  }

  public static int angleBucket(double degrees, int bucketSize) {
    int safeSize = Math.max(1, Math.min(180, bucketSize));
    double normalized = ((degrees % 360D) + 360D) % 360D;
    return (int) Math.floor(normalized / safeSize);
  }

  public static double decayHalfLife(double value, long elapsedMillis, long halfLifeMillis) {
    if (value <= 0D || elapsedMillis <= 0L || halfLifeMillis <= 0L) {
      return Math.max(0D, value);
    }
    double halves = elapsedMillis / (double) halfLifeMillis;
    return Math.max(0D, value * Math.pow(0.5D, halves));
  }

  public static boolean canFormulaEcho(MutationClaim claim) {
    return claim == MutationClaim.UTILITY_ECHO;
  }
}
