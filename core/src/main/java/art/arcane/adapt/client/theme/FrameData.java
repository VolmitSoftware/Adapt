package art.arcane.adapt.client.theme;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Locale;

/**
 * A hover frame described as data, the {@code frame} section of a theme kit: lines of role-coloured pixels that grow
 * round the tile's outline, and pieces (frame sprites or single pixels) placed on the outline. Each line and piece has an
 * assembly window, an idle loop and a way of leaving. {@link #GENERIC} is the frame of a kit that has neither a
 * registered renderer nor a {@code frame} section; it uses palette roles only, so it fits any kit.
 */
public final class FrameData {
    private static final String[] ANCHORS = {"topLeft", "topRight", "bottomRight", "bottomLeft", "topMiddle", "rightMiddle",
            "bottomMiddle", "leftMiddle"};

    public static final FrameData GENERIC = parse(JsonParser.parseString("""
            {
              "lines": [
                {"from": "bottomMiddle", "way": "both", "roles": "bbbc", "in": [0, 0.7], "ease": "out"},
                {"from": "bottomMiddle", "way": "both", "roles": "a", "offset": 1, "in": [0.2, 0.6], "ease": "out", "alpha": 0.5}
              ],
              "pieces": [
                {"pixel": "p", "at": ["topLeft", "topRight", "bottomRight", "bottomLeft"], "in": [0.6, 0.2], "stagger": 0.05,
                 "enter": "slide", "distance": 2, "pulse": [0.35, 2.4], "leave": "away"},
                {"pixel": "q", "at": ["topLeft"], "in": [0.8, 0.2], "travel": 22, "leave": "fade"}
              ]
            }
            """));

    private final Line[] lines;
    private final Piece[] pieces;

    private FrameData(Line[] lines, Piece[] pieces) {
        this.lines = lines;
        this.pieces = pieces;
    }

    /** How a line or piece's assembly progress is shaped. */
    public enum Ease {
        LINEAR, IN, OUT, IN_OUT;

        public double apply(double p) {
            return switch (this) {
                case LINEAR -> p;
                case IN -> p * p;
                case OUT -> 1.0 - (1.0 - p) * (1.0 - p);
                case IN_OUT -> p < 0.5 ? 2.0 * p * p : 1.0 - 2.0 * (1.0 - p) * (1.0 - p);
            };
        }
    }

    /** How a piece comes in: fading, appearing whole once its window ends, sliding in from outside, or dropping from above. */
    public enum Enter {
        FADE, POP, SLIDE, DROP
    }

    /** How a piece goes: its entry played backward, or fading, falling, rising or moving outward. */
    public enum Leave {
        REVERSE, FADE, FALL, RISE, AWAY
    }

    /**
     * A ring position: one of the canvas's eight anchors ({@code 0..7}, corners clockwise from the top left, then side
     * middles clockwise from the top) plus a pixel offset, or,
     * when {@code anchor} is -1, a fraction of the ring's length.
     */
    public record Spot(int anchor, int offset, double fraction) {
    }

    /** A pixel colour: a palette role, or an exact ARGB colour when {@code role} is 0. */
    public record Paint(char role, int argb) {
    }

    /**
     * A line of pixels growing from {@code from} along the ring, both ways or one ({@code way} 0, 1 clockwise, -1
     * counter-clockwise), coloured by cycling {@code roles}, rippled by {@code wave} offsets scrolling at {@code speed}
     * pixels a second, with frame sprite {@code head} (or null) at its growing tip.
     */
    public record Line(Spot from, int way, char[] roles, int offset, double start, double length, Ease ease, int[] wave,
                       double speed, String head, double alpha) {
    }

    /**
     * A frame sprite (cycling {@code sprites} at {@code fps} when there are several) or a single pixel ({@code pixel},
     * when {@code sprites} is empty) at each of {@code at}, or at {@code spread} even steps from the first. Assembly:
     * window {@code start} and {@code length}, each further position {@code stagger} later, shaped by {@code ease},
     * entering as {@code enter} over {@code distance} pixels. Idle: an outward bob, an alpha pulse, a drift that loops
     * and a travel along the ring in pixels a second. Leaving as {@code leave}. A sprite's colours are multiplied by
     * {@code tint}, {@code 0xRRGGBB}.
     */
    public record Piece(String[] sprites, double fps, Paint pixel, Spot[] at, int spread, double start, double length,
                        double stagger, Ease ease, Enter enter, double distance, double bob, double bobPeriod, double pulse,
                        double pulsePeriod, double driftX, double driftY, double driftPeriod, double travel, Leave leave,
                        boolean flip, boolean upright, int sink, double outward, double alpha, int tint) {
    }

