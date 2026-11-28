package art.arcane.adapt.pack.bake;

import art.arcane.adapt.pack.layout.PackArt;
import art.arcane.adapt.pack.raster.Raster;

import java.util.Map;
import java.util.function.Function;

/** The rules that make art the design describes but ships no file for: greyed icons, faded fills, and paint glyphs. */
final class ArtRules {
    private static final int ARROW = 0xFF5A4430;

    private ArtRules() {
    }

    /** The greyed copy of an icon for level 0, locked and lost entries: its luma on a warm grey, at 80% opacity. */
    static Raster dim(Raster icon) {
        Raster out = new Raster(icon.width(), icon.height());
        for (int y = 0; y < icon.height(); y++) {
            for (int x = 0; x < icon.width(); x++) {
                int argb = icon.get(x, y);
                double luma = ((argb >> 16) & 0xFF) * 0.3 + ((argb >> 8) & 0xFF) * 0.59 + (argb & 0xFF) * 0.11;
                int alpha = (int) Math.round((argb >>> 24) * 0.8);
                out.set(x, y, alpha << 24 | channel(luma * 0.45 + 132) << 16 | channel(luma * 0.45 + 120) << 8 | channel(luma * 0.45 + 96));
            }
        }
        return out;
    }

    private static int channel(double value) {
        return (int) Math.max(0, Math.min(255, Math.round(value)));
    }

    /** {@code raster} with its alpha scaled by {@code alpha}. */
    static Raster faded(Raster raster, double alpha) {
        Raster out = new Raster(raster.width(), raster.height());
        for (int y = 0; y < raster.height(); y++) {
            for (int x = 0; x < raster.width(); x++) {
                int argb = raster.get(x, y);
                out.set(x, y, (int) Math.round((argb >>> 24) * alpha) << 24 | (argb & 0xFFFFFF));
            }
        }
        return out;
    }

    /**
     * The raster of paint glyph {@code piece}: a kit piece nine-sliced with border 4, a catalog sprite, a design piece, or
     * drawn by rule; null when its source is missing.
     */
    static Raster paint(PackArt.Paint piece, Function<String, byte[]> sprites, Map<String, Raster> named) {
        Raster raster = new Raster(piece.width(), piece.height());
        switch (piece.kind()) {
            case NINE -> {
                byte[] png = sprites.apply(piece.source());
                return png == null ? null : KitBake.nine(png, piece.width(), piece.height(), 4);
            }
            case SPRITE -> {
                byte[] png = sprites.apply(piece.source());
                return png == null ? null : reduced(Raster.decode(png), piece.width(), piece.height());
            }
            case ART -> {
                return named.get(piece.source());
            }
            case ARROW -> {
                fill(raster, 0, 3, 14, 3, ARROW);
                for (int step = 0; step < 5; step++) {
                    fill(raster, 14 + step, step, 1, 9 - (step * 2), ARROW);
                }
            }
            case TRACK -> {
                fill(raster, 0, 0, piece.width(), piece.height(), PackArt.OUTLINE);
                fill(raster, 0, 1, piece.width(), piece.height() - 2, PackArt.TRACK);
            }
            case CAP -> fill(raster, 0, 0, piece.width(), piece.height(), PackArt.OUTLINE);
            case FILL -> fill(raster, 0, 0, piece.width(), piece.height(), 0xFFFFFFFF);
            case SHADE -> fill(raster, 0, 0, piece.width(), piece.height(), PackArt.SHADE);
            case WASH -> fill(raster, 0, 0, piece.width(), piece.height(),
                    (piece.source().equals("strong") ? PackArt.WASH_STRONG_ALPHA : PackArt.WASH_ALPHA) << 24 | 0xFFFFFF);
            case DOTS -> {
                boolean rows = piece.source().equals("rows");
                for (int along = 0; along < (rows ? piece.width() : piece.height()); along += 2) {
                    if (rows) {
                        raster.set(along, 0, 0xFFFFFFFF);
                        raster.set(along, piece.height() - 1, 0xFFFFFFFF);
                    } else {
                        raster.set(0, along, 0xFFFFFFFF);
                    }
                }
            }
        }
        return raster;
    }

    /**
     * {@code sprite} at {@code width} by {@code height} when it is a whole multiple of that size: each pixel the
     * alpha-weighted mean of its block, kept only where at least half the block is drawn so the edges stay hard.
     */
    static Raster reduced(Raster sprite, int width, int height) {
        if (sprite.width() == width && sprite.height() == height) {
            return sprite;
        }
        int stepX = sprite.width() / width;
        int stepY = sprite.height() / height;
        Raster out = new Raster(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                long alpha = 0;
                long red = 0;
                long green = 0;
                long blue = 0;
                for (int dy = 0; dy < stepY; dy++) {
                    for (int dx = 0; dx < stepX; dx++) {
                        int argb = sprite.get((x * stepX) + dx, (y * stepY) + dy);
                        int a = argb >>> 24;
                        alpha += a;
                        red += (long) ((argb >>> 16) & 0xFF) * a;
                        green += (long) ((argb >>> 8) & 0xFF) * a;
                        blue += (long) (argb & 0xFF) * a;
                    }
                }
                if (alpha * 2 >= 255L * stepX * stepY) {
                    out.set(x, y, 0xFF000000 | (int) (red / alpha) << 16 | (int) (green / alpha) << 8 | (int) (blue / alpha));
                }
            }
        }
        return out;
    }

    private static void fill(Raster raster, int x, int y, int width, int height, int argb) {
        for (int row = y; row < y + height; row++) {
            for (int column = x; column < x + width; column++) {
                raster.set(column, row, argb);
            }
        }
    }

    /** One part of a ribbon drawn {@link KitBake#RIBBON_WIDTH} wide: its head, its tail, or its middle column repeated. */
    static Raster ribbon(PackArt.Paint part, Raster ribbon) {
        Raster out = new Raster(part.width(), part.height());
        int from = switch (part.source()) {
            case "head" -> 0;
            case "tail" -> ribbon.width() - PackArt.RIBBON_TAIL;
            default -> -1;
        };
        for (int x = 0; x < part.width(); x++) {
            for (int y = 0; y < part.height(); y++) {
                out.set(x, y, ribbon.get(from < 0 ? PackArt.RIBBON_HEAD : from + x, y));
            }
        }
        return out;
    }
}
