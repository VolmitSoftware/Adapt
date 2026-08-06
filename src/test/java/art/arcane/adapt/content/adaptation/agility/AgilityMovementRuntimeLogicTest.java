package art.arcane.adapt.content.adaptation.agility;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgilityMovementRuntimeLogicTest {
  @Test
  void ladderCameraPitchMapsDirectlyToMovementModes() {
    assertThat(AgilityLadderSlide.resolveMode(AgilityLadderSlide.Mode.NONE, -45F, 30D, 15D))
        .isEqualTo(AgilityLadderSlide.Mode.CLIMB);
    assertThat(AgilityLadderSlide.resolveMode(AgilityLadderSlide.Mode.NONE, 45F, 30D, 15D))
        .isEqualTo(AgilityLadderSlide.Mode.SLIDE);
    assertThat(AgilityLadderSlide.resolveMode(AgilityLadderSlide.Mode.NONE, 25F, 30D, 15D))
        .isEqualTo(AgilityLadderSlide.Mode.NONE);
  }

  @Test
  void ladderSneakOverridesDirectionalMovement() {
    assertThat(AgilityLadderSlide.resolveMode(AgilityLadderSlide.Mode.CLIMB, -45F, 20D, 10D, true))
        .isEqualTo(AgilityLadderSlide.Mode.NONE);
    assertThat(AgilityLadderSlide.resolveMode(AgilityLadderSlide.Mode.SLIDE, 45F, 20D, 10D, true))
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
}
