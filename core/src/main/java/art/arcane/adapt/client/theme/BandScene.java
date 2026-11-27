package art.arcane.adapt.client.theme;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * One kit's top or foot band as the demo exported it: a strip of three tile variants with drawn ends, and a short cast
 * of sprites, each a timeline of steps that adds up to the scene's period. Positions are spots on the stage, a fraction
 * of its width plus an offset; {@link BandStage} resolves them for one book. See the demo's {@code src/14-bands.js}.
 */
public final class BandScene {
    static final int MAX_ELEMENTS = 16;
    static final int LINEAR = 0;
    static final int SMOOTH = 1;
    static final int EASE_IN = 2;
    static final int EASE_OUT = 3;

    private final double period;
    private final double rest;
    private final int groundHeight;
    private final int groundPast;
    private final int tileWidth;
    private final int startWidth;
    private final int endWidth;
    private final ThemeSprite[] tiles;
    private final double[] weights;
    private final int stripFrames;
    private final double stripFps;
    private final boolean legacyOrder;
    private final ThemeSprite[] start;
    private final ThemeSprite[] end;
    private final ThemeSprite[] fill;
    private final Map<String, Sprite> sprites = new HashMap<>();
    private final List<Element> cast = new ArrayList<>();

    /** A frame list played at {@code fps}; {@code once} holds the last frame instead of looping. */
    record Clip(int[] frames, double fps, boolean once) {
    }

    /** One scene sprite: its frames both ways round, its anchor, its named points, its clips and its pixels in any frame. */
    static final class Sprite {
        final int width;
        final int height;
        final int anchorX;
        final int anchorY;
        final ThemeSprite[] frames;
        final boolean[] mask;
        final Map<String, int[]> points;
        final Map<String, Clip> clips;

        private Sprite(int width, int height, int anchorX, int anchorY, ThemeSprite[] frames, boolean[] mask,
                       Map<String, int[]> points, Map<String, Clip> clips) {
            this.width = width;
            this.height = height;
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.frames = frames;
            this.mask = mask;
            this.points = points;
            this.clips = clips;
        }

        ThemeSprite frame(int frame, boolean flip) {
            return frames[frame * 2 + (flip ? 1 : 0)];
        }

        boolean opaque(boolean flip, int x, int y) {
            if (x < 0 || y < 0 || x >= width || y >= height) {
                return false;
            }
            return mask[y * width + (flip ? width - 1 - x : x)];
        }
    }

    /** A point another step aims at: an element's named point, or a spot on the ground lifted {@code lift} units. */
    record Target(String element, String point, double[] spot, double lift) {
    }

    /** A circle round a step's path: radii, the starting angle in degrees and how many turns over the step. */
    record Orbit(double radiusX, double radiusY, double from, double turns) {
    }

    /** Particles thrown from a step's position: how many, how far apart in seconds, how long each lives, speeds, pull. */
    record Emit(int count, double every, double life, double speedXFrom, double speedXTo, double speedYFrom, double speedYTo, double gravity) {
    }

    /**
     * One step of a timeline, as authored. A null {@code clip} hides the element for the step; {@code mutatedOn} replaces
     * {@code on} on a mutated spread.
     */
    record Step(double duration, String clip, String alt, int[] on, int[] mutatedOn, String when, double[] to, double liftFrom, double liftTo,
                int ease, int face, String[] carry, Target from, Target flyTo, double arc, Target at, Orbit orbit, Emit emit) {
    }

    /**
     * One cast member as authored; a {@code behind} element is drawn under the strip, a {@code corner} one ({@code start}
     * or {@code end}) stands on that end of the strip, centred on the corner or, {@code flush}, wholly on the strip.
     * {@code face} is 0 when the author left it to the default.
     */
    record Element(String id, String sprite, double[] at, String when, int[] on, double min, List<String> needs, boolean cross, boolean behind,
                   String corner, boolean flush, int face, List<Step> steps) {
    }

