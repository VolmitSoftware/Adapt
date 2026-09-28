package art.arcane.adapt.api.preference;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillConfig;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.collection.KList;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillPreferencesTest extends AdaptTestBase {
  private SkillRegistry registry;
  private Skill<?> skill;
  private Adaptation<?> adaptation;
  private SkillConfig skillConfig;
  private AdaptationConfig adaptationConfig;
  private PlayerData data;

  @BeforeEach
  void prepare() {
    skill = mock(Skill.class);
    adaptation = mock(Adaptation.class);
    skillConfig = new SkillConfig();
    adaptationConfig = new AdaptationConfig();
    data = new PlayerData();
    when(skill.getName()).thenReturn("agility");
    when(skill.isEnabled()).thenReturn(true);
    AdaptServer server = mock(AdaptServer.class);
    registry = mock(SkillRegistry.class);
    when(plugin.getAdaptServer()).thenReturn(server);
    when(server.getSkillRegistry()).thenReturn(registry);
    doReturn(skill).when(registry).getSkill("agility");

    when(skill.getAdaptations()).thenReturn(new KList<>(List.of(adaptation)));
    when(adaptation.getName()).thenReturn("agility-air-dash");
    when(adaptation.getPlayerPreferences()).thenReturn(List.of(CommonPreferences.ENABLED));
    doReturn(skill).when(adaptation).getSkill();
    doReturn(skillConfig).when(skill).getConfig();
    doReturn(adaptationConfig).when(adaptation).getConfig();
    PlayerPreferences.validate(skill, skillConfig);
    PlayerPreferences.validate(adaptation, adaptationConfig);
  }

  @Test
  void skillOffWinsWithoutOverwritingAdaptationChoiceOrAnotherPlayer() {
    data.getPreferences().set(adaptation.getName(), "enabled", "ON");
    data.getPreferences().setSkill(skill.getName(), "enabled", "OFF");

    assertThat(PlayerPreferences.isEnabled(adaptation, data, 3)).isFalse();
    assertThat(PlayerPreferences.isEnabled(adaptation, new PlayerData(), 3)).isTrue();
    assertThat(data.getPreferences().get(adaptation.getName(), "enabled")).isEqualTo("ON");
    data.getPreferences().resetSkill(skill.getName());
    assertThat(PlayerPreferences.isEnabled(adaptation, data, 3)).isTrue();
  }

  @Test
  void serverLocksOverrideDormantChoicesAtBothScopes() {
    data.getPreferences().setSkill(skill.getName(), "enabled", "OFF");
    PreferencePolicy skillPolicy = skillConfig.playerPreferences.get("enabled");
    skillPolicy.playerEditable = false;
    assertThat(PlayerPreferences.isEnabled(adaptation, data, 3)).isTrue();
    adaptationConfig.playerPreferences.get("enabled").defaultValue = "OFF";
    adaptationConfig.playerPreferences.get("enabled").playerEditable = false;
    assertThat(PlayerPreferences.isEnabled(adaptation, data, 3)).isFalse();
    assertThat(data.getPreferences().getSkill(skill.getName(), "enabled")).isEqualTo("OFF");
  }

  @Test
  void skillAndAdaptationPreferencesSurviveSerializationIndependently() {
    data.getPreferences().set(adaptation.getName(), "enabled", "OFF");
    data.getPreferences().setSkill(skill.getName(), "enabled", "OFF");
    PlayerData restored = PlayerData.fromJson(data.toJson(false));
    assertThat(restored.getPreferences().getSkill(skill.getName(), "enabled")).isEqualTo("OFF");
    assertThat(restored.getPreferences().get(adaptation.getName(), "enabled")).isEqualTo("OFF");
    restored.getPreferences().reset(adaptation.getName());
    assertThat(PlayerPreferences.isEnabled(adaptation, restored, 3)).isFalse();
    assertThat(PlayerPreferences.resolve(skill, PlayerData.fromJson("{}"), CommonPreferences.SKILL_ENABLED))
        .isEqualTo(CommonPreferences.Toggle.ON);
  }

  @Test
  void skillPolicyRejectsAnUnavailableDefault() {
    skillConfig.playerPreferences.get("enabled").allowedValues = List.of("OFF");
    assertThatThrownBy(() -> PlayerPreferences.validate(skill, skillConfig))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("agility.playerPreferences.enabled.defaultValue");
  }

  @Test
  void changingSkillReconcilesChildrenAndSavesOnlyTheOwner() {
    Player owner = mock(Player.class);
    AdaptPlayer player = mock(AdaptPlayer.class);
    when(owner.getUniqueId()).thenReturn(UUID.randomUUID());
    when(owner.isOnline()).thenReturn(true);
    when(player.getPlayer()).thenReturn(owner);
    when(player.getData()).thenReturn(data);
    when(player.isRuntimeReady()).thenReturn(true);
    when(skill.hasUsePermission(owner, skill)).thenReturn(true);
    try (MockedStatic<J> scheduler = mockStatic(J.class)) {
      scheduler.when(() -> J.isOwnedByCurrentRegion(owner)).thenReturn(true);
      assertThat(PlayerPreferences.setSkillEnabled(skill, player, CommonPreferences.Toggle.OFF)).isTrue();
    }
    verify(player).requestSave();
    verify(adaptation).onPlayerPreferencesChanged(player);
    assertThat(PlayerPreferences.isEnabled(adaptation, data, 3)).isFalse();
    assertThat(skillConfig.playerPreferences.get("enabled").defaultValue).isEqualTo("ON");
  }

  @Test
  void retiredSkillsAndRemovedAdaptationsCannotWriteOrResetPreferences() {
    Player owner = mock(Player.class);
    AdaptPlayer player = mock(AdaptPlayer.class);
    when(owner.getUniqueId()).thenReturn(UUID.randomUUID());
    when(owner.isOnline()).thenReturn(true);
    when(player.getPlayer()).thenReturn(owner);
    when(player.getData()).thenReturn(data);
    when(player.isRuntimeReady()).thenReturn(true);
    when(skill.hasUsePermission(owner, skill)).thenReturn(true);
    when(adaptation.isEnabled()).thenReturn(true);
    when(adaptation.hasUsePermission(owner, adaptation)).thenReturn(true);
    when(adaptation.getLevel(player)).thenReturn(3);
    data.getPreferences().set(adaptation.getName(), "enabled", "ON");
    data.getPreferences().setSkill(skill.getName(), "enabled", "ON");
    try (MockedStatic<J> scheduler = mockStatic(J.class)) {
      scheduler.when(() -> J.isOwnedByCurrentRegion(owner)).thenReturn(true);
      doReturn(mock(Skill.class)).when(registry).getSkill("agility");
      assertThat(PlayerPreferences.setSkillEnabled(skill, player, CommonPreferences.Toggle.OFF)).isFalse();
      assertThat(PlayerPreferences.resetSkill(skill, player)).isFalse();
      assertThat(PlayerPreferences.set(adaptation, player, CommonPreferences.ENABLED, CommonPreferences.Toggle.OFF)).isFalse();
      assertThat(PlayerPreferences.reset(adaptation, player)).isFalse();
      doReturn(skill).when(registry).getSkill("agility");
      when(skill.getAdaptations()).thenReturn(new KList<>());
      assertThat(PlayerPreferences.set(adaptation, player, CommonPreferences.ENABLED, CommonPreferences.Toggle.OFF)).isFalse();
      assertThat(PlayerPreferences.reset(adaptation, player)).isFalse();
    }
    assertThat(data.getPreferences().getSkill(skill.getName(), "enabled")).isEqualTo("ON");
    assertThat(data.getPreferences().get(adaptation.getName(), "enabled")).isEqualTo("ON");
    verify(player, never()).requestSave();
    verify(adaptation, never()).onPlayerPreferencesChanged(player);
  }

  @Test
  void changingSkillFromTheWrongRegionDoesNothing() {
    Player owner = mock(Player.class);
    AdaptPlayer player = mock(AdaptPlayer.class);
    when(owner.isOnline()).thenReturn(true);
    when(player.getPlayer()).thenReturn(owner);
    when(player.isRuntimeReady()).thenReturn(true);
    try (MockedStatic<J> scheduler = mockStatic(J.class)) {
      assertThat(PlayerPreferences.setSkillEnabled(skill, player, CommonPreferences.Toggle.OFF)).isFalse();
    }
    verify(player, never()).requestSave();
    verify(adaptation, never()).onPlayerPreferencesChanged(player);
  }
}
