package art.arcane.adapt.content.adaptation;

import art.arcane.adapt.content.adaptation.architect.ArchitectPlacement;
import art.arcane.adapt.content.adaptation.architect.ArchitectWirelessRedstone;
import art.arcane.adapt.content.adaptation.axe.AxeChop;
import art.arcane.adapt.content.adaptation.crafting.CraftingDeconstruction;
import art.arcane.adapt.content.adaptation.excavation.ExcavationBurrow;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismCompostCascade;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismReplant;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismSeedSower;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismSporeBloom;
import art.arcane.adapt.content.adaptation.pickaxe.PickaxeChisel;
import art.arcane.adapt.content.adaptation.seaborrne.SeaborneCoralGardener;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class ProtectionHandlerPriorityTest {
  @Test
  void indirectActionsObserveProtectionAtMonitorBeforeMutating() throws NoSuchMethodException {
    assertMonitor(ArchitectPlacement.class, BlockPlaceEvent.class, true);
    assertMonitor(CraftingDeconstruction.class, PlayerInteractEvent.class, true);
    assertMonitor(HerbalismCompostCascade.class, PlayerInteractEvent.class, true);
    assertMonitor(HerbalismSeedSower.class, PlayerInteractEvent.class, false);
    assertMonitor(SeaborneCoralGardener.class, PlayerInteractEvent.class, true);
    assertMonitor(ArchitectWirelessRedstone.class, "onPlayerInteract", PlayerInteractEvent.class, false);
    assertMonitor(AxeChop.class, PlayerInteractEvent.class, true);
    assertMonitor(ExcavationBurrow.class, PlayerInteractEvent.class, false);
    assertMonitor(HerbalismReplant.class, PlayerInteractEvent.class, true);
    assertMonitor(HerbalismSporeBloom.class, BlockPlaceEvent.class, true);
    assertMonitor(PickaxeChisel.class, PlayerInteractEvent.class, true);
  }

  private void assertMonitor(Class<?> listenerType, Class<? extends Event> eventType,
                             boolean ignoreCancelled) throws NoSuchMethodException {
    assertMonitor(listenerType, "on", eventType, ignoreCancelled);
  }

  private void assertMonitor(Class<?> listenerType, String methodName,
                             Class<? extends Event> eventType,
                             boolean ignoreCancelled) throws NoSuchMethodException {
    Method method = listenerType.getDeclaredMethod(methodName, eventType);
    EventHandler handler = method.getAnnotation(EventHandler.class);

    assertThat(handler.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(handler.ignoreCancelled()).isEqualTo(ignoreCancelled);
  }
}