    /** {@code prefix} names the atlas sprites: {@code s} for a foot, {@code t} for a top. */
    BandScene(JsonObject data, Function<String, ThemeSprite> atlas, String prefix) {
        period = data.get("period").getAsDouble();
        rest = data.get("rest").getAsDouble();
        JsonObject ground = data.getAsJsonObject("ground");
        groundHeight = ground.get("h").getAsInt();
        groundPast = ground.get("past").getAsInt();
        tileWidth = ground.get("tw").getAsInt();
        startWidth = ground.get("sw").getAsInt();
        endWidth = ground.get("ew").getAsInt();
        int count = ground.get("tiles").getAsInt();
        stripFrames = ground.has("frames") && !ground.get("frames").isJsonNull() ? ground.get("frames").getAsInt() : 1;
        stripFps = ground.has("fps") && !ground.get("fps").isJsonNull() ? ground.get("fps").getAsDouble() : 0;
        legacyOrder = ground.has("legacy") && !ground.get("legacy").isJsonNull() && ground.get("legacy").getAsBoolean();
        tiles = new ThemeSprite[count * stripFrames * 2];
        weights = new double[count];
        JsonArray weighted = ground.has("weights") && ground.get("weights").isJsonArray() ? ground.getAsJsonArray("weights") : null;
        for (int index = 0; index < count; index++) {
            weights[index] = weighted == null ? 1 : weighted.get(index).getAsDouble();
        }
        for (int index = 0; index < count; index++) {
            for (int frame = 0; frame < stripFrames; frame++) {
                String name = prefix + "g/" + index + (frame > 0 ? "~" + frame : "");
                tiles[(index * stripFrames + frame) * 2] = need(atlas, name);
                tiles[(index * stripFrames + frame) * 2 + 1] = need(atlas, name + "f");
            }
        }
        start = new ThemeSprite[]{need(atlas, prefix + "g/start"), need(atlas, prefix + "g/startf")};
        end = new ThemeSprite[]{need(atlas, prefix + "g/end"), need(atlas, prefix + "g/endf")};
        fill = new ThemeSprite[]{need(atlas, prefix + "g/fill"), need(atlas, prefix + "g/fillf")};
        for (Map.Entry<String, JsonElement> entry : data.getAsJsonObject("sprites").entrySet()) {
            sprites.put(entry.getKey(), sprite(prefix + "c/" + entry.getKey(), entry.getValue().getAsJsonObject(), atlas));
        }
        JsonObject spots = data.getAsJsonObject("spots");
        JsonArray elements = data.getAsJsonArray("cast");
        if (elements.size() > MAX_ELEMENTS) {
            throw new IllegalArgumentException("a foot scene has at most " + MAX_ELEMENTS + " elements");
        }
        for (JsonElement element : elements) {
            cast.add(element(element.getAsJsonObject(), spots));
        }
    }

    private static ThemeSprite need(Function<String, ThemeSprite> atlas, String name) {
        ThemeSprite sprite = atlas.apply(name);
        if (sprite == null) {
            throw new IllegalArgumentException("missing sprite " + name);
        }
        return sprite;
    }

    /** {@code name} is the sprite's atlas name without its frame. */
    private static Sprite sprite(String name, JsonObject entry, Function<String, ThemeSprite> atlas) {
        int width = entry.get("w").getAsInt();
        int height = entry.get("h").getAsInt();
        int count = entry.get("frames").getAsInt();
        ThemeSprite[] frames = new ThemeSprite[count * 2];
        for (int frame = 0; frame < count; frame++) {
            frames[frame * 2] = need(atlas, name + "/" + frame);
            frames[frame * 2 + 1] = need(atlas, name + "/" + frame + "f");
        }
        boolean[] mask = new boolean[width * height];
        JsonArray rows = entry.getAsJsonArray("mask");
        for (int y = 0; y < height; y++) {
            String row = rows.get(y).getAsString();
            for (int x = 0; x < width; x++) {
                mask[y * width + x] = row.charAt(x) == '#';
            }
        }
        Map<String, int[]> points = new HashMap<>();
        for (Map.Entry<String, JsonElement> point : entry.getAsJsonObject("pts").entrySet()) {
            JsonArray at = point.getValue().getAsJsonArray();
            points.put(point.getKey(), new int[]{at.get(0).getAsInt(), at.get(1).getAsInt()});
        }
        Map<String, Clip> clips = new HashMap<>();
        for (Map.Entry<String, JsonElement> clip : entry.getAsJsonObject("clips").entrySet()) {
            JsonObject c = clip.getValue().getAsJsonObject();
            clips.put(clip.getKey(), new Clip(ints(c.getAsJsonArray("f")), c.get("fps").getAsDouble(), c.get("once").getAsBoolean()));
        }
        return new Sprite(width, height, entry.get("ax").getAsInt(), entry.get("ay").getAsInt(), frames, mask, points, clips);
    }

