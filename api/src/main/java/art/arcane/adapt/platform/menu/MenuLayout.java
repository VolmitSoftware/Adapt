package art.arcane.adapt.platform.menu;

import org.jetbrains.annotations.ApiStatus;

/**
 * How clients that draw menus natively arrange a menu: {@code GRID} keeps the chest geometry (position carries
 * meaning), {@code LIST} shows rows of entries, {@code FORM} shows settings rows with controls.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.AvailableSince("1.0.0")
public enum MenuLayout {
  GRID,
  LIST,
  FORM
}
