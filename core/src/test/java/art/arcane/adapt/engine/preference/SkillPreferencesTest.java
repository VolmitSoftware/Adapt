package art.arcane.adapt.engine.preference;

import art.arcane.adapt.SchedulerForwarding;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.content.SkillConfig;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PreferencePolicy;
import art.arcane.adapt.engine.player.PlayerApi;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.platform.value.Id;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.TestCores;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.AdaptServer;
import art.arcane.adapt.engine.world.PlayerData;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.volmlib.util.collection.KList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.lenient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillPreferencesTest {
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
    lenient().when(skill.getId()).thenReturn(Id.adapt("agility"));
    when(skill.isEnabled()).thenReturn(true);
    AdaptServer server = mock(AdaptServer.class);
    registry = mock(SkillRegistry.class);
    AdaptCore core = TestCores.install();
    TestCores.setField(core, "adaptServer", server);
    when(server.getSkillRegistry()).thenReturn(registry);
    doReturn(skill).when(registry).getSkill("agility");
    lenient().doReturn(skill).when(registry).getSkill(Id.parseAdapt("agility").toString());
    lenient().doReturn(skill).when(registry).getSkill(Id.parseAdapt("agility"));

    when(skill.getAdaptations()).thenReturn(new KList<>(List.of(adaptation)));
    lenient().when(adaptation.getId()).thenReturn(Id.adapt("agility/air_dash"));
    when(adaptation.getPlayerPreferences()).thenReturn(List.of(CommonPreferences.ENABLED));
    doReturn(skill).when(adaptation).getSkill();
    doReturn(skillConfig).when(skill).getConfig();
    doReturn(adaptationConfig).when(adaptation).getConfig();
    PlayerPreferences.validate(skill, skillConfig);
    PlayerPreferences.validate(adaptation, adaptationConfig);
  }

  @AfterEach
  void clearCore() {
    TestCores.clear();
  }

  @Test
  void skillOffWinsWithoutOverwritingAdaptationChoiceOrAnotherPlayer() {
    data.getPreferences().set(adaptation.getId().toString(), "enabled", "ON");
    data.getPreferences().setSkill(SkillHost.lineKey(skill), "enabled", "OFF");

    assertThat(PlayerPreferences.isEnabled(adaptation, data, 3)).isFalse();
    assertThat(PlayerPreferences.isEnabled(adaptation, new PlayerData(), 3)).isTrue();
    assertThat(data.getPreferences().get(adaptation.getId().toString(), "enabled")).isEqualTo("ON");
    data.getPreferences().resetSkill(SkillHost.lineKey(skill));
    assertThat(PlayerPreferences.isEnabled(adaptation, data, 3)).isTrue();
  }

  @Test
  void serverLocksOverrideDormantChoicesAtBothScopes() {
    data.getPreferences().setSkill(SkillHost.lineKey(skill), "enabled", "OFF");
    PreferencePolicy skillPolicy = skillConfig.playerPreferences.get("enabled");
    skillPolicy.playerEditable = false;
    assertThat(PlayerPreferences.isEnabled(adaptation, data, 3)).isTrue();
    adaptationConfig.playerPreferences.get("enabled").defaultValue = "OFF";
    adaptationConfig.playerPreferences.get("enabled").playerEditable = false;
    assertThat(PlayerPreferences.isEnabled(adaptation, data, 3)).isFalse();
    assertThat(data.getPreferences().getSkill(SkillHost.lineKey(skill), "enabled")).isEqualTo("OFF");
  }

  @Test
  void skillAndAdaptationPreferencesSurviveSerializationIndependently() {
    data.getPreferences().set(adaptation.getId().toString(), "enabled", "OFF");
    data.getPreferences().setSkill(SkillHost.lineKey(skill), "enabled", "OFF");
    PlayerData restored = PlayerData.fromJson(data.toJson(false));
    assertThat(restored.getPreferences().getSkill(SkillHost.lineKey(skill), "enabled")).isEqualTo("OFF");
    assertThat(restored.getPreferences().get(adaptation.getId().toString(), "enabled")).isEqualTo("OFF");
    restored.getPreferences().reset(adaptation.getId().toString());
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
    PlayerRef owner = mock(PlayerRef.class);
    AdaptPlayer player = mock(AdaptPlayer.class);
    when(owner.uuid()).thenReturn(UUID.randomUUID());
    when(owner.isOnline()).thenReturn(true);
    when(player.getPlayer()).thenReturn(owner);
    when(player.getData()).thenReturn(data);
    when(player.isRuntimeReady()).thenReturn(true);
    when(skill.hasUsePermission(owner)).thenReturn(true);
    try (MockedStatic<Tasks> scheduler = SchedulerForwarding.mockTasks()) {
      scheduler.when(() -> Tasks.isOwnedByCurrentRegion(owner)).thenReturn(true);
      assertThat(PlayerPreferences.setSkillEnabled(skill, player, CommonPreferences.Toggle.OFF)).isTrue();
    }
    verify(player).requestSave();
    verify(adaptation).onPlayerPreferencesChanged(PlayerApi.profile(player));
    assertThat(PlayerPreferences.isEnabled(adaptation, data, 3)).isFalse();
    assertThat(skillConfig.playerPreferences.get("enabled").defaultValue).isEqualTo("ON");
  }

  @Test
  void retiredSkillsAndRemovedAdaptationsCannotWriteOrResetPreferences() {
    PlayerRef owner = mock(PlayerRef.class);
    AdaptPlayer player = mock(AdaptPlayer.class);
    when(owner.uuid()).thenReturn(UUID.randomUUID());
    when(owner.isOnline()).thenReturn(true);
    when(player.getPlayer()).thenReturn(owner);
    when(player.getData()).thenReturn(data);
    when(player.isRuntimeReady()).thenReturn(true);
    when(skill.hasUsePermission(owner)).thenReturn(true);
    when(adaptation.isEnabled()).thenReturn(true);
    when(adaptation.hasUsePermission(owner)).thenReturn(true);
    lenient().when(adaptation.getLevel(owner)).thenReturn(3);
    data.getPreferences().set(adaptation.getId().toString(), "enabled", "ON");
    data.getPreferences().setSkill(SkillHost.lineKey(skill), "enabled", "ON");
    try (MockedStatic<Tasks> scheduler = SchedulerForwarding.mockTasks()) {
      scheduler.when(() -> Tasks.isOwnedByCurrentRegion(owner)).thenReturn(true);
      Skill<?> retired = mock(Skill.class);
      doReturn(retired).when(registry).getSkill("agility");
      doReturn(retired).when(registry).getSkill(Id.parseAdapt("agility").toString());
      doReturn(retired).when(registry).getSkill(Id.parseAdapt("agility"));
      assertThat(PlayerPreferences.setSkillEnabled(skill, player, CommonPreferences.Toggle.OFF)).isFalse();
      assertThat(PlayerPreferences.resetSkill(skill, player)).isFalse();
      assertThat(PlayerPreferences.set(adaptation, player, CommonPreferences.ENABLED, CommonPreferences.Toggle.OFF)).isFalse();
      assertThat(PlayerPreferences.reset(adaptation, player)).isFalse();
      doReturn(skill).when(registry).getSkill("agility");
      lenient().doReturn(skill).when(registry).getSkill(Id.parseAdapt("agility").toString());
      lenient().doReturn(skill).when(registry).getSkill(Id.parseAdapt("agility"));
      when(skill.getAdaptations()).thenReturn(new KList<>());
      assertThat(PlayerPreferences.set(adaptation, player, CommonPreferences.ENABLED, CommonPreferences.Toggle.OFF)).isFalse();
      assertThat(PlayerPreferences.reset(adaptation, player)).isFalse();
    }
    assertThat(data.getPreferences().getSkill(SkillHost.lineKey(skill), "enabled")).isEqualTo("ON");
    assertThat(data.getPreferences().get(adaptation.getId().toString(), "enabled")).isEqualTo("ON");
    verify(player, never()).requestSave();
    verify(adaptation, never()).onPlayerPreferencesChanged(PlayerApi.profile(player));
  }

  @Test
  void changingSkillFromTheWrongRegionDoesNothing() {
    PlayerRef owner = mock(PlayerRef.class);
    AdaptPlayer player = mock(AdaptPlayer.class);
    when(owner.isOnline()).thenReturn(true);
    when(player.getPlayer()).thenReturn(owner);
    when(player.isRuntimeReady()).thenReturn(true);
    try (MockedStatic<Tasks> scheduler = SchedulerForwarding.mockTasks()) {
      assertThat(PlayerPreferences.setSkillEnabled(skill, player, CommonPreferences.Toggle.OFF)).isFalse();
    }
    verify(player, never()).requestSave();
    verify(adaptation, never()).onPlayerPreferencesChanged(PlayerApi.profile(player));
  }
}
