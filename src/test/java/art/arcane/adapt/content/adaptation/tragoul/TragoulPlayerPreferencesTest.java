package art.arcane.adapt.content.adaptation.tragoul;

import org.bukkit.ChatColor;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TragoulPlayerPreferencesTest {
  @Test
  void targetGroupsSeparateUndeadArthropodsAndPlayers() {
    assertThat(TragoulPreferences.Targets.UNDEAD.accepts(EntityType.DROWNED)).isTrue();
    assertThat(TragoulPreferences.Targets.UNDEAD.accepts(EntityType.SPIDER)).isFalse();
    assertThat(TragoulPreferences.Targets.ARTHROPODS.accepts(EntityType.SPIDER)).isTrue();
    assertThat(TragoulPreferences.Targets.NON_PLAYERS.accepts(EntityType.PLAYER)).isFalse();
  }

  @Test
  void reservesAddToFullBoneCostAndCoolPaletteRetainsFourHealthBands() {
    assertThat(TragoulPreferences.Reserve.EIGHT.required(3)).isEqualTo(11);
    assertThat(TragoulPreferences.Palette.COOL.color(ChatColor.DARK_RED)).isEqualTo(ChatColor.DARK_BLUE);
    assertThat(TragoulPreferences.Palette.COOL.color(ChatColor.RED)).isEqualTo(ChatColor.BLUE);
    assertThat(TragoulPreferences.Palette.COOL.color(ChatColor.GOLD)).isEqualTo(ChatColor.AQUA);
    assertThat(TragoulPreferences.Palette.COOL.color(ChatColor.YELLOW)).isEqualTo(ChatColor.WHITE);
  }
}
