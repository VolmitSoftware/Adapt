package art.arcane.adapt.api.adaptation;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.TestCores;
import art.arcane.adapt.api.preference.PlayerPreferences;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.api.world.PlayerAdaptation;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.volmlib.util.math.M;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdaptationUsageXpTest {
  private final AtomicLong now = new AtomicLong(1_000_000L);
  private PlayerRef player;
  private Adaptation<Object> adaptation;
  private Skill<Object> skill;
  private PlayerData profile;

  @BeforeEach
  void prepareLearnedAdaptation() {
    UUID id = UUID.randomUUID();
    AdaptServer server = mock(AdaptServer.class);
    AdaptPlayer runtime = mock(AdaptPlayer.class);
    player = mock(PlayerRef.class);
    adaptation = mock(Adaptation.class, CALLS_REAL_METHODS);
    skill = mock(Skill.class);
    profile = new PlayerData();
    PlayerSkillLine line = new PlayerSkillLine();
    line.setLine("test-skill");
    PlayerAdaptation learned = new PlayerAdaptation();
    learned.setId("test-action");
    learned.setLevel(2);
    line.getAdaptations().put("test-action", learned);
    profile.getSkillLines().put("test-skill", line);
    AdaptCore core = TestCores.install();
    TestCores.setField(core, "adaptServer", server);
    when(server.getOnlineAdaptPlayer(id)).thenReturn(runtime);
    when(runtime.isRuntimeReady()).thenReturn(true);
    when(runtime.getPlayer()).thenReturn(player);
    when(runtime.getData()).thenAnswer(invocation -> profile);
    when(runtime.getSkillLine("test-skill")).thenAnswer(invocation -> profile.getSkillLines().get("test-skill"));
    when(player.uuid()).thenReturn(id);
    when(player.gameMode()).thenReturn(GameMode.SURVIVAL);
    doReturn(skill).when(adaptation).getSkill();
    doReturn("test-action").when(adaptation).getName();
    doReturn(5).when(adaptation).getMaxLevel();
    doReturn(true).when(adaptation).isEnabled();
    doReturn(true).when(adaptation).isPlayerEnabled(player);
    doReturn(true).when(adaptation).checkRegion(player);
    doReturn(true).when(adaptation).hasUsePermission(player, adaptation);
    doReturn(true).when(adaptation).canUse(player);
    when(skill.getName()).thenReturn("test-skill");
    when(skill.isEnabled()).thenReturn(true);
  }

  @AfterEach
  void clearCore() {
    TestCores.clear();
  }

  @Test
  void repeatedEligibilityChecksNeverGrantOrConsumeUsageXp() {
    try (MockedStatic<Tasks> scheduler = ownedScheduler();
         MockedStatic<M> clock = clock();
         MockedStatic<PlayerPreferences> preferences = preferences()) {
      for (int index = 0; index < 100; index++) {
        assertThat(AdaptationRuntimeGuards.getActiveLevel(adaptation, player)).isEqualTo(2);
        now.addAndGet(50L);
      }
      assertThat(learned().getStorage()).doesNotContainKey("usage-xp-at");
      verify(skill, never()).xpSilent(any(PlayerRef.class), anyDouble(), anyString());
      verify(skill, never()).xp(any(PlayerRef.class), anyDouble(), anyString());
      assertThat(profile.getXpEarnings().snapshot()).isEmpty();
    }
  }

  @Test
  void explicitSuccessfulUsePaysOncePerPersistedCooldown() {
    double baseline = baseline();
    try (MockedStatic<Tasks> scheduler = ownedScheduler();
         MockedStatic<M> clock = clock()) {
      AdaptationRuntimeGuards.awardUsageBaselineXp(adaptation, player, 2);
      AdaptationRuntimeGuards.awardUsageBaselineXp(adaptation, player, 2);
      verify(skill).xpSilent(player, baseline, "adaptation:test-action:baseline-use");
      profile = PlayerData.fromJson(profile.toJson(true));
      AdaptationRuntimeGuards.awardUsageBaselineXp(adaptation, player, 2);
      verify(skill).xpSilent(player, baseline, "adaptation:test-action:baseline-use");
      now.addAndGet(Math.max(250L, AdaptConfig.get().getAdaptationXp().getUsageBaselineCooldownMillis()));
      AdaptationRuntimeGuards.awardUsageBaselineXp(adaptation, player, 2);
      verify(skill, times(2)).xpSilent(player, baseline, "adaptation:test-action:baseline-use");
    }
  }

  @Test
  void explicitXpAndBaselineHookShareOneCooldownAcrossProfileReload() {
    double baseline = baseline();
    try (MockedStatic<Tasks> scheduler = ownedScheduler();
         MockedStatic<M> clock = clock()) {
      adaptation.xp(player, 10D, "harvest");
      verify(skill).xp(player, 10D + baseline, "adaptation:test-action:harvest");
      profile = PlayerData.fromJson(profile.toJson(true));
      adaptation.xpSilent(player, 10D, "harvest");
      verify(skill).xpSilent(player, 10D, "adaptation:test-action:harvest");
      AdaptationRuntimeGuards.awardUsageBaselineXp(adaptation, player, 2);
      verify(skill, never()).xpSilent(eq(player), anyDouble(), eq("adaptation:test-action:baseline-use"));
    }
  }

  @Test
  void rejectedOrUnlearnedUseDoesNotStartTheBaselineCooldown() {
    try (MockedStatic<Tasks> scheduler = ownedScheduler();
         MockedStatic<M> clock = clock()) {
      adaptation.xp(player, 0D, "harvest");
      adaptation.xpSilent(player, Double.NaN, "harvest");
      assertThat(learned().getStorage()).doesNotContainKey("usage-xp-at");
      assertThat(AdaptationRuntimeGuards.claimUsageBaselineXp(adaptation, player, 0)).isZero();
      profile.getSkillLines().get("test-skill").getAdaptations().clear();
      assertThat(AdaptationRuntimeGuards.claimUsageBaselineXp(adaptation, player, 2)).isZero();
      verify(skill, never()).xpSilent(any(PlayerRef.class), anyDouble(), anyString());
      verify(skill, never()).xp(any(PlayerRef.class), anyDouble(), anyString());
    }
  }

  private PlayerAdaptation learned() {
    return profile.getSkillLines().get("test-skill").getAdaptation("test-action");
  }

  private double baseline() {
    AdaptConfig.AdaptationXp config = AdaptConfig.get().getAdaptationXp();
    return config.getUsageBaselineXp() + config.getUsageBaselineXpPerLevel();
  }

  private MockedStatic<M> clock() {
    MockedStatic<M> clock = mockStatic(M.class);
    clock.when(M::ms).thenAnswer(invocation -> now.get());
    return clock;
  }

  private MockedStatic<Tasks> ownedScheduler() {
    MockedStatic<Tasks> scheduler = mockStatic(Tasks.class);
    scheduler.when(() -> Tasks.isOwnedByCurrentRegion((EntityRef) player)).thenReturn(true);
    return scheduler;
  }

  private MockedStatic<PlayerPreferences> preferences() {
    MockedStatic<PlayerPreferences> preferences = mockStatic(PlayerPreferences.class);
    preferences.when(() -> PlayerPreferences.isEnabled(eq(adaptation), any(PlayerData.class), anyInt())).thenReturn(true);
    return preferences;
  }
}
