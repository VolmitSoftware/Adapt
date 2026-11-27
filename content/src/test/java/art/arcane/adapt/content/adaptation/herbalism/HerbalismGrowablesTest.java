package art.arcane.adapt.content.adaptation.herbalism;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismGrowables.Growable;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
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

class HerbalismGrowablesTest extends AdaptTestBase {
  @Test
  void everyAgeingPlantBlockMapsToItsGrowableAndAnythingElseToOther() {
    assertThat(HerbalismGrowables.of(Id.minecraft("wheat"))).isEqualTo(Growable.WHEAT);
    assertThat(HerbalismGrowables.of(Id.minecraft("carrots"))).isEqualTo(Growable.CARROTS);
    assertThat(HerbalismGrowables.of(Id.minecraft("melon_stem"))).isEqualTo(Growable.MELONS);
    assertThat(HerbalismGrowables.of(Id.minecraft("sweet_berry_bush"))).isEqualTo(Growable.SWEET_BERRIES);
    assertThat(HerbalismGrowables.of(Id.minecraft("cave_vines"))).isEqualTo(Growable.GLOW_BERRIES);
    assertThat(HerbalismGrowables.of(Id.minecraft("torchflower_crop"))).isEqualTo(Growable.TORCHFLOWERS);
    assertThat(HerbalismGrowables.of(Id.minecraft("pitcher_crop"))).isEqualTo(Growable.PITCHER_PLANTS);
    assertThat(HerbalismGrowables.of(Id.minecraft("weeping_vines"))).isEqualTo(Growable.NETHER_VINES);
    assertThat(HerbalismGrowables.of(Id.minecraft("twisting_vines"))).isEqualTo(Growable.NETHER_VINES);
    assertThat(HerbalismGrowables.of(Id.minecraft("mangrove_propagule"))).isEqualTo(Growable.MANGROVE_PROPAGULES);
    assertThat(HerbalismGrowables.of(Id.minecraft("frosted_ice"))).isEqualTo(Growable.OTHER);
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
    Predicate<Id> weepingOnly = Set.of(Id.minecraft("weeping_vines"))::contains;
    Predicate<Id> bothVines = Set.of(Id.minecraft("weeping_vines"), Id.minecraft("twisting_vines"))::contains;

    assertThat(HerbalismGrowables.blocked(Growable.GLOW_BERRIES, Set.of(Id.minecraft("cave_vines"))::contains)).isTrue();
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
    PlayerRef player = mock(PlayerRef.class);
    doReturn(true).when(aura).preferenceEnabled(eq(player), any());
    doReturn(false).when(aura).preferenceEnabled(player, Growable.WHEAT.preference());
    Set<Id> blacklist = Set.of(Id.minecraft("cave_vines"), Id.minecraft("cave_vines_plant"));
    AdaptConfig config = mock(AdaptConfig.class);
    when(config.isGrowthBlacklisted(any(Id.class))).thenAnswer(call -> blacklist.contains(call.<Id>getArgument(0)));

    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class)) {
      configured.when(AdaptConfig::get).thenReturn(config);

      assertThat(aura.grows(player, Id.minecraft("wheat"))).isFalse();
      assertThat(aura.grows(player, Id.minecraft("carrots"))).isTrue();
      assertThat(aura.grows(player, Id.minecraft("cave_vines"))).isFalse();
      assertThat(aura.grows(player, Id.minecraft("frosted_ice"))).isTrue();
      assertThat(aura.isPlayerPreferenceBlocked(Growable.GLOW_BERRIES.preference())).isTrue();
      assertThat(aura.isPlayerPreferenceBlocked(Growable.WHEAT.preference())).isFalse();
      assertThat(List.of(Growable.values())).filteredOn(growable -> aura.isPlayerPreferenceBlocked(growable.preference()))
          .containsExactly(Growable.GLOW_BERRIES);
    }
  }
}
