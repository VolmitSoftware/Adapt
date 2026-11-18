package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.api.golem.GolemStat;
import art.arcane.adapt.api.golem.GolemStatDelta;
import art.arcane.adapt.api.golem.GolemStatSource;
import art.arcane.adapt.api.golem.GolemStats;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.testkit.MemoryData;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.ProjectileHitEvent;
import art.arcane.adapt.platform.event.entity.ProjectileLaunchEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GolemFrostFrameTest extends AdaptTestBase {
  private GolemFrostFrame adaptation;
  private MockedStatic<GolemancyRuntime> runtime;
  private GolemRegistrations<GolemStatSource> statSources;
  private WorldRef world;

  @BeforeEach
  void construct() {
    runtime = mockStatic(GolemancyRuntime.class);
    runtime.when(() -> GolemancyRuntime.handle(any())).thenReturn(Optional.empty());
    statSources = GolemRegistrations.stats();
    adaptation = new GolemFrostFrame();
    world = mock(WorldRef.class);
    lenient().when(world.uid()).thenReturn(UUID.randomUUID());
  }

  @AfterEach
  void close() {
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
    GolemFrostFrame learned = spy(adaptation);
    doReturn(4).when(learned).getLevel(online);

    assertThat(learned.ownerLevel(handle)).isEqualTo(4);
    verify(handle).recordOwnerLevel(adaptation.getName(), 4);
  }

  @Test
  void aLevelChangeOrTheOwnerQuittingRefreshesTheRecordOnTheOwnersLoadedGolems() {
    UUID owner = UUID.randomUUID();
    PlayerRef player = mock(PlayerRef.class);
    when(player.uuid()).thenReturn(owner);
    GolemHandle loaded = mock(GolemHandle.class);
    GolemRegistry registry = mock(GolemRegistry.class);
    when(registry.loadedOf(owner)).thenReturn(List.of(loaded));
    runtime.when(GolemancyRuntime::registry).thenReturn(Optional.of(registry));
    AdaptServer server = mock(AdaptServer.class);
    AdaptPlayer online = mock(AdaptPlayer.class);
    when(core.getAdaptServer()).thenReturn(server);
    when(server.getOnlineAdaptPlayer(owner)).thenReturn(online);
    GolemFrostFrame learned = spy(adaptation);
    doReturn(2).when(learned).getLevel(online);

    try (MockedStatic<Tasks> scheduling = mockStatic(Tasks.class)) {
      scheduling.when(() -> Tasks.runEntity(any(), any(Runnable.class))).thenAnswer(invocation -> {
        invocation.<Runnable>getArgument(1).run();
        return true;
      });
      learned.onLevelChanged(player, 0);
      learned.on(new PlayerQuitEvent(player));
    }

    InOrder order = inOrder(loaded);
    order.verify(loaded).recordOwnerLevel(adaptation.getName(), 0);
    order.verify(loaded).recordOwnerLevel(adaptation.getName(), 2);
  }

  @Test
  void defaultsMatchThePlanTables() {
    GolemFrostFrame.Config config = new GolemFrostFrame.Config();

    assertThat(config.healthBonusByLevel).containsExactly(4, 8, 12, 16, 20);
    assertThat(config.snowballDamageByLevel).containsExactly(1, 2, 2, 3, 4);
    assertThat(config.warmBiomeDamageMultiplierByLevel).containsExactly(1.0D, 0.5D, 0D, 0D, 0D);
    assertThat(config.chillLevel).isEqualTo(3);
    assertThat(config.chillTicks).isEqualTo(40);
    assertThat(config.fireRateBonusLevel).isEqualTo(4);
    assertThat(config.extraSnowballChance).isEqualTo(0.25D);
    assertThat(config.frostbiteLevel).isEqualTo(5);
    assertThat(config.frostbiteHits).isEqualTo(4);
    assertThat(config.frostbiteWindowMillis).isEqualTo(5000L);
    assertThat(config.frostbiteFreezeTicks).isEqualTo(100);
    assertThat(config.frostbitePerTargetCooldownMillis).isEqualTo(10000L);
    assertThat(config.requires).containsExactly("golem-animus-binding:1");
    assertThat(config.maxLevel).isEqualTo(5);
  }

  @Test
  void configurationLoadsWithFiveLevelsAndARuntimeOnlyStatSource() {
    assertThat(adaptation.reloadConfigFromDisk(false)).isTrue();
    assertThat(adaptation.getName()).isEqualTo("golem-frost-frame");
    assertThat(adaptation.getMaxLevel()).isEqualTo(5);
    double previous = 0D;
    for (int level = 1; level <= adaptation.getMaxLevel(); level++) {
      assertThat(adaptation.getCostFor(level)).as("cost at level %s", level).isPositive();
      assertThat(adaptation.getLevelPercent(level)).isBetween(previous, 1D);
      previous = adaptation.getLevelPercent(level);
    }
    assertThat(adaptation.isRuntimeRegistered()).isFalse();
  }

  @Test
  void everyLevelShowsItsHealthSnowballsWeatherAndUnlocks() {
    assertThat(lore(1)).containsExactly("+ 4 Bonus Health for Frost Golems", "+ 1 Snowball Damage", "* 100% Warm Biome Damage Taken",
        "+ Immune to Rain and Water");
    assertThat(lore(2)).containsExactly("+ 8 Bonus Health for Frost Golems", "+ 2 Snowball Damage", "* 50% Warm Biome Damage Taken",
        "+ Immune to Rain and Water");
    assertThat(lore(3)).containsExactly("+ 12 Bonus Health for Frost Golems", "+ 2 Snowball Damage", "* 0% Warm Biome Damage Taken",
        "+ Immune to Rain and Water", "+ 2 Seconds of Slowness on Hit");
    assertThat(lore(4)).containsExactly("+ 16 Bonus Health for Frost Golems", "+ 3 Snowball Damage", "* 0% Warm Biome Damage Taken",
        "+ Immune to Rain and Water", "+ 2 Seconds of Slowness on Hit", "+ 25% Chance of an Extra Snowball");
    assertThat(lore(5)).containsExactly("+ 20 Bonus Health for Frost Golems", "+ 4 Snowball Damage", "* 0% Warm Biome Damage Taken",
        "+ Immune to Rain and Water", "+ 2 Seconds of Slowness on Hit", "+ 25% Chance of an Extra Snowball",
        "+ 4 Snowball Hits to Freeze a Target");
  }

  @Test
  void theStatSourceAddsMaximumHealthToFrostGolemsOnly() {
    GolemFrostFrame.Config config = new GolemFrostFrame.Config();

    assertThat(GolemFrostFrame.STAT_SOURCE).isEqualTo("frost_frame");
    assertThat(GolemFrostFrame.frameStats(config, GolemBody.FROST, 3)).containsExactly(GolemStatDelta.add(GolemStat.MAX_HEALTH, 12D));
    assertThat(GolemFrostFrame.frameStats(config, GolemBody.FROST, 0)).isEmpty();
    assertThat(GolemFrostFrame.frameStats(config, GolemBody.IRON, 5)).isEmpty();
    assertThat(GolemFrostFrame.frameStats(config, GolemBody.COPPER, 5)).isEmpty();
  }

  @Test
  void boundFrostGolemsShrugOffRainAndTakeLessWarmBiomeDamage() {
    MobRef golem = boundGolem();
    GolemFrostFrame learned = learned(2);
    EntityDamageEvent rain = weather(golem, DamageCause.DROWNING);
    EntityDamageEvent melting = weather(golem, DamageCause.MELTING);
    EntityDamageEvent fall = weather(golem, DamageCause.FALL);

    learned.on(rain);
    learned.on(melting);
    learned.on(fall);

    verify(rain).setCancelled(true);
    verify(melting).setDamage(0.5D);
    verify(melting, never()).setCancelled(anyBoolean());
    verify(fall, never()).setCancelled(anyBoolean());
    verify(fall, never()).setDamage(anyDouble());
  }

  @Test
  void heatImmunityCancelsMeltingAndUnlearnedOrUnboundGolemsTakeVanillaDamage() {
    MobRef golem = boundGolem();
    EntityDamageEvent immune = weather(golem, DamageCause.MELTING);
    EntityDamageEvent unlearned = weather(golem, DamageCause.DROWNING);
    MobRef wild = mock(MobRef.class);
    EntityDamageEvent unbound = weather(wild, DamageCause.DROWNING);

    learned(3).on(immune);
    learned(0).on(unlearned);
    learned(5).on(unbound);

    verify(immune).setCancelled(true);
    verify(unlearned, never()).setCancelled(anyBoolean());
    verify(unbound, never()).setCancelled(anyBoolean());
  }

  @Test
  void launchedSnowballsAreTaggedWithTheLevelAndTheGolemsTarget() {
    MobRef golem = boundGolem();
    MobRef target = mock(MobRef.class);
    UUID targetId = UUID.randomUUID();
    when(target.uuid()).thenReturn(targetId);
    when(golem.target()).thenReturn(target);
    ProjectileRef snowball = snowball(golem);
    ProjectileRef unlearnedSnowball = snowball(golem);

    learned(2).on(launch(snowball));
    learned(0).on(launch(unlearnedSnowball));

    assertThat(GolemSnowball.read(snowball)).contains(new GolemSnowball.Tag(2, targetId, false));
    assertThat(GolemSnowball.read(unlearnedSnowball)).isEmpty();
  }

  @Test
  void theExtraSnowballFiresAtTheSameTargetFiveTicksLaterWithoutChaining() {
    assertThat(adaptation.reloadConfigSnapshot("extraSnowballChance = 1.0\n", dataFolder.toPath().resolve("extra.toml").toFile(), false)).isTrue();
    MobRef golem = boundGolem();
    MobRef target = mock(MobRef.class);
    when(target.uuid()).thenReturn(UUID.randomUUID());
    when(target.isValid()).thenReturn(true);
    when(target.world()).thenReturn(world);
    when(golem.target()).thenReturn(target);
    when(golem.isValid()).thenReturn(true);
    when(golem.world()).thenReturn(world);
    GolemFrostFrame learned = learned(4);
    ProjectileRef extra = snowball(golem);
    doAnswer(invocation -> {
      learned.on(launch(extra));
      return true;
    }).when(golem).rangedAttack(target);

    try (MockedStatic<Tasks> scheduling = mockStatic(Tasks.class)) {
      scheduling.when(() -> Tasks.runEntity(any(EntityRef.class), any(Runnable.class), anyInt())).thenReturn(true);
      ProjectileRef regular = snowball(golem);
      learned.on(launch(regular));
      learned.on(launch(snowball(golem)));
      ArgumentCaptor<Runnable> shot = ArgumentCaptor.forClass(Runnable.class);
      scheduling.verify(() -> Tasks.runEntity(eq(golem), shot.capture(), eq(5)), times(2));

      shot.getAllValues().getFirst().run();

      verify(golem).rangedAttack(target);
      assertThat(GolemSnowball.read(extra)).hasValueSatisfying(tag -> {
        assertThat(tag.level()).isEqualTo(4);
        assertThat(tag.extra()).isTrue();
      });
      assertThat(GolemSnowball.read(regular)).hasValueSatisfying(tag -> assertThat(tag.extra()).isFalse());
      scheduling.verify(() -> Tasks.runEntity(eq(golem), any(Runnable.class), eq(5)), times(2));
    }
  }

  @Test
  void taggedSnowballsDamageTheirTargetOrAHostileButNotBystanders() {
    UUID targetId = UUID.randomUUID();
    MobRef hostile = hostile();
    MobRef target = living(MobRef.class);
    when(target.uuid()).thenReturn(targetId);
    MobRef bystander = living(MobRef.class);
    EntityDamageByEntityEvent hostileHit = hit(tagged(3, targetId), hostile, 0D);
    EntityDamageByEntityEvent targetHit = hit(tagged(5, targetId), target, 0D);
    EntityDamageByEntityEvent bystanderHit = hit(tagged(5, targetId), bystander, 0D);
    EntityDamageByEntityEvent vanillaHit = hit(snowball(mock(MobRef.class)), hostile, 0D);

    adaptation.on(hostileHit);
    adaptation.on(targetHit);
    adaptation.on(bystanderHit);
    adaptation.on(vanillaHit);

    verify(hostileHit).setDamage(2D);
    verify(targetHit).setDamage(4D);
    verify(bystanderHit, never()).setDamage(anyDouble());
    verify(vanillaHit, never()).setDamage(anyDouble());
  }

  @Test
  void theFourthLandedHitWithinTheWindowKeepsTheTargetFullyFrozenForTheConfiguredTicks() {
    assertThat(adaptation.reloadConfigSnapshot("chillLevel = 9\n", dataFolder.toPath().resolve("frostbite.toml").toFile(), false)).isTrue();
    MobRef hostile = frozenCandidate(0);
    MobRef other = frozenCandidate(0);
    MobRef lockedElsewhere = frozenCandidate(900);

    for (int hit = 0; hit < 3; hit++) {
      adaptation.onSnowballLanded(hit(tagged(5, null), hostile, 0D));
      adaptation.onSnowballLanded(hit(tagged(5, null), lockedElsewhere, 0D));
    }
    adaptation.onSnowballLanded(hit(tagged(4, null), other, 0D));
    verify(hostile, never()).setFreezeTicks(anyInt());

    adaptation.onSnowballLanded(hit(tagged(5, null), hostile, 0D));
    adaptation.onSnowballLanded(hit(tagged(5, null), hostile, 0D));
    adaptation.onSnowballLanded(hit(tagged(5, null), lockedElsewhere, 0D));

    verify(hostile, times(1)).setFreezeTicks(340);
    verify(lockedElsewhere, times(1)).setFreezeTicks(900);
    verify(other, never()).setFreezeTicks(anyInt());
    assertThat(adaptation.hasTickDemand()).isTrue();
  }

  @Test
  void frostbiteLeavesATargetWhoseFreezingAnotherPluginLockedAlone() {
    assertThat(adaptation.reloadConfigSnapshot("chillLevel = 9\n", dataFolder.toPath().resolve("frostbite.toml").toFile(), false)).isTrue();
    MobRef locked = frozenCandidate(0);
    when(locked.isFreezeTickingLocked()).thenReturn(true);

    for (int hit = 0; hit < 4; hit++) {
      adaptation.onSnowballLanded(hit(tagged(5, null), locked, 0D));
    }

    verify(locked, never()).setFreezeTicks(anyInt());
  }

  @Test
  void extraSnowballsClearTheDamageImmunityOfTheirTargetOrAHostileOnly() {
    UUID targetId = UUID.randomUUID();
    MobRef target = living(MobRef.class);
    when(target.uuid()).thenReturn(targetId);
    MobRef hostile = hostile();
    MobRef bystander = living(MobRef.class);
    MobRef regularVictim = hostile();

    adaptation.on(projectileHit(extra(targetId), target));
    adaptation.on(projectileHit(extra(targetId), hostile));
    adaptation.on(projectileHit(extra(targetId), bystander));
    adaptation.on(projectileHit(tagged(5, targetId), regularVictim));
    adaptation.on(projectileHit(snowball(mock(MobRef.class)), hostile));

    verify(target).setNoDamageTicks(0);
    verify(hostile, times(1)).setNoDamageTicks(0);
    verify(bystander, never()).setNoDamageTicks(anyInt());
    verify(regularVictim, never()).setNoDamageTicks(anyInt());
  }

  @Test
  void anOfflineOwnersFrameLevelComesFromTheGolemsRecord() {
    AdaptServer server = mock(AdaptServer.class);
    when(core.getAdaptServer()).thenReturn(server);
    GolemHandle handle = mock(GolemHandle.class);
    when(handle.owner()).thenReturn(UUID.randomUUID());
    when(handle.ownerLevel(adaptation.getName())).thenReturn(3);

    assertThat(adaptation.ownerLevel(handle)).isEqualTo(3);
    verify(handle, never()).recordOwnerLevel(anyString(), anyInt());
  }

  @Test
  void theStatSourceSkipsTheOwnerLookupForOtherBodies() {
    GolemHandle iron = mock(GolemHandle.class);
    when(iron.body()).thenReturn(GolemBody.IRON);
    when(core.getAdaptServer()).thenThrow(new AssertionError("owner level looked up for an iron golem"));
    adaptation.onRuntimeActivated();
    GolemStatSource source = statSources.registered().stream()
        .filter(candidate -> candidate.id().equals(GolemFrostFrame.STAT_SOURCE)).findFirst().orElseThrow();
    try {
      assertThat(source.deltas(iron)).isEmpty();
    } finally {
      GolemStats.unregister(source);
    }
  }

  private MobRef frozenCandidate(int freezeTicks) {
    MobRef zombie = hostile();
    lenient().when(zombie.freezeTicks()).thenReturn(freezeTicks);
    lenient().when(zombie.maxFreezeTicks()).thenReturn(140);
    return zombie;
  }

  private ProjectileRef extra(UUID target) {
    ProjectileRef snowball = snowball(mock(MobRef.class));
    GolemSnowball.tag(snowball, 4, target, true);
    return snowball;
  }

  private static ProjectileHitEvent projectileHit(ProjectileRef snowball, EntityRef victim) {
    ProjectileHitEvent event = mock(ProjectileHitEvent.class);
    lenient().when(event.projectile()).thenReturn(snowball);
    lenient().when(event.hitEntity()).thenReturn(victim);
    return event;
  }

  private GolemFrostFrame learned(int level) {
    GolemFrostFrame learned = spy(adaptation);
    doReturn(level).when(learned).ownerLevel(any(GolemHandle.class));
    return learned;
  }

  private MobRef boundGolem() {
    MobRef golem = mock(MobRef.class);
    lenient().when(golem.uuid()).thenReturn(UUID.randomUUID());
    lenient().when(golem.type()).thenReturn(Id.minecraft("snow_golem"));
    GolemHandle handle = mock(GolemHandle.class);
    lenient().when(handle.body()).thenReturn(GolemBody.FROST);
    runtime.when(() -> GolemancyRuntime.handle(golem)).thenReturn(Optional.of(handle));
    return golem;
  }

  private <T extends LivingRef> T living(Class<T> type) {
    T entity = mock(type);
    lenient().when(entity.uuid()).thenReturn(UUID.randomUUID());
    return entity;
  }

  private MobRef hostile() {
    MobRef entity = living(MobRef.class);
    lenient().when(entity.has(EntityTrait.ENEMY)).thenReturn(true);
    return entity;
  }

  private ProjectileRef tagged(int level, UUID target) {
    ProjectileRef snowball = snowball(mock(MobRef.class));
    GolemSnowball.tag(snowball, level, target, false);
    return snowball;
  }

  private static EntityDamageEvent weather(MobRef golem, DamageCause cause) {
    EntityDamageEvent event = mock(EntityDamageEvent.class);
    lenient().when(event.entity()).thenReturn(golem);
    lenient().when(event.cause()).thenReturn(cause);
    lenient().when(event.damage()).thenReturn(1D);
    return event;
  }

  private static ProjectileLaunchEvent launch(ProjectileRef snowball) {
    ProjectileLaunchEvent event = mock(ProjectileLaunchEvent.class);
    when(event.projectile()).thenReturn(snowball);
    return event;
  }

  private static EntityDamageByEntityEvent hit(ProjectileRef snowball, LivingRef victim, double damage) {
    EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
    lenient().when(event.damager()).thenReturn(snowball);
    lenient().when(event.entity()).thenReturn(victim);
    lenient().when(event.damage()).thenReturn(damage);
    return event;
  }

  private static ProjectileRef snowball(MobRef shooter) {
    MemoryData data = new MemoryData();
    ProjectileRef snowball = mock(ProjectileRef.class);
    lenient().when(snowball.type()).thenReturn(Id.minecraft("snowball"));
    lenient().when(snowball.data()).thenReturn(data);
    lenient().when(snowball.shooter()).thenReturn(shooter);
    return snowball;
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
