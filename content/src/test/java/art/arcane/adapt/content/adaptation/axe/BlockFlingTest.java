package art.arcane.adapt.content.adaptation.axe;

import art.arcane.adapt.platform.value.Quat;
import art.arcane.adapt.platform.value.Transform;
import art.arcane.adapt.platform.value.Vec3;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class BlockFlingTest {
  @Test
  void theLaunchLandsOnTheTargetUnderSnowballPhysics() {
    Vec3 from = new Vec3(0.5D, 60.9D, 1.5D);
    for (Vec3 to : new Vec3[]{new Vec3(0.5D, 62D, 9.5D), new Vec3(6D, 58D, -4D), new Vec3(-12D, 64D, 3D)}) {
      int ticks = BlockFling.flightTicks(from.distance(to), 1.1D);
      Vec3 velocity = BlockFling.launch(from, to, ticks);
      Vec3 at = from;
      Vec3 moving = velocity;
      double peak = from.y();
      for (int tick = 0; tick < ticks; tick++) {
        at = at.add(moving);
        moving = moving.multiply(BlockFling.DRAG).add(0D, -BlockFling.GRAVITY, 0D);
        peak = Math.max(peak, at.y());
      }
      assertThat(at.distance(to)).isLessThan(1.0E-6D);
      assertThat(peak).isGreaterThan(Math.min(from.y(), to.y()));
    }
  }

  @Test
  void flightTimeGrowsWithDistanceWithinBounds() {
    assertThat(BlockFling.flightTicks(0D, 1.1D)).isEqualTo(2);
    assertThat(BlockFling.flightTicks(11D, 1.1D)).isEqualTo(10);
    assertThat(BlockFling.flightTicks(500D, 1.1D)).isEqualTo(40);
  }

  @Test
  void theTumblingBlockSpinsAboutItsOwnCentre() {
    for (double tick : new double[]{0D, 3D, 17D}) {
      Transform pose = AxeThrow.tumble(tick);
      Vec3 centre = pose.translation().add(AxeThrow.rotate(pose.leftRotation(), new Vec3(0.25D, 0.25D, 0.25D)));
      assertThat(centre.x()).isCloseTo(0D, within(1.0E-5D));
      assertThat(centre.y()).isCloseTo(-0.25D, within(1.0E-5D));
      assertThat(centre.z()).isCloseTo(0D, within(1.0E-5D));
    }
    assertThat(AxeThrow.rotate(Quat.IDENTITY, new Vec3(1D, 2D, 3D))).isEqualTo(new Vec3(1D, 2D, 3D));
  }

  @Test
  void aReturnLandsAtTheImpactWhenTheOwnerDiedOrLeftTheWorld() {
    assertThat(AxeThrow.returnLandsAtImpact(true, null, null)).isTrue();
    assertThat(BlockFling.returnPoint(new Vec3(0D, 60D, 0D), new Vec3(4D, 60D, 0D), 0.5D).y()).isEqualTo(61D);
  }
}
