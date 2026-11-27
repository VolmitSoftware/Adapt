package art.arcane.adapt.content.skill.kinetics;

import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class KineticsMotionTest {
  @Test
  void bouncySurfaceCoversSlimeHoneyAndEveryBedColor() {
    assertThat(KineticsMotion.isBouncySurface(Id.minecraft("slime_block"))).isTrue();
    assertThat(KineticsMotion.isBouncySurface(Id.minecraft("honey_block"))).isTrue();
    assertThat(KineticsMotion.isBouncySurface(Id.minecraft("white_bed"))).isTrue();
    assertThat(KineticsMotion.isBouncySurface(Id.minecraft("orange_bed"))).isTrue();
    assertThat(KineticsMotion.isBouncySurface(Id.minecraft("magenta_bed"))).isTrue();
    assertThat(KineticsMotion.isBouncySurface(Id.minecraft("light_blue_bed"))).isTrue();
    assertThat(KineticsMotion.isBouncySurface(Id.minecraft("yellow_bed"))).isTrue();
    assertThat(KineticsMotion.isBouncySurface(Id.minecraft("lime_bed"))).isTrue();
    assertThat(KineticsMotion.isBouncySurface(Id.minecraft("pink_bed"))).isTrue();
    assertThat(KineticsMotion.isBouncySurface(Id.minecraft("gray_bed"))).isTrue();
    assertThat(KineticsMotion.isBouncySurface(Id.minecraft("light_gray_bed"))).isTrue();
    assertThat(KineticsMotion.isBouncySurface(Id.minecraft("cyan_bed"))).isTrue();
    assertThat(KineticsMotion.isBouncySurface(Id.minecraft("purple_bed"))).isTrue();
    assertThat(KineticsMotion.isBouncySurface(Id.minecraft("blue_bed"))).isTrue();
    assertThat(KineticsMotion.isBouncySurface(Id.minecraft("brown_bed"))).isTrue();
    assertThat(KineticsMotion.isBouncySurface(Id.minecraft("green_bed"))).isTrue();
    assertThat(KineticsMotion.isBouncySurface(Id.minecraft("red_bed"))).isTrue();
    assertThat(KineticsMotion.isBouncySurface(Id.minecraft("black_bed"))).isTrue();
  }

  @Test
  void bouncySurfaceRejectsSoftOnlyLegacyAndOrdinaryBlocks() {
    assertThat(KineticsMotion.isBouncySurface(Id.minecraft("hay_block"))).isFalse();
    assertThat(KineticsMotion.isBouncySurface(Id.minecraft("powder_snow"))).isFalse();
    assertThat(KineticsMotion.isBouncySurface(Id.minecraft("sponge"))).isFalse();
    assertThat(KineticsMotion.isBouncySurface(Id.minecraft("wet_sponge"))).isFalse();
    assertThat(KineticsMotion.isBouncySurface(Id.minecraft("legacy_bed"))).isFalse();
    assertThat(KineticsMotion.isBouncySurface(Id.minecraft("stone"))).isFalse();
    assertThat(KineticsMotion.isBouncySurface(Id.minecraft("dirt"))).isFalse();
    assertThat(KineticsMotion.isBouncySurface(Id.minecraft("air"))).isFalse();
    assertThat(KineticsMotion.isBouncySurface(null)).isFalse();
  }

  @Test
  void softLandingCoversBouncySetPlusSoftBlocks() {
    assertThat(KineticsMotion.isSoftLanding(Id.minecraft("slime_block"))).isTrue();
    assertThat(KineticsMotion.isSoftLanding(Id.minecraft("honey_block"))).isTrue();
    assertThat(KineticsMotion.isSoftLanding(Id.minecraft("red_bed"))).isTrue();
    assertThat(KineticsMotion.isSoftLanding(Id.minecraft("hay_block"))).isTrue();
    assertThat(KineticsMotion.isSoftLanding(Id.minecraft("powder_snow"))).isTrue();
    assertThat(KineticsMotion.isSoftLanding(Id.minecraft("sponge"))).isTrue();
    assertThat(KineticsMotion.isSoftLanding(Id.minecraft("wet_sponge"))).isTrue();
  }

  @Test
  void softLandingExcludesDiggableGroundAndLegacyMaterials() {
    assertThat(KineticsMotion.isSoftLanding(Id.minecraft("dirt"))).isFalse();
    assertThat(KineticsMotion.isSoftLanding(Id.minecraft("sand"))).isFalse();
    assertThat(KineticsMotion.isSoftLanding(Id.minecraft("gravel"))).isFalse();
    assertThat(KineticsMotion.isSoftLanding(Id.minecraft("snow_block"))).isFalse();
    assertThat(KineticsMotion.isSoftLanding(Id.minecraft("legacy_bed"))).isFalse();
    assertThat(KineticsMotion.isSoftLanding(Id.minecraft("legacy_sponge"))).isFalse();
    assertThat(KineticsMotion.isSoftLanding(Id.minecraft("stone"))).isFalse();
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
