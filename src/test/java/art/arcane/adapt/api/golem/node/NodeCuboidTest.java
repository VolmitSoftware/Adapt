package art.arcane.adapt.api.golem.node;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NodeCuboidTest {
  @Test
  void aSymmetricCuboidReachesTheRadiusOnBothSidesOfEveryAxis() {
    NodeCuboid area = NodeCuboid.symmetric(2, 1, 3);

    assertThat(area).isEqualTo(new NodeCuboid(-2, -1, -3, 2, 1, 3));
    assertThat(area.spanX()).isEqualTo(5);
    assertThat(area.spanY()).isEqualTo(3);
    assertThat(area.spanZ()).isEqualTo(7);
    assertThat(area.volume()).isEqualTo(5 * 3 * 7);
  }

  @Test
  void containsTakesEveryOffsetBetweenTheFacesInclusive() {
    NodeCuboid area = new NodeCuboid(-1, 0, -4, 5, 2, 0);

    assertThat(area.contains(0, 0, 0)).isTrue();
    assertThat(area.contains(-1, 0, -4)).isTrue();
    assertThat(area.contains(5, 2, 0)).isTrue();
    assertThat(area.contains(-2, 0, 0)).isFalse();
    assertThat(area.contains(6, 0, 0)).isFalse();
    assertThat(area.contains(0, -1, 0)).isFalse();
    assertThat(area.contains(0, 3, 0)).isFalse();
    assertThat(area.contains(0, 0, 1)).isFalse();
    assertThat(area.volume()).isEqualTo(7 * 3 * 5);
  }

  @Test
  void aCuboidThatHoldsTheCenterWithEveryFaceInReachFits() {
    assertThat(new NodeCuboid(-8, 0, -1, 0, 8, 7).check(8).fits()).isTrue();
    assertThat(new NodeCuboid(0, 0, 0, 0, 0, 0).check(1).fits()).isTrue();
    assertThat(new NodeCuboid(-8, -8, -8, 7, 7, 7).check(32).fits()).isTrue();
    assertThat(NodeCuboid.symmetric(8, 8, 8).check(32).verdict()).isEqualTo(NodeCuboid.Verdict.TOO_LARGE);
  }

  @Test
  void aCuboidThatMissesTheCenterBlockIsRejectedForThatReason() {
    NodeCuboid.Limit beside = new NodeCuboid(1, 0, 0, 4, 2, 2).check(8);
    NodeCuboid.Limit below = new NodeCuboid(-2, -5, -2, 2, -1, 2).check(8);

    assertThat(beside.verdict()).isEqualTo(NodeCuboid.Verdict.MISSING_CENTER);
    assertThat(beside.axis()).isEqualTo(NodeEdits.Axis.X);
    assertThat(below.verdict()).isEqualTo(NodeCuboid.Verdict.MISSING_CENTER);
    assertThat(below.axis()).isEqualTo(NodeEdits.Axis.Y);
  }

  @Test
  void aFaceBeyondTheMaxRadiusIsRejectedNamingTheFarthestFace() {
    NodeCuboid.Limit east = new NodeCuboid(-2, 0, -2, 9, 1, 2).check(8);
    NodeCuboid.Limit down = new NodeCuboid(-9, -12, 0, 0, 0, 3).check(8);
    NodeCuboid.Limit south = new NodeCuboid(0, 0, 0, 0, 0, 9).check(8);

    assertThat(east.verdict()).isEqualTo(NodeCuboid.Verdict.TOO_FAR);
    assertThat(east.face()).isEqualTo("+X");
    assertThat(east.distance()).isEqualTo(9);
    assertThat(down.verdict()).isEqualTo(NodeCuboid.Verdict.TOO_FAR);
    assertThat(down.face()).isEqualTo("-Y");
    assertThat(down.distance()).isEqualTo(12);
    assertThat(south.face()).isEqualTo("+Z");
    assertThat(south.distance()).isEqualTo(9);
  }

  @Test
  void eachFaceIsMeasuredFromTheCenterOnItsOwnAxis() {
    for (NodeEdits.Axis axis : NodeEdits.Axis.values()) {
      NodeCuboid low = NodeCuboid.symmetric(0, 0, 0).withAxis(axis, -5, 0);
      NodeCuboid high = NodeCuboid.symmetric(0, 0, 0).withAxis(axis, 0, 5);

      assertThat(low.check(5).fits()).as("%s low face at the limit", axis).isTrue();
      assertThat(high.check(5).fits()).as("%s high face at the limit", axis).isTrue();
      assertThat(low.check(4).face()).isEqualTo("-" + axis.name());
      assertThat(high.check(4).face()).isEqualTo("+" + axis.name());
      assertThat(low.min(axis)).isEqualTo(-5);
      assertThat(high.max(axis)).isEqualTo(5);
    }
  }

  @Test
  void clampingPullsEveryFaceBackToTheCenterAndTheLimit() {
    assertThat(new NodeCuboid(3, -40, -2, 10, 50, -1).clamp(32)).isEqualTo(new NodeCuboid(0, -32, -2, 10, 32, 0));
  }

  @Test
  void theHorizontalReachIsTheFarthestSideFace() {
    assertThat(new NodeCuboid(-3, -20, -1, 2, 20, 7).horizontalReach()).isEqualTo(7);
    assertThat(new NodeCuboid(-9, 0, 0, 2, 0, 1).horizontalReach()).isEqualTo(9);
  }

  @Test
  void aSixteenBlockAreaCanMoveUntilTheNodeTouchesEitherFace() {
    NodeCuboid cube = new NodeCuboid(-8, -8, -8, 7, 7, 7);
    for (NodeEdits.Axis axis : NodeEdits.Axis.values()) {
      NodeCuboid positive = cube.shift(axis, 8);
      NodeCuboid negative = cube.shift(axis, -7);
      assertThat(positive.check(32).fits()).isTrue();
      assertThat(negative.check(32).fits()).isTrue();
      assertThat(positive.shift(axis, 1).check(32).verdict()).isEqualTo(NodeCuboid.Verdict.MISSING_CENTER);
      assertThat(negative.shift(axis, -1).check(32).verdict()).isEqualTo(NodeCuboid.Verdict.MISSING_CENTER);
      assertThat(positive.volume()).isEqualTo(4096);
    }
  }
}
