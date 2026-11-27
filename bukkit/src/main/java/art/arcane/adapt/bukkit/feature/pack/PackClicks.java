package art.arcane.adapt.bukkit.feature.pack;

import art.arcane.adapt.bukkit.canvas.CanvasInput;
import art.arcane.adapt.pack.surface.PackSurface;
import art.arcane.adapt.platform.menu.ChestMenu;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.menu.MenuButton;
import org.bukkit.event.inventory.ClickType;

/** Click translation and routing for pack windows. */
final class PackClicks {
  private static final int OFFHAND_BUTTON = 40;
  private static final int LAST_NUMBER_KEY = 8;

  private PackClicks() {
  }

  /** The kind of a canvas click, or null for inputs that are not a click on a button (drag steps). */
  static ClickKind kind(int button, CanvasInput input) {
    return switch (input) {
      case PICKUP -> button == 0 ? ClickKind.LEFT : button == 1 ? ClickKind.RIGHT : null;
      case QUICK_MOVE -> button == 0 ? ClickKind.SHIFT_LEFT : button == 1 ? ClickKind.SHIFT_RIGHT : null;
      case SWAP -> button == OFFHAND_BUTTON ? ClickKind.SWAP_OFFHAND
          : button >= 0 && button <= LAST_NUMBER_KEY ? ClickKind.NUMBER_KEY : null;
      case CLONE -> ClickKind.MIDDLE;
      case THROW -> button == 0 ? ClickKind.DROP : button == 1 ? ClickKind.CONTROL_DROP : null;
      case PICKUP_ALL -> ClickKind.DOUBLE_CLICK;
      case QUICK_CRAFT -> null;
    };
  }

  /** The kind of a Bukkit click on a chest window. */
  static ClickKind kind(ClickType type) {
    return switch (type) {
      case LEFT -> ClickKind.LEFT;
      case RIGHT -> ClickKind.RIGHT;
      case SHIFT_LEFT -> ClickKind.SHIFT_LEFT;
      case SHIFT_RIGHT -> ClickKind.SHIFT_RIGHT;
      case MIDDLE -> ClickKind.MIDDLE;
      case NUMBER_KEY -> ClickKind.NUMBER_KEY;
      case DROP -> ClickKind.DROP;
      case CONTROL_DROP -> ClickKind.CONTROL_DROP;
      case DOUBLE_CLICK -> ClickKind.DOUBLE_CLICK;
      case SWAP_OFFHAND -> ClickKind.SWAP_OFFHAND;
      default -> ClickKind.OTHER;
    };
  }

  /** The button a click on {@code windowSlot} reaches: a chest slot's own, else the lower button below it. */
  static MenuButton button(ChestMenu menu, PackSurface surface, int windowSlot) {
    if (windowSlot < 0) {
      return null;
    }
    if (windowSlot < menu.size()) {
      return menu.button(windowSlot);
    }
    int lower = PackSurface.lowerIndex(menu.rows(), windowSlot);
    return lower < 0 ? null : surface.lower().get(lower);
  }
}
