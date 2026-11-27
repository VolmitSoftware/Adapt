package art.arcane.adapt.pack.bake;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayerBakeTest {
    private static long[] periodic(int length, int period, int offset) {
        long[] hashes = new long[length];
        for (int i = 0; i < length; i++) {
            hashes[i] = ((i + offset) % period) / 3;
        }
        return hashes;
    }

    @Test
    void findsTheShortestExactPeriod() {
        assertEquals(67, LayerBake.period(periodic(400, 67, 5), 200));
        assertEquals(0, LayerBake.period(periodic(400, 267, 5), 200));
    }

    @Test
    void aRestingStretchDoesNotPassForTheLoop() {
        long[] looks = new long[LayerBake.SAMPLES];
        for (int i = 0; i < looks.length; i++) {
            looks[i] = i % 320 < 200 ? i % 20 : 1000 + i % 320;
        }
        assertEquals(320, LayerBake.period(looks, LayerBake.MAX_LOOP));
    }

    @Test
    void nothingRepeatingTakesTheLengthItsLookRecursAtMost() {
        long[] looks = new long[LayerBake.SAMPLES];
        long next = 1000;
        for (int i = 0; i < looks.length; i++) {
            looks[i] = i % 90 < 30 ? next++ : 0;
        }
        List<Integer> lengths = LayerBake.recurring(new long[][]{looks}, new int[][]{{0}});
        assertEquals(90, lengths.get(0));
    }

    @Test
    void theSeamFallsWhereTheLookRepeats() {
        long[] looks = new long[200];
        for (int i = 0; i < looks.length; i++) {
            looks[i] = i < 120 ? i % 10 : i;
        }
        assertEquals(0, LayerBake.seam(looks, 10));
        long[] never = new long[100];
        for (int i = 0; i < never.length; i++) {
            never[i] = i;
        }
        assertEquals(0, LayerBake.seam(never, 10));
    }

    @Test
    void aFootSceneLoopsOverItsWholeAct() {
        PageSpec spec = BookTop.fit(Builtin.kit("herbalism"), DesignSpec.builtin().page("A"));
        BakedLayer layer = LayerBake.bake(Builtin.kit("herbalism"), spec, 1);
        LayerBake.Motion foot = layer.motions().stream().filter((LayerBake.Motion motion) -> motion.y() + spec.canvasY() > 210 && motion.width() > 60)
                .findFirst().orElseThrow();
        assertEquals(320, foot.ticks());
        assertTrue(foot.frames().size() > 20, "foot scene shows " + foot.frames().size() + " looks");
    }

    @Test
    void touchingRegionsMerge() {
        int width = 20;
        boolean[] varies = new boolean[width * 10];
        varies[2 * width + 2] = true;
        varies[2 * width + 4] = true;
        varies[8 * width + 15] = true;
        List<int[]> boxes = LayerBake.regions(varies, width, 10);
        assertEquals(2, boxes.size());
        assertEquals(2, boxes.get(0)[0]);
        assertEquals(5, boxes.get(0)[2]);
    }

    @Test
    void sameKitBakesToTheSamePixels() {
        PageSpec spec = DesignSpec.builtin().page("A");
        BakedLayer first = LayerBake.bake(Builtin.kit("herbalism"), spec, 1);
        BakedLayer second = LayerBake.bake(Builtin.kit("herbalism"), spec, 1);
        assertTrue(first.still().same(second.still()));
        assertEquals(first.motions().size(), second.motions().size());
        for (int index = 0; index < first.motions().size(); index++) {
            LayerBake.Motion a = first.motions().get(index);
            LayerBake.Motion b = second.motions().get(index);
            assertEquals(a.frames().size(), b.frames().size());
            for (int frame = 0; frame < a.frames().size(); frame++) {
                assertTrue(a.frames().get(frame).same(b.frames().get(frame)));
            }
        }
    }

    @Test
    void framesFillEveryTickOfTheLoopAndPiecesKeepTheClock() {
        BakedLayer layer = LayerBake.bake(Builtin.kit("hunter"), DesignSpec.builtin().page("A"), 1);
        assertTrue(!layer.motions().isEmpty());
        for (LayerBake.Motion motion : layer.motions()) {
            int total = 0;
            for (int[] step : motion.sequence()) {
                total += step[1];
                assertTrue(step[0] < motion.frames().size());
            }
            assertEquals(motion.ticks(), total);
            for (LayerBake.Motion piece : motion.pieces(16)) {
                int pieceTotal = 0;
                for (int[] step : piece.sequence()) {
                    pieceTotal += step[1];
                }
                assertEquals(motion.ticks(), pieceTotal);
                assertTrue(piece.width() <= 16 && piece.height() <= 16);
                for (long tick = 0; tick < motion.ticks(); tick++) {
                    assertTrue(motion.frames().get(PagePreview.frameAt(motion, tick))
                            .crop(piece.x() - motion.x(), piece.y() - motion.y(), piece.width(), piece.height())
                            .same(piece.frames().get(PagePreview.frameAt(piece, tick))));
                }
            }
        }
    }

    @Test
    void stillLayerIsClearExactlyWhereSomethingMoves() {
        BakedLayer layer = LayerBake.bake(Builtin.kit("herbalism"), DesignSpec.builtin().page("A"), 1);
        for (LayerBake.Motion motion : layer.motions()) {
            for (int y = 0; y < motion.height(); y++) {
                for (int x = 0; x < motion.width(); x++) {
                    if (motion.mask()[y * motion.width() + x]) {
                        assertEquals(0, layer.still().get(motion.x() + x, motion.y() + y));
                    }
                }
            }
        }
    }
}
