package art.arcane.adapt.content.adaptation.chronos;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Vec3;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TemporalFreezeRegistryTest {
  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void overlappingFreezesRestoreOriginalMotionOnlyAfterBothRelease(boolean bombFirst) {
    EntityRef arrow = mock(EntityRef.class);
    when(arrow.uuid()).thenReturn(UUID.randomUUID());
    Vec3 initial = new Vec3(0.6D, 0.3D, 0D);
    when(arrow.velocity()).thenReturn(initial);
    when(arrow.hasGravity()).thenReturn(true);
    when(arrow.isPersistent()).thenReturn(true);
    TemporalFreezeRegistry.Lease before = TemporalFreezeRegistry.acquire(arrow);
    when(arrow.velocity()).thenReturn(Vec3.ZERO);
    when(arrow.hasGravity()).thenReturn(false);
    when(arrow.isPersistent()).thenReturn(false);
    TemporalFreezeRegistry.Lease after = TemporalFreezeRegistry.acquire(arrow);
    assertThat(after.snapshot().gravity()).isTrue();
    assertThat(after.snapshot().persistent()).isTrue();
    assertThat(after.snapshot().velocity()).isEqualTo(initial);
    clearInvocations(arrow);
    TemporalFreezeRegistry.release(arrow, before, bombFirst ? before.snapshot().velocity() : null);
    verify(arrow, never()).setGravity(true);
    assertThat(TemporalFreezeRegistry.isFrozen(arrow)).isTrue();
    TemporalFreezeRegistry.release(arrow, after, bombFirst ? null : after.snapshot().velocity());
    verify(arrow).setGravity(true);
    verify(arrow).setPersistent(true);
    verify(arrow).setVelocity(initial);
    assertThat(TemporalFreezeRegistry.isFrozen(arrow)).isFalse();
  }

  @Test
  void anEarlierBombReleasePreservesItsImpulseUntilStasisAlsoReleases() {
    EntityRef arrow = mock(EntityRef.class);
    when(arrow.uuid()).thenReturn(UUID.randomUUID());
    when(arrow.velocity()).thenReturn(new Vec3(0.4D, 0.2D, 0D));
    TemporalFreezeRegistry.Lease bomb = TemporalFreezeRegistry.acquire(arrow);
    TemporalFreezeRegistry.Lease stasis = TemporalFreezeRegistry.acquire(arrow);
    Vec3 released = new Vec3(0.8D, 0.4D, 0D);
    TemporalFreezeRegistry.release(arrow, bomb, released);
    TemporalFreezeRegistry.release(arrow, stasis, null);
    verify(arrow).setVelocity(released);
  }

  @Test
  void aQueuedOldReleaseCannotReleaseTheNewFreezeOrLoseOriginalAi() {
    LivingRef mob = mock(LivingRef.class);
    when(mob.uuid()).thenReturn(UUID.randomUUID());
    when(mob.velocity()).thenReturn(new Vec3(0.4D, 0D, 0D));
    when(mob.hasGravity()).thenReturn(true);
    when(mob.hasAi()).thenReturn(true);
    TemporalFreezeRegistry.Lease old = TemporalFreezeRegistry.acquire(mob);
    when(mob.hasGravity()).thenReturn(false);
    when(mob.hasAi()).thenReturn(false);
    TemporalFreezeRegistry.Lease current = TemporalFreezeRegistry.acquire(mob);
    assertThat(current.id()).isNotEqualTo(old.id());
    assertThat(current.snapshot().hadAi()).isTrue();
    TemporalFreezeRegistry.release(mob, old, null);
    TemporalFreezeRegistry.release(mob, old, null);
    verify(mob, never()).setGravity(true);
    verify(mob, never()).setAi(true);
    assertThat(TemporalFreezeRegistry.isFrozen(mob)).isTrue();
    TemporalFreezeRegistry.release(mob, current, null);
    verify(mob).setGravity(true);
    verify(mob).setAi(true);
    assertThat(TemporalFreezeRegistry.isFrozen(mob)).isFalse();
  }

  @Test
  void recallCapturesTheFlightStateBeforeAFreezeGrantedFlight() {
    PlayerRef player = mock(PlayerRef.class);
    when(player.uuid()).thenReturn(UUID.randomUUID());
    when(player.hasGravity()).thenReturn(true);
    TemporalFreezeRegistry.Lease lease = TemporalFreezeRegistry.acquire(player);
    when(player.allowFlight()).thenReturn(true);
    when(player.isFlying()).thenReturn(true);
    TemporalFreezeRegistry.Flight original = TemporalFreezeRegistry.flightBeforeFreeze(player);
    assertThat(original.allowFlight()).isFalse();
    assertThat(original.flying()).isFalse();
    TemporalFreezeRegistry.release(player, lease, null);
    verify(player).setAllowFlight(false);
    verify(player).setFlying(false);
  }
}
