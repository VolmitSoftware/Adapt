package art.arcane.adapt.content.skill.kinetics;

public final class KineticsKnockback {
    private static final double VANILLA_BASE_KNOCKBACK = 0.4D;

    private KineticsKnockback() {
    }

    public static boolean qualifies(double knockbackMagnitude, double minMagnitude) {
        return Double.isFinite(knockbackMagnitude) && Double.isFinite(minMagnitude)
            && minMagnitude >= 0D && knockbackMagnitude >= minMagnitude;
    }

    public static double dealtXp(double baseXp, double magnitude, double cap) {
        return normalizedXp(baseXp, magnitude, cap);
    }

    public static double takenXp(double baseXp, double magnitude, double cap) {
        return normalizedXp(baseXp, magnitude, cap);
    }

    public static double applySelfFactor(double xp, boolean selfCaused, double selfFactor) {
        if (!Double.isFinite(xp) || !Double.isFinite(selfFactor) || xp <= 0D || selfFactor < 0D) {
            return 0D;
        }
        double payout = selfCaused ? xp * selfFactor : xp;
        return Double.isFinite(payout) && payout > 0D ? payout : 0D;
    }

    private static double normalizedXp(double baseXp, double magnitude, double cap) {
        if (!Double.isFinite(baseXp) || !Double.isFinite(magnitude) || !Double.isFinite(cap)
            || baseXp <= 0D || magnitude <= 0D || cap <= 0D) {
            return 0D;
        }
        double payout = Math.min(cap, baseXp * magnitude / VANILLA_BASE_KNOCKBACK);
        return Double.isFinite(payout) && payout > 0D ? payout : 0D;
    }
}
