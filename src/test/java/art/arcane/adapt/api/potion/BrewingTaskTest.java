package art.arcane.adapt.api.potion;

import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BrewingTaskTest {
  @Test
  void golemItemsAreNeverBrewingIngredients() {
    Material solid = mock(Material.class);
    when(solid.isAir()).thenReturn(false);
    ItemStack dormantGolem = mock(ItemStack.class);
    when(dormantGolem.getType()).thenReturn(solid);
    ItemStack ingot = mock(ItemStack.class);
    when(ingot.getType()).thenReturn(solid);

    try (MockedStatic<GolemItems> items = mockStatic(GolemItems.class)) {
      items.when(() -> GolemItems.isGolemItem(dormantGolem)).thenReturn(true);
      items.when(() -> GolemItems.isGolemItem(ingot)).thenReturn(false);

      assertFalse(BrewingTask.acceptsIngredient(dormantGolem));
      assertTrue(BrewingTask.acceptsIngredient(ingot));
    }
    assertFalse(BrewingTask.acceptsIngredient(null));
  }

  @Test
  void exactStoredFuelDoesNotConsumeAnItem() {
    assertEquals(0, BrewingTask.fuelItemsRequired(20, 20));
    assertEquals(0, BrewingTask.remainingFuel(20, 20));
  }

  @Test
  void fuelItemsCoverOnlyTheUnstoredCost() {
    assertEquals(1, BrewingTask.fuelItemsRequired(4, 16));
    assertEquals(8, BrewingTask.fuelItemsRequired(0, 160));
    assertEquals(8, BrewingTask.remainingFuel(0, 32));
  }

  @Test
  void decreaseDoesNotMutateTheSourceStack() {
    ItemStack source = mock(ItemStack.class);
    ItemStack result = mock(ItemStack.class);
    when(source.getAmount()).thenReturn(4);
    when(source.clone()).thenReturn(result);
    when(result.getAmount()).thenReturn(4);

    ItemStack decreased = BrewingTask.decrease(source, 2);

    assertEquals(result, decreased);
    verify(source, never()).setAmount(2);
    verify(result).setAmount(2);
  }

  @Test
  void brewerRuntimeMustStillBelongToTheStartingPlayerSession() {
    UUID playerId = UUID.randomUUID();
    AdaptServer server = mock(AdaptServer.class);
    AdaptPlayer adaptPlayer = mock(AdaptPlayer.class);
    Player brewer = mock(Player.class);
    Player replacement = mock(Player.class);
    when(server.getOnlineAdaptPlayer(playerId)).thenReturn(adaptPlayer);
    when(adaptPlayer.isRuntimeReady()).thenReturn(true);
    when(adaptPlayer.getPlayer()).thenReturn(brewer);

    assertTrue(BrewingTask.isBrewerRuntimeAvailable(playerId, adaptPlayer, brewer, server));
    assertFalse(BrewingTask.isBrewerRuntimeAvailable(playerId, adaptPlayer, replacement, server));

    AdaptPlayer replacementRuntime = mock(AdaptPlayer.class);
    when(server.getOnlineAdaptPlayer(playerId)).thenReturn(replacementRuntime);
    assertFalse(BrewingTask.isBrewerRuntimeAvailable(playerId, adaptPlayer, brewer, server));

    when(server.getOnlineAdaptPlayer(playerId)).thenReturn(null);
    assertFalse(BrewingTask.isBrewerRuntimeAvailable(playerId, adaptPlayer, brewer, server));
  }
}
