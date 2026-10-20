package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.api.golem.NodeType;
import org.bukkit.DyeColor;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NodeRecordTest {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

  @Test
  void aNewNodeStartsIdleWithDefaultsAndNoChannel() {
    NodeRecord node = NodeRecord.builder(OWNER, NodeType.DEPOSIT, BlockFace.NORTH).build();

    assertThat(node.owner()).isEqualTo(OWNER);
    assertThat(node.type()).isEqualTo(NodeType.DEPOSIT);
    assertThat(node.face()).isEqualTo(BlockFace.NORTH);
    assertThat(node.channel()).isNull();
    assertThat(node.priority()).isEqualTo(NodeRecord.DEFAULT_PRIORITY);
    assertThat(node.redstone()).isEqualTo(RedstoneMode.IGNORE);
    assertThat(node.area()).isEqualTo(NodeRecord.DEFAULT_AREA).isEqualTo(new NodeCuboid(-2, -2, -2, 2, 2, 2));
    assertThat(node.filterItems()).isEmpty();
    assertThat(node.filterTags()).isEmpty();
    assertThat(node.filterCount()).isZero();
    assertThat(node.whitelist()).isTrue();
    assertThat(node.data()).isEmpty();
  }

  @Test
  void priorityAndAreaAreClampedIntoTheirRanges() {
    NodeRecord low = NodeRecord.builder(OWNER, NodeType.GUARD, BlockFace.UP).priority(-4).area(new NodeCuboid(3, 1, 2, -1, -2, -3)).build();
    NodeRecord high = NodeRecord.builder(OWNER, NodeType.GUARD, BlockFace.UP).priority(9).area(NodeCuboid.symmetric(99, 40, 33)).build();
    NodeRecord lopsided = NodeRecord.builder(OWNER, NodeType.GUARD, BlockFace.UP).area(new NodeCuboid(-40, 0, -5, 7, 50, 0)).build();

    assertThat(low.priority()).isEqualTo(NodeRecord.MIN_PRIORITY);
    assertThat(low.area()).isEqualTo(new NodeCuboid(0, 0, 0, 0, 0, 0));
    assertThat(high.priority()).isEqualTo(NodeRecord.MAX_PRIORITY);
    assertThat(high.area()).isEqualTo(new NodeCuboid(-8, -8, -8, 7, 7, 7));
    assertThat(lopsided.area()).isEqualTo(new NodeCuboid(-8, 0, -5, 7, 15, 0));
  }

  @Test
  void theFilterHoldsAtMostEighteenItemsAndTagsTogether() {
    List<ItemStack> items = new ArrayList<>();
    for (int index = 0; index < 12; index++) {
      items.add(stack());
    }
    List<String> tags = List.of("#logs", "#planks", "#minecraft:wool", "#saplings", "#flowers", "#leaves");

    NodeRecord full = NodeRecord.builder(OWNER, NodeType.FILTER, BlockFace.EAST).filterItems(items).filterTags(tags).build();

    assertThat(full.filterCount()).isEqualTo(NodeRecord.MAX_FILTER_ENTRIES);
    assertThatThrownBy(() -> full.toBuilder().filterTags(List.of("#logs", "#planks", "#minecraft:wool", "#saplings", "#flowers", "#leaves", "#sand")).build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void tagEntriesMustBeHashPrefixedNames() {
    assertThatThrownBy(() -> NodeRecord.builder(OWNER, NodeType.FILTER, BlockFace.EAST).filterTags(List.of("logs")).build())
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> NodeRecord.builder(OWNER, NodeType.FILTER, BlockFace.EAST).filterTags(List.of("#")).build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void onlyTheSixBlockFacesCanHoldANode() {
    assertThatThrownBy(() -> NodeRecord.builder(OWNER, NodeType.GUARD, BlockFace.NORTH_EAST))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> NodeRecord.builder(OWNER, NodeType.GUARD, BlockFace.SELF))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void recordsAreImmutableValuesThatCopyThroughTheirBuilder() {
    Map<String, String> data = new HashMap<>(Map.of("replant", "true"));
    NodeRecord original = NodeRecord.builder(OWNER, NodeType.HARVEST, BlockFace.UP)
        .channel(DyeColor.LIME).priority(4).redstone(RedstoneMode.POWERED).area(NodeCuboid.symmetric(3, 1, 3))
        .filterTags(List.of("#crops")).whitelist(false).data(data).build();
    data.put("replant", "false");

    NodeRecord copy = original.toBuilder().build();
    NodeRecord changed = original.toBuilder().channel(null).build();

    assertThat(original.data()).containsEntry("replant", "true");
    assertThat(copy).isEqualTo(original).hasSameHashCodeAs(original);
    assertThat(changed).isNotEqualTo(original);
    assertThat(changed.channel()).isNull();
    assertThatThrownBy(() -> original.data().put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> original.filterTags().add("#x")).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void filterItemsAreClonedSoCallersCannotChangeAStoredNode() {
    ItemStack original = mock(ItemStack.class);
    ItemStack copy = mock(ItemStack.class);
    when(original.clone()).thenReturn(copy);

    NodeRecord node = NodeRecord.builder(OWNER, NodeType.FILTER, BlockFace.EAST).filterItems(List.of(original)).build();

    assertThat(node.filterItems()).containsExactly(copy);
  }

  private static ItemStack stack() {
    ItemStack item = mock(ItemStack.class);
    when(item.clone()).thenReturn(item);
    return item;
  }
}
