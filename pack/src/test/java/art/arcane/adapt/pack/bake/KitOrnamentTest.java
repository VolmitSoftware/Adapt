package art.arcane.adapt.pack.bake;

import art.arcane.adapt.engine.asset.ThemeAssets;
import art.arcane.adapt.pack.layout.PackArt;
import art.arcane.adapt.pack.manifest.PackKeys;
import art.arcane.adapt.pack.raster.Raster;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KitOrnamentTest {
    private static final PackManifest.Build UNSTAMPED = new PackManifest.Build(PackBake.VERSION, "", "");

    @Test
    void everyKitGetsAnOrnamentThatFitsAndReadsOnThePaper() {
        for (ThemeAssets kit : Builtin.withGenerated()) {
            Raster ornament = KitOrnament.of(kit);
            String name = kit.skill().toString();
            assertTrue(ornament.width() <= PackArt.ORNAMENT_WIDTH && ornament.height() <= PackArt.ORNAMENT_HEIGHT,
                    name + " ornament is " + ornament.width() + "x" + ornament.height());
            assertTrue(ornament.width() >= 3 && ornament.height() >= 3, name + " ornament is a sliver");
            int[] bounds = ornament.bounds();
            assertNotNull(bounds, name + " ornament is empty");
            assertEquals(List.of(0, 0, ornament.width(), ornament.height()), List.of(bounds[0], bounds[1], bounds[2], bounds[3]),
                    name + " ornament is not trimmed");
            int drawn = 0;
            int reading = 0;
            for (int pixel : ornament.pixels()) {
                int alpha = pixel >>> 24;
                assertTrue(alpha == 0 || alpha == 0xFF, name + " ornament has a half-transparent pixel");
                if (alpha != 0) {
                    drawn++;
                    reading += KitOrnament.standing(pixel) >= KitOrnament.READS ? 1 : 0;
                }
            }
            assertTrue(reading * 4 >= drawn * 3, name + " ornament fades into the paper");
        }
    }

    @Test
    void kitsDrawTheirOwnOrnaments() {
        List<Raster> distinct = new ArrayList<>();
        for (ThemeAssets kit : Builtin.withGenerated()) {
            Raster ornament = KitOrnament.of(kit);
            if (distinct.stream().noneMatch((Raster seen) -> seen.width() == ornament.width() && seen.height() == ornament.height()
                    && seen.same(ornament))) {
                distinct.add(ornament);
            }
        }
        assertTrue(distinct.size() >= Builtin.kits().size() / 2, "only " + distinct.size() + " different ornaments");
    }

    @Test
    void mutateFaceFramesTheButtonInTheKitsColoursAndKeepsTheLabelGround() {
        Raster button = KitBake.nine(sprite("adapt:gui/kit/" + PackArt.BUTTON_PRIMARY), 54, 18, 4);
        MutateFace herbalism = MutateFace.of(Builtin.kit("herbalism"));
        MutateFace stealth = MutateFace.of(Builtin.kit("stealth"));
        Raster face = herbalism.draw(button);
        assertEquals(54, face.width());
        assertEquals(18, face.height());
        assertEquals(herbalism.outline(), face.get(10, 0));
        assertEquals(herbalism.outline(), face.get(0, 9));
        assertEquals(herbalism.main(), face.get(10, 2));
        assertEquals(herbalism.accent(), face.get(2, 2));
        for (int y = 3; y < 15; y++) {
            for (int x = 3; x < 51; x++) {
                assertEquals(button.get(x, y), face.get(x, y), "label ground changed at " + x + "," + y);
            }
        }
        for (int[] clear : new int[][]{{0, 0}, {53, 0}, {0, 17}, {53, 17}}) {
            assertEquals(0, face.get(clear[0], clear[1]) >>> 24, "rounded corner filled");
        }
        assertFalse(face.same(stealth.draw(button)));
    }

    @Test
    void builtPackPaintsEveryKitsOrnamentAndMutateFaceOnEveryRow() {
        PackWriter writer = new PackWriter("test", UNSTAMPED);
        Raster ribbon = new Raster(KitBake.RIBBON_WIDTH, KitBake.RIBBON_HEIGHT);
        for (int index = 0; index < ribbon.pixels().length; index++) {
            ribbon.pixels()[index] = 0xFF000000;
        }
        List<String> kits = new ArrayList<>();
        for (ThemeAssets kit : Builtin.withGenerated()) {
            writer.kit(new KitBake.Result(kit, List.of(), ribbon));
            kits.add(PackKeys.kit(kit.skill()));
        }
        PackManifest manifest = writer.paint(List.of(), KitOrnamentTest::sprite, List.of()).build().manifest();
        Map<String, PackManifest.Paint> paint = new TreeMap<>();
        for (PackManifest.Paint piece : manifest.paint()) {
            paint.put(piece.key(), piece);
        }
        for (String kit : kits) {
            for (int rowY : PackArt.ROWS) {
                PackManifest.Paint ornament = paint.get(PackArt.ornament(kit, rowY));
                assertNotNull(ornament, "no ornament for " + kit + " at " + rowY);
                assertEquals(-ornament.width(), ornament.x(), "ornament not left of its anchor");
                assertEquals(rowY + ((17 - ornament.height()) / 2), ornament.y(), "ornament not centred on the box");
                PackManifest.Paint face = paint.get(PackArt.face(PackArt.mutate(kit), rowY));
                assertNotNull(face, "no Mutate face for " + kit + " at " + rowY);
                assertEquals(-1, face.x());
                assertEquals(rowY - 1, face.y());
                assertEquals(54, face.width());
            }
        }
    }

    /** A built-in catalog sprite's PNG by id, or null. */
    private static byte[] sprite(String id) {
        Id parsed = Id.parse(id);
        Path file = Builtin.root().resolve("assets/" + parsed.namespace() + "/textures/" + parsed.path() + ".png");
        try {
            return Files.exists(file) ? Files.readAllBytes(file) : null;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
