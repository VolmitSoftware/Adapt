package art.arcane.adapt.util.config;

import art.arcane.adapt.SchedulerForwarding;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.fx.SoundCategory;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigHotloadThreadAffinityTest {
  @Test
  void operatorNotificationTouchesPlayerOnlyInsideEntityOwnedTask() {
    PlayerRef player = mock(PlayerRef.class);
    Loc location = mock(Loc.class);
    AtomicReference<Runnable> ownerTask = new AtomicReference<>();
    when(player.isOp()).thenReturn(true);
    when(player.location()).thenReturn(location);

    try (MockedStatic<Tasks> scheduling = SchedulerForwarding.mockTasks()) {
      scheduling.when(() -> Tasks.runEntity(same(player), any(Runnable.class))).thenAnswer(invocation -> {
        ownerTask.set(invocation.getArgument(1));
        return true;
      });

      assertTrue(ConfigHotload.scheduleOperatorNotification(player, List.of("updated"), null));
      verify(player, never()).isOp();
      verify(player, never()).location();
      verify(player, never()).sendMessage(any(String.class));

      ownerTask.get().run();

      verify(player).isOp();
      verify(player).location();
      verify(player).playSound(location, (Id) null, SoundCategory.MASTER, 0.8F, 1.6F);
      verify(player).sendMessage("updated");
    }
  }
}
