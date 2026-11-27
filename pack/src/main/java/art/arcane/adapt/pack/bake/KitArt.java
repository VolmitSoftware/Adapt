package art.arcane.adapt.pack.bake;

import art.arcane.adapt.pack.raster.Raster;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;

/**
 * Art drawn from a kit's palette, so every kit, a third-party one included, gets it: a 16x16 tooltip frame (nine-slice,
 * border 5) in the shape of the tome's tooltip piece, and the four toast corner brackets laid over a skill's icon. Roles:
 * outline {@code 1} (else {@code k}), main {@code B} (else {@code 3}), accent {@code A} (else {@code q}, else gold), light
 * {@code 4} (else the page colour).
 */
public final class KitArt {
    public static final int SIZE = 16;
    public static final int TOOLTIP_BORDER = 5;
    private static final int PAGE = 0xFFF6EDD8;
    private static final int GOLD = 0xFFE2B648;
    private static final String[] CORNER_TOP = {"kkkk........kkkk", "kaak........kaak", "kak..........kak", "kk............kk"};
    private static final String[] CORNER_BOTTOM = {"kk............kk", "kak..........kak", "kaak........kaak", "kkkk........kkkk"};

    private final int dark;
    private final int main;
    private final int accent;
    private final int light;

    private KitArt(int dark, int main, int accent, int light) {
        this.dark = dark;
        this.main = main;
        this.accent = accent;
        this.light = light;
    }

    /** The roles of the kit whose JSON is {@code kitJson}. */
    public static KitArt of(byte[] kitJson) {
        JsonObject palette = JsonParser.parseString(new String(kitJson, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("palette");
        return new KitArt(role(palette, PAGE, "1", "k"), role(palette, PAGE, "B", "3"), role(palette, GOLD, "A", "q"), role(palette, PAGE, "4"));
    }

    private static int role(JsonObject palette, int fallback, String... names) {
        for (String name : names) {
            if (palette.has(name)) {
                return 0xFF000000 | Integer.parseInt(palette.get(name).getAsString().substring(1), 16);
            }
        }
        return fallback;
    }

    /** The tooltip frame over {@code shape} (the tome's 16x16 tooltip piece): outline, main, then two page tints inward. */
    public Raster tooltip(Raster shape) {
        Raster tip = new Raster(SIZE, SIZE);
        int inner = mix(PAGE, light, 0.45);
        int paper = mix(PAGE, light, 0.18);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if ((shape.get(x, y) >>> 24) == 0) {
                    continue;
                }
                int edge = Math.min(Math.min(x, y), Math.min(SIZE - 1 - x, SIZE - 1 - y));
                tip.set(x, y, edge == 0 ? dark : edge == 1 ? main : edge == 2 ? inner : paper);
            }
        }
        for (int[] pin : new int[][]{{1, 1}, {14, 1}, {1, 14}, {14, 14}}) {
            tip.set(pin[0], pin[1], accent);
        }
        for (int[] corner : new int[][]{{0, 0}, {15, 0}, {0, 15}, {15, 15}}) {
            tip.set(corner[0], corner[1], tip.get(corner[0], corner[1]) & 0x00FFFFFF);
        }
        return tip;
    }

    /** The toast corner brackets, clear between them, to lay over a 16x16 icon. */
    public Raster toast() {
        Raster corners = new Raster(SIZE, SIZE);
        stamp(corners, 0, CORNER_TOP);
        stamp(corners, 12, CORNER_BOTTOM);
        return corners;
    }

    private void stamp(Raster into, int top, String[] rows) {
        for (int row = 0; row < rows.length; row++) {
            for (int column = 0; column < rows[row].length(); column++) {
                char c = rows[row].charAt(column);
                if (c != '.') {
                    into.set(column, top + row, c == 'k' ? dark : main);
                }
            }
        }
    }

    private static int mix(int from, int to, double t) {
        int r = (int) Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = (int) Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = (int) Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
}
