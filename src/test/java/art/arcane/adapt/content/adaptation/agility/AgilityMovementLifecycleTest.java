package art.arcane.adapt.content.adaptation.agility;

import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgilityMovementLifecycleTest {
  @Test
  void slipstreamTeleportCancellationRestoresCapturedFixedPose() throws Exception {
    AgilitySlipstreamSlide adaptation = new AgilitySlipstreamSlide();
    Player player = mock(Player.class);
    PlayerTeleportEvent event = mock(PlayerTeleportEvent.class);
    UUID id = UUID.randomUUID();
    when(event.getPlayer()).thenReturn(player);
    when(player.getUniqueId()).thenReturn(id);
    when(player.getPose()).thenReturn(Pose.SNEAKING, Pose.SWIMMING);
    when(player.hasFixedPose()).thenReturn(true);
    when(player.isSwimming()).thenReturn(false);

    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(() -> J.runEntity(same(player), any(Runnable.class), eq(1))).thenReturn(true);

      startProne(adaptation, player, 10, 0.9D, null);
      adaptation.on(event);
    }

    verify(player).setPose(Pose.SNEAKING, true);
    assertThat(forcedPoses(adaptation)).isEmpty();
  }

  @Test
  void slipstreamSchedulerRejectionImmediatelyRestoresPose() throws Exception {
    AgilitySlipstreamSlide adaptation = new AgilitySlipstreamSlide();
    Player player = mock(Player.class);
    UUID id = UUID.randomUUID();
    when(player.getUniqueId()).thenReturn(id);
    when(player.getPose()).thenReturn(Pose.STANDING, Pose.SWIMMING);
    when(player.hasFixedPose()).thenReturn(false, true);
    when(player.isSwimming()).thenReturn(false);

    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(() -> J.runEntity(same(player), any(Runnable.class), eq(1))).thenReturn(false);

      startProne(adaptation, player, 10, 0.9D, null);
    }

    InOrder poseOrder = inOrder(player);
    poseOrder.verify(player).setPose(Pose.SWIMMING, true);
    poseOrder.verify(player).setPose(Pose.STANDING, false);
    assertThat(forcedPoses(adaptation)).isEmpty();
  }

  @Test
  void slipstreamUnregisterRestoresEveryTrackedPose() throws Exception {
    AgilitySlipstreamSlide adaptation = new AgilitySlipstreamSlide();
    Player player = mock(Player.class);
    UUID id = UUID.randomUUID();
    when(player.getUniqueId()).thenReturn(id);
    when(player.getPose()).thenReturn(Pose.STANDING, Pose.SWIMMING);
    when(player.hasFixedPose()).thenReturn(false, true);
    when(player.isSwimming()).thenReturn(false);

    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(() -> J.runEntity(same(player), any(Runnable.class), eq(1))).thenReturn(true);
      scheduling.when(() -> J.runEntity(same(player), any(Runnable.class))).thenAnswer(invocation -> {
        Runnable restore = invocation.getArgument(1);
        restore.run();
        return true;
      });

      startProne(adaptation, player, 10, 0.9D, null);
      adaptation.unregister();
    }

    verify(player).setPose(Pose.STANDING, false);
    assertThat(forcedPoses(adaptation)).isEmpty();
  }

  @Test
  void slipstreamMaintenanceFailureRestoresPoseBeforeRethrowing() throws Exception {
    AgilitySlipstreamSlide adaptation = new AgilitySlipstreamSlide();
    Player player = mock(Player.class);
    UUID id = UUID.randomUUID();
    AtomicReference<Runnable> maintenance = new AtomicReference<>();
    when(player.getUniqueId()).thenReturn(id);
    when(player.isOnline()).thenReturn(true);
    when(player.isDead()).thenReturn(false);
    when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
    when(player.getPose()).thenReturn(Pose.STANDING, Pose.SWIMMING);
    when(player.hasFixedPose()).thenReturn(false, true);
    when(player.isSwimming()).thenReturn(false);

    try (MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(() -> J.runEntity(same(player), any(Runnable.class), eq(1))).thenAnswer(invocation -> {
        maintenance.set(invocation.getArgument(1));
        return true;
      });

      startProne(adaptation, player, 10, 0.9D, null);
      doThrow(new IllegalStateException("pose failed")).when(player).setPose(Pose.SWIMMING, true);
      assertThatThrownBy(maintenance.get()::run).isInstanceOf(IllegalStateException.class);
    }

    verify(player).setPose(Pose.STANDING, false);
    assertThat(forcedPoses(adaptation)).isEmpty();
  }

  private static void startProne(AgilitySlipstreamSlide adaptation, Player player, int ticks, double frictionReduction, Attribute frictionAttribute) throws Exception {
    Method method = AgilitySlipstreamSlide.class.getDeclaredMethod("startProne", Player.class, int.class, double.class, Attribute.class);
    method.setAccessible(true);
    method.invoke(adaptation, player, ticks, frictionReduction, frictionAttribute);
  }

  private static Map<?, ?> forcedPoses(AgilitySlipstreamSlide adaptation) throws Exception {
    return field(adaptation, "forcedPoses");
  }

  @SuppressWarnings("unchecked")
  private static <T> T field(Object target, String name) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return (T) field.get(target);
  }
}
