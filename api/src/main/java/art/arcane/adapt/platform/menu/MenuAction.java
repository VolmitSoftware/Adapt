package art.arcane.adapt.platform.menu;

import org.jetbrains.annotations.ApiStatus;

/** What a button does when clicked. */
@FunctionalInterface
@ApiStatus.AvailableSince("1.0.0")
public interface MenuAction {
  /** Handles one click, on the viewer's owning thread. */
  void click(MenuClick click);
}
