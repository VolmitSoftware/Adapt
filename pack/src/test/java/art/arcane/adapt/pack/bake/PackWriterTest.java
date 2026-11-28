package art.arcane.adapt.pack.bake;

import art.arcane.adapt.pack.TestCatalogs;
import art.arcane.adapt.pack.layout.PackArt;
import art.arcane.adapt.pack.raster.Raster;
import art.arcane.adapt.platform.value.Id;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackWriterTest {
    private static final PackManifest.Build UNSTAMPED = new PackManifest.Build(PackBake.VERSION, "", "");
    private static KitBake.Result herbalism;
    private static List<IconFiles.Icon> icons;
    private static Raster shape;

    @BeforeAll
    static void bake() {
        herbalism = KitBake.bake(Builtin.kit("herbalism"), DesignSpec.builtin().page("A"), null);
        List<IconFiles.Icon> all = PackBake.icons(TestCatalogs.builtin());
        icons = new ArrayList<>(all.subList(0, 20));
        all.stream().filter((IconFiles.Icon icon) -> icon.sprite().equals("adapt:gui/skill/herbalism")).forEach(icons::add);
        try {
            shape = Raster.decode(Files.readAllBytes(Builtin.root().resolve("assets/adapt/textures/gui/kit/tooltip.png")));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static PackWriter.Built build() {
        return new PackWriter("test", UNSTAMPED).icons(icons).tooltipShape(shape).kit(herbalism).art(ArtPieces.builtin(), null)
                .hud(icons, ArtPieces.builtin(), shape).textRows(List.of(-1, 0, 18, 40)).build();
    }

    @Test
    void sameInputGivesTheSameZip() {
        PackWriter.Built first = build();
        KitBake.Result again = KitBake.bake(Builtin.kit("herbalism"), DesignSpec.builtin().page("A"), null);
        PackWriter.Built second = new PackWriter("test", UNSTAMPED).icons(icons).tooltipShape(shape).kit(again).art(ArtPieces.builtin(), null)
                .hud(icons, ArtPieces.builtin(), shape).textRows(List.of(40, 18, 0, -1)).build();
        assertArrayEquals(first.zip(), second.zip());
        assertEquals(first.manifest().contentHash(), second.manifest().contentHash());
    }

    @Test
    void packPassesTheValidator() {
        assertEquals(List.of(), PackValidator.check(build().files()));
    }

    @Test
    void mcmetaStatesTheFormatRangeOfEvery26xClientAndNothingElse() {
        JsonObject root = JsonParser.parseString(new String(build().files().get("pack.mcmeta"), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(Set.of("pack"), root.keySet());
        JsonObject pack = root.getAsJsonObject("pack");
        assertEquals(Set.of("description", "min_format", "max_format"), pack.keySet());
        assertEquals("test", pack.get("description").getAsString());
        assertEquals(84, pack.get("min_format").getAsInt());
        assertEquals(97, pack.get("max_format").getAsInt());
        assertTrue(pack.get("min_format").getAsJsonPrimitive().isNumber() && pack.get("max_format").getAsJsonPrimitive().isNumber());
    }

    @Test
    void itemsAreChosenByItemDefinitionsWithoutCarrierModels() {
        PackWriter.Built built = build();
        assertTrue(built.files().containsKey("assets/minecraft/atlases/items.json"));
        assertFalse(built.files().containsKey("assets/minecraft/atlases/blocks.json"));
        assertFalse(built.files().containsKey("assets/minecraft/models/item/paper.json"));
        assertFalse(built.files().containsKey("assets/minecraft/models/item/leather_horse_armor.json"));
        List<String> missing = new ArrayList<>();
        PackManifest manifest = built.manifest();
        List<String> models = new ArrayList<>();
        manifest.icons().forEach((PackManifest.Icon icon) -> models.add(icon.model()));
        manifest.art().forEach((PackManifest.Art art) -> models.add(art.model()));
        manifest.pages().forEach((PackManifest.PageFont page) -> page.items().forEach((PackManifest.Item item) -> models.add(item.model())));
        manifest.kits().stream().filter((PackManifest.KitStyle kit) -> kit.toastModel() != null)
                .forEach((PackManifest.KitStyle kit) -> models.add(kit.toastModel()));
        for (String model : models) {
            Id id = Id.parse(model);
            if (!built.files().containsKey("assets/" + id.namespace() + "/items/" + id.path() + ".json")) {
                missing.add(model);
            }
        }
        assertTrue(models.size() > icons.size());
        assertEquals(List.of(), missing);
    }

    @Test
    void hostItemsAreComposites() {
        PackWriter.Built modern = build();
        assertEquals(List.of(22, 31, 40, 85), modern.manifest().hostSlots().get("A"));
        assertEquals(null, modern.manifest().hostSlots().get("B"));
        int composites = 0;
        for (PackManifest.PageFont page : modern.manifest().pages()) {
            for (PackManifest.Item item : page.items()) {
                assertTrue(modern.manifest().hostSlots().get(page.mode()).contains(item.slot()));
                String path = "assets/adapt/items/" + item.model().substring("adapt:".length()) + ".json";
                String definition = new String(modern.files().get(path), StandardCharsets.UTF_8);
                assertTrue(definition.contains("\"minecraft:composite\"") && definition.contains("\"oversized_in_gui\": true"), path);
                assertEquals(item.regions().size(), definition.split("\"minecraft:model\"", -1).length - 1);
                composites++;
            }
        }
        assertTrue(composites > 0);
    }

    @Test
    void textRowsAboveTheOriginAreLeftOut() {
        PackWriter.Built built = new PackWriter("test", UNSTAMPED).textRows(List.of(-5, -1, 3)).build();
        assertEquals(List.of(-1, 3), built.manifest().textFonts().stream().map(PackManifest.TextFont::dy).toList());
    }

    @Test
    void animationStripsMatchTheirTimeline() {
        PackWriter.Built built = build();
        int checked = 0;
        for (PackManifest.PageFont page : built.manifest().pages()) {
            for (PackManifest.Item item : page.items()) {
                for (PackManifest.Region region : item.regions()) {
                    Raster strip = Raster.decode(built.files().get(region.texture()));
                    assertEquals(region.width(), strip.width());
                    assertEquals(region.height() * region.frames(), strip.height());
                    assertTrue(built.files().containsKey(region.texture() + ".mcmeta"));
                    checked++;
                }
            }
        }
        assertTrue(checked > 0);
    }

    @Test
    void kitGetsATooltipStyleAndAToastIcon() {
        PackManifest modern = build().manifest();
        assertEquals("adapt:herbalism", modern.kits().get(0).tooltipStyle());
        assertEquals("adapt:adapt_ui/toast/herbalism", modern.kits().get(0).toastModel());
        assertTrue(modern.tooltipStyles().contains("adapt:tome"));
        assertTrue(build().files().containsKey("assets/adapt/textures/gui/sprites/tooltip/herbalism_frame.png"));
    }

    @Test
    void ledgerPagesBecomeGlyphFonts() {
        PackManifest manifest = build().manifest();
        assertTrue(manifest.pages().stream().anyMatch((PackManifest.PageFont page) -> page.layout().equals("ledger-r6")));
        assertTrue(manifest.art().stream().anyMatch((PackManifest.Art art) -> art.name().equals("seal/mark_0") && art.x() == -3 && art.y() == -3));
    }

    @Test
    void hudFontKeepsTheStripWritersCodepoints() {
        PackWriter.Built built = build();
        String font = new String(built.files().get("assets/adapt/font/hud.json"), StandardCharsets.UTF_8);
        for (int codepoint : new int[]{0xE000, 0xE00F, 0xE010, 0xE011, 0xE018, 0xE019, 0xE020, 0xE03E, 0xE03F}) {
            assertTrue(font.contains(Character.toString(codepoint)), Integer.toHexString(codepoint));
        }
        Map<String, Integer> icons = built.manifest().hud().icons();
        assertEquals(0xE100, icons.values().stream().mapToInt(Integer::intValue).min().orElseThrow());
        assertEquals(0xE100 + icons.size() - 1, icons.get("adapt:gui/skill/herbalism"));
        assertTrue(built.files().containsKey("assets/adapt/textures/font/hud/wipe_8.png"));
    }

    @Test
    void everyHudGlyphAdvancesByItsWholeCell() {
        PackWriter.Built built = build();
        JsonObject font = JsonParser.parseString(
                new String(built.files().get("assets/adapt/font/hud.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        int glyphs = 0;
        for (JsonElement element : font.getAsJsonArray("providers")) {
            JsonObject provider = element.getAsJsonObject();
            if (!provider.get("type").getAsString().equals("bitmap")) {
                continue;
            }
            String file = provider.get("file").getAsString();
            assertTrue(file.startsWith("adapt:font/hud/"), file);
            Raster glyph = Raster.decode(built.files().get("assets/adapt/textures/" + file.substring("adapt:".length())));
            assertEquals(glyph.width() + 1, StillTiles.advance(glyph), file);
            glyphs++;
        }
        assertEquals(1 + 8 + 1 + HudFont.FILLS + 1 + built.manifest().hud().icons().size(), glyphs);
    }

    @Test
    void pinningLeavesVisibleArtAndAddsOneInvisiblePixel() {
        Raster empty = new Raster(60, 4);
        Raster pinned = HudFont.pinned(empty);
        assertEquals(HudFont.PIN, pinned.get(59, 0));
        assertEquals(0, empty.get(59, 0));
        Raster full = new Raster(4, 4);
        full.set(3, 2, 0xFF00FF00);
        assertTrue(HudFont.pinned(full) == full);
    }

    @Test
    void manifestRoundTrips() {
        PackManifest manifest = build().manifest();
        PackManifest read = PackManifest.fromJson(manifest.toJson());
        assertEquals(manifest.toJson(), read.toJson());
        assertEquals(manifest.spaces(), read.spaces());
        assertEquals(manifest.pages().get(0).glyphs(), read.pages().get(0).glyphs());
    }

    @Test
    void spaceFontCoversEveryShiftInPowersOfTwo() {
        PackManifest manifest = build().manifest();
        Map<Integer, Integer> spaces = new TreeMap<>(manifest.spaces());
        assertEquals(22, spaces.size());
        assertTrue(spaces.containsValue(1024) && spaces.containsValue(-1024) && spaces.containsValue(1) && spaces.containsValue(-1));
        int[] glyphCodepoints = manifest.pages().get(0).glyphs().stream().mapToInt(PackManifest.Glyph::codepoint).toArray();
        assertEquals(glyphCodepoints.length, Arrays.stream(glyphCodepoints).distinct().count());
    }

    @Test
    void stackItemSelectsEveryIconPerLayer() {
        PackWriter.Built modern = build();
        JsonObject composite = JsonParser.parseString(new String(modern.files().get("assets/adapt/items/adapt_ui/stack.json"), StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("model");
        assertEquals("minecraft:composite", composite.get("type").getAsString());
        assertEquals(ArtWriter.STACK_LAYERS, composite.getAsJsonArray("models").size());
        for (int index = 0; index < ArtWriter.STACK_LAYERS; index++) {
            JsonObject layer = composite.getAsJsonArray("models").get(index).getAsJsonObject();
            assertEquals(index, layer.get("index").getAsInt());
            assertEquals(icons.size(), layer.getAsJsonArray("cases").size());
            JsonObject first = layer.getAsJsonArray("cases").get(0).getAsJsonObject();
            assertEquals(icons.get(0).sprite(), first.get("when").getAsString());
            assertEquals(index, first.getAsJsonObject("model").getAsJsonArray("tints").get(0).getAsJsonObject().get("index").getAsInt());
        }
        assertTrue(modern.manifest().art().stream().anyMatch((PackManifest.Art art) -> art.name().equals("stack")));
    }

    @Test
    void packBlanksTheInventoryLabelInEveryLocaleTheClientsShip() {
        PackWriter.Built built = build();
        List<String> locales = Languages.locales();
        assertTrue(locales.contains("en_us"));
        assertTrue(locales.size() > 100);
        for (String locale : locales) {
            byte[] json = built.files().get("assets/minecraft/lang/" + locale + ".json");
            JsonObject lang = JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(1, lang.size(), locale);
            assertEquals("", lang.get(Languages.INVENTORY_LABEL).getAsString(), locale);
        }
        assertFalse(built.files().keySet().stream().anyMatch((String path) -> path.contains("/cover")));
    }

    @Test
    void theHoverHighlightIsOneRowOfPaperBelowTheSlotAndNothingOverTheItem() {
        PackWriter.Built modern = build();
        Raster back = Raster.decode(modern.files().get(PackWriter.HIGHLIGHT_BACK));
        Raster front = Raster.decode(modern.files().get(PackWriter.HIGHLIGHT_FRONT));
        for (int y = 0; y < PackWriter.HIGHLIGHT_SIZE; y++) {
            for (int x = 0; x < PackWriter.HIGHLIGHT_SIZE; x++) {
                assertEquals(0, front.get(x, y) >>> 24, "front " + x + "," + y);
                boolean paper = y == 20 && x >= 3 && x <= 20;
                assertEquals(paper ? PackArt.PAPER : 0, back.get(x, y), "back " + x + "," + y);
            }
        }
        assertFalse(modern.files().keySet().stream().anyMatch((String path) -> path.contains("_d6") || path.contains("_u7")));
    }
}
