package art.arcane.adapt.content.adaptation.axe;

import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.content.adaptation.excavation.ExcavationEarthMover;
import art.arcane.adapt.content.adaptation.sword.SwordsCrimsonCyclone;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AreaDamageTargetProtectionTest {
  @Test
  void protectionDefaultsOffForEveryAreaAttack() throws ReflectiveOperationException {
    for (Class<?> adaptationType : List.of(AxeCleave.class, AxeGroundSmash.class,
        AxeThrowingAxe.class, SwordsCrimsonCyclone.class, ExcavationEarthMover.class)) {
      Object config = newConfiguration(adaptationType);
      Field protection = config.getClass().getDeclaredField("ignorePassiveMobs");
      protection.setAccessible(true);
      assertThat(protection.getBoolean(config)).as(adaptationType.getSimpleName()).isFalse();
    }
  }

  @Test
  void directThrowsRemainAvailableWhileRicochetHitsRespectProtection() {
    Cow passive = mock(Cow.class);
    Enemy neutral = enemy(EntityType.ENDERMAN);
    Enemy hostile = enemy(EntityType.HUSK);
    Player player = mock(Player.class);

    for (LivingEntity target : List.of(passive, neutral, hostile, player)) {
      assertThat(AxeThrowingAxe.allowsThrowImpact(target, 0, true)).isTrue();
      assertThat(AxeThrowingAxe.allowsThrowImpact(target, 2, false)).isTrue();
    }
    assertThat(AxeThrowingAxe.allowsThrowImpact(passive, 1, true)).isFalse();
    assertThat(AxeThrowingAxe.allowsThrowImpact(neutral, 2, true)).isFalse();
    assertThat(AxeThrowingAxe.allowsThrowImpact(hostile, 1, true)).isTrue();
    assertThat(AxeThrowingAxe.allowsThrowImpact(player, 1, true)).isTrue();
  }

  @Test
  void cleaveRechecksProtectionWhenScheduledDamageExecutes() throws ReflectiveOperationException {
    AxeCleave adaptation = configured(AxeCleave.class, false);
    Player owner = mock(Player.class);
    Cow passive = mock(Cow.class);
    when(passive.isValid()).thenReturn(true);
    when(passive.getUniqueId()).thenReturn(UUID.randomUUID());
    doReturn(false).when(adaptation).isProtectedFriendly(owner, passive);
    Field cleaving = AxeCleave.class.getDeclaredField("cleaving");
    cleaving.setAccessible(true);
    cleaving.set(adaptation, new ConcurrentHashMap<UUID, Long>());
    AtomicReference<Runnable> pending = new AtomicReference<>();
    Method damage = AxeCleave.class.getDeclaredMethod("markAndDamage", LivingEntity.class,
        Player.class, double.class, long.class);
    damage.setAccessible(true);

    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(() -> J.runEntity(same(passive), any(Runnable.class))).thenAnswer(invocation -> {
        pending.set(invocation.getArgument(1, Runnable.class));
        return true;
      });
      damage.invoke(adaptation, passive, owner, 4D, 0L);
      adaptation.getConfig().ignorePassiveMobs = true;
      assertThat(pending.get()).isNotNull();
      pending.get().run();
    }

    verify(passive, never()).damage(anyDouble(), same(owner));
  }

  @Test
  void groundSmashSkipsProtectedMobsBeforeItsCandidateLimit() throws ReflectiveOperationException {
    AxeGroundSmash adaptation = configured(AxeGroundSmash.class, true);
    Player owner = mock(Player.class);
    Location center = new Location(mock(World.class), 0, 64, 0);
    Enemy hostile = enemy(EntityType.HUSK);
    List<LivingEntity> candidates = crowdedCandidates(hostile);
    Method collect = AxeGroundSmash.class.getDeclaredMethod("collectCandidates", Player.class, Location.class, double.class);
    collect.setAccessible(true);

    try (MockedStatic<PaperCompat> compat = mockStatic(PaperCompat.class)) {
      compat.when(() -> PaperCompat.nearbyLivingEntities(center, 5D)).thenReturn(candidates);
      assertThat((List<?>) collect.invoke(adaptation, owner, center, 5D)).isEqualTo(List.of(hostile));
    }
  }

  @Test
  void cycloneSkipsProtectedMobsBeforeItsCandidateLimit() throws ReflectiveOperationException {
    SwordsCrimsonCyclone adaptation = configured(SwordsCrimsonCyclone.class, true);
    Player owner = mock(Player.class);
    Cow primary = mock(Cow.class);
    Location center = new Location(mock(World.class), 0, 64, 0);
    Enemy hostile = enemy(EntityType.HUSK);
    List<LivingEntity> candidates = crowdedCandidates(hostile);
    candidates.add(0, primary);
    Method collect = SwordsCrimsonCyclone.class.getDeclaredMethod("collectCandidates", Player.class,
        LivingEntity.class, Location.class, double.class);
    collect.setAccessible(true);

    try (MockedStatic<PaperCompat> compat = mockStatic(PaperCompat.class)) {
      compat.when(() -> PaperCompat.nearbyLivingEntities(center, 5D, 5D, 5D)).thenReturn(candidates);
      assertThat((List<?>) collect.invoke(adaptation, owner, primary, center, 5D)).isEqualTo(List.of(hostile));
    }
  }

  @Test
  void earthMoverSkipsNeutralEnemiesBeforeItsCandidateLimit() throws ReflectiveOperationException {
    ExcavationEarthMover adaptation = configured(ExcavationEarthMover.class, true);
    World world = mock(World.class);
    Location center = new Location(world, 0, 64, 0);
    Enemy hostile = enemy(EntityType.HUSK);
    List<LivingEntity> candidates = crowdedCandidates(hostile);
    when(world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble()))
        .thenReturn(new ArrayList<>(candidates));
    Method collect = ExcavationEarthMover.class.getDeclaredMethod("collectCandidates", Player.class, Location.class, double.class);
    collect.setAccessible(true);

    assertThat((List<?>) collect.invoke(adaptation, mock(Player.class), center, 5D)).isEqualTo(List.of(hostile));
  }

  @Test
  void earthMoverPersonalProtectionFiltersBeforeItsCandidateLimit() throws ReflectiveOperationException {
    ExcavationEarthMover adaptation = configured(ExcavationEarthMover.class, false);
    Player owner = mock(Player.class);
    doReturn(true).when(adaptation).preferenceEnabled(owner, ExcavationEarthMover.IGNORE_PASSIVE);
    World world = mock(World.class);
    Location center = new Location(world, 0, 64, 0);
    Enemy hostile = enemy(EntityType.HUSK);
    List<LivingEntity> candidates = crowdedCandidates(hostile);
    when(world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble()))
        .thenReturn(new ArrayList<>(candidates));
    Method collect = ExcavationEarthMover.class.getDeclaredMethod("collectCandidates", Player.class, Location.class, double.class);
    collect.setAccessible(true);

    assertThat((List<?>) collect.invoke(adaptation, owner, center, 5D)).isEqualTo(List.of(hostile));
  }

  private static List<LivingEntity> crowdedCandidates(Enemy hostile) {
    List<LivingEntity> candidates = new ArrayList<>(129);
    for (int index = 0; index < 64; index++) {
      candidates.add(mock(Cow.class));
      candidates.add(enemy(EntityType.ENDERMAN));
    }
    candidates.add(hostile);
    return candidates;
  }

  private static Enemy enemy(EntityType type) {
    Enemy enemy = mock(Enemy.class);
    when(enemy.getType()).thenReturn(type);
    return enemy;
  }

  private static <T extends SimpleAdaptation<?>> T configured(Class<T> adaptationType, boolean enabled)
      throws ReflectiveOperationException {
    Object config = newConfiguration(adaptationType);
    Field protection = config.getClass().getDeclaredField("ignorePassiveMobs");
    protection.setAccessible(true);
    protection.setBoolean(config, enabled);
    T adaptation = mock(adaptationType, CALLS_REAL_METHODS);
    doReturn(config).when(adaptation).getConfig();
    return adaptation;
  }

  private static Object newConfiguration(Class<?> adaptationType) throws ReflectiveOperationException {
    Class<?> configType = Class.forName(adaptationType.getName() + "$Config");
    Constructor<?> constructor = configType.getDeclaredConstructor();
    constructor.setAccessible(true);
    return constructor.newInstance();
  }
}
