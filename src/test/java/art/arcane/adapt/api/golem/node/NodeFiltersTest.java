package art.arcane.adapt.api.golem.node;

import art.arcane.adapt.api.golem.NodeType;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.junit.jupiter.api.Test;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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

  private final Map<ItemStack, String> identities = new IdentityHashMap<>();

  @Test
  void togglingAnAbsentItemAddsItUnderTheSlotCap() {
    NodeRecord record = record().build();
    ItemStack cobble = item(Material.COBBLESTONE);

    NodeFilters.FilterEdit edit = NodeFilters.toggleItem(record, cobble, 3);

    assertThat(edit.outcome()).isEqualTo(NodeFilters.Outcome.ADDED);
    assertThat(edit.record().filterItems()).extracting(ItemStack::getType).containsExactly(Material.COBBLESTONE);
  }

  @Test
  void aGhostIsOneOfTheHeldItemWithoutBundleContents() {
    ItemStack held = mock(ItemStack.class);
    ItemStack copy = mock(ItemStack.class);
    BundleMeta bundle = mock(BundleMeta.class);
    when(held.clone()).thenReturn(copy);
    when(copy.getItemMeta()).thenReturn(bundle);
    when(bundle.hasItems()).thenReturn(true);

    assertThat(NodeFilters.ghost(held)).isSameAs(copy);

    verify(copy).setAmount(1);
    verify(bundle).setItems(List.of());
    verify(copy).setItemMeta(bundle);
    verify(held, never()).setAmount(1);
  }

  @Test
  void aGhostShulkerBoxKeepsItsBlockButDropsItsContents() {
    ItemStack held = mock(ItemStack.class);
    ItemStack copy = mock(ItemStack.class);
    BlockStateMeta meta = mock(BlockStateMeta.class);
    ShulkerBox box = mock(ShulkerBox.class);
    Inventory contents = mock(Inventory.class);
    when(held.clone()).thenReturn(copy);
    when(copy.getItemMeta()).thenReturn(meta);
    when(meta.hasBlockState()).thenReturn(true);
    when(meta.getBlockState()).thenReturn(box);
    when(box.getInventory()).thenReturn(contents);

    NodeFilters.ghost(held);

    verify(contents).clear();
    verify(meta).setBlockState(box);
    verify(copy).setItemMeta(meta);
  }

  @Test
  void togglingASimilarItemRemovesIt() {
    NodeRecord record = record().filterItems(List.of(item(Material.COBBLESTONE), item(Material.WHEAT))).build();

    NodeFilters.FilterEdit edit = NodeFilters.toggleItem(record, item(Material.COBBLESTONE), 3);

    assertThat(edit.outcome()).isEqualTo(NodeFilters.Outcome.REMOVED);
    assertThat(edit.record().filterItems()).extracting(ItemStack::getType).containsExactly(Material.WHEAT);
  }

  @Test
  void twoPotionsOfDifferentTypesAreBothStoredAndTogglingRemovesTheSimilarOne() {
    ItemStack swiftness = item(Material.POTION, "swiftness");
    ItemStack healing = item(Material.POTION, "healing");

    NodeRecord both = NodeFilters.toggleItem(NodeFilters.toggleItem(record().build(), swiftness, 3).record(), healing, 3).record();
    NodeFilters.FilterEdit removed = NodeFilters.toggleItem(both, item(Material.POTION, "swiftness"), 3);

    assertThat(both.filterItems()).containsExactly(swiftness, healing);
    assertThat(removed.outcome()).isEqualTo(NodeFilters.Outcome.REMOVED);
    assertThat(removed.record().filterItems()).containsExactly(healing);
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
  void anEmptySlotAppendsUnlessTheFilterHoldsASimilarItemOrIsFull() {
    ItemStack cobble = item(Material.COBBLESTONE);
    NodeRecord record = record().filterItems(List.of(cobble, item(Material.WHEAT))).build();

    NodeFilters.FilterEdit added = NodeFilters.addItem(record, item(Material.STONE), 3);
    NodeFilters.FilterEdit similar = NodeFilters.addItem(record, item(Material.COBBLESTONE), 3);
    NodeFilters.FilterEdit full = NodeFilters.addItem(added.record(), item(Material.DIRT), 3);

    assertThat(added.outcome()).isEqualTo(NodeFilters.Outcome.ADDED);
    assertThat(added.record().filterItems()).extracting(ItemStack::getType).containsExactly(Material.COBBLESTONE, Material.WHEAT, Material.STONE);
    assertThat(similar.outcome()).isEqualTo(NodeFilters.Outcome.UNCHANGED);
    assertThat(full.outcome()).isEqualTo(NodeFilters.Outcome.FULL);
  }

  @Test
  void aSlotReplacesTheEntryItShowedEvenAfterTheListShifted() {
    ItemStack cobble = item(Material.COBBLESTONE);
    ItemStack wheat = item(Material.WHEAT);
    ItemStack stone = item(Material.STONE);
    NodeRecord shifted = record().filterItems(List.of(wheat, stone)).build();

    NodeFilters.FilterEdit replaced = NodeFilters.replaceItem(shifted, wheat, item(Material.DIRT));

    assertThat(replaced.outcome()).isEqualTo(NodeFilters.Outcome.REPLACED);
    assertThat(replaced.record().filterItems()).extracting(ItemStack::getType).containsExactly(Material.DIRT, Material.STONE);
    assertThat(NodeFilters.replaceItem(shifted, cobble, item(Material.DIRT)).outcome()).isEqualTo(NodeFilters.Outcome.UNCHANGED);
    assertThat(NodeFilters.replaceItem(shifted, wheat, item(Material.WHEAT)).outcome()).isEqualTo(NodeFilters.Outcome.UNCHANGED);
  }

  @Test
  void replacingWithAnItemHeldInAnotherSlotMovesIt() {
    ItemStack cobble = item(Material.COBBLESTONE);
    NodeRecord record = record().filterItems(List.of(cobble, item(Material.WHEAT))).build();

    NodeFilters.FilterEdit moved = NodeFilters.replaceItem(record, cobble, item(Material.WHEAT));

    assertThat(moved.outcome()).isEqualTo(NodeFilters.Outcome.REPLACED);
    assertThat(moved.record().filterItems()).extracting(ItemStack::getType).containsExactly(Material.WHEAT);
  }

  @Test
  void clearingASlotRemovesTheSimilarItemOrTheTag() {
    NodeRecord record = record()
        .filterItems(List.of(item(Material.COBBLESTONE), item(Material.POTION, "healing")))
        .filterTags(List.of(LOGS, SAPLINGS))
        .build();

    assertThat(NodeFilters.removeItem(record, item(Material.POTION, "healing")).filterItems()).extracting(ItemStack::getType).containsExactly(Material.COBBLESTONE);
    assertThat(NodeFilters.removeItem(record, item(Material.POTION, "swiftness"))).isSameAs(record);
    assertThat(NodeFilters.removeTag(record, LOGS).filterTags()).containsExactly(SAPLINGS);
    assertThat(NodeFilters.removeTag(record, "#minecraft:flowers")).isSameAs(record);
  }

  @Test
  void matchingStaysByItemTypeForStoredItems() {
    NodeRecord record = record().filterItems(List.of(item(Material.POTION, "healing"))).build();

    assertThat(NodeFilters.accepts(record, Material.POTION, TAGS)).isTrue();
    assertThat(NodeFilters.accepts(record, Material.SPLASH_POTION, TAGS)).isFalse();
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

  private ItemStack item(Material type) {
    return item(type, "");
  }

  private ItemStack item(Material type, String variant) {
    ItemStack item = mock(ItemStack.class);
    String identity = type + "/" + variant;
    identities.put(item, identity);
    lenient().when(item.getType()).thenReturn(type);
    lenient().when(item.clone()).thenReturn(item);
    lenient().when(item.isSimilar(any())).thenAnswer(invocation -> identity.equals(identities.get(invocation.<ItemStack>getArgument(0))));
    return item;
  }
}
