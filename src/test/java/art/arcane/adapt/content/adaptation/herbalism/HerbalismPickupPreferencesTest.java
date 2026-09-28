package art.arcane.adapt.content.adaptation.herbalism;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class HerbalismPickupPreferencesTest {
  @Test
  void plantingOnlyLeavesHarvestedWheatOnTheGround() {
    HerbalismDropToInventory adaptation = mock(HerbalismDropToInventory.class, CALLS_REAL_METHODS);
    Player player = mock(Player.class);
    doReturn(HerbalismPreferences.Materials.ALL).when(adaptation).preference(player, HerbalismDropToInventory.MATERIALS);
    doReturn(true).when(adaptation).preferenceEnabled(player, HerbalismDropToInventory.SEEDS_ONLY);

    assertThat(adaptation.acceptsDrop(player, Material.WHEAT_SEEDS)).isTrue();
    assertThat(adaptation.acceptsDrop(player, Material.WHEAT)).isFalse();
    assertThat(adaptation.acceptsDrop(player, Material.POTATO)).isTrue();
  }

  @Test
  void cropSelectionAndPlantingFilterIntersectForEachPlayer() {
    HerbalismDropToInventory adaptation = mock(HerbalismDropToInventory.class, CALLS_REAL_METHODS);
    Player wheatFarmer = mock(Player.class);
    Player potatoFarmer = mock(Player.class);
    doReturn(HerbalismPreferences.Materials.WHEAT).when(adaptation).preference(wheatFarmer, HerbalismDropToInventory.MATERIALS);
    doReturn(HerbalismPreferences.Materials.POTATO).when(adaptation).preference(potatoFarmer, HerbalismDropToInventory.MATERIALS);
    doReturn(false).when(adaptation).preferenceEnabled(wheatFarmer, HerbalismDropToInventory.SEEDS_ONLY);
    doReturn(true).when(adaptation).preferenceEnabled(potatoFarmer, HerbalismDropToInventory.SEEDS_ONLY);

    assertThat(adaptation.acceptsDrop(wheatFarmer, Material.WHEAT)).isTrue();
    assertThat(adaptation.acceptsDrop(wheatFarmer, Material.POTATO)).isFalse();
    assertThat(adaptation.acceptsDrop(potatoFarmer, Material.WHEAT_SEEDS)).isFalse();
    assertThat(adaptation.acceptsDrop(potatoFarmer, Material.POTATO)).isTrue();
  }
}
