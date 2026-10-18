package art.arcane.adapt.api.golem.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class NodeGuiCursorTest {
  private static final UUID VIEWER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

  private final Inventory top = mock(Inventory.class);
  private final PlayerInventory bottom = mock(PlayerInventory.class);

  @AfterEach
  void tearDown() {
    NodeGui.untrackCursorWindow(VIEWER, top);
  }

  @ParameterizedTest
  @EnumSource(value = InventoryAction.class, names = {"PICKUP_ALL", "PICKUP_SOME", "PICKUP_HALF", "PICKUP_ONE", "PLACE_ALL", "PLACE_SOME", "PLACE_ONE", "SWAP_WITH_CURSOR"})
  void pickingUpAndPlacingInYourOwnInventoryIsAllowed(InventoryAction action) {
    assertThat(NodeGuiCursor.allowsBottomClick(action)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(value = InventoryAction.class, names = {"MOVE_TO_OTHER_INVENTORY", "COLLECT_TO_CURSOR", "HOTBAR_SWAP", "DROP_ALL_SLOT", "CLONE_STACK", "NOTHING"})
  void movesThatCouldReachTheMenuStayBlocked(InventoryAction action) {
    assertThat(NodeGuiCursor.allowsBottomClick(action)).isFalse();
  }

  @Test
  void aPickupInYourInventoryWhileTheNodeMenuIsOpenGoesThrough() {
    NodeGui.trackCursorWindow(VIEWER, top);
    InventoryClickEvent click = click(bottom, InventoryAction.PICKUP_ALL);

    new NodeGuiCursor().on(click);

    verify(click).setCancelled(false);
  }

  @Test
  void clicksInTheMenuItselfOrInOtherMenusStayCancelled() {
    InventoryClickEvent untracked = click(bottom, InventoryAction.PICKUP_ALL);
    new NodeGuiCursor().on(untracked);
    NodeGui.trackCursorWindow(VIEWER, top);
    InventoryClickEvent inMenu = click(top, InventoryAction.PICKUP_ALL);
    new NodeGuiCursor().on(inMenu);
    InventoryClickEvent shift = click(bottom, InventoryAction.MOVE_TO_OTHER_INVENTORY);
    new NodeGuiCursor().on(shift);

    verify(untracked, never()).setCancelled(false);
    verify(inMenu, never()).setCancelled(false);
    verify(shift, never()).setCancelled(false);
  }

  private InventoryClickEvent click(Inventory clicked, InventoryAction action) {
    Player player = mock(Player.class);
    lenient().when(player.getUniqueId()).thenReturn(VIEWER);
    InventoryView view = mock(InventoryView.class);
    lenient().when(view.getTopInventory()).thenReturn(top);
    InventoryClickEvent event = mock(InventoryClickEvent.class);
    lenient().when(event.getWhoClicked()).thenReturn(player);
    lenient().when(event.getView()).thenReturn(view);
    lenient().when(event.getClickedInventory()).thenReturn(clicked);
    lenient().when(event.getAction()).thenReturn(action);
    return event;
  }
}
