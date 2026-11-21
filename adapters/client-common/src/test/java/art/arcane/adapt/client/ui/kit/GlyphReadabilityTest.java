package art.arcane.adapt.client.ui.kit;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Arrow and stepper glyphs read as ink on their tabs, tabs stand out from the page, and checkboxes differ on and off. */
class GlyphReadabilityTest {
    private static final UiTheme THEME = UiTheme.DEFAULT;
    private static final UiRect BOX = new UiRect(0, 0, 16, 16);

    private static int color(String fill) {
        return (int) Long.parseLong(fill.substring(fill.lastIndexOf(' ') + 1), 16);
    }

    private static List<String> tab(UiTome.Tab look, UiTome.Glyph glyph) {
        KitDraw draw = new KitDraw();
        UiTome.tab(draw, BOX, look, THEME);
        UiTome.glyph(draw, glyph, BOX, THEME.text());
        return draw.calls("fill ");
    }

    @Test
    void everyGlyphKeepsTextContrastOnItsTab() {
        for (UiTome.Glyph glyph : UiTome.Glyph.values()) {
            for (UiTome.Tab look : List.of(UiTome.Tab.REST, UiTome.Tab.HOVER, UiTome.Tab.PRESSED)) {
                List<String> fills = tab(look, glyph);
                int paper = color(fills.get(0));
                int ink = color(fills.get(fills.size() - 1));
                double ratio = UiContrast.ratio(ink, paper);
                assertTrue(ratio >= UiContrast.TEXT, glyph + " " + look + ": " + ratio);
            }
        }
    }

    @Test
    void tabsStandOutFromThePageAndChangeUnderThePointer() {
        List<String> rest = tab(UiTome.Tab.REST, UiTome.Glyph.BACK);
        List<String> hover = tab(UiTome.Tab.HOVER, UiTome.Glyph.BACK);
        int restEdge = color(rest.get(3));
        int hoverEdge = color(hover.get(3));
        assertTrue(UiContrast.ratio(restEdge, THEME.backdrop()) >= UiContrast.GRAPHIC, "rest edge on the page");
        assertTrue(UiContrast.ratio(hoverEdge, THEME.backdrop()) >= UiContrast.TEXT, "hover edge on the page");
        assertNotEquals(color(rest.get(0)), color(hover.get(0)), "hover changes the paper");
        assertNotEquals(restEdge, hoverEdge, "hover inks the edge");
    }

    @Test
    void checkboxesDifferOnAndOffByMoreThanColour() {
        KitDraw off = new KitDraw();
        UiTome.check(off, 0, 0, false, true, false, THEME);
        KitDraw on = new KitDraw();
        UiTome.check(on, 0, 0, true, true, false, THEME);
        List<String> offFills = off.calls("fill ");
        List<String> onFills = on.calls("fill ");
        int offInside = color(offFills.get(0));
        int onInside = color(onFills.get(0));
        int edge = color(offFills.get(3));
        assertTrue(UiContrast.ratio(onInside, offInside) >= UiContrast.GRAPHIC, "on and off fills");
        assertTrue(UiContrast.ratio(edge, THEME.backdrop()) >= UiContrast.GRAPHIC, "box edge on the page");
        assertTrue(onFills.size() > offFills.size(), "the on state adds a tick");
        int tick = color(onFills.get(onFills.size() - 1));
        assertTrue(UiContrast.ratio(tick, onInside) >= UiContrast.GRAPHIC, "tick on the filled box");
    }
}
