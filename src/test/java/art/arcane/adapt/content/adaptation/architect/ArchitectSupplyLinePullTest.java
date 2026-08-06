package art.arcane.adapt.content.adaptation.architect;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
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
    assertThat(ArchitectSupplyLine.consumesLastOfStack(stack(Material.STONE, 2))).isFalse();
    assertThat(ArchitectSupplyLine.consumesLastOfStack(stack(Material.STONE, 1))).isTrue();
    assertThat(ArchitectSupplyLine.consumesLastOfStack(stack(Material.STONE, 0))).isTrue();
    assertThat(ArchitectSupplyLine.consumesLastOfStack(stack(Material.AIR, 1))).isFalse();
    assertThat(ArchitectSupplyLine.consumesLastOfStack(null)).isFalse();
  }

  @Test
  void seedsAndOtherNonBlockPlaceablesStillTriggerARefill() {
    assertThat(ArchitectSupplyLine.consumesLastOfStack(stack(Material.WHEAT_SEEDS, 1))).isTrue();
    assertThat(ArchitectSupplyLine.consumesLastOfStack(stack(Material.REDSTONE, 1))).isTrue();
  }

  @Test
  void matchingSlotFindsTheFirstSimilarStack() {
    ItemStack template = stack(Material.STONE, 1);
    ItemStack other = stack(Material.DIRT, 12);
    ItemStack match = similar(Material.STONE, 32, template);

    assertThat(ArchitectSupplyLine.matchingSlot(new ItemStack[]{null, other, match}, template))
        .isEqualTo(2);
    assertThat(ArchitectSupplyLine.matchingSlot(new ItemStack[]{other}, template)).isEqualTo(-1);
    assertThat(ArchitectSupplyLine.matchingSlot(null, template)).isEqualTo(-1);
    assertThat(ArchitectSupplyLine.matchingSlot(new ItemStack[]{match}, null)).isEqualTo(-1);
  }

  @Test
  void matchingSlotSkipsTheHeldSlotAndEmptyStacks() {
    ItemStack template = stack(Material.STONE, 1);
    ItemStack held = similar(Material.STONE, 8, template);
    ItemStack air = stack(Material.AIR, 1);
    ItemStack empty = similar(Material.STONE, 0, template);
    ItemStack match = similar(Material.STONE, 64, template);

    assertThat(ArchitectSupplyLine.matchingSlot(new ItemStack[]{held, air, empty, match}, template, 0))
        .isEqualTo(3);
    assertThat(ArchitectSupplyLine.matchingSlot(new ItemStack[]{held, air, empty}, template, 0))
        .isEqualTo(-1);
  }

  @Test
  void bundleExtractionRemovesOnlyTheFirstMatchingEntry() {
    ItemStack template = stack(Material.STONE, 1);
    ItemStack first = similar(Material.STONE, 16, template);
    ItemStack second = similar(Material.STONE, 4, template);
    ItemStack unrelated = stack(Material.TORCH, 3);
    List<ItemStack> items = new ArrayList<>(Arrays.asList(unrelated, first, second));

    ArchitectSupplyLine.BundleExtraction extraction =
        ArchitectSupplyLine.extractFromBundle(items, template);

    assertThat(extraction).isNotNull();
    assertThat(extraction.pulled()).isSameAs(first);
    assertThat(extraction.remaining()).containsExactly(unrelated, second);
    assertThat(items).containsExactly(unrelated, first, second);
  }

  @Test
  void bundleExtractionReportsNothingWhenTheBundleCannotHelp() {
    ItemStack template = stack(Material.STONE, 1);

    assertThat(ArchitectSupplyLine.extractFromBundle(List.of(), template)).isNull();
    assertThat(ArchitectSupplyLine.extractFromBundle(null, template)).isNull();
    assertThat(ArchitectSupplyLine.extractFromBundle(List.of(stack(Material.TORCH, 1)), template))
        .isNull();
  }

  @Test
  void shulkerBoxesAreRecognizedByEveryDye() {
    assertThat(ArchitectSupplyLine.isShulkerBox(Material.SHULKER_BOX)).isTrue();
    assertThat(ArchitectSupplyLine.isShulkerBox(Material.LIME_SHULKER_BOX)).isTrue();
    assertThat(ArchitectSupplyLine.isShulkerBox(Material.BUNDLE)).isFalse();
    assertThat(ArchitectSupplyLine.isShulkerBox(null)).isFalse();
  }

  private static ItemStack stack(Material type, int amount) {
    ItemStack stack = mock(ItemStack.class);
    when(stack.getType()).thenReturn(type);
    when(stack.getAmount()).thenReturn(amount);
    when(stack.clone()).thenReturn(stack);
    return stack;
  }

  private static ItemStack similar(Material type, int amount, ItemStack template) {
    ItemStack stack = stack(type, amount);
    when(stack.isSimilar(template)).thenReturn(true);
    return stack;
  }
}
