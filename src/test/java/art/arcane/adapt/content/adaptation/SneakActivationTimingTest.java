package art.arcane.adapt.content.adaptation;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.content.adaptation.blocking.BlockingBastionStance;
import art.arcane.adapt.content.adaptation.stealth.StealthSpeed;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SneakActivationTimingTest extends AdaptTestBase {
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void firstOwnerRefreshWaitsForTheCommittedSneakState(boolean stealth) throws Exception {
    Player player = player();
    SimpleAdaptation<?> adaptation = adaptation(stealth, player);
    List<Runnable> pending = new ArrayList<>();
    List<Integer> delays = new ArrayList<>();

    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(() -> J.runEntity(same(player), any(Runnable.class), anyInt())).thenAnswer(invocation -> {
        pending.add(invocation.getArgument(1));
        delays.add(invocation.getArgument(2));
        return true;
      });

      toggle(adaptation, player, true);

      assertThat(pending).hasSize(1);
      assertThat(delays).containsExactly(1);
      assertThat(states(adaptation, stealth).containsKey(player.getUniqueId())).isTrue();
      verify(player, never()).isSneaking();

      when(player.isSneaking()).thenReturn(true);
      pending.removeFirst().run();

      assertThat(states(adaptation, stealth).containsKey(player.getUniqueId())).isTrue();
      assertThat(pending).hasSize(1);
      assertThat(delays.get(1)).isPositive();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void rejectedInitialOwnerTaskDoesNotLeaveAnUnrefreshableSession(boolean stealth) throws Exception {
    Player player = player();
    SimpleAdaptation<?> adaptation = adaptation(stealth, player);

    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      toggle(adaptation, player, true);

      scheduling.verify(() -> J.runEntity(same(player), any(Runnable.class), eq(1)));
      assertThat(states(adaptation, stealth)).isEmpty();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void cancelledOrRevertedSneakDoesNotStartAMaintenanceLoop(boolean stealth) throws Exception {
    Player player = player();
    SimpleAdaptation<?> adaptation = adaptation(stealth, player);
    List<Runnable> pending = new ArrayList<>();

    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(() -> J.runEntity(same(player), any(Runnable.class), anyInt())).thenAnswer(invocation -> {
        pending.add(invocation.getArgument(1));
        return true;
      });

      toggle(adaptation, player, true);
      assertThat(pending).hasSize(1);
      pending.removeFirst().run();

      assertThat(states(adaptation, stealth)).isEmpty();
      assertThat(pending).isEmpty();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void aStoppedSessionCannotBeResurrectedByItsQueuedInitialRefresh(boolean stealth) throws Exception {
    Player player = player();
    SimpleAdaptation<?> adaptation = adaptation(stealth, player);
    List<Runnable> pending = new ArrayList<>();

    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(() -> J.runEntity(same(player), any(Runnable.class), anyInt())).thenAnswer(invocation -> {
        pending.add(invocation.getArgument(1));
        return true;
      });

      toggle(adaptation, player, true);
      toggle(adaptation, player, false);
      assertThat(states(adaptation, stealth)).isEmpty();
      when(player.isSneaking()).thenReturn(true);
      pending.removeFirst().run();

      assertThat(states(adaptation, stealth)).isEmpty();
      assertThat(pending).isEmpty();
    }
  }

  private Player player() {
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    when(player.isOnline()).thenReturn(true);
    when(player.getHeight()).thenReturn(1.8D);
    when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
    return player;
  }

  private SimpleAdaptation<?> adaptation(boolean stealth, Player player) {
    SimpleAdaptation<?> adaptation = stealth ? spy(new StealthSpeed()) : spy(new BlockingBastionStance());
    doReturn(true).when(adaptation).hasActiveAdaptation(player);
    doReturn(5).when(adaptation).getActiveLevel(player);
    return adaptation;
  }

  private void toggle(SimpleAdaptation<?> adaptation, Player player, boolean sneaking) throws Exception {
    Method handler = adaptation.getClass().getMethod("on", PlayerToggleSneakEvent.class);
    handler.invoke(adaptation, new PlayerToggleSneakEvent(player, sneaking));
  }

  private Map<?, ?> states(SimpleAdaptation<?> adaptation, boolean stealth) throws ReflectiveOperationException {
    Class<?> type = stealth ? StealthSpeed.class : BlockingBastionStance.class;
    Field field = type.getDeclaredField(stealth ? "states" : "stanceStates");
    field.setAccessible(true);
    return (Map<?, ?>) field.get(adaptation);
  }
}
