package art.arcane.adapt.content.adaptation.unarmed;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UnarmedPlayerPreferencesTest {
  @Test
  void chargeLoadoutDoesNotAdmitAnUnselectedWeapon() {
    assertThat(UnarmedPreferences.Loadout.FISTS.accepts(false, true)).isFalse();
    assertThat(UnarmedPreferences.Loadout.SHIELD.accepts(true, false)).isFalse();
    assertThat(UnarmedPreferences.Loadout.BOTH.accepts(false, true)).isTrue();
    assertThat(UnarmedPreferences.Loadout.BOTH.accepts(false, false)).isFalse();
  }

  @Test
  void clapReserveIncludesEntireHungerCost() {
    assertThat(UnarmedPreferences.Reserve.TEN.permits(12, 2)).isTrue();
    assertThat(UnarmedPreferences.Reserve.TEN.permits(11, 2)).isFalse();
    assertThat(UnarmedPreferences.Reserve.NONE.permits(1, 2)).isFalse();
  }
}
