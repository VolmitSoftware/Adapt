package art.arcane.adapt.client.theme;

import art.arcane.adapt.client.ui.UiTexture;
import art.arcane.adapt.platform.value.Id;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * One skill's page theme as exported from the approved demo: its palette by role, its band, cover and foot tiles, its
 * dressing sprites by slot, its page life, its hover frame sprites and its designed top and foot if it has them, all in one atlas.
 * Roles are the demo's: {@code 1 2 3} growth dark to light, {@code 4} page wash, {@code a b c} frame dark to light,
 * {@code p} accent, {@code q} pale pop, {@code w} well, {@code k} outline, and the derived band panels {@code A B S T} and
 * cover fleck {@code F}.
 */
public final class ThemeKit {
    private static final String ATLAS_FOLDER = "textures/gui/theme/";
    private static final String DATA_FOLDER = "themes/";
    private static final int ROLES = 128;
    private static final String FRAME_SPRITE = "fs/";
    private static final String FRAME_RAW = "fr/";
    private static final String CAST = "cast/";

    private final Id id;
    private final String skill;
    private final String seed;
    private final UiTexture texture;
    private final int[] palette = new int[ROLES];
    private final boolean solid;
    private final boolean footBand;
    private final boolean cloth;
    private final boolean ring;
    private final boolean solo;
    private final boolean column;
    private final boolean noflip;
    private final double density;
    private final String[] band;
    private final Map<DressSlot, DressMove> moves = new EnumMap<>(DressSlot.class);
    private final Map<DressSlot, DressSprite[]> dress = new EnumMap<>(DressSlot.class);
    private final Map<String, ThemeSprite> sprites = new HashMap<>();
    private final Map<String, ThemeSprite[]> frameSprites = new HashMap<>();
    private final Map<String, ThemeSprite> frameRaws = new HashMap<>();
    private final Map<String, ThemeSprite[]> casts = new HashMap<>();
    private final String emit;
    private final int emitBit;
    private final String flyer;
    private final char flyerRole;
    private final int flyers;
    private final String hopper;
    private final int glints;
    private final BandScene foot;
    private final BandScene top;
    private final FrameData frame;

    private ThemeKit(String namespace, JsonObject data) {
        skill = data.get("skill").getAsString();
        id = skill.indexOf(':') < 0 ? Id.of(namespace, skill) : Id.parse(skill);
        seed = data.get("seed").getAsString();
        JsonArray atlas = data.getAsJsonArray("atlas");
        texture = new UiTexture(atlasId(id.namespace(), id.path()), atlas.get(0).getAsInt(), atlas.get(1).getAsInt());
        for (Map.Entry<String, JsonElement> role : data.getAsJsonObject("palette").entrySet()) {
            palette[role.getKey().charAt(0)] = color(role.getValue().getAsString());
        }
        solid = data.get("solid").getAsBoolean();
        footBand = data.get("footBand").getAsBoolean();
        cloth = data.get("cloth").getAsBoolean();
        ring = data.get("ring").getAsBoolean();
        JsonElement alone = data.get("solo");
        solo = alone != null && alone.getAsBoolean();
        JsonElement stacked = data.get("column");
        column = stacked != null && stacked.getAsBoolean();
        noflip = data.get("noflip").getAsBoolean();
        density = data.get("dens").getAsDouble();
        JsonElement bandData = data.get("band");
        JsonArray bandRows = bandData == null || bandData.isJsonNull() ? new JsonArray() : bandData.getAsJsonArray();
        band = new String[bandRows.size()];
        for (int row = 0; row < band.length; row++) {
            band[row] = bandRows.get(row).getAsString();
        }
        for (Map.Entry<String, JsonElement> entry : data.getAsJsonObject("sprites").entrySet()) {
            JsonArray rect = entry.getValue().getAsJsonArray();
            String name = entry.getKey();
            ThemeSprite sprite = new ThemeSprite(rect.get(0).getAsInt(), rect.get(1).getAsInt(), rect.get(2).getAsInt(),
                    rect.get(3).getAsInt());
            sprites.put(name, sprite);
            if (name.startsWith(FRAME_SPRITE)) {
                int slash = name.lastIndexOf('/');
                String key = name.substring(FRAME_SPRITE.length(), slash);
                int side = name.charAt(slash + 2) - '0';
                frameSprites.computeIfAbsent(key, (String ignored) -> new ThemeSprite[8])[side + (name.charAt(slash + 1) == 'f' ? 4 : 0)] = sprite;
            } else if (name.startsWith(FRAME_RAW)) {
                frameRaws.put(name.substring(FRAME_RAW.length()), sprite);
            } else if (name.startsWith(CAST)) {
                boolean flip = name.endsWith("f");
                String body = name.substring(CAST.length(), name.length() - (flip ? 1 : 0));
                int frame = body.charAt(body.length() - 1) - '0';
                casts.computeIfAbsent(body.substring(0, body.length() - 1), (String ignored) -> new ThemeSprite[4])[frame * 2 + (flip ? 1 : 0)] = sprite;
            }
        }
        JsonObject move = data.getAsJsonObject("move");
        JsonObject slots = data.getAsJsonObject("dress");
        for (DressSlot slot : DressSlot.values()) {
            JsonElement named = move.get(slot.key());
            moves.put(slot, DressMove.of(named == null ? null : named.getAsString()));
            JsonArray list = slots.getAsJsonArray(slot.key());
            DressSprite[] parsed = new DressSprite[list.size()];
            for (int index = 0; index < parsed.length; index++) {
                parsed[index] = dressSprite(slot, index, list.get(index).getAsJsonObject());
            }
            dress.put(slot, parsed);
        }
        JsonElement emitted = data.get("emit");
        if (emitted != null && emitted.isJsonArray()) {
            emit = emitted.getAsJsonArray().get(0).getAsString();
            emitBit = emitted.getAsJsonArray().get(1).getAsInt();
        } else {
            emit = null;
            emitBit = 0;
        }
        JsonObject life = data.getAsJsonObject("life");
        flyer = string(life.get("flyer"));
        String role = string(life.get("role"));
        flyerRole = role == null || role.isEmpty() ? 0 : role.charAt(0);
        flyers = life.get("flyers").getAsInt();
        hopper = string(life.get("hopper"));
        glints = life.get("glints").getAsInt();
        JsonElement footScene = data.get("scene");
        foot = footScene == null || footScene.isJsonNull() ? null : new BandScene(footScene.getAsJsonObject(), sprites::get, "s");
        JsonElement topScene = data.get("top");
        top = topScene == null || topScene.isJsonNull() ? null : new BandScene(topScene.getAsJsonObject(), sprites::get, "t");
        frame = FrameData.parse(data.get("frame"));
    }

