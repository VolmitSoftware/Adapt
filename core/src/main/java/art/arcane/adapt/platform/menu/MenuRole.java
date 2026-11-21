package art.arcane.adapt.platform.menu;

/**
 * What a button is in its menu, for clients that draw menus natively. {@code DECORATION} is background filler they
 * never draw; {@code HEADER} starts a section; {@code INFO} shows a fact; {@code NAVIGATION} opens another menu.
 */
public enum MenuRole {
  ENTRY,
  SETTING,
  ACTION,
  NAVIGATION,
  HEADER,
  DECORATION,
  INFO
}
