package art.arcane.adapt.pack.bake;

import art.arcane.adapt.pack.raster.Raster;

import java.util.List;

/** A baked page on its canvas: the still layer and the moving regions in canvas pixels. */
public record BakedLayer(Raster still, List<LayerBake.Motion> motions) {
    public BakedLayer {
        motions = List.copyOf(motions);
    }

    BakedLayer(int width, int height, Raster still, List<LayerBake.Motion> motions) {
        this(checked(still, width, height), motions);
    }

    private static Raster checked(Raster still, int width, int height) {
        if (still.width() != width || still.height() != height) {
            throw new IllegalArgumentException("still layer does not cover the canvas");
        }
        return still;
    }

    /** Texture pixels the regions need: each distinct frame's area. */
    public long motionArea() {
        long area = 0;
        for (LayerBake.Motion motion : motions) {
            area += (long) motion.width() * motion.height() * motion.frames().size();
        }
        return area;
    }
}
