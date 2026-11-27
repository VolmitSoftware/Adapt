package art.arcane.adapt.content.golem;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.event.entity.EntityRegainHealthEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class GolemVitalsTest {
  private final AtomicReference<Double> health = new AtomicReference<>(6D);
  private final MobRef golem = mock(MobRef.class);
  private final List<EntityRegainHealthEvent> fired = new ArrayList<>();
  private Consumer<EntityRegainHealthEvent> listener = event -> {
  };

  @BeforeEach
  void setUp() {
    lenient().when(golem.attributeValue(Attributes.MAX_HEALTH)).thenReturn(12D);
    lenient().when(golem.health()).thenAnswer(invocation -> health.get());
    lenient().doAnswer(invocation -> {
      health.set(invocation.getArgument(0));
      return null;
    }).when(golem).setHealth(anyDouble());
    lenient().when(golem.regainHealth(anyDouble(), any())).thenAnswer(invocation -> regain(invocation.getArgument(0), invocation.getArgument(1)));
  }

  @Test
  void healsFireARegainEventAndStopAtTheMaximum() {
    assertThat(GolemVitals.heal(golem, 4D, EntityRegainHealthEvent.Reason.CUSTOM)).isEqualTo(4D);
    assertThat(health.get()).isEqualTo(10D);
    assertThat(GolemVitals.heal(golem, 4D, EntityRegainHealthEvent.Reason.REGEN)).isEqualTo(2D);
    assertThat(health.get()).isEqualTo(12D);
    assertThat(GolemVitals.fullHealth(golem)).isTrue();

    assertThat(fired).hasSize(2);
    assertThat(fired.getFirst().reason()).isEqualTo(EntityRegainHealthEvent.Reason.CUSTOM);
    assertThat(fired.get(1).reason()).isEqualTo(EntityRegainHealthEvent.Reason.REGEN);
  }

  @Test
  void listenersCanChangeOrCancelTheHeal() {
    listener = event -> event.setAmount(event.amount() + 1D);
    assertThat(GolemVitals.heal(golem, 2D, EntityRegainHealthEvent.Reason.CUSTOM)).isEqualTo(3D);

    listener = event -> event.setCancelled(true);
    assertThat(GolemVitals.heal(golem, 2D, EntityRegainHealthEvent.Reason.CUSTOM)).isZero();
    assertThat(health.get()).isEqualTo(9D);
  }

  @Test
  void fullDeadOrEmptyHealsDoNothing() {
    health.set(12D);
    assertThat(GolemVitals.heal(golem, 3D, EntityRegainHealthEvent.Reason.CUSTOM)).isZero();
    health.set(5D);
    assertThat(GolemVitals.heal(golem, 0D, EntityRegainHealthEvent.Reason.CUSTOM)).isZero();
    assertThat(GolemVitals.heal(golem, Double.NaN, EntityRegainHealthEvent.Reason.CUSTOM)).isZero();
    health.set(0D);
    assertThat(GolemVitals.heal(golem, 3D, EntityRegainHealthEvent.Reason.CUSTOM)).isZero();

    assertThat(fired).isEmpty();
    verify(golem, never()).setHealth(anyDouble());
  }

  private double regain(double amount, EntityRegainHealthEvent.Reason reason) {
    double current = health.get();
    double maximum = GolemVitals.maxHealth(golem);
    if (!Double.isFinite(amount) || amount <= 0D || current <= 0D || current >= maximum) {
      return 0D;
    }
    EntityRegainHealthEvent event = new EntityRegainHealthEvent(golem, amount, reason);
    fired.add(event);
    listener.accept(event);
    if (event.isCancelled() || event.amount() <= 0D) {
      return 0D;
    }
    double next = Math.min(maximum, current + event.amount());
    golem.setHealth(next);
    return next - current;
  }
}
