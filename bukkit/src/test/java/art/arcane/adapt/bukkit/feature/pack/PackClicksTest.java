package art.arcane.adapt.bukkit.feature.pack;

import art.arcane.adapt.bukkit.canvas.CanvasInput;
import art.arcane.adapt.pack.surface.PackSurface;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuButton;
import art.arcane.adapt.platform.menu.MenuIcon;
import art.arcane.adapt.platform.value.Id;
import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PackClicksTest {
  private static final MenuIcon ICON = MenuIcon.of(Id.minecraft("stone"), "Stone", List.of());

  @Test
  void canvasInputsMapToClickKinds() {
    assertThat(PackClicks.kind(0, CanvasInput.PICKUP)).isEqualTo(ClickKind.LEFT);
    assertThat(PackClicks.kind(1, CanvasInput.PICKUP)).isEqualTo(ClickKind.RIGHT);
    assertThat(PackClicks.kind(0, CanvasInput.QUICK_MOVE)).isEqualTo(ClickKind.SHIFT_LEFT);
    assertThat(PackClicks.kind(1, CanvasInput.QUICK_MOVE)).isEqualTo(ClickKind.SHIFT_RIGHT);
    for (int key = 0; key <= 8; key++) {
      assertThat(PackClicks.kind(key, CanvasInput.SWAP)).isEqualTo(ClickKind.NUMBER_KEY);
    }
    assertThat(PackClicks.kind(40, CanvasInput.SWAP)).isEqualTo(ClickKind.SWAP_OFFHAND);
    assertThat(PackClicks.kind(2, CanvasInput.CLONE)).isEqualTo(ClickKind.MIDDLE);
    assertThat(PackClicks.kind(0, CanvasInput.THROW)).isEqualTo(ClickKind.DROP);
    assertThat(PackClicks.kind(1, CanvasInput.THROW)).isEqualTo(ClickKind.CONTROL_DROP);
    assertThat(PackClicks.kind(0, CanvasInput.PICKUP_ALL)).isEqualTo(ClickKind.DOUBLE_CLICK);
  }

  @Test
  void dragStepsAndUnknownButtonsAreNotClicks() {
    assertThat(PackClicks.kind(0, CanvasInput.QUICK_CRAFT)).isNull();
    assertThat(PackClicks.kind(5, CanvasInput.QUICK_CRAFT)).isNull();
    assertThat(PackClicks.kind(2, CanvasInput.PICKUP)).isNull();
    assertThat(PackClicks.kind(20, CanvasInput.SWAP)).isNull();
    assertThat(PackClicks.kind(3, CanvasInput.THROW)).isNull();
  }

  @Test
  void bukkitClickTypesMapToClickKinds() {
    assertThat(PackClicks.kind(ClickType.LEFT)).isEqualTo(ClickKind.LEFT);
    assertThat(PackClicks.kind(ClickType.SHIFT_RIGHT)).isEqualTo(ClickKind.SHIFT_RIGHT);
    assertThat(PackClicks.kind(ClickType.NUMBER_KEY)).isEqualTo(ClickKind.NUMBER_KEY);
    assertThat(PackClicks.kind(ClickType.SWAP_OFFHAND)).isEqualTo(ClickKind.SWAP_OFFHAND);
    assertThat(PackClicks.kind(ClickType.DOUBLE_CLICK)).isEqualTo(ClickKind.DOUBLE_CLICK);
    assertThat(PackClicks.kind(ClickType.CONTROL_DROP)).isEqualTo(ClickKind.CONTROL_DROP);
    assertThat(PackClicks.kind(ClickType.CREATIVE)).isEqualTo(ClickKind.OTHER);
  }

  @Test
  void clicksReachChestButtonsThenLowerButtons() {
    MenuButton top = MenuButton.display(ICON);
    MenuButton lowerFirst = MenuButton.display(ICON);
    MenuButton hotbarLast = MenuButton.display(ICON);
    for (int rows = 1; rows <= 6; rows++) {
      ChestMenu menu = ChestMenu.builder("Test", rows).set(0, top).build();
      PackSurface surface = PackSurface.builder().lower(0, lowerFirst).lower(35, hotbarLast).build();
      assertThat(PackClicks.button(menu, surface, 0)).isSameAs(top);
      assertThat(PackClicks.button(menu, surface, rows * 9)).isSameAs(lowerFirst);
      assertThat(PackClicks.button(menu, surface, (rows * 9) + 35)).isSameAs(hotbarLast);
      assertThat(PackClicks.button(menu, surface, (rows * 9) + 36)).isNull();
      assertThat(PackClicks.button(menu, surface, (rows * 9) - 1)).isNull();
      assertThat(PackClicks.button(menu, surface, -999)).isNull();
    }
  }
}
