package art.arcane.adapt.bukkit.feature.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MenuCursorTest {
  private final MenuCursor cursor = new MenuCursor();
  private Player player;
  private MenuWindow window;
  private Inventory top;
  private Inventory bottom;

  @BeforeEach
  void setUp() throws ReflectiveOperationException {
    player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    window = mock(MenuWindow.class);
    when(window.getViewer()).thenReturn(player);
    when(window.isVisible()).thenReturn(true);
    doCallRealMethod().when(window).opened();
    doCallRealMethod().when(window).closed();
    top = mock(Inventory.class);
    bottom = mock(Inventory.class);
    free(true);
    window.opened();
  }

  @AfterEach
  void tearDown() {
    window.closed();
  }

  @Test
  void ownInventoryClicksUnderAFreeMenuAreUncancelledWhoeverCancelledThem() {
    InventoryClickEvent click = click(bottom, InventoryAction.PICKUP_ALL);

    cursor.on(click);

    verify(click).setCancelled(false);
  }

  @Test
  void menuClicksAndBlockedActionsStayCancelled() throws ReflectiveOperationException {
    InventoryClickEvent menuClick = click(top, InventoryAction.PICKUP_ALL);
    InventoryClickEvent shiftMove = click(bottom, InventoryAction.MOVE_TO_OTHER_INVENTORY);
    cursor.on(menuClick);
    cursor.on(shiftMove);
    free(false);
    InventoryClickEvent lockedMenu = click(bottom, InventoryAction.PICKUP_ALL);
    cursor.on(lockedMenu);

    verify(menuClick, never()).setCancelled(anyBoolean());
    verify(shiftMove, never()).setCancelled(anyBoolean());
    verify(lockedMenu, never()).setCancelled(anyBoolean());
  }

  @Test
  void closingTheMenuStopsTheUncancel() {
    window.closed();
    InventoryClickEvent click = click(bottom, InventoryAction.PICKUP_ALL);

    cursor.on(click);

    verify(click, never()).setCancelled(anyBoolean());
  }

  private InventoryClickEvent click(Inventory clicked, InventoryAction action) {
    InventoryView view = mock(InventoryView.class);
    when(view.getTopInventory()).thenReturn(top);
    InventoryClickEvent click = mock(InventoryClickEvent.class);
    when(click.getView()).thenReturn(view);
    when(click.getClickedInventory()).thenReturn(clicked);
    when(click.getAction()).thenReturn(action);
    when(click.getWhoClicked()).thenReturn(player);
    return click;
  }

  private void free(boolean free) throws ReflectiveOperationException {
    Field field = MenuWindow.class.getDeclaredField("freePlayerInventory");
    field.setAccessible(true);
    field.setBoolean(window, free);
  }
}
