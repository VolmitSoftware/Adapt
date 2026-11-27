package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ArchitectDemolitionRestitutionTest {
  @Test
  void restitutionReturnsThePlacedItemBeforeAnyStoredContents() {
    ItemRef placed = stack(Id.minecraft("chest"), 1);
    ItemRef storedOne = stack(Id.minecraft("diamond"), 5);
    ItemRef storedTwo = stack(Id.minecraft("stick"), 2);

    List<ItemRef> items = ArchitectDemolition.restitution(placed,
        Arrays.asList(storedOne, null, storedTwo));

    assertThat(items).containsExactly(placed, storedOne, storedTwo);
  }

  @Test
  void restitutionKeepsBlocksThatVanillaWouldNotDrop() {
    ItemRef glass = stack(Id.minecraft("glass"), 1);

    assertThat(ArchitectDemolition.restitution(glass, null)).containsExactly(glass);
    assertThat(ArchitectDemolition.restitution(glass, List.of())).containsExactly(glass);
  }

  @Test
  void restitutionDropsAirAndEmptyStacks() {
    ItemRef air = stack(Id.minecraft("air"), 1);
    ItemRef empty = stack(Id.minecraft("stone"), 0);
    ItemRef real = stack(Id.minecraft("stone"), 3);

    assertThat(ArchitectDemolition.restitution(air, Arrays.asList(air, empty, real)))
        .containsExactly(real);
    assertThat(ArchitectDemolition.restitution(null, Arrays.asList(empty))).isEmpty();
  }

  @Test
  void snapshotPlacedItemRecordsASingleUnitOfWhatWasPlaced() {
    ItemRef hand = mock(ItemRef.class);
    ItemRef clone = mock(ItemRef.class);
    when(hand.type()).thenReturn(Id.minecraft("glass"));
    when(hand.amount()).thenReturn(32);
    when(hand.copyWithAmount(1)).thenReturn(clone);

    assertThat(ArchitectDemolition.snapshotPlacedItem(hand, Id.minecraft("glass"))).isSameAs(clone);
    assertThat(ArchitectDemolition.snapshotPlacedItem(null, Id.minecraft("glass"))).isNull();
    assertThat(ArchitectDemolition.snapshotPlacedItem(stack(Id.minecraft("air"), 1), Id.minecraft("air"))).isNull();
    assertThat(ArchitectDemolition.snapshotPlacedItem(stack(Id.minecraft("glass"), 0), Id.minecraft("glass"))).isNull();
    verify(hand).copyWithAmount(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"diamond_shovel", "diamond_hoe", "diamond_axe", "flint_and_steel", "bone_meal", "wheat_seeds"})
  void terrainTransformsCannotRefundTheirHeldToolsOrIngredients(String material) {
    ItemRef hand = stack(material, 1);

    assertThat(ArchitectDemolition.snapshotPlacedItem(hand, Id.minecraft("dirt"))).isNull();
    verify(hand, never()).copyWithAmount(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"torch", "redstone", "string", "wheat_seeds"})
  void placementMaterialsWithDifferentBlockNamesStillRefundOneItem(String material) {
    ItemRef hand = stack(material, 16);

    assertThat(ArchitectDemolition.snapshotPlacedItem(hand, Id.minecraft(material))).isSameAs(hand);
    verify(hand).copyWithAmount(1);
  }

  @Test
  void shulkerBoxesAreTheOnlyContainersThatCarryContentsInTheirItem() {
    assertThat(ArchitectDemolition.keepsContentsInItem(Id.minecraft("shulker_box"))).isTrue();
    assertThat(ArchitectDemolition.keepsContentsInItem(Id.minecraft("red_shulker_box"))).isTrue();
    assertThat(ArchitectDemolition.keepsContentsInItem(Id.minecraft("chest"))).isFalse();
    assertThat(ArchitectDemolition.keepsContentsInItem(Id.minecraft("barrel"))).isFalse();
    assertThat(ArchitectDemolition.keepsContentsInItem(null)).isFalse();
  }

  private static ItemRef stack(Id type, int amount) {
    ItemRef stack = mock(ItemRef.class);
    when(stack.type()).thenReturn(type);
    when(stack.amount()).thenReturn(amount);
    when(stack.copy()).thenReturn(stack);
    when(stack.copyWithAmount(1)).thenReturn(stack);
    return stack;
  }

  private static ItemRef stack(String type, int amount) {
    return stack(Id.minecraft(type), amount);
  }
}
