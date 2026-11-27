package art.arcane.adapt.api.viewer;

import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.viewer.GlowColor;
import org.jetbrains.annotations.ApiStatus;

import java.util.UUID;

/**
 * Glow outlines only one player sees, stacked in {@link GlowLayer}s. Call it from the thread that owns the viewer.
 */
@ApiStatus.Experimental
public interface ViewerGlowPort {
  /** The running core's glow: the platform's own when it installs one, else Adapt's fallback on the viewer host. */
  static ViewerGlowPort current() {
    return Engine.bridge().viewers().glow();
  }

  /** False when the platform cannot send private glow; every other call then does nothing and reports false. */
  boolean isAvailable();

  /** Colours {@code entity} for {@code viewer} in {@code layer}, replacing that layer's earlier colour. */
  boolean set(GlowLayer layer, EntityRef entity, PlayerRef viewer, GlowColor color);

  /** Removes {@code layer}'s colour from {@code entity} for {@code viewer}, revealing the next layer if any. */
  boolean unset(GlowLayer layer, EntityRef entity, PlayerRef viewer);

  /** As {@link #unset(GlowLayer, EntityRef, PlayerRef)}, for an entity that may already be gone. */
  boolean unset(GlowLayer layer, UUID targetId, int runtimeEntityId, PlayerRef viewer);

  /** Removes {@code layer} from every entity for every viewer. */
  void clearLayer(GlowLayer layer);

  /** Forgets a viewer who left without sending anything to them. */
  void discardViewer(UUID viewerId);
}
