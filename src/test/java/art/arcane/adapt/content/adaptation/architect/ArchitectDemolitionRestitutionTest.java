package art.arcane.adapt.content.adaptation.architect;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ArchitectDemolitionRestitutionTest {
  @Test
  void restitutionReturnsThePlacedItemBeforeAnyStoredContents() {
    ItemStack placed = stack(Material.CHEST, 1);
    ItemStack storedOne = stack(Material.DIAMOND, 5);
    ItemStack storedTwo = stack(Material.STICK, 2);

    List<ItemStack> items = ArchitectDemolition.restitution(placed,
        new ItemStack[]{storedOne, null, storedTwo});

    assertThat(items).containsExactly(placed, storedOne, storedTwo);
  }

  @Test
  void restitutionKeepsBlocksThatVanillaWouldNotDrop() {
    ItemStack glass = stack(Material.GLASS, 1);

    assertThat(ArchitectDemolition.restitution(glass, null)).containsExactly(glass);
    assertThat(ArchitectDemolition.restitution(glass, new ItemStack[0])).containsExactly(glass);
  }

  @Test
  void restitutionDropsAirAndEmptyStacks() {
    ItemStack air = stack(Material.AIR, 1);
    ItemStack empty = stack(Material.STONE, 0);
    ItemStack real = stack(Material.STONE, 3);

    assertThat(ArchitectDemolition.restitution(air, new ItemStack[]{air, empty, real}))
        .containsExactly(real);
    assertThat(ArchitectDemolition.restitution(null, new ItemStack[]{empty})).isEmpty();
  }

  @Test
  void snapshotPlacedItemRecordsASingleUnitOfWhatWasPlaced() {
    ItemStack hand = mock(ItemStack.class);
    ItemStack clone = mock(ItemStack.class);
    when(hand.getType()).thenReturn(Material.GLASS);
    when(hand.getAmount()).thenReturn(32);
    when(hand.clone()).thenReturn(clone);

    assertThat(ArchitectDemolition.snapshotPlacedItem(hand, Material.GLASS)).isSameAs(clone);
    assertThat(ArchitectDemolition.snapshotPlacedItem(null, Material.GLASS)).isNull();
    assertThat(ArchitectDemolition.snapshotPlacedItem(stack(Material.AIR, 1), Material.AIR)).isNull();
    assertThat(ArchitectDemolition.snapshotPlacedItem(stack(Material.GLASS, 0), Material.GLASS)).isNull();
    verify(clone).setAmount(1);
  }

  @ParameterizedTest
  @EnumSource(value = Material.class, names = {"DIAMOND_SHOVEL", "DIAMOND_HOE", "DIAMOND_AXE", "FLINT_AND_STEEL", "BONE_MEAL", "WHEAT_SEEDS"})
  void terrainTransformsCannotRefundTheirHeldToolsOrIngredients(Material material) {
    ItemStack hand = stack(material, 1);

    assertThat(ArchitectDemolition.snapshotPlacedItem(hand, Material.DIRT)).isNull();
    verify(hand, never()).clone();
  }

  @ParameterizedTest
  @EnumSource(value = Material.class, names = {"TORCH", "REDSTONE", "STRING", "WHEAT_SEEDS"})
  void placementMaterialsWithDifferentBlockNamesStillRefundOneItem(Material material) {
    ItemStack hand = stack(material, 16);

    assertThat(ArchitectDemolition.snapshotPlacedItem(hand, material)).isSameAs(hand);
    verify(hand).setAmount(1);
  }

  @Test
  void shulkerBoxesAreTheOnlyContainersThatCarryContentsInTheirItem() {
    assertThat(ArchitectDemolition.keepsContentsInItem(Material.SHULKER_BOX)).isTrue();
    assertThat(ArchitectDemolition.keepsContentsInItem(Material.RED_SHULKER_BOX)).isTrue();
    assertThat(ArchitectDemolition.keepsContentsInItem(Material.CHEST)).isFalse();
    assertThat(ArchitectDemolition.keepsContentsInItem(Material.BARREL)).isFalse();
    assertThat(ArchitectDemolition.keepsContentsInItem(null)).isFalse();
  }

  private static ItemStack stack(Material type, int amount) {
    ItemStack stack = mock(ItemStack.class);
    when(stack.getType()).thenReturn(type);
    when(stack.getAmount()).thenReturn(amount);
    when(stack.clone()).thenReturn(stack);
    return stack;
  }
}
