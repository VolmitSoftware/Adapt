package art.arcane.adapt.api.asset;

import org.jetbrains.annotations.ApiStatus;

/**
 * What an {@link AssetEntry} is used for. The folder a file sits in decides its kind, see {@link AssetBundle}.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.Experimental
public enum AssetKind {
  /** A 16x16 skill or adaptation icon. */
  SPRITE,
  /** A skill's theme description, JSON. */
  THEME_KIT,
  /** The texture atlas a theme kit draws from. */
  THEME_ATLAS,
  /** The emblem a skill gives a mutation it leads. */
  EMBLEM,
  /** The trim a skill gives a mutation it joins as partner. */
  TRIM,
  /** A 16x16 preference choice icon. */
  PREFERENCE_ICON,
  /**
   * Any other texture below {@code textures/gui/} or {@code textures/font/}: frame kits, toasts, HUD, glyph and mark
   * art, font sheets. Any size; drawn by its sprite id.
   */
  GUI_TEXTURE,
  /** A resource pack model, JSON. */
  MODEL,
  /** Any other file; copied into the resource pack untouched. */
  OTHER
}
