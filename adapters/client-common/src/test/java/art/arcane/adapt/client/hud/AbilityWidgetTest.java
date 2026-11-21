package art.arcane.adapt.client.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbilityWidgetTest {
    @Test
    void textThatFitsKeepsItsSize() {
        assertEquals(1.0, AbilityWidget.fitScale(6, 13.2, 2.0));
        assertEquals(1.0, AbilityWidget.fitScale(18, 18.0, 3.0));
    }

    @Test
    void textTooWideShrinksToWholeScreenPixelsButNotBelowHalf() {
        assertEquals(2.0 / 3.0, AbilityWidget.fitScale(18, 12.0, 3.0), 1e-9);
        assertEquals(0.5, AbilityWidget.fitScale(12, 9.6, 2.0), 1e-9);
        assertEquals(0.75, AbilityWidget.fitScale(12, 9.6, 4.0), 1e-9);
        assertEquals(0.5, AbilityWidget.fitScale(60, 9.6, 1.0), 1e-9);
    }

    @Test
    void glyphsDrawATenthSmallerThanTheirConfiguredSizeInWholeUnits() {
        assertEquals(14, AbilityWidget.iconSize(16));
        assertEquals(20, AbilityWidget.glyphSize(AbilityWidget.iconSize(16)));
        for (int icon = HudOptions.Abilities.MIN_ICON; icon <= HudOptions.Abilities.MAX_ICON; icon += 2) {
            double ratio = (double) AbilityWidget.glyphSize(AbilityWidget.iconSize(icon)) / (icon + AbilityWidget.RING_PAD * 2);
            assertTrue(ratio >= 0.85 && ratio <= 0.95, "icon " + icon + " ratio " + ratio);
        }
    }

    @Test
    void everyCountAndOverflowLabelFitsItsGlyphAtEveryIconSizeAndGuiScale() {
        for (int icon = HudOptions.Abilities.MIN_ICON; icon <= HudOptions.Abilities.MAX_ICON; icon += 2) {
            int glyph = AbilityWidget.glyphSize(AbilityWidget.iconSize(icon));
            for (double pixels = 1.0; pixels <= 4.0; pixels += 1.0) {
                for (int digits = 1; digits <= 3; digits++) {
                    int width = 6 * digits;
                    double count = AbilityWidget.countScale(width, 9, glyph, pixels);
                    double more = AbilityWidget.fitScale(width + 6, AbilityWidget.moreRoom(glyph), pixels);
                    String where = "icon " + icon + " gui" + pixels + " " + digits + " digits";
                    assertTrue(width * count <= AbilityWidget.countRoom(glyph) + 1e-9 || count == 0.5, where);
                    assertTrue(9 * count <= glyph * 0.5 + 1e-9 || count == 0.5, where + " count height " + 9 * count);
                    assertTrue((width + 6) * more <= AbilityWidget.moreRoom(glyph) + 1e-9 || more == 0.5, where);
                }
            }
        }
    }
}
