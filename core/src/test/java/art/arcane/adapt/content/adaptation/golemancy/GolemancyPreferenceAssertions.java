package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.world.AdaptPlayer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

final class GolemancyPreferenceAssertions {
  private GolemancyPreferenceAssertions() {
  }

  static void assertVisibleFrom(SimpleAdaptation<?> adaptation, PlayerPreference<?> preference, int unlockLevel) {
    SimpleAdaptation<?> learned = spy(adaptation);
    AdaptPlayer player = mock(AdaptPlayer.class);
    for (int level = 0; level <= adaptation.getMaxLevel(); level++) {
      doReturn(level).when(learned).getLevel(player);
      assertThat(learned.isPlayerPreferenceVisible(player, preference))
          .describedAs("%s %s visible at level %d", adaptation.getName(), preference.id(), level)
          .isEqualTo(level >= unlockLevel);
    }
  }

  static void serverDefault(SimpleAdaptation<?> adaptation, PlayerPreference<?> preference, String value) {
    AdaptationConfig config = (AdaptationConfig) adaptation.getConfig();
    config.playerPreferences.get(preference.id()).defaultValue = value;
  }
}
