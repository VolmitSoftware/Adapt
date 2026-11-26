package art.arcane.adapt.content.adaptation.hunter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HunterSnareLineRootTest {
  @Test
  void rootScalarFullyStopsMovementAtDefaultAmplifier() {
    assertEquals(-1.0, HunterSnares.rootSpeedScalar(6));
  }

  @Test
  void rootScalarMatchesSlownessParityBelowTheCap() {
    assertEquals(-0.15, HunterSnares.rootSpeedScalar(0), 1.0e-9);
    assertEquals(-0.3, HunterSnares.rootSpeedScalar(1), 1.0e-9);
    assertEquals(-0.6, HunterSnares.rootSpeedScalar(3), 1.0e-9);
  }

  @Test
  void rootScalarClampsAtFullStopForHighAmplifiers() {
    assertEquals(-1.0, HunterSnares.rootSpeedScalar(40));
    assertEquals(-1.0, HunterSnares.rootSpeedScalar(Integer.MAX_VALUE));
  }

  @Test
  void rootScalarFloorsNegativeAmplifiersToSlownessOne() {
    assertEquals(HunterSnares.rootSpeedScalar(0), HunterSnares.rootSpeedScalar(-5));
  }

  @Test
  void rootScalarIsMonotonicallyStrongerWithAmplifier() {
    assertTrue(HunterSnares.rootSpeedScalar(2) < HunterSnares.rootSpeedScalar(1));
    assertTrue(HunterSnares.rootSpeedScalar(1) < HunterSnares.rootSpeedScalar(0));
  }
}
