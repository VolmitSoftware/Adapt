package art.arcane.adapt.pack.bake;

import art.arcane.adapt.client.theme.ThemeKit;
import art.arcane.adapt.engine.asset.ThemeAssets;
import art.arcane.adapt.pack.layout.PackArt;
import art.arcane.adapt.pack.raster.Raster;

import java.nio.charset.StandardCharsets;

/**
 * A kit's Mutate button: the primary button face bound like a bookplate in the kit's colours. Its outline takes the kit's
 * outline colour, a rule in the kit's main colour runs two pixels inside it with accent pins at its corners, and a clasp
 * of the main colour joins outline and rule at the middle of each end. The middle, where the label sits, stays the
 * button's own. Roles: outline {@code 1} (else {@code k}), main {@code B} (else {@code 3}), accent {@code A} (else
 * {@code q}).
 */
record MutateFace(int outline, int main, int accent) {
    private static final int RULE = 2;
    private static final int CLASP = 4;

    static MutateFace of(ThemeAssets assets) {
        ThemeKit kit = ThemeKit.parse(assets.skill().namespace(), new String(assets.kitJson(), StandardCharsets.UTF_8));
        int main = role(kit, PackArt.OUTLINE, 'B', '3');
        return new MutateFace(role(kit, PackArt.OUTLINE, '1', 'k'), main, role(kit, main, 'A', 'q'));
    }

    private static int role(ThemeKit kit, int fallback, char... roles) {
        for (char role : roles) {
            int colour = kit.color(role);
            if (colour != 0) {
                return 0xFF000000 | colour;
            }
        }
        return fallback;
    }

    /** This kit's face over {@code button}, the primary button already nine-sliced to the face's size. */
    Raster draw(Raster button) {
        int width = button.width();
        int height = button.height();
        Raster face = button.copy();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (button.get(x, y) >>> 24 != 0 && edge(button, x, y)) {
                    face.set(x, y, outline);
                }
            }
        }
        int right = width - 1 - RULE;
        int bottom = height - 1 - RULE;
        for (int x = RULE; x <= right; x++) {
            face.set(x, RULE, main);
            face.set(x, bottom, main);
        }
        for (int y = RULE; y <= bottom; y++) {
            face.set(RULE, y, main);
            face.set(right, y, main);
        }
        for (int[] pin : new int[][]{{RULE, RULE}, {right, RULE}, {RULE, bottom}, {right, bottom}}) {
            face.set(pin[0], pin[1], accent);
        }
        int claspTop = (height - CLASP) / 2;
        for (int y = claspTop; y < claspTop + CLASP; y++) {
            for (int x = 1; x < RULE; x++) {
                face.set(x, y, main);
                face.set(width - 1 - x, y, main);
            }
        }
        return face;
    }

    /** Whether the drawn pixel at (x, y) touches a clear pixel or the raster's edge on a side. */
    private static boolean edge(Raster raster, int x, int y) {
        for (int[] step : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int nx = x + step[0];
            int ny = y + step[1];
            if (nx < 0 || ny < 0 || nx >= raster.width() || ny >= raster.height() || raster.get(nx, ny) >>> 24 == 0) {
                return true;
            }
        }
        return false;
    }
}
