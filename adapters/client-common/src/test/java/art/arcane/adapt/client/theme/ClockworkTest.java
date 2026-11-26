package art.arcane.adapt.client.theme;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Chronos page's clockwork: every clock keeps its own time, clocks run, and gear copies counter-rotate. */
class ClockworkTest {
    private static final int CLOCK_FRAMES = 144;

    private static List<Dressing.Piece> clocks(Dressing dressing) {
        List<Dressing.Piece> clocks = new ArrayList<>();
        for (Dressing.Piece piece : dressing.pieces()) {
            if (piece.sprite().step() > 0 && piece.sprite().loop() == CLOCK_FRAMES) {
                clocks.add(piece);
            }
        }
        return clocks;
    }

    @Test
    void everyClockOnTheChronosPageShowsADifferentTimeEvenWithMotionHeld() {
        for (int leaf = 0; leaf < 3; leaf++) {
            BookGeometry book = DressingRulesTest.book(leaf, 640, 360);
            List<Dressing.Piece> clocks = clocks(Dressing.compose(book, ThemeFixtures.theme("chronos", null)));
            assertTrue(clocks.size() >= 3, "leaf " + leaf + " has " + clocks.size() + " clocks");
            Set<String> faces = new HashSet<>();
            for (Dressing.Piece clock : clocks) {
                ThemeSprite face = clock.sprite().frame(clock.frame(0), clock.flip());
                faces.add(clock.sprite().hashCode() + ":" + face.u() + "," + face.v());
            }
            assertEquals(clocks.size(), faces.size(), "leaf " + leaf + " repeats a time");
        }
    }

    @Test
    void clocksRun() {
        BookGeometry book = DressingRulesTest.book(0, 640, 360);
        for (Dressing.Piece clock : clocks(Dressing.compose(book, ThemeFixtures.theme("chronos", null)))) {
            int at = clock.frame(0);
            assertNotEquals(at, clock.frame(1.0 / clock.sprite().fps()), "a clock that does not advance");
        }
    }

    @Test
    void turningCopiesAlternateDirectionOnScreen() {
        BookGeometry book = DressingRulesTest.book(0, 640, 360);
        int expected = 1;
        int turning = 0;
        for (Dressing.Piece piece : Dressing.compose(book, ThemeFixtures.theme("chronos", null)).pieces()) {
            if (!piece.sprite().turn()) {
                continue;
            }
            int onScreen = piece.direction() * (piece.flip() ? -1 : 1);
            assertEquals(expected, onScreen, "turning piece " + turning);
            expected = -expected;
            turning++;
        }
        assertTrue(turning >= 4, "only " + turning + " gear pieces");
    }

    @Test
    void aSelfTimedSpriteStepsAtItsRateFromItsStart() {
        BookGeometry book = DressingRulesTest.book(0, 640, 360);
        for (Dressing.Piece piece : Dressing.compose(book, ThemeFixtures.theme("chronos", null)).pieces()) {
            DressSprite sprite = piece.sprite();
            if (sprite.fps() <= 0) {
                continue;
            }
            assertEquals(sprite.frameAt(0, piece.start(), piece.direction()), piece.frame(0.999 / sprite.fps()));
            assertEquals(sprite.frameAt(0, piece.start() + piece.direction(), piece.direction()), piece.frame(1.0 / sprite.fps()));
        }
    }
}
