package art.arcane.adapt.content.adaptation.enchanting;

import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.InventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.inventory.EnchantItemEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EnchantingLapisReturnTest {
  @Test
  void maximumLevelCannotRefundThreeLapisForAOneLapisOffer() {
    EnchantingLapisReturn.Refund refund = paidEnchant(3, 0, 16, 15);
    when(refund.event().expLevelCost()).thenReturn(30);

    assertThat(EnchantingLapisReturn.refundAmount(refund)).isEqualTo(1);
  }

  @Test
  void everyLevelAndOfferIsBoundedByItsActualLapisPayment() {
    for (int level : new int[] {1, 2, 3, 10, Integer.MAX_VALUE}) {
      for (int button = 0; button < 3; button++) {
        int paid = button + 1;
        EnchantingLapisReturn.Refund refund = paidEnchant(level, button, 16, 16 - paid);

        assertThat(EnchantingLapisReturn.refundAmount(refund)).isEqualTo(Math.min(level, paid));
      }
    }
  }

  @Test
  void consumingTheLastLapisStillRefundsOnlyThatOneItem() {
    EnchantingLapisReturn.Refund refund = paidEnchant(3, 0, 1, 0);
    when(refund.inventory().item(1)).thenReturn(null);

    assertThat(EnchantingLapisReturn.refundAmount(refund)).isEqualTo(1);
  }

  @Test
  void cancelledOrFreeEnchantNeverRefundsLapis() {
    EnchantingLapisReturn.Refund cancelled = paidEnchant(3, 0, 16, 15);
    when(cancelled.event().isCancelled()).thenReturn(true);
    assertThat(EnchantingLapisReturn.refundAmount(cancelled)).isZero();

    EnchantingLapisReturn.Refund creative = paidEnchant(3, 0, 16, 15);
    when(creative.event().player().gameMode()).thenReturn(GameMode.CREATIVE);
    assertThat(EnchantingLapisReturn.refundAmount(creative)).isZero();

    EnchantingLapisReturn.Refund unchanged = paidEnchant(3, 0, 16, 16);
    assertThat(EnchantingLapisReturn.refundAmount(unchanged)).isZero();
  }

  @Test
  void failedEnchantCannotTurnAnUnrelatedSlotDecreaseIntoARefund() {
    EnchantingLapisReturn.Refund refund = paidEnchant(3, 0, 16, 15);
    when(refund.event().player().enchantmentSeed()).thenReturn(refund.enchantmentSeed());

    assertThat(EnchantingLapisReturn.refundAmount(refund)).isZero();

    when(refund.event().player().enchantmentSeed()).thenReturn(43);
    when(refund.event().enchantsToAdd()).thenReturn(Map.of());
    assertThat(EnchantingLapisReturn.refundAmount(refund)).isZero();
  }

  @Test
  void closedOrReplacedInventoryCannotRefundItsRemovedLapisStack() {
    EnchantingLapisReturn.Refund refund = paidEnchant(3, 0, 16, 0);
    assertThat(EnchantingLapisReturn.refundAmount(refund)).isZero();

    when(refund.event().player().openMenuInventory()).thenReturn(mock(InventoryRef.class));
    assertThat(EnchantingLapisReturn.refundAmount(refund)).isZero();
  }

  @Test
  void overlappingEnchantCallbacksCannotAttributeTwoPaymentsToOneOffer() {
    EnchantingLapisReturn.Refund first = paidEnchant(3, 0, 16, 14);
    EnchantingLapisReturn.Refund second = paidEnchant(3, 0, 15, 14);

    assertThat(EnchantingLapisReturn.refundAmount(first)).isZero();
    assertThat(EnchantingLapisReturn.refundAmount(second)).isEqualTo(1);
  }

  @Test
  void invalidOffersAndUnlearnedLevelsNeverRefund() {
    assertThat(EnchantingLapisReturn.refundAmount(paidEnchant(3, -1, 16, 15))).isZero();
    assertThat(EnchantingLapisReturn.refundAmount(paidEnchant(3, 3, 16, 12))).isZero();
    assertThat(EnchantingLapisReturn.refundAmount(paidEnchant(0, 0, 16, 15))).isZero();
  }

  private EnchantingLapisReturn.Refund paidEnchant(int level, int button, int before, int after) {
    PlayerRef player = mock(PlayerRef.class);
    EnchantItemEvent event = mock(EnchantItemEvent.class);
    InventoryRef inventory = mock(InventoryRef.class);
    ItemRef lapis = mock(ItemRef.class);
    when(event.player()).thenReturn(player);
    when(event.button()).thenReturn(button);
    when(event.enchantsToAdd()).thenReturn(Collections.singletonMap(Id.minecraft("sharpness"), 1));
    when(player.isOnline()).thenReturn(true);
    when(player.gameMode()).thenReturn(GameMode.SURVIVAL);
    when(player.enchantmentSeed()).thenReturn(43);
    when(player.openMenuInventory()).thenReturn(inventory);
    when(inventory.item(1)).thenReturn(lapis);
    when(lapis.type()).thenReturn(Id.minecraft("lapis_lazuli"));
    when(lapis.amount()).thenReturn(after);
    return new EnchantingLapisReturn.Refund(event, inventory, level, before, 42);
  }
}
