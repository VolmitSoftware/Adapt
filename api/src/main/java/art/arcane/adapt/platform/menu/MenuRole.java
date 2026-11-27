package art.arcane.adapt.platform.menu;

import org.jetbrains.annotations.ApiStatus;

/**
 * What a button is in its menu, for clients that draw menus natively. {@code DECORATION} is background filler they
 * never draw; {@code HEADER} starts a section; {@code INFO} shows a fact; {@code NAVIGATION} opens another menu.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.AvailableSince("1.0.0")
public enum MenuRole {
  /** One item in a list of similar entries. */
  ENTRY,
  /** A control that changes a setting. */
  SETTING,
  /** A button that does something when clicked. */
  ACTION,
  NAVIGATION,
  HEADER,
  DECORATION,
  INFO
}
