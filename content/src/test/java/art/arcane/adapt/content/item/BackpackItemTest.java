package art.arcane.adapt.content.item;

import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakeItem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

class BackpackItemTest {
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
    ItemRef incoming = stack(Id.minecraft("stone"), 40, 64);
    ItemRef partial = similar(Id.minecraft("stone"), 64, incoming);
    ItemRef unrelated = stack(Id.minecraft("torch"), 5, 64);
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
    ItemRef incoming = stack(Id.minecraft("stone"), 200, 64);

    List<BackpackItem.HeapSlot> next = BackpackItem.deposit(List.of(), incoming, 140);

    assertThat(next).hasSize(3);
    assertThat(next.get(0).amount()).isEqualTo(64);
    assertThat(next.get(1).amount()).isEqualTo(64);
    assertThat(next.get(2).amount()).isEqualTo(12);
  }

  @Test
  void depositIgnoresNothingToDeposit() {
    ItemRef incoming = stack(Id.minecraft("stone"), 4, 64);

    assertThat(BackpackItem.deposit(List.of(), incoming, 0)).isEmpty();
    assertThat(BackpackItem.deposit(List.of(), null, 4)).isEmpty();
  }

  @Test
  void withdrawTakesPartOfAStackOrRemovesItEntirely() {
    ItemRef stone = stack(Id.minecraft("stone"), 64, 64);
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
    ItemRef stone = stack(Id.minecraft("stone"), 64, 64);
    List<BackpackItem.HeapSlot> heap = List.of(new BackpackItem.HeapSlot(stone, 40));

    assertThat(BackpackItem.withdraw(heap, -1, 4).taken()).isZero();
    assertThat(BackpackItem.withdraw(heap, 5, 4).taken()).isZero();
    assertThat(BackpackItem.withdraw(heap, 0, 0).taken()).isZero();
  }

  @Test
  void heapWeightSumsUnitWeightsAcrossEveryStack() {
    ItemRef stone = stack(Id.minecraft("stone"), 64, 64);
    ItemRef egg = stack(Id.minecraft("egg"), 16, 16);
    List<BackpackItem.HeapSlot> heap = List.of(
        new BackpackItem.HeapSlot(stone, 64),
        new BackpackItem.HeapSlot(egg, 16),
        new BackpackItem.HeapSlot(null, 9)
    );

    assertThat(BackpackItem.heapWeight(heap, item -> BackpackItem.unitWeight(item.maxStackSize())))
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
    ItemRef nested = backpack("a", BackpackItem.Mode.SLOTS);

    assertThat(BackpackItem.deniesDeposit(nested, false)).isTrue();
    assertThat(BackpackItem.deniesDeposit(nested, true)).isTrue();
  }

  @Test
  void plainItemsAndEmptySlotsAreNeverDenied() {
    assertThat(BackpackItem.deniesDeposit(null, true)).isFalse();
    assertThat(BackpackItem.deniesDeposit(stack(Id.minecraft("air"), 1, 64), true)).isFalse();
    assertThat(BackpackItem.deniesDeposit(stack(Id.minecraft("stone"), 4, 64), true)).isFalse();
  }

  @Test
  void aShulkerBoxHidingABackpackIsDeniedOnlyWhileNestedContainersAreBlocked() {
    ItemRef shulker = shulkerHolding(backpack("b", BackpackItem.Mode.BUNDLE));

    assertThat(BackpackItem.deniesDeposit(shulker, true)).isTrue();
    assertThat(BackpackItem.deniesDeposit(shulker, false)).isFalse();
  }

  @Test
  void aVanillaBundleHidingABackpackIsDeniedTheSameWay() {
    ItemRef carrier = bundleHolding(backpack("c", BackpackItem.Mode.SLOTS));

    assertThat(BackpackItem.deniesDeposit(carrier, true)).isTrue();
    assertThat(BackpackItem.deniesDeposit(carrier, false)).isFalse();
  }

  @Test
  void theNestingScanStopsAtItsDepthLimit() {
    ItemRef deep = bundleHolding(bundleHolding(bundleHolding(bundleHolding(
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
    ItemRef first = backpack("aaa", BackpackItem.Mode.SLOTS);
    ItemRef second = backpack("bbb", BackpackItem.Mode.BUNDLE);
    ItemRef plain = stack(Id.minecraft("stone"), 1, 64);
    ItemRef[] contents = new ItemRef[]{plain, null, first, second};

    assertThat(BackpackItem.identitySlot(contents, "bbb")).isEqualTo(3);
    assertThat(BackpackItem.identitySlot(contents, "aaa")).isEqualTo(2);
    assertThat(BackpackItem.identitySlot(contents, "ccc")).isEqualTo(-1);
    assertThat(BackpackItem.identitySlot(contents, null)).isEqualTo(-1);
    assertThat(BackpackItem.identitySlot(null, "aaa")).isEqualTo(-1);
  }

  @Test
  void modeAndIdentityRoundTripThroughTheItemsOwnData() {
    ItemRef slots = backpack("one", BackpackItem.Mode.SLOTS);
    ItemRef bundle = backpack("two", BackpackItem.Mode.BUNDLE);

    assertThat(BackpackItem.modeOf(slots)).isEqualTo(BackpackItem.Mode.SLOTS);
    assertThat(BackpackItem.modeOf(bundle)).isEqualTo(BackpackItem.Mode.BUNDLE);
    assertThat(BackpackItem.identityOf(slots)).isEqualTo("one");
    assertThat(BackpackItem.identityOf(bundle)).isEqualTo("two");
    assertThat(BackpackItem.identityOf(stack(Id.minecraft("stone"), 1, 64))).isNull();
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
    ItemRef template = stack(Id.minecraft("stone"), 1, 64);
    ItemRef match = similar(Id.minecraft("stone"), 32, template);
    ItemRef other = stack(Id.minecraft("dirt"), 8, 64);
    ItemRef[] contents = new ItemRef[]{null, other, match, null};

    BackpackItem.BackpackExtraction extraction = BackpackItem.extractMatching(contents, template);

    assertThat(extraction).isNotNull();
    assertThat(extraction.pulled()).isSameAs(match);
    assertThat(extraction.remaining()).containsExactly(null, other, null, null);
    assertThat(contents[2]).isSameAs(match);
  }

  @Test
  void extractionWorksTheSameOnACompactedBundleHeap() {
    ItemRef template = stack(Id.minecraft("stone"), 1, 64);
    ItemRef other = stack(Id.minecraft("dirt"), 8, 64);
    ItemRef match = similar(Id.minecraft("stone"), 64, template);
    ItemRef[] heap = new ItemRef[]{other, match};

    BackpackItem.BackpackExtraction extraction = BackpackItem.extractMatching(heap, template);

    assertThat(extraction).isNotNull();
    assertThat(BackpackItem.compact(extraction.remaining())).containsExactly(other);
  }

  @Test
  void extractionReportsNothingWhenTheBackpackCannotHelp() {
    ItemRef template = stack(Id.minecraft("stone"), 1, 64);

    assertThat(BackpackItem.extractMatching(new ItemRef[0], template)).isNull();
    assertThat(BackpackItem.extractMatching(null, template)).isNull();
    assertThat(BackpackItem.extractMatching(new ItemRef[]{stack(Id.minecraft("dirt"), 1, 64)}, template)).isNull();
    assertThat(BackpackItem.extractMatching(new ItemRef[]{stack(Id.minecraft("stone"), 0, 64)}, template)).isNull();
  }

  @Test
  void countingAndCompactingIgnoreEmptyEntries() {
    ItemRef real = stack(Id.minecraft("stone"), 4, 64);
    ItemRef[] contents = new ItemRef[]{null, real, stack(Id.minecraft("air"), 1, 64), stack(Id.minecraft("dirt"), 0, 64)};

    assertThat(BackpackItem.countStored(contents)).isEqualTo(1);
    assertThat(BackpackItem.compact(contents)).containsExactly(real);
    assertThat(BackpackItem.countStored(null)).isZero();
    assertThat(BackpackItem.compact(null)).isEmpty();
  }

  @Test
  void readingAHeapDropsHolesAndKeepsRealAmounts() {
    ItemRef stone = stack(Id.minecraft("stone"), 12, 64);
    List<BackpackItem.HeapSlot> heap = BackpackItem.readHeap(new ItemRef[]{null, stone, stack(Id.minecraft("air"), 3, 64)});

    assertThat(heap).hasSize(1);
    assertThat(heap.get(0).template()).isSameAs(stone);
    assertThat(heap.get(0).amount()).isEqualTo(12);
  }

  // ----------------------------------------------------------------- helpers

  private static ItemRef stack(Id type, int amount, int maxStackSize) {
    ItemRef stack = mock(ItemRef.class);
    lenient().when(stack.type()).thenReturn(type);
    lenient().when(stack.amount()).thenReturn(amount);
    lenient().when(stack.maxStackSize()).thenReturn(maxStackSize);
    lenient().when(stack.copy()).thenReturn(stack);
    return stack;
  }

  private static ItemRef similar(Id type, int amount, ItemRef template) {
    ItemRef stack = stack(type, amount, template.maxStackSize());
    lenient().when(stack.isSimilar(template)).thenReturn(true);
    lenient().when(template.isSimilar(stack)).thenReturn(true);
    return stack;
  }

  private static ItemRef backpack(String id, BackpackItem.Mode mode) {
    FakeItem stack = new FakeItem(Id.minecraft("bundle"), 1);
    stack.setMaxStackSize(1);
    stack.data().set(DataKey.of(BackpackItem.ITEM.dataKey(), BackpackItem.ITEM.codec()), BackpackItem.Data.of(id, mode, 9, 0));
    return stack;
  }

  private static ItemRef bundleHolding(ItemRef inner) {
    FakeItem stack = new FakeItem(Id.minecraft("bundle"), 1);
    stack.setMaxStackSize(1);
    return stack.store(inner);
  }

  private static ItemRef shulkerHolding(ItemRef inner) {
    FakeItem stack = new FakeItem(Id.minecraft("shulker_box"), 1);
    stack.setMaxStackSize(1);
    return stack.store(new FakeItem(Id.minecraft("air"), 0), inner);
  }
}
