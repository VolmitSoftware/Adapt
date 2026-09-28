package art.arcane.adapt.content.adaptation.sword;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SwordPlayerPreferencesTest {
  @Test
  void experienceReserveIncludesTheFullRitualCost() {
    assertThat(SwordPreferences.XpReserve.THIRTY.permits(32, 2)).isTrue();
    assertThat(SwordPreferences.XpReserve.THIRTY.permits(31, 2)).isFalse();
    assertThat(SwordPreferences.XpReserve.NONE.permits(1, 2)).isFalse();
  }

  @Test
  void materialPresetsKeepOnlyChosenSwordsAndFoliage() {
    assertThat(SwordPreferences.Swords.PRECIOUS.accepts(Material.DIAMOND_SWORD)).isTrue();
    assertThat(SwordPreferences.Swords.PRECIOUS.accepts(Material.IRON_SWORD)).isFalse();
    assertThat(SwordPreferences.Foliage.GRASS.accepts(Material.FERN)).isTrue();
    assertThat(SwordPreferences.Foliage.GRASS.accepts(Material.OAK_LEAVES)).isFalse();
    assertThat(SwordPreferences.Foliage.VINES.accepts(Material.TWISTING_VINES)).isTrue();
  }
}
