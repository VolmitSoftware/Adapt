package art.arcane.adapt.util.common.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeWorld;
import java.util.List;
import org.junit.jupiter.api.Test;

class VelocitySpeedTest {
  private static final double THRESHOLD_SQUARED = 0.0008D * 0.0008D;

  private static FakePlayer facingEast(Vec3 movement) {
    FakePlayer player = new FakePlayer("Walker", new Loc(new FakeWorld("world"), 0, 100, 0, -90F, 0F));
    player.setVelocity(new Vec3(0, -0.0784D, 0));
    player.setMovement(movement);
    return player;
  }

  @Test
  void walkingInfersInputFromClientMovementWhileServerVelocityHoldsOnlyGravity() {
    VelocitySpeed.InputSnapshot input = VelocitySpeed.readInput(facingEast(new Vec3(0.2D, 0, 0)), THRESHOLD_SQUARED, Vec3.ZERO);

    assertTrue(input.forward());
    assertFalse(input.backward());
    assertFalse(input.left());
    assertFalse(input.right());
  }

  @Test
  void standingStillInfersNoInput() {
    assertEquals(VelocitySpeed.InputSnapshot.NONE, VelocitySpeed.readInput(facingEast(Vec3.ZERO), THRESHOLD_SQUARED, Vec3.ZERO));
  }

  @Test
  void movementThatOnlyFollowsTheImposedVelocityIsNotInput() {
    Vec3 imposed = new Vec3(0, 0, 0.4D);

    assertEquals(VelocitySpeed.InputSnapshot.NONE,
        VelocitySpeed.readInput(facingEast(new Vec3(0.01D, 0, 0.41D)), THRESHOLD_SQUARED, imposed));
  }

  @Test
  void movementLeftWithNoExpectationIsNotInferred() {
    assertEquals(VelocitySpeed.InputSnapshot.NONE, VelocitySpeed.readInput(facingEast(new Vec3(0.3D, 0, 0)), THRESHOLD_SQUARED, null));
  }

  @Test
  void knockbackCoastingOutOnTheGroundIsNotInput() {
    Vec3 previous = new Vec3(0, 0, -0.2D);

    assertEquals(VelocitySpeed.InputSnapshot.NONE,
        VelocitySpeed.readInput(facingEast(previous.multiply(VelocitySpeed.GROUND_COAST)), THRESHOLD_SQUARED,
            previous.multiply(VelocitySpeed.GROUND_COAST)));
  }

  @Test
  void walkingOnTopOfAnImposedVelocityStillCounts() {
    Vec3 imposed = new Vec3(0, 0, 0.4D);

    VelocitySpeed.InputSnapshot input = VelocitySpeed.readInput(facingEast(new Vec3(0.1D, 0, 0.4D)), THRESHOLD_SQUARED, imposed);

    assertTrue(input.forward());
    assertFalse(input.right());
  }

  @Test
  void movementFollowsTheClosestRecentlyAppliedVelocity() {
    List<Vec3> applied = List.of(new Vec3(-0.045D, 0, 0), new Vec3(-0.09D, 0, 0));

    assertEquals(new Vec3(-0.045D, 0, 0), VelocitySpeed.closestApplied(new Vec3(-0.0449D, 0, 0), applied));
    assertEquals(new Vec3(-0.09D, 0, 0), VelocitySpeed.closestApplied(new Vec3(-0.0934D, 0, 0), applied));
  }

  @Test
  void coastingKeepsTheShareOfTheSurfaceTheTickStartedOn() {
    Vec3 previous = new Vec3(-0.1128D, 0.3D, 0);

    assertEquals(-0.1128D * VelocitySpeed.AIR_COAST, VelocitySpeed.coasting(previous, false).x(), 1.0E-9);
    assertEquals(-0.1128D * VelocitySpeed.GROUND_COAST, VelocitySpeed.coasting(previous, true).x(), 1.0E-9);
    assertEquals(0D, VelocitySpeed.coasting(previous, true).y(), 1.0E-9);
  }
}