    /** The kit data file of skill {@code path} in {@code namespace}: {@code <namespace>:themes/<path>.json}. */
    public static String dataId(String namespace, String path) {
        return namespace + ':' + DATA_FOLDER + path + ".json";
    }

    /** The atlas of skill {@code path} in {@code namespace}: {@code <namespace>:textures/gui/theme/<path>.png}. */
    public static String atlasId(String namespace, String path) {
        return namespace + ':' + ATLAS_FOLDER + path + ".png";
    }

    /**
     * Parses a kit's data file from {@code namespace}; a {@code skill} of the form {@code namespace:path} names its own
     * namespace. Throws {@link IllegalArgumentException} when it is not a kit.
     */
    public static ThemeKit parse(String namespace, String json) {
        try {
            return new ThemeKit(namespace, JsonParser.parseString(json).getAsJsonObject());
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("not a theme kit: " + failure.getMessage(), failure);
        }
    }

    private DressSprite dressSprite(DressSlot slot, int index, JsonObject entry) {
        int width = entry.get("w").getAsInt();
        int height = entry.get("h").getAsInt();
        int count = entry.get("frames").getAsInt();
        boolean[] mask = new boolean[width * height];
        JsonArray rows = entry.getAsJsonArray("mask");
        for (int y = 0; y < height; y++) {
            String row = rows.get(y).getAsString();
            for (int x = 0; x < width; x++) {
                mask[y * width + x] = row.charAt(x) == '#';
            }
        }
        ThemeSprite[] frames = new ThemeSprite[count * 2];
        for (int frame = 0; frame < count; frame++) {
            String name = "d/" + slot.key() + index + "/" + frame;
            frames[frame * 2] = require(name);
            frames[frame * 2 + 1] = require(name + "f");
        }
        JsonElement rate = entry.get("fps");
        if (rate == null) {
            return new DressSprite(width, height, mask, frames, 0, null, false, 0);
        }
        JsonElement order = entry.get("seq");
        int[] sequence = null;
        if (order != null && order.isJsonArray()) {
            JsonArray steps = order.getAsJsonArray();
            sequence = new int[steps.size()];
            for (int at = 0; at < sequence.length; at++) {
                sequence[at] = steps.get(at).getAsInt();
            }
        }
        return new DressSprite(width, height, mask, frames, rate.getAsDouble(), sequence, entry.get("turn").getAsBoolean(),
                entry.get("step").getAsInt());
    }

