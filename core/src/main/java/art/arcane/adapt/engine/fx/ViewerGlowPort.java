package art.arcane.adapt.engine.fx;

import art.arcane.adapt.AdaptPorts;

/**
 * Per-viewer entity glow in priority layers: each viewer sees the colour of the highest layer set on an entity.
 * The fallback is {@link LayeredViewerGlow} on the viewer SPI; Bukkit installs its glow coordinator so layers set by
 * Bukkit-side skills and core code share one stack.
 */
public interface ViewerGlowPort extends art.arcane.adapt.api.viewer.ViewerGlowPort {
  ViewerGlowPort NONE = LayeredViewerGlow.INSTANCE;

  static ViewerGlowPort current() {
    return AdaptPorts.get(ViewerGlowPort.class, NONE);
  }
}
