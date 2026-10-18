package art.arcane.adapt.api.golem;

import org.bukkit.Bukkit;
import org.bukkit.entity.CopperGolem;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class GolemVitalsTest {
  private final PluginManager plugins = mock(PluginManager.class);
  private final AtomicReference<Double> health = new AtomicReference<>(6D);
  private final CopperGolem golem = mock(CopperGolem.class);
  private MockedStatic<GolemVitals> vitals;
  private MockedStatic<Bukkit> bukkit;

  @BeforeEach
  void setUp() {
    vitals = mockStatic(GolemVitals.class);
    vitals.when(() -> GolemVitals.maxHealth(any())).thenReturn(12D);
    vitals.when(() -> GolemVitals.heal(any(), anyDouble(), any())).thenCallRealMethod();
    vitals.when(() -> GolemVitals.fullHealth(any())).thenCallRealMethod();
    bukkit = mockStatic(Bukkit.class);
    bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
    lenient().when(golem.getHealth()).thenAnswer(invocation -> health.get());
    lenient().doAnswer(invocation -> {
      health.set(invocation.getArgument(0));
      return null;
    }).when(golem).setHealth(anyDouble());
  }

  @AfterEach
  void tearDown() {
    bukkit.close();
    vitals.close();
  }

  @Test
  void healsFireARegainEventAndStopAtTheMaximum() {
    assertThat(GolemVitals.heal(golem, 4D, RegainReason.CUSTOM)).isEqualTo(4D);
    assertThat(health.get()).isEqualTo(10D);
    assertThat(GolemVitals.heal(golem, 4D, RegainReason.REGEN)).isEqualTo(2D);
    assertThat(health.get()).isEqualTo(12D);
    assertThat(GolemVitals.fullHealth(golem)).isTrue();

    ArgumentCaptor<EntityRegainHealthEvent> fired = ArgumentCaptor.forClass(EntityRegainHealthEvent.class);
    verify(plugins, times(2)).callEvent(fired.capture());
    assertThat(fired.getAllValues().getFirst().getRegainReason()).isEqualTo(RegainReason.CUSTOM);
    assertThat(fired.getAllValues().get(1).getRegainReason()).isEqualTo(RegainReason.REGEN);
  }

  @Test
  void listenersCanChangeOrCancelTheHeal() {
    answerRegain(event -> event.setAmount(event.getAmount() + 1D));
    assertThat(GolemVitals.heal(golem, 2D, RegainReason.CUSTOM)).isEqualTo(3D);

    answerRegain(event -> event.setCancelled(true));
    assertThat(GolemVitals.heal(golem, 2D, RegainReason.CUSTOM)).isZero();
    assertThat(health.get()).isEqualTo(9D);
  }

  @Test
  void fullDeadOrEmptyHealsDoNothing() {
    health.set(12D);
    assertThat(GolemVitals.heal(golem, 3D, RegainReason.CUSTOM)).isZero();
    health.set(5D);
    assertThat(GolemVitals.heal(golem, 0D, RegainReason.CUSTOM)).isZero();
    assertThat(GolemVitals.heal(golem, Double.NaN, RegainReason.CUSTOM)).isZero();
    health.set(0D);
    assertThat(GolemVitals.heal(golem, 3D, RegainReason.CUSTOM)).isZero();

    verify(plugins, never()).callEvent(any());
    verify(golem, never()).setHealth(anyDouble());
  }

  private void answerRegain(Consumer<EntityRegainHealthEvent> listener) {
    doAnswer(invocation -> {
      listener.accept(invocation.getArgument(0));
      return null;
    }).when(plugins).callEvent(any());
  }
}
