package art.arcane.adapt.engine;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.SchedulerForwarding;
import art.arcane.adapt.engine.component.ComponentSupport;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ComponentSafeGiveProtectionTest {
  @Test
  void pickupListenerStackGrowthCannotDeleteTheExtraItems() {
    PlayerRef player = mock(PlayerRef.class);
    PlayerInventoryRef inventory = mock(PlayerInventoryRef.class);
    ItemEntityRef item = mock(ItemEntityRef.class);
    ItemRef snapshot = mock(ItemRef.class);
    ItemRef expanded = mock(ItemRef.class);
    ComponentPort port = mock(ComponentPort.class);
    when(player.inventory()).thenReturn(inventory);
    when(item.isValid()).thenReturn(true);
    when(item.isDead()).thenReturn(false);
    when(item.item()).thenReturn(expanded);
    when(snapshot.type()).thenReturn(Id.minecraft("stone"));
    when(snapshot.amount()).thenReturn(1);
    when(expanded.isSimilar(snapshot)).thenReturn(true);
    when(expanded.amount()).thenReturn(2);
    when(port.attemptItemPickup(player, item, snapshot)).thenReturn(true);

    boolean transferred;
    AdaptPorts.install(ComponentPort.class, port);
    try (MockedStatic<Tasks> scheduling = SchedulerForwarding.mockTasks()) {
      scheduling.when(Tasks::isFoliaThreading).thenReturn(false);

      transferred = ComponentSupport.safeGiveItem(player, item, snapshot);
    } finally {
      AdaptPorts.uninstall(ComponentPort.class);
    }

    assertThat(transferred).isFalse();
    verify(inventory, never()).addItem(any(ItemRef.class));
    verify(item, never()).remove();
  }
}
