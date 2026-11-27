package art.arcane.adapt.content.adaptation.rift;

import art.arcane.adapt.platform.value.Vec3;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class RiftPearlReboundDirectionTest {
  private static final Vec3 WEST_FACE = new Vec3(-1D, 0D, 0D);
  private static final Vec3 REFLECTED = new Vec3(-1D, 0D, 0D);

  @Test
  void crosshairStillOnTheWallSendsTheReboundAwayFromIt() {
    Vec3 aimIntoWall = new Vec3(1D, 0D, 0D);

    Vec3 direction = RiftPearlRebound.reboundDirection(REFLECTED, aimIntoWall, WEST_FACE, 0.9D);

    assertThat(direction.dot(WEST_FACE)).isPositive();
    assertThat(direction.length()).isCloseTo(1D, offset(0.000001D));
  }

  @Test
  void crosshairOnTheWallKeepsItsSidewaysSteering() {
    Vec3 aimIntoWallAndNorth = new Vec3(1D, 0D, -1D).normalize();

    Vec3 direction = RiftPearlRebound.reboundDirection(REFLECTED, aimIntoWallAndNorth, WEST_FACE, 0.9D);

    assertThat(direction.dot(WEST_FACE)).isPositive();
    assertThat(direction.z()).isNegative();
  }

  @Test
  void crosshairBehindTheShooterIsFollowedUnchanged() {
    Vec3 aimBack = new Vec3(-1D, 0.2D, 0.3D).normalize();
    Vec3 reflected = new Vec3(-1D, 0D, 0.2D).normalize();
    double bias = 0.6D;
    Vec3 blended = reflected.multiply(1D - bias).add(aimBack.multiply(bias)).normalize();

    Vec3 direction = RiftPearlRebound.reboundDirection(reflected, aimBack, WEST_FACE, bias);

    assertThat(direction.x()).isCloseTo(blended.x(), offset(0.000001D));
    assertThat(direction.y()).isCloseTo(blended.y(), offset(0.000001D));
    assertThat(direction.z()).isCloseTo(blended.z(), offset(0.000001D));
  }
}
