package art.arcane.adapt.content.item;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.util.common.io.BukkitGson;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BackpackItemTest extends AdaptTestBase {
  @BeforeEach
  void configurePluginIdentity() {
    lenient().when(plugin.namespace()).thenReturn("adapt");
  }

  // ------------------------------------------------------------------ sizing

  @Test
  void capacitySnapsToTheNearestContainerRepresentableSize() {
    assertThat(BackpackItem.snapCapacity(9)).isEqualTo(9);
    assertThat(BackpackItem.snapCapacity(18)).isEqualTo(18);
    assertThat(BackpackItem.snapCapacity(27)).isEqualTo(27);
    assertThat(BackpackItem.snapCapacity(36)).isEqualTo(36);
    assertThat(BackpackItem.snapCapacity(45)).isEqualTo(45);
    assertThat(BackpackItem.snapCapacity(54)).isEqualTo(54);
  }

  @Test
  void capacityClampsBelowNineAndAboveFiftyFour() {
    assertThat(BackpackItem.snapCapacity(0)).isEqualTo(9);
    assertThat(BackpackItem.snapCapacity(-40)).isEqualTo(9);
    assertThat(BackpackItem.snapCapacity(1)).isEqualTo(9);
    assertThat(BackpackItem.snapCapacity(55)).isEqualTo(54);
    assertThat(BackpackItem.snapCapacity(9999)).isEqualTo(54);
  }

  @Test
  void capacityRoundsToTheNearerValidSizeAtEveryBoundary() {
    assertThat(BackpackItem.snapCapacity(13)).isEqualTo(9);
    assertThat(BackpackItem.snapCapacity(14)).isEqualTo(18);
    assertThat(BackpackItem.snapCapacity(22)).isEqualTo(18);
    assertThat(BackpackItem.snapCapacity(23)).isEqualTo(27);
    assertThat(BackpackItem.snapCapacity(31)).isEqualTo(27);
    assertThat(BackpackItem.snapCapacity(32)).isEqualTo(36);
    assertThat(BackpackItem.snapCapacity(40)).isEqualTo(36);
    assertThat(BackpackItem.snapCapacity(41)).isEqualTo(45);
    assertThat(BackpackItem.snapCapacity(49)).isEqualTo(45);
    assertThat(BackpackItem.snapCapacity(50)).isEqualTo(54);
  }

  @Test
  void slotViewGrowsToShowEverythingWhenTheConfiguredCapacityShrank() {
    assertThat(BackpackItem.slotViewSize(0, 9)).isEqualTo(9);
    assertThat(BackpackItem.slotViewSize(5, 9)).isEqualTo(9);
    assertThat(BackpackItem.slotViewSize(10, 9)).isEqualTo(18);
    assertThat(BackpackItem.slotViewSize(28, 9)).isEqualTo(36);
    assertThat(BackpackItem.slotViewSize(54, 9)).isEqualTo(54);
    assertThat(BackpackItem.slotViewSize(3, 54)).isEqualTo(54);
  }

  @Test
  void onlyStacksBeyondADoubleChestCountAsOverflowToDeliver() {
    assertThat(BackpackItem.slotOverflow(54)).isZero();
    assertThat(BackpackItem.slotOverflow(0)).isZero();
    assertThat(BackpackItem.slotOverflow(57)).isEqualTo(3);
  }

  // ------------------------------------------------------------------ weight

  @Test
  void unitWeightMirrorsVanillaBundleCosts() {
    assertThat(BackpackItem.unitWeight(64)).isEqualTo(1);
    assertThat(BackpackItem.unitWeight(16)).isEqualTo(4);
    assertThat(BackpackItem.unitWeight(1)).isEqualTo(64);
    assertThat(BackpackItem.unitWeight(0)).isEqualTo(64);
    assertThat(BackpackItem.unitWeight(-5)).isEqualTo(64);
    assertThat(BackpackItem.unitWeight(128)).isEqualTo(1);
  }

  @Test
  void capacityWeightIsTheSnappedStackCountTimesOneStack() {
    assertThat(BackpackItem.capacityWeight(9)).isEqualTo(576);
    assertThat(BackpackItem.capacityWeight(54)).isEqualTo(3456);
    assertThat(BackpackItem.capacityWeight(13)).isEqualTo(576);
  }

  @Test
  void anyFullStackAlwaysFitsWithinOneStackOfWeight() {
    for (int maxStackSize : new int[]{1, 2, 3, 4, 8, 16, 32, 64}) {
      for (int amount = 1; amount <= maxStackSize; amount++) {
        assertThat(BackpackItem.weightOf(BackpackItem.unitWeight(maxStackSize), amount))
            .describedAs("weight of %d/%d", amount, maxStackSize)
            .isLessThanOrEqualTo(BackpackItem.STACK_WEIGHT);
      }
    }
  }

  @Test
  void aSlotModeBackpackAlwaysFitsIntoBundleModeAtTheSameCapacity() {
    for (int capacity : new int[]{9, 18, 27, 36, 45, 54}) {
      int worstCaseWeight = capacity * BackpackItem.STACK_WEIGHT;
      assertThat(worstCaseWeight).isLessThanOrEqualTo(BackpackItem.capacityWeight(capacity));
    }
  }

  @Test
  void acceptableAmountTakesOnlyWhatTheBudgetAllows() {
    assertThat(BackpackItem.acceptableAmount(1, 64, 576)).isEqualTo(64);
    assertThat(BackpackItem.acceptableAmount(4, 16, 40)).isEqualTo(10);
    assertThat(BackpackItem.acceptableAmount(64, 3, 128)).isEqualTo(2);
    assertThat(BackpackItem.acceptableAmount(64, 3, 0)).isZero();
    assertThat(BackpackItem.acceptableAmount(64, 3, 63)).isZero();
    assertThat(BackpackItem.acceptableAmount(0, 3, 100)).isZero();
    assertThat(BackpackItem.acceptableAmount(1, 0, 100)).isZero();
  }

  @Test
  void freeWeightNeverGoesNegative() {
    assertThat(BackpackItem.freeWeight(100, 576)).isEqualTo(476);
    assertThat(BackpackItem.freeWeight(700, 576)).isZero();
  }

  // -------------------------------------------------------------------- heap

  @Test
  void depositFillsMatchingStacksBeforeOpeningNewOnes() {
    ItemStack incoming = stack(Material.STONE, 40, 64);
    ItemStack partial = similar(Material.STONE, 64, incoming);
    ItemStack unrelated = stack(Material.TORCH, 5, 64);
    List<BackpackItem.HeapSlot> heap = new ArrayList<>(List.of(
        new BackpackItem.HeapSlot(unrelated, 5),
        new BackpackItem.HeapSlot(partial, 50)
    ));

    List<BackpackItem.HeapSlot> next = BackpackItem.deposit(heap, incoming, 40);

    assertThat(next).hasSize(3);
    assertThat(next.get(0).amount()).isEqualTo(5);
    assertThat(next.get(1).amount()).isEqualTo(64);
    assertThat(next.get(2).template()).isSameAs(incoming);
    assertThat(next.get(2).amount()).isEqualTo(26);
    assertThat(heap).hasSize(2);
  }

  @Test
  void depositSplitsIntoWholeStacksWhenNothingMatches() {
    ItemStack incoming = stack(Material.STONE, 200, 64);

    List<BackpackItem.HeapSlot> next = BackpackItem.deposit(List.of(), incoming, 140);

    assertThat(next).hasSize(3);
    assertThat(next.get(0).amount()).isEqualTo(64);
    assertThat(next.get(1).amount()).isEqualTo(64);
    assertThat(next.get(2).amount()).isEqualTo(12);
  }

  @Test
  void depositIgnoresNothingToDeposit() {
    ItemStack incoming = stack(Material.STONE, 4, 64);

    assertThat(BackpackItem.deposit(List.of(), incoming, 0)).isEmpty();
    assertThat(BackpackItem.deposit(List.of(), null, 4)).isEmpty();
  }

  @Test
  void withdrawTakesPartOfAStackOrRemovesItEntirely() {
    ItemStack stone = stack(Material.STONE, 64, 64);
    List<BackpackItem.HeapSlot> heap = List.of(new BackpackItem.HeapSlot(stone, 40));

    BackpackItem.HeapWithdrawal half = BackpackItem.withdraw(heap, 0, 20);
    BackpackItem.HeapWithdrawal all = BackpackItem.withdraw(heap, 0, 99);

    assertThat(half.taken()).isEqualTo(20);
    assertThat(half.heap()).hasSize(1);
    assertThat(half.heap().get(0).amount()).isEqualTo(20);
    assertThat(all.taken()).isEqualTo(40);
    assertThat(all.heap()).isEmpty();
    assertThat(heap.get(0).amount()).isEqualTo(40);
  }

  @Test
  void withdrawOutsideTheHeapChangesNothing() {
    ItemStack stone = stack(Material.STONE, 64, 64);
    List<BackpackItem.HeapSlot> heap = List.of(new BackpackItem.HeapSlot(stone, 40));

    assertThat(BackpackItem.withdraw(heap, -1, 4).taken()).isZero();
    assertThat(BackpackItem.withdraw(heap, 5, 4).taken()).isZero();
    assertThat(BackpackItem.withdraw(heap, 0, 0).taken()).isZero();
  }

  @Test
  void heapWeightSumsUnitWeightsAcrossEveryStack() {
    ItemStack stone = stack(Material.STONE, 64, 64);
    ItemStack egg = stack(Material.EGG, 16, 16);
    List<BackpackItem.HeapSlot> heap = List.of(
        new BackpackItem.HeapSlot(stone, 64),
        new BackpackItem.HeapSlot(egg, 16),
        new BackpackItem.HeapSlot(null, 9)
    );

    assertThat(BackpackItem.heapWeight(heap, item -> BackpackItem.unitWeight(item.getMaxStackSize())))
        .isEqualTo(128);
  }

  // -------------------------------------------------------------- projection

  @Test
  void bundleViewsAlwaysReserveTheBottomRowAndFitTheDistinctStacks() {
    assertThat(BackpackItem.bundleViewSize(0)).isEqualTo(18);
    assertThat(BackpackItem.bundleViewSize(9)).isEqualTo(18);
    assertThat(BackpackItem.bundleViewSize(10)).isEqualTo(27);
    assertThat(BackpackItem.bundleViewSize(45)).isEqualTo(54);
    assertThat(BackpackItem.bundleViewSize(500)).isEqualTo(54);
  }

  @Test
  void pagingMathCoversEveryStoredStackWithoutHidingAny() {
    assertThat(BackpackItem.pageCapacity(18)).isEqualTo(9);
    assertThat(BackpackItem.pageCapacity(54)).isEqualTo(45);
    assertThat(BackpackItem.pageCount(0, 45)).isEqualTo(1);
    assertThat(BackpackItem.pageCount(45, 45)).isEqualTo(1);
    assertThat(BackpackItem.pageCount(46, 45)).isEqualTo(2);
    assertThat(BackpackItem.pageCount(500, 45)).isEqualTo(12);
    assertThat(BackpackItem.pageStart(0, 45)).isZero();
    assertThat(BackpackItem.pageStart(2, 45)).isEqualTo(90);
    assertThat(BackpackItem.clampPage(7, 3)).isEqualTo(2);
    assertThat(BackpackItem.clampPage(-4, 3)).isZero();
    assertThat(BackpackItem.clampPage(0, 0)).isZero();
  }

  // ------------------------------------------------------------------ denial

  @Test
  void aBackpackIsAlwaysDeniedAsADeposit() {
    ItemStack nested = backpack("a", BackpackItem.Mode.SLOTS);

    assertThat(BackpackItem.deniesDeposit(nested, false)).isTrue();
    assertThat(BackpackItem.deniesDeposit(nested, true)).isTrue();
  }

  @Test
  void plainItemsAndEmptySlotsAreNeverDenied() {
    assertThat(BackpackItem.deniesDeposit(null, true)).isFalse();
    assertThat(BackpackItem.deniesDeposit(stack(Material.AIR, 1, 64), true)).isFalse();
    assertThat(BackpackItem.deniesDeposit(stack(Material.STONE, 4, 64), true)).isFalse();
  }

  @Test
  void aShulkerBoxHidingABackpackIsDeniedOnlyWhileNestedContainersAreBlocked() {
    ItemStack shulker = shulkerHolding(backpack("b", BackpackItem.Mode.BUNDLE));

    assertThat(BackpackItem.deniesDeposit(shulker, true)).isTrue();
    assertThat(BackpackItem.deniesDeposit(shulker, false)).isFalse();
  }

  @Test
  void aVanillaBundleHidingABackpackIsDeniedTheSameWay() {
    ItemStack carrier = bundleHolding(backpack("c", BackpackItem.Mode.SLOTS));

    assertThat(BackpackItem.deniesDeposit(carrier, true)).isTrue();
    assertThat(BackpackItem.deniesDeposit(carrier, false)).isFalse();
  }

  @Test
  void theNestingScanStopsAtItsDepthLimit() {
    ItemStack deep = bundleHolding(bundleHolding(bundleHolding(bundleHolding(
        bundleHolding(backpack("d", BackpackItem.Mode.SLOTS))))));

    assertThat(BackpackItem.containsBackpack(deep, BackpackItem.NEST_SCAN_DEPTH)).isFalse();
    assertThat(BackpackItem.containsBackpack(deep, 8)).isTrue();
  }

  @Test
  void theSerializedSizeCeilingIsTheBackstopForAnythingTheScanMisses() {
    assertThat(BackpackItem.exceedsCeiling(200_000, 100_000, 262_144)).isTrue();
    assertThat(BackpackItem.exceedsCeiling(200_000, 60_000, 262_144)).isFalse();
    assertThat(BackpackItem.exceedsCeiling(200_000, 100_000, 0)).isFalse();
    assertThat(BackpackItem.exceedsCeiling(-5, -5, 10)).isFalse();
  }

  // ---------------------------------------------------------------- identity

  @Test
  void writeBackTargetsTheStackCarryingTheMatchingIdentity() {
    ItemStack first = backpack("aaa", BackpackItem.Mode.SLOTS);
    ItemStack second = backpack("bbb", BackpackItem.Mode.BUNDLE);
    ItemStack plain = stack(Material.STONE, 1, 64);
    ItemStack[] contents = new ItemStack[]{plain, null, first, second};

    assertThat(BackpackItem.identitySlot(contents, "bbb")).isEqualTo(3);
    assertThat(BackpackItem.identitySlot(contents, "aaa")).isEqualTo(2);
    assertThat(BackpackItem.identitySlot(contents, "ccc")).isEqualTo(-1);
    assertThat(BackpackItem.identitySlot(contents, null)).isEqualTo(-1);
    assertThat(BackpackItem.identitySlot(null, "aaa")).isEqualTo(-1);
  }

  @Test
  void modeAndIdentityRoundTripThroughTheItemsOwnData() {
    ItemStack slots = backpack("one", BackpackItem.Mode.SLOTS);
    ItemStack bundle = backpack("two", BackpackItem.Mode.BUNDLE);

    assertThat(BackpackItem.modeOf(slots)).isEqualTo(BackpackItem.Mode.SLOTS);
    assertThat(BackpackItem.modeOf(bundle)).isEqualTo(BackpackItem.Mode.BUNDLE);
    assertThat(BackpackItem.identityOf(slots)).isEqualTo("one");
    assertThat(BackpackItem.identityOf(bundle)).isEqualTo("two");
    assertThat(BackpackItem.identityOf(stack(Material.STONE, 1, 64))).isNull();
  }

  @Test
  void modeParsingIsForgivingAndAlwaysFallsBackToSlots() {
    assertThat(BackpackItem.Mode.parse("BUNDLE")).isEqualTo(BackpackItem.Mode.BUNDLE);
    assertThat(BackpackItem.Mode.parse(" bundle ")).isEqualTo(BackpackItem.Mode.BUNDLE);
    assertThat(BackpackItem.Mode.parse("SLOTS")).isEqualTo(BackpackItem.Mode.SLOTS);
    assertThat(BackpackItem.Mode.parse("nonsense")).isEqualTo(BackpackItem.Mode.SLOTS);
    assertThat(BackpackItem.Mode.parse(null)).isEqualTo(BackpackItem.Mode.SLOTS);
    assertThat(BackpackItem.Mode.SLOTS.cycle()).isEqualTo(BackpackItem.Mode.BUNDLE);
    assertThat(BackpackItem.Mode.BUNDLE.cycle()).isEqualTo(BackpackItem.Mode.SLOTS);
  }

  // -------------------------------------------------------------- extraction

  @Test
  void extractionPullsTheFirstMatchingStackOutOfASlotIndexedBackpack() {
    ItemStack template = stack(Material.STONE, 1, 64);
    ItemStack match = similar(Material.STONE, 32, template);
    ItemStack other = stack(Material.DIRT, 8, 64);
    ItemStack[] contents = new ItemStack[]{null, other, match, null};

    BackpackItem.BackpackExtraction extraction = BackpackItem.extractMatching(contents, template);

    assertThat(extraction).isNotNull();
    assertThat(extraction.pulled()).isSameAs(match);
    assertThat(extraction.remaining()).containsExactly(null, other, null, null);
    assertThat(contents[2]).isSameAs(match);
  }

  @Test
  void extractionWorksTheSameOnACompactedBundleHeap() {
    ItemStack template = stack(Material.STONE, 1, 64);
    ItemStack other = stack(Material.DIRT, 8, 64);
    ItemStack match = similar(Material.STONE, 64, template);
    ItemStack[] heap = new ItemStack[]{other, match};

    BackpackItem.BackpackExtraction extraction = BackpackItem.extractMatching(heap, template);

    assertThat(extraction).isNotNull();
    assertThat(BackpackItem.compact(extraction.remaining())).containsExactly(other);
  }

  @Test
  void extractionReportsNothingWhenTheBackpackCannotHelp() {
    ItemStack template = stack(Material.STONE, 1, 64);

    assertThat(BackpackItem.extractMatching(new ItemStack[0], template)).isNull();
    assertThat(BackpackItem.extractMatching(null, template)).isNull();
    assertThat(BackpackItem.extractMatching(new ItemStack[]{stack(Material.DIRT, 1, 64)}, template)).isNull();
    assertThat(BackpackItem.extractMatching(new ItemStack[]{stack(Material.STONE, 0, 64)}, template)).isNull();
  }

  @Test
  void countingAndCompactingIgnoreEmptyEntries() {
    ItemStack real = stack(Material.STONE, 4, 64);
    ItemStack[] contents = new ItemStack[]{null, real, stack(Material.AIR, 1, 64), stack(Material.DIRT, 0, 64)};

    assertThat(BackpackItem.countStored(contents)).isEqualTo(1);
    assertThat(BackpackItem.compact(contents)).containsExactly(real);
    assertThat(BackpackItem.countStored(null)).isZero();
    assertThat(BackpackItem.compact(null)).isEmpty();
  }

  @Test
  void readingAHeapDropsHolesAndKeepsRealAmounts() {
    ItemStack stone = stack(Material.STONE, 12, 64);
    List<BackpackItem.HeapSlot> heap = BackpackItem.readHeap(new ItemStack[]{null, stone, stack(Material.AIR, 3, 64)});

    assertThat(heap).hasSize(1);
    assertThat(heap.get(0).template()).isSameAs(stone);
    assertThat(heap.get(0).amount()).isEqualTo(12);
  }

  // ----------------------------------------------------------------- helpers

  private static ItemStack stack(Material type, int amount, int maxStackSize) {
    ItemStack stack = mock(ItemStack.class);
    lenient().when(stack.getType()).thenReturn(type);
    lenient().when(stack.getAmount()).thenReturn(amount);
    lenient().when(stack.getMaxStackSize()).thenReturn(maxStackSize);
    lenient().when(stack.clone()).thenReturn(stack);
    return stack;
  }

  private static ItemStack similar(Material type, int amount, ItemStack template) {
    ItemStack stack = stack(type, amount, template.getMaxStackSize());
    lenient().when(stack.isSimilar(template)).thenReturn(true);
    lenient().when(template.isSimilar(stack)).thenReturn(true);
    return stack;
  }

  private static ItemStack backpack(String id, BackpackItem.Mode mode) {
    ItemStack stack = stack(Material.BUNDLE, 1, 1);
    ItemMeta meta = mock(ItemMeta.class);
    PersistentDataContainer data = mock(PersistentDataContainer.class);
    lenient().when(stack.getItemMeta()).thenReturn(meta);
    lenient().when(meta.getPersistentDataContainer()).thenReturn(data);
    lenient().when(data.has(any(NamespacedKey.class), eq(PersistentDataType.STRING))).thenReturn(true);
    lenient().when(data.get(any(NamespacedKey.class), eq(PersistentDataType.STRING)))
        .thenReturn(BukkitGson.gson.toJson(BackpackItem.Data.of(id, mode, 9, 0)));
    return stack;
  }

  private static ItemStack bundleHolding(ItemStack inner) {
    ItemStack stack = stack(Material.BUNDLE, 1, 1);
    BundleMeta meta = mock(BundleMeta.class);
    PersistentDataContainer data = mock(PersistentDataContainer.class);
    lenient().when(stack.getItemMeta()).thenReturn(meta);
    lenient().when(meta.getPersistentDataContainer()).thenReturn(data);
    lenient().when(data.has(any(NamespacedKey.class), eq(PersistentDataType.STRING))).thenReturn(false);
    lenient().when(meta.hasItems()).thenReturn(true);
    lenient().when(meta.getItems()).thenReturn(List.of(inner));
    return stack;
  }

  private static ItemStack shulkerHolding(ItemStack inner) {
    ItemStack stack = stack(Material.SHULKER_BOX, 1, 1);
    BlockStateMeta meta = mock(BlockStateMeta.class);
    PersistentDataContainer data = mock(PersistentDataContainer.class);
    ShulkerBox box = mock(ShulkerBox.class);
    Inventory inventory = mock(Inventory.class);
    lenient().when(stack.getItemMeta()).thenReturn(meta);
    lenient().when(meta.getPersistentDataContainer()).thenReturn(data);
    lenient().when(data.has(any(NamespacedKey.class), eq(PersistentDataType.STRING))).thenReturn(false);
    lenient().when(meta.hasBlockState()).thenReturn(true);
    lenient().when(meta.getBlockState()).thenReturn(box);
    lenient().when(box.getInventory()).thenReturn(inventory);
    when(inventory.getContents()).thenReturn(new ItemStack[]{null, inner});
    return stack;
  }
}
