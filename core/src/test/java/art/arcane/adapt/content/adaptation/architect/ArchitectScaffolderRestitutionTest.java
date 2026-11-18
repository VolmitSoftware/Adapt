package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.testkit.FakeInventory;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeWorld;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.List;
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
    PlayerRef owner = mock(PlayerRef.class);
    WorldRef world = mock(WorldRef.class);
    Loc source = new Loc(world, 10, 70, 20);
    ItemRef refund = stack(Id.minecraft("purple_shulker_box"), 1);
    Runnable delivered = mock(Runnable.class);
    AtomicReference<Runnable> regionWork = new AtomicReference<>();

    try (MockedStatic<Tasks> scheduler = mockStatic(Tasks.class)) {
      scheduler.when(() -> Tasks.runEntity(eq(owner), any(Runnable.class), any(Runnable.class))).thenAnswer(call -> {
        if (invokeRetired) {
          Runnable retired = call.getArgument(2);
          retired.run();
          retired.run();
        }
        return false;
      });
      scheduler.when(() -> Tasks.runAt(eq(source), any(Runnable.class))).thenAnswer(call -> {
        regionWork.set(call.getArgument(1));
        return true;
      });

      ArchitectScaffolder.restitute(owner, source, List.of(refund), delivered);

      scheduler.verify(() -> Tasks.runAt(eq(source), any(Runnable.class)));
      verify(world, never()).dropItemNaturally(any(Vec3.class), any(ItemRef.class));
      regionWork.get().run();
      verify(world).dropItemNaturally(source.position(), refund);
      verify(owner, never()).inventory();
      verify(delivered, never()).run();
    }
  }

  @Test
  void completedOwnerDeliveryCannotAlsoUseTheRetiredFallback() {
    PlayerRef owner = mock(PlayerRef.class);
    PlayerInventoryRef inventory = mock(PlayerInventoryRef.class);
    WorldRef world = mock(WorldRef.class);
    Loc source = new Loc(world, 10, 70, 20);
    ItemRef refund = stack(Id.minecraft("purple_shulker_box"), 1);
    Runnable delivered = mock(Runnable.class);
    when(owner.isOnline()).thenReturn(true);
    when(owner.inventory()).thenReturn(inventory);
    when(inventory.addItem(refund)).thenReturn(List.of());

    try (MockedStatic<Tasks> scheduler = mockStatic(Tasks.class)) {
      scheduler.when(() -> Tasks.runEntity(eq(owner), any(Runnable.class), any(Runnable.class))).thenAnswer(call -> {
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
      scheduler.verify(() -> Tasks.runAt(eq(source), any(Runnable.class)), never());
      verify(world, never()).dropItemNaturally(any(Vec3.class), any(ItemRef.class));
    }
  }

  @Test
  void ownerLoggingOutBeforeQueuedDeliveryDropsOnTheSourceRegion() {
    PlayerRef owner = mock(PlayerRef.class);
    WorldRef world = mock(WorldRef.class);
    Loc source = new Loc(world, 10, 70, 20);
    ItemRef refund = stack(Id.minecraft("purple_shulker_box"), 1);
    Runnable delivered = mock(Runnable.class);
    AtomicReference<Runnable> ownerWork = new AtomicReference<>();

    try (MockedStatic<Tasks> scheduler = mockStatic(Tasks.class)) {
      scheduler.when(() -> Tasks.runEntity(eq(owner), any(Runnable.class), any(Runnable.class))).thenAnswer(call -> {
        ownerWork.set(call.getArgument(1));
        return true;
      });
      scheduler.when(() -> Tasks.runAt(eq(source), any(Runnable.class))).thenAnswer(call -> {
        Runnable work = call.getArgument(1);
        work.run();
        return true;
      });

      ArchitectScaffolder.restitute(owner, source, List.of(refund), delivered);
      ownerWork.get().run();

      verify(world).dropItemNaturally(source.position(), refund);
      verify(owner, never()).inventory();
      verify(delivered, never()).run();
    }
  }

  @Test
  void shulkerRefundPreservesOriginalMetadataAndUsesCurrentContents() {
    BlockRef block = mock(BlockRef.class);
    ItemRef placed = mock(ItemRef.class);
    ItemRef refund = mock(ItemRef.class);
    when(block.type()).thenReturn(Id.minecraft("purple_shulker_box"));
    when(block.itemWithBlockState(placed)).thenReturn(refund);

    assertThat(ArchitectScaffolder.collectRestitution(block, placed)).containsExactly(refund);
    verify(block).itemWithBlockState(placed);
    verify(block, never()).drops(any(), any());
    verify(block, never()).blockInventory();
  }

  @Test
  void shulkerRefundCarriesTheBlockStateOntoACopyOfThePlacedItem() {
    FakeWorld world = new FakeWorld("world");
    world.set(4, 64, 4, "purple_shulker_box");
    BlockPos pos = new BlockPos(4, 64, 4);
    FakeInventory contents = new FakeInventory();
    contents.setItem(0, new FakeItem(Id.minecraft("diamond"), 2));
    world.blockInventory(pos, contents).blockEntityName(pos, "Tools");
    FakeItem placed = new FakeItem(Id.minecraft("purple_shulker_box"), 1);
    placed.setLore(List.of("kept"));

    List<ItemRef> refunds = ArchitectScaffolder.collectRestitution(world.blockAt(4, 64, 4), placed);

    assertThat(refunds).hasSize(1);
    ItemRef refund = refunds.getFirst();
    assertThat(refund).isNotSameAs(placed);
    assertThat(refund.displayName()).isEqualTo("Tools");
    assertThat(refund.lore()).containsExactly("kept");
    assertThat(refund.containerContents().orElseThrow().getFirst().type()).isEqualTo(Id.minecraft("diamond"));
    assertThat(placed.containerContents().orElseThrow()).allMatch(ItemRef::isEmpty);
  }

  @Test
  void shulkerWithoutBlockStateFallsBackToTheBlockDrops() {
    BlockRef block = mock(BlockRef.class);
    ItemRef placed = mock(ItemRef.class);
    ItemRef drop = mock(ItemRef.class);
    when(block.type()).thenReturn(Id.minecraft("purple_shulker_box"));
    when(block.drops(null, null)).thenReturn(List.of(drop));

    assertThat(ArchitectScaffolder.collectRestitution(block, placed)).containsExactly(drop);
  }

  @Test
  void chestRefundReturnsOnlyItsOwnInventoryAndDetachesStoredItems() {
    BlockRef block = mock(BlockRef.class);
    InventoryRef inventory = mock(InventoryRef.class);
    ItemRef placed = stack(Id.minecraft("chest"), 1);
    ItemRef stored = stack(Id.minecraft("diamond"), 3);
    when(block.type()).thenReturn(Id.minecraft("chest"));
    when(block.blockInventory()).thenReturn(inventory);
    when(inventory.size()).thenReturn(2);
    when(inventory.contents()).thenReturn(Arrays.asList(null, stored));

    assertThat(ArchitectScaffolder.collectRestitution(block, placed)).containsExactly(placed, stored);
    verify(inventory).clear(0);
    verify(inventory).clear(1);
    verify(block, never()).container();
  }

  @Test
  void emptyContainerStillReturnsOriginalPlacedItem() {
    BlockRef block = mock(BlockRef.class);
    InventoryRef inventory = mock(InventoryRef.class);
    ItemRef placed = stack(Id.minecraft("chest"), 1);
    when(block.type()).thenReturn(Id.minecraft("chest"));
    when(block.blockInventory()).thenReturn(inventory);
    ItemRef air = stack(Id.minecraft("air"), 1);
    when(inventory.contents()).thenReturn(List.of(air));

    assertThat(ArchitectScaffolder.collectRestitution(block, placed)).containsExactly(placed);
  }

  private static ItemRef stack(Id type, int amount) {
    ItemRef stack = mock(ItemRef.class);
    when(stack.type()).thenReturn(type);
    when(stack.amount()).thenReturn(amount);
    when(stack.copy()).thenReturn(stack);
    return stack;
  }
}
