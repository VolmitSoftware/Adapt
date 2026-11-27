package art.arcane.adapt.content.adaptation.agility;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.SchedulerForwarding;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.Pose;
import art.arcane.adapt.platform.event.player.PlayerTeleportEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.TestContent;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgilityMovementLifecycleTest extends AdaptTestBase {
  @Test
  void slipstreamTeleportCancellationRestoresCapturedFixedPose() throws Exception {
    AgilitySlipstreamSlide adaptation = TestContent.adaptation(Id.adapt("agility"), AgilitySlipstreamSlide::new);
    PlayerRef player = mock(PlayerRef.class);
    PlayerTeleportEvent event = mock(PlayerTeleportEvent.class);
    UUID id = UUID.randomUUID();
    when(event.player()).thenReturn(player);
    when(player.uuid()).thenReturn(id);
    when(player.pose()).thenReturn(Pose.SNEAKING, Pose.SWIMMING);
    when(player.hasFixedPose()).thenReturn(true);
    when(player.isSwimming()).thenReturn(false);

    try (MockedStatic<Tasks> scheduling = SchedulerForwarding.mockTasks()) {
      scheduling.when(() -> Tasks.runEntity(same(player), any(Runnable.class), eq(1))).thenReturn(true);

      slide(adaptation).start(player, 10, 0.9D, null);
      adaptation.on(event);
    }

    verify(player).setPose(Pose.SNEAKING, true);
    assertThat(slide(adaptation).sliding(id)).isFalse();
  }

  @Test
  void slipstreamSchedulerRejectionImmediatelyRestoresPose() {
    AgilitySlide slide = new AgilitySlide(mock(Adaptation.class));
    PlayerRef player = mock(PlayerRef.class);
    UUID id = UUID.randomUUID();
    when(player.uuid()).thenReturn(id);
    when(player.pose()).thenReturn(Pose.STANDING, Pose.SWIMMING);
    when(player.hasFixedPose()).thenReturn(false, true);
    when(player.isSwimming()).thenReturn(false);

    try (MockedStatic<Tasks> scheduling = SchedulerForwarding.mockTasks()) {
      scheduling.when(() -> Tasks.runEntity(same(player), any(Runnable.class), eq(1))).thenReturn(false);

      slide.start(player, 10, 0.9D, null);
    }

    InOrder poseOrder = inOrder(player);
    poseOrder.verify(player).setPose(Pose.SWIMMING, true);
    poseOrder.verify(player).setPose(Pose.STANDING, false);
    assertThat(slide.sliding(id)).isFalse();
  }

  @Test
  void slipstreamUnregisterRestoresEveryTrackedPose() throws Exception {
    AgilitySlipstreamSlide adaptation = TestContent.adaptation(Id.adapt("agility"), AgilitySlipstreamSlide::new);
    PlayerRef player = mock(PlayerRef.class);
    UUID id = UUID.randomUUID();
    when(player.uuid()).thenReturn(id);
    when(player.pose()).thenReturn(Pose.STANDING, Pose.SWIMMING);
    when(player.hasFixedPose()).thenReturn(false, true);
    when(player.isSwimming()).thenReturn(false);

    try (MockedStatic<Tasks> scheduling = SchedulerForwarding.mockTasks()) {
      scheduling.when(() -> Tasks.runEntity(same(player), any(Runnable.class), eq(1))).thenReturn(true);
      scheduling.when(() -> Tasks.runEntity(same(player), any(Runnable.class))).thenAnswer(invocation -> {
        Runnable restore = invocation.getArgument(1);
        restore.run();
        return true;
      });

      slide(adaptation).start(player, 10, 0.9D, null);
      adaptation.unregister();
    }

    verify(player).setPose(Pose.STANDING, false);
    assertThat(slide(adaptation).sliding(id)).isFalse();
  }

  @Test
  @SuppressWarnings("unchecked")
  void slipstreamMaintenanceFailureRestoresPoseBeforeRethrowing() {
    Adaptation<AdaptationConfig> adaptation = mock(Adaptation.class);
    AgilitySlide slide = new AgilitySlide(adaptation);
    PlayerRef player = mock(PlayerRef.class);
    UUID id = UUID.randomUUID();
    AtomicReference<Runnable> maintenance = new AtomicReference<>();
    when(player.uuid()).thenReturn(id);
    when(player.isOnline()).thenReturn(true);
    when(player.isDead()).thenReturn(false);
    when(player.gameMode()).thenReturn(GameMode.SURVIVAL);
    doReturn(true).when(adaptation).hasActiveAdaptation(player);
    doReturn(AgilitySlipstreamSlide.Control.TAP).when(adaptation).preference(player, AgilitySlipstreamSlide.CONTROL);
    when(player.pose()).thenReturn(Pose.STANDING, Pose.SWIMMING);
    when(player.hasFixedPose()).thenReturn(false, true);
    when(player.isSwimming()).thenReturn(false);

    try (MockedStatic<Tasks> scheduling = SchedulerForwarding.mockTasks()) {
      scheduling.when(() -> Tasks.runEntity(same(player), any(Runnable.class), eq(1))).thenAnswer(invocation -> {
        maintenance.set(invocation.getArgument(1));
        return true;
      });

      slide.start(player, 10, 0.9D, null);
      doThrow(new IllegalStateException("pose failed")).when(player).setPose(Pose.SWIMMING, true);
      assertThatThrownBy(maintenance.get()::run).isInstanceOf(IllegalStateException.class);
    }

    verify(player).setPose(Pose.STANDING, false);
    assertThat(slide.sliding(id)).isFalse();
  }

  private static AgilitySlide slide(AgilitySlipstreamSlide adaptation) throws Exception {
    return field(adaptation, "slide");
  }

  @SuppressWarnings("unchecked")
  private static <T> T field(Object target, String name) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return (T) field.get(target);
  }
}
