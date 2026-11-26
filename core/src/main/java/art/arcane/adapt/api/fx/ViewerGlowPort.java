package art.arcane.adapt.api.fx;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.viewer.GlowColor;

import java.util.UUID;

/**
 * Per-viewer entity glow in priority layers: each viewer sees the colour of the highest layer set on an entity.
 * The fallback is {@link LayeredViewerGlow} on the viewer SPI; Bukkit installs its glow coordinator so layers set by
 * Bukkit-side skills and core code share one stack.
 */
public interface ViewerGlowPort {
  ViewerGlowPort NONE = LayeredViewerGlow.INSTANCE;

  static ViewerGlowPort current() {
    return AdaptPorts.get(ViewerGlowPort.class, NONE);
  }

  boolean isAvailable();

  boolean set(Layer layer, EntityRef entity, PlayerRef viewer, GlowColor color);

  boolean unset(Layer layer, EntityRef entity, PlayerRef viewer);

  boolean unset(Layer layer, UUID targetId, int runtimeEntityId, PlayerRef viewer);

  void clearLayer(Layer layer);

  void discardViewer(UUID viewerId);

  enum Layer {
    GOLEMANCY(50),
    HUNTER(75),
    STEALTH_SIGHT(100),
    TRAGOUL_DEATH_SENSE(200),
    RANGED_TRAJECTORY_SIGHT(300),
    STEALTH_THREAT(400),
    TAMING_ALPHAS_COMMAND(600),
    RANGED_HEARTSEEKER(700),
    GOLEMANCY_LOCATE(800);

    private final int priority;

    Layer(int priority) {
      this.priority = priority;
    }

    public int priority() {
      return priority;
    }
  }
}
