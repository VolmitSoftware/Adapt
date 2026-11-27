package art.arcane.adapt.api.viewer;

import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockStateRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.UUID;

/**
 * Block displays only one player can see, grouped by channel (normally the adaptation id) and key. A show with a key
 * already in use replaces it. Durations are in ticks.
 */
@ApiStatus.Experimental
public interface ViewerDisplayPort {
  /** The running core's displays: the platform's own when it installs one, else Adapt's fallback. */
  static ViewerDisplayPort current() {
    return Engine.bridge().viewers().displays();
  }

  /**
   * Shows {@code block} at the block of {@code location} to {@code viewer} alone, outlined in {@code glowColor}, for
   * {@code durationTicks}, at least 1. False when nothing was shown, such as for a viewer who is offline.
   */
  boolean showBlock(String channel, String key, PlayerRef viewer, Loc location, BlockStateRef block, Rgb glowColor,
                    int durationTicks);

  /** {@code block} stretched from {@code start} to {@code end} in one world, with a square cross-section. */
  boolean showLine(String channel, String key, PlayerRef viewer, Loc start, Loc end, BlockStateRef block,
                   Rgb glowColor, double thickness, int durationTicks);

  /** A box from {@code minimum} spanning {@code size} blocks; with {@code inside} the faces point inwards. */
  boolean showCuboid(String channel, String key, PlayerRef viewer, Loc minimum, Vec3 size, BlockStateRef block,
                     Rgb glowColor, boolean inside, int durationTicks);

  /** A display that stays until cleared. */
  boolean showPersistentBlock(String channel, String key, PlayerRef viewer, Loc location, BlockStateRef block,
                              Rgb glowColor);

  /** True while that display is shown to {@code viewerId} at the block of {@code location}. */
  boolean isShowing(String channel, String key, UUID viewerId, Loc location);

  /** Removes every display of {@code channel} shown to the viewer. */
  void clearViewer(String channel, UUID viewerId);

  /** Removes the one display under {@code channel} and {@code key} shown to the viewer. */
  void clearViewerKey(String channel, String key, UUID viewerId);

  /** Removes every display of {@code channel} for every viewer. */
  void clearChannel(String channel);
}