    public Line[] lines() {
        return lines;
    }

    public Piece[] pieces() {
        return pieces;
    }

    /**
     * Reads a kit's {@code frame} section; null when there is none. Throws {@link IllegalArgumentException} saying what is
     * wrong with a broken one.
     */
    public static FrameData parse(JsonElement section) {
        if (section == null || section.isJsonNull()) {
            return null;
        }
        if (!section.isJsonObject()) {
            throw new IllegalArgumentException("frame must be an object");
        }
        try {
            return read(section.getAsJsonObject());
        } catch (IllegalStateException | UnsupportedOperationException | NumberFormatException | ClassCastException wrongType) {
            throw new IllegalArgumentException("frame has a value of the wrong type: " + wrongType.getMessage(), wrongType);
        }
    }

    private static FrameData read(JsonObject frame) {
        JsonArray lineData = array(frame, "lines");
        JsonArray pieceData = array(frame, "pieces");
        Line[] lines = new Line[lineData.size()];
        for (int index = 0; index < lines.length; index++) {
            lines[index] = line(object(lineData.get(index), "frame line " + index));
        }
        Piece[] pieces = new Piece[pieceData.size()];
        for (int index = 0; index < pieces.length; index++) {
            pieces[index] = piece(object(pieceData.get(index), "frame piece " + index));
        }
        return new FrameData(lines, pieces);
    }

