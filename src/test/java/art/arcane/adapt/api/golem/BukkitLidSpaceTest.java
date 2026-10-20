package art.arcane.adapt.api.golem;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Hopper;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class BukkitLidSpaceTest {
  private final World world = mock(World.class);

  @Test
  void everyLiddedContainerKindIsItsOwnHalf() {
    Block block = block(3, 64, 3);
    Chest chest = mock(Chest.class);
    lenient().when(chest.getInventory()).thenReturn(mock(Inventory.class));

    assertThat(BukkitLidSpace.halves(block, chest)).containsExactly(block);
    assertThat(BukkitLidSpace.halves(block, mock(Barrel.class))).containsExactly(block);
    assertThat(BukkitLidSpace.halves(block, mock(ShulkerBox.class))).containsExactly(block);
  }

  @Test
  void aContainerWithoutALidHasNoHalves() {
    Hopper hopper = mock(Hopper.class);

    assertThat(BukkitLidSpace.halves(block(3, 64, 3), hopper)).isEmpty();
    verifyNoInteractions(hopper);
  }

  @Test
  void aDoubleChestIsBothOfItsBlocksFromEitherSide() {
    Block clicked = block(4, 64, 3);
    Block left = block(3, 64, 3);
    Block right = block(4, 64, 3);
    Inventory leftInventory = inventoryAt(left);
    Inventory rightInventory = inventoryAt(right);
    DoubleChestInventory pair = mock(DoubleChestInventory.class);
    lenient().when(pair.getLeftSide()).thenReturn(leftInventory);
    lenient().when(pair.getRightSide()).thenReturn(rightInventory);
    Chest chest = mock(Chest.class);
    lenient().when(chest.getInventory()).thenReturn(pair);

    assertThat(BukkitLidSpace.halves(clicked, chest)).containsExactly(left, right);
  }

  @Test
  void aDoubleChestWhoseHalvesCannotBeLocatedFallsBackToTheClickedBlock() {
    Block clicked = block(4, 64, 3);
    Inventory unplaced = mock(Inventory.class);
    DoubleChestInventory pair = mock(DoubleChestInventory.class);
    lenient().when(pair.getLeftSide()).thenReturn(unplaced);
    lenient().when(pair.getRightSide()).thenReturn(unplaced);
    Chest chest = mock(Chest.class);
    lenient().when(chest.getInventory()).thenReturn(pair);

    assertThat(BukkitLidSpace.halves(clicked, chest)).containsExactly(clicked);
  }

  @Test
  void aLidIsViewedWhilePlayersHaveTheContainerOpenAndDrivesTheLiveState() {
    Chest chest = mock(Chest.class);
    Inventory inventory = mock(Inventory.class);
    lenient().when(chest.getInventory()).thenReturn(inventory);
    lenient().when(inventory.getViewers()).thenReturn(List.of());
    lenient().when(chest.isOpen()).thenReturn(true);
    BukkitLidSpace.BukkitLid lid = new BukkitLidSpace.BukkitLid(chest, chest);

    assertThat(lid.viewed()).isFalse();
    assertThat(lid.isOpen()).isTrue();

    lenient().when(inventory.getViewers()).thenReturn(List.of(mock(HumanEntity.class)));
    assertThat(lid.viewed()).isTrue();

    lid.open();
    lid.close();
    verify(chest).open();
    verify(chest).close();
  }

  private Block block(int x, int y, int z) {
    Block block = mock(Block.class);
    lenient().when(block.getWorld()).thenReturn(world);
    lenient().when(block.getX()).thenReturn(x);
    lenient().when(block.getY()).thenReturn(y);
    lenient().when(block.getZ()).thenReturn(z);
    return block;
  }

  private Inventory inventoryAt(Block block) {
    Location at = mock(Location.class);
    lenient().when(at.getWorld()).thenReturn(world);
    lenient().when(at.getBlock()).thenReturn(block);
    Inventory inventory = mock(Inventory.class);
    lenient().when(inventory.getLocation()).thenReturn(at);
    return inventory;
  }
}
