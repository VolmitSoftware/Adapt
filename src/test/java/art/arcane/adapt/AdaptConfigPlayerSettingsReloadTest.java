package art.arcane.adapt;

import art.arcane.adapt.api.preference.PlayerPreferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.File;
import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

class AdaptConfigPlayerSettingsReloadTest extends AdaptTestBase {
  private Field configField;
  private AdaptConfig previousConfig;

  @BeforeEach
  void installDefaultConfig() throws Exception {
    configField = AdaptConfig.class.getDeclaredField("config");
    configField.setAccessible(true);
    previousConfig = (AdaptConfig) configField.get(null);
    configField.set(null, new AdaptConfig());
  }

  @AfterEach
  void restoreConfig() throws Exception {
    configField.set(null, previousConfig);
  }

  @Test
  void lockingAndUnlockingReconcileOnlinePreferences() {
    File source = new File(dataFolder, "adapt.toml");
    try (MockedStatic<PlayerPreferences> preferences = mockStatic(PlayerPreferences.class)) {
      assertThat(AdaptConfig.reloadSnapshot("playerSettingsEnabled = false\n", source)).isTrue();
      assertThat(AdaptConfig.get().isPlayerSettingsEnabled()).isFalse();
      preferences.verify(PlayerPreferences::reconcileOnline);

      assertThat(AdaptConfig.reloadSnapshot("playerSettingsEnabled = true\n", source)).isTrue();
      assertThat(AdaptConfig.get().isPlayerSettingsEnabled()).isTrue();
      preferences.verify(PlayerPreferences::reconcileOnline, times(2));
    }
  }

  @Test
  void unrelatedReloadsDoNotReconcileOnlinePreferences() {
    File source = new File(dataFolder, "adapt.toml");
    try (MockedStatic<PlayerPreferences> preferences = mockStatic(PlayerPreferences.class)) {
      assertThat(AdaptConfig.reloadSnapshot("playerSettingsEnabled = true\nwelcomeMessage = false\n", source)).isTrue();
      preferences.verifyNoInteractions();

      assertThat(AdaptConfig.reloadSnapshot("playerSettingsEnabled = false\n", source)).isTrue();
      preferences.clearInvocations();
      assertThat(AdaptConfig.reloadSnapshot("playerSettingsEnabled = false\nwelcomeMessage = true\n", source)).isTrue();
      assertThat(AdaptConfig.get().isPlayerSettingsEnabled()).isFalse();
      preferences.verifyNoInteractions();
    }
  }

  @Test
  void editorMutationBeforeReloadStillReconcilesBothTransitions() throws Exception {
    File source = new File(dataFolder, "adapt.toml");
    Field settingsField = AdaptConfig.class.getDeclaredField("playerSettingsEnabled");
    settingsField.setAccessible(true);
    try (MockedStatic<PlayerPreferences> preferences = mockStatic(PlayerPreferences.class)) {
      settingsField.setBoolean(AdaptConfig.get(), false);
      assertThat(AdaptConfig.reloadSnapshot("playerSettingsEnabled = false\n", source)).isTrue();
      preferences.verify(PlayerPreferences::reconcileOnline);

      settingsField.setBoolean(AdaptConfig.get(), true);
      assertThat(AdaptConfig.reloadSnapshot("playerSettingsEnabled = true\n", source)).isTrue();
      preferences.verify(PlayerPreferences::reconcileOnline, times(2));
    }
  }
}
