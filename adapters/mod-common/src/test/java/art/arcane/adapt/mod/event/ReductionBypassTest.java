package art.arcane.adapt.mod.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReductionBypassTest {
    private static double landed(double base, double multiplier, double cooldownReduction) {
        return Math.max(0.0D, (float) (base * multiplier) - cooldownReduction);
    }

    @Test
    void unscaledHitLandsTheRequestedDamage() {
        assertEquals(12.0D, ReductionBypass.base(12.0D, 1.0D, 0.0D), 1.0E-9D);
    }

    @Test
    void helmetScalingIsCancelledOut() {
        double base = ReductionBypass.base(8.0D, 0.75D, 0.0D);
        assertEquals(8.0D, landed(base, 0.75D, 0.0D), 1.0E-5D);
    }

    @Test
    void freezingExtraDamageIsCancelledOut() {
        double base = ReductionBypass.base(10.0D, 5.0D, 0.0D);
        assertEquals(2.0D, base, 1.0E-9D);
        assertEquals(10.0D, landed(base, 5.0D, 0.0D), 1.0E-5D);
    }

    @Test
    void hurtCooldownSubtractionIsCancelledOut() {
        double base = ReductionBypass.base(6.0D, 1.0D, 4.0D);
        assertEquals(10.0D, base, 1.0E-9D);
        assertEquals(6.0D, landed(base, 1.0D, 4.0D), 1.0E-5D);
        double scaled = ReductionBypass.base(6.0D, 0.75D, 4.0D);
        assertEquals(6.0D, landed(scaled, 0.75D, 4.0D), 1.0E-5D);
    }

    @Test
    void nothingToLandStaysAsRequested() {
        assertEquals(0.0D, ReductionBypass.base(0.0D, 0.75D, 4.0D), 1.0E-9D);
        assertEquals(-1.0D, ReductionBypass.base(-1.0D, 1.0D, 0.0D), 1.0E-9D);
    }
}
