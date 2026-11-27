package art.arcane.adapt.engine.xp;

import art.arcane.adapt.SchedulerForwarding;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.content.SkillConfig;
import art.arcane.adapt.api.xp.XpAward;
import art.arcane.adapt.platform.value.Id;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.engine.notification.Notifier;
import art.arcane.adapt.engine.protection.RegionPolicy;
import art.arcane.adapt.engine.protection.RegionPolicyService;
import art.arcane.adapt.engine.protection.RegionPolicySource;
import art.arcane.adapt.engine.runtime.AdaptationGate;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.AdaptServer;
import art.arcane.adapt.engine.world.PlayerData;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.testkit.TestContent;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Mockito.lenient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class XpAwardPolicyTest extends AdaptTestBase {
  private final WorldRef world = mock(WorldRef.class);
  private PlayerRef player;
  private AdaptPlayer runtime;
  private Skill<?> skill;
  private PlayerSkillLine line;
  private PlayerData profile;
  private Loc location;
  private RegionPolicySource regionSource;
  private RegionPolicy policy;

  @BeforeEach
  void prepareRuntime() {
    UUID id = UUID.randomUUID();
    player = mock(PlayerRef.class);
    runtime = mock(AdaptPlayer.class);
    skill = mock(Skill.class);
    AdaptServer server = mock(AdaptServer.class);
    profile = new PlayerData();
    line = new PlayerSkillLine();
    line.setLine("adapt:mining");
    profile.getSkillLines().put("adapt:mining", line);
    location = new Loc(world, 100D, 64D, 100D);
    doReturn(server).when(core).getAdaptServer();
    when(player.uuid()).thenReturn(id);
    when(player.location()).thenAnswer(invocation -> location);
    when(player.isOnline()).thenReturn(true);
    when(server.getOnlineAdaptPlayer(id)).thenReturn(runtime);
    when(runtime.isRuntimeReady()).thenReturn(true);
    when(runtime.getPlayer()).thenReturn(player);
    when(runtime.getData()).thenReturn(profile);
    when(runtime.getSkillLine("adapt:mining")).thenReturn(line);
    when(runtime.getNot()).thenReturn(mock(Notifier.class));
    lenient().when(skill.getId()).thenReturn(Id.adapt("mining"));
    policy = new RegionPolicy(true, 0.5D, 0, Set.of());
    regionSource = mock(RegionPolicySource.class);
    when(regionSource.resolve(any(PlayerRef.class), any(Loc.class))).thenAnswer(invocation -> policy);
    RegionPolicyService.install(regionSource);
  }

  @AfterEach
  void removeRegionSource() {
    RegionPolicyService.clear();
  }

  @ParameterizedTest
  @CsvSource({"true, true", "true, false", "false, true", "false, false"})
  void visibleAndSilentFacadesApplyBothNoveltyAndRegionPolicy(boolean visible, boolean bukkitFacade) {
    try (MockedStatic<Tasks> scheduler = ownedScheduler();
         MockedStatic<AdaptationGate> gate = mockStatic(AdaptationGate.class);
         MockedStatic<XpNovelty> novelty = mockStatic(XpNovelty.class)) {
      novelty.when(() -> XpNovelty.noveltyMultiplier(any(PlayerRef.class), any(Loc.class), anyString())).thenReturn(0.25D);
      grant(visible, bukkitFacade, 100D, "mining:stone");
      XpEarnings.SourceSnapshot receipt = profile.getXpEarnings().snapshot().getFirst();
      assertThat(receipt.requested()).isEqualTo(100D);
      assertThat(receipt.contextual()).isEqualTo(12.5D);
      assertThat(receipt.credited()).isGreaterThan(0D).isLessThanOrEqualTo(receipt.contextual());
      assertThat(receipt.source()).isEqualTo("adapt:mining/mining:stone");
      verify(regionSource).resolve(player, location);
      novelty.verify(() -> XpNovelty.noveltyMultiplier(player, location, "mining:stone"));
      if (AdaptConfig.get().getXpIntegrity().isPooledPayoutEnabled()) {
        assertThat(line.getPooledNotifyXp()).isEqualTo(visible ? receipt.credited() : 0D);
      }
    }
  }

  @Test
  void explicitRewardLocationDrivesBothPolicyAndNovelty() {
    Loc source = new Loc(location.world(), 150D, 70D, 110D);
    XpAward award = XpAward.gameplay(80D, "furnace:smelt").at(source);
    try (MockedStatic<Tasks> scheduler = ownedScheduler();
         MockedStatic<AdaptationGate> gate = mockStatic(AdaptationGate.class);
         MockedStatic<XpNovelty> novelty = mockStatic(XpNovelty.class)) {
      novelty.when(() -> XpNovelty.noveltyMultiplier(player, source, "furnace:smelt")).thenReturn(0.4D);
      XP.award(runtime, skill, award);
      assertThat(profile.getXpEarnings().snapshot().getFirst().contextual()).isEqualTo(16D);
      verify(regionSource).resolve(player, source);
      novelty.verify(() -> XpNovelty.noveltyMultiplier(player, source, "furnace:smelt"));
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void regionDenialCreditsNothingAndDoesNotConsumeNoveltyOrAllowance(boolean visible) {
    policy = new RegionPolicy(false, 100D, 0, Set.of());
    try (MockedStatic<Tasks> scheduler = ownedScheduler();
         MockedStatic<AdaptationGate> gate = mockStatic(AdaptationGate.class);
         MockedStatic<XpNovelty> novelty = mockStatic(XpNovelty.class)) {
      grant(visible, true, 100D, "mining:stone");
      line.flushXpPool(null);
      assertThat(line.getXp()).isZero();
      assertThat(profile.getXpEarnings().budget("adapt:mining").absoluteUsed()).isZero();
      assertThat(profile.getXpEarnings().snapshot().getFirst().credited()).isZero();
      novelty.verifyNoInteractions();
    }
  }

  @Test
  void disallowedGameplayNeverReachesTheEarningBudget() {
    try (MockedStatic<Tasks> scheduler = ownedScheduler();
         MockedStatic<AdaptationGate> gate = mockStatic(AdaptationGate.class);
         MockedStatic<XpNovelty> novelty = mockStatic(XpNovelty.class)) {
      gate.when(() -> AdaptationGate.shouldSkipPlayer(player, skill, true)).thenReturn(true);
      grant(true, true, 100D, "mining:stone");
      grant(false, true, 100D, "mining:stone");
      assertThat(profile.getXpEarnings().snapshot()).isEmpty();
      assertThat(profile.getXpEarnings().budget("adapt:mining").absoluteUsed()).isZero();
      novelty.verifyNoInteractions();
    }
  }

  @Test
  void invalidScalarAwardsCannotConsumeAllowanceOrPolluteAccounting() {
    double[] amounts = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1D, 0D};
    try (MockedStatic<Tasks> scheduler = ownedScheduler();
         MockedStatic<AdaptationGate> gate = mockStatic(AdaptationGate.class);
         MockedStatic<XpNovelty> novelty = mockStatic(XpNovelty.class)) {
      for (double amount : amounts) {
        grant(true, true, amount, "mining:stone");
        grant(false, true, amount, "mining:stone");
        assertThatThrownBy(() -> XpAward.gameplay(amount, "mining:stone").at(location).silent())
            .isInstanceOf(IllegalArgumentException.class);
      }
      assertThat(profile.getXpEarnings().snapshot()).isEmpty();
      assertThat(profile.getXpEarnings().budget("adapt:mining").absoluteUsed()).isZero();
      novelty.verifyNoInteractions();
    }
  }

  @Test
  void overflowAfterRegionAdjustmentCannotProduceNonfiniteCredit() {
    policy = new RegionPolicy(true, 1000D, 0, Set.of());
    try (MockedStatic<Tasks> scheduler = ownedScheduler();
         MockedStatic<AdaptationGate> gate = mockStatic(AdaptationGate.class);
         MockedStatic<XpNovelty> novelty = mockStatic(XpNovelty.class)) {
      grant(false, true, Double.MAX_VALUE, "mining:stone");
      line.flushXpPool(null);
      assertThat(line.getXp()).isZero();
      assertThat(profile.getXpEarnings().budget("adapt:mining").absoluteUsed()).isZero();
      assertThat(profile.getXpEarnings().snapshot().getFirst().credited()).isZero();
      novelty.verifyNoInteractions();
    }
  }

  @Test
  void unlabelledRewardsUseTheirSkillAsTheActivityIdentity() {
    try (MockedStatic<Tasks> scheduler = ownedScheduler();
         MockedStatic<AdaptationGate> gate = mockStatic(AdaptationGate.class);
         MockedStatic<XpNovelty> novelty = mockStatic(XpNovelty.class)) {
      novelty.when(() -> XpNovelty.noveltyMultiplier(any(PlayerRef.class), any(Loc.class), anyString())).thenReturn(1D);
      grant(false, true, 10D, null);
      novelty.verify(() -> XpNovelty.noveltyMultiplier(player, location, "skill:adapt:mining:general"));
      assertThat(profile.getXpEarnings().snapshot().getFirst().source()).isEqualTo("adapt:mining/skill:adapt:mining:general");
    }
  }

  @ParameterizedTest
  @CsvSource({"true, true", "true, false", "false, true", "false, false"})
  void offThreadAwardsDeferLocationPolicyAndProfileAccessUntilOwnerCallback(boolean folia, boolean silentSkillFacade) {
    AtomicBoolean owned = new AtomicBoolean();
    AtomicReference<Runnable> pending = new AtomicReference<>();
    when(skill.isEnabled()).thenReturn(true);
    Skill<?> awarding = silentSkillFacade ? TestContent.skill(Mining::new) : skill;
    try (MockedStatic<Tasks> scheduler = SchedulerForwarding.mockTasks();
         MockedStatic<AdaptationGate> gate = mockStatic(AdaptationGate.class);
         MockedStatic<XpNovelty> novelty = mockStatic(XpNovelty.class)) {
      scheduler.when(Tasks::isFoliaThreading).thenReturn(folia);
      scheduler.when(() -> Tasks.isOwnedByCurrentRegion(player)).thenAnswer(invocation -> owned.get());
      scheduler.when(() -> Tasks.runEntity(eq(player), any(Runnable.class))).thenAnswer(invocation -> {
        pending.set(invocation.getArgument(1));
        return true;
      });
      novelty.when(() -> XpNovelty.noveltyMultiplier(player, location, "mining:stone")).thenReturn(0.25D);
      if (silentSkillFacade) {
        awarding.xpSilent(player, 40D, "mining:stone");
      } else {
        XP.xp(player, skill, 40D, "mining:stone");
      }
      assertThat(pending.get()).isNotNull();
      verify(player, never()).location();
      verify(runtime, never()).getData();
      verify(runtime, never()).getSkillLine(anyString());
      verifyNoInteractions(regionSource);
      gate.verifyNoInteractions();
      novelty.verifyNoInteractions();
      assertThat(profile.getXpEarnings().snapshot()).isEmpty();

      owned.set(true);
      pending.get().run();

      verify(player).location();
      verify(regionSource).resolve(player, location);
      gate.verify(() -> AdaptationGate.shouldSkipPlayer(player, awarding, true));
      novelty.verify(() -> XpNovelty.noveltyMultiplier(player, location, "mining:stone"));
      assertThat(profile.getXpEarnings().snapshot()).hasSize(1);
      assertThat(profile.getXpEarnings().snapshot().getFirst().credited()).isEqualTo(5D);
    }
  }

  @Test
  void rejectedOwnerDispatchDoesNotReachPolicyOrConsumeAllowance() {
    try (MockedStatic<Tasks> scheduler = SchedulerForwarding.mockTasks();
         MockedStatic<AdaptationGate> gate = mockStatic(AdaptationGate.class);
         MockedStatic<XpNovelty> novelty = mockStatic(XpNovelty.class)) {
      XP.xp(player, skill, 40D, "mining:stone");
      scheduler.verify(() -> Tasks.runEntity(eq(player), any(Runnable.class)));
      verify(player, never()).location();
      verify(runtime, never()).getData();
      verifyNoInteractions(regionSource);
      gate.verifyNoInteractions();
      novelty.verifyNoInteractions();
      assertThat(profile.getXpEarnings().snapshot()).isEmpty();
      assertThat(profile.getXpEarnings().budget("adapt:mining").absoluteUsed()).isZero();
    }
  }

  @Test
  void providerFailureAndQuarantineBlockAwardsUntilAHealthyProviderIsInstalled() {
    when(regionSource.resolve(player, location)).thenThrow(new IllegalStateException("Region lookup failed"));
    try (MockedStatic<Tasks> scheduler = ownedScheduler();
         MockedStatic<AdaptationGate> gate = mockStatic(AdaptationGate.class);
         MockedStatic<XpNovelty> novelty = mockStatic(XpNovelty.class)) {
      grant(true, true, 40D, "mining:stone");
      grant(false, true, 40D, "mining:stone");

      verify(regionSource, times(1)).resolve(player, location);
      novelty.verifyNoInteractions();
      assertThat(profile.getXpEarnings().budget("adapt:mining").absoluteUsed()).isZero();
      assertThat(profile.getXpEarnings().snapshot().getFirst().credited()).isZero();
      assertThat(line.getPooledXp()).isZero();
      assertThat(line.getXp()).isZero();

      RegionPolicySource recovered = mock(RegionPolicySource.class);
      when(recovered.resolve(player, location)).thenReturn(policy);
      RegionPolicyService.install(recovered);
      novelty.when(() -> XpNovelty.noveltyMultiplier(player, location, "mining:stone")).thenReturn(1D);
      grant(false, true, 40D, "mining:stone");

      verify(recovered).resolve(player, location);
      assertThat(profile.getXpEarnings().snapshot().getFirst().credited()).isEqualTo(20D);
      assertThat(profile.getXpEarnings().budget("adapt:mining").absoluteUsed()).isEqualTo(20D);
    }
  }

  private MockedStatic<Tasks> ownedScheduler() {
    MockedStatic<Tasks> scheduler = SchedulerForwarding.mockTasks();
    scheduler.when(() -> Tasks.isOwnedByCurrentRegion(player)).thenReturn(true);
    return scheduler;
  }

  private void grant(boolean visible, boolean bukkitFacade, double amount, String source) {
    if (bukkitFacade) {
      if (visible) {
        XP.xp(player, skill, amount, source);
      } else {
        XP.xpSilent(player, skill, amount, source);
      }
    } else if (visible) {
      XP.xp(runtime, skill, amount, source);
    } else {
      XP.xpSilent(runtime, skill, amount, source);
    }
  }

  /** A skill with the id the player's line uses, so the award runs through the public skill facade. */
  public static final class Mining extends SimpleSkill<SkillConfig> {
    public Mining() {
      super(Id.adapt("mining"));
      registerConfiguration(SkillConfig.class);
      setColor(Rgb.of(0x8A8A8A));
      setIcon(Id.minecraft("stone"));
    }
  }
}
