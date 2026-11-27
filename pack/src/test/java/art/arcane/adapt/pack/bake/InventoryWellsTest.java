package art.arcane.adapt.pack.bake;

import art.arcane.adapt.pack.raster.Raster;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InventoryWellsTest {
    @Test
    void hotbarWellsAreFourteenTallSoTheFootSceneShows() throws IOException {
        InventoryWells wells = InventoryWells.of(Files.readAllBytes(Builtin.root().resolve("assets/adapt/textures/gui/kit/tile.png")));
        assertEquals(18, wells.well().height());
        assertEquals(14, wells.hotbarWell().height());
        Raster page = wells.over(new Raster(200, 260), -10, -10);
        int hotbarTop = SlotGrid.y(81) - 1;
        assertEquals(196, hotbarTop);
        assertEquals(0, page.get(SlotGrid.x(81) + 4 + 10, hotbarTop + 14 + 10) >>> 24);
        assertEquals(0xFF, page.get(SlotGrid.x(81) + 4 + 10, hotbarTop + 13 + 10) >>> 24);
        assertEquals(0xFF, page.get(SlotGrid.x(54) + 4 + 10, SlotGrid.y(54) + 16 + 10) >>> 24);
    }
}
