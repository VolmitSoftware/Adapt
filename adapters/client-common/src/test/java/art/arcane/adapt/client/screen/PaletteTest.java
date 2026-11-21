package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.ui.kit.ProgressBar;
import art.arcane.adapt.client.ui.kit.UiContrast;
import art.arcane.adapt.client.ui.kit.UiTheme;
import art.arcane.adapt.client.ui.kit.UiTome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Skill colours become inks that read on every paper a name is written on and stay apart from each other. */
class PaletteTest {
    /** The sixteen chat colours skills are given, by legacy code 0-9 and a-f. */
    private static final int[] CHAT = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA, 0x555555, 0x5555FF,
            0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};
    private static final int[] ODD = {0x7F3FB2, 0x2E8B57, 0xFF8800, 0x123456, 0xFEFEFE, 0x808080, 0xC0FFEE};
    /** The least two inks may differ, as the summed channel difference; below this two names look the same colour. */
    private static final int APART = 48;

    @Test
    void skillInksKeepTextContrastOnEveryPaperANameSitsOn() {
        UiTheme theme = UiTheme.DEFAULT;
        int[] papers = {theme.backdrop(), UiContrast.over(theme.tile(), theme.backdrop()), UiContrast.over(UiTome.HOVER, theme.backdrop()),
                theme.tooltip(), theme.surfaceHover(), theme.surface()};
        for (int[] colours : new int[][] {CHAT, ODD}) {
            for (int colour : colours) {
                int ink = Palette.accent(colour);
                assertTrue(ink >>> 24 == 0xFF, Integer.toHexString(colour) + " is not opaque");
                for (int paper : papers) {
                    double ratio = UiContrast.ratio(ink, paper);
                    assertTrue(ratio >= UiContrast.TEXT, Integer.toHexString(colour) + " as " + Integer.toHexString(ink) + " on "
                            + Integer.toHexString(paper) + ": " + ratio);
                }
            }
        }
    }

    @Test
    void theSixteenChatColoursStayApartAsInks() {
        for (int first = 0; first < CHAT.length; first++) {
            for (int second = first + 1; second < CHAT.length; second++) {
                int a = Palette.accent(CHAT[first]);
                int b = Palette.accent(CHAT[second]);
                int apart = Math.abs((a >> 16 & 0xFF) - (b >> 16 & 0xFF)) + Math.abs((a >> 8 & 0xFF) - (b >> 8 & 0xFF))
                        + Math.abs((a & 0xFF) - (b & 0xFF));
                assertTrue(apart >= APART, Integer.toHexString(CHAT[first]) + " and " + Integer.toHexString(CHAT[second]) + " both read as "
                        + Integer.toHexString(a) + " / " + Integer.toHexString(b));
            }
        }
    }

    @Test
    void lighterSourcesGiveLighterInks() {
        int[][] pairs = {{0x00AA00, 0x55FF55}, {0x0000AA, 0x5555FF}, {0xAA0000, 0xFF5555}, {0xAA00AA, 0xFF55FF}, {0x00AAAA, 0x55FFFF},
                {0x000000, 0x555555}, {0x555555, 0xAAAAAA}};
        for (int[] pair : pairs) {
            assertTrue(UiContrast.luminance(Palette.accent(pair[0])) < UiContrast.luminance(Palette.accent(pair[1])),
                    Integer.toHexString(pair[0]) + " is not darker than " + Integer.toHexString(pair[1]));
        }
    }

    @Test
    void stateInksAndTracksReadOnPaper() {
        UiTheme theme = UiTheme.DEFAULT;
        for (int ink : new int[] {Palette.GOOD, Palette.BAD, Palette.LOCK, Palette.MUTED}) {
            assertTrue(UiContrast.ratio(ink, Palette.PAPER) >= UiContrast.TEXT, Integer.toHexString(ink) + " on paper");
        }
        int hovered = UiContrast.over(UiTome.HOVER, theme.backdrop());
        assertTrue(UiContrast.ratio(UiContrast.over(ProgressBar.rail(theme), hovered), hovered) >= UiContrast.GRAPHIC);
    }
}