    private ThemeSprite require(String name) {
        ThemeSprite sprite = sprites.get(name);
        if (sprite == null) {
            throw new IllegalArgumentException("missing sprite " + name);
        }
        return sprite;
    }

    private static String string(JsonElement element) {
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    static int color(String hex) {
        return 0xFF000000 | Integer.parseInt(hex.substring(1), 16);
    }

    /** The skill's namespaced id, such as {@code adapt:pickaxe}. */
    public Id id() {
        return id;
    }

    /** The skill as the kit names it, such as {@code pickaxe}, or {@code namespace:path} for a skill outside {@code adapt}. */
    public String skill() {
        return skill;
    }

    /** The name the demo seeded this kit's layouts with, such as {@code pickaxes}. */
    public String seed() {
        return seed;
    }

    public UiTexture texture() {
        return texture;
    }

    /** The colour of {@code role}, opaque ARGB, or 0 for a role the kit does not have. */
    public int color(char role) {
        return role < ROLES ? palette[role] : 0;
    }

    /** Whether the kit has a solid cover; a ghost kit keeps a thin outline and its dressing rings the page. */
    public boolean solid() {
        return solid;
    }

    public boolean footBand() {
        return footBand;
    }

    /** Whether the band's hem is cloth or drips, which billow. */
    public boolean cloth() {
        return cloth;
    }

    public boolean ring() {
        return ring;
    }

    /** Whether the kit's edge pieces stand one by one instead of in overlapping clusters. */
    public boolean solo() {
        return solo;
    }

    /** Whether the kit's side pieces rise from the foot as one thinning column instead of clusters. */
    public boolean column() {
        return column;
    }

    public boolean noflip() {
        return noflip;
    }

    public double density() {
        return density;
    }

    /** The band tile's rows of roles, 16 wide and 9 tall; empty for a kit with a designed top. */
    public String[] band() {
        return band;
    }

    DressMove move(DressSlot slot) {
        return moves.get(slot);
    }

    public DressSprite[] dress(DressSlot slot) {
        return dress.get(slot);
    }

    /** The sprite named {@code name} in the atlas, or null. */
    public ThemeSprite sprite(String name) {
        return sprites.get(name);
    }

    /**
     * Hover frame sprite {@code key} turned to face out from ring side {@code side} (0 top, 1 right, 2 bottom, 3 left),
     * mirrored first when {@code flip}; null when the kit's frame never draws it.
     */
    public ThemeSprite frameSprite(String key, int side, boolean flip) {
        ThemeSprite[] turns = frameSprites.get(key);
        return turns == null ? null : turns[side + (flip ? 4 : 0)];
    }

    /** Hover frame sprite {@code key} drawn as it is, at a fixed place; null when the kit's frame never draws it. */
    public ThemeSprite frameRaw(String key) {
        return frameRaws.get(key);
    }

    /** Page life sprite {@code name} (a flyer or hopper), its frame wrapped to the frames it has, mirrored when {@code flip}; null when absent. */
    public ThemeSprite castSprite(String name, int frame, boolean flip) {
        ThemeSprite[] frames = casts.get(name);
        if (frames == null) {
            return null;
        }
        int count = frames[2] != null || frames[3] != null ? 2 : 1;
        return frames[Math.floorMod(frame, count) * 2 + (flip ? 1 : 0)];
    }

    /** How the kit's particles move ({@code rise}, {@code fall}, {@code drift}, {@code pop}), or null for none. */
    public String emit() {
        return emit;
    }

    public int emitBit() {
        return emitBit;
    }

    public String flyer() {
        return flyer;
    }

    public char flyerRole() {
        return flyerRole;
    }

    public int flyers() {
        return flyers;
    }

    public String hopper() {
        return hopper;
    }

    public int glints() {
        return glints;
    }

    /** The kit's foot scene, which replaces its foot band, foot spills and hopper; null for a kit that keeps them. */
    public BandScene foot() {
        return foot;
    }

    /** The kit's designed top, which replaces its band, top dressing and header hangs; null for a kit that keeps them. */
    public BandScene top() {
        return top;
    }

    /** The kit's hover frame as data, null when it has none. */
    public FrameData frame() {
        return frame;
    }
}