    private static int[] ints(JsonArray array) {
        int[] out = new int[array.size()];
        for (int index = 0; index < out.length; index++) {
            out[index] = array.get(index).getAsInt();
        }
        return out;
    }

    private static String string(JsonElement element) {
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    /** A spot: its name in the scene's spots, or [fraction, offset]. */
    private static double[] spot(JsonElement value, JsonObject spots) {
        JsonArray pair = value.isJsonPrimitive() ? spots.getAsJsonArray(value.getAsString()) : value.getAsJsonArray();
        if (pair == null) {
            throw new IllegalArgumentException("no spot " + value);
        }
        return new double[]{pair.get(0).getAsDouble(), pair.size() > 1 ? pair.get(1).getAsDouble() : 0};
    }

    private static Target target(JsonElement value, JsonObject spots) {
        if (value == null) {
            return null;
        }
        JsonArray ref = value.getAsJsonArray();
        if (ref.get(0).isJsonPrimitive() && ref.get(0).getAsJsonPrimitive().isString()) {
            return new Target(ref.get(0).getAsString(), ref.size() > 1 ? string(ref.get(1)) : null, null, 0);
        }
        return new Target(null, null, spot(ref.get(0), spots), ref.size() > 1 ? ref.get(1).getAsDouble() : 0);
    }

    private static int ease(String name, int fallback) {
        if (name == null) {
            return fallback;
        }
        return switch (name) {
            case "linear" -> LINEAR;
            case "smooth" -> SMOOTH;
            case "in" -> EASE_IN;
            case "out" -> EASE_OUT;
            default -> throw new IllegalArgumentException("no ease " + name);
        };
    }

    private static Element element(JsonObject e, JsonObject spots) {
        List<Step> steps = new ArrayList<>();
        for (JsonElement s : e.getAsJsonArray("steps")) {
            JsonArray step = s.getAsJsonArray();
            JsonObject o = step.size() > 2 && step.get(2).isJsonObject() ? step.get(2).getAsJsonObject() : new JsonObject();
            JsonElement y = o.get("y");
            double liftFrom = 0;
            double liftTo = 0;
            if (y != null) {
                liftFrom = y.isJsonArray() ? y.getAsJsonArray().get(0).getAsDouble() : y.getAsDouble();
                liftTo = y.isJsonArray() ? y.getAsJsonArray().get(1).getAsDouble() : liftFrom;
            }
            String[] carry = null;
            JsonElement carried = o.get("carry");
            if (carried != null) {
                if (carried.isJsonArray()) {
                    JsonArray list = carried.getAsJsonArray();
                    carry = new String[list.size()];
                    for (int index = 0; index < carry.length; index++) {
                        carry[index] = list.get(index).getAsString();
                    }
                } else {
                    carry = new String[]{carried.getAsString()};
                }
            }
            JsonObject fly = o.has("fly") ? o.getAsJsonObject("fly") : null;
            Orbit orbit = null;
            if (o.has("orbit")) {
                JsonObject circle = o.getAsJsonObject("orbit");
                JsonArray radius = circle.getAsJsonArray("r");
                orbit = new Orbit(radius.get(0).getAsDouble(), radius.get(1).getAsDouble(), circle.has("from") ? circle.get("from").getAsDouble() : 0,
                        circle.has("turns") ? circle.get("turns").getAsDouble() : 1);
            }
            Emit emit = null;
            if (o.has("emit")) {
                JsonObject spray = o.getAsJsonObject("emit");
                JsonArray vx = spray.getAsJsonArray("vx");
                JsonArray vy = spray.getAsJsonArray("vy");
                emit = new Emit(spray.get("n").getAsInt(), spray.has("every") ? spray.get("every").getAsDouble() : 0, spray.get("life").getAsDouble(),
                        vx.get(0).getAsDouble(), vx.get(1).getAsDouble(), vy.get(0).getAsDouble(), vy.get(1).getAsDouble(),
                        spray.has("g") ? spray.get("g").getAsDouble() : 0);
            }
            int fallback = fly != null || orbit != null ? LINEAR : SMOOTH;
            steps.add(new Step(step.get(0).getAsDouble(), string(step.get(1)), string(o.get("alt")), o.has("on") ? ints(o.getAsJsonArray("on")) : null,
                    o.has("mutatedOn") ? ints(o.getAsJsonArray("mutatedOn")) : null,
                    string(o.get("when")), o.has("to") ? spot(o.get("to"), spots) : null, liftFrom, liftTo,
                    ease(string(o.get("ease")), fallback), o.has("face") ? o.get("face").getAsInt() : 0, carry,
                    fly != null ? target(fly.get("from"), spots) : null, fly != null ? target(fly.get("to"), spots) : null,
                    fly != null ? (fly.has("arc") ? fly.get("arc").getAsDouble() : 0) : o.has("arc") ? o.get("arc").getAsDouble() : 0,
                    target(o.get("at"), spots), orbit, emit));
        }
        List<String> needs = new ArrayList<>();
        if (e.has("needs")) {
            for (JsonElement need : e.getAsJsonArray("needs")) {
                needs.add(need.getAsString());
            }
        }
        return new Element(e.get("id").getAsString(), e.get("sprite").getAsString(), e.has("at") ? spot(e.get("at"), spots) : new double[]{0, 0},
                string(e.get("when")), e.has("on") ? ints(e.getAsJsonArray("on")) : null, e.has("min") ? e.get("min").getAsDouble() : 0,
                List.copyOf(needs), e.has("cross") && e.get("cross").getAsBoolean(), e.has("behind") && e.get("behind").getAsBoolean(),
                string(e.get("corner")), e.has("flush") && e.get("flush").getAsBoolean(), e.has("face") ? e.get("face").getAsInt() : 0,
                List.copyOf(steps));
    }

    static double ease(int kind, double q) {
        return switch (kind) {
            case SMOOTH -> q * q * (3 - 2 * q);
            case EASE_IN -> q * q;
            case EASE_OUT -> q * (2 - q);
            default -> q;
        };
    }

    public double period() {
        return period;
    }

    /** The loop time a still page shows. */
    public double rest() {
        return rest;
    }

    public int groundHeight() {
        return groundHeight;
    }

    /** Rows of the strip past its shared line: a foot's crests above it, a top's fringe below it. */
    int groundPast() {
        return groundPast;
    }

    /** Whether the band brings its own corner pieces, in place of the kit's old ones. */
    boolean cornered() {
        for (Element element : cast) {
            if (element.corner() != null) {
                return true;
            }
        }
        return false;
    }

    int tileWidth() {
        return tileWidth;
    }

    int startWidth() {
        return startWidth;
    }

    int endWidth() {
        return endWidth;
    }

    int tileCount() {
        return tiles.length / 2 / stripFrames;
    }

    /** How many frames the strip's tiles step through, and how fast; 1 frame or 0 fps is a still strip. */
    int stripFrames() {
        return stripFrames;
    }

    double stripFps() {
        return stripFps;
    }

    /** Whether tiles follow the first, hash-per-index order instead of the seeded stream. */
    boolean legacyOrder() {
        return legacyOrder;
    }

    /** Whether the strip is drawn with the scene each frame: a piece stands behind it, or it moves. */
    boolean moving() {
        return behind() || (stripFrames > 1 && stripFps > 0);
    }

    /** The tile a strip lays at {@code pick}, a fraction in [0, 1), by the tiles' weights. */
    int pickTile(double pick) {
        double total = 0;
        for (double weight : weights) {
            total += weight;
        }
        double at = pick * total;
        int v = 0;
        while (v < weights.length - 1 && at >= weights[v]) {
            at -= weights[v];
            v++;
        }
        return v;
    }

    ThemeSprite tile(int index, int frame, boolean flip) {
        return tiles[(index * stripFrames + frame) * 2 + (flip ? 1 : 0)];
    }

    ThemeSprite start(boolean flip) {
        return start[flip ? 1 : 0];
    }

    ThemeSprite end(boolean flip) {
        return end[flip ? 1 : 0];
    }

    ThemeSprite fill(boolean flip) {
        return fill[flip ? 1 : 0];
    }

    Sprite sprite(String name) {
        Sprite sprite = sprites.get(name);
        if (sprite == null) {
            throw new IllegalArgumentException("no scene sprite " + name);
        }
        return sprite;
    }

    List<Element> cast() {
        return cast;
    }

    /** Whether some of the cast is drawn under the strip, so the strip is drawn with the scene each frame. */
    boolean behind() {
        for (Element element : cast) {
            if (element.behind()) {
                return true;
            }
        }
        return false;
    }
}
