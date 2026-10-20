package art.arcane.adapt.api.golem.plinth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class InfusionVortexTest {
  private static final int[] DURATIONS = {100, 160, 240, 300};
  private static final double EPSILON = 1.0E-9D;

  @Test
  void phasesSplitTheRitualAtAQuarterAndAtEightyFivePercent() {
    assertThat(InfusionVortex.phase(0D)).isEqualTo(InfusionVortex.Phase.LIFT);
    assertThat(InfusionVortex.phase(0.2499D)).isEqualTo(InfusionVortex.Phase.LIFT);
    assertThat(InfusionVortex.phase(0.25D)).isEqualTo(InfusionVortex.Phase.SWIRL);
    assertThat(InfusionVortex.phase(0.8499D)).isEqualTo(InfusionVortex.Phase.SWIRL);
    assertThat(InfusionVortex.phase(0.85D)).isEqualTo(InfusionVortex.Phase.COLLIDE);
    assertThat(InfusionVortex.phase(1D)).isEqualTo(InfusionVortex.Phase.COLLIDE);
  }

  @Test
  void everyRecipeDurationChangesPhaseOnTheSameFractionOfItsTicks() {
    for (int duration : DURATIONS) {
      int swirlStart = duration / 4;
      int collideStart = duration * 85 / 100;
      assertThat(InfusionVortex.phase(InfusionVortex.progress(swirlStart - 1, duration))).as("%d", duration).isEqualTo(InfusionVortex.Phase.LIFT);
      assertThat(InfusionVortex.phase(InfusionVortex.progress(swirlStart, duration))).as("%d", duration).isEqualTo(InfusionVortex.Phase.SWIRL);
      assertThat(InfusionVortex.phase(InfusionVortex.progress(collideStart - 1, duration))).as("%d", duration).isEqualTo(InfusionVortex.Phase.SWIRL);
      assertThat(InfusionVortex.phase(InfusionVortex.progress(collideStart, duration))).as("%d", duration).isEqualTo(InfusionVortex.Phase.COLLIDE);
      assertThat(InfusionVortex.phase(InfusionVortex.progress(duration, duration))).as("%d", duration).isEqualTo(InfusionVortex.Phase.COLLIDE);
    }
  }

  @Test
  void progressIsClampedToTheRitual() {
    assertThat(InfusionVortex.progress(-0.5D, 100)).isZero();
    assertThat(InfusionVortex.progress(50D, 100)).isEqualTo(0.5D);
    assertThat(InfusionVortex.progress(120D, 100)).isEqualTo(1D);
  }

  @Test
  void streamsOrbitAtThePedestalRadiusThroughTheLift() {
    for (double progress = 0D; progress < InfusionVortex.LIFT_END; progress += 0.01D) {
      assertThat(InfusionVortex.radius(progress)).isEqualTo(InfusionVortex.PEDESTAL_RADIUS);
    }
    assertThat(InfusionVortex.PEDESTAL_RADIUS).isEqualTo(Math.abs(PlinthSlot.NORTH.dz()));
  }

  @Test
  void theSwirlShrinksTheOrbitSmoothlyToTheCoreAndTheCollisionClosesIt() {
    assertThat(InfusionVortex.radius(InfusionVortex.SWIRL_END)).isCloseTo(InfusionVortex.CORE_RADIUS, within(EPSILON));
    assertThat(InfusionVortex.CORE_RADIUS).isEqualTo(0.3D);
    assertThat(InfusionVortex.radius(1D)).isCloseTo(0D, within(EPSILON));
    for (int duration : DURATIONS) {
      double previous = InfusionVortex.radius(0D);
      for (int tick = 1; tick <= duration; tick++) {
        double progress = InfusionVortex.progress(tick, duration);
        double radius = InfusionVortex.radius(progress);
        assertThat(radius).as("tick %d of %d", tick, duration).isLessThanOrEqualTo(previous);
        if (InfusionVortex.phase(progress) == InfusionVortex.Phase.SWIRL && tick > duration / 4) {
          assertThat(radius).as("tick %d of %d", tick, duration).isLessThan(previous);
        }
        assertThat(previous - radius).as("tick %d of %d", tick, duration).isLessThan(0.05D);
        previous = radius;
      }
    }
  }

  @Test
  void streamsStartOnThePedestalsAndTurnFasterAsTheSwirlTightens() {
    for (int duration : DURATIONS) {
      assertThat(InfusionVortex.angle(0D, duration)).isZero();
      assertThat(InfusionVortex.angle(-1D, duration)).isZero();
      double previousStep = 0D;
      for (int tick = 0; tick < duration; tick++) {
        double step = InfusionVortex.angle(tick + 1D, duration) - InfusionVortex.angle(tick, duration);
        assertThat(step).as("tick %d of %d", tick, duration).isPositive().isGreaterThanOrEqualTo(previousStep);
        previousStep = step;
      }
      double first = InfusionVortex.angle(1D, duration);
      assertThat(previousStep).as("%d", duration).isGreaterThan(first * 4D);
    }
  }

  @Test
  void streamsRiseAboveThePedestalsThenSettleAtTheCenterItemHeight() {
    assertThat(InfusionVortex.height(0D)).isEqualTo(InfusionVortex.ITEM_HEIGHT);
    double previous = InfusionVortex.height(0D);
    for (double progress = 0.01D; progress <= InfusionVortex.LIFT_END; progress += 0.01D) {
      double height = InfusionVortex.height(progress);
      assertThat(height).isGreaterThan(previous);
      previous = height;
    }
    double centerItem = InfusionVortex.ITEM_HEIGHT + InfusionVortex.centerLift(InfusionVortex.SWIRL_END);
    assertThat(InfusionVortex.height(InfusionVortex.LIFT_END)).isGreaterThan(InfusionVortex.ITEM_HEIGHT + InfusionVortex.MAX_LIFT);
    assertThat(InfusionVortex.height(InfusionVortex.SWIRL_END)).isCloseTo(centerItem, within(EPSILON));
    assertThat(InfusionVortex.height(0.92D)).isCloseTo(centerItem, within(EPSILON));
    assertThat(InfusionVortex.height(1D)).isCloseTo(centerItem, within(EPSILON));
  }

  @Test
  void pedestalItemsRiseThroughTheLiftAndThenBob() {
    for (int pedestal = 0; pedestal < PlinthSlot.PEDESTALS.size(); pedestal++) {
      assertThat(InfusionVortex.pedestalLift(0D, 0, pedestal)).isZero();
      float low = Float.MAX_VALUE;
      float high = -Float.MAX_VALUE;
      for (int tick = 25; tick <= 100; tick++) {
        float lift = InfusionVortex.pedestalLift(InfusionVortex.progress(tick, 100), tick, pedestal);
        assertThat(lift).isBetween(InfusionVortex.MAX_LIFT - InfusionVortex.BOB - 1.0E-6F, InfusionVortex.MAX_LIFT + InfusionVortex.BOB + 1.0E-6F);
        low = Math.min(low, lift);
        high = Math.max(high, lift);
      }
      assertThat(high - low).as("pedestal %d bobs", pedestal).isGreaterThan(InfusionVortex.BOB);
    }
    assertThat(InfusionVortex.pedestalLift(0.5D, 10, 0)).isNotEqualTo(InfusionVortex.pedestalLift(0.5D, 10, 1));
  }

  @Test
  void theCenterItemSpinsFasterAsTheSwirlTightens() {
    assertThat(InfusionVortex.centerSpin(0D)).isEqualTo(PlinthRenderer.IDLE_DEGREES_PER_TICK);
    float previous = InfusionVortex.centerSpin(0D);
    for (int tick = 1; tick <= 100; tick++) {
      float spin = InfusionVortex.centerSpin(InfusionVortex.progress(tick, 100));
      assertThat(spin).isGreaterThanOrEqualTo(previous);
      previous = spin;
    }
    assertThat(InfusionVortex.centerSpin(1D)).isEqualTo(InfusionVortex.MAX_SPIN);
    assertThat(InfusionVortex.MAX_SPIN * PlinthRenderer.RITUAL_STEP_TICKS).isLessThan(180F);
  }

  @Test
  void everyFrameStaysUnderThePerStreamAndPerTickCaps() {
    for (int duration : DURATIONS) {
      for (int tick = 1; tick <= duration; tick++) {
        double progress = InfusionVortex.progress(tick, duration);
        InfusionVortex.Phase phase = InfusionVortex.phase(progress);
        int perStream = InfusionVortex.streamPoints(phase) + (InfusionVortex.accentDue(phase, tick) ? 1 : 0);
        int frame = perStream * InfusionVortex.ARMS + InfusionVortex.coreParticles(progress);
        assertThat(perStream).as("tick %d of %d", tick, duration).isBetween(4, InfusionVortex.STREAM_CAP);
        assertThat(frame).as("tick %d of %d", tick, duration).isLessThanOrEqualTo(InfusionVortex.FRAME_CAP);
      }
    }
    assertThat(InfusionVortex.STREAM_CAP).isEqualTo(6);
    assertThat(InfusionVortex.BURST_CAP).isEqualTo(60);
    assertThat(InfusionVortex.RING_POINTS).isEqualTo(32);
  }

  @Test
  void theShockwaveRingExpandsAcrossThePlinth() {
    assertThat(InfusionVortex.ringRadius(0D)).isLessThan(InfusionVortex.PEDESTAL_RADIUS);
    double previous = InfusionVortex.ringRadius(0D);
    for (double progress = 0.1D; progress <= 1D; progress += 0.1D) {
      double radius = InfusionVortex.ringRadius(progress);
      assertThat(radius).isGreaterThan(previous);
      previous = radius;
    }
    assertThat(InfusionVortex.ringRadius(1D)).isGreaterThan(InfusionVortex.PEDESTAL_RADIUS);
  }

  @Test
  void aFizzleScattersTheStreamsOutwardAndThinsThemOut() {
    for (double start : new double[] {0D, InfusionVortex.CORE_RADIUS, InfusionVortex.PEDESTAL_RADIUS}) {
      double previous = start;
      for (int tick = 1; tick < InfusionVortex.FIZZLE_TICKS; tick++) {
        double progress = (double) tick / (InfusionVortex.FIZZLE_TICKS - 1);
        double radius = InfusionVortex.scatterRadius(start, progress);
        assertThat(radius).isGreaterThan(previous);
        previous = radius;
      }
    }
    int previousPoints = InfusionVortex.fizzlePoints(0);
    assertThat(previousPoints).isLessThan(InfusionVortex.STREAM_CAP);
    for (int tick = 1; tick < InfusionVortex.FIZZLE_TICKS; tick++) {
      int points = InfusionVortex.fizzlePoints(tick);
      assertThat(points).isPositive().isLessThanOrEqualTo(previousPoints);
      previousPoints = points;
    }
    assertThat(InfusionVortex.fizzlePoints(InfusionVortex.FIZZLE_TICKS - 1)).isLessThan(InfusionVortex.fizzlePoints(0));
  }
}
