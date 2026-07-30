package art.arcane.adapt.content.adaptation.ranged;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RangedArrowRecoveryChanceTest {
  @Test
  void chanceUsesConfiguredEntryForInBoundsLevels() {
    double[] chances = {10, 20, 30};
    assertThat(RangedArrowRecovery.chanceFromTable(chances, 1)).isEqualTo(0.10D);
    assertThat(RangedArrowRecovery.chanceFromTable(chances, 2)).isEqualTo(0.20D);
    assertThat(RangedArrowRecovery.chanceFromTable(chances, 3)).isEqualTo(0.30D);
  }

  @Test
  void chanceClampsToLastEntryWhenOperatorShrinksTable() {
    double[] chances = {10, 20, 30};
    assertThat(RangedArrowRecovery.chanceFromTable(chances, 8)).isEqualTo(0.30D);
  }

  @Test
  void chanceClampsToFirstEntryForNonPositiveLevels() {
    double[] chances = {10, 20, 30};
    assertThat(RangedArrowRecovery.chanceFromTable(chances, 0)).isEqualTo(0.10D);
    assertThat(RangedArrowRecovery.chanceFromTable(chances, -3)).isEqualTo(0.10D);
  }

  @Test
  void chanceIsZeroForMissingOrEmptyTable() {
    assertThat(RangedArrowRecovery.chanceFromTable(new double[0], 1)).isEqualTo(0.0D);
    assertThat(RangedArrowRecovery.chanceFromTable(null, 1)).isEqualTo(0.0D);
  }

  @Test
  void fullInventoryDropsOnlyTheReturnedLeftoverStack() {
    Player player = mock(Player.class);
    PlayerInventory inventory = mock(PlayerInventory.class);
    World world = mock(World.class);
    Location location = mock(Location.class);
    ItemStack recovered = mock(ItemStack.class);
    ItemStack leftover = mock(ItemStack.class);
    when(player.getInventory()).thenReturn(inventory);
    when(player.getWorld()).thenReturn(world);
    when(player.getLocation()).thenReturn(location);
    HashMap<Integer, ItemStack> leftovers = new HashMap<>();
    leftovers.put(0, leftover);
    when(inventory.addItem(any(ItemStack[].class))).thenReturn(leftovers);

    RangedArrowRecovery.deliverRecoveredArrow(player, recovered);

    verify(inventory).addItem(recovered);
    verify(world).dropItemNaturally(location, leftover);
  }

  @Test
  void successfulInventoryDeliveryDoesNotDropAnotherArrow() {
    Player player = mock(Player.class);
    PlayerInventory inventory = mock(PlayerInventory.class);
    World world = mock(World.class);
    ItemStack recovered = mock(ItemStack.class);
    when(player.getInventory()).thenReturn(inventory);
    when(player.getWorld()).thenReturn(world);
    when(inventory.addItem(any(ItemStack[].class))).thenReturn(new HashMap<>());

    RangedArrowRecovery.deliverRecoveredArrow(player, recovered);

    verify(inventory).addItem(recovered);
    verify(world, never()).dropItemNaturally(any(Location.class), any(ItemStack.class));
  }
}
