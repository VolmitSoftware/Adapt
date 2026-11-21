package art.arcane.adapt.platform.menu;

/**
 * How clients that draw menus natively arrange a menu: {@code GRID} keeps the chest geometry (position carries
 * meaning), {@code LIST} shows rows of entries, {@code FORM} shows settings rows with controls.
 */
public enum MenuLayout {
  GRID,
  LIST,
  FORM
}
