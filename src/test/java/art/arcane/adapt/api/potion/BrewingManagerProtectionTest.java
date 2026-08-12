package art.arcane.adapt.api.potion;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BrewingStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BrewingManagerProtectionTest {
  @Test
  void inventoryClickHandlerIgnoresCancelledEventsAtRegistration() throws NoSuchMethodException {
    Method method = BrewingManager.class.getDeclaredMethod("onInventoryClick", InventoryClickEvent.class);
    Method monitor = BrewingManager.class.getDeclaredMethod("afterInventoryClick", InventoryClickEvent.class);

    EventHandler annotation = method.getAnnotation(EventHandler.class);
    EventHandler monitorAnnotation = monitor.getAnnotation(EventHandler.class);
    assertThat(annotation.ignoreCancelled()).isTrue();
    assertThat(annotation.priority()).isEqualTo(EventPriority.HIGHEST);
    assertThat(monitorAnnotation.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(monitorAnnotation.ignoreCancelled()).isFalse();
  }

  @Test
  void alreadyCancelledClickReturnsBeforeInspectingTheView() {
    InventoryClickEvent event = mock(InventoryClickEvent.class);
    when(event.isCancelled()).thenReturn(true);

    new BrewingManager().onInventoryClick(event);

    verify(event, never()).getView();
  }

  @Test
  void delayedProcessingStopsWhenPlayerHasOpenedAnotherInventory() {
    BrewingManager manager = new BrewingManager();
    InventoryClickEvent event = mock(InventoryClickEvent.class);
    InventoryView sourceView = mock(InventoryView.class);
    InventoryView replacementView = mock(InventoryView.class);
    BrewerInventory brewingInventory = mock(BrewerInventory.class);
    Inventory replacementInventory = mock(Inventory.class);
    BrewingStand stand = mock(BrewingStand.class);
    Block standBlock = mock(Block.class);
    Player player = mock(Player.class);

    when(event.getView()).thenReturn(sourceView);
    when(sourceView.getTopInventory()).thenReturn(brewingInventory);
    when(brewingInventory.getHolder()).thenReturn(stand);
    when(stand.getBlock()).thenReturn(standBlock);
    when(event.getWhoClicked()).thenReturn(player);
    when(player.getOpenInventory()).thenReturn(replacementView);
    when(replacementView.getTopInventory()).thenReturn(replacementInventory);

    try (MockedStatic<Adapt> adapt = mockStatic(Adapt.class);
         MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(() -> J.runEntity(same(player), any(Runnable.class), eq(1))).thenAnswer(invocation -> {
        invocation.<Runnable>getArgument(1).run();
        return true;
      });

      manager.onInventoryClick(event);
      manager.afterInventoryClick(event);
    }

    verify(standBlock, never()).getLocation();
  }

  @Test
  void cancellationAfterTheHighestHandlerPreventsDelayedProcessing() {
    BrewingManager manager = new BrewingManager();
    InventoryClickEvent event = mock(InventoryClickEvent.class);
    InventoryView sourceView = mock(InventoryView.class);
    BrewerInventory brewingInventory = mock(BrewerInventory.class);
    BrewingStand stand = mock(BrewingStand.class);
    Block standBlock = mock(Block.class);
    Player player = mock(Player.class);
    when(event.isCancelled()).thenReturn(false, true);
    when(event.getView()).thenReturn(sourceView);
    when(sourceView.getTopInventory()).thenReturn(brewingInventory);
    when(brewingInventory.getHolder()).thenReturn(stand);
    when(stand.getBlock()).thenReturn(standBlock);
    when(event.getWhoClicked()).thenReturn(player);

    try (MockedStatic<Adapt> adapt = mockStatic(Adapt.class);
         MockedStatic<J> scheduling = mockStatic(J.class)) {
      manager.onInventoryClick(event);
      manager.afterInventoryClick(event);

      scheduling.verify(
          () -> J.runEntity(same(player), any(Runnable.class), eq(1)),
          never()
      );
    }
  }

  @Test
  void sameInventoryWithDifferentStandIsRejected() {
    Player player = mock(Player.class);
    InventoryView view = mock(InventoryView.class);
    BrewerInventory inventory = mock(BrewerInventory.class);
    BrewingStand currentStand = mock(BrewingStand.class);
    Block expectedBlock = mock(Block.class);
    Block currentBlock = mock(Block.class);
    when(player.getOpenInventory()).thenReturn(view);
    when(view.getTopInventory()).thenReturn(inventory);
    when(inventory.getHolder()).thenReturn(currentStand);
    when(currentStand.getBlock()).thenReturn(currentBlock);

    assertThat(BrewingManager.isSameOpenStand(player, inventory, expectedBlock)).isFalse();
  }

  @Test
  void delayedProcessingStopsBeforeInventoryAccessAfterFoliaRegionChange() {
    BrewingManager manager = new BrewingManager();
    InventoryClickEvent event = mock(InventoryClickEvent.class);
    InventoryView sourceView = mock(InventoryView.class);
    BrewerInventory brewingInventory = mock(BrewerInventory.class);
    BrewingStand stand = mock(BrewingStand.class);
    Block standBlock = mock(Block.class);
    Location standLocation = mock(Location.class);
    Player player = mock(Player.class);
    AtomicReference<Runnable> delayedTask = new AtomicReference<>();

    when(event.getView()).thenReturn(sourceView);
    when(sourceView.getTopInventory()).thenReturn(brewingInventory);
    when(brewingInventory.getHolder()).thenReturn(stand);
    when(stand.getBlock()).thenReturn(standBlock);
    when(standBlock.getLocation()).thenReturn(standLocation);
    when(event.getWhoClicked()).thenReturn(player);

    try (MockedStatic<Adapt> adapt = mockStatic(Adapt.class);
         MockedStatic<J> scheduling = mockStatic(J.class)) {
      scheduling.when(J::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> J.isOwnedByCurrentRegion(standLocation)).thenReturn(false);
      scheduling.when(() -> J.runEntity(same(player), any(Runnable.class), eq(1))).thenAnswer(invocation -> {
        delayedTask.set(invocation.getArgument(1));
        return true;
      });

      manager.onInventoryClick(event);
      manager.afterInventoryClick(event);
      clearInvocations(player, brewingInventory);
      delayedTask.get().run();
    }

    verify(player, never()).getOpenInventory();
    verify(brewingInventory, never()).getIngredient();
  }
}
