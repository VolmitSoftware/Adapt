package art.arcane.adapt.api.adaptation;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdaptationRuntimeGuardsMainHandTest {
  @Test
  void materialCooldownDoesNotSuppressPredicateValidatedPassiveAbilities() {
    Adaptation<?> adaptation = mock(Adaptation.class);
    Player player = mock(Player.class);
    PlayerInventory inventory = mock(PlayerInventory.class);
    ItemStack sword = mock(ItemStack.class);
    when(player.getInventory()).thenReturn(inventory);
    when(inventory.getItemInMainHand()).thenReturn(sword);
    when(adaptation.isItem(sword)).thenReturn(true);
    when(sword.getType()).thenReturn(Material.IRON_SWORD);
    when(player.hasCooldown(Material.IRON_SWORD)).thenReturn(true);

    ItemStack resolved = AdaptationRuntimeGuards.readyMainHand(adaptation, player,
        item -> item == sword);

    assertThat(resolved).isSameAs(sword);
    verify(player, never()).hasCooldown(Material.IRON_SWORD);
  }
}
