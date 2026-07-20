package art.arcane.adapt.content.adaptation.agility;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class AgilityMovementRuntimeLogicTest {
  @Test
  void ladderCameraPitchMapsDirectlyToMovementModes() {
    assertThat(AgilityLadderSlide.resolveMode(AgilityLadderSlide.Mode.NONE, -45F, 20D, 10D))
        .isEqualTo(AgilityLadderSlide.Mode.CLIMB);
    assertThat(AgilityLadderSlide.resolveMode(AgilityLadderSlide.Mode.NONE, 45F, 20D, 10D))
        .isEqualTo(AgilityLadderSlide.Mode.SLIDE);
    assertThat(AgilityLadderSlide.resolveMode(AgilityLadderSlide.Mode.NONE, 0F, 20D, 10D))
        .isEqualTo(AgilityLadderSlide.Mode.NONE);
  }

  @Test
  void slipstreamProneWindowIncludesEveryTickBeforeDeadline() {
    assertThat(AgilitySlipstreamSlide.isProneActive(2_000L, 1_999L)).isTrue();
    assertThat(AgilitySlipstreamSlide.isProneActive(2_000L, 2_000L)).isFalse();
  }

  @Test
  void wallJumpTriggersOnlyWhenSneakIsReleasedWhileLatched() {
    assertThat(AgilityWallJump.shouldReleaseJump(false, true)).isTrue();
    assertThat(AgilityWallJump.shouldReleaseJump(true, true)).isFalse();
    assertThat(AgilityWallJump.shouldReleaseJump(false, false)).isFalse();
  }

  @Test
  void parkourMomentumBuildsAndCapsAcrossCleanLandings() {
    assertThat(AgilityParkourMomentum.advanceMomentum(0, 1, 6)).isEqualTo(1);
    assertThat(AgilityParkourMomentum.advanceMomentum(5, 2, 6)).isEqualTo(6);
    assertThat(AgilityParkourMomentum.advanceMomentum(3, -2, 6)).isEqualTo(3);
  }

  @Test
  void parkourChainExpiresOnlyAfterConfiguredWindow() {
    assertThat(AgilityParkourMomentum.chainExpired(1_000L, 3_500L, 2_500L)).isFalse();
    assertThat(AgilityParkourMomentum.chainExpired(1_000L, 3_501L, 2_500L)).isTrue();
  }

  @Test
  void parkourJumpBoostsScaleWithoutExceedingCaps() {
    assertThat(AgilityParkourMomentum.horizontalSpeed(2, 0.28D, 0.025D, 0.45D)).isCloseTo(0.33D, offset(1.0E-9D));
    assertThat(AgilityParkourMomentum.horizontalSpeed(20, 0.28D, 0.025D, 0.45D)).isEqualTo(0.45D);
    assertThat(AgilityParkourMomentum.verticalBoost(4, 0.015D, 0.12D)).isCloseTo(0.06D, offset(1.0E-9D));
    assertThat(AgilityParkourMomentum.verticalBoost(20, 0.015D, 0.12D)).isEqualTo(0.12D);
  }
}
