package art.arcane.adapt.content.adaptation.tragoul;

import org.bukkit.damage.DamageSource;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
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
    DamageSource source = mock(DamageSource.class);
    Player attacker = mock(Player.class);
    Projectile projectile = mock(Projectile.class);
    when(source.getCausingEntity()).thenReturn(attacker);
    when(source.getDirectEntity()).thenReturn(projectile);

    assertThat(TragoulHealing.resolveAttacker(source)).isSameAs(attacker);
  }

  @Test
  void projectileFallbackResolvesItsLivingShooterAndRejectsUnknownSources() {
    Projectile projectile = mock(Projectile.class);
    LivingEntity shooter = mock(LivingEntity.class);
    when(projectile.getShooter()).thenReturn(shooter);

    assertThat(TragoulHealing.resolveAttacker(projectile)).isSameAs(shooter);
    assertThat(TragoulHealing.resolveAttacker(mock(org.bukkit.entity.Item.class))).isNull();
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
    EventHandler eventHandler = handler.getAnnotation(EventHandler.class);

    assertThat(eventHandler.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(eventHandler.ignoreCancelled()).isTrue();
  }
}
