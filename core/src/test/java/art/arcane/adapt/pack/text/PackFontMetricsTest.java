package art.arcane.adapt.pack.text;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PackFontMetricsTest {
    private final PackFontMetrics font = PackFontMetrics.vanilla();

    @Test
    void aTableFontMeasuresItsAdvancesAndCoversOnlyWhatItLists() {
        PackFontMetrics table = PackFontMetrics.of("test", java.util.Map.of((int) 'a', 4, (int) 'b', 4, (int) 'm', 6, (int) ' ', 3));
        assertThat(table.width("ab m")).isEqualTo(17);
        assertThat(table.fits("ab m", 16)).isTrue();
        assertThat(table.covers("§aab")).isTrue();
        assertThat(table.covers("abc")).isFalse();
        assertThat(font.covers("anything at all")).isTrue();
    }

    @Test
    void asciiAdvancesMatchTheClient() {
        assertThat(font.width('A', false)).isEqualTo(6);
        assertThat(font.width('i', false)).isEqualTo(2);
        assertThat(font.width('l', false)).isEqualTo(3);
        assertThat(font.width(' ', false)).isEqualTo(4);
        assertThat(font.width('!', false)).isEqualTo(2);
        assertThat(font.width('@', false)).isEqualTo(7);
        assertThat(font.width("Hello")).isEqualTo(24);
    }

    @Test
    void boldAddsAPixelToBitmapGlyphsAndHalfToUnifont() {
        assertThat(font.width('A', true)).isEqualTo(7);
        assertThat(font.width("§lAA")).isEqualTo(14);
        assertThat(font.width('中', false)).isEqualTo(9);
        assertThat(font.width("§l中中")).isEqualTo(19);
    }

    @Test
    void sectionCodesTakeNoRoomAndColourEndsBold() {
        assertThat(font.width("§aA")).isEqualTo(6);
        assertThat(font.width("§lA§cA")).isEqualTo(13);
        assertThat(font.width("A§")).isEqualTo(6);
    }

    @Test
    void accentedAndCyrillicComeFromTheirOwnSheets() {
        assertThat(font.width('é', false)).isEqualTo(6);
        assertThat(font.width('Ж', false)).isGreaterThan(0);
    }

    @Test
    void unknownCodepointsUseTheMissingGlyph() {
        assertThat(font.width(0x10FFFD, false)).isEqualTo(6);
    }

    @Test
    void wrapBreaksAtTheLastSpaceAndDropsIt() {
        assertThat(font.wrap("aaaa bbbb cccc", 52)).containsExactly("aaaa bbbb", "cccc");
    }

    @Test
    void wrapBreaksInsideAWordWithNoSpace() {
        List<String> lines = font.wrap("abcdefghij", 30);
        assertThat(lines).containsExactly("abcde", "fghij");
        for (String line : lines) {
            assertThat(font.width(line)).isLessThanOrEqualTo(30);
        }
    }

    @Test
    void wrapKeepsAtLeastOneGlyphPerLine() {
        assertThat(font.wrap("ab", 1)).containsExactly("a", "b");
    }

    @Test
    void wrapHonoursNewlinesAndCarriesCodes() {
        assertThat(font.wrap("§cone\ntwo", 200)).containsExactly("§cone", "§ctwo");
        assertThat(font.wrap("", 10)).containsExactly("");
    }

    @Test
    void wordWrapBreaksOnlyAtSpacesAndLeavesALongWordWhole() {
        assertThat(PackFontMetrics.vanilla().wrapWords("Hunter's Resistance Training", 50))
            .containsExactly("Hunter's", "Resistance", "Training");
    }

    @Test
    void breakingWordWrapSplitsAWordTooWideAtASyllableWithAHyphen() {
        PackFontMetrics vanilla = PackFontMetrics.vanilla();
        assertThat(vanilla.wrapWords("Convergence", 54, true)).containsExactly("Conver-", "gence");
        assertThat(vanilla.wrapWords("Tidecaller Mason", 40, true)).containsExactly("Tide-", "caller", "Mason");
        assertThat(vanilla.wrapWords("Second Chance", 54, true)).containsExactly("Second", "Chance");
        for (String line : vanilla.wrapWords("Stonewrought Bloodbinder", 30, true)) {
            assertThat(vanilla.fits(line, 30)).as(line).isTrue();
        }
    }

    @Test
    void lineHeightIsNine() {
        assertThat(font.lineHeight()).isEqualTo(9);
        assertThat(font.version()).isEqualTo("26.3");
    }
}
