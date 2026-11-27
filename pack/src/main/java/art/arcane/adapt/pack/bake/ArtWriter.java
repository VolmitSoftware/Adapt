package art.arcane.adapt.pack.bake;

import art.arcane.adapt.pack.layout.PackArt;
import art.arcane.adapt.pack.raster.Raster;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Writes the design's fixed art and the paint the screens ask for by {@link PackArt} key: art pieces as item models drawn
 * at their offset from the slot, the items derived from them (blank, armed wash, seal marks, pips), the fixed pages as
 * glyph fonts through the page writer, and the paint font with every kit's crumb ribbon parts.
 */
final class ArtWriter {
    private static final String ART_TEXTURE = "item/adapt_ui/art/";
    private static final String ART_MODEL = "adapt_ui/art/";
    /** The wine wash over an armed button, as the design's mock draws it. */
    private static final int ARMED_WASH = 0x4DB2523F;
    private static final int ARMED_TICKS = 6;
    private static final int SEAL_GLINT_TICKS = 4;
    private static final int SEAL_REST_TICKS = 64;
    private static final int SEAL_ARMED_TICKS = 3;
    private static final double SEAL_FILL_ALPHA = 0.8;
    private static final String TOME_RIBBON = "adapt:gui/kit/ribbon";
    private static final int RIBBON_BORDER = 6;
    static final int STACK_LAYERS = 3;

    private final PackFiles out;
    private final PageWriter pages;
    private final List<PackManifest.Art> art = new ArrayList<>();
    private final List<PackManifest.Paint> paint = new ArrayList<>();

    ArtWriter(PackFiles out, PageWriter pages) {
        this.out = out;
        this.pages = pages;
    }

    List<PackManifest.Art> art() {
        return art;
    }

    List<PackManifest.Paint> paint() {
        return paint;
    }

    /** See {@link PackWriter#art}. */
    void art(List<ArtPieces.Piece> pieces, InventoryWells wells) {
        for (ArtPieces.Piece piece : pieces) {
            Raster raster = Raster.decode(piece.png());
            String name = piece.name();
            if (name.startsWith("ledger/page_r")) {
                pages.glyphs(PackArt.TOME, PackArt.LEDGER + name.substring("ledger/page_r".length()), "ui/" + name, raster, piece.offsetX(),
                        piece.offsetY());
            } else if (name.equals("page/tome")) {
                pages.glyphs(PackArt.TOME, PackArt.SINGLE, "ui/page/tome", raster, piece.offsetX(), piece.offsetY());
                if (wells != null) {
                    pages.glyphs(PackArt.TOME, PackArt.FALLBACK, "ui/page/tome_fallback", wells.over(raster, piece.offsetX(), piece.offsetY()),
                            piece.offsetX(), piece.offsetY());
                }
            } else if (name.startsWith("tooltip/")) {
                out.tooltipStyle(PackWriter.NAMESPACE, name.substring("tooltip/".length()), raster);
            } else if (!name.startsWith(PackArt.FOOT_FACE_ART)) {
                boolean fill = name.startsWith("seal/fill_");
                String texture = texture(name, fill ? ArtRules.faded(raster, SEAL_FILL_ALPHA) : raster);
                artItem(name, piece.role(), List.of(new Layer(texture, piece.offsetX(), piece.offsetY(), piece.width(), piece.height(), fill)));
                if (PackArt.SHIFTED_ITEMS.contains(name)) {
                    for (int dy : PackArt.ICON_SHIFTS) {
                        shiftedItem(name, texture, piece.role(), dy);
                    }
                }
            }
        }
        derived();
    }

    /**
     * {@link PackArt#STACK}: up to {@link #STACK_LAYERS} icons drawn over each other, layer {@code i}
     * picked by custom model data string {@code i} (an icon's sprite id) and tinted by custom model data colour {@code i},
     * from the icons' own generated models; and one per {@link PackArt#ICON_SHIFTS} from the shifted models.
     */
    void stack(List<IconFiles.Icon> icons) {
        if (icons.isEmpty()) {
            return;
        }
        stack(icons, 0);
        for (int dy : PackArt.ICON_SHIFTS) {
            stack(icons, dy);
        }
    }

