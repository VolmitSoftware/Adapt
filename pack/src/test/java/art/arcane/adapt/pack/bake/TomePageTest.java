package art.arcane.adapt.pack.bake;

import art.arcane.adapt.pack.raster.Raster;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The plain tome page has no header divider: its paper is plain from the header line down into the first card row. */
class TomePageTest {
    @Test
    void thePaperUnderTheHeaderIsPlain() {
        ArtPieces.Piece tome = ArtPieces.builtin().stream().filter((ArtPieces.Piece piece) -> piece.name().equals("page/tome")).findFirst()
                .orElseThrow();
        Raster art = Raster.decode(tome.png());
        int paper = art.get(100 - tome.offsetX(), 30 - tome.offsetY());
        for (int y = 17; y <= 34; y++) {
            for (int x = -19; x < 196; x++) {
                assertEquals(paper, art.get(x - tome.offsetX(), y - tome.offsetY()), "tome page at container (" + x + ", " + y + ")");
            }
        }
    }
}
