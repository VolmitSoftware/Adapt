package art.arcane.adapt.api.fx;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockStateRef;

import java.util.UUID;

/**
 * Block displays only one player can see, grouped by channel (normally the adaptation name) and key. The
 * fallback is {@link PrivateBlockDisplays}, built on the display and viewer SPI; Bukkit installs its
 * display director.
 */
public interface ViewerDisplayPort {
  ViewerDisplayPort NONE = PrivateBlockDisplays.INSTANCE;

  /** Shows {@code block} at {@code location} to {@code viewer} for {@code durationTicks}, replacing the same key. */
  boolean showBlock(String channel, String key, PlayerRef viewer, Loc location, BlockStateRef block, Rgb glowColor,
                    int durationTicks);

  /**
   * Shows {@code block} stretched from {@code start} to {@code end} (same world) with a square cross-section of
   * {@code thickness} to {@code viewer} for {@code durationTicks}, replacing the same key.
   */
  boolean showLine(String channel, String key, PlayerRef viewer, Loc start, Loc end, BlockStateRef block,
                   Rgb glowColor, double thickness, int durationTicks);

  /**
   * Shows {@code block} stretched over the box from {@code minimum} spanning {@code size} blocks to {@code viewer}
   * for {@code durationTicks}, replacing the same key. With {@code inside} the faces point inwards, so the box is
   * seen from within.
   */
  boolean showCuboid(String channel, String key, PlayerRef viewer, Loc minimum, Vec3 size, BlockStateRef block,
                     Rgb glowColor, boolean inside, int durationTicks);

  /** Shows {@code block} at {@code location} to {@code viewer} until cleared, replacing the same key. */
  boolean showPersistentBlock(String channel, String key, PlayerRef viewer, Loc location, BlockStateRef block,
                              Rgb glowColor);

  /** True while the display under {@code channel} and {@code key} is shown to {@code viewerId} at the block of {@code location}. */
  boolean isShowing(String channel, String key, UUID viewerId, Loc location);

  void clearViewer(String channel, UUID viewerId);

  /** Removes the display shown to {@code viewerId} under {@code channel} and {@code key}. */
  void clearViewerKey(String channel, String key, UUID viewerId);

  void clearChannel(String channel);
}
