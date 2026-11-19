package art.arcane.adapt.bukkit.platform.entity;

import art.arcane.adapt.platform.event.entity.EntityRegainHealthEvent.Reason;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.IronGolem;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.UUID;
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

/** The copper golem is newer than this server, so the iron golem stands in as the healed entity. */
class BukkitLivingRefRegainHealthTest {
  private final PluginManager plugins = mock(PluginManager.class);
  private final AtomicReference<Double> health = new AtomicReference<>(6D);
  private final IronGolem golem = mock(IronGolem.class);
  private MockedStatic<Bukkit> bukkit;
  private BukkitLivingRef living;

  @BeforeEach
  void setUp() {
    bukkit = mockStatic(Bukkit.class);
    bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
    lenient().when(golem.getUniqueId()).thenReturn(UUID.randomUUID());
    lenient().when(golem.getType()).thenReturn(EntityType.IRON_GOLEM);
    lenient().when(golem.getHealth()).thenAnswer(invocation -> health.get());
    lenient().doAnswer(invocation -> {
      health.set(invocation.getArgument(0));
      return null;
    }).when(golem).setHealth(anyDouble());
    living = new BukkitLivingRef(golem) {
      @Override
      public double maxHealth() {
        return 12D;
      }
    };
  }

  @AfterEach
  void tearDown() {
    bukkit.close();
  }

  @Test
  void healsFireARegainEventAndStopAtTheMaximum() {
    assertThat(living.regainHealth(4D, Reason.CUSTOM)).isEqualTo(4D);
    assertThat(health.get()).isEqualTo(10D);
    assertThat(living.regainHealth(4D, Reason.REGEN)).isEqualTo(2D);
    assertThat(health.get()).isEqualTo(12D);

    ArgumentCaptor<EntityRegainHealthEvent> fired = ArgumentCaptor.forClass(EntityRegainHealthEvent.class);
    verify(plugins, times(2)).callEvent(fired.capture());
    assertThat(fired.getAllValues().getFirst().getRegainReason()).isEqualTo(RegainReason.CUSTOM);
    assertThat(fired.getAllValues().get(1).getRegainReason()).isEqualTo(RegainReason.REGEN);
  }

  @Test
  void listenersCanChangeOrCancelTheHeal() {
    answerRegain(event -> event.setAmount(event.getAmount() + 1D));
    assertThat(living.regainHealth(2D, Reason.CUSTOM)).isEqualTo(3D);

    answerRegain(event -> event.setCancelled(true));
    assertThat(living.regainHealth(2D, Reason.CUSTOM)).isZero();
    assertThat(health.get()).isEqualTo(9D);
  }

  @Test
  void fullDeadOrEmptyHealsDoNothing() {
    health.set(12D);
    assertThat(living.regainHealth(3D, Reason.CUSTOM)).isZero();
    health.set(5D);
    assertThat(living.regainHealth(0D, Reason.CUSTOM)).isZero();
    assertThat(living.regainHealth(Double.NaN, Reason.CUSTOM)).isZero();
    health.set(0D);
    assertThat(living.regainHealth(3D, Reason.CUSTOM)).isZero();

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
