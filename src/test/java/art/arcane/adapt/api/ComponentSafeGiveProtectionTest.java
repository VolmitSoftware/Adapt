package art.arcane.adapt.api;

import art.arcane.adapt.util.common.plugin.ProtectionEventProbe;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ComponentSafeGiveProtectionTest {
  @Test
  void pickupListenerStackGrowthCannotDeleteTheExtraItems() {
    Component component = mock(Component.class, CALLS_REAL_METHODS);
    Player player = mock(Player.class);
    PlayerInventory inventory = mock(PlayerInventory.class);
    Item item = mock(Item.class);
    ItemStack snapshot = mock(ItemStack.class);
    ItemStack expanded = mock(ItemStack.class);
    Material material = mock(Material.class);
    when(player.getInventory()).thenReturn(inventory);
    when(item.isValid()).thenReturn(true);
    when(item.isDead()).thenReturn(false);
    when(item.getItemStack()).thenReturn(expanded);
    when(snapshot.getType()).thenReturn(material);
    when(snapshot.getAmount()).thenReturn(1);
    when(material.isAir()).thenReturn(false);
    when(expanded.isSimilar(snapshot)).thenReturn(true);
    when(expanded.getAmount()).thenReturn(2);
    doReturn(true).when(component).canSnatchItem(player, item);

    boolean transferred;
    try (MockedStatic<J> scheduling = mockStatic(J.class);
         MockedStatic<ProtectionEventProbe> protection = mockStatic(ProtectionEventProbe.class)) {
      scheduling.when(J::isFoliaThreading).thenReturn(false);
      protection.when(() -> ProtectionEventProbe.remainingAfterPickup(inventory, snapshot)).thenReturn(0);
      protection.when(() -> ProtectionEventProbe.attemptItemPickup(player, item, 0)).thenReturn(true);

      transferred = component.safeGiveItem(player, item, snapshot);
    }

    assertThat(transferred).isFalse();
    verify(inventory, never()).addItem(any(ItemStack.class));
    verify(item, never()).remove();
  }
}
