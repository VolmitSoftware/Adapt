package art.arcane.adapt.content.adaptation.herbalism;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismGrowables.Growable;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class HerbalismGrowablesTest {
  @Test
  void everyAgeingPlantBlockMapsToItsGrowableAndAnythingElseToOther() {
    assertThat(HerbalismGrowables.of(Material.WHEAT)).isEqualTo(Growable.WHEAT);
    assertThat(HerbalismGrowables.of(Material.CARROTS)).isEqualTo(Growable.CARROTS);
    assertThat(HerbalismGrowables.of(Material.MELON_STEM)).isEqualTo(Growable.MELONS);
    assertThat(HerbalismGrowables.of(Material.SWEET_BERRY_BUSH)).isEqualTo(Growable.SWEET_BERRIES);
    assertThat(HerbalismGrowables.of(Material.CAVE_VINES)).isEqualTo(Growable.GLOW_BERRIES);
    assertThat(HerbalismGrowables.of(Material.TORCHFLOWER_CROP)).isEqualTo(Growable.TORCHFLOWERS);
    assertThat(HerbalismGrowables.of(Material.PITCHER_CROP)).isEqualTo(Growable.PITCHER_PLANTS);
    assertThat(HerbalismGrowables.of(Material.WEEPING_VINES)).isEqualTo(Growable.NETHER_VINES);
    assertThat(HerbalismGrowables.of(Material.TWISTING_VINES)).isEqualTo(Growable.NETHER_VINES);
    assertThat(HerbalismGrowables.of(Material.MANGROVE_PROPAGULE)).isEqualTo(Growable.MANGROVE_PROPAGULES);
    assertThat(HerbalismGrowables.of(Material.FROSTED_ICE)).isEqualTo(Growable.OTHER);
  }

  @Test
  void eachGrowableHasItsOwnToggleThatStartsOn() {
    Set<String> ids = new HashSet<>();
    for (Growable growable : Growable.values()) {
      PlayerPreference<CommonPreferences.Toggle> preference = growable.preference();
      assertThat(ids.add(preference.id())).describedAs(preference.id()).isTrue();
      assertThat(preference.id()).startsWith("growable-");
      assertThat(preference.defaultValue()).isEqualTo(CommonPreferences.Toggle.ON);
    }
    assertThat(HerbalismGrowthAura.GROWABLES.entries()).hasSize(Growable.values().length);
  }

  @Test
  void aGrowableIsBlockedOnlyWhenEveryBlockItGrowsIsBlacklisted() {
    Predicate<Material> weepingOnly = Set.of(Material.WEEPING_VINES)::contains;
    Predicate<Material> bothVines = Set.of(Material.WEEPING_VINES, Material.TWISTING_VINES)::contains;

    assertThat(HerbalismGrowables.blocked(Growable.GLOW_BERRIES, Set.of(Material.CAVE_VINES)::contains)).isTrue();
    assertThat(HerbalismGrowables.blocked(Growable.NETHER_VINES, weepingOnly)).isFalse();
    assertThat(HerbalismGrowables.blocked(Growable.NETHER_VINES, bothVines)).isTrue();
    assertThat(HerbalismGrowables.blocked(Growable.OTHER, material -> true)).isFalse();
    assertThat(HerbalismGrowables.blocked(HerbalismGrowthAura.SURFACE, material -> true)).isFalse();
  }

  @Test
  void growthAuraRegistersEveryGrowableBehindOneSettingsGroup() {
    HerbalismGrowthAura aura = mock(HerbalismGrowthAura.class, CALLS_REAL_METHODS);

    assertThat(aura.getPlayerPreferenceGroups()).containsExactly(HerbalismGrowthAura.GROWABLES);
    assertThat(aura.getPlayerPreferences())
        .containsAll(HerbalismGrowthAura.GROWABLES.preferences())
        .contains(CommonPreferences.ENABLED, HerbalismGrowthAura.SURFACE, HerbalismGrowthAura.FOOD_RESERVE, HerbalismGrowthAura.RADIUS)
        .extracting(PlayerPreference::id)
        .doesNotContain("materials");
  }

  @Test
  void growthAuraSkipsPlayerDisabledAndServerBlacklistedPlants() {
    HerbalismGrowthAura aura = mock(HerbalismGrowthAura.class, CALLS_REAL_METHODS);
    Player player = mock(Player.class);
    doReturn(true).when(aura).preferenceEnabled(eq(player), any());
    doReturn(false).when(aura).preferenceEnabled(player, Growable.WHEAT.preference());
    Set<Material> blacklist = Set.of(Material.CAVE_VINES, Material.CAVE_VINES_PLANT);
    AdaptConfig config = mock(AdaptConfig.class);
    when(config.isGrowthBlacklisted(any())).thenAnswer(call -> blacklist.contains(call.<Material>getArgument(0)));

    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class)) {
      configured.when(AdaptConfig::get).thenReturn(config);

      assertThat(aura.grows(player, Material.WHEAT)).isFalse();
      assertThat(aura.grows(player, Material.CARROTS)).isTrue();
      assertThat(aura.grows(player, Material.CAVE_VINES)).isFalse();
      assertThat(aura.grows(player, Material.FROSTED_ICE)).isTrue();
      assertThat(aura.isPlayerPreferenceBlocked(Growable.GLOW_BERRIES.preference())).isTrue();
      assertThat(aura.isPlayerPreferenceBlocked(Growable.WHEAT.preference())).isFalse();
      assertThat(List.of(Growable.values())).filteredOn(growable -> aura.isPlayerPreferenceBlocked(growable.preference()))
          .containsExactly(Growable.GLOW_BERRIES);
    }
  }
}