    private void stack(List<IconFiles.Icon> icons, int dy) {
        JsonArray layers = new JsonArray();
        for (int index = 0; index < STACK_LAYERS; index++) {
            JsonArray cases = new JsonArray();
            for (IconFiles.Icon icon : icons) {
                JsonObject tint = new JsonObject();
                tint.addProperty("type", "minecraft:custom_model_data");
                tint.addProperty("index", index);
                tint.addProperty("default", -1);
                JsonArray tints = new JsonArray();
                tints.add(tint);
                JsonObject model = new JsonObject();
                model.addProperty("type", "minecraft:model");
                model.addProperty("model", icon.namespace() + ":adapt_ui/" + icon.path() + PackArt.shiftSuffix(dy));
                model.add("tints", tints);
                JsonObject when = new JsonObject();
                when.addProperty("when", icon.sprite());
                when.add("model", model);
                cases.add(when);
            }
            JsonObject empty = new JsonObject();
            empty.addProperty("type", "minecraft:empty");
            JsonObject layer = new JsonObject();
            layer.addProperty("type", "minecraft:select");
            layer.addProperty("property", "minecraft:custom_model_data");
            layer.addProperty("index", index);
            layer.add("cases", cases);
            layer.add("fallback", empty);
            layers.add(layer);
        }
        JsonObject composite = new JsonObject();
        composite.addProperty("type", "minecraft:composite");
        composite.add("models", layers);
        JsonObject definition = new JsonObject();
        definition.add("model", composite);
        if (dy != 0) {
            definition.addProperty("oversized_in_gui", true);
        }
        String path = "adapt_ui/" + PackArt.STACK + PackArt.shiftSuffix(dy);
        out.put("assets/" + PackWriter.NAMESPACE + "/items/" + path + ".json", PackJson.json(definition));
        art.add(new PackManifest.Art(PackArt.shifted(PackArt.STACK, dy), "icons stacked by custom model data strings and tinted by its colours",
                PackWriter.NAMESPACE + ":" + path, 0, dy, 16, 16));
    }

    /** The items the screens ask for that are not one design piece each: see {@link PackArt#items()}. */
    private void derived() {
        blank();
        Raster wash = new Raster(18, 18);
        for (int y = 0; y < 18; y++) {
            for (int x = 0; x < 18; x++) {
                wash.set(x, y, ARMED_WASH);
            }
        }
        String armed = animated(PackArt.ARMED, List.of(wash, new Raster(18, 18)), new int[][]{{0, ARMED_TICKS}, {1, ARMED_TICKS}});
        artItem(PackArt.ARMED, "armed button wash", List.of(new Layer(armed, -1, -1, 18, 18, false)));
        List<Raster> marks = new ArrayList<>();
        for (int frame = 0; frame <= 4; frame++) {
            marks.add(Raster.decode(out.get("assets/" + PackWriter.NAMESPACE + "/textures/" + ART_TEXTURE + "seal/mark_" + frame + ".png")));
        }
        String idle = animated(PackArt.SEAL_IDLE, marks, new int[][]{{1, SEAL_GLINT_TICKS}, {2, SEAL_GLINT_TICKS}, {3, SEAL_GLINT_TICKS},
                {4, SEAL_GLINT_TICKS}, {0, SEAL_REST_TICKS}});
        artItem(PackArt.SEAL_IDLE, "seal mark resting", List.of(new Layer(idle, -3, -3, 22, 22, false)));
        String glint = animated(PackArt.SEAL_ARMED, marks.subList(1, 5), new int[][]{{0, SEAL_ARMED_TICKS}, {1, SEAL_ARMED_TICKS},
                {2, SEAL_ARMED_TICKS}, {3, SEAL_ARMED_TICKS}});
        artItem(PackArt.SEAL_ARMED, "seal mark armed", List.of(new Layer(glint, -3, -3, 22, 22, false)));
        String fill = PackWriter.NAMESPACE + ":" + ART_TEXTURE + "pip/fill";
        String ring = PackWriter.NAMESPACE + ":" + ART_TEXTURE + "pip/ring";
        for (String state : PackArt.PIP_STATES) {
            for (int level = 1; level <= PackArt.PIP_LEVELS; level++) {
                String key = PackArt.pip(state, level);
                List<Layer> layers = new ArrayList<>();
                if (state.equals(PackArt.PIP_LEARNED)) {
                    layers.add(new Layer(fill, 0, 0, 16, 16, true));
                }
                layers.add(new Layer(PackWriter.NAMESPACE + ":" + ART_TEXTURE + key, 0, 0, 16, 16, false));
                if (layers.size() > 1) {
                    artItem(key, "level pip " + state + " " + level + " over its tinted fill", layers);
                }
                layers.add(new Layer(ring, -1, -1, 18, 18, false));
                artItem(PackArt.pipRing(state, level), "level pip " + state + " " + level + " with the previewed-level ring", layers);
            }
        }
    }

