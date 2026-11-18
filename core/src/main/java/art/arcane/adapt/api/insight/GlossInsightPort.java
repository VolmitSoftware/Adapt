package art.arcane.adapt.api.insight;

import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;

import java.util.List;
import java.util.UUID;

/**
 * The Gloss plugin's entity overlay (per-viewer entity readouts and overlay restriction). Installed on platforms where
 * Gloss can run; the neutral fallback reports Gloss as absent.
 */
public interface GlossInsightPort {
  String API_CLASS = "art.arcane.gloss.api.GlossAPI";

  GlossInsightPort NONE = new GlossInsightPort() {
  };

  default boolean available() {
    return false;
  }

  /** True when Gloss currently draws its own overlay for the probe entity. */
  default boolean overlayActive(LivingRef probe) {
    return false;
  }

  default boolean update(PlayerRef viewer, LivingRef target, List<String> details, long durationMillis) {
    return false;
  }

  default void clear(UUID viewerId) {
  }

  default void setRestricted(boolean restricted) {
  }

  /** Drops the cached Gloss binding so the next call discovers it again. */
  default void refresh() {
  }
}
