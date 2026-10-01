package art.arcane.adapt.content.adaptation.enchanting;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
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
    when(refund.event().getExpLevelCost()).thenReturn(30);

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
    when(refund.inventory().getItem(1)).thenReturn(null);

    assertThat(EnchantingLapisReturn.refundAmount(refund)).isEqualTo(1);
  }

  @Test
  void cancelledOrFreeEnchantNeverRefundsLapis() {
    EnchantingLapisReturn.Refund cancelled = paidEnchant(3, 0, 16, 15);
    when(cancelled.event().isCancelled()).thenReturn(true);
    assertThat(EnchantingLapisReturn.refundAmount(cancelled)).isZero();

    EnchantingLapisReturn.Refund creative = paidEnchant(3, 0, 16, 15);
    when(creative.event().getEnchanter().getGameMode()).thenReturn(GameMode.CREATIVE);
    assertThat(EnchantingLapisReturn.refundAmount(creative)).isZero();

    EnchantingLapisReturn.Refund unchanged = paidEnchant(3, 0, 16, 16);
    assertThat(EnchantingLapisReturn.refundAmount(unchanged)).isZero();
  }

  @Test
  void failedEnchantCannotTurnAnUnrelatedSlotDecreaseIntoARefund() {
    EnchantingLapisReturn.Refund refund = paidEnchant(3, 0, 16, 15);
    when(refund.event().getEnchanter().getEnchantmentSeed()).thenReturn(refund.enchantmentSeed());

    assertThat(EnchantingLapisReturn.refundAmount(refund)).isZero();

    when(refund.event().getEnchanter().getEnchantmentSeed()).thenReturn(43);
    when(refund.event().getEnchantsToAdd()).thenReturn(Map.of());
    assertThat(EnchantingLapisReturn.refundAmount(refund)).isZero();
  }

  @Test
  void closedOrReplacedInventoryCannotRefundItsRemovedLapisStack() {
    EnchantingLapisReturn.Refund refund = paidEnchant(3, 0, 16, 0);
    assertThat(EnchantingLapisReturn.refundAmount(refund)).isZero();

    when(refund.event().getEnchanter().getOpenInventory().getTopInventory()).thenReturn(mock(Inventory.class));
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
    Player player = mock(Player.class);
    EnchantItemEvent event = mock(EnchantItemEvent.class);
    Inventory inventory = mock(Inventory.class);
    InventoryView view = mock(InventoryView.class);
    ItemStack lapis = mock(ItemStack.class);
    when(event.getEnchanter()).thenReturn(player);
    when(event.whichButton()).thenReturn(button);
    when(event.getEnchantsToAdd()).thenReturn(Collections.singletonMap(null, 1));
    when(player.isOnline()).thenReturn(true);
    when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
    when(player.getEnchantmentSeed()).thenReturn(43);
    when(player.getOpenInventory()).thenReturn(view);
    when(view.getTopInventory()).thenReturn(inventory);
    when(inventory.getItem(1)).thenReturn(lapis);
    when(lapis.getType()).thenReturn(Material.LAPIS_LAZULI);
    when(lapis.getAmount()).thenReturn(after);
    return new EnchantingLapisReturn.Refund(event, inventory, level, before, 42);
  }
}
