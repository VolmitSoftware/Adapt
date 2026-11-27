package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.testkit.RuntimeSpy;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.engine.adaptation.AdaptationHost;
import art.arcane.adapt.engine.component.ComponentHost;
import art.arcane.adapt.content.golem.GolemBody;
import art.arcane.adapt.content.golem.GolemCapacity;
import art.arcane.adapt.content.golem.GolemHandle;
import art.arcane.adapt.content.golem.GolemStat;
import art.arcane.adapt.content.golem.GolemStatDelta;
import art.arcane.adapt.content.golem.GolemStatSource;
import art.arcane.adapt.content.golem.GolemStats;
import art.arcane.adapt.content.golem.GolemWorkSpeed;
import art.arcane.adapt.content.golem.GolemancyRuntime;
import art.arcane.adapt.engine.preference.PlayerPreferenceData;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.AdaptServer;
import art.arcane.adapt.engine.world.PlayerData;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.world.LightningStrikeEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.testkit.PlacedAdaptations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GolemCopperFrameTest extends AdaptTestBase {
  private GolemCopperFrame adaptation;
  private MockedStatic<GolemancyRuntime> runtime;
  private GolemRegistrations<GolemStatSource> statSources;
  private GolemRegistrations<GolemCapacity.Source> carrySources;
  private GolemRegistrations<GolemWorkSpeed.Source> workSpeedSources;
  private WorldRef world;
  private final List<EntityRef> nearby = new ArrayList<>();

  @BeforeEach
  void construct() {
    runtime = mockStatic(GolemancyRuntime.class);
    runtime.when(() -> GolemancyRuntime.handle(any())).thenReturn(Optional.empty());
    statSources = GolemRegistrations.stats();
    carrySources = GolemRegistrations.carry();
    workSpeedSources = GolemRegistrations.workSpeed();
    adaptation = PlacedAdaptations.on(Id.adapt("golemancy"), GolemCopperFrame::new);
    world = mock(WorldRef.class);
    lenient().when(world.nearbyEntities(any(Vec3.class), anyDouble(), anyDouble(), anyDouble())).thenAnswer(invocation -> List.copyOf(nearby));
  }

  @AfterEach
  void close() {
    workSpeedSources.close();
    carrySources.close();
    statSources.close();
    runtime.close();
  }

  @Test
  void anOnlineOwnersLevelIsReadFromTheOwnerAndRecordedOnTheGolem() {
    AdaptServer server = mock(AdaptServer.class);
    AdaptPlayer online = mock(AdaptPlayer.class);
    UUID owner = UUID.randomUUID();
    when(core.getAdaptServer()).thenReturn(server);
    when(server.getOnlineAdaptPlayer(owner)).thenReturn(online);
    GolemHandle handle = mock(GolemHandle.class);
    when(handle.owner()).thenReturn(owner);
    GolemCopperFrame learned = spy(adaptation);
    doReturn(4).when(RuntimeSpy.install(adaptation, learned)).level(any(PlayerProfile.class));

    assertThat(learned.ownerLevel(handle)).isEqualTo(4);
    verify(handle).recordOwnerLevel(adaptation.getId().toString(), 4);
  }

  @Test
  void defaultsMatchThePlanTables() {
    GolemCopperFrame.Config config = new GolemCopperFrame.Config();

    assertThat(config.healthBonusByLevel).containsExactly(4, 8, 12, 16, 20);
    assertThat(config.carryByLevel).containsExactly(16, 32, 48, 64, 128);
    assertThat(config.speedBonusByLevel).containsExactly(0D, 0.05D, 0.10D, 0.15D, 0.20D);
    assertThat(config.patinaArmorPerStage).isEqualTo(2);
    assertThat(config.patinaSpeedPenaltyPerStage).isEqualTo(0.05D);
    assertThat(config.patinaArmorDoubleLevel).isEqualTo(3);
    assertThat(config.patinaPenaltyRemovedLevel).isEqualTo(5);
    assertThat(config.overdriveLevel).isEqualTo(4);
    assertThat(config.overdriveRadius).isEqualTo(16D);
    assertThat(config.overdriveSeconds).isEqualTo(60);
    assertThat(config.overdriveCooldownSeconds).isEqualTo(300);
    assertThat(config.overdriveWorkIntervalMultiplier).isEqualTo(0.5D);
    assertThat(config.requires).containsExactly("adapt:golem/animus_binding@1");
    assertThat(config.maxLevel).isEqualTo(5);
  }

  @Test
  void loadingClampsTheOverdriveRadiusToSixtyFourBlocks() {
    assertThat(ComponentHost.of(adaptation).reloadConfigSnapshot("overdriveRadius = 500.0\n", dataFolder.toPath().resolve("far.toml").toFile(), false)).isTrue();
    assertThat(adaptation.getConfig().overdriveRadius).isEqualTo(64D);

    assertThat(ComponentHost.of(adaptation).reloadConfigSnapshot("overdriveRadius = -3.0\n", dataFolder.toPath().resolve("negative.toml").toFile(), false)).isTrue();
    assertThat(adaptation.getConfig().overdriveRadius).isZero();

    assertThat(ComponentHost.of(adaptation).reloadConfigSnapshot("overdriveRadius = 24.0\n", dataFolder.toPath().resolve("near.toml").toFile(), false)).isTrue();
    assertThat(adaptation.getConfig().overdriveRadius).isEqualTo(24D);
  }

  @Test
  void overdriveUsesTheConfiguredWorkIntervalMultiplier() {
    Loc strike = new Loc(world, 0.5D, 64D, 0.5D);
    MobRef golem = copperGolem(new Loc(world, 2.5D, 64D, 0.5D));
    GolemHandle handle = bound(golem, false);
    assertThat(ComponentHost.of(adaptation).reloadConfigSnapshot("overdriveWorkIntervalMultiplier = 0.25\n", dataFolder.toPath().resolve("multiplier.toml").toFile(), false))
        .isTrue();
    adaptation.onRuntimeActivated();
    try {
      learned(4).on(lightning(strike));

      assertThat(workSpeedSource().intervalMultiplier(handle)).isEqualTo(0.25D);
    } finally {
      unregisterSources();
    }
  }

  @Test
  void configurationLoadsWithFiveLevelsAndRuntimeOnlySources() {
    assertThat(ComponentHost.of(adaptation).reloadConfigFromDisk(false)).isTrue();
    assertThat(adaptation.getId().toString()).isEqualTo("adapt:golem/copper_frame");
    assertThat(adaptation.getMaxLevel()).isEqualTo(5);
    double previous = 0D;
    for (int level = 1; level <= adaptation.getMaxLevel(); level++) {
      assertThat(adaptation.getCostFor(level)).as("cost at level %s", level).isPositive();
      assertThat(adaptation.getLevelPercent(level)).isBetween(previous, 1D);
      previous = adaptation.getLevelPercent(level);
    }
    assertThat(adaptation.isRuntimeRegistered()).isFalse();
    assertThat(statSources.registered()).isEmpty();
  }

  @Test
  void everyLevelShowsItsHealthCarrySpeedPatinaAndUnlocks() {
    assertThat(lore(1)).containsExactly("+ 4 Bonus Health for Copper Golems", "+ 16 Item Carry Capacity", "+ Never Turns into a Statue",
        "+ 2 Armor per Patina Stage", "- 5% Speed Lost per Patina Stage");
    assertThat(lore(2)).containsExactly("+ 8 Bonus Health for Copper Golems", "+ 32 Item Carry Capacity", "+ 5% Movement Speed",
        "+ Never Turns into a Statue", "+ 2 Armor per Patina Stage", "- 5% Speed Lost per Patina Stage");
    assertThat(lore(3)).containsExactly("+ 12 Bonus Health for Copper Golems", "+ 48 Item Carry Capacity", "+ 10% Movement Speed",
        "+ Never Turns into a Statue", "+ 4 Armor per Patina Stage", "- 5% Speed Lost per Patina Stage");
    assertThat(lore(4)).containsExactly("+ 16 Bonus Health for Copper Golems", "+ 64 Item Carry Capacity", "+ 15% Movement Speed",
        "+ Never Turns into a Statue", "+ 4 Armor per Patina Stage", "- 5% Speed Lost per Patina Stage",
        "+ 60 Seconds of Speed II from Nearby Lightning");
    assertThat(lore(5)).containsExactly("+ 20 Bonus Health for Copper Golems", "+ 128 Item Carry Capacity", "+ 20% Movement Speed",
        "+ Never Turns into a Statue", "+ 4 Armor per Patina Stage", "+ 60 Seconds of Speed II from Nearby Lightning");
  }

  @Test
  void theFrameAndPatinaSourcesStrengthenCopperGolemsOnly() {
    GolemCopperFrame.Config config = new GolemCopperFrame.Config();

    assertThat(GolemCopperFrame.STAT_SOURCE).isEqualTo("copper_frame");
    assertThat(GolemCopperFrame.PATINA_SOURCE).isEqualTo("patina");
    assertThat(GolemCopperFrame.frameStats(config, GolemBody.COPPER, 4))
        .containsExactly(GolemStatDelta.add(GolemStat.MAX_HEALTH, 16D), GolemStatDelta.multiplyBase(GolemStat.MOVEMENT_SPEED, 0.15D));
    assertThat(GolemCopperFrame.frameStats(config, GolemBody.COPPER, 0)).isEmpty();
    assertThat(GolemCopperFrame.frameStats(config, GolemBody.IRON, 5)).isEmpty();
    assertThat(GolemCopperFrame.patinaStats(config, GolemBody.COPPER, 2, 1))
        .containsExactly(GolemStatDelta.add(GolemStat.ARMOR, 4D), GolemStatDelta.multiplyBase(GolemStat.MOVEMENT_SPEED, -0.10D));
    assertThat(GolemCopperFrame.patinaStats(config, GolemBody.COPPER, 2, 5)).containsExactly(GolemStatDelta.add(GolemStat.ARMOR, 8D));
    assertThat(GolemCopperFrame.patinaStats(config, GolemBody.FROST, 3, 5)).isEmpty();
  }

  @Test
  void copperGolemsCarryTheirFrameLevelsCapacity() {
    GolemCopperFrame.Config config = new GolemCopperFrame.Config();

    assertThat(GolemCopperFrame.carry(config, GolemBody.COPPER, 3)).isEqualTo(GolemCapacity.Contribution.atLeast(48));
    assertThat(GolemCopperFrame.carry(config, GolemBody.COPPER, 5)).isEqualTo(GolemCapacity.Contribution.atLeast(128));
    assertThat(GolemCopperFrame.carry(config, GolemBody.COPPER, 0)).isEqualTo(GolemCapacity.Contribution.NONE);
    assertThat(GolemCopperFrame.carry(config, GolemBody.IRON, 5)).isEqualTo(GolemCapacity.Contribution.NONE);
  }

  @Test
  void theRegisteredSourcesSkipTheOwnerLookupForOtherBodies() {
    GolemHandle iron = mock(GolemHandle.class);
    when(iron.body()).thenReturn(GolemBody.IRON);
    when(core.getAdaptServer()).thenThrow(new AssertionError("owner level looked up for an iron golem"));
    adaptation.onRuntimeActivated();
    try {
      assertThat(statSource(GolemCopperFrame.STAT_SOURCE).deltas(iron)).isEmpty();
      assertThat(statSource(GolemCopperFrame.PATINA_SOURCE).deltas(iron)).isEmpty();
      assertThat(carrySource().carry(iron)).isEqualTo(GolemCapacity.Contribution.NONE);
      assertThat(workSpeedSource().intervalMultiplier(iron)).isEqualTo(1D);
    } finally {
      unregisterSources();
    }
  }

  @Test
  void lightningNearABoundCopperGolemGrantsOverdriveOncePerCooldown() {
    Loc strike = new Loc(world, 0.5D, 64D, 0.5D);
    MobRef golem = copperGolem(new Loc(world, 8.5D, 64D, 8.5D));
    GolemHandle handle = bound(golem, false);
    adaptation.onRuntimeActivated();
    try {
      GolemCopperFrame learned = learned(4);
      GolemWorkSpeed.Source workSpeed = workSpeedSource();
      assertThat(workSpeed.intervalMultiplier(handle)).isEqualTo(1D);

      learned.on(lightning(strike));
      learned.on(lightning(strike));

      verify(learned, times(1)).boost(golem, 1200);
      assertThat(workSpeed.intervalMultiplier(handle)).isEqualTo(0.5D);
      assertThat(learned.hasTickDemand()).isTrue();
    } finally {
      unregisterSources();
    }
  }

  @Test
  void overdriveSkipsFarDormantUnlearnedAndUnboundCopperGolems() {
    Loc strike = new Loc(world, 0.5D, 64D, 0.5D);
    MobRef far = copperGolem(new Loc(world, 16.5D, 64D, 16.5D));
    bound(far, false);
    MobRef dormant = copperGolem(new Loc(world, 2.5D, 64D, 0.5D));
    bound(dormant, true);
    MobRef wild = copperGolem(new Loc(world, 1.5D, 64D, 0.5D));
    GolemCopperFrame learned = learned(5);
    GolemCopperFrame lowLevel = learned(3);
    MobRef close = copperGolem(new Loc(world, 0.5D, 64D, 3.5D));
    bound(close, false);

    lowLevel.on(lightning(strike));
    learned.on(lightning(strike));

    verify(learned, never()).boost(far, 1200);
    verify(learned, never()).boost(dormant, 1200);
    verify(learned, never()).boost(wild, 1200);
    verify(learned).boost(close, 1200);
    verify(lowLevel, never()).boost(any(MobRef.class), anyInt());
  }

  @Test
  void staticOverdriveStaysOffWhileTheOwnerSwitchesItOff() {
    Loc strike = new Loc(world, 0.5D, 64D, 0.5D);
    MobRef golem = copperGolem(new Loc(world, 2.5D, 64D, 0.5D));
    bound(golem, false);
    GolemCopperFrame learned = learned(5);
    doReturn(false).when(learned).allowsOverdrive(any());

    learned.on(lightning(strike));

    verify(learned, never()).boost(any(MobRef.class), anyInt());
    assertThat(learned.hasTickDemand()).isFalse();
  }

  @Test
  void anOwnerWhoSwitchedStaticOverdriveOffAndQuitKeepsItOffWhileOffline() {
    UUID owner = UUID.randomUUID();
    AdaptServer server = mock(AdaptServer.class);
    when(core.getAdaptServer()).thenReturn(server);
    AdaptPlayer online = learnedOwner(owner, server, 4);
    online.getData().getPreferences().set(adaptation.getId().toString(), "static-overdrive", "OFF");
    PlayerRef player = mock(PlayerRef.class);
    when(player.uuid()).thenReturn(owner);
    when(server.getPlayer(player)).thenReturn(online);
    when(online.getPlayer()).thenReturn(player);
    when(server.getSkillRegistry()).thenReturn(mock(SkillRegistry.class));
    PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
    when(quit.player()).thenReturn(player);
    MobRef golem = copperGolem(new Loc(world, 2.5D, 64D, 0.5D));
    GolemHandle handle = bound(golem, false);
    when(handle.owner()).thenReturn(owner);
    when(handle.ownerLevel(adaptation.getId().toString())).thenReturn(4);
    GolemCopperFrame learned = spy(adaptation);
    doNothing().when(learned).boost(any(MobRef.class), anyInt());

    adaptation.on(quit);
    when(server.getOnlineAdaptPlayer(owner)).thenReturn(null);
    learned.on(lightning(new Loc(world, 0.5D, 64D, 0.5D)));

    verify(learned, never()).boost(any(MobRef.class), anyInt());
  }

  @Test
  void theStaticOverdriveControlAppearsOnceItsLevelUnlocksAndDefaultsOn() {
    assertThat(adaptation.getPlayerPreferences()).containsExactly(CommonPreferences.ENABLED, GolemancyPreferences.STATIC_OVERDRIVE);
    GolemancyPreferenceAssertions.assertVisibleFrom(adaptation, GolemancyPreferences.STATIC_OVERDRIVE, 4);
    UUID owner = UUID.randomUUID();
    when(core.getAdaptServer()).thenReturn(mock(AdaptServer.class));

    assertThat(adaptation.allowsOverdrive(owner)).isTrue();

    GolemancyPreferenceAssertions.serverDefault(adaptation, GolemancyPreferences.STATIC_OVERDRIVE, "OFF");
    assertThat(adaptation.allowsOverdrive(owner)).isFalse();
  }

  private AdaptPlayer learnedOwner(UUID owner, AdaptServer server, int level) {
    AdaptPlayer online = mock(AdaptPlayer.class);
    PlayerData data = mock(PlayerData.class);
    PlayerSkillLine line = mock(PlayerSkillLine.class);
    PlayerPreferenceData preferences = new PlayerPreferenceData();
    when(server.getOnlineAdaptPlayer(owner)).thenReturn(online);
    when(online.isRuntimeReady()).thenReturn(true);
    when(online.getData()).thenReturn(data);
    when(data.getPreferences()).thenReturn(preferences);
    when(data.getSkillLineNullable("adapt:golemancy")).thenReturn(line);
    when(line.getAdaptationLevel(adaptation.getId().toString())).thenReturn(level);
    return online;
  }

  private GolemCopperFrame learned(int level) {
    GolemCopperFrame learned = spy(adaptation);
    doReturn(level).when(learned).ownerLevel(any(GolemHandle.class));
    lenient().doReturn(true).when(learned).allowsOverdrive(any());
    doNothing().when(learned).boost(any(MobRef.class), anyInt());
    return learned;
  }

  private MobRef copperGolem(Loc location) {
    MobRef golem = mock(MobRef.class);
    lenient().when(golem.uuid()).thenReturn(UUID.randomUUID());
    lenient().when(golem.type()).thenReturn(Id.minecraft("copper_golem"));
    lenient().when(golem.location()).thenReturn(location);
    lenient().when(golem.world()).thenReturn(world);
    nearby.add(golem);
    return golem;
  }

  private GolemHandle bound(MobRef golem, boolean dormant) {
    GolemHandle handle = mock(GolemHandle.class);
    lenient().when(handle.id()).thenReturn(UUID.randomUUID());
    lenient().when(handle.body()).thenReturn(GolemBody.COPPER);
    lenient().when(handle.isDormant()).thenReturn(dormant);
    runtime.when(() -> GolemancyRuntime.handle(golem)).thenReturn(Optional.of(handle));
    return handle;
  }

  private static LightningStrikeEvent lightning(Loc location) {
    EntityRef bolt = mock(EntityRef.class);
    lenient().when(bolt.location()).thenReturn(location);
    LightningStrikeEvent event = mock(LightningStrikeEvent.class);
    lenient().when(event.lightning()).thenReturn(bolt);
    return event;
  }

  private GolemStatSource statSource(String id) {
    return statSources.registered().stream().filter(candidate -> candidate.id().equals(id)).findFirst().orElseThrow();
  }

  private GolemCapacity.Source carrySource() {
    return carrySources.registered().stream().filter(candidate -> candidate.id().equals(GolemCopperFrame.STAT_SOURCE)).findFirst().orElseThrow();
  }

  private GolemWorkSpeed.Source workSpeedSource() {
    return workSpeedSources.registered().stream().filter(candidate -> candidate.id().equals(GolemCopperFrame.STAT_SOURCE)).findFirst().orElseThrow();
  }

  private void unregisterSources() {
    GolemStats.unregister(statSource(GolemCopperFrame.STAT_SOURCE));
    GolemStats.unregister(statSource(GolemCopperFrame.PATINA_SOURCE));
    GolemCapacity.unregister(carrySource());
    GolemWorkSpeed.unregister(workSpeedSource());
  }

  private List<String> lore(int level) {
    List<String> lines = new ArrayList<>();
    StatLines stats = new StatLines();
    adaptation.addStats(level, stats);
    for (String line : stats.lines()) {
      lines.add(line.replaceAll("§[0-9a-fk-orx]", ""));
    }
    return lines;
  }
}
