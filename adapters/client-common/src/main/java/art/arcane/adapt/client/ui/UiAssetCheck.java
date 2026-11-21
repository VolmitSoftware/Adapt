package art.arcane.adapt.client.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Rules for the files under {@code assets/adapt/textures/gui/}, shared by the sprite cache (a file that breaks them is
 * not drawn) and the build's artifact check:
 * <ul>
 *   <li>{@code skill/}, {@code adaptation/}, {@code toast/}, {@code hud/}: square PNG icons, a power of two from 16 to 512</li>
 *   <li>{@code kit/<name>.png}: nine-slice chrome up to 1024 pixels a side, each with {@code kit/<name>.json} such as
 *   {@code {"border": 4}} or {@code {"border": 8, "scale": 2}} (border in texture pixels, scale in texture pixels per GUI
 *   unit; size and border divisible by scale, centre at least one pixel); {@code "fill": "tile"} repeats the edges and
 *   centre at their own size instead of stretching them</li>
 *   <li>anything else: PNG up to 1024 pixels a side</li>
 * </ul>
 */
public final class UiAssetCheck {
    public static final String GUI_ROOT = "assets/adapt/textures/gui/";
    public static final String GUI_ID_PREFIX = "adapt:textures/gui/";
    public static final int HEADER_BYTES = 24;
    public static final int MIN_ICON = 16;
    public static final int MAX_ICON = 512;
    public static final int MAX_TEXTURE = 1024;
    public static final int MAX_SLICE_JSON = 4096;
    private static final Set<String> ICON_KINDS = Set.of("skill", "adaptation", "toast", "hud");
    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private UiAssetCheck() {
    }

    /** Pixel size from the first {@value #HEADER_BYTES} bytes of a PNG, or null when they are not a PNG header. */
    public static Size pngSize(byte[] header) {
        if (header == null || header.length < HEADER_BYTES) {
            return null;
        }
        for (int index = 0; index < SIGNATURE.length; index++) {
            if (header[index] != SIGNATURE[index]) {
                return null;
            }
        }
        if (header[12] != 'I' || header[13] != 'H' || header[14] != 'D' || header[15] != 'R') {
            return null;
        }
        int width = readInt(header, 16);
        int height = readInt(header, 20);
        return width > 0 && height > 0 ? new Size(width, height) : null;
    }

    /** Why a PNG of this size cannot be used at {@code relative} (a path under the gui root), or null when it can. */
    public static String sizeProblem(String relative, int width, int height) {
        String kind = relative.indexOf('/') < 0 ? "" : relative.substring(0, relative.indexOf('/'));
        if (ICON_KINDS.contains(kind)) {
            if (width != height) {
                return "icon must be square, is " + width + "x" + height;
            }
            if (width < MIN_ICON || width > MAX_ICON || Integer.bitCount(width) != 1) {
                return "icon size must be a power of two from " + MIN_ICON + " to " + MAX_ICON + ", is " + width;
            }
            return null;
        }
        if (width > MAX_TEXTURE || height > MAX_TEXTURE) {
            return "texture larger than " + MAX_TEXTURE + " pixels: " + width + "x" + height;
        }
        return null;
    }

    /** {@code id} as a path under the gui root, or null when it is not an Adapt gui texture id. */
    public static String relative(String id) {
        return id.startsWith(GUI_ID_PREFIX) ? id.substring(GUI_ID_PREFIX.length()) : null;
    }

    /** The slice JSON {@code id} for nine-slice texture {@code textureId}: the same name ending in {@code .json}. */
    public static String sliceId(String textureId) {
        return textureId.endsWith(".png") ? textureId.substring(0, textureId.length() - 4) + ".json" : textureId + ".json";
    }

    /** Reads a slice JSON for {@code texture}; throws {@link IllegalArgumentException} saying what is wrong. */
    public static UiNineSlice parseSlice(UiTexture texture, String json) {
        JsonObject object;
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException("slice JSON must be an object");
            }
            object = parsed.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException error) {
            throw new IllegalArgumentException("slice JSON does not parse: " + error.getMessage(), error);
        }
        int border = integer(object, "border", -1);
        int scale = integer(object, "scale", 1);
        if (border < 1) {
            throw new IllegalArgumentException("slice JSON needs a positive integer \"border\"");
        }
        if (scale < 1) {
            throw new IllegalArgumentException("\"scale\" must be a positive integer");
        }
        if (texture.width() % scale != 0 || texture.height() % scale != 0 || border % scale != 0) {
            throw new IllegalArgumentException("size " + texture.width() + "x" + texture.height() + " and border " + border
                    + " must be divisible by scale " + scale);
        }
        if (border * 2 >= Math.min(texture.width(), texture.height())) {
            throw new IllegalArgumentException("border " + border + " leaves no centre in " + texture.width() + "x" + texture.height());
        }
        String fill = object.has("fill") && object.get("fill").isJsonPrimitive() ? object.get("fill").getAsString() : "stretch";
        if (!fill.equals("stretch") && !fill.equals("tile")) {
            throw new IllegalArgumentException("\"fill\" must be \"stretch\" or \"tile\", is \"" + fill + "\"");
        }
        return new UiNineSlice(texture, border, scale, fill.equals("tile"));
    }

    /**
     * Every problem with a set of gui files, keyed by path under the gui root, each message naming its file. An empty
     * list means all of them can be used.
     */
    public static List<String> problems(Map<String, byte[]> files) {
        Map<String, byte[]> sorted = new TreeMap<>(files);
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, byte[]> entry : sorted.entrySet()) {
            String path = entry.getKey();
            byte[] content = entry.getValue();
            boolean kit = path.startsWith("kit/");
            if (path.endsWith(".png")) {
                Size size = pngSize(content);
                if (size == null) {
                    problems.add(path + ": not a PNG");
                    continue;
                }
                String problem = sizeProblem(path, size.width(), size.height());
                if (problem != null) {
                    problems.add(path + ": " + problem);
                    continue;
                }
                if (kit) {
                    byte[] json = sorted.get(sliceId(path));
                    if (json == null) {
                        problems.add(path + ": missing " + sliceId(path));
                        continue;
                    }
                    try {
                        parseSlice(new UiTexture(GUI_ID_PREFIX + path, size.width(), size.height()), new String(json, StandardCharsets.UTF_8));
                    } catch (IllegalArgumentException error) {
                        problems.add(sliceId(path) + ": " + error.getMessage());
                    }
                }
            } else if (path.endsWith(".json") && kit) {
                if (!sorted.containsKey(path.substring(0, path.length() - 5) + ".png")) {
                    problems.add(path + ": no matching PNG");
                }
            } else {
                problems.add(path + ": only PNG files, and JSON slices under kit/, belong here");
            }
        }
        return problems;
    }

    private static int integer(JsonObject object, String key, int fallback) {
        if (!object.has(key)) {
            return fallback;
        }
        JsonElement value = object.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return -1;
        }
        double number = value.getAsDouble();
        return number == Math.rint(number) && number <= Integer.MAX_VALUE ? (int) number : -1;
    }

    private static int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24) | ((bytes[offset + 1] & 0xFF) << 16) | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
    }

    public record Size(int width, int height) {
    }
}
