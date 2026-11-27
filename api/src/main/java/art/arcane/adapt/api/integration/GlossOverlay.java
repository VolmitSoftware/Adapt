package art.arcane.adapt.api.integration;

import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The Gloss plugin's entity overlay: per-viewer readouts drawn over entities. Without Gloss every call does nothing
 * and {@link #available()} is false. Needs a running core.
 */
@ApiStatus.Experimental
public final class GlossOverlay {
  /** The class Gloss registers its service under; a service registration with this name means Gloss came up. */
  public static final String SERVICE_CLASS = "art.arcane.gloss.api.GlossAPI";

  private GlossOverlay() {
  }

  /** Whether Gloss runs and answers. */
  public static boolean available() {
    return Engine.bridge().integrations().glossAvailable();
  }

  /** Whether Gloss currently draws its own overlay for the entity. */
  public static boolean overlayActive(LivingRef entity) {
    return Engine.bridge().integrations().glossOverlayActive(Objects.requireNonNull(entity, "entity"));
  }

  /** Shows {@code lines} over {@code target} to the viewer for {@code durationMillis}; false when Gloss refused. */
  public static boolean show(PlayerRef viewer, LivingRef target, List<String> lines, long durationMillis) {
    return Engine.bridge().integrations().glossShow(Objects.requireNonNull(viewer, "viewer"),
        Objects.requireNonNull(target, "target"), List.copyOf(lines), durationMillis);
  }

  /** Removes what was shown to the viewer. */
  public static void clear(UUID viewer) {
    Engine.bridge().integrations().glossClear(Objects.requireNonNull(viewer, "viewer"));
  }

  /** Whether Gloss shows overlays only for targets an Adapt readout selected. */
  public static void restrict(boolean restricted) {
    Engine.bridge().integrations().glossRestrict(restricted);
  }

  /** Looks Gloss up again, after it was enabled or registered its service. */
  public static void refresh() {
    Engine.bridge().integrations().glossRefresh();
  }
}
