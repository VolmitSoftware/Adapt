package art.arcane.adapt.pack.bake;

import art.arcane.adapt.engine.asset.ThemeAssets;
import art.arcane.adapt.pack.raster.Raster;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every kit's page closes the chest window: a generated kit's book top is found by the design's rule, not left at its
 * floor, and at the top {@link BookTop} picks both of its leaves cover the window; and every baked page of every kit,
 * built in and generated (single, partner, fallback and their still copies, on the kit's own top, low or not), is opaque
 * over the whole window in its still layer and in every frame of its moving regions.
 */
class KitCoverageTest {
    @Test
    void everyGeneratedKitFindsATopWhereItsPageCoversTheWindow() {
        PageSpec framing = DesignSpec.builtin().page("A");
        List<String> problems = new ArrayList<>();
        for (ThemeAssets kit : Builtin.generated()) {
            PageSpec spec = BookTop.fit(kit, framing);
            if (spec.bookY() == BookTop.LOWEST) {
                problems.add(kit.skill() + " falls to the lowest top");
            }
            for (int leaf = 1; leaf <= 2; leaf++) {
                if (!BookTop.covers(new KitRender(kit, spec, leaf).back(), spec)) {
                    problems.add(kit.skill() + " leaf " + leaf + " leaves the window showing at top " + spec.bookY());
                }
            }
        }
        assertEquals(List.of(), problems);
    }

    @Test
    void everyBakedPageOfEveryKitCoversTheWindow() throws IOException {
        PageSpec framing = DesignSpec.builtin().page("A");
        InventoryWells wells = InventoryWells.of(Files.readAllBytes(Builtin.root().resolve("assets/adapt/textures/gui/kit/tile.png")));
        List<String> problems = new ArrayList<>();
        int pages = 0;
        for (ThemeAssets kit : Builtin.withGenerated()) {
            for (KitBake.Page page : KitBake.bake(kit, framing, wells).pages()) {
                pages++;
                int still = clear(page.still(), page.left(), page.top());
                int frames = clearFrames(page);
                if (still > 0 || frames > 0) {
                    problems.add(kit.skill() + " " + page.layout() + ": " + still + " still and " + frames + " frame pixels clear");
                }
            }
        }
        assertEquals(List.of(), problems);
        assertEquals(Builtin.withGenerated().size() * 6, pages);
    }

    @Test
    void aKitNoTopClosesTakesTheTopLeavingTheFewestPixelsClear() {
        PageSpec framing = DesignSpec.builtin().page("A");
        for (String skill : List.of("ranged", "stealth")) {
            ThemeAssets kit = Builtin.kit(skill);
            PageSpec chosen = BookTop.fit(kit, framing);
            int fewest = BookTop.clear(new KitRender(kit, chosen, 1).back(), chosen);
            for (int top = BookTop.LOWEST; top <= BookTop.HIGHEST; top++) {
                PageSpec spec = framing.withTop(top);
                int clear = BookTop.clear(new KitRender(kit, spec, 1).back(), spec);
                if (clear < fewest || clear == fewest && top > chosen.bookY()) {
                    throw new AssertionError(skill + " picks top " + chosen.bookY() + " (" + fewest + " clear) over top " + top + " (" + clear + ")");
                }
            }
        }
    }

    private static int clear(Raster raster, int left, int top) {
        int clear = 0;
        for (int y = 0; y < SlotGrid.CONTAINER_HEIGHT; y++) {
            for (int x = 0; x < SlotGrid.CONTAINER_WIDTH; x++) {
                if (raster.get(x - left, y - top) >>> 24 != 0xFF) {
                    clear++;
                }
            }
        }
        return clear;
    }

    private static int clearFrames(KitBake.Page page) {
        int clear = 0;
        for (LayerBake.Motion motion : page.motions()) {
            for (Raster frame : motion.frames()) {
                for (int y = 0; y < motion.height(); y++) {
                    for (int x = 0; x < motion.width(); x++) {
                        int cx = page.left() + motion.x() + x;
                        int cy = page.top() + motion.y() + y;
                        boolean window = cx >= 0 && cy >= 0 && cx < SlotGrid.CONTAINER_WIDTH && cy < SlotGrid.CONTAINER_HEIGHT;
                        if (window && motion.mask()[y * motion.width() + x] && frame.get(x, y) >>> 24 != 0xFF) {
                            clear++;
                        }
                    }
                }
            }
        }
        return clear;
    }
}
