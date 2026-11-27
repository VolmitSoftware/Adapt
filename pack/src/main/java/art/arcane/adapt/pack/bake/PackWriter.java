package art.arcane.adapt.pack.bake;

import art.arcane.adapt.pack.layout.PackArt;
import art.arcane.adapt.pack.manifest.PackKeys;
import art.arcane.adapt.pack.raster.Raster;
import art.arcane.adapt.platform.value.Id;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Assembles the pack from baked kits and icons: {@code pack.mcmeta}, the space font, the client's "Inventory"
 * label blanked in every locale, the text row fonts, each
 * kit layout's glyph font and textures, the animated slot items, the icon items, the fixed art and paint the screens
 * ask for by their {@link PackArt} keys, and the manifest. Files are kept sorted
 * by path and every JSON and PNG is written the same way each time, so the same input gives the same bytes and zip.
 */
public final class PackWriter {
    public static final String NAMESPACE = "adapt";
    public static final String SPACE_FONT = NAMESPACE + ":ui/space";
    public static final String PAINT_FONT = NAMESPACE + ":ui/paint";
    public static final int FIRST_CODEPOINT = 0xE000;
    /** The resource pack formats of the clients the pack is for: 26.1.2 (84.0) through 26.3 (97.1). */
    public static final int MIN_FORMAT = 84;
    public static final int MAX_FORMAT = 97;
    private static final int SPACE_POWERS = 11;
    private static final LocalDateTime ZIP_TIME = LocalDateTime.of(1980, 1, 1, 0, 0);

    private final String description;
    private final PackManifest.Build build;
    private final PackFiles out;
    private final PageWriter pageWriter;
    private final ArtWriter artWriter;
    private final TextFonts textFonts;
    private final List<PackManifest.Icon> icons = new ArrayList<>();
    private final TreeSet<String> iconFolders = new TreeSet<>();
    private final Map<Integer, Integer> spaces = new TreeMap<>();
    private final TreeMap<String, Raster> ribbons = new TreeMap<>();
    private final List<PackManifest.KitStyle> kitStyles = new ArrayList<>();
    private Raster tooltipShape;
    private PackManifest.Hud hud;

    public PackWriter(String description, PackManifest.Build build) {
        this.description = description;
        this.build = build;
        out = new PackFiles();
        pageWriter = new PageWriter(out);
        artWriter = new ArtWriter(out, pageWriter);
        textFonts = new TextFonts(out);
    }

    /** A built pack: its files by path (manifest included) and the manifest. */
    public record Built(Map<String, byte[]> files, PackManifest manifest) {
        public Built {
            files = new TreeMap<>(files);
        }

