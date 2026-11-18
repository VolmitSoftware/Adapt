package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.api.data.StoredItem;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.BlockFace;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NodeFiltersTest {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final String LOGS = "#minecraft:logs";
  private static final String SAPLINGS = "#minecraft:saplings";
  private static final NodeTagLookup TAGS = new NodeTagLookup() {
    private final Map<String, Set<Id>> tags = Map.of(
        LOGS, Set.of(Id.minecraft("oak_log"), Id.minecraft("birch_log")),
        SAPLINGS, Set.of(Id.minecraft("oak_sapling")));

    @Override
    public boolean exists(String tag) {
      return tags.containsKey(tag);
    }

    @Override
    public boolean tagged(String tag, Id material) {
      return tags.getOrDefault(tag, Set.of()).contains(material);
    }
  };

  private static final IntFunction<byte[]> UNREADABLE_BYTES = index -> ("not an item " + index).getBytes(StandardCharsets.US_ASCII);

  private final Map<ItemRef, String> identities = new IdentityHashMap<>();

  @Test
  void togglingAnAbsentItemAddsItUnderTheSlotCap() {
    NodeRecord record = record().build();
    ItemRef cobble = item(Id.minecraft("cobblestone"));

    NodeFilters.FilterEdit edit = NodeFilters.toggleItem(record, cobble, 3);

    assertThat(edit.outcome()).isEqualTo(NodeFilters.Outcome.ADDED);
    assertThat(edit.record().filterItems()).extracting(ItemRef::type).containsExactly(Id.minecraft("cobblestone"));
  }

  @Test
  void aGhostIsOneOfTheHeldItemWithoutBundleContents() {
    ItemRef held = mock(ItemRef.class);
    ItemRef copy = mock(ItemRef.class);
    when(held.copyWithAmount(1)).thenReturn(copy);
    when(copy.clearStoredItems()).thenReturn(true);

    assertThat(NodeFilters.ghost(held)).isSameAs(copy);

    verify(held).copyWithAmount(1);
    verify(copy).clearStoredItems();
    verify(held, never()).setAmount(1);
    verify(held, never()).clearStoredItems();
  }

  @Test
  void aGhostShulkerBoxKeepsItsBlockButDropsItsContents() {
    ItemRef held = mock(ItemRef.class);
    ItemRef copy = mock(ItemRef.class);
    when(held.copyWithAmount(1)).thenReturn(copy);
    when(copy.type()).thenReturn(Id.minecraft("red_shulker_box"));

    NodeFilters.ghost(held);

    verify(copy).clearStoredItems();
    verify(copy, never()).setItemModel(any());
    verify(held, never()).clearStoredItems();
  }

  @Test
  void togglingASimilarItemRemovesIt() {
    NodeRecord record = record().filterItems(List.of(item(Id.minecraft("cobblestone")), item(Id.minecraft("wheat")))).build();

    NodeFilters.FilterEdit edit = NodeFilters.toggleItem(record, item(Id.minecraft("cobblestone")), 3);

    assertThat(edit.outcome()).isEqualTo(NodeFilters.Outcome.REMOVED);
    assertThat(edit.record().filterItems()).extracting(ItemRef::type).containsExactly(Id.minecraft("wheat"));
  }

  @Test
  void twoPotionsOfDifferentTypesAreBothStoredAndTogglingRemovesTheSimilarOne() {
    ItemRef swiftness = item(Id.minecraft("potion"), "swiftness");
    ItemRef healing = item(Id.minecraft("potion"), "healing");

    NodeRecord both = NodeFilters.toggleItem(NodeFilters.toggleItem(record().build(), swiftness, 3).record(), healing, 3).record();
    NodeFilters.FilterEdit removed = NodeFilters.toggleItem(both, item(Id.minecraft("potion"), "swiftness"), 3);

    assertThat(both.filterItems()).containsExactly(swiftness, healing);
    assertThat(removed.outcome()).isEqualTo(NodeFilters.Outcome.REMOVED);
    assertThat(removed.record().filterItems()).containsExactly(healing);
  }

  @Test
  void aFullFilterRefusesNewItemsButStillRemovesEntriesAboveALoweredCap() {
    NodeRecord record = record()
        .filterItems(List.of(item(Id.minecraft("cobblestone")), item(Id.minecraft("wheat"))))
        .filterTags(List.of(LOGS))
        .build();

    NodeFilters.FilterEdit refused = NodeFilters.toggleItem(record, item(Id.minecraft("stone")), 3);
    NodeFilters.FilterEdit removed = NodeFilters.toggleItem(record, item(Id.minecraft("wheat")), 1);

    assertThat(refused.outcome()).isEqualTo(NodeFilters.Outcome.FULL);
    assertThat(refused.record()).isSameAs(record);
    assertThat(removed.outcome()).isEqualTo(NodeFilters.Outcome.REMOVED);
    assertThat(removed.record().filterCount()).isEqualTo(2);
  }

  @Test
  void theSlotCapNeverExceedsTheRecordLimit() {
    NodeRecord record = record().build();
    for (int index = 0; index < NodeRecord.MAX_FILTER_ENTRIES; index++) {
      record = NodeFilters.toggleItem(record, item(Id.minecraft("filler_" + index)), 64).record();
    }

    assertThat(record.filterCount()).isEqualTo(NodeRecord.MAX_FILTER_ENTRIES);
    assertThat(NodeFilters.toggleItem(record, item(Id.minecraft("diamond")), 64).outcome()).isEqualTo(NodeFilters.Outcome.FULL);
  }

  @Test
  void tagsToggleAndShareTheSlotCapWithItems() {
    NodeRecord record = record().filterItems(List.of(item(Id.minecraft("cobblestone")))).build();

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
  void anEmptySlotAppendsUnlessTheFilterHoldsASimilarItemOrIsFull() {
    ItemRef cobble = item(Id.minecraft("cobblestone"));
    NodeRecord record = record().filterItems(List.of(cobble, item(Id.minecraft("wheat")))).build();

    NodeFilters.FilterEdit added = NodeFilters.addItem(record, item(Id.minecraft("stone")), 3);
    NodeFilters.FilterEdit similar = NodeFilters.addItem(record, item(Id.minecraft("cobblestone")), 3);
    NodeFilters.FilterEdit full = NodeFilters.addItem(added.record(), item(Id.minecraft("dirt")), 3);

    assertThat(added.outcome()).isEqualTo(NodeFilters.Outcome.ADDED);
    assertThat(added.record().filterItems()).extracting(ItemRef::type).containsExactly(Id.minecraft("cobblestone"), Id.minecraft("wheat"), Id.minecraft("stone"));
    assertThat(similar.outcome()).isEqualTo(NodeFilters.Outcome.UNCHANGED);
    assertThat(full.outcome()).isEqualTo(NodeFilters.Outcome.FULL);
  }

  @Test
  void aSlotReplacesTheEntryItShowedEvenAfterTheListShifted() {
    ItemRef cobble = item(Id.minecraft("cobblestone"));
    ItemRef wheat = item(Id.minecraft("wheat"));
    ItemRef stone = item(Id.minecraft("stone"));
    NodeRecord shifted = record().filterItems(List.of(wheat, stone)).build();

    NodeFilters.FilterEdit replaced = NodeFilters.replaceItem(shifted, wheat, item(Id.minecraft("dirt")));

    assertThat(replaced.outcome()).isEqualTo(NodeFilters.Outcome.REPLACED);
    assertThat(replaced.record().filterItems()).extracting(ItemRef::type).containsExactly(Id.minecraft("dirt"), Id.minecraft("stone"));
    assertThat(NodeFilters.replaceItem(shifted, cobble, item(Id.minecraft("dirt"))).outcome()).isEqualTo(NodeFilters.Outcome.UNCHANGED);
    assertThat(NodeFilters.replaceItem(shifted, wheat, item(Id.minecraft("wheat"))).outcome()).isEqualTo(NodeFilters.Outcome.UNCHANGED);
  }

  @Test
  void replacingWithAnItemHeldInAnotherSlotMovesIt() {
    ItemRef cobble = item(Id.minecraft("cobblestone"));
    NodeRecord record = record().filterItems(List.of(cobble, item(Id.minecraft("wheat")))).build();

    NodeFilters.FilterEdit moved = NodeFilters.replaceItem(record, cobble, item(Id.minecraft("wheat")));

    assertThat(moved.outcome()).isEqualTo(NodeFilters.Outcome.REPLACED);
    assertThat(moved.record().filterItems()).extracting(ItemRef::type).containsExactly(Id.minecraft("wheat"));
  }

  @Test
  void clearingASlotRemovesTheSimilarItemOrTheTag() {
    NodeRecord record = record()
        .filterItems(List.of(item(Id.minecraft("cobblestone")), item(Id.minecraft("potion"), "healing")))
        .filterTags(List.of(LOGS, SAPLINGS))
        .build();

    assertThat(NodeFilters.removeItem(record, item(Id.minecraft("potion"), "healing")).filterItems()).extracting(ItemRef::type).containsExactly(Id.minecraft("cobblestone"));
    assertThat(NodeFilters.removeItem(record, item(Id.minecraft("potion"), "swiftness"))).isSameAs(record);
    assertThat(NodeFilters.removeTag(record, LOGS).filterTags()).containsExactly(SAPLINGS);
    assertThat(NodeFilters.removeTag(record, "#minecraft:flowers")).isSameAs(record);
  }

  @Test
  void matchingStaysByItemTypeForStoredItems() {
    NodeRecord record = record().filterItems(List.of(item(Id.minecraft("potion"), "healing"))).build();

    assertThat(NodeFilters.accepts(record, Id.minecraft("potion"), TAGS)).isTrue();
    assertThat(NodeFilters.accepts(record, Id.minecraft("splash_potion"), TAGS)).isFalse();
  }

  @Test
  void storedItemsCanMatchByAGivenSimilarityWhileTagsMatchByType() {
    NodeRecord record = record().filterItems(List.of(item(Id.minecraft("potion"), "healing"))).filterTags(List.of(LOGS)).build();
    BiPredicate<ItemRef, ItemRef> variant = (stored, candidate) -> identities.get(stored).equals(identities.get(candidate));

    assertThat(NodeFilters.accepts(record, item(Id.minecraft("potion"), "healing"), TAGS, variant)).isTrue();
    assertThat(NodeFilters.accepts(record, item(Id.minecraft("potion"), "swiftness"), TAGS, variant)).isFalse();
    assertThat(NodeFilters.accepts(record, item(Id.minecraft("oak_log")), TAGS, variant)).isTrue();
    assertThat(NodeFilters.accepts(record.toBuilder().whitelist(false).build(), item(Id.minecraft("potion"), "swiftness"), TAGS, variant)).isTrue();
    assertThat(NodeFilters.accepts(record().build(), item(Id.minecraft("stone")), TAGS, variant)).isTrue();
  }

  @Test
  void anEmptyFilterAcceptsEverythingInEitherMode() {
    assertThat(NodeFilters.accepts(record().build(), Id.minecraft("stone"), TAGS)).isTrue();
    assertThat(NodeFilters.accepts(record().whitelist(false).build(), Id.minecraft("stone"), TAGS)).isTrue();
  }

  @Test
  void aWhitelistAcceptsOnlyMatchingItemsAndTags() {
    NodeRecord record = record().filterItems(List.of(item(Id.minecraft("wheat")))).filterTags(List.of(LOGS)).build();

    assertThat(NodeFilters.accepts(record, Id.minecraft("wheat"), TAGS)).isTrue();
    assertThat(NodeFilters.accepts(record, Id.minecraft("birch_log"), TAGS)).isTrue();
    assertThat(NodeFilters.accepts(record, Id.minecraft("stone"), TAGS)).isFalse();
    assertThat(NodeFilters.accepts(record, Id.minecraft("oak_sapling"), TAGS)).isFalse();
  }

  @Test
  void aBlacklistAcceptsEverythingExceptMatchingItemsAndTags() {
    NodeRecord record = record().whitelist(false).filterItems(List.of(item(Id.minecraft("wheat")))).filterTags(List.of(LOGS)).build();

    assertThat(NodeFilters.accepts(record, Id.minecraft("wheat"), TAGS)).isFalse();
    assertThat(NodeFilters.accepts(record, Id.minecraft("oak_log"), TAGS)).isFalse();
    assertThat(NodeFilters.accepts(record, Id.minecraft("stone"), TAGS)).isTrue();
    assertThat(NodeFilters.accepts(record, Id.minecraft("oak_sapling"), TAGS)).isTrue();
  }

  @Test
  void aWhitelistWithAnUnreadableEntryNeverWidensToEverything() {
    NodeRecord only = record().filterEntries(unreadable(1)).build();
    NodeRecord beside = record().filterEntries(unreadable(1)).filterItems(List.of(item(Id.minecraft("wheat")))).build();
    ItemRef stone = item(Id.minecraft("stone"));

    assertThat(only.filterItems()).isEmpty();
    assertThat(only.filterCount()).isEqualTo(1);
    assertThat(NodeFilters.accepts(only, Id.minecraft("stone"), TAGS)).isFalse();
    assertThat(NodeFilters.accepts(only, stone, TAGS, ItemRef::isSimilar)).isFalse();
    assertThat(NodeFilters.accepts(beside, Id.minecraft("wheat"), TAGS)).isTrue();
    assertThat(NodeFilters.accepts(beside, Id.minecraft("stone"), TAGS)).isFalse();
  }

  @Test
  void aBlacklistWithAnUnreadableEntryAcceptsNothing() {
    NodeRecord record = record().whitelist(false).filterEntries(unreadable(1)).filterItems(List.of(item(Id.minecraft("wheat")))).build();
    ItemRef stone = item(Id.minecraft("stone"));

    assertThat(NodeFilters.accepts(record, Id.minecraft("wheat"), TAGS)).isFalse();
    assertThat(NodeFilters.accepts(record, Id.minecraft("stone"), TAGS)).isFalse();
    assertThat(NodeFilters.accepts(record, stone, TAGS, ItemRef::isSimilar)).isFalse();
  }

  @Test
  void aZeroLengthMarkerHoldsItsSlotLikeAnyUnreadableEntry() {
    NodeRecord whitelist = record().filterEntries(List.of(StoredItem.stored(new byte[0]))).build();
    NodeRecord blacklist = whitelist.toBuilder().whitelist(false).build();

    assertThat(whitelist.unreadableFilters()).isEqualTo(1);
    assertThat(NodeFilters.accepts(whitelist, Id.minecraft("stone"), TAGS)).isFalse();
    assertThat(NodeFilters.accepts(blacklist, Id.minecraft("stone"), TAGS)).isFalse();
  }

  @Test
  void unreadableEntriesHoldTheirSlotsAndTheirBytesThroughEdits() {
    List<StoredItem> entries = unreadable(2);
    NodeRecord record = record().filterEntries(entries).build();
    ItemRef cobble = item(Id.minecraft("cobblestone"));

    NodeFilters.FilterEdit full = NodeFilters.toggleItem(record, cobble, 2);
    NodeFilters.FilterEdit added = NodeFilters.toggleItem(record, cobble, 3);
    NodeFilters.FilterEdit tagged = NodeFilters.toggleTag(added.record(), LOGS, 4);
    NodeRecord removed = NodeFilters.removeItem(tagged.record(), cobble);

    assertThat(full.outcome()).isEqualTo(NodeFilters.Outcome.FULL);
    assertThat(added.outcome()).isEqualTo(NodeFilters.Outcome.ADDED);
    assertThat(added.record().unreadableFilters()).isEqualTo(2);
    assertThat(added.record().filterCount()).isEqualTo(3);
    assertThat(added.record().filterItems()).containsExactly(cobble);
    assertThat(tagged.record().filterCount()).isEqualTo(4);
    assertThat(removed.filterItems()).isEmpty();
    assertThat(removed.filterCount()).isEqualTo(3);
    for (NodeRecord edited : List.of(added.record(), tagged.record(), removed)) {
      assertThat(edited.filterEntries()).filteredOn(entry -> !entry.readable()).containsExactlyElementsOf(entries);
      assertThat(edited.filterEntries()).filteredOn(entry -> !entry.readable()).extracting(StoredItem::bytes)
          .containsExactly(UNREADABLE_BYTES.apply(0), UNREADABLE_BYTES.apply(1));
    }
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

  // Bytes no item codec reads: the real decoder rejects them before it touches the server.
  private static List<StoredItem> unreadable(int count) {
    List<StoredItem> entries = new ArrayList<>(count);
    for (int index = 0; index < count; index++) {
      entries.add(StoredItem.stored(UNREADABLE_BYTES.apply(index)));
    }
    return entries;
  }

  private static NodeRecord.Builder record() {
    return NodeRecord.builder(OWNER, NodeType.COLLECTION, BlockFace.UP);
  }

  private ItemRef item(Id type) {
    return item(type, "");
  }

  private ItemRef item(Id type, String variant) {
    ItemRef item = mock(ItemRef.class);
    String identity = type + "/" + variant;
    identities.put(item, identity);
    lenient().when(item.type()).thenReturn(type);
    lenient().when(item.copy()).thenReturn(item);
    lenient().when(item.isSimilar(any())).thenAnswer(invocation -> identity.equals(identities.get(invocation.<ItemRef>getArgument(0))));
    return item;
  }
}
