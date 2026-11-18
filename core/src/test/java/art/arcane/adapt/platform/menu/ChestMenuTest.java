package art.arcane.adapt.platform.menu;

import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChestMenuTest {
  private static final MenuButton PANE = MenuButton.display(MenuIcon.of(Id.minecraft("gray_stained_glass_pane"), " ", List.of()));

  @Test
  void rejectsBadRowsAndSlots() {
    assertThrows(IllegalArgumentException.class, () -> ChestMenu.builder("t", 0));
    assertThrows(IllegalArgumentException.class, () -> ChestMenu.builder("t", 7));
    ChestMenu.Builder builder = ChestMenu.builder("t", 1);
    assertThrows(IllegalArgumentException.class, () -> builder.set(9, PANE));
    assertThrows(IllegalArgumentException.class, () -> builder.set(-1, PANE));
  }

  @Test
  void slotMath() {
    assertEquals(0, ChestMenu.slot(0, 0));
    assertEquals(13, ChestMenu.slot(1, 4));
    assertThrows(IllegalArgumentException.class, () -> ChestMenu.slot(0, 9));
  }

  @Test
  void fillKeepsExistingButtons() {
    MenuButton action = new MenuButton(MenuIcon.of(Id.minecraft("emerald"), "Go", List.of("a")), click -> { });
    ChestMenu menu = ChestMenu.builder("Skills", 2).set(4, action).fill(PANE).build();
    assertEquals(18, menu.buttons().size());
    assertSame(action, menu.button(4));
    assertTrue(menu.button(4).clickable());
    assertFalse(menu.button(0).clickable());
  }

  @Test
  void builtMenuIsImmutableAndCopyable() {
    ChestMenu menu = ChestMenu.builder("Skills", 1).set(0, PANE).build();
    assertThrows(UnsupportedOperationException.class, () -> menu.buttons().put(1, PANE));
    ChestMenu changed = menu.toBuilder().clear(0).build();
    assertNull(changed.button(0));
    assertSame(PANE, menu.button(0));
  }

  @Test
  void iconValidatesAmount() {
    assertThrows(IllegalArgumentException.class, () -> MenuIcon.of(Id.minecraft("stone"), "x", List.of()).withAmount(0));
    assertEquals(64, MenuIcon.of(Id.minecraft("stone"), "x", null).withAmount(64).amount());
  }
}
