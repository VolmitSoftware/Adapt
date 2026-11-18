package art.arcane.adapt.content.adaptation.taming;

import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TamingPlayerPreferencesTest {
  @Test
  void explicitFetchCollarsAndItemsNarrowTheEligibleSet() {
    assertThat(TamingPreferences.Collar.BLUE.accepts(DyeColor.BLUE)).isTrue();
    assertThat(TamingPreferences.Collar.BLUE.accepts(DyeColor.RED)).isFalse();
    assertThat(TamingPreferences.Items.VALUABLES.accepts(Id.minecraft("diamond"))).isTrue();
    assertThat(TamingPreferences.Items.VALUABLES.accepts(Id.minecraft("diamond_sword"))).isFalse();
    assertThat(TamingPreferences.Pets.WOLVES.accepts(Id.minecraft("cat"))).isFalse();
    assertThat(TamingPreferences.Pets.HORSES.accepts(Id.minecraft("mule"))).isTrue();
  }

  @Test
  void healthReserveNeverLowersServerMinimum() {
    assertThat(TamingPreferences.Health.DEFAULT.floor(40D, 5D)).isEqualTo(5D);
    assertThat(TamingPreferences.Health.HALF.floor(40D, 5D)).isEqualTo(20D);
    assertThat(TamingPreferences.Health.HALF.floor(40D, 30D)).isEqualTo(30D);
    assertThat(TamingPreferences.Health.THREE_QUARTERS.floor(40D, 5D)).isEqualTo(30D);
  }

  @Test
  void recallAndCommandReservesDoNotReduceTheActionCost() {
    assertThat(TamingPreferences.Reserve.FOUR.permits(6, 2)).isTrue();
    assertThat(TamingPreferences.Reserve.FOUR.permits(5, 2)).isFalse();
    assertThat(TamingPreferences.Reserve.EIGHT.permits(8, 1)).isFalse();
  }
}
