package art.arcane.adapt.content.adaptation.axe;

import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
    for (Class<?> adaptationType : List.of(AxeCleave.class, AxeGroundSmash.class, AxeThrowingAxe.class)) {
      Object config = newConfiguration(adaptationType);
      Field protection = config.getClass().getDeclaredField("ignorePassiveMobs");
      protection.setAccessible(true);
      assertThat(protection.getBoolean(config)).as(adaptationType.getSimpleName()).isFalse();
    }
  }

  @Test
  void directThrowsRemainAvailableWhileRicochetHitsRespectProtection() {
    LivingRef passive = passive();
    LivingRef neutral = enemy("enderman");
    LivingRef hostile = enemy("husk");
    PlayerRef player = mock(PlayerRef.class);
    when(player.uuid()).thenReturn(UUID.randomUUID());

    for (LivingRef target : List.of(passive, neutral, hostile, player)) {
      assertThat(AxeThrow.allowsThrowImpact(target, 0, true)).isTrue();
      assertThat(AxeThrow.allowsThrowImpact(target, 2, false)).isTrue();
    }
    assertThat(AxeThrow.allowsThrowImpact(passive, 1, true)).isFalse();
    assertThat(AxeThrow.allowsThrowImpact(neutral, 2, true)).isFalse();
    assertThat(AxeThrow.allowsThrowImpact(hostile, 1, true)).isTrue();
    assertThat(AxeThrow.allowsThrowImpact(player, 1, true)).isTrue();
  }

  @Test
  void cleaveRechecksProtectionWhenScheduledDamageExecutes() throws ReflectiveOperationException {
    AxeCleave adaptation = configured(AxeCleave.class, false);
    PlayerRef owner = mock(PlayerRef.class);
    LivingRef passive = passive();
    when(passive.isValid()).thenReturn(true);
    doReturn(false).when(adaptation).isProtectedFriendly(owner, passive);
    AxeCleaveArc arc = new AxeCleaveArc(adaptation, () -> adaptation.getConfig().ignorePassiveMobs);
    AtomicReference<Runnable> pending = new AtomicReference<>();

    try (MockedStatic<Tasks> scheduling = mockStatic(Tasks.class)) {
      scheduling.when(() -> Tasks.runEntity(same(passive), any(Runnable.class))).thenAnswer(invocation -> {
        pending.set(invocation.getArgument(1, Runnable.class));
        return true;
      });
      arc.strike(passive, owner, 4D, 0L, (LivingRef struck) -> { });
      adaptation.getConfig().ignorePassiveMobs = true;
      assertThat(pending.get()).isNotNull();
      pending.get().run();
    }

    verify(passive, never()).damage(anyDouble(), same(owner));
  }

  @Test
  void groundSmashSkipsProtectedMobsBeforeItsCandidateLimit() throws ReflectiveOperationException {
    AxeGroundSmash adaptation = configured(AxeGroundSmash.class, true);
    PlayerRef owner = mock(PlayerRef.class);
    when(owner.uuid()).thenReturn(UUID.randomUUID());
    WorldRef world = mock(WorldRef.class);
    Loc center = new Loc(world, 0, 64, 0);
    LivingRef hostile = enemy("husk");
    List<EntityRef> candidates = crowdedCandidates(hostile);
    when(world.entitiesIn(any(), any())).thenReturn(candidates);
    assertThat(AxeSmash.collectCandidates(adaptation, owner, center, 5D, adaptation.getConfig().ignorePassiveMobs))
        .isEqualTo(List.of(hostile));
  }

  private static List<EntityRef> crowdedCandidates(LivingRef hostile) {
    List<EntityRef> candidates = new ArrayList<>(129);
    for (int index = 0; index < 64; index++) {
      candidates.add(passive());
      candidates.add(enemy("enderman"));
    }
    candidates.add(hostile);
    return candidates;
  }

  private static LivingRef passive() {
    LivingRef passive = mock(LivingRef.class);
    when(passive.uuid()).thenReturn(UUID.randomUUID());
    when(passive.type()).thenReturn(Id.minecraft("cow"));
    return passive;
  }

  private static LivingRef enemy(String type) {
    LivingRef enemy = mock(LivingRef.class);
    when(enemy.uuid()).thenReturn(UUID.randomUUID());
    when(enemy.type()).thenReturn(Id.minecraft(type));
    when(enemy.has(EntityTrait.ENEMY)).thenReturn(true);
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
