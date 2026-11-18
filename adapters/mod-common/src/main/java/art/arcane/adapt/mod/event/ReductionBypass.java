package art.arcane.adapt.mod.event;

/**
 * Base damage for a hit that ignores every reduction. Armour, enchantment and effect protection, absorption and
 * shields are skipped in the damage pipeline itself; what remains before {@code actuallyHurt} is vanilla's scaling
 * (freezing, helmet) and the hurt-cooldown subtraction, which this base cancels out exactly.
 */
public final class ReductionBypass {
    private ReductionBypass() {
    }

    /**
     * The base that lands {@code wanted} after vanilla multiplies it by {@code multiplier} and subtracts
     * {@code cooldownReduction}.
     */
    public static double base(double wanted, double multiplier, double cooldownReduction) {
        if (wanted <= 0.0D || multiplier <= 0.0D) {
            return wanted;
        }
        return (wanted + Math.max(0.0D, cooldownReduction)) / multiplier;
    }
}
