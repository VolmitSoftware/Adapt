package art.arcane.adapt.content.adaptation.chronos;

import art.arcane.adapt.engine.player.PlayerApi;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.EntityPotionEffectEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Vec3;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChronosPocketWatchPreferencesTest {
  private static final Id SLOW_FALLING = Id.minecraft("slow_falling");

  @Test
  void preferenceChangeRestoresPreviousEffectWithoutRestoringSpentAirtime() throws ReflectiveOperationException {
    ChronosPocketWatch adaptation = adaptation();
    PlayerRef player = player();
    EffectInstance previous = EffectInstance.of(SLOW_FALLING, 100, 1);
    when(player.effect(SLOW_FALLING)).thenReturn(previous);
    ConcurrentHashMap<UUID, Long> budget = new ConcurrentHashMap<>();
    budget.put(player.uuid(), 500L);
    set(adaptation, "airBudgetMillis", budget);
    apply(adaptation, player);
    when(player.ticksLived()).thenReturn(30);
    clearInvocations(player);

    adaptation.onPlayerPreferencesChanged(PlayerApi.profile(owner(player)));

    verify(player).removeEffect(SLOW_FALLING);
    ArgumentCaptor<EffectInstance> restored = ArgumentCaptor.forClass(EffectInstance.class);
    verify(player).addEffect(restored.capture());
    assertThat(restored.getValue().durationTicks()).isEqualTo(80);
    assertThat(restored.getValue().amplifier()).isEqualTo(1);
    assertThat(budget.get(player.uuid())).isEqualTo(500L);
  }

  @Test
  void externalEffectReplacementRelinquishesOwnership() throws ReflectiveOperationException {
    ChronosPocketWatch adaptation = adaptation();
    PlayerRef player = player();
    apply(adaptation, player);
    EntityPotionEffectEvent event = mock(EntityPotionEffectEvent.class);
    when(event.entity()).thenReturn(player);
    when(event.modifiedType()).thenReturn(SLOW_FALLING);
    adaptation.on(event);
    clearInvocations(player);

    adaptation.onPlayerPreferencesChanged(PlayerApi.profile(owner(player)));

    verify(player, never()).removeEffect(SLOW_FALLING);
    verify(player, never()).addEffect(any(EffectInstance.class));
  }

  @Test
  void acceptedPulseBrakesExistingDownwardMomentum() throws ReflectiveOperationException {
    ChronosPocketWatch adaptation = adaptation();
    PlayerRef player = player();
    when(player.velocity()).thenReturn(new Vec3(0.3, -1.8, -0.2));

    apply(adaptation, player);

    verify(player).setVelocity(new Vec3(0.3, -0.49, -0.2));
  }

  @Test
  void rejectedPulseLeavesMomentumAlone() throws ReflectiveOperationException {
    ChronosPocketWatch adaptation = adaptation();
    PlayerRef player = player();
    when(player.addEffect(any(EffectInstance.class))).thenReturn(false);
    when(player.velocity()).thenReturn(new Vec3(0.3, -1.8, -0.2));

    apply(adaptation, player);

    verify(player, never()).setVelocity(any(Vec3.class));
  }

  @Test
  void acceptedPulsePreservesUpwardMomentum() throws ReflectiveOperationException {
    ChronosPocketWatch adaptation = adaptation();
    PlayerRef player = player();
    when(player.velocity()).thenReturn(new Vec3(0.3, 0.42, -0.2));

    apply(adaptation, player);

    verify(player, never()).setVelocity(any(Vec3.class));
  }

  private static ChronosPocketWatch adaptation() throws ReflectiveOperationException {
    ChronosPocketWatch adaptation = mock(ChronosPocketWatch.class, CALLS_REAL_METHODS);
    doReturn(new ChronosPocketWatch.Config()).when(adaptation).getConfig();
    set(adaptation, "fallingEffects", new ConcurrentHashMap<UUID, Object>());
    set(adaptation, "applyingEffects", new ConcurrentHashMap<UUID, Boolean>());
    return adaptation;
  }

  private static PlayerRef player() {
    PlayerRef player = mock(PlayerRef.class);
    when(player.uuid()).thenReturn(UUID.randomUUID());
    when(player.ticksLived()).thenReturn(10);
    when(player.velocity()).thenReturn(Vec3.ZERO);
    when(player.addEffect(any(EffectInstance.class))).thenReturn(true);
    return player;
  }

  private static AdaptPlayer owner(PlayerRef player) {
    AdaptPlayer owner = mock(AdaptPlayer.class);
    when(owner.getPlayer()).thenReturn(player);
    return owner;
  }

  private static void apply(ChronosPocketWatch adaptation, PlayerRef player) throws ReflectiveOperationException {
    Method method = ChronosPocketWatch.class.getDeclaredMethod("applyFallEffect", PlayerRef.class);
    method.setAccessible(true);
    method.invoke(adaptation, player);
  }

  private static void set(ChronosPocketWatch adaptation, String name, Object value) throws ReflectiveOperationException {
    Field field = ChronosPocketWatch.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(adaptation, value);
  }
}