    private static Line line(JsonObject data) {
        String roles = string(data, "roles", null);
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("frame line needs \"roles\"");
        }
        double[] window = window(data, 0.0, 0.7);
        String way = string(data, "way", "both");
        int direction = switch (way) {
            case "both" -> 0;
            case "cw" -> 1;
            case "ccw" -> -1;
            default -> throw new IllegalArgumentException("frame line \"way\" must be both, cw or ccw, is " + way);
        };
        String wave = string(data, "wave", null);
        int[] offsets = null;
        if (wave != null) {
            offsets = new int[wave.length()];
            for (int index = 0; index < offsets.length; index++) {
                offsets[index] = Character.digit(wave.charAt(index), 10);
                if (offsets[index] < 0) {
                    throw new IllegalArgumentException("frame line \"wave\" must be digits, is " + wave);
                }
            }
        }
        return new Line(spot(data.get("from"), "from"), direction, roles.toCharArray(), integer(data, "offset", 0), window[0],
                window[1], ease(data), offsets, number(data, "speed", 0.0), string(data, "head", null), number(data, "alpha", 1.0));
    }

    private static Piece piece(JsonObject data) {
        JsonElement sprite = data.get("sprite");
        String[] sprites;
        if (sprite == null) {
            sprites = new String[0];
        } else if (sprite.isJsonArray()) {
            JsonArray keys = sprite.getAsJsonArray();
            sprites = new String[keys.size()];
            for (int index = 0; index < sprites.length; index++) {
                sprites[index] = keys.get(index).getAsString();
            }
        } else {
            sprites = new String[]{sprite.getAsString()};
        }
        String pixel = string(data, "pixel", null);
        if ((sprites.length == 0) == (pixel == null)) {
            throw new IllegalArgumentException("frame piece needs exactly one of \"sprite\" and \"pixel\"");
        }
        JsonArray places = array(data, "at");
        if (places.size() == 0) {
            throw new IllegalArgumentException("frame piece needs at least one \"at\" position");
        }
        Spot[] at = new Spot[places.size()];
        for (int index = 0; index < at.length; index++) {
            at[index] = spot(places.get(index), "at");
        }
        double[] window = window(data, 0.0, 0.3);
        double[] bob = pair(data, "bob");
        double[] pulse = pair(data, "pulse");
        double[] drift = triple(data, "drift");
        return new Piece(sprites, number(data, "fps", 0.0), pixel == null ? null : paint(pixel), at, integer(data, "spread", 0),
                window[0], window[1], number(data, "stagger", 0.0), ease(data),
                choice(Enter.class, string(data, "enter", "fade"), "enter"), number(data, "distance", 3.0), bob[0], bob[1],
                pulse[0], pulse[1], drift[0], drift[1], drift[2], number(data, "travel", 0.0),
                choice(Leave.class, string(data, "leave", "reverse"), "leave"), bool(data, "flip"), bool(data, "upright"),
                integer(data, "sink", 0), number(data, "outward", 0.0), number(data, "alpha", 1.0), tint(data));
    }

    private static int tint(JsonObject data) {
        String text = string(data, "tint", null);
        if (text == null) {
            return 0xFFFFFF;
        }
        if (!text.startsWith("#") || text.length() != 7) {
            throw new IllegalArgumentException("frame \"tint\" must be #rrggbb, is " + text);
        }
        return ThemeKit.color(text) & 0xFFFFFF;
    }

    static Spot spot(JsonElement element, String key) {
        if (element == null || element.isJsonNull()) {
            throw new IllegalArgumentException("frame needs \"" + key + "\"");
        }
        if (element.getAsJsonPrimitive().isNumber()) {
            return new Spot(-1, 0, element.getAsDouble());
        }
        String text = element.getAsString();
        int sign = Math.max(text.indexOf('+'), text.indexOf('-'));
        String name = sign < 0 ? text : text.substring(0, sign);
        int offset = sign < 0 ? 0 : Integer.parseInt(text.substring(sign));
        for (int anchor = 0; anchor < ANCHORS.length; anchor++) {
            if (ANCHORS[anchor].equals(name)) {
                return new Spot(anchor, offset, 0.0);
            }
        }
        throw new IllegalArgumentException("unknown frame anchor " + name);
    }

    static Paint paint(String text) {
        if (text.startsWith("#") && text.length() == 7) {
            return new Paint((char) 0, ThemeKit.color(text));
        }
        if (text.length() == 1) {
            return new Paint(text.charAt(0), 0);
        }
        throw new IllegalArgumentException("frame pixel must be a role or #rrggbb, is " + text);
    }

    private static Ease ease(JsonObject data) {
        String name = string(data, "ease", "linear");
        return switch (name) {
            case "linear" -> Ease.LINEAR;
            case "in" -> Ease.IN;
            case "out" -> Ease.OUT;
            case "inOut" -> Ease.IN_OUT;
            default -> throw new IllegalArgumentException("frame \"ease\" must be linear, in, out or inOut, is " + name);
        };
    }

    private static <E extends Enum<E>> E choice(Class<E> type, String name, String key) {
        try {
            return Enum.valueOf(type, name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("frame \"" + key + "\" cannot be " + name, unknown);
        }
    }

    private static double[] window(JsonObject data, double start, double length) {
        JsonArray window = data.has("in") ? data.getAsJsonArray("in") : null;
        if (window == null) {
            return new double[]{start, length};
        }
        if (window.size() != 2) {
            throw new IllegalArgumentException("frame \"in\" must be [start, length]");
        }
        return new double[]{window.get(0).getAsDouble(), window.get(1).getAsDouble()};
    }

    private static double[] pair(JsonObject data, String key) {
        JsonArray values = data.has(key) ? data.getAsJsonArray(key) : null;
        if (values == null) {
            return new double[]{0.0, 1.0};
        }
        if (values.size() != 2 || values.get(1).getAsDouble() <= 0.0) {
            throw new IllegalArgumentException("frame \"" + key + "\" must be [amount, period above 0]");
        }
        return new double[]{values.get(0).getAsDouble(), values.get(1).getAsDouble()};
    }

    private static double[] triple(JsonObject data, String key) {
        JsonArray values = data.has(key) ? data.getAsJsonArray(key) : null;
        if (values == null) {
            return new double[]{0.0, 0.0, 1.0};
        }
        if (values.size() != 3 || values.get(2).getAsDouble() <= 0.0) {
            throw new IllegalArgumentException("frame \"" + key + "\" must be [dx, dy, period above 0]");
        }
        return new double[]{values.get(0).getAsDouble(), values.get(1).getAsDouble(), values.get(2).getAsDouble()};
    }

    private static JsonObject object(JsonElement element, String what) {
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException(what + " must be an object");
        }
        return element.getAsJsonObject();
    }

    private static JsonArray array(JsonObject data, String key) {
        JsonElement element = data.get(key);
        return element == null || element.isJsonNull() ? new JsonArray() : element.getAsJsonArray();
    }

    private static String string(JsonObject data, String key, String fallback) {
        JsonElement element = data.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsString();
    }

    private static double number(JsonObject data, String key, double fallback) {
        JsonElement element = data.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsDouble();
    }

    private static int integer(JsonObject data, String key, int fallback) {
        JsonElement element = data.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsInt();
    }

    private static boolean bool(JsonObject data, String key) {
        JsonElement element = data.get(key);
        return element != null && !element.isJsonNull() && element.getAsBoolean();
    }
}
