package art.arcane.adapt.pack.bake;

import art.arcane.adapt.engine.asset.ThemeAssets;
import art.arcane.adapt.pack.raster.Raster;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** The palette-derived art matches the design's own derivation pixel for pixel for every built-in kit. */
class KitArtTest {
    private static Raster golden(String path) throws IOException {
        try (InputStream in = KitArtTest.class.getResourceAsStream("/kit-art/" + path)) {
            assertNotNull(in, path);
            return Raster.decode(in.readAllBytes());
        }
    }

    private static void assertSameVisible(Raster want, Raster got, String name) {
        assertEquals(want.width(), got.width(), name);
        assertEquals(want.height(), got.height(), name);
        for (int y = 0; y < want.height(); y++) {
            for (int x = 0; x < want.width(); x++) {
                int a = want.get(x, y);
                int b = got.get(x, y);
                assertEquals((a >>> 24) == 0 ? 0 : a, (b >>> 24) == 0 ? 0 : b, name + " at " + x + "," + y);
            }
        }
    }

    @Test
    void tooltipFramesAndToastCornersMatchTheDesign() throws IOException {
        Raster shape = Raster.decode(Files.readAllBytes(Builtin.root().resolve("assets/adapt/textures/gui/kit/tooltip.png")));
        assertEquals(24, Builtin.kits().size());
        for (ThemeAssets kit : Builtin.kits()) {
            String skill = kit.skill().path();
            KitArt art = KitArt.of(kit.kitJson());
            assertSameVisible(golden("tooltip/" + skill + ".png"), art.tooltip(shape), "tooltip " + skill);
            assertSameVisible(golden("toast/" + skill + ".png"), art.toast(), "toast " + skill);
        }
    }
}