    /** {@link PackArt#BLANK}: an item that draws nothing. */
    private void blank() {
        String path = ART_MODEL + PackArt.BLANK;
        JsonObject empty = new JsonObject();
        empty.addProperty("type", "minecraft:empty");
        JsonObject definition = new JsonObject();
        definition.add("model", empty);
        out.put("assets/" + PackWriter.NAMESPACE + "/items/" + path + ".json", PackJson.json(definition));
        art.add(new PackManifest.Art(PackArt.BLANK, "draws nothing", PackWriter.NAMESPACE + ":" + path, 0, 0, 16, 16));
    }

    /** One texture an art item shows: its box relative to the slot's corner, tinted by custom model data colour 0 or not. */
    private record Layer(String texture, int x, int y, int width, int height, boolean tinted) {
    }

    /** Writes art texture {@code name} and returns its id. */
    private String texture(String name, Raster raster) {
        out.put("assets/" + PackWriter.NAMESPACE + "/textures/" + ART_TEXTURE + name + ".png", raster.png());
        return PackWriter.NAMESPACE + ":" + ART_TEXTURE + name;
    }

    /** Writes the animated art texture {@code name}: {@code frames} played as {@code sequence} (frame index, ticks), and returns its id. */
    private String animated(String name, List<Raster> frames, int[][] sequence) {
        Raster first = frames.get(0);
        Raster strip = new Raster(first.width(), first.height() * frames.size());
        for (int index = 0; index < frames.size(); index++) {
            strip.paste(frames.get(index), 0, index * first.height());
        }
        String path = "assets/" + PackWriter.NAMESPACE + "/textures/" + ART_TEXTURE + name + ".png";
        out.put(path, strip.png());
        JsonObject animation = new JsonObject();
        animation.addProperty("width", first.width());
        animation.addProperty("height", first.height());
        animation.addProperty("frametime", 1);
        JsonArray steps = new JsonArray();
        for (int[] step : sequence) {
            JsonObject frame = new JsonObject();
            frame.addProperty("index", step[0]);
            frame.addProperty("time", step[1]);
            steps.add(frame);
        }
        animation.add("frames", steps);
        JsonObject meta = new JsonObject();
        meta.add("animation", animation);
        out.put(path + ".mcmeta", PackJson.json(meta));
        return PackWriter.NAMESPACE + ":" + ART_TEXTURE + name;
    }

    /**
     * The item {@code name} drawing {@code layers} in order: a generated model for one plain 16x16 layer on its slot,
     * otherwise flat elements under one GUI transform, oversized when they reach past the slot.
     */
    /** The 16x16 art item {@code name} drawn {@code dy} pixels below its slot, listed as {@link PackArt#shifted}. */
    private void shiftedItem(String name, String texture, String role, int dy) {
        String modelPath = ART_MODEL + name + PackArt.shiftSuffix(dy);
        out.shiftedModel(PackWriter.NAMESPACE, "item/" + modelPath, List.of(texture), dy);
        String modelId = PackWriter.NAMESPACE + ":item/" + modelPath;
        out.item(PackWriter.NAMESPACE, modelPath, modelId, true);
        art.add(new PackManifest.Art(PackArt.shifted(name, dy), role, PackWriter.NAMESPACE + ":" + modelPath, 0, dy, 16, 16));
    }

    private void artItem(String name, String role, List<Layer> layers) {
        int x0 = Integer.MAX_VALUE;
        int y0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE;
        int y1 = Integer.MIN_VALUE;
        boolean tinted = false;
        for (Layer layer : layers) {
            x0 = Math.min(x0, layer.x());
            y0 = Math.min(y0, layer.y());
            x1 = Math.max(x1, layer.x() + layer.width());
            y1 = Math.max(y1, layer.y() + layer.height());
            tinted |= layer.tinted();
        }
        boolean onSlot = x0 == 0 && y0 == 0 && x1 == 16 && y1 == 16;
        JsonObject model;
        if (onSlot && layers.size() == 1 && !tinted) {
            model = new JsonObject();
            model.addProperty("parent", "minecraft:item/generated");
            JsonObject textures = new JsonObject();
            textures.addProperty("layer0", layers.get(0).texture());
            model.add("textures", textures);
        } else {
            MotionItems.Area box = new MotionItems.Area(0, SlotGrid.x(0) + x0, SlotGrid.y(0) + y0, x1 - x0, y1 - y0);
            MotionItems.Transform transform = MotionItems.transform(0, List.of(box));
            JsonObject textures = new JsonObject();
            JsonArray elements = new JsonArray();
            for (int index = 0; index < layers.size(); index++) {
                Layer layer = layers.get(index);
                String key = "l" + index;
                textures.addProperty(key, layer.texture());
                if (!textures.has("particle")) {
                    textures.addProperty("particle", layer.texture());
                }
                MotionItems.Area area = new MotionItems.Area(index, SlotGrid.x(0) + layer.x(), SlotGrid.y(0) + layer.y(), layer.width(), layer.height());
                elements.add(PackJson.element(key, area, SlotGrid.x(0), SlotGrid.y(0), transform, 8 + (index * 0.01), layer.tinted()));
            }
            model = PackJson.flatModel(textures, elements, transform);
        }
        String modelPath = ART_MODEL + name;
        out.put("assets/" + PackWriter.NAMESPACE + "/models/item/" + modelPath + ".json", PackJson.json(model));
        String modelId = PackWriter.NAMESPACE + ":item/" + modelPath;
        out.item(PackWriter.NAMESPACE, modelPath, modelId, !onSlot, tinted);
        art.add(new PackManifest.Art(name, role, PackWriter.NAMESPACE + ":" + modelPath, x0, y0, x1 - x0, y1 - y0));
    }

