package art.arcane.adapt.content.adaptation.golemancy;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class SorterIndexTest {
  private static final UUID WORLD_ID = UUID.fromString("00000000-0000-0000-0000-0000000026b1");
  private static final long REFRESH = 30_000L;

  private final SorterItems items = new SorterItems();
  private final World world = mock(World.class);
  private final AtomicInteger capacity = new AtomicInteger(8);
  private final SorterIndex index = new SorterIndex(capacity::get, () -> REFRESH);

  SorterIndexTest() {
    lenient().when(world.getUID()).thenReturn(WORLD_ID);
  }

  @Test
  void aSnapshotStaysFreshUntilTheRefreshWindowPasses() {
    ContainerId id = new ContainerId(WORLD_ID, 1, 64, 1);
    SorterStock stock = SorterStock.read(items.inventory(9, items.item(Material.DIRT, 4)), 1_000L);

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

    index.invalidate(items.inventory(9, new Location(world, 1, 64, 1)));
    assertThat(index.fresh(single, 0L, REFRESH)).isEmpty();
    assertThat(index.fresh(left, 0L, REFRESH)).isPresent();

    index.invalidate(doubleChest(new Location(world, 4, 64, 1), new Location(world, 4.5, 64, 1.5)));
    assertThat(index.fresh(left, 0L, REFRESH)).isEmpty();
    assertThat(index.isEmpty()).isTrue();

    index.invalidate(items.inventory(9));
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
    Block right = block(5, 64, 1);
    DoubleChestInventory inventory = doubleChest(new Location(world, 4, 64, 1), new Location(world, 4.5, 64, 1.5));

    assertThat(ContainerId.of(right, inventory)).isEqualTo(new ContainerId(WORLD_ID, 4, 64, 1));
    assertThat(ContainerId.of(block(7, 64, 1), items.inventory(9))).isEqualTo(new ContainerId(WORLD_ID, 7, 64, 1));
    assertThat(new ContainerId(WORLD_ID, 0, 64, 0).distanceSquared(new ContainerId(WORLD_ID, 3, 68, 0))).isEqualTo(25D);
  }

  private SorterStock stock(long at) {
    return SorterStock.read(items.inventory(9, items.item(Material.DIRT, 1)), at);
  }

  private Block block(int x, int y, int z) {
    Block block = mock(Block.class);
    lenient().when(block.getWorld()).thenReturn(world);
    lenient().when(block.getX()).thenReturn(x);
    lenient().when(block.getY()).thenReturn(y);
    lenient().when(block.getZ()).thenReturn(z);
    return block;
  }

  private DoubleChestInventory doubleChest(Location left, Location center) {
    DoubleChestInventory inventory = mock(DoubleChestInventory.class);
    Inventory leftSide = mock(Inventory.class);
    lenient().when(leftSide.getLocation()).thenReturn(left);
    lenient().when(inventory.getLeftSide()).thenReturn(leftSide);
    lenient().when(inventory.getLocation()).thenReturn(center);
    return inventory;
  }
}
