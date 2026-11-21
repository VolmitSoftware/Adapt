package art.arcane.adapt.client.protocol;

/**
 * How the native panel arranges a menu. {@code GRID} keeps the chest's slot geometry for menus where position carries
 * meaning; {@code LIST} shows rows of labelled entries; {@code FORM} shows settings rows with controls.
 */
public enum PanelLayout {
  GRID,
  LIST,
  FORM
}
