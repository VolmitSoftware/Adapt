package art.arcane.adapt.content.skill.kinetics;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class KineticsMotionTest {
  @Test
  void bouncySurfaceCoversSlimeHoneyAndEveryBedColor() {
    assertThat(KineticsMotion.isBouncySurface(Material.SLIME_BLOCK)).isTrue();
    assertThat(KineticsMotion.isBouncySurface(Material.HONEY_BLOCK)).isTrue();
    assertThat(KineticsMotion.isBouncySurface(Material.WHITE_BED)).isTrue();
    assertThat(KineticsMotion.isBouncySurface(Material.ORANGE_BED)).isTrue();
    assertThat(KineticsMotion.isBouncySurface(Material.MAGENTA_BED)).isTrue();
    assertThat(KineticsMotion.isBouncySurface(Material.LIGHT_BLUE_BED)).isTrue();
    assertThat(KineticsMotion.isBouncySurface(Material.YELLOW_BED)).isTrue();
    assertThat(KineticsMotion.isBouncySurface(Material.LIME_BED)).isTrue();
    assertThat(KineticsMotion.isBouncySurface(Material.PINK_BED)).isTrue();
    assertThat(KineticsMotion.isBouncySurface(Material.GRAY_BED)).isTrue();
    assertThat(KineticsMotion.isBouncySurface(Material.LIGHT_GRAY_BED)).isTrue();
    assertThat(KineticsMotion.isBouncySurface(Material.CYAN_BED)).isTrue();
    assertThat(KineticsMotion.isBouncySurface(Material.PURPLE_BED)).isTrue();
    assertThat(KineticsMotion.isBouncySurface(Material.BLUE_BED)).isTrue();
    assertThat(KineticsMotion.isBouncySurface(Material.BROWN_BED)).isTrue();
    assertThat(KineticsMotion.isBouncySurface(Material.GREEN_BED)).isTrue();
    assertThat(KineticsMotion.isBouncySurface(Material.RED_BED)).isTrue();
    assertThat(KineticsMotion.isBouncySurface(Material.BLACK_BED)).isTrue();
  }

  @Test
  void bouncySurfaceRejectsSoftOnlyLegacyAndOrdinaryBlocks() {
    assertThat(KineticsMotion.isBouncySurface(Material.HAY_BLOCK)).isFalse();
    assertThat(KineticsMotion.isBouncySurface(Material.POWDER_SNOW)).isFalse();
    assertThat(KineticsMotion.isBouncySurface(Material.SPONGE)).isFalse();
    assertThat(KineticsMotion.isBouncySurface(Material.WET_SPONGE)).isFalse();
    assertThat(KineticsMotion.isBouncySurface(Material.valueOf("LEGACY_BED"))).isFalse();
    assertThat(KineticsMotion.isBouncySurface(Material.STONE)).isFalse();
    assertThat(KineticsMotion.isBouncySurface(Material.DIRT)).isFalse();
    assertThat(KineticsMotion.isBouncySurface(Material.AIR)).isFalse();
    assertThat(KineticsMotion.isBouncySurface(null)).isFalse();
  }

  @Test
  void softLandingCoversBouncySetPlusSoftBlocks() {
    assertThat(KineticsMotion.isSoftLanding(Material.SLIME_BLOCK)).isTrue();
    assertThat(KineticsMotion.isSoftLanding(Material.HONEY_BLOCK)).isTrue();
    assertThat(KineticsMotion.isSoftLanding(Material.RED_BED)).isTrue();
    assertThat(KineticsMotion.isSoftLanding(Material.HAY_BLOCK)).isTrue();
    assertThat(KineticsMotion.isSoftLanding(Material.POWDER_SNOW)).isTrue();
    assertThat(KineticsMotion.isSoftLanding(Material.SPONGE)).isTrue();
    assertThat(KineticsMotion.isSoftLanding(Material.WET_SPONGE)).isTrue();
  }

  @Test
  void softLandingExcludesDiggableGroundAndLegacyMaterials() {
    assertThat(KineticsMotion.isSoftLanding(Material.DIRT)).isFalse();
    assertThat(KineticsMotion.isSoftLanding(Material.SAND)).isFalse();
    assertThat(KineticsMotion.isSoftLanding(Material.GRAVEL)).isFalse();
    assertThat(KineticsMotion.isSoftLanding(Material.SNOW_BLOCK)).isFalse();
    assertThat(KineticsMotion.isSoftLanding(Material.valueOf("LEGACY_BED"))).isFalse();
    assertThat(KineticsMotion.isSoftLanding(Material.valueOf("LEGACY_SPONGE"))).isFalse();
    assertThat(KineticsMotion.isSoftLanding(Material.STONE)).isFalse();
    assertThat(KineticsMotion.isSoftLanding(null)).isFalse();
  }

  @Test
  void breakFallXpScalesPerBlockAndCaps() {
    assertThat(KineticsMotion.breakFallXp(3D, 1.2D, 25D)).isCloseTo(3.6D, offset(1e-9));
    assertThat(KineticsMotion.breakFallXp(10D, 1.2D, 25D)).isCloseTo(12D, offset(1e-9));
    assertThat(KineticsMotion.breakFallXp(30D, 1.2D, 25D)).isCloseTo(25D, offset(1e-9));
  }

  @Test
  void breakFallXpPaysNothingBelowThreeBlocks() {
    assertThat(KineticsMotion.breakFallXp(2.999999D, 1.2D, 25D)).isZero();
    assertThat(KineticsMotion.breakFallXp(0D, 1.2D, 25D)).isZero();
    assertThat(KineticsMotion.breakFallXp(-5D, 1.2D, 25D)).isZero();
  }

  @Test
  void breakFallXpClampsNaNAndNegativeInputs() {
    assertThat(KineticsMotion.breakFallXp(Double.NaN, 1.2D, 25D)).isZero();
    assertThat(KineticsMotion.breakFallXp(10D, Double.NaN, 25D)).isZero();
    assertThat(KineticsMotion.breakFallXp(10D, 1.2D, Double.NaN)).isZero();
    assertThat(KineticsMotion.breakFallXp(10D, -1.2D, 25D)).isZero();
    assertThat(KineticsMotion.breakFallXp(10D, 1.2D, -25D)).isZero();
    assertThat(KineticsMotion.breakFallXp(Double.POSITIVE_INFINITY, 1.2D, 25D)).isZero();
    assertThat(KineticsMotion.breakFallXp(10D, Double.POSITIVE_INFINITY, 25D)).isZero();
  }

  @Test
  void launchRequiresUpwardFlipAtOrAboveThreshold() {
    assertThat(KineticsMotion.isLaunch(0.6D, 0D, 0.6D)).isTrue();
    assertThat(KineticsMotion.isLaunch(0.8D, -0.5D, 0.6D)).isTrue();
    assertThat(KineticsMotion.isLaunch(0.59D, 0D, 0.6D)).isFalse();
    assertThat(KineticsMotion.isLaunch(0.8D, 0.1D, 0.6D)).isFalse();
    assertThat(KineticsMotion.isLaunch(-0.8D, -0.5D, 0.6D)).isFalse();
  }

  @Test
  void launchRejectsNaNInputs() {
    assertThat(KineticsMotion.isLaunch(Double.NaN, 0D, 0.6D)).isFalse();
    assertThat(KineticsMotion.isLaunch(0.8D, Double.NaN, 0.6D)).isFalse();
    assertThat(KineticsMotion.isLaunch(0.8D, 0D, Double.NaN)).isFalse();
  }

  @Test
  void bounceChainIncrementsInsideWindowAndResetsOutside() {
    assertThat(KineticsMotion.nextBounceChain(1, 5000L, 2000L, 4000L)).isEqualTo(2);
    assertThat(KineticsMotion.nextBounceChain(3, 6000L, 2000L, 4000L)).isEqualTo(4);
    assertThat(KineticsMotion.nextBounceChain(3, 6001L, 2000L, 4000L)).isEqualTo(1);
    assertThat(KineticsMotion.nextBounceChain(5, 100_000L, 2000L, 4000L)).isEqualTo(1);
  }

  @Test
  void bounceChainNeverDropsBelowOne() {
    assertThat(KineticsMotion.nextBounceChain(0, 5000L, 2000L, 4000L)).isEqualTo(1);
    assertThat(KineticsMotion.nextBounceChain(-3, 5000L, 2000L, 4000L)).isEqualTo(1);
    assertThat(KineticsMotion.nextBounceChain(2, 5000L, 2000L, -1L)).isEqualTo(1);
  }

  @Test
  void bounceXpAddsChainBonusAboveFirstBounceAndCaps() {
    assertThat(KineticsMotion.bounceXp(4D, 1, 2D, 20D)).isCloseTo(4D, offset(1e-9));
    assertThat(KineticsMotion.bounceXp(4D, 3, 2D, 20D)).isCloseTo(8D, offset(1e-9));
    assertThat(KineticsMotion.bounceXp(4D, 10, 2D, 20D)).isCloseTo(20D, offset(1e-9));
    assertThat(KineticsMotion.bounceXp(4D, 0, 2D, 20D)).isCloseTo(4D, offset(1e-9));
  }

  @Test
  void bounceXpClampsNaNAndNegativeResults() {
    assertThat(KineticsMotion.bounceXp(Double.NaN, 2, 2D, 20D)).isZero();
    assertThat(KineticsMotion.bounceXp(4D, 2, Double.NaN, 20D)).isZero();
    assertThat(KineticsMotion.bounceXp(4D, 2, 2D, Double.NaN)).isZero();
    assertThat(KineticsMotion.bounceXp(4D, 5, -2D, 20D)).isZero();
    assertThat(KineticsMotion.bounceXp(4D, 1, 2D, -20D)).isZero();
    assertThat(KineticsMotion.bounceXp(Double.POSITIVE_INFINITY, 2, 2D, 20D)).isZero();
    assertThat(KineticsMotion.bounceXp(4D, 2, Double.POSITIVE_INFINITY, 20D)).isZero();
  }

  @Test
  void motionStateCarriesItsComponents() {
    KineticsMotion.MotionState state = new KineticsMotion.MotionState(1234L, 3, -0.42D, 5678L);
    assertThat(state.lastBounceAtMs()).isEqualTo(1234L);
    assertThat(state.bounceChain()).isEqualTo(3);
    assertThat(state.lastDeltaY()).isCloseTo(-0.42D, offset(1e-9));
    assertThat(state.lastLaunchAtMs()).isEqualTo(5678L);
  }
}
