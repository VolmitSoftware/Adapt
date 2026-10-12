package art.arcane.adapt.api.preference;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillConfig;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.util.config.TomlCodec;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerSettingsLockTest extends AdaptTestBase {
  @Test
  void globalLockForcesConfiguredDefaultsAndRestoresDormantChoices() throws Exception {
    Adaptation<?> adaptation = mock(Adaptation.class);
    Skill<?> skill = mock(Skill.class);
    AdaptationConfig adaptationConfig = new AdaptationConfig();
    SkillConfig skillConfig = new SkillConfig();
    when(adaptation.getName()).thenReturn("agility-air-dash");
    when(adaptation.getPlayerPreferences()).thenReturn(List.of(CommonPreferences.ENABLED));
    when(skill.getName()).thenReturn("agility");
    doReturn(adaptationConfig).when(adaptation).getConfig();
    doReturn(skillConfig).when(skill).getConfig();
    doReturn(skill).when(adaptation).getSkill();
    PlayerPreferences.validate(adaptation, adaptationConfig);
    PlayerPreferences.validate(skill, skillConfig);
    PlayerData data = new PlayerData();
    data.getPreferences().set(adaptation.getName(), "enabled", "OFF");
    data.getPreferences().setSkill(skill.getName(), "enabled", "OFF");
    AdaptConfig locked = TomlCodec.fromToml("playerSettingsEnabled = false\n", AdaptConfig.class);
    AdaptConfig unlocked = new AdaptConfig();
    try (MockedStatic<AdaptConfig> configuration = mockStatic(AdaptConfig.class)) {
      configuration.when(AdaptConfig::get).thenReturn(locked);
      assertThat(PlayerPreferences.isEnabled(adaptation, data, 3)).isTrue();
      adaptationConfig.playerPreferences.get("enabled").defaultValue = "OFF";
      assertThat(PlayerPreferences.isEnabled(adaptation, data, 3)).isFalse();
      adaptationConfig.playerPreferences.get("enabled").defaultValue = "ON";
      skillConfig.playerPreferences.get("enabled").defaultValue = "OFF";
      assertThat(PlayerPreferences.isEnabled(adaptation, data, 3)).isFalse();
      skillConfig.playerPreferences.get("enabled").defaultValue = "ON";
      assertThat(data.getPreferences().get(adaptation.getName(), "enabled")).isEqualTo("OFF");
      assertThat(data.getPreferences().getSkill(skill.getName(), "enabled")).isEqualTo("OFF");
      configuration.when(AdaptConfig::get).thenReturn(unlocked);
      assertThat(PlayerPreferences.isEnabled(adaptation, data, 3)).isFalse();
    }
  }

  @Test
  void globalLockRejectsWritesAndResetsWithoutSavingOrReconciling() throws Exception {
    AdaptConfig locked = TomlCodec.fromToml("playerSettingsEnabled = false\n", AdaptConfig.class);
    Adaptation<?> adaptation = mock(Adaptation.class);
    Skill<?> skill = mock(Skill.class);
    AdaptPlayer player = mock(AdaptPlayer.class);
    try (MockedStatic<AdaptConfig> configuration = mockStatic(AdaptConfig.class)) {
      configuration.when(AdaptConfig::get).thenReturn(locked);
      assertThat(PlayerPreferences.set(adaptation, player, CommonPreferences.ENABLED, CommonPreferences.Toggle.OFF)).isFalse();
      assertThat(PlayerPreferences.reset(adaptation, player)).isFalse();
      assertThat(PlayerPreferences.setSkillEnabled(skill, player, CommonPreferences.Toggle.OFF)).isFalse();
      assertThat(PlayerPreferences.resetSkill(skill, player)).isFalse();
    }
    verify(player, never()).getData();
    verify(player, never()).requestSave();
    verify(adaptation, never()).onPlayerPreferencesChanged(player);
  }

  @Test
  void personalEffectsRemainEditableWhenSkillSettingsAreLocked() throws Exception {
    AdaptConfig locked = TomlCodec.fromToml("playerSettingsEnabled = false\n", AdaptConfig.class);
    AdaptConfig unlocked = new AdaptConfig();
    PlayerData data = new PlayerData();
    try (MockedStatic<AdaptConfig> configuration = mockStatic(AdaptConfig.class)) {
      configuration.when(AdaptConfig::get).thenReturn(unlocked);
      data.setEffectsEnabled(false);
      configuration.when(AdaptConfig::get).thenReturn(locked);
      assertThat(data.isEffectsEnabled()).isFalse();
      data.setEffectsEnabled(true);
      PlayerData restored = PlayerData.fromJson(data.toJson(false));
      assertThat(restored.isEffectsEnabled()).isTrue();
      assertThat(data.isEffectsEnabled()).isTrue();
    }
  }
}
