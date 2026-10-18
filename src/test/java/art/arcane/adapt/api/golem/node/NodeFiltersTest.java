package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.api.golem.NodeType;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class NodeFiltersTest {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final String LOGS = "#minecraft:logs";
  private static final String SAPLINGS = "#minecraft:saplings";
  private static final NodeTagLookup TAGS = new NodeTagLookup() {
    private final Map<String, Set<Material>> tags = Map.of(
        LOGS, Set.of(Material.OAK_LOG, Material.BIRCH_LOG),
        SAPLINGS, Set.of(Material.OAK_SAPLING));

    @Override
    public boolean exists(String tag) {
      return tags.containsKey(tag);
    }

    @Override
    public boolean tagged(String tag, Material material) {
      return tags.getOrDefault(tag, Set.of()).contains(material);
    }
  };

  @Test
  void togglingAnAbsentItemAddsItUnderTheSlotCap() {
    NodeRecord record = record().build();
    ItemStack cobble = item(Material.COBBLESTONE);

    NodeFilters.FilterEdit edit = NodeFilters.toggleItem(record, cobble, 3);

    assertThat(edit.outcome()).isEqualTo(NodeFilters.Outcome.ADDED);
    assertThat(edit.record().filterItems()).extracting(ItemStack::getType).containsExactly(Material.COBBLESTONE);
  }

  @Test
  void togglingAPresentItemRemovesItByType() {
    NodeRecord record = record().filterItems(List.of(item(Material.COBBLESTONE), item(Material.WHEAT))).build();

    NodeFilters.FilterEdit edit = NodeFilters.toggleItem(record, item(Material.COBBLESTONE), 3);

    assertThat(edit.outcome()).isEqualTo(NodeFilters.Outcome.REMOVED);
    assertThat(edit.record().filterItems()).extracting(ItemStack::getType).containsExactly(Material.WHEAT);
  }

  @Test
  void aFullFilterRefusesNewItemsButStillRemovesEntriesAboveALoweredCap() {
    NodeRecord record = record()
        .filterItems(List.of(item(Material.COBBLESTONE), item(Material.WHEAT)))
        .filterTags(List.of(LOGS))
        .build();

    NodeFilters.FilterEdit refused = NodeFilters.toggleItem(record, item(Material.STONE), 3);
    NodeFilters.FilterEdit removed = NodeFilters.toggleItem(record, item(Material.WHEAT), 1);

    assertThat(refused.outcome()).isEqualTo(NodeFilters.Outcome.FULL);
    assertThat(refused.record()).isSameAs(record);
    assertThat(removed.outcome()).isEqualTo(NodeFilters.Outcome.REMOVED);
    assertThat(removed.record().filterCount()).isEqualTo(2);
  }

  @Test
  void theSlotCapNeverExceedsTheRecordLimit() {
    NodeRecord record = record().build();
    for (int index = 0; index < NodeRecord.MAX_FILTER_ENTRIES; index++) {
      record = NodeFilters.toggleItem(record, item(Material.values()[index + 1]), 64).record();
    }

    assertThat(record.filterCount()).isEqualTo(NodeRecord.MAX_FILTER_ENTRIES);
    assertThat(NodeFilters.toggleItem(record, item(Material.DIAMOND), 64).outcome()).isEqualTo(NodeFilters.Outcome.FULL);
  }

  @Test
  void tagsToggleAndShareTheSlotCapWithItems() {
    NodeRecord record = record().filterItems(List.of(item(Material.COBBLESTONE))).build();

    NodeFilters.FilterEdit added = NodeFilters.toggleTag(record, LOGS, 2);
    NodeFilters.FilterEdit full = NodeFilters.toggleTag(added.record(), SAPLINGS, 2);
    NodeFilters.FilterEdit removed = NodeFilters.toggleTag(added.record(), LOGS, 2);

    assertThat(added.outcome()).isEqualTo(NodeFilters.Outcome.ADDED);
    assertThat(added.record().filterTags()).containsExactly(LOGS);
    assertThat(full.outcome()).isEqualTo(NodeFilters.Outcome.FULL);
    assertThat(removed.outcome()).isEqualTo(NodeFilters.Outcome.REMOVED);
    assertThat(removed.record().filterTags()).isEmpty();
  }

  @Test
  void settingAGhostSlotReplacesItsItemOrAppendsIntoAnEmptySlot() {
    NodeRecord record = record().filterItems(List.of(item(Material.COBBLESTONE), item(Material.WHEAT))).build();

    NodeFilters.FilterEdit replaced = NodeFilters.setItem(record, 0, item(Material.STONE), 3);
    NodeFilters.FilterEdit appended = NodeFilters.setItem(record, 2, item(Material.STONE), 3);
    NodeFilters.FilterEdit full = NodeFilters.setItem(appended.record(), 3, item(Material.DIRT), 3);
    NodeFilters.FilterEdit same = NodeFilters.setItem(record, 1, item(Material.WHEAT), 3);

    assertThat(replaced.outcome()).isEqualTo(NodeFilters.Outcome.REPLACED);
    assertThat(replaced.record().filterItems()).extracting(ItemStack::getType).containsExactly(Material.STONE, Material.WHEAT);
    assertThat(appended.outcome()).isEqualTo(NodeFilters.Outcome.ADDED);
    assertThat(appended.record().filterItems()).extracting(ItemStack::getType).containsExactly(Material.COBBLESTONE, Material.WHEAT, Material.STONE);
    assertThat(full.outcome()).isEqualTo(NodeFilters.Outcome.FULL);
    assertThat(same.outcome()).isEqualTo(NodeFilters.Outcome.UNCHANGED);
  }

  @Test
  void settingAnItemAlreadyInAnotherSlotMovesIt() {
    NodeRecord record = record().filterItems(List.of(item(Material.COBBLESTONE), item(Material.WHEAT))).build();

    NodeFilters.FilterEdit moved = NodeFilters.setItem(record, 0, item(Material.WHEAT), 3);

    assertThat(moved.outcome()).isEqualTo(NodeFilters.Outcome.REPLACED);
    assertThat(moved.record().filterItems()).extracting(ItemStack::getType).containsExactly(Material.WHEAT);
  }

  @Test
  void clearingASlotRemovesThatItemOrTagWhereverItNowSits() {
    NodeRecord record = record()
        .filterItems(List.of(item(Material.COBBLESTONE), item(Material.WHEAT)))
        .filterTags(List.of(LOGS, SAPLINGS))
        .build();

    assertThat(NodeFilters.removeItem(record, Material.WHEAT).filterItems()).extracting(ItemStack::getType).containsExactly(Material.COBBLESTONE);
    assertThat(NodeFilters.removeTag(record, LOGS).filterTags()).containsExactly(SAPLINGS);
    assertThat(NodeFilters.removeItem(record, Material.STONE)).isSameAs(record);
    assertThat(NodeFilters.removeTag(record, "#minecraft:flowers")).isSameAs(record);
  }

  @Test
  void anEmptyFilterAcceptsEverythingInEitherMode() {
    assertThat(NodeFilters.accepts(record().build(), Material.STONE, TAGS)).isTrue();
    assertThat(NodeFilters.accepts(record().whitelist(false).build(), Material.STONE, TAGS)).isTrue();
  }

  @Test
  void aWhitelistAcceptsOnlyMatchingItemsAndTags() {
    NodeRecord record = record().filterItems(List.of(item(Material.WHEAT))).filterTags(List.of(LOGS)).build();

    assertThat(NodeFilters.accepts(record, Material.WHEAT, TAGS)).isTrue();
    assertThat(NodeFilters.accepts(record, Material.BIRCH_LOG, TAGS)).isTrue();
    assertThat(NodeFilters.accepts(record, Material.STONE, TAGS)).isFalse();
    assertThat(NodeFilters.accepts(record, Material.OAK_SAPLING, TAGS)).isFalse();
  }

  @Test
  void aBlacklistAcceptsEverythingExceptMatchingItemsAndTags() {
    NodeRecord record = record().whitelist(false).filterItems(List.of(item(Material.WHEAT))).filterTags(List.of(LOGS)).build();

    assertThat(NodeFilters.accepts(record, Material.WHEAT, TAGS)).isFalse();
    assertThat(NodeFilters.accepts(record, Material.OAK_LOG, TAGS)).isFalse();
    assertThat(NodeFilters.accepts(record, Material.STONE, TAGS)).isTrue();
    assertThat(NodeFilters.accepts(record, Material.OAK_SAPLING, TAGS)).isTrue();
  }

  @Test
  void typedTagsNormalizeToNamespacedForm() {
    assertThat(NodeFilters.normalizeTag("#logs")).contains(LOGS);
    assertThat(NodeFilters.normalizeTag("logs")).contains(LOGS);
    assertThat(NodeFilters.normalizeTag("  #Minecraft:Logs ")).contains(LOGS);
    assertThat(NodeFilters.normalizeTag("#c:ingots/iron")).contains("#c:ingots/iron");
    assertThat(NodeFilters.normalizeTag(null)).isEmpty();
    assertThat(NodeFilters.normalizeTag("")).isEmpty();
    assertThat(NodeFilters.normalizeTag("#")).isEmpty();
    assertThat(NodeFilters.normalizeTag("#bad tag")).isEmpty();
    assertThat(NodeFilters.normalizeTag("#a:b:c")).isEmpty();
    assertThat(NodeFilters.normalizeTag("#:logs")).isEmpty();
    assertThat(NodeFilters.normalizeTag("#minecraft:")).isEmpty();
    assertThat(NodeFilters.normalizeTag("#mine/craft:logs")).isEmpty();
  }

  private static NodeRecord.Builder record() {
    return NodeRecord.builder(OWNER, NodeType.COLLECTION, BlockFace.UP);
  }

  private static ItemStack item(Material type) {
    ItemStack item = mock(ItemStack.class);
    lenient().when(item.getType()).thenReturn(type);
    lenient().when(item.clone()).thenReturn(item);
    return item;
  }
}
