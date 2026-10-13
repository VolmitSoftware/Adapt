package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArchitectScaffolderRestitutionTest {
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void rejectedDeliveryDropsOnceOnTheSourceRegionEvenWhenRetiredCallbackAlsoRuns(boolean invokeRetired) {
    Player owner = mock(Player.class);
    World world = mock(World.class);
    Location source = new Location(world, 10, 70, 20);
    ItemStack refund = stack(Material.PURPLE_SHULKER_BOX, 1);
    Runnable delivered = mock(Runnable.class);
    AtomicReference<Runnable> regionWork = new AtomicReference<>();
    try (MockedStatic<J> scheduler = mockStatic(J.class)) {
      scheduler.when(() -> J.runEntity(eq(owner), any(Runnable.class), any(Runnable.class))).thenAnswer(call -> {
        if (invokeRetired) {
          Runnable retired = call.getArgument(2);
          retired.run();
          retired.run();
        }
        return false;
      });
      scheduler.when(() -> J.runAt(eq(source), any(Runnable.class))).thenAnswer(call -> {
        regionWork.set(call.getArgument(1));
        return true;
      });

      ArchitectScaffolder.restitute(owner, source, List.of(refund), delivered);

      scheduler.verify(() -> J.runAt(eq(source), any(Runnable.class)));
      verify(world, never()).dropItemNaturally(any(Location.class), any(ItemStack.class));
      regionWork.get().run();
      verify(world).dropItemNaturally(source, refund);
      verify(owner, never()).getInventory();
      verify(delivered, never()).run();
    }
  }

  @Test
  void completedOwnerDeliveryCannotAlsoUseTheRetiredFallback() {
    Player owner = mock(Player.class);
    PlayerInventory inventory = mock(PlayerInventory.class);
    World world = mock(World.class);
    Location source = new Location(world, 10, 70, 20);
    ItemStack refund = stack(Material.PURPLE_SHULKER_BOX, 1);
    Runnable delivered = mock(Runnable.class);
    when(owner.isOnline()).thenReturn(true);
    when(owner.getInventory()).thenReturn(inventory);
    when(inventory.addItem(refund)).thenReturn(new HashMap<>());
    try (MockedStatic<J> scheduler = mockStatic(J.class)) {
      scheduler.when(() -> J.runEntity(eq(owner), any(Runnable.class), any(Runnable.class))).thenAnswer(call -> {
        Runnable give = call.getArgument(1);
        Runnable retired = call.getArgument(2);
        give.run();
        retired.run();
        give.run();
        return true;
      });

      ArchitectScaffolder.restitute(owner, source, List.of(refund), delivered);

      verify(inventory).addItem(refund);
      verify(delivered).run();
      scheduler.verify(() -> J.runAt(eq(source), any(Runnable.class)), never());
      verify(world, never()).dropItemNaturally(any(Location.class), any(ItemStack.class));
    }
  }

  @Test
  void ownerLoggingOutBeforeQueuedDeliveryDropsOnTheSourceRegion() {
    Player owner = mock(Player.class);
    World world = mock(World.class);
    Location source = new Location(world, 10, 70, 20);
    ItemStack refund = stack(Material.PURPLE_SHULKER_BOX, 1);
    Runnable delivered = mock(Runnable.class);
    AtomicReference<Runnable> ownerWork = new AtomicReference<>();
    try (MockedStatic<J> scheduler = mockStatic(J.class)) {
      scheduler.when(() -> J.runEntity(eq(owner), any(Runnable.class), any(Runnable.class))).thenAnswer(call -> {
        ownerWork.set(call.getArgument(1));
        return true;
      });
      scheduler.when(() -> J.runAt(eq(source), any(Runnable.class))).thenAnswer(call -> {
        Runnable work = call.getArgument(1);
        work.run();
        return true;
      });

      ArchitectScaffolder.restitute(owner, source, List.of(refund), delivered);
      ownerWork.get().run();

      verify(world).dropItemNaturally(source, refund);
      verify(owner, never()).getInventory();
      verify(delivered, never()).run();
    }
  }

  @Test
  void shulkerRefundPreservesOriginalMetadataAndUsesCurrentContents() {
    Block block = mock(Block.class);
    ShulkerBox current = mock(ShulkerBox.class);
    ItemStack placed = mock(ItemStack.class);
    ItemStack refund = mock(ItemStack.class);
    BlockStateMeta metadata = mock(BlockStateMeta.class);
    when(block.getType()).thenReturn(Material.PURPLE_SHULKER_BOX);
    when(block.getState()).thenReturn(current);
    when(placed.clone()).thenReturn(refund);
    when(refund.getItemMeta()).thenReturn(metadata);

    assertThat(ArchitectScaffolder.collectRestitution(block, placed)).containsExactly(refund);
    verify(metadata).setBlockState(current);
    verify(refund).setItemMeta(metadata);
    verify(block, never()).getDrops();
    verify(current, never()).getInventory();
  }

  @Test
  void chestRefundReturnsOnlyItsOwnInventoryAndDetachesStoredItems() {
    Block block = mock(Block.class);
    Chest chest = mock(Chest.class);
    Inventory inventory = mock(Inventory.class);
    ItemStack placed = stack(Material.CHEST, 1);
    ItemStack stored = stack(Material.DIAMOND, 3);
    when(block.getType()).thenReturn(Material.CHEST);
    when(block.getState()).thenReturn(chest);
    when(chest.getBlockInventory()).thenReturn(inventory);
    when(inventory.getContents()).thenReturn(new ItemStack[]{null, stored});

    assertThat(ArchitectScaffolder.collectRestitution(block, placed)).containsExactly(placed, stored);
    verify(inventory).clear();
    verify(chest, never()).getInventory();
  }

  @Test
  void emptyContainerStillReturnsOriginalPlacedItem() {
    Block block = mock(Block.class);
    Chest chest = mock(Chest.class);
    Inventory inventory = mock(Inventory.class);
    ItemStack placed = stack(Material.CHEST, 1);
    when(block.getType()).thenReturn(Material.CHEST);
    when(block.getState()).thenReturn(chest);
    when(chest.getBlockInventory()).thenReturn(inventory);
    ItemStack air = stack(Material.AIR, 1);
    when(inventory.getContents()).thenReturn(new ItemStack[]{air});

    assertThat(ArchitectScaffolder.collectRestitution(block, placed)).containsExactly(placed);
  }

  private static ItemStack stack(Material type, int amount) {
    ItemStack stack = mock(ItemStack.class);
    when(stack.getType()).thenReturn(type);
    when(stack.getAmount()).thenReturn(amount);
    when(stack.clone()).thenReturn(stack);
    return stack;
  }
}
