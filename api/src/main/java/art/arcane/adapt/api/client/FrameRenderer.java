package art.arcane.adapt.api.client;

import org.jetbrains.annotations.ApiStatus;

/**
 * Draws one skill's hover frame: its assembly as {@link FrameCanvas#assembled()} runs 0 to 1, an idle loop on
 * {@link FrameCanvas#seconds()}, and its own way of leaving while {@link FrameCanvas#leaving()} is set.
 *
 * <p>Called every frame for every hovered tile on the render thread. Allocate nothing and keep no reference to
 * the canvas; one canvas is reused for every frame drawn.
 */
@ApiStatus.Experimental
@FunctionalInterface
public interface FrameRenderer {
  /** Draws the frame of one tile for the current render frame. */
  void draw(FrameCanvas canvas);
}
