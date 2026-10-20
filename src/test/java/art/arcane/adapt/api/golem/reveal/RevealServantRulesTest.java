package art.arcane.adapt.api.golem.reveal;

import art.arcane.adapt.api.golem.ChannelMask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RevealServantRulesTest {
  @Test
  void theGlowTakesTheFirstChannelsColorAndIsWhiteWithoutAChannel() {
    assertThat(RevealRules.glowColor(ChannelMask.NONE)).isEqualTo(ChatColor.WHITE);
    assertThat(RevealRules.glowColor(ChannelMask.of(DyeColor.RED))).isEqualTo(ChatColor.RED);
    assertThat(RevealRules.glowColor(ChannelMask.of(DyeColor.LIME, DyeColor.RED))).isEqualTo(ChatColor.GREEN);
    assertThat(RevealRules.glowColor(ChannelMask.of(DyeColor.BLUE))).isEqualTo(ChatColor.DARK_BLUE);
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
    Component label = GolemLabel.compose(new GolemLabel.Content(Component.text("Skeletal Servant"), 12D, 24D, "40s left", "", ChannelMask.of(DyeColor.RED), false, true));

    String plain = PlainTextComponentSerializer.plainText().serialize(label);

    assertThat(plain.lines().toList()).containsExactly("Skeletal Servant", "|||||||||| 12/24", "40s left · " + GolemLabel.SWATCH);
  }
}
