package art.arcane.adapt.mod.feature.menu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PromptNumbersTest {
    @Test
    void wholeNumbersFormatWithoutFraction() {
        assertEquals("5", PromptNumbers.format(5.0));
        assertEquals("-3", PromptNumbers.format(-3.0));
        assertEquals("2.5", PromptNumbers.format(2.5));
    }

    @Test
    void parseHonoursBounds() {
        assertEquals(4.0, PromptNumbers.parse(" 4 ", 0.0, 10.0));
        assertEquals(0.0, PromptNumbers.parse("0", 0.0, 10.0));
        assertNull(PromptNumbers.parse("11", 0.0, 10.0));
        assertNull(PromptNumbers.parse("-1", 0.0, null));
        assertEquals(1.0E9, PromptNumbers.parse("1e9", null, null));
        assertNull(PromptNumbers.parse("four", null, null));
        assertNull(PromptNumbers.parse("NaN", null, null));
        assertNull(PromptNumbers.parse(null, null, null));
    }

    @Test
    void indexAcceptsOnlyKnownIds() {
        assertEquals(0, PromptNumbers.index("0", 3));
        assertEquals(2, PromptNumbers.index("2", 3));
        assertEquals(-1, PromptNumbers.index("3", 3));
        assertEquals(-1, PromptNumbers.index("-1", 3));
        assertEquals(-1, PromptNumbers.index("x", 3));
    }
}
