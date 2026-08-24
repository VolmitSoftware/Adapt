package art.arcane.adapt.api;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ComponentInventoryExperienceTest {
  private final Component component = mock(Component.class, CALLS_REAL_METHODS);

  @Test
  void takeAllHonorsExplicitAmountAcrossStacks() {
    Inventory inventory = mock(Inventory.class);
    ItemStack template = mock(ItemStack.class);
    ItemStack requested = mock(ItemStack.class);
    ItemStack first = mock(ItemStack.class);
    ItemStack second = mock(ItemStack.class);
    ItemStack reduced = mock(ItemStack.class);
    when(template.clone()).thenReturn(requested);
    when(requested.getAmount()).thenReturn(5);
    when(first.isSimilar(requested)).thenReturn(true);
    when(second.isSimilar(requested)).thenReturn(true);
    when(first.getAmount()).thenReturn(3);
    when(second.getAmount()).thenReturn(4);
    when(second.clone()).thenReturn(reduced);
    when(inventory.getStorageContents()).thenReturn(new ItemStack[]{first, second});

    boolean removed = component.takeAll(inventory, template, 5);

    ArgumentCaptor<ItemStack[]> contents = ArgumentCaptor.forClass(ItemStack[].class);
    verify(inventory).setStorageContents(contents.capture());
    assertThat(removed).isTrue();
    assertThat(contents.getValue()[0]).isNull();
    assertThat(contents.getValue()[1]).isSameAs(reduced);
    verify(requested).setAmount(5);
    verify(reduced).setAmount(2);
  }

  @Test
  void insufficientInventoryIsNotMutated() {
    Inventory inventory = mock(Inventory.class);
    ItemStack existing = mock(ItemStack.class);
    ItemStack template = mock(ItemStack.class);
    when(template.getAmount()).thenReturn(3);
    when(existing.isSimilar(template)).thenReturn(true);
    when(existing.getAmount()).thenReturn(2);
    when(inventory.getStorageContents()).thenReturn(new ItemStack[]{existing});

    boolean removed = component.takeAll(inventory, template);

    assertThat(removed).isFalse();
    verify(existing, never()).setAmount(anyInt());
    verify(inventory, never()).setStorageContents(any(ItemStack[].class));
  }

  @Test
  void vanillaExperienceCurveUsesCorrectLevelBands() {
    assertThat(component.getExpToLevel(0)).isEqualTo(7);
    assertThat(component.getExpToLevel(14)).isEqualTo(35);
    assertThat(component.getExpToLevel(15)).isEqualTo(37);
    assertThat(component.getExpToLevel(29)).isEqualTo(107);
    assertThat(component.getExpToLevel(30)).isEqualTo(112);
  }

  @Test
  void recalculateUsesServersConstantTimeExperienceTotal() {
    Player player = mock(Player.class);
    when(player.calculateTotalExperiencePoints()).thenReturn(1_234);

    component.recalcTotalExp(player);

    verify(player).setTotalExperience(1_234);
    verify(player, never()).getLevel();
  }

  @Test
  void takeBarExperienceSubtractsOnlyRequestedAmount() {
    Player player = mock(Player.class);
    when(player.getTotalExperience()).thenReturn(100);
    when(player.getExpToLevel()).thenReturn(20);
    when(player.getExp()).thenReturn(0.5F);

    component.takeExp(player, 3, false);

    verify(player).setTotalExperience(0);
    verify(player).giveExp(97);
  }
}
