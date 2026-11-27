package art.arcane.adapt.pack.screen;

import art.arcane.adapt.pack.bake.KitBake;
import art.arcane.adapt.pack.bake.LayerBake;
import art.arcane.adapt.pack.bake.SlotGrid;
import art.arcane.adapt.pack.raster.Raster;

/**
 * What a baked kit page can ever draw, over all frames of all its moving regions: its extent, and the window pixels some
 * frame leaves uncovered. Moving regions carry their whole pixel, page art under the motion included, and the still layer
 * is clear under them.
 */
final class PageExtent {
  private PageExtent() {
  }

  /** The container box {x0, y0, x1, y1} (inclusive) of every pixel the page draws in any frame. */
  static int[] of(KitBake.Page page) {
    int[] box = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
    grow(box, page.still(), page.left(), page.top());
    for (LayerBake.Motion motion : page.motions()) {
      for (Raster frame : motion.frames()) {
        grow(box, frame, page.left() + motion.x(), page.top() + motion.y());
      }
    }
    return box;
  }

  /** Window pixels where neither the still layer nor every frame of a region over them is opaque. */
  static int uncovered(KitBake.Page page) {
    int uncovered = 0;
    for (int y = 0; y < SlotGrid.CONTAINER_HEIGHT; y++) {
      for (int x = 0; x < SlotGrid.CONTAINER_WIDTH; x++) {
        if (page.still().get(x - page.left(), y - page.top()) >>> 24 == 0xFF || moving(page, x, y)) {
          continue;
        }
        uncovered++;
      }
    }
    return uncovered;
  }

  /** Pixels in the header row (container x {@code left} to 195, y 0 to 17) that a slot item draws, in any frame. */
  static int movingInHeader(KitBake.Page page, int left) {
    int moving = 0;
    for (LayerBake.Motion motion : page.motions()) {
      for (int y = 0; y < motion.height(); y++) {
        for (int x = 0; x < motion.width(); x++) {
          int cx = page.left() + motion.x() + x;
          int cy = page.top() + motion.y() + y;
          if (motion.mask()[y * motion.width() + x] && cx >= left && cy >= 0 && cx < 196 && cy < 18) {
            moving++;
          }
        }
      }
    }
    return moving;
  }

  private static boolean moving(KitBake.Page page, int x, int y) {
    for (LayerBake.Motion motion : page.motions()) {
      int mx = x - page.left() - motion.x();
      int my = y - page.top() - motion.y();
      if (mx < 0 || my < 0 || mx >= motion.width() || my >= motion.height() || !motion.mask()[my * motion.width() + mx]) {
        continue;
      }
      boolean opaque = true;
      for (Raster frame : motion.frames()) {
        opaque &= frame.get(mx, my) >>> 24 == 0xFF;
      }
      if (opaque) {
        return true;
      }
    }
    return false;
  }

  private static void grow(int[] box, Raster raster, int left, int top) {
    for (int y = 0; y < raster.height(); y++) {
      for (int x = 0; x < raster.width(); x++) {
        if (raster.get(x, y) >>> 24 != 0) {
          box[0] = Math.min(box[0], left + x);
          box[1] = Math.min(box[1], top + y);
          box[2] = Math.max(box[2], left + x);
          box[3] = Math.max(box[3], top + y);
        }
      }
    }
  }
}
