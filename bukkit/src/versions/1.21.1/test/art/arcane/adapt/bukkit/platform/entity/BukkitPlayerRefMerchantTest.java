package art.arcane.adapt.bukkit.platform.entity;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantInventory;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Paper before 26.x hands out a merchant wrapper from MerchantInventory#getMerchant; the trader is the inventory holder. */
class BukkitPlayerRefMerchantTest {
  @Test
  void theOpenTraderComesFromTheInventoryHolderWhenTheMerchantIsNotTheEntity() {
    UUID villagerId = UUID.randomUUID();
    Villager villager = mock(Villager.class);
    when(villager.getUniqueId()).thenReturn(villagerId);
    when(villager.getType()).thenReturn(EntityType.VILLAGER);
    MerchantInventory inventory = mock(MerchantInventory.class);
    when(inventory.getMerchant()).thenReturn(mock(Merchant.class));
    when(inventory.getHolder(false)).thenReturn(villager);
    InventoryView view = mock(InventoryView.class);
    when(view.getTopInventory()).thenReturn(inventory);
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    when(player.getOpenInventory()).thenReturn(view);

    assertThat(BukkitRefs.player(player).openMerchant().uuid()).isEqualTo(villagerId);
  }
}
