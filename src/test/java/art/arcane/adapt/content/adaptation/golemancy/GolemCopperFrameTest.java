package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemCapacity;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemStat;
import art.arcane.adapt.api.golem.GolemStatDelta;
import art.arcane.adapt.api.golem.GolemStatSource;
import art.arcane.adapt.api.golem.GolemStats;
import art.arcane.adapt.api.golem.GolemWorkSpeed;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreferenceData;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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
  private World world;
  private final List<Entity> nearby = new ArrayList<>();

  @BeforeEach
  void construct() {
    when(plugin.namespace()).thenReturn("adapt");
    runtime = mockStatic(GolemancyRuntime.class);
    runtime.when(() -> GolemancyRuntime.handle(any())).thenReturn(Optional.empty());
    statSources = GolemRegistrations.stats();
    carrySources = GolemRegistrations.carry();
    workSpeedSources = GolemRegistrations.workSpeed();
    adaptation = new GolemCopperFrame();
    world = mock(World.class);
    lenient().when(world.getNearbyEntities(any(BoundingBox.class), any())).thenAnswer(invocation -> {
      Predicate<Entity> filter = invocation.getArgument(1);
      return nearby.stream().filter(filter).toList();
    });
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
    when(plugin.getAdaptServer()).thenReturn(server);
    when(server.getOnlineAdaptPlayer(owner)).thenReturn(online);
    GolemHandle handle = mock(GolemHandle.class);
    when(handle.owner()).thenReturn(owner);
    GolemCopperFrame learned = spy(adaptation);
    doReturn(4).when(learned).getLevel(online);

    assertThat(learned.ownerLevel(handle)).isEqualTo(4);
    verify(handle).recordOwnerLevel(adaptation.getName(), 4);
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
    assertThat(config.requires).containsExactly("golem-animus-binding:1");
    assertThat(config.maxLevel).isEqualTo(5);
  }

  @Test
  void loadingClampsTheOverdriveRadiusToSixtyFourBlocks() {
    assertThat(adaptation.reloadConfigSnapshot("overdriveRadius = 500.0\n", dataFolder.toPath().resolve("far.toml").toFile(), false)).isTrue();
    assertThat(adaptation.getConfig().overdriveRadius).isEqualTo(64D);

    assertThat(adaptation.reloadConfigSnapshot("overdriveRadius = -3.0\n", dataFolder.toPath().resolve("negative.toml").toFile(), false)).isTrue();
    assertThat(adaptation.getConfig().overdriveRadius).isZero();

    assertThat(adaptation.reloadConfigSnapshot("overdriveRadius = 24.0\n", dataFolder.toPath().resolve("near.toml").toFile(), false)).isTrue();
    assertThat(adaptation.getConfig().overdriveRadius).isEqualTo(24D);
  }

  @Test
  void overdriveUsesTheConfiguredWorkIntervalMultiplier() {
    Location strike = new Location(world, 0.5D, 64D, 0.5D);
    CopperGolem golem = copperGolem(new Location(world, 2.5D, 64D, 0.5D));
    GolemHandle handle = bound(golem, false);
    assertThat(adaptation.reloadConfigSnapshot("overdriveWorkIntervalMultiplier = 0.25\n", dataFolder.toPath().resolve("multiplier.toml").toFile(), false))
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
    assertThat(adaptation.reloadConfigFromDisk(false)).isTrue();
    assertThat(adaptation.getName()).isEqualTo("golem-copper-frame");
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
    when(plugin.getAdaptServer()).thenThrow(new AssertionError("owner level looked up for an iron golem"));
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
    Location strike = new Location(world, 0.5D, 64D, 0.5D);
    CopperGolem golem = copperGolem(new Location(world, 8.5D, 64D, 8.5D));
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
    Location strike = new Location(world, 0.5D, 64D, 0.5D);
    CopperGolem far = copperGolem(new Location(world, 16.5D, 64D, 16.5D));
    bound(far, false);
    CopperGolem dormant = copperGolem(new Location(world, 2.5D, 64D, 0.5D));
    bound(dormant, true);
    CopperGolem wild = copperGolem(new Location(world, 1.5D, 64D, 0.5D));
    GolemCopperFrame learned = learned(5);
    GolemCopperFrame lowLevel = learned(3);
    CopperGolem close = copperGolem(new Location(world, 0.5D, 64D, 3.5D));
    bound(close, false);

    lowLevel.on(lightning(strike));
    learned.on(lightning(strike));

    verify(learned, never()).boost(far, 1200);
    verify(learned, never()).boost(dormant, 1200);
    verify(learned, never()).boost(wild, 1200);
    verify(learned).boost(close, 1200);
    verify(lowLevel, never()).boost(any(CopperGolem.class), anyInt());
  }

  @Test
  void staticOverdriveStaysOffWhileTheOwnerSwitchesItOff() {
    Location strike = new Location(world, 0.5D, 64D, 0.5D);
    CopperGolem golem = copperGolem(new Location(world, 2.5D, 64D, 0.5D));
    bound(golem, false);
    GolemCopperFrame learned = learned(5);
    doReturn(false).when(learned).allowsOverdrive(any());

    learned.on(lightning(strike));

    verify(learned, never()).boost(any(CopperGolem.class), anyInt());
    assertThat(learned.hasTickDemand()).isFalse();
  }

  @Test
  void anOwnerWhoSwitchedStaticOverdriveOffAndQuitKeepsItOffWhileOffline() {
    UUID owner = UUID.randomUUID();
    AdaptServer server = mock(AdaptServer.class);
    when(plugin.getAdaptServer()).thenReturn(server);
    AdaptPlayer online = learnedOwner(owner, server, 4);
    online.getData().getPreferences().set(adaptation.getName(), "static-overdrive", "OFF");
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(owner);
    PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
    when(quit.getPlayer()).thenReturn(player);
    CopperGolem golem = copperGolem(new Location(world, 2.5D, 64D, 0.5D));
    GolemHandle handle = bound(golem, false);
    when(handle.owner()).thenReturn(owner);
    when(handle.ownerLevel(adaptation.getName())).thenReturn(4);
    GolemCopperFrame learned = spy(adaptation);
    doNothing().when(learned).boost(any(CopperGolem.class), anyInt());

    learned.on(quit);
    when(server.getOnlineAdaptPlayer(owner)).thenReturn(null);
    learned.on(lightning(new Location(world, 0.5D, 64D, 0.5D)));

    verify(learned, never()).boost(any(CopperGolem.class), anyInt());
  }

  @Test
  void theStaticOverdriveControlAppearsOnceItsLevelUnlocksAndDefaultsOn() {
    assertThat(adaptation.getPlayerPreferences()).containsExactly(CommonPreferences.ENABLED, GolemancyPreferences.STATIC_OVERDRIVE);
    GolemancyPreferenceAssertions.assertVisibleFrom(adaptation, GolemancyPreferences.STATIC_OVERDRIVE, 4);
    UUID owner = UUID.randomUUID();
    when(plugin.getAdaptServer()).thenReturn(mock(AdaptServer.class));

    assertThat(adaptation.allowsOverdrive(owner)).isTrue();

    GolemancyPreferenceAssertions.serverDefault(adaptation, GolemancyPreferences.STATIC_OVERDRIVE, "OFF");
    assertThat(adaptation.allowsOverdrive(owner)).isFalse();
  }

  private AdaptPlayer learnedOwner(UUID owner, AdaptServer server, int level) {
    AdaptPlayer online = mock(AdaptPlayer.class);
    PlayerData data = mock(PlayerData.class);
    PlayerSkillLine line = mock(PlayerSkillLine.class);
    Skill<?> skill = mock(Skill.class);
    PlayerPreferenceData preferences = new PlayerPreferenceData();
    when(server.getOnlineAdaptPlayer(owner)).thenReturn(online);
    when(online.isRuntimeReady()).thenReturn(true);
    when(online.getData()).thenReturn(data);
    when(data.getPreferences()).thenReturn(preferences);
    when(skill.isEnabled()).thenReturn(true);
    when(skill.getName()).thenReturn("golemancy");
    when(data.getSkillLineNullable("golemancy")).thenReturn(line);
    when(line.getAdaptationLevel(adaptation.getName())).thenReturn(level);
    adaptation.setSkill(skill);
    return online;
  }

  private GolemCopperFrame learned(int level) {
    GolemCopperFrame learned = spy(adaptation);
    doReturn(level).when(learned).ownerLevel(any(GolemHandle.class));
    lenient().doReturn(true).when(learned).allowsOverdrive(any());
    doNothing().when(learned).boost(any(CopperGolem.class), anyInt());
    return learned;
  }

  private CopperGolem copperGolem(Location location) {
    CopperGolem golem = mock(CopperGolem.class);
    lenient().when(golem.getUniqueId()).thenReturn(UUID.randomUUID());
    lenient().when(golem.getLocation()).thenReturn(location);
    lenient().when(golem.getWorld()).thenReturn(world);
    nearby.add(golem);
    return golem;
  }

  private GolemHandle bound(CopperGolem golem, boolean dormant) {
    GolemHandle handle = mock(GolemHandle.class);
    lenient().when(handle.id()).thenReturn(UUID.randomUUID());
    lenient().when(handle.body()).thenReturn(GolemBody.COPPER);
    lenient().when(handle.isDormant()).thenReturn(dormant);
    runtime.when(() -> GolemancyRuntime.handle(golem)).thenReturn(Optional.of(handle));
    return handle;
  }

  private static LightningStrikeEvent lightning(Location location) {
    LightningStrike bolt = mock(LightningStrike.class);
    lenient().when(bolt.getLocation()).thenReturn(location);
    LightningStrikeEvent event = mock(LightningStrikeEvent.class);
    lenient().when(event.getLightning()).thenReturn(bolt);
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
    Element stats = mock(Element.class, RETURNS_SELF);
    when(stats.addLore(anyString())).thenAnswer(invocation -> {
      lines.add(invocation.<String>getArgument(0).replaceAll("§[0-9a-fk-orx]", ""));
      return stats;
    });
    adaptation.addStats(level, stats);
    return lines;
  }
}
