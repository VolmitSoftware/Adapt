package art.arcane.adapt.content.adaptation;

import art.arcane.adapt.api.content.ReceiveCancelledEvents;
import art.arcane.adapt.content.adaptation.architect.ArchitectPlacement;
import art.arcane.adapt.content.adaptation.architect.ArchitectWirelessRedstone;
import art.arcane.adapt.content.adaptation.crafting.CraftingDeconstruction;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneCoralGardener;
import art.arcane.adapt.platform.event.Handle;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class ProtectionHandlerPriorityTest {
  @Test
  void indirectActionsObserveProtectionAtMonitorBeforeMutating() throws NoSuchMethodException {
    Method placement = ArchitectPlacement.class.getDeclaredMethod("on",
        art.arcane.adapt.platform.event.block.BlockPlaceEvent.class);
    assertThat(placement.getAnnotation(Handle.class).priority())
        .isEqualTo(art.arcane.adapt.platform.event.EventPriority.MONITOR);
    assertThat(placement.getAnnotation(Handle.class).ignoreCancelled()).isTrue();
    assertThat(placement.isAnnotationPresent(ReceiveCancelledEvents.class)).isFalse();
    Method deconstruction = CraftingDeconstruction.class.getDeclaredMethod("on",
        art.arcane.adapt.platform.event.player.PlayerInteractEvent.class);
    assertThat(deconstruction.getAnnotation(Handle.class).priority())
        .isEqualTo(art.arcane.adapt.platform.event.EventPriority.MONITOR);
    assertThat(deconstruction.getAnnotation(Handle.class).ignoreCancelled()).isTrue();
    Method coralGardener = SeaborneCoralGardener.class.getDeclaredMethod("on",
        art.arcane.adapt.platform.event.player.PlayerInteractEvent.class);
    assertThat(coralGardener.getAnnotation(Handle.class).priority())
        .isEqualTo(art.arcane.adapt.platform.event.EventPriority.MONITOR);
    assertThat(coralGardener.getAnnotation(Handle.class).ignoreCancelled()).isTrue();
    Method wireless = ArchitectWirelessRedstone.class.getDeclaredMethod("onPlayerInteract",
        art.arcane.adapt.platform.event.player.PlayerInteractEvent.class);
    assertThat(wireless.getAnnotation(Handle.class).priority())
        .isEqualTo(art.arcane.adapt.platform.event.EventPriority.MONITOR);
    assertThat(wireless.isAnnotationPresent(ReceiveCancelledEvents.class)).isTrue();
  }
}
