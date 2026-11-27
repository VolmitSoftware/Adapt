package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.content.item.BackpackItem;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.item.ItemRef;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArchitectSupplyLinePullTest {
  @Test
  void refillTriggersOnlyOnThePlacementThatEmptiesTheHand() {
    assertThat(ArchitectSupplyLine.consumesLastOfStack(stack(Id.minecraft("stone"), 2))).isFalse();
    assertThat(ArchitectSupplyLine.consumesLastOfStack(stack(Id.minecraft("stone"), 1))).isTrue();
    assertThat(ArchitectSupplyLine.consumesLastOfStack(stack(Id.minecraft("stone"), 0))).isTrue();
    assertThat(ArchitectSupplyLine.consumesLastOfStack(stack(Id.minecraft("air"), 1))).isFalse();
    assertThat(ArchitectSupplyLine.consumesLastOfStack(null)).isFalse();
  }

  @Test
  void seedsAndOtherNonBlockPlaceablesStillTriggerARefill() {
    assertThat(ArchitectSupplyLine.consumesLastOfStack(stack(Id.minecraft("wheat_seeds"), 1))).isTrue();
    assertThat(ArchitectSupplyLine.consumesLastOfStack(stack(Id.minecraft("redstone"), 1))).isTrue();
  }

  @Test
  void matchingSlotFindsTheFirstSimilarStack() {
    ItemRef template = stack(Id.minecraft("stone"), 1);
    ItemRef other = stack(Id.minecraft("dirt"), 12);
    ItemRef match = similar(Id.minecraft("stone"), 32, template);

    assertThat(ArchitectSupplyLine.matchingSlot(new ItemRef[]{null, other, match}, template))
        .isEqualTo(2);
    assertThat(ArchitectSupplyLine.matchingSlot(new ItemRef[]{other}, template)).isEqualTo(-1);
    assertThat(ArchitectSupplyLine.matchingSlot(null, template)).isEqualTo(-1);
    assertThat(ArchitectSupplyLine.matchingSlot(new ItemRef[]{match}, null)).isEqualTo(-1);
  }

  @Test
  void matchingSlotSkipsTheHeldSlotAndEmptyStacks() {
    ItemRef template = stack(Id.minecraft("stone"), 1);
    ItemRef held = similar(Id.minecraft("stone"), 8, template);
    ItemRef air = stack(Id.minecraft("air"), 1);
    ItemRef empty = similar(Id.minecraft("stone"), 0, template);
    ItemRef match = similar(Id.minecraft("stone"), 64, template);

    assertThat(ArchitectSupplyLine.matchingSlot(new ItemRef[]{held, air, empty, match}, template, 0))
        .isEqualTo(3);
    assertThat(ArchitectSupplyLine.matchingSlot(new ItemRef[]{held, air, empty}, template, 0))
        .isEqualTo(-1);
  }

  @Test
  void bundleExtractionRemovesOnlyTheFirstMatchingEntry() {
    ItemRef template = stack(Id.minecraft("stone"), 1);
    ItemRef first = similar(Id.minecraft("stone"), 16, template);
    ItemRef second = similar(Id.minecraft("stone"), 4, template);
    ItemRef unrelated = stack(Id.minecraft("torch"), 3);
    List<ItemRef> items = new ArrayList<>(Arrays.asList(unrelated, first, second));

    ArchitectSupplyLine.BundleExtraction extraction =
        ArchitectSupplyLine.extractFromBundle(items, template);

    assertThat(extraction).isNotNull();
    assertThat(extraction.pulled()).isSameAs(first);
    assertThat(extraction.remaining()).containsExactly(unrelated, second);
    assertThat(items).containsExactly(unrelated, first, second);
  }

  @Test
  void bundleExtractionReportsNothingWhenTheBundleCannotHelp() {
    ItemRef template = stack(Id.minecraft("stone"), 1);

    assertThat(ArchitectSupplyLine.extractFromBundle(List.of(), template)).isNull();
    assertThat(ArchitectSupplyLine.extractFromBundle(null, template)).isNull();
    assertThat(ArchitectSupplyLine.extractFromBundle(List.of(stack(Id.minecraft("torch"), 1)), template))
        .isNull();
  }

  @Test
  void shulkerBoxesAreRecognizedByEveryDye() {
    assertThat(ArchitectSupplyLine.isShulkerBox(Id.minecraft("shulker_box"))).isTrue();
    assertThat(ArchitectSupplyLine.isShulkerBox(Id.minecraft("lime_shulker_box"))).isTrue();
    assertThat(ArchitectSupplyLine.isShulkerBox(Id.minecraft("bundle"))).isFalse();
    assertThat(ArchitectSupplyLine.isShulkerBox(null)).isFalse();
  }

  @Test
  void backpackRefillsPullFromASlotIndexedBackpackWithoutDisturbingItsLayout() {
    ItemRef template = stack(Id.minecraft("stone"), 1);
    ItemRef unrelated = stack(Id.minecraft("torch"), 3);
    ItemRef match = similar(Id.minecraft("stone"), 16, template);
    ItemRef[] slots = new ItemRef[]{null, unrelated, match, null};

    BackpackItem.BackpackExtraction extraction = BackpackItem.extractMatching(slots, template);

    assertThat(extraction).isNotNull();
    assertThat(extraction.pulled()).isSameAs(match);
    assertThat(extraction.remaining()).containsExactly(null, unrelated, null, null);
  }

  @Test
  void backpackRefillsPullFromABundleModeHeapTheSameWay() {
    ItemRef template = stack(Id.minecraft("stone"), 1);
    ItemRef first = similar(Id.minecraft("stone"), 64, template);
    ItemRef second = similar(Id.minecraft("stone"), 8, template);
    ItemRef[] heap = new ItemRef[]{first, second};

    BackpackItem.BackpackExtraction extraction = BackpackItem.extractMatching(heap, template);

    assertThat(extraction).isNotNull();
    assertThat(extraction.pulled()).isSameAs(first);
    assertThat(BackpackItem.compact(extraction.remaining())).containsExactly(second);
  }

  @Test
  void backpackRefillsReportNothingWhenNoStoredStackMatches() {
    ItemRef template = stack(Id.minecraft("stone"), 1);

    assertThat(BackpackItem.extractMatching(new ItemRef[]{stack(Id.minecraft("torch"), 1)}, template)).isNull();
    assertThat(BackpackItem.extractMatching(new ItemRef[0], template)).isNull();
    assertThat(BackpackItem.extractMatching(null, template)).isNull();
  }

  private static ItemRef stack(Id type, int amount) {
    ItemRef stack = mock(ItemRef.class);
    when(stack.type()).thenReturn(type);
    when(stack.amount()).thenReturn(amount);
    when(stack.isEmpty()).thenReturn(type.isAir() || amount <= 0);
    when(stack.copy()).thenReturn(stack);
    return stack;
  }

  private static ItemRef similar(Id type, int amount, ItemRef template) {
    ItemRef stack = stack(type, amount);
    when(stack.isSimilar(template)).thenReturn(true);
    return stack;
  }
}
