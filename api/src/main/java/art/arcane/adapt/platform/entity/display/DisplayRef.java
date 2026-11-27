package art.arcane.adapt.platform.entity.display;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.value.Transform;
import org.jetbrains.annotations.ApiStatus;

/**
 * Display entity. Spawn through {@code WorldRef.spawn*Display}. Per-viewer visibility goes through
 * {@code ViewerHost}; set {@link #setVisibleByDefault} to false in the spawn callback to start hidden.
 */
@ApiStatus.AvailableSince("1.0.0")
public interface DisplayRef extends EntityRef {
  /** Translation, rotations and scale applied to the rendered content. */
  Transform transform();

  /** Replaces the transform. */
  void setTransform(Transform transform);

  /** How the display turns to face the camera. */
  Billboard billboard();

  /** Sets how the display turns to face the camera. */
  void setBillboard(Billboard billboard);

  /** Light override, or null for world light. */
  Brightness brightness();

  /** Sets the light override; null uses world light. */
  void setBrightness(Brightness brightness);

  /** View range multiplier; 1 is the vanilla default distance. */
  float viewRange();

  /** Sets the view range multiplier. */
  void setViewRange(float range);

  /** Radius of the shadow under the display in blocks; 0 for none. */
  float shadowRadius();

  /** Sets the shadow radius in blocks. */
  void setShadowRadius(float radius);

  /** Glow colour override, or null for the team colour. */
  Rgb glowColorOverride();

  /** Sets the glow colour; null uses the team colour. */
  void setGlowColorOverride(Rgb color);

  /** Ticks clients take to animate to a new transform. */
  int interpolationDuration();

  /** Sets the ticks clients take to animate to a new transform. */
  void setInterpolationDuration(int ticks);

  /** Ticks clients wait before starting the transform animation. */
  int interpolationDelay();

  /** Sets the ticks clients wait before starting the transform animation. */
  void setInterpolationDelay(int ticks);

  /** Ticks clients take to slide to a new position after a teleport. */
  int teleportDuration();

  /** Sets the ticks clients take to slide to a new position after a teleport. */
  void setTeleportDuration(int ticks);

  /** False when the display is hidden from players unless shown to them one by one. */
  boolean isVisibleByDefault();

  /** Shows the display to every player, or hides it from everyone not shown it one by one. */
  void setVisibleByDefault(boolean visible);
}
