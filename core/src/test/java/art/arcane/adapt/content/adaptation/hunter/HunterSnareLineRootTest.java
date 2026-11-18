package art.arcane.adapt.content.adaptation.hunter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HunterSnareLineRootTest {
  @Test
  void rootScalarFullyStopsMovementAtDefaultAmplifier() {
    assertEquals(-1.0, HunterSnareLine.rootSpeedScalar(6));
  }

  @Test
  void rootScalarMatchesSlownessParityBelowTheCap() {
    assertEquals(-0.15, HunterSnareLine.rootSpeedScalar(0), 1.0e-9);
    assertEquals(-0.3, HunterSnareLine.rootSpeedScalar(1), 1.0e-9);
    assertEquals(-0.6, HunterSnareLine.rootSpeedScalar(3), 1.0e-9);
  }

  @Test
  void rootScalarClampsAtFullStopForHighAmplifiers() {
    assertEquals(-1.0, HunterSnareLine.rootSpeedScalar(40));
    assertEquals(-1.0, HunterSnareLine.rootSpeedScalar(Integer.MAX_VALUE));
  }

  @Test
  void rootScalarFloorsNegativeAmplifiersToSlownessOne() {
    assertEquals(HunterSnareLine.rootSpeedScalar(0), HunterSnareLine.rootSpeedScalar(-5));
  }

  @Test
  void rootScalarIsMonotonicallyStrongerWithAmplifier() {
    assertTrue(HunterSnareLine.rootSpeedScalar(2) < HunterSnareLine.rootSpeedScalar(1));
    assertTrue(HunterSnareLine.rootSpeedScalar(1) < HunterSnareLine.rootSpeedScalar(0));
  }
}
