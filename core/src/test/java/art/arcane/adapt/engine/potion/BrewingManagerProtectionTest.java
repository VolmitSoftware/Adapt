package art.arcane.adapt.engine.potion;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.SchedulerForwarding;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.AdaptServer;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.InventoryAction;
import art.arcane.adapt.platform.event.inventory.InventoryClickEvent;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.event.inventory.SlotKind;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BrewingStandRef;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeWorld;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BrewingManagerProtectionTest extends AdaptTestBase {
  @Test
  void inventoryClickHandlerIgnoresCancelledEventsAtRegistration() throws NoSuchMethodException {
    Method method = BrewingManager.class.getDeclaredMethod("onInventoryClick", InventoryClickEvent.class);
    Method monitor = BrewingManager.class.getDeclaredMethod("afterInventoryClick", InventoryClickEvent.class);

    Handle annotation = method.getAnnotation(Handle.class);
    Handle monitorAnnotation = monitor.getAnnotation(Handle.class);
    assertThat(annotation.ignoreCancelled()).isTrue();
    assertThat(annotation.priority()).isEqualTo(EventPriority.HIGHEST);
    assertThat(monitorAnnotation.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(monitorAnnotation.ignoreCancelled()).isFalse();
  }

  @Test
  void alreadyCancelledClickReturnsBeforeInspectingTheView() {
    PlayerRef player = mock(PlayerRef.class);
    InventoryClickEvent event = click(player, FakeItem.empty());
    event.setCancelled(true);

    new BrewingManager().onInventoryClick(event);

    verify(player, never()).openContainerBlock();
  }

  @Test
  void delayedProcessingStopsWhenPlayerHasOpenedAnotherInventory() {
    BrewingManager manager = new BrewingManager();
    PlayerRef player = mock(PlayerRef.class);
    BlockRef standBlock = standBlock(mock(BrewingStandRef.class));
    BlockRef replacementBlock = mock(BlockRef.class);
    when(player.openContainerBlock()).thenReturn(Optional.of(standBlock), Optional.of(replacementBlock));
    installReadyRuntime(player);
    InventoryClickEvent event = click(player, FakeItem.empty());

    try (MockedStatic<Tasks> scheduling = SchedulerForwarding.mockTasks()) {
      scheduling.when(() -> Tasks.runEntity(same(player), any(Runnable.class), eq(1))).thenAnswer(invocation -> {
        invocation.<Runnable>getArgument(1).run();
        return true;
      });

      manager.onInventoryClick(event);
      manager.afterInventoryClick(event);
    }

    verify(standBlock, never()).location();
  }

  @Test
  void cancellationAfterTheHighestHandlerPreventsDelayedProcessing() {
    BrewingManager manager = new BrewingManager();
    PlayerRef player = mock(PlayerRef.class);
    BlockRef standBlock = standBlock(mock(BrewingStandRef.class));
    when(player.openContainerBlock()).thenReturn(Optional.of(standBlock));
    installReadyRuntime(player);
    InventoryClickEvent event = click(player, FakeItem.empty());

    try (MockedStatic<Tasks> scheduling = SchedulerForwarding.mockTasks()) {
      manager.onInventoryClick(event);
      event.setCancelled(true);
      manager.afterInventoryClick(event);

      scheduling.verify(
          () -> Tasks.runEntity(same(player), any(Runnable.class), eq(1)),
          never()
      );
    }
  }

  @Test
  void sameInventoryWithDifferentStandIsRejected() {
    PlayerRef player = mock(PlayerRef.class);
    BlockRef expectedBlock = mock(BlockRef.class);
    BlockRef currentBlock = standBlock(mock(BrewingStandRef.class));
    when(player.openContainerBlock()).thenReturn(Optional.of(currentBlock));

    assertThat(BrewingManager.isSameOpenStand(player, expectedBlock)).isFalse();
  }

  @Test
  void delayedProcessingStopsBeforeInventoryAccessAfterFoliaRegionChange() {
    BrewingManager manager = new BrewingManager();
    PlayerRef player = mock(PlayerRef.class);
    BrewingStandRef stand = mock(BrewingStandRef.class);
    BlockRef standBlock = standBlock(stand);
    Loc standLocation = Loc.of(new FakeWorld("brewing"), new BlockPos(4, 64, 4));
    when(standBlock.location()).thenReturn(standLocation);
    when(player.openContainerBlock()).thenReturn(Optional.of(standBlock));
    installReadyRuntime(player);
    InventoryClickEvent event = click(player, FakeItem.empty());
    AtomicReference<Runnable> delayedTask = new AtomicReference<>();

    try (MockedStatic<Tasks> scheduling = SchedulerForwarding.mockTasks()) {
      scheduling.when(Tasks::isFoliaThreading).thenReturn(true);
      scheduling.when(() -> Tasks.isOwnedByCurrentRegion(standLocation)).thenReturn(false);
      scheduling.when(() -> Tasks.runEntity(same(player), any(Runnable.class), eq(1))).thenAnswer(invocation -> {
        delayedTask.set(invocation.getArgument(1));
        return true;
      });

      manager.onInventoryClick(event);
      manager.afterInventoryClick(event);
      clearInvocations(player, stand);
      delayedTask.get().run();
    }

    verify(player, never()).openContainerBlock();
    verify(stand, never()).ingredient();
  }

  @Test
  void unavailablePlayerIsIgnoredBeforeCustomClickHandling() {
    BrewingManager manager = new BrewingManager();
    PlayerRef player = mock(PlayerRef.class);
    BrewingStandRef stand = mock(BrewingStandRef.class);
    BlockRef standBlock = standBlock(stand);
    AdaptServer server = mock(AdaptServer.class);
    UUID playerId = UUID.randomUUID();
    when(player.openContainerBlock()).thenReturn(Optional.of(standBlock));
    when(player.uuid()).thenReturn(playerId);
    when(core.getAdaptServer()).thenReturn(server);
    InventoryClickEvent event = click(player, FakeItem.of("iron_ingot"));

    try (MockedStatic<Tasks> scheduling = SchedulerForwarding.mockTasks()) {
      manager.onInventoryClick(event);
      manager.afterInventoryClick(event);

      scheduling.verify(() -> Tasks.runEntity(same(player), any(Runnable.class), eq(1)), never());
    }

    assertThat(event.isCancelled()).isFalse();
    verify(stand, never()).ingredient();
  }

  private static InventoryClickEvent click(PlayerRef player, ItemRef cursor) {
    return new InventoryClickEvent(player, InventoryKind.BREWING, InventoryKind.BREWING, 3, 3, SlotKind.CONTAINER,
        ClickKind.LEFT, InventoryAction.PLACE_ALL, -1, FakeItem.empty(), cursor);
  }

  private static BlockRef standBlock(BrewingStandRef stand) {
    when(stand.ingredient()).thenReturn(FakeItem.empty());
    BlockRef block = mock(BlockRef.class);
    when(block.brewingStand()).thenReturn(Optional.of(stand));
    return block;
  }

  private void installReadyRuntime(PlayerRef player) {
    AdaptServer server = mock(AdaptServer.class);
    AdaptPlayer adaptPlayer = mock(AdaptPlayer.class);
    UUID playerId = UUID.randomUUID();
    when(core.getAdaptServer()).thenReturn(server);
    when(player.uuid()).thenReturn(playerId);
    when(server.getOnlineAdaptPlayer(playerId)).thenReturn(adaptPlayer);
    when(adaptPlayer.isRuntimeReady()).thenReturn(true);
    when(adaptPlayer.getPlayer()).thenReturn(player);
  }
}
