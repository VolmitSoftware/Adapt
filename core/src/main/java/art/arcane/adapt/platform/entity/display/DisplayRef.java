package art.arcane.adapt.platform.entity.display;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.value.Transform;

/**
 * Display entity. Spawn through {@code WorldRef.spawn*Display}. Per-viewer visibility goes through
 * {@code ViewerHost}; set {@link #setVisibleByDefault} to false in the spawn callback to start hidden.
 */
public interface DisplayRef extends EntityRef {
  Transform transform();

  void setTransform(Transform transform);

  Billboard billboard();

  void setBillboard(Billboard billboard);

  /** Light override, or null for world light. */
  Brightness brightness();

  void setBrightness(Brightness brightness);

  float viewRange();

  void setViewRange(float range);

  float shadowRadius();

  void setShadowRadius(float radius);

  /** Glow colour override, or null for the team colour. */
  Rgb glowColorOverride();

  void setGlowColorOverride(Rgb color);

  int interpolationDuration();

  void setInterpolationDuration(int ticks);

  int interpolationDelay();

  void setInterpolationDelay(int ticks);

  int teleportDuration();

  void setTeleportDuration(int ticks);

  boolean isVisibleByDefault();

  void setVisibleByDefault(boolean visible);
}
