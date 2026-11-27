package art.arcane.adapt.engine.potion;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.item.CustomItem;
import art.arcane.adapt.content.golem.item.GolemItems;
import art.arcane.adapt.engine.item.AdaptItems;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.AdaptServer;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.testkit.FakeItem;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class BrewingTaskTest extends AdaptTestBase {
  @Test
  void golemItemsAreNeverBrewingIngredients() {
    FakeItem dormantGolem = FakeItem.of("snow_block");
    dormantGolem.data().set(DataKey.adapt("golem_item", DataCodecs.STRING), "dormant_golem");
    FakeItem ingot = FakeItem.of("iron_ingot");

    try (MockedStatic<AdaptItems> items = mockStatic(AdaptItems.class, CALLS_REAL_METHODS)) {
      items.when(AdaptItems::items).thenReturn(List.<CustomItem.Definition<?>>of(GolemItems.ITEM));
      assertFalse(BrewingTask.acceptsIngredient(dormantGolem));
      assertTrue(BrewingTask.acceptsIngredient(ingot));
      assertFalse(BrewingTask.acceptsIngredient(null));
    }
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
    FakeItem source = FakeItem.of("blaze_powder");
    source.setAmount(4);

    ItemRef decreased = BrewingTask.decrease(source, 2);

    assertNotSame(source, decreased);
    assertEquals(4, source.amount());
    assertEquals(2, decreased.amount());
  }

  @Test
  void brewerRuntimeMustStillBelongToTheStartingPlayerSession() {
    UUID playerId = UUID.randomUUID();
    AdaptServer server = mock(AdaptServer.class);
    AdaptPlayer adaptPlayer = mock(AdaptPlayer.class);
    PlayerRef brewer = mock(PlayerRef.class);
    PlayerRef replacement = mock(PlayerRef.class);
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
