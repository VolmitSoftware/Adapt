package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class SorterIndexTest {
  private static final UUID WORLD_ID = UUID.fromString("00000000-0000-0000-0000-0000000026b1");
  private static final long REFRESH = 30_000L;

  private final SorterItems items = new SorterItems();
  private final WorldRef world = mock(WorldRef.class);
  private final AtomicInteger capacity = new AtomicInteger(8);
  private final SorterIndex index = new SorterIndex(capacity::get, () -> REFRESH);

  SorterIndexTest() {
    lenient().when(world.uid()).thenReturn(WORLD_ID);
  }

  @Test
  void aSnapshotStaysFreshUntilTheRefreshWindowPasses() {
    ContainerId id = new ContainerId(WORLD_ID, 1, 64, 1);
    SorterStock stock = SorterStock.read(items.inventory(9, items.item(Id.minecraft("dirt"), 4)), 1_000L);

    index.put(id, stock);

    assertThat(index.fresh(id, 1_000L + REFRESH - 1L, REFRESH)).containsSame(stock);
    assertThat(index.fresh(id, 1_000L + REFRESH, REFRESH)).isEmpty();
    assertThat(index.fresh(new ContainerId(WORLD_ID, 2, 64, 1), 1_000L, REFRESH)).isEmpty();
  }

  @Test
  void inventoryEventsInvalidateTheContainerTheyTouch() {
    ContainerId single = new ContainerId(WORLD_ID, 1, 64, 1);
    ContainerId left = new ContainerId(WORLD_ID, 4, 64, 1);
    index.put(single, stock(0L));
    index.put(left, stock(0L));

    index.invalidate(container(1, 64, 1));
    assertThat(index.fresh(single, 0L, REFRESH)).isEmpty();
    assertThat(index.fresh(left, 0L, REFRESH)).isPresent();

    index.invalidate(doubleChestRight(4, 5, 64, 1));
    assertThat(index.fresh(left, 0L, REFRESH)).isEmpty();
    assertThat(index.isEmpty()).isTrue();

    index.invalidate((BlockRef) null);
  }

  @Test
  void aFullIndexDropsItsOldestSnapshot() {
    capacity.set(2);
    index.put(new ContainerId(WORLD_ID, 1, 64, 1), stock(10L));
    index.put(new ContainerId(WORLD_ID, 2, 64, 1), stock(5L));
    index.put(new ContainerId(WORLD_ID, 3, 64, 1), stock(20L));

    assertThat(index.fresh(new ContainerId(WORLD_ID, 2, 64, 1), 20L, REFRESH)).isEmpty();
    assertThat(index.fresh(new ContainerId(WORLD_ID, 1, 64, 1), 20L, REFRESH)).isPresent();
    assertThat(index.fresh(new ContainerId(WORLD_ID, 3, 64, 1), 20L, REFRESH)).isPresent();
  }

  @Test
  void sweepingDropsSnapshotsPastTheRefreshWindow() {
    index.put(new ContainerId(WORLD_ID, 1, 64, 1), stock(0L));
    index.put(new ContainerId(WORLD_ID, 2, 64, 1), stock(REFRESH));

    index.sweep(REFRESH + 1L);
    assertThat(index.isEmpty()).isFalse();

    index.sweep(REFRESH * 2L);
    assertThat(index.isEmpty()).isTrue();

    index.put(new ContainerId(WORLD_ID, 3, 64, 1), stock(0L));
    index.clear();
    assertThat(index.isEmpty()).isTrue();
  }

  @Test
  void aDoubleChestIsOneContainerKeyedByItsLeftHalf() {
    BlockRef right = doubleChestRight(4, 5, 64, 1);

    assertThat(ContainerId.of(right)).isEqualTo(new ContainerId(WORLD_ID, 4, 64, 1));
    assertThat(ContainerId.of(container(7, 64, 1))).isEqualTo(new ContainerId(WORLD_ID, 7, 64, 1));
    assertThat(new ContainerId(WORLD_ID, 0, 64, 0).distanceSquared(new ContainerId(WORLD_ID, 3, 68, 0))).isEqualTo(25D);
  }

  private SorterStock stock(long at) {
    return SorterStock.read(items.inventory(9, items.item(Id.minecraft("dirt"), 1)), at);
  }

  private BlockRef block(int x, int y, int z) {
    BlockRef block = mock(BlockRef.class);
    lenient().when(block.world()).thenReturn(world);
    lenient().when(block.x()).thenReturn(x);
    lenient().when(block.y()).thenReturn(y);
    lenient().when(block.z()).thenReturn(z);
    return block;
  }

  private BlockRef container(int x, int y, int z) {
    BlockRef block = block(x, y, z);
    lenient().when(block.containerHalves()).thenReturn(List.of(block));
    return block;
  }

  private BlockRef doubleChestRight(int leftX, int rightX, int y, int z) {
    BlockRef left = block(leftX, y, z);
    BlockRef right = block(rightX, y, z);
    lenient().when(left.containerHalves()).thenReturn(List.of(left, right));
    lenient().when(right.containerHalves()).thenReturn(List.of(left, right));
    return right;
  }
}
