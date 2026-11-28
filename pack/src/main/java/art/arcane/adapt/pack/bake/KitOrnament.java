package art.arcane.adapt.pack.bake;

import art.arcane.adapt.client.theme.DressSlot;
import art.arcane.adapt.client.theme.DressSprite;
import art.arcane.adapt.client.theme.ThemeKit;
import art.arcane.adapt.client.theme.ThemeSprite;
import art.arcane.adapt.engine.asset.ThemeAssets;
import art.arcane.adapt.pack.layout.PackArt;
import art.arcane.adapt.pack.raster.Raster;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The ornament at the end of a maxed list row: one small piece of the kit's own dressing that fits
 * {@link PackArt#ORNAMENT_WIDTH} by {@link PackArt#ORNAMENT_HEIGHT}, every pixel drawn whole or left clear. Hanging pieces
 * come first, then the other dressing; among them the piece whose pixels stand furthest off the paper in all wins, a piece
 * whose pixels mostly fade into the paper is passed over, and a piece much taller than wide reads as a stick and loses
 * to any squatter one. A kit with no such piece gets a small lozenge in its outline, main and accent colours.
 */
final class KitOrnament {
    private static final List<DressSlot> SLOTS = List.of(DressSlot.HANG, DressSlot.CORNER, DressSlot.TRIM, DressSlot.PILE, DressSlot.TOP,
            DressSlot.FOOT, DressSlot.BIT);
    private static final int MIN_WIDTH = 3;
    private static final double MIN_WEIGHT = 8;
    /** Tallest a piece may be for its width before it reads as a stick. */
    private static final double STICK = 2.0;
    /** How far off the paper a pixel must stand to read, how far counts in full, and the share of a piece's pixels that must read. */
    static final int READS = 48;
    private static final int FULL = 128;
    private static final double READ_SHARE = 0.75;
    private static final String[] LOZENGE = {"..o..", ".omo.", "omamo", ".omo.", "..o.."};

    private KitOrnament() {
    }

    /** The ornament of {@code assets}, trimmed to its drawn pixels. */
    static Raster of(ThemeAssets assets) {
        ThemeKit kit = ThemeKit.parse(assets.skill().namespace(), new String(assets.kitJson(), StandardCharsets.UTF_8));
        Raster atlas = Raster.decode(assets.atlasPng());
        Raster best = null;
        boolean bestSquat = false;
        double bestWeight = 0;
        for (DressSlot slot : SLOTS) {
            for (DressSprite sprite : kit.dress(slot)) {
                ThemeSprite frame = sprite.frame(0, false);
                Raster piece = trimmed(solid(atlas.crop(frame.u(), frame.v(), frame.width(), frame.height())));
                if (piece == null || piece.width() > PackArt.ORNAMENT_WIDTH || piece.height() > PackArt.ORNAMENT_HEIGHT
                        || piece.width() < MIN_WIDTH) {
                    continue;
                }
                double weight = weight(piece);
                boolean squat = piece.height() <= piece.width() * STICK;
                if (weight >= MIN_WEIGHT && (best == null || (squat && !bestSquat) || (squat == bestSquat && weight > bestWeight))) {
                    best = piece;
                    bestSquat = squat;
                    bestWeight = weight;
                }
            }
            if (best != null && bestSquat) {
                return best;
            }
        }
        return best != null ? best : lozenge(kit);
    }

    /** Every pixel at least half drawn made whole, the rest cleared. */
    private static Raster solid(Raster piece) {
        Raster out = new Raster(piece.width(), piece.height());
        for (int y = 0; y < piece.height(); y++) {
            for (int x = 0; x < piece.width(); x++) {
                int argb = piece.get(x, y);
                if (argb >>> 24 >= 0x80) {
                    out.set(x, y, 0xFF000000 | argb);
                }
            }
        }
        return out;
    }

    /** {@code piece} cut to its drawn pixels, or null when nothing is drawn. */
    private static Raster trimmed(Raster piece) {
        int[] bounds = piece.bounds();
        return bounds == null ? null : piece.crop(bounds[0], bounds[1], bounds[2], bounds[3]);
    }

    /** How far {@code argb} stands off the paper: its widest channel difference from it. */
    static int standing(int argb) {
        int dr = Math.abs(((argb >> 16) & 0xFF) - ((PackArt.PAPER >> 16) & 0xFF));
        int dg = Math.abs(((argb >> 8) & 0xFF) - ((PackArt.PAPER >> 8) & 0xFF));
        int db = Math.abs((argb & 0xFF) - (PackArt.PAPER & 0xFF));
        return Math.max(dr, Math.max(dg, db));
    }

    /**
     * How well {@code piece} reads on the paper: each pixel counted by how far it stands off it, up to {@link #FULL}; 0 when
     * fewer than {@link #READ_SHARE} of its pixels stand at least {@link #READS} off it.
     */
    private static double weight(Raster piece) {
        int drawn = 0;
        int reading = 0;
        double weight = 0;
        for (int pixel : piece.pixels()) {
            if (pixel >>> 24 != 0) {
                int standing = standing(pixel);
                drawn++;
                reading += standing >= READS ? 1 : 0;
                weight += Math.min(standing, FULL) / (double) FULL;
            }
        }
        return reading < drawn * READ_SHARE ? 0 : weight;
    }

    /**
     * A 5x5 lozenge in the first of each role's colours that reads on the paper: outline {@code 1} or {@code k}, main
     * {@code B}, {@code 3} or {@code 2}, accent {@code A} or {@code q}.
     */
    private static Raster lozenge(ThemeKit kit) {
        int outline = role(kit, PackArt.OUTLINE, '1', 'k');
        int main = role(kit, PackArt.OUTLINE, 'B', '3', '2');
        int accent = role(kit, main, 'A', 'q');
        Raster out = new Raster(LOZENGE[0].length(), LOZENGE.length);
        for (int y = 0; y < LOZENGE.length; y++) {
            for (int x = 0; x < LOZENGE[y].length(); x++) {
                char c = LOZENGE[y].charAt(x);
                if (c != '.') {
                    out.set(x, y, c == 'o' ? outline : c == 'm' ? main : accent);
                }
            }
        }
        return out;
    }

    private static int role(ThemeKit kit, int fallback, char... roles) {
        for (char role : roles) {
            int colour = 0xFF000000 | kit.color(role);
            if (kit.color(role) != 0 && standing(colour) >= READS) {
                return colour;
            }
        }
        return fallback;
    }
}
