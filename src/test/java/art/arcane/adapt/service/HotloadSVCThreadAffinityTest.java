package art.arcane.adapt.service;

import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HotloadSVCThreadAffinityTest {
  @Test
  void operatorNotificationTouchesPlayerOnlyInsideEntityOwnedTask() {
    Player player = mock(Player.class);
    Location location = mock(Location.class);
    AtomicReference<Runnable> ownerTask = new AtomicReference<>();
    when(player.isOp()).thenReturn(true);
    when(player.getLocation()).thenReturn(location);

    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(() -> J.runEntity(same(player), any(Runnable.class))).thenAnswer(invocation -> {
        ownerTask.set(invocation.getArgument(1));
        return true;
      });

      assertTrue(HotloadSVC.scheduleOperatorNotification(player, List.of("updated"), null));
      verify(player, never()).isOp();
      verify(player, never()).getLocation();
      verify(player, never()).sendMessage(any(String.class));

      ownerTask.get().run();

      verify(player).isOp();
      verify(player).getLocation();
      verify(player).playSound(location, (Sound) null, 0.8F, 1.6F);
      verify(player).sendMessage("updated");
    }
  }
}
