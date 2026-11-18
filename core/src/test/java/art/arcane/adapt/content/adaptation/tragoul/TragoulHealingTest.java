package art.arcane.adapt.content.adaptation.tragoul;

import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TragoulHealingTest {
  @Test
  void drainDamageScalesFromHalfAHealthPointToTwoHealth() {
    assertThat(TragoulHealing.drainDamage(0.5D, 2D, 1, 5)).isEqualTo(0.5D);
    assertThat(TragoulHealing.drainDamage(0.5D, 2D, 3, 5)).isEqualTo(1.25D);
    assertThat(TragoulHealing.drainDamage(0.5D, 2D, 5, 5)).isEqualTo(2D);
  }

  @Test
  void drainDamageRejectsInvalidValuesAndNeverGoesNegative() {
    assertThat(TragoulHealing.drainDamage(Double.NaN, 2D, 1, 5)).isZero();
    assertThat(TragoulHealing.drainDamage(-2D, -1D, 5, 5)).isZero();
    assertThat(TragoulHealing.drainDamage(0.5D, 2D, 1, 1)).isEqualTo(2D);
  }

  @Test
  void restoredHealthCannotExceedMaximumOrUseInvalidDrain() {
    assertThat(TragoulHealing.restoredHealth(10D, 20D, 2D)).isEqualTo(12D);
    assertThat(TragoulHealing.restoredHealth(19D, 20D, 2D)).isEqualTo(20D);
    assertThat(TragoulHealing.restoredHealth(10D, 20D, -2D)).isEqualTo(10D);
    assertThat(TragoulHealing.restoredHealth(10D, 20D, Double.NaN)).isEqualTo(10D);
  }

  @Test
  void damageSourceResolvesTheLivingEntityWhoCausedAnIndirectStrike() {
    PlayerRef attacker = mock(PlayerRef.class);
    ProjectileRef projectile = mock(ProjectileRef.class);

    assertThat(TragoulHealing.resolveAttacker(attacker, projectile)).isSameAs(attacker);
  }

  @Test
  void projectileFallbackResolvesItsLivingShooterAndRejectsUnknownSources() {
    ProjectileRef projectile = mock(ProjectileRef.class);
    LivingRef shooter = mock(LivingRef.class);
    when(projectile.shooter()).thenReturn(shooter);

    assertThat(TragoulHealing.resolveAttacker(projectile)).isSameAs(shooter);
    assertThat(TragoulHealing.resolveAttacker(mock(ItemEntityRef.class))).isNull();
  }

  @Test
  void reactiveDamageContextBlocksNestedRetaliationAndAlwaysCleansUp() {
    AtomicBoolean observed = new AtomicBoolean();

    TragoulReactiveDamage.apply(() -> {
      observed.set(TragoulReactiveDamage.isActive());
      TragoulReactiveDamage.apply(() -> assertThat(TragoulReactiveDamage.isActive()).isTrue());
      assertThat(TragoulReactiveDamage.isActive()).isTrue();
    });

    assertThat(observed).isTrue();
    assertThat(TragoulReactiveDamage.isActive()).isFalse();

    assertThatThrownBy(() -> TragoulReactiveDamage.apply(() -> {
      throw new IllegalStateException("expected");
    })).isInstanceOf(IllegalStateException.class);

    assertThat(TragoulReactiveDamage.isActive()).isFalse();
  }

  @Test
  void retaliationListensToAllRealDamageAtMonitor() throws NoSuchMethodException {
    Method handler = TragoulHealing.class.getDeclaredMethod("on", EntityDamageEvent.class);
    Handle eventHandler = handler.getAnnotation(Handle.class);

    assertThat(eventHandler.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(eventHandler.ignoreCancelled()).isTrue();
  }
}
