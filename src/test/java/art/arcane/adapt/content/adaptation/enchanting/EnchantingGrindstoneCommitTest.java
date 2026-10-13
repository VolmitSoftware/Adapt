package art.arcane.adapt.content.adaptation.enchanting;

import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EnchantingGrindstoneCommitTest {
  @Test
  void consumedInputsAndOutputAllowRecovery() {
    assertThat(EnchantingGrindstoneRecovery.committedRemoval(mock(InventoryClickEvent.class), mock(Inventory.class))).isTrue();
  }

  @Test
  void unchangedInputsAndBlockedResultDoNotRecover() {
    InventoryClickEvent event = mock(InventoryClickEvent.class);
    Inventory inventory = mock(Inventory.class);
    ItemStack input = mock(ItemStack.class);
    when(input.getAmount()).thenReturn(1);
    when(input.getType()).thenReturn(Material.DIAMOND_PICKAXE);
    when(inventory.getItem(0)).thenReturn(input);
    assertThat(EnchantingGrindstoneRecovery.committedRemoval(event, inventory)).isFalse();
    when(inventory.getItem(0)).thenReturn(null);
    when(inventory.getItem(2)).thenReturn(input);
    assertThat(EnchantingGrindstoneRecovery.committedRemoval(event, inventory)).isFalse();
  }

  @Test
  void cancelledClickCannotRecoverEvenAfterInventoryChanges() {
    InventoryClickEvent event = mock(InventoryClickEvent.class);
    when(event.isCancelled()).thenReturn(true);
    assertThat(EnchantingGrindstoneRecovery.committedRemoval(event, mock(Inventory.class))).isFalse();
  }
}
