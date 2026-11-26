package art.arcane.adapt.content.adaptation.enchanting;

import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.event.inventory.InventoryClickEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EnchantingGrindstoneCommitTest {
  @Test
  void consumedInputsAndOutputAllowRecovery() {
    assertThat(EnchantingGrindstones.committedRemoval(mock(InventoryClickEvent.class), mock(InventoryRef.class))).isTrue();
  }

  @Test
  void unchangedInputsAndBlockedResultDoNotRecover() {
    InventoryClickEvent event = mock(InventoryClickEvent.class);
    InventoryRef inventory = mock(InventoryRef.class);
    ItemRef input = mock(ItemRef.class);
    when(input.amount()).thenReturn(1);
    when(input.type()).thenReturn(Id.minecraft("diamond_pickaxe"));
    when(inventory.item(0)).thenReturn(input);
    assertThat(EnchantingGrindstones.committedRemoval(event, inventory)).isFalse();
    when(inventory.item(0)).thenReturn(null);
    when(inventory.item(2)).thenReturn(input);
    assertThat(EnchantingGrindstones.committedRemoval(event, inventory)).isFalse();
  }

  @Test
  void cancelledClickCannotRecoverEvenAfterInventoryChanges() {
    InventoryClickEvent event = mock(InventoryClickEvent.class);
    when(event.isCancelled()).thenReturn(true);
    assertThat(EnchantingGrindstones.committedRemoval(event, mock(InventoryRef.class))).isFalse();
  }
}