        /** The pack as a zip: entries sorted, fixed times, no directory entries. */
        public byte[] zip() {
            try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
                    zip.setLevel(9);
                    for (Map.Entry<String, byte[]> file : files.entrySet()) {
                        ZipEntry entry = new ZipEntry(file.getKey());
                        entry.setTimeLocal(ZIP_TIME);
                        zip.putNextEntry(entry);
                        zip.write(file.getValue());
                        zip.closeEntry();
                    }
                }
                return bytes.toByteArray();
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }
    }

    /** The tome's 16x16 tooltip piece, whose shape every kit's tooltip frame takes; without it kits get no tooltip style. */
    public PackWriter tooltipShape(Raster shape) {
        tooltipShape = shape;
        return this;
    }

    /**
     * Adds one baked kit's layouts, its tooltip style from its palette, and its toast icon: the skill's icon
     * ({@link PackKeys#skillSprite}, when the icons added include it) with the kit's corner brackets over it.
     */
    public PackWriter kit(KitBake.Result result) {
        Id skill = result.kit().skill();
        String ns = skill.namespace();
        String kit = PackKeys.kit(skill);
        for (KitBake.Page page : result.pages()) {
            pageWriter.page(skill, page);
        }
        ribbons.put(kit, result.ribbon());
        KitArt palette = KitArt.of(result.kit().kitJson());
        String style = null;
        if (tooltipShape != null) {
            out.tooltipStyle(ns, skill.path(), palette.tooltip(tooltipShape));
            style = skill.toString();
        }
        String toast = null;
        String sprite = PackKeys.skillSprite(skill);
        Id icon = Id.parse(sprite);
        if (out.has("assets/" + icon.namespace() + "/textures/" + icon.path() + ".png")) {
            Id modelPath = PackKeys.toast(skill);
            String texture = "item/" + modelPath.path();
            out.put("assets/" + ns + "/textures/" + texture + ".png", palette.toast().png());
            JsonObject model = new JsonObject();
            model.addProperty("parent", "minecraft:item/generated");
            JsonObject textures = new JsonObject();
            textures.addProperty("layer0", sprite);
            textures.addProperty("layer1", ns + ":" + texture);
            model.add("textures", textures);
            out.put("assets/" + ns + "/models/item/" + modelPath.path() + ".json", PackJson.json(model));
            out.item(ns, modelPath.path(), ns + ":item/" + modelPath.path(), false);
            toast = modelPath.toString();
        }
        kitStyles.add(new PackManifest.KitStyle(kit, style, toast == null ? null : sprite, toast));
        return this;
    }

    /** Adds an item model for each icon, its greyed and shifted copies, and the stack item drawing icons in layers. */
    public PackWriter icons(List<IconFiles.Icon> list) {
        for (IconFiles.Icon icon : list) {
            String ns = icon.namespace();
            String path = icon.path();
            out.put("assets/" + ns + "/textures/" + path + ".png", icon.png());
            String model = ns + ":adapt_ui/" + path;
            JsonObject json = new JsonObject();
            json.addProperty("parent", "minecraft:item/generated");
            JsonObject textures = new JsonObject();
            textures.addProperty("layer0", icon.sprite());
            json.add("textures", textures);
            out.put("assets/" + ns + "/models/adapt_ui/" + path + ".json", PackJson.json(json));
            out.item(ns, "adapt_ui/" + path, model, false);
            int slash = path.lastIndexOf('/');
            iconFolders.add(slash < 0 ? path : path.substring(0, slash));
            icons.add(new PackManifest.Icon(icon.sprite(), ns + ":adapt_ui/" + path, "assets/" + ns + "/textures/" + path + ".png"));
            String dim = path + "_dim";
            out.put("assets/" + ns + "/textures/" + dim + ".png", ArtRules.dim(Raster.decode(icon.png())).png());
            JsonObject dimJson = new JsonObject();
            dimJson.addProperty("parent", "minecraft:item/generated");
            JsonObject dimTextures = new JsonObject();
            dimTextures.addProperty("layer0", ns + ":" + dim);
            dimJson.add("textures", dimTextures);
            out.put("assets/" + ns + "/models/adapt_ui/" + dim + ".json", PackJson.json(dimJson));
            out.item(ns, "adapt_ui/" + dim, ns + ":adapt_ui/" + dim, false);
            icons.add(new PackManifest.Icon(icon.sprite() + PackArt.DIM, ns + ":adapt_ui/" + dim, "assets/" + ns + "/textures/" + dim + ".png"));
            for (int dy : PackArt.ICON_SHIFTS) {
                shifted(ns, path, icon.sprite(), dy);
                shifted(ns, dim, icon.sprite() + PackArt.DIM, dy);
            }
        }
        artWriter.stack(list);
        return this;
    }

    /**
     * The icon texture {@code ns:path} as a model drawing it {@code dy} pixels below its slot, listed as
     * {@link PackArt#shifted} of {@code sprite}.
     */
    private void shifted(String ns, String path, String sprite, int dy) {
        String shifted = path + PackArt.shiftSuffix(dy);
        out.shiftedModel(ns, "adapt_ui/" + shifted, List.of(ns + ":" + path), dy);
        out.item(ns, "adapt_ui/" + shifted, ns + ":adapt_ui/" + shifted, true);
        icons.add(new PackManifest.Icon(PackArt.shifted(sprite, dy), ns + ":adapt_ui/" + shifted, "assets/" + ns + "/textures/" + path + ".png"));
    }

    /**
     * Adds the action bar font {@code adapt:hud} from the design's well and cooldown art, a fallback ability icon cut from
     * {@code token}, and the skill and adaptation icons among {@code list}, which must also be added with {@link #icons}.
     */
    public PackWriter hud(List<IconFiles.Icon> list, List<ArtPieces.Piece> pieces, Raster token) {
        Map<String, Raster> art = new TreeMap<>();
        for (ArtPieces.Piece piece : pieces) {
            art.put(piece.name(), Raster.decode(piece.png()));
        }
        hud = new PackManifest.Hud(HudFont.FONT, HudFont.write(out.files(), list, art, token));
        return this;
    }

    /** Adds text fonts for rows whose top is each of {@code rows} below the title text's top; rows above -1 are skipped. */
    public PackWriter textRows(List<Integer> rows) {
        textFonts.rows(rows);
        return this;
    }

    public Built build() {
        Map<String, Integer> advances = new LinkedHashMap<>();
        for (int power = 0; power < SPACE_POWERS; power++) {
            int up = FIRST_CODEPOINT + power;
            int down = FIRST_CODEPOINT + SPACE_POWERS + power;
            advances.put(Character.toString(up), 1 << power);
            advances.put(Character.toString(down), -(1 << power));
            spaces.put(up, 1 << power);
            spaces.put(down, -(1 << power));
        }
        JsonObject space = new JsonObject();
        JsonArray spaceProviders = new JsonArray();
        spaceProviders.add(PackJson.spaceProvider(advances));
        space.add("providers", spaceProviders);
        out.put("assets/" + NAMESPACE + "/font/ui/space.json", PackJson.json(space));
        JsonObject blankLabel = new JsonObject();
        blankLabel.addProperty(Languages.INVENTORY_LABEL, "");
        for (String locale : Languages.locales()) {
            out.put("assets/minecraft/lang/" + locale + ".json", PackJson.json(blankLabel));
        }

        atlas();
        out.put("pack.mcmeta", PackJson.json(mcmeta()));
        String hash = PackJson.hash(out.files());
        PackManifest manifest = new PackManifest(PackManifest.SCHEMA, SPACE_FONT, spaces, textFonts.fonts(), pageWriter.pages(),
                pageWriter.hostSlots(), icons, artWriter.art(), artWriter.paint(), kitStyles, out.styles(), hud, hash, textFonts.condensed(), build);
        out.put(PackManifest.PATH, manifest.toJson().getBytes(StandardCharsets.UTF_8));
        return new Built(out.files(), manifest);
    }

    /**
     * Adds the design's fixed art: ledger pages and the plain tome page (and its top-rows-only copy with
     * inventory wells) as title glyph fonts, the plain tome tooltip as a tooltip style, every other piece but the foot-bar
     * faces (paint only) as an item model drawn at its offset from its slot, and the items {@link PackArt#items()} derives
     * from them.
     */
    public PackWriter art(List<ArtPieces.Piece> pieces, InventoryWells wells) {
        artWriter.art(pieces, wells);
        return this;
    }

    /**
     * Adds the paint font {@link #PAINT_FONT}: each of {@code pieces} drawn as one title glyph at its container y.
     * {@code sprites} gives a catalog sprite's PNG by id (null when missing); {@code pieces} without art are left out.
     */
    public PackWriter paint(List<PackArt.Paint> pieces, Function<String, byte[]> sprites, List<ArtPieces.Piece> artPieces) {
        artWriter.paint(pieces, sprites, artPieces, ribbons);
        return this;
    }

    /** Stitches the icon folders into the atlas item models read from; page strips already live under {@code item/}. */
    private void atlas() {
        if (iconFolders.isEmpty()) {
            return;
        }
        JsonArray sources = new JsonArray();
        for (String folder : iconFolders) {
            JsonObject source = new JsonObject();
            source.addProperty("type", "minecraft:directory");
            source.addProperty("source", folder);
            source.addProperty("prefix", folder + "/");
            sources.add(source);
        }
        JsonObject atlas = new JsonObject();
        atlas.add("sources", sources);
        out.put("assets/minecraft/atlases/items.json", PackJson.json(atlas));
    }

    private JsonObject mcmeta() {
        JsonObject pack = new JsonObject();
        pack.addProperty("description", description);
        pack.addProperty("min_format", MIN_FORMAT);
        pack.addProperty("max_format", MAX_FORMAT);
        JsonObject root = new JsonObject();
        root.add("pack", pack);
        return root;
    }
}
