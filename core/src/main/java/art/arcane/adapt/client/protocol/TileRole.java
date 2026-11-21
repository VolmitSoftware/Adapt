package art.arcane.adapt.client.protocol;

/**
 * What a tile is in its menu. {@code DECORATION} is background filler the native panel never draws; {@code HEADER}
 * starts a section; {@code INFO} shows a fact without an action; {@code NAVIGATION} opens another menu.
 */
public enum TileRole {
  ENTRY,
  SETTING,
  ACTION,
  NAVIGATION,
  HEADER,
  DECORATION,
  INFO
}
