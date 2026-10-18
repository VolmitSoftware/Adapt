package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.api.golem.GolemTestItems;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class NodeContainersTest {
  private final World world = mock(World.class);

  @Test
  void containerBlocksExposeTheirInventory() {
    Inventory inventory = GolemTestItems.inventory(27);
    Barrel barrel = mock(Barrel.class);
    lenient().when(barrel.getInventory()).thenReturn(inventory);

    assertThat(NodeContainers.inventory(block(0, 64, 0, barrel))).containsSame(inventory);
    assertThat(NodeContainers.inventory(block(0, 65, 0, mock(BlockState.class)))).isEmpty();
  }

  @Test
  void bothHalvesOfADoubleChestShareOneKey() {
    Inventory leftHalf = mock(Inventory.class);
    lenient().when(leftHalf.getLocation()).thenReturn(new Location(world, 4D, 64D, 9D));
    DoubleChestInventory fromLeft = doubleChest(leftHalf);
    DoubleChestInventory fromRight = doubleChest(leftHalf);
    Block left = block(4, 64, 9, chest(fromLeft));
    Block right = block(5, 64, 9, chest(fromRight));

    assertThat(NodeContainers.key(right, fromRight)).isEqualTo(NodeContainers.key(left, fromLeft));
    assertThat(NodeContainers.key(left, fromLeft)).isEqualTo(NodeContainers.blockKey(4, 64, 9));
  }

  @Test
  void aSingleContainerIsKeyedByItsBlock() {
    Inventory inventory = GolemTestItems.inventory(27);
    Block block = block(-3, 70, 12, chest(inventory));

    assertThat(NodeContainers.key(block, inventory)).isEqualTo(NodeContainers.blockKey(-3, 70, 12));
    assertThat(NodeContainers.blockKey(-3, 70, 12)).isNotEqualTo(NodeContainers.blockKey(3, 70, 12));
    assertThat(NodeContainers.blockKey(0, -64, 0)).isNotEqualTo(NodeContainers.blockKey(0, 64, 0));
  }

  @Test
  void roomCountsEmptySlotsAndSimilarPartialStacks() {
    Inventory inventory = GolemTestItems.inventory(2);
    inventory.addItem(GolemTestItems.stack(Material.WHEAT, 60));

    assertThat(NodeContainers.room(inventory, GolemTestItems.stack(Material.WHEAT, 1))).isEqualTo(68);
    assertThat(NodeContainers.room(inventory, GolemTestItems.stack(Material.CARROT, 1))).isEqualTo(64);

    inventory.addItem(GolemTestItems.stack(Material.CARROT, 64));
    assertThat(NodeContainers.room(inventory, GolemTestItems.stack(Material.CARROT, 1))).isZero();
    assertThat(NodeContainers.full(inventory)).isTrue();
    assertThat(NodeContainers.fits(inventory, List.of(GolemTestItems.stack(Material.CARROT, 1), GolemTestItems.stack(Material.WHEAT, 1)))).isTrue();
    assertThat(NodeContainers.fits(inventory, List.of(GolemTestItems.stack(Material.CARROT, 1)))).isFalse();
  }

  private static DoubleChestInventory doubleChest(Inventory leftHalf) {
    DoubleChestInventory inventory = mock(DoubleChestInventory.class);
    lenient().when(inventory.getLeftSide()).thenReturn(leftHalf);
    return inventory;
  }

  private static Chest chest(Inventory inventory) {
    Chest chest = mock(Chest.class);
    lenient().when(chest.getInventory()).thenReturn(inventory);
    return chest;
  }

  private Block block(int x, int y, int z, BlockState state) {
    Block block = mock(Block.class);
    lenient().when(block.getWorld()).thenReturn(world);
    lenient().when(block.getX()).thenReturn(x);
    lenient().when(block.getY()).thenReturn(y);
    lenient().when(block.getZ()).thenReturn(z);
    lenient().when(block.getState()).thenReturn(state);
    lenient().when(block.getState(false)).thenReturn(state);
    lenient().when(block.getLocation()).thenReturn(new Location(world, x, y, z));
    return block;
  }
}
