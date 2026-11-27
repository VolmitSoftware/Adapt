package art.arcane.adapt.content.adaptation;

import art.arcane.adapt.EngineTestBase;
import art.arcane.adapt.SchedulerForwarding;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.content.adaptation.stealth.StealthSpeed;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.testkit.TestContent;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SneakActivationTimingTest extends EngineTestBase {
  @Test
  void firstOwnerRefreshWaitsForTheCommittedSneakState() throws Exception {
    PlayerRef player = player();
    SimpleAdaptation<?> adaptation = adaptation(player);
    List<Runnable> pending = new ArrayList<>();
    List<Integer> delays = new ArrayList<>();

    try (MockedStatic<Tasks> scheduling = SchedulerForwarding.mockTasks()) {
      scheduling.when(() -> Tasks.runEntity(same(player), any(Runnable.class), anyInt())).thenAnswer(invocation -> {
        pending.add(invocation.getArgument(1));
        delays.add(invocation.getArgument(2));
        return true;
      });

      toggle(adaptation, player, true);

      assertThat(pending).hasSize(1);
      assertThat(delays).containsExactly(1);
      assertThat(states(adaptation).containsKey(player.uuid())).isTrue();
      verify(player, never()).isSneaking();

      when(player.isSneaking()).thenReturn(true);
      pending.removeFirst().run();

      assertThat(states(adaptation).containsKey(player.uuid())).isTrue();
      assertThat(pending).hasSize(1);
      assertThat(delays.get(1)).isPositive();
    }
  }

  @Test
  void rejectedInitialOwnerTaskDoesNotLeaveAnUnrefreshableSession() throws Exception {
    PlayerRef player = player();
    SimpleAdaptation<?> adaptation = adaptation(player);

    try (MockedStatic<Tasks> scheduling = SchedulerForwarding.mockTasks()) {
      toggle(adaptation, player, true);

      scheduling.verify(() -> Tasks.runEntity(same(player), any(Runnable.class), eq(1)));
      assertThat(states(adaptation)).isEmpty();
    }
  }

  @Test
  void cancelledOrRevertedSneakDoesNotStartAMaintenanceLoop() throws Exception {
    PlayerRef player = player();
    SimpleAdaptation<?> adaptation = adaptation(player);
    List<Runnable> pending = new ArrayList<>();

    try (MockedStatic<Tasks> scheduling = SchedulerForwarding.mockTasks()) {
      scheduling.when(() -> Tasks.runEntity(same(player), any(Runnable.class), anyInt())).thenAnswer(invocation -> {
        pending.add(invocation.getArgument(1));
        return true;
      });

      toggle(adaptation, player, true);
      assertThat(pending).hasSize(1);
      pending.removeFirst().run();

      assertThat(states(adaptation)).isEmpty();
      assertThat(pending).isEmpty();
    }
  }

  @Test
  void aStoppedSessionCannotBeResurrectedByItsQueuedInitialRefresh() throws Exception {
    PlayerRef player = player();
    SimpleAdaptation<?> adaptation = adaptation(player);
    List<Runnable> pending = new ArrayList<>();

    try (MockedStatic<Tasks> scheduling = SchedulerForwarding.mockTasks()) {
      scheduling.when(() -> Tasks.runEntity(same(player), any(Runnable.class), anyInt())).thenAnswer(invocation -> {
        pending.add(invocation.getArgument(1));
        return true;
      });

      toggle(adaptation, player, true);
      toggle(adaptation, player, false);
      assertThat(states(adaptation)).isEmpty();
      when(player.isSneaking()).thenReturn(true);
      pending.removeFirst().run();

      assertThat(states(adaptation)).isEmpty();
      assertThat(pending).isEmpty();
    }
  }

  private PlayerRef player() {
    PlayerRef player = mock(PlayerRef.class);
    when(player.uuid()).thenReturn(UUID.randomUUID());
    when(player.isOnline()).thenReturn(true);
    when(player.height()).thenReturn(1.8D);
    when(player.gameMode()).thenReturn(GameMode.SURVIVAL);
    return player;
  }

  private SimpleAdaptation<?> adaptation(PlayerRef player) {
    SimpleAdaptation<?> adaptation = spy(TestContent.adaptation(SkillStealth.ID, StealthSpeed::new));
    doReturn(true).when(adaptation).hasActiveAdaptation(player);
    doReturn(5).when(adaptation).getActiveLevel(player);
    return adaptation;
  }

  private void toggle(SimpleAdaptation<?> adaptation, PlayerRef player, boolean sneaking) throws Exception {
    Method handler = adaptation.getClass().getMethod("on", PlayerToggleSneakEvent.class);
    handler.invoke(adaptation, new PlayerToggleSneakEvent(player, sneaking));
  }

  private Map<?, ?> states(SimpleAdaptation<?> adaptation) throws ReflectiveOperationException {
    Field field = StealthSpeed.class.getDeclaredField("states");
    field.setAccessible(true);
    return (Map<?, ?>) field.get(adaptation);
  }
}
