package art.arcane.adapt.api.xp;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class XpMathTest extends AdaptTestBase {

    @Test
    void nonFiniteProgressionInputsAndCurveResultsStayFinite() throws Exception {
        AdaptConfig config = AdaptConfig.get();
        int previousMaximum = config.experienceMaxLevel;
        Field curveField = AdaptConfig.class.getDeclaredField("xpCurve");
        curveField.setAccessible(true);
        Curves previousCurve = (Curves) curveField.get(config);
        try {
            config.experienceMaxLevel = 2;
            curveField.set(config, Curves.HYPER);

            assertThat(XP.getXpForLevel(2D)).isEqualTo(2800D);
            assertThat(XP.getXpForLevel(Double.NaN)).isFinite();
            assertThat(XP.getLevelForXp(Double.NaN)).isZero();
            assertThat(XP.getLevelForXp(Double.POSITIVE_INFINITY)).isEqualTo(2D);
            assertThat(XP.getLevelForXp(Double.NEGATIVE_INFINITY)).isZero();
        } finally {
            config.experienceMaxLevel = previousMaximum;
            curveField.set(config, previousCurve);
        }
    }

    @Test
    @DisplayName("xp required for a level increases monotonically")
    void xpForLevelIsMonotonic() {
        double previous = Double.NEGATIVE_INFINITY;
        for (int level = 1; level <= 100; level++) {
            double xp = XP.getXpForLevel(level);
            assertThat(xp).isGreaterThan(previous);
            previous = xp;
        }
    }

    @Test
    @DisplayName("getLevelForXp inverts getXpForLevel")
    void levelForXpInvertsXpForLevel() {
        for (int level = 1; level <= 100; level++) {
            double xp = XP.getXpForLevel(level);
            double recovered = XP.getLevelForXp(xp);
            assertThat(recovered).isCloseTo(level, within(0.05));
        }
    }

    @Test
    @DisplayName("level progress stays within [0, 1)")
    void levelProgressWithinUnitInterval() {
        for (double xp = 0.0; xp < 500000.0; xp += 137.0) {
            double progress = XP.getLevelProgress(xp);
            assertThat(progress).isGreaterThanOrEqualTo(0.0);
            assertThat(progress).isLessThan(1.0);
        }
    }

    @Test
    @DisplayName("xp until level up is never negative")
    void xpUntilLevelUpNeverNegative() {
        for (double xp = 0.0; xp < 500000.0; xp += 211.0) {
            assertThat(XP.getXpUntilLevelUp(xp)).isGreaterThanOrEqualTo(0.0);
        }
    }

    @Test
    void maximumIntegerLevelDoesNotOverflowTheSuccessor() {
        AdaptConfig config = AdaptConfig.get();
        int previousMaximum = config.experienceMaxLevel;
        try {
            config.experienceMaxLevel = Integer.MAX_VALUE;
            assertThat(XP.getXpUntilLevelUp(Double.POSITIVE_INFINITY)).isZero();
            assertThat(XP.getXpUntilLevelUp(Double.POSITIVE_INFINITY)).isFinite();
        } finally {
            config.experienceMaxLevel = previousMaximum;
        }
    }
}