    /** See {@link PackWriter#paint}; {@code ribbons} are the kits' crumb ribbons, to which the tome's is added. */
    void paint(List<PackArt.Paint> pieces, Function<String, byte[]> sprites, List<ArtPieces.Piece> artPieces, TreeMap<String, Raster> ribbons) {
        Map<String, Raster> named = new TreeMap<>();
        for (ArtPieces.Piece piece : artPieces) {
            named.put(piece.name(), Raster.decode(piece.png()));
        }
        JsonArray providers = new JsonArray();
        int codepoint = PackWriter.FIRST_CODEPOINT;
        for (PackArt.Paint piece : pieces) {
            codepoint = paintGlyph(piece, ArtRules.paint(piece, sprites, named), codepoint, providers);
        }
        byte[] tome = sprites.apply(TOME_RIBBON);
        if (tome != null) {
            ribbons.put(PackArt.TOME, KitBake.nine(tome, KitBake.RIBBON_WIDTH, KitBake.RIBBON_HEIGHT, RIBBON_BORDER));
        }
        for (Map.Entry<String, Raster> ribbon : ribbons.entrySet()) {
            for (PackArt.Paint part : ribbonParts(ribbon.getKey())) {
                codepoint = paintGlyph(part, ArtRules.ribbon(part, ribbon.getValue()), codepoint, providers);
            }
        }
        JsonObject font = new JsonObject();
        font.add("providers", providers);
        out.put("assets/" + PackWriter.NAMESPACE + "/font/ui/paint.json", PackJson.json(font));
    }

    /** Writes {@code raster} as the glyph of paint {@code piece} at {@code codepoint}; returns the next free codepoint. */
    private int paintGlyph(PackArt.Paint piece, Raster raster, int codepoint, JsonArray providers) {
        if (raster == null) {
            return codepoint;
        }
        List<StillTiles.Tile> tiles = StillTiles.slice(raster, piece.dx(), piece.y());
        if (tiles.size() != 1) {
            throw new IllegalStateException("paint " + piece.key() + " is " + tiles.size() + " glyphs");
        }
        StillTiles.Tile tile = tiles.get(0);
        out.glyph(PackWriter.NAMESPACE, "font/ui/paint/p" + (codepoint - PackWriter.FIRST_CODEPOINT) + ".png", codepoint, tile, providers);
        paint.add(new PackManifest.Paint(piece.key(), PackWriter.PAINT_FONT, codepoint, tile.x(), tile.y(), tile.width(), tile.height(),
                tile.advance()));
        return codepoint + 1;
    }

    /** The parts of {@code kit}'s crumb ribbon: its head, its middle in runs of every segment width, and its tail. */
    static List<PackArt.Paint> ribbonParts(String kit) {
        List<PackArt.Paint> parts = new ArrayList<>();
        parts.add(new PackArt.Paint(PackArt.ribbonHead(kit), PackArt.Kind.ART, "head", PackArt.RIBBON_HEAD, KitBake.RIBBON_HEIGHT, PackArt.RIBBON_Y, 0));
        for (int power = 0; power < PackArt.SEGMENT_POWERS; power++) {
            parts.add(new PackArt.Paint(PackArt.ribbonBody(kit, 1 << power), PackArt.Kind.ART, "body", 1 << power, KitBake.RIBBON_HEIGHT,
                    PackArt.RIBBON_Y, 0));
        }
        parts.add(new PackArt.Paint(PackArt.ribbonTail(kit), PackArt.Kind.ART, "tail", PackArt.RIBBON_TAIL, KitBake.RIBBON_HEIGHT, PackArt.RIBBON_Y, 0));
        return parts;
    }
}
