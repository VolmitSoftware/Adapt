package art.arcane.adapt.client.theme;

import art.arcane.adapt.client.ui.RecordingDraw;
import art.arcane.adapt.client.ui.kit.UiRect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which parent owns which part of a mutated book, and how hover frames come and go. */
class PageThemeTest {
    @Test
    void aMutatedSkillIdNamesItsLeadAndPartner() {
        PageTheme theme = ThemeFixtures.LIBRARY.theme(ThemeFixtures.SPRITES, "mutated-herbalism-rift");
        assertSame(ThemeFixtures.kit("herbalism"), theme.lead());
        assertSame(ThemeFixtures.kit("rift"), theme.partner());
        assertNull(ThemeFixtures.LIBRARY.theme(ThemeFixtures.SPRITES, "mutated-herbalism-nothing"));
        assertNull(ThemeFixtures.LIBRARY.theme(ThemeFixtures.SPRITES, "not-a-skill"));
        assertFalse(ThemeFixtures.LIBRARY.theme(ThemeFixtures.SPRITES, "pickaxe").mutated());
    }

    @Test
    void theLeadOwnsTheLeftPageAndThePartnerTheRight() {
        PageTheme theme = ThemeFixtures.theme("herbalism", "rift");
        BookGeometry spread = DressingRulesTest.book(0, 640, 360);
        assertSame(theme.lead(), theme.owner(spread.fold() - 1, spread.fold()));
        assertSame(theme.partner(), theme.owner(spread.fold(), spread.fold()));
        PageLife life = PageLife.of(spread, theme, Dressing.compose(spread, theme));
        for (PageLife.FootTile tile : life.foot()) {
            boolean back = tile.y() == spread.paper().get(0).y1() - 12 || tile.y() == spread.paper().get(0).y1() - 13;
            int cut = spread.fold() + (back ? 8 : -16);
            assertSame(tile.x() < cut ? theme.lead() : theme.partner(), tile.kit(), "foot tile at " + tile.x());
        }
    }

    @Test
    void aSinglePageShowsTheLeadOnPageOneAndThePartnerOnPageTwo() {
        PageTheme theme = ThemeFixtures.theme("herbalism", "rift");
        BookGeometry first = DressingRulesTest.book(1, 640, 360);
        BookGeometry second = DressingRulesTest.book(2, 640, 360);
        assertEquals(first.book().x1(), first.fold());
        assertEquals(second.book().x0(), second.fold());
        ThemedBook one = ThemedBook.of(first, theme);
        ThemedBook two = ThemedBook.of(second, theme);
        assertSame(theme.lead(), one.owner(first.pages().get(0).x() + 20));
        assertSame(theme.partner(), two.owner(second.pages().get(0).x() + 20));
        for (PageLife.FootTile tile : one.life().foot()) {
            assertSame(theme.lead(), tile.kit());
        }
        for (PageLife.FootTile tile : two.life().foot()) {
            assertSame(theme.partner(), tile.kit());
        }
    }

    @Test
    void theSpineHeadAndFootAreDressedByBothParents() {
        PageTheme theme = ThemeFixtures.theme("nether", "seaborne");
        BookGeometry spread = DressingRulesTest.book(0, 640, 360);
        boolean lead = false;
        boolean partner = false;
        int hooks = 0;
        for (Dressing.Piece piece : Dressing.compose(spread, theme).pieces()) {
            if (piece.spine()) {
                lead |= piece.kit() == theme.lead();
                partner |= piece.kit() == theme.partner();
                hooks += piece.slot() == DressSlot.HOOK ? 1 : 0;
            }
        }
        assertTrue(lead && partner);
        assertTrue(hooks > 4, "the gutter is braided");
        assertEquals(7, ThemedBook.of(spread, theme).life().wisps().size());
        assertEquals(0, ThemedBook.of(spread, ThemeFixtures.theme("nether", null)).life().wisps().size());
    }

    @Test
    void aFrameAssemblesIdlesAndLeavesOnTime() {
        TileFrame frame = new TileFrame();
        PageTheme theme = ThemeFixtures.theme("herbalism", null);
        UiRect tile = new UiRect(40, 40, 106, 23);
        RecordingDraw draw = new RecordingDraw();
        frame.paint(draw, theme, tile, true, 0L, 0.0, false);
        for (long now = 50; now <= TileFrame.ASSEMBLE_MILLIS; now += 50) {
            frame.paint(draw, theme, tile, true, now, now / 1000.0, false);
        }
        assertEquals(1.0, frame.lift(), 1e-9);
        draw.calls().clear();
        frame.paint(draw, theme, tile, true, TileFrame.ASSEMBLE_MILLIS + 50, 2.0, false);
        assertFalse(draw.calls().isEmpty(), "a built frame idles");
        long start = TileFrame.ASSEMBLE_MILLIS + 50;
        for (long now = start + 50; now <= start + TileFrame.LEAVE_MILLIS - 50; now += 50) {
            frame.paint(draw, theme, tile, false, now, now / 1000.0, false);
            assertTrue(frame.active(), "still leaving at " + (now - start));
        }
        frame.paint(draw, theme, tile, false, start + TileFrame.LEAVE_MILLIS, 3.0, false);
        assertFalse(frame.active());
    }

    @Test
    void stillMotionShowsTheBuiltFrameAtOnceAndHoldsIt() {
        TileFrame frame = new TileFrame();
        PageTheme theme = ThemeFixtures.theme("seaborne", "chronos");
        UiRect tile = new UiRect(40, 40, 106, 23);
        RecordingDraw first = new RecordingDraw();
        RecordingDraw later = new RecordingDraw();
        frame.paint(first, theme, tile, true, 0L, 0.0, true);
        assertEquals(1.0, frame.lift());
        frame.paint(later, theme, tile, true, 5000L, 0.0, true);
        assertEquals(first.calls(), later.calls());
        frame.paint(later, theme, tile, false, 5050L, 0.0, true);
        assertFalse(frame.active());
    }

    @Test
    void aMutatedTileKeepsEachParentToItsHalf() {
        PageTheme theme = ThemeFixtures.theme("nether", "seaborne");
        RecordingDraw draw = new RecordingDraw();
        TileFrame.draw(draw, theme, 40, 40, 106, 23, 1.0, 2.0, false);
        int mid = 40 + 53;
        for (String call : draw.calls()) {
            if (!call.startsWith("fill ")) {
                continue;
            }
            String[] box = call.substring(5, call.indexOf(' ', 5)).split(",");
            int x = Integer.parseInt(box[0]);
            assertTrue(x < mid - 2 || x >= mid + 2, "a pixel of the frame sits in the seam: " + call);
        }
    }
}
