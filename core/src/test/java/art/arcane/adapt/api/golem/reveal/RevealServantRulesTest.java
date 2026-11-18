package art.arcane.adapt.api.golem.reveal;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.golem.ChannelMask;
import art.arcane.adapt.localization.MarkupPort;
import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.viewer.GlowColor;
import art.arcane.adapt.testkit.TestMarkup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RevealServantRulesTest {
  @BeforeEach
  void installMarkup() {
    AdaptPorts.install(MarkupPort.class, new TestMarkup());
  }

  @AfterEach
  void removeMarkup() {
    AdaptPorts.uninstall(MarkupPort.class);
  }

  @Test
  void theGlowTakesTheFirstChannelsColorAndIsWhiteWithoutAChannel() {
    assertThat(RevealRules.glowColor(ChannelMask.NONE)).isEqualTo(GlowColor.WHITE);
    assertThat(RevealRules.glowColor(ChannelMask.of(DyeColor.RED))).isEqualTo(GlowColor.RED);
    assertThat(RevealRules.glowColor(ChannelMask.of(DyeColor.LIME, DyeColor.RED))).isEqualTo(GlowColor.GREEN);
    assertThat(RevealRules.glowColor(ChannelMask.of(DyeColor.BLUE))).isEqualTo(GlowColor.DARK_BLUE);
  }

  @Test
  void secondsLeftRoundUpAndNeverGoNegative() {
    assertThat(RevealRules.secondsLeft(10_000L, 0L)).isEqualTo(10L);
    assertThat(RevealRules.secondsLeft(10_000L, 9_001L)).isEqualTo(1L);
    assertThat(RevealRules.secondsLeft(10_000L, 10_000L)).isZero();
    assertThat(RevealRules.secondsLeft(10_000L, 12_000L)).isZero();
  }

  @Test
  void aServantLabelWithoutAModeShowsTimeLeftBesideTheChannel() {
    String label = GolemLabel.compose(new GolemLabel.Content("Skeletal Servant", 12D, 24D, "40s left", "", ChannelMask.of(DyeColor.RED), false, true));
    String plain = MarkupPort.current().plain(label);

    assertThat(plain.lines().toList()).containsExactly("Skeletal Servant", "|||||||||| 12/24", "40s left · " + GolemLabel.SWATCH);
  }
}
