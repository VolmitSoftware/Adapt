package art.arcane.adapt.bukkit.feature.menu;

import art.arcane.adapt.platform.menu.ClickKind;
import org.bukkit.event.inventory.ClickType;

/**
 * Click translation. The inventorygui window reports left, right, middle and shift clicks through its
 * elements; {@link MenuWindow} reports the remaining kinds straight from the click event.
 */
final class MenuClicks {
  private MenuClicks() {
  }

  /** Kinds the window itself ignores; null for kinds it already routes or that are not clicks on a slot. */
  static ClickKind extraKind(ClickType type) {
    if (type == null) {
      return null;
    }
    return switch (type) {
      case NUMBER_KEY -> ClickKind.NUMBER_KEY;
      case DROP -> ClickKind.DROP;
      case CONTROL_DROP -> ClickKind.CONTROL_DROP;
      case DOUBLE_CLICK -> ClickKind.DOUBLE_CLICK;
      case SWAP_OFFHAND -> ClickKind.SWAP_OFFHAND;
      case CREATIVE, UNKNOWN -> ClickKind.OTHER;
      default -> null;
    };
  }
}
