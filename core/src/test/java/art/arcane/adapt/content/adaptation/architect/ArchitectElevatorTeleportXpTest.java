package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArchitectElevatorTeleportXpTest extends AdaptTestBase {
  private final WorldRef world = mock(WorldRef.class);

  @ParameterizedTest
  @CsvSource({"true, true", "false, true", "true, false", "false, false"})
  void elevatorRewardsOnlyCompletedTeleportsForOnlinePlayers(boolean success, boolean online) throws Exception {
    ArchitectElevator elevator = mock(ArchitectElevator.class, RETURNS_DEEP_STUBS);
    PlayerRef player = player(online);
    when(elevator.getLevel(player)).thenReturn(1);
    Method finish = ArchitectElevator.class.getDeclaredMethod("finishElevatorTeleport", PlayerRef.class, int.class, Boolean.class, Throwable.class);
    finish.setAccessible(true);
    try (MockedStatic<Tasks> scheduler = immediateScheduler(player)) {
      finish.invoke(elevator, player, 5, success, null);
    }
    verify(elevator, times(success && online ? 1 : 0)).awardUsageBaselineXp(player, 1);
  }

  private PlayerRef player(boolean online) {
    PlayerRef player = mock(PlayerRef.class);
    when(player.uuid()).thenReturn(UUID.randomUUID());
    when(player.isOnline()).thenReturn(online);
    Loc location = new Loc(world, 0D, 70D, 0D);
    when(player.location()).thenReturn(location);
    return player;
  }

  private MockedStatic<Tasks> immediateScheduler(PlayerRef player) {
    MockedStatic<Tasks> scheduler = mockStatic(Tasks.class);
    scheduler.when(() -> Tasks.runEntity(eq(player), any(Runnable.class))).thenAnswer(invocation -> {
      Runnable callback = invocation.getArgument(1);
      callback.run();
      return true;
    });
    return scheduler;
  }
}
