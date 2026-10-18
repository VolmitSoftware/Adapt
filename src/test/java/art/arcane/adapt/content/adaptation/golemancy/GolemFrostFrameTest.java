package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemStat;
import art.arcane.adapt.api.golem.GolemStatDelta;
import art.arcane.adapt.api.golem.GolemStatSource;
import art.arcane.adapt.api.golem.GolemStats;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Snowman;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
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
  private World world;

  @BeforeEach
  void construct() {
    when(plugin.namespace()).thenReturn("adapt");
    runtime = mockStatic(GolemancyRuntime.class);
    runtime.when(() -> GolemancyRuntime.handle(any())).thenReturn(Optional.empty());
    adaptation = new GolemFrostFrame();
    world = mock(World.class);
  }

  @AfterEach
  void close() {
    runtime.close();
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
    Snowman golem = boundGolem();
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
    Snowman golem = boundGolem();
    EntityDamageEvent immune = weather(golem, DamageCause.MELTING);
    EntityDamageEvent unlearned = weather(golem, DamageCause.DROWNING);
    Snowman wild = mock(Snowman.class);
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
    Snowman golem = boundGolem();
    Zombie target = mock(Zombie.class);
    UUID targetId = UUID.randomUUID();
    when(target.getUniqueId()).thenReturn(targetId);
    when(golem.getTarget()).thenReturn(target);
    Snowball snowball = snowball(golem);
    Snowball unlearnedSnowball = snowball(golem);

    learned(2).on(launch(snowball));
    learned(0).on(launch(unlearnedSnowball));

    assertThat(GolemSnowball.read(snowball)).contains(new GolemSnowball.Tag(2, targetId, false));
    assertThat(GolemSnowball.read(unlearnedSnowball)).isEmpty();
  }

  @Test
  void theExtraSnowballFiresAtTheSameTargetFiveTicksLaterWithoutChaining() {
    assertThat(adaptation.reloadConfigSnapshot("extraSnowballChance = 1.0\n", dataFolder.toPath().resolve("extra.toml").toFile(), false)).isTrue();
    Snowman golem = boundGolem();
    Zombie target = mock(Zombie.class);
    when(target.getUniqueId()).thenReturn(UUID.randomUUID());
    when(target.isValid()).thenReturn(true);
    when(target.getWorld()).thenReturn(world);
    when(golem.getTarget()).thenReturn(target);
    when(golem.isValid()).thenReturn(true);
    when(golem.getWorld()).thenReturn(world);
    GolemFrostFrame learned = learned(4);
    Snowball extra = snowball(golem);
    doAnswer(invocation -> {
      learned.on(launch(extra));
      return null;
    }).when(golem).rangedAttack(target, 1F);

    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(() -> J.runEntity(any(Entity.class), any(Runnable.class), anyInt())).thenReturn(true);
      Snowball regular = snowball(golem);
      learned.on(launch(regular));
      learned.on(launch(snowball(golem)));
      ArgumentCaptor<Runnable> shot = ArgumentCaptor.forClass(Runnable.class);
      scheduling.verify(() -> J.runEntity(eq(golem), shot.capture(), eq(5)), times(2));

      shot.getAllValues().getFirst().run();

      verify(golem).rangedAttack(target, 1F);
      assertThat(GolemSnowball.read(extra)).hasValueSatisfying(tag -> {
        assertThat(tag.level()).isEqualTo(4);
        assertThat(tag.extra()).isTrue();
      });
      assertThat(GolemSnowball.read(regular)).hasValueSatisfying(tag -> assertThat(tag.extra()).isFalse());
      scheduling.verify(() -> J.runEntity(eq(golem), any(Runnable.class), eq(5)), times(2));
    }
  }

  @Test
  void taggedSnowballsDamageTheirTargetOrAHostileButNotBystanders() {
    UUID targetId = UUID.randomUUID();
    Zombie hostile = living(Zombie.class);
    Cow target = living(Cow.class);
    when(target.getUniqueId()).thenReturn(targetId);
    Cow bystander = living(Cow.class);
    EntityDamageByEntityEvent hostileHit = hit(tagged(3, targetId), hostile, 0D);
    EntityDamageByEntityEvent targetHit = hit(tagged(5, targetId), target, 0D);
    EntityDamageByEntityEvent bystanderHit = hit(tagged(5, targetId), bystander, 0D);
    EntityDamageByEntityEvent vanillaHit = hit(snowball(mock(Snowman.class)), hostile, 0D);

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
    Zombie hostile = frozenCandidate(0);
    Zombie other = frozenCandidate(0);
    Zombie lockedElsewhere = frozenCandidate(900);

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
    verify(hostile, never()).lockFreezeTicks(anyBoolean());
    verify(lockedElsewhere, never()).lockFreezeTicks(anyBoolean());
    assertThat(adaptation.hasTickDemand()).isTrue();
  }

  @Test
  void extraSnowballsClearTheDamageImmunityOfTheirTargetOrAHostileOnly() {
    UUID targetId = UUID.randomUUID();
    Cow target = living(Cow.class);
    when(target.getUniqueId()).thenReturn(targetId);
    Zombie hostile = living(Zombie.class);
    Cow bystander = living(Cow.class);
    Zombie regularVictim = living(Zombie.class);

    adaptation.on(projectileHit(extra(targetId), target));
    adaptation.on(projectileHit(extra(targetId), hostile));
    adaptation.on(projectileHit(extra(targetId), bystander));
    adaptation.on(projectileHit(tagged(5, targetId), regularVictim));
    adaptation.on(projectileHit(snowball(mock(Snowman.class)), hostile));

    verify(target).setNoDamageTicks(0);
    verify(hostile, times(1)).setNoDamageTicks(0);
    verify(bystander, never()).setNoDamageTicks(anyInt());
    verify(regularVictim, never()).setNoDamageTicks(anyInt());
  }

  @Test
  void theStatSourceSkipsTheOwnerLookupForOtherBodies() {
    GolemHandle iron = mock(GolemHandle.class);
    when(iron.body()).thenReturn(GolemBody.IRON);
    when(plugin.getAdaptServer()).thenThrow(new AssertionError("owner level looked up for an iron golem"));
    adaptation.onRuntimeActivated();
    GolemStatSource source = GolemStats.sources().stream()
        .filter(candidate -> candidate.id().equals(GolemFrostFrame.STAT_SOURCE)).findFirst().orElseThrow();
    try {
      assertThat(source.deltas(iron)).isEmpty();
    } finally {
      GolemStats.unregister(source);
    }
  }

  private Zombie frozenCandidate(int freezeTicks) {
    Zombie zombie = living(Zombie.class);
    lenient().when(zombie.getFreezeTicks()).thenReturn(freezeTicks);
    lenient().when(zombie.getMaxFreezeTicks()).thenReturn(140);
    return zombie;
  }

  private Snowball extra(UUID target) {
    Snowball snowball = snowball(mock(Snowman.class));
    GolemSnowball.tag(snowball, 4, target, true);
    return snowball;
  }

  private static ProjectileHitEvent projectileHit(Snowball snowball, Entity victim) {
    ProjectileHitEvent event = mock(ProjectileHitEvent.class);
    lenient().when(event.getEntity()).thenReturn(snowball);
    lenient().when(event.getHitEntity()).thenReturn(victim);
    return event;
  }

  private GolemFrostFrame learned(int level) {
    GolemFrostFrame learned = spy(adaptation);
    doReturn(level).when(learned).ownerLevel(any(GolemHandle.class));
    return learned;
  }

  private Snowman boundGolem() {
    Snowman golem = mock(Snowman.class);
    lenient().when(golem.getUniqueId()).thenReturn(UUID.randomUUID());
    GolemHandle handle = mock(GolemHandle.class);
    lenient().when(handle.body()).thenReturn(GolemBody.FROST);
    runtime.when(() -> GolemancyRuntime.handle(golem)).thenReturn(Optional.of(handle));
    return golem;
  }

  private <T extends LivingEntity> T living(Class<T> type) {
    T entity = mock(type);
    lenient().when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
    return entity;
  }

  private Snowball tagged(int level, UUID target) {
    Snowball snowball = snowball(mock(Snowman.class));
    GolemSnowball.tag(snowball, level, target, false);
    return snowball;
  }

  private static EntityDamageEvent weather(Snowman golem, DamageCause cause) {
    EntityDamageEvent event = mock(EntityDamageEvent.class);
    lenient().when(event.getEntity()).thenReturn(golem);
    lenient().when(event.getCause()).thenReturn(cause);
    lenient().when(event.getDamage()).thenReturn(1D);
    return event;
  }

  private static ProjectileLaunchEvent launch(Snowball snowball) {
    ProjectileLaunchEvent event = mock(ProjectileLaunchEvent.class);
    when(event.getEntity()).thenReturn(snowball);
    return event;
  }

  private static EntityDamageByEntityEvent hit(Snowball snowball, LivingEntity victim, double damage) {
    EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
    lenient().when(event.getDamager()).thenReturn(snowball);
    lenient().when(event.getEntity()).thenReturn(victim);
    lenient().when(event.getDamage()).thenReturn(damage);
    return event;
  }

  private static Snowball snowball(Snowman shooter) {
    PersistentDataContainer data = mapBacked();
    Snowball snowball = mock(Snowball.class);
    lenient().when(snowball.getPersistentDataContainer()).thenReturn(data);
    lenient().when(snowball.getShooter()).thenReturn(shooter);
    return snowball;
  }

  private static PersistentDataContainer mapBacked() {
    Map<NamespacedKey, Object> values = new HashMap<>();
    PersistentDataContainer data = mock(PersistentDataContainer.class);
    lenient().when(data.get(any(NamespacedKey.class), any(PersistentDataType.class)))
        .thenAnswer(invocation -> values.get(invocation.<NamespacedKey>getArgument(0)));
    lenient().when(data.has(any(NamespacedKey.class), any(PersistentDataType.class)))
        .thenAnswer(invocation -> values.containsKey(invocation.<NamespacedKey>getArgument(0)));
    lenient().doAnswer(invocation -> values.put(invocation.getArgument(0), invocation.getArgument(2)))
        .when(data).set(any(NamespacedKey.class), any(PersistentDataType.class), any());
    lenient().doAnswer(invocation -> values.remove(invocation.<NamespacedKey>getArgument(0)))
        .when(data).remove(any(NamespacedKey.class));
    return data;
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
