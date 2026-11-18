package art.arcane.adapt.content.adaptation.tragoul;

import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.viewer.GlowColor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TragoulPlayerPreferencesTest {
  @Test
  void targetGroupsSeparateUndeadArthropodsAndPlayers() {
    assertThat(TragoulPreferences.Targets.UNDEAD.accepts(Id.minecraft("drowned"))).isTrue();
    assertThat(TragoulPreferences.Targets.UNDEAD.accepts(Id.minecraft("spider"))).isFalse();
    assertThat(TragoulPreferences.Targets.ARTHROPODS.accepts(Id.minecraft("spider"))).isTrue();
    assertThat(TragoulPreferences.Targets.NON_PLAYERS.accepts(Id.minecraft("player"))).isFalse();
  }

  @Test
  void reservesAddToFullBoneCostAndCoolPaletteRetainsFourHealthBands() {
    assertThat(TragoulPreferences.Reserve.EIGHT.required(3)).isEqualTo(11);
    assertThat(TragoulPreferences.Palette.COOL.color(GlowColor.DARK_RED)).isEqualTo(GlowColor.DARK_BLUE);
    assertThat(TragoulPreferences.Palette.COOL.color(GlowColor.RED)).isEqualTo(GlowColor.BLUE);
    assertThat(TragoulPreferences.Palette.COOL.color(GlowColor.GOLD)).isEqualTo(GlowColor.AQUA);
    assertThat(TragoulPreferences.Palette.COOL.color(GlowColor.YELLOW)).isEqualTo(GlowColor.WHITE);
  }
}
