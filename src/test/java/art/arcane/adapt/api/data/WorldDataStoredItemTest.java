package art.arcane.adapt.api.data;

import art.arcane.adapt.api.data.unit.Earnings;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeFaces;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.plinth.PlinthSlotItem;
import art.arcane.adapt.nms.NMS;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;

/**
 * Items inside values of the current mantle format: what a world keeps of them across saves, whether they decode or not.
 */
class WorldDataStoredItemTest extends MatterWorldTestBase {
  private static final UUID SLOT = new UUID(11L, 12L);
  private static final UUID OWNER = new UUID(13L, 14L);
  private static final int SAVE_CYCLES = 4;

  @Test
  void itemsThatStopDecodingKeepTheirBytesThroughEverySave() {
    byte[] fragile = itemBytes("fragile");
    byte[] sturdy = itemBytes("sturdy");
    WorldData data = open(WORLD);
    data.set(1, 64, 1, PlinthSlotItem.of(SLOT, item("fragile")));
    data.set(2, 64, 1, NodeFaces.EMPTY.with(filter().filterItems(List.of(item("sturdy"), item("fragile"))).build()));
    close(data);
    undecodable = Set.of("fragile");

    for (int cycle = 0; cycle < SAVE_CYCLES; cycle++) {
      data = open(WORLD);
      PlinthSlotItem slot = data.get(1, 64, 1, PlinthSlotItem.class);
      NodeRecord filter = data.get(2, 64, 1, NodeFaces.class).get(BlockFace.UP);
      assertThat(slot.readable()).as("save %d", cycle).isFalse();
      assertThat(slot.lost()).as("save %d", cycle).isFalse();
      assertThat(slot.stored().bytes()).as("save %d", cycle).isEqualTo(fragile);
      assertThat(filter.filterItems()).as("save %d", cycle).containsExactly(item("sturdy"));
      assertThat(filter.unreadableFilters()).as("save %d", cycle).isEqualTo(1);
      assertThat(filter.filterEntries()).as("save %d", cycle).extracting(StoredItem::bytes).containsExactly(sturdy, fragile);
      data.set(3, 64, 1, new Earnings(cycle));
      close(data);
    }

    undecodable = Set.of();
    data = open(WORLD);
    assertThat(data.get(1, 64, 1, PlinthSlotItem.class).item()).isSameAs(item("fragile"));
    NodeRecord recovered = data.get(2, 64, 1, NodeFaces.class).get(BlockFace.UP);
    assertThat(recovered.filterItems()).containsExactly(item("sturdy"), item("fragile"));
    assertThat(recovered.unreadableFilters()).isZero();
  }

  @Test
  void aSaveWritesStoredItemsWithoutDecodingThem() {
    WorldData data = open(WORLD);
    data.set(1, 64, 1, PlinthSlotItem.of(SLOT, item("sugar")));
    data.set(2, 64, 1, NodeFaces.EMPTY.with(filter().filterItems(List.of(item("sugar"))).build()));
    close(data);
    decodeCalls.clear();
    nms().clearInvocations();

    data = open(WORLD);
    assertThat(data.get(1, 64, 1, PlinthSlotItem.class).id()).isEqualTo(SLOT);
    assertThat(data.get(2, 64, 1, NodeFaces.class).get(BlockFace.UP).filterCount()).isEqualTo(1);
    data.set(3, 64, 1, new Earnings(1));
    close(data);

    assertThat(decodeCalls).isEmpty();
    nms().verify(() -> NMS.deserializeStack(anyString()), never());
  }

  @Test
  void zeroLengthMarkersWrittenByEarlierBuildsStillLoadAndStayAsTheyAre() {
    WorldData data = open(WORLD);
    data.set(1, 64, 1, PlinthSlotItem.stored(SLOT, new byte[0]));
    data.set(2, 64, 1, NodeFaces.EMPTY.with(filter()
        .filterEntries(List.of(StoredItem.of(item("sturdy")), StoredItem.stored(new byte[0]))).whitelist(false).build()));
    close(data);

    for (int cycle = 0; cycle < 2; cycle++) {
      data = open(WORLD);
      PlinthSlotItem slot = data.get(1, 64, 1, PlinthSlotItem.class);
      NodeRecord filter = data.get(2, 64, 1, NodeFaces.class).get(BlockFace.UP);
      assertThat(slot.id()).isEqualTo(SLOT);
      assertThat(slot.lost()).isTrue();
      assertThat(slot.readable()).isFalse();
      assertThat(slot.stored().bytes()).isEmpty();
      assertThat(filter.filterItems()).containsExactly(item("sturdy"));
      assertThat(filter.unreadableFilters()).isEqualTo(1);
      assertThat(filter.filterCount()).isEqualTo(2);
      assertThat(filter.filterEntries().get(1).isEmpty()).isTrue();
      data.set(3, 64, 1, new Earnings(cycle));
      close(data);
    }
  }

  @Test
  void aValueSetThisSessionEqualsItselfReadBackFromItsFile() {
    PlinthSlotItem placed = PlinthSlotItem.of(SLOT, item("sugar"));
    NodeFaces edited = NodeFaces.EMPTY.with(filter().filterItems(List.of(item("sugar"), item("wheat"))).build());
    WorldData data = open(WORLD);
    data.set(1, 64, 1, placed);
    data.set(2, 64, 1, edited);
    assertThat(data.get(1, 64, 1, PlinthSlotItem.class)).isEqualTo(placed);
    close(data);

    data = open(WORLD);

    PlinthSlotItem reloadedSlot = data.get(1, 64, 1, PlinthSlotItem.class);
    NodeFaces reloadedNodes = data.get(2, 64, 1, NodeFaces.class);
    assertThat(reloadedSlot).isNotSameAs(placed).isEqualTo(placed);
    assertThat(placed).isEqualTo(reloadedSlot);
    assertThat(reloadedNodes).isNotSameAs(edited).isEqualTo(edited);
    assertThat(edited).isEqualTo(reloadedNodes);
    assertThat(reloadedNodes.hashCode()).isEqualTo(edited.hashCode());
  }

  @Test
  void slotsThatShareAnIdButNotTheirBytesAreStoredApart() {
    WorldData data = open(WORLD);
    data.set(1, 64, 1, PlinthSlotItem.of(SLOT, item("fragile")));
    data.set(2, 64, 1, PlinthSlotItem.of(SLOT, item("also fragile")));
    close(data);
    undecodable = Set.of("fragile", "also fragile");

    for (int cycle = 0; cycle < 2; cycle++) {
      data = open(WORLD);
      assertThat(data.get(1, 64, 1, PlinthSlotItem.class).stored().bytes()).isEqualTo(itemBytes("fragile"));
      assertThat(data.get(2, 64, 1, PlinthSlotItem.class).stored().bytes()).isEqualTo(itemBytes("also fragile"));
      data.set(3, 64, 1, new Earnings(cycle));
      close(data);
    }
  }

  private static NodeRecord.Builder filter() {
    return NodeRecord.builder(OWNER, NodeType.FILTER, BlockFace.UP);
  }
}
