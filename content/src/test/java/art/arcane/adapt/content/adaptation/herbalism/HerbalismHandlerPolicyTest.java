package art.arcane.adapt.content.adaptation.herbalism;

import art.arcane.adapt.api.content.ReceiveCancelledEvents;
import art.arcane.adapt.platform.event.AdaptEvent;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockDropItemEvent;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class HerbalismHandlerPolicyTest {
  @Test
  void indirectActionsObserveProtectionAtMonitorBeforeMutating() throws NoSuchMethodException {
    assertMonitor(HerbalismCompostCascade.class, PlayerInteractEvent.class, true);
    assertMonitor(HerbalismSeedSower.class, PlayerInteractEvent.class, false);
    assertMonitor(HerbalismReplant.class, PlayerInteractEvent.class, true);
    assertMonitor(HerbalismSporeBloom.class, BlockPlaceEvent.class, true);
  }

  @Test
  void everyBlockDropTransferIgnoresAlreadyClaimedDrops() throws NoSuchMethodException {
    Method handler = HerbalismDropToInventory.class.getDeclaredMethod("on", BlockDropItemEvent.class);
    Handle annotation = handler.getAnnotation(Handle.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.ignoreCancelled()).isTrue();
    assertThat(handler.isAnnotationPresent(ReceiveCancelledEvents.class)).isFalse();
  }

  private void assertMonitor(Class<?> listenerType, Class<? extends AdaptEvent> eventType,
                             boolean ignoreCancelled) throws NoSuchMethodException {
    Method method = listenerType.getDeclaredMethod("on", eventType);
    Handle handler = method.getAnnotation(Handle.class);

    assertThat(handler.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(method.isAnnotationPresent(ReceiveCancelledEvents.class)).isEqualTo(!ignoreCancelled);
  }
}
