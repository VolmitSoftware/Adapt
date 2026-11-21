package art.arcane.adapt.client.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyRunTest {
    private static final int[] PALETTE = new int[16];

    static {
        for (int index = 0; index < PALETTE.length; index++) {
            PALETTE[index] = 0xFF000000 | index;
        }
    }

    @Test
    void coloursComeFromThePaletteAndResetFormats() {
        List<LegacyRun> runs = LegacyRun.parse("plain §aGreen §lbold§c red§r back", PALETTE);
        assertEquals(List.of(
                new LegacyRun("plain ", 0, false, false, false, false, false),
                new LegacyRun("Green ", 0xFF00000A, false, false, false, false, false),
                new LegacyRun("bold", 0xFF00000A, true, false, false, false, false),
                new LegacyRun(" red", 0xFF00000C, false, false, false, false, false),
                new LegacyRun(" back", 0, false, false, false, false, false)), runs);
    }

    @Test
    void upperCaseCodesUnknownCodesAndATrailingSignAreHandled() {
        assertEquals(List.of(new LegacyRun("x", 0xFF00000F, false, true, false, false, false),
                new LegacyRun("y§", 0xFF00000F, false, true, false, false, false)), LegacyRun.parse("§F§ox§zy§", PALETTE));
    }

    @Test
    void onlyLightTextKeepsItsShadow() {
        assertTrue(LegacyRun.light(0xFFFFFFFF));
        assertTrue(LegacyRun.light(0xFFF2D98A));
        assertFalse(LegacyRun.light(0xFF2E2216));
        assertFalse(LegacyRun.light(0xFF5A4430));
    }
}
