package art.arcane.adapt.content.adaptation.sword;

import art.arcane.adapt.platform.value.Id;
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
    assertThat(SwordPreferences.Swords.PRECIOUS.accepts(Id.minecraft("diamond_sword"))).isTrue();
    assertThat(SwordPreferences.Swords.PRECIOUS.accepts(Id.minecraft("iron_sword"))).isFalse();
    assertThat(SwordPreferences.Foliage.GRASS.accepts(Id.minecraft("fern"))).isTrue();
    assertThat(SwordPreferences.Foliage.GRASS.accepts(Id.minecraft("oak_leaves"))).isFalse();
    assertThat(SwordPreferences.Foliage.VINES.accepts(Id.minecraft("twisting_vines"))).isTrue();
  }
}
