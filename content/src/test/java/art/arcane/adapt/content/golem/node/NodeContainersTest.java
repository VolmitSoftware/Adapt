package art.arcane.adapt.content.golem.node;

import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class NodeContainersTest {
  private static final Id WHEAT = Id.minecraft("wheat");
  private static final Id CARROT = Id.minecraft("carrot");
  private static final Id RED_SHULKER_BOX = Id.minecraft("red_shulker_box");
  private static final Id SHULKER_BOX = Id.minecraft("shulker_box");
  private static final Id CHEST = Id.minecraft("chest");
  private final WorldRef world = mock(WorldRef.class);

  @Test
  void containerBlocksExposeTheirInventory() {
    InventoryRef inventory = NodeTestItems.inventory(27);
    BlockRef barrel = block(0, 64, 0, inventory);

    assertThat(NodeContainers.inventory(barrel)).containsSame(inventory);
    assertThat(NodeContainers.inventory(block(0, 65, 0, null))).isEmpty();
  }

  @Test
  void bothHalvesOfADoubleChestShareOneKey() {
    InventoryRef pair = NodeTestItems.inventory(54);
    BlockRef left = block(4, 64, 9, pair);
    BlockRef right = block(5, 64, 9, pair);
    lenient().when(left.containerHalves()).thenReturn(List.of(left, right));
    lenient().when(right.containerHalves()).thenReturn(List.of(left, right));

    assertThat(NodeContainers.key(right, pair)).isEqualTo(NodeContainers.key(left, pair));
    assertThat(NodeContainers.key(left, pair)).isEqualTo(NodeContainers.blockKey(4, 64, 9));
  }

  @Test
  void aSingleContainerIsKeyedByItsBlock() {
    InventoryRef inventory = NodeTestItems.inventory(27);
    BlockRef block = block(-3, 70, 12, inventory);

    assertThat(NodeContainers.key(block, inventory)).isEqualTo(NodeContainers.blockKey(-3, 70, 12));
    assertThat(NodeContainers.blockKey(-3, 70, 12)).isNotEqualTo(NodeContainers.blockKey(3, 70, 12));
    assertThat(NodeContainers.blockKey(0, -64, 0)).isNotEqualTo(NodeContainers.blockKey(0, 64, 0));
  }

  @Test
  void roomCountsEmptySlotsAndSimilarPartialStacks() {
    InventoryRef inventory = NodeTestItems.inventory(2);
    inventory.addItem(NodeTestItems.stack(WHEAT, 60));

    assertThat(NodeContainers.room(inventory, NodeTestItems.stack(WHEAT, 1))).isEqualTo(68);
    assertThat(NodeContainers.room(inventory, NodeTestItems.stack(CARROT, 1))).isEqualTo(64);

    inventory.addItem(NodeTestItems.stack(CARROT, 64));
    assertThat(NodeContainers.room(inventory, NodeTestItems.stack(CARROT, 1))).isZero();
    assertThat(NodeContainers.full(inventory)).isTrue();
    assertThat(NodeContainers.fits(inventory, List.of(NodeTestItems.stack(CARROT, 1), NodeTestItems.stack(WHEAT, 1)))).isTrue();
    assertThat(NodeContainers.fits(inventory, List.of(NodeTestItems.stack(CARROT, 1)))).isFalse();
  }

  @Test
  void aShulkerBoxNeverTakesAnotherShulkerBox() {
    InventoryRef empty = NodeTestItems.inventory(27);
    ItemRef box = NodeTestItems.stack(RED_SHULKER_BOX, 1);
    ItemRef wheat = NodeTestItems.stack(WHEAT, 1);

    assertThat(NodeContainers.isShulkerBox(RED_SHULKER_BOX)).isTrue();
    assertThat(NodeContainers.isShulkerBox(CHEST)).isFalse();
    assertThat(NodeContainers.nests(SHULKER_BOX, box)).isTrue();
    assertThat(NodeContainers.nests(SHULKER_BOX, wheat)).isFalse();
    assertThat(NodeContainers.nests(CHEST, box)).isFalse();
    assertThat(NodeContainers.fits(SHULKER_BOX, empty, List.of(box))).isFalse();
    assertThat(NodeContainers.fits(SHULKER_BOX, empty, List.of(box, wheat))).isTrue();
    assertThat(NodeContainers.fits(CHEST, empty, List.of(box))).isTrue();
  }

  private BlockRef block(int x, int y, int z, InventoryRef container) {
    BlockRef block = mock(BlockRef.class);
    lenient().when(block.world()).thenReturn(world);
    lenient().when(block.x()).thenReturn(x);
    lenient().when(block.y()).thenReturn(y);
    lenient().when(block.z()).thenReturn(z);
    lenient().when(block.container()).thenReturn(container);
    lenient().when(block.containerHalves()).thenReturn(container == null ? List.of() : List.of(block));
    return block;
  }
}
