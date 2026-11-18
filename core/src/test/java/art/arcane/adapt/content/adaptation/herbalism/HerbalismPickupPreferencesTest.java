package art.arcane.adapt.content.adaptation.herbalism;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class HerbalismPickupPreferencesTest {
  @Test
  void plantingOnlyLeavesHarvestedWheatOnTheGround() {
    HerbalismDropToInventory adaptation = mock(HerbalismDropToInventory.class, CALLS_REAL_METHODS);
    PlayerRef player = mock(PlayerRef.class);
    doReturn(HerbalismPreferences.Materials.ALL).when(adaptation).preference(player, HerbalismDropToInventory.MATERIALS);
    doReturn(true).when(adaptation).preferenceEnabled(player, HerbalismDropToInventory.SEEDS_ONLY);

    assertThat(adaptation.acceptsDrop(player, Id.minecraft("wheat_seeds"))).isTrue();
    assertThat(adaptation.acceptsDrop(player, Id.minecraft("wheat"))).isFalse();
    assertThat(adaptation.acceptsDrop(player, Id.minecraft("potato"))).isTrue();
  }

  @Test
  void cropSelectionAndPlantingFilterIntersectForEachPlayer() {
    HerbalismDropToInventory adaptation = mock(HerbalismDropToInventory.class, CALLS_REAL_METHODS);
    PlayerRef wheatFarmer = mock(PlayerRef.class);
    PlayerRef potatoFarmer = mock(PlayerRef.class);
    doReturn(HerbalismPreferences.Materials.WHEAT).when(adaptation).preference(wheatFarmer, HerbalismDropToInventory.MATERIALS);
    doReturn(HerbalismPreferences.Materials.POTATO).when(adaptation).preference(potatoFarmer, HerbalismDropToInventory.MATERIALS);
    doReturn(false).when(adaptation).preferenceEnabled(wheatFarmer, HerbalismDropToInventory.SEEDS_ONLY);
    doReturn(true).when(adaptation).preferenceEnabled(potatoFarmer, HerbalismDropToInventory.SEEDS_ONLY);

    assertThat(adaptation.acceptsDrop(wheatFarmer, Id.minecraft("wheat"))).isTrue();
    assertThat(adaptation.acceptsDrop(wheatFarmer, Id.minecraft("potato"))).isFalse();
    assertThat(adaptation.acceptsDrop(potatoFarmer, Id.minecraft("wheat_seeds"))).isFalse();
    assertThat(adaptation.acceptsDrop(potatoFarmer, Id.minecraft("potato"))).isTrue();
  }
}
